/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.hooks

import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import android.content.Context
import android.media.AudioManager
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 音乐岛创建拦截（上游 HyperIsland v3.2.3 `hookMediaIslandVisibility` 的移植改造版）。
 *
 * 上游原版是"按应用"开关：某些应用的媒体通知不显示音乐岛。
 * 本模块改成**跟随自身模式**：
 *   - 状态栏歌词模式（超级岛总开关关闭 + 状态栏歌词开启 + 岛隐藏行为不是"不隐藏"）
 *       → 就地拦截 `MiuiIslandMediaControllerImpl.addDynamicIslandView(...)`：
 *         不创建音乐岛（return null），并把已在显示的那颗岛用
 *         `removeDynamicIslandView(key, animate)` 摘掉。
 *   - 超级岛模式（总开关开启）→ 一律放行（chain.proceed），歌词照常注入自绘玻璃岛。
 *
 * 为什么要在源头拦截：
 *   状态栏模式原先靠 `StatusBarLyricHooker.hideIslandViews()` 事后把岛容器
 *   （island_content / container + DynamicIslandBackgroundView）宽高清零 + alpha=0 + GONE 压掉，
 *   这条路依赖"能找到容器 + 能压住 + 压完没人再拉回来"，系统每 ~3 秒一次的
 *   hideIslandLayout 风暴和重建动画都可能把容器重新拉出来（玻璃模式切状态栏压不干净）。
 *   直接不让系统建岛，就没有后续要压的对象。
 *
 * 反射契约（对齐上游，且做宽松匹配，避免 OS 版本差异静默失效）：
 *   类  com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaControllerImpl
 *   方法 addDynamicIslandView(...)：返回 void，末参类型名含 MediaData
 *   方法 removeDynamicIslandView*(String, Boolean)
 *   字段 currentKey（控制器上）；MediaData.packageName / MediaData.notificationKey
 *
 * 注意：拿不到反射目标时只打日志、不抛异常，绝不因为签名漂移影响 SystemUI 正常建岛。
 */
object MediaIslandCreationGate {
    private const val TAG = "MediaIslandGate"
    private const val CONTROLLER_CLASS =
        "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaControllerImpl"

    @Volatile
    private var installed = false

    @Volatile
    private var removeMethod: Method? = null

    @Volatile
    private var currentKeyField: Field? = null

    @Volatile
    private var mediaNotificationKeyField: Field? = null

    @Volatile
    private var mediaPackageNameField: Field? = null

    /** 建岛方法本体（切回超级岛时用它让系统把岛重建出来）。 */
    @Volatile
    private var addMethodRef: Method? = null

    /** 控制器缓存：topMediaData / 两个 ViewHolder，重建时原样喂回去。 */
    @Volatile
    private var topMediaDataField: Field? = null

    @Volatile
    private var playerHolderField: Field? = null

    @Volatile
    private var dummyHolderField: Field? = null

    /** 最近一次建岛调用的入参（ViewHolder 可能为 null），重建时兜底用。 */
    @Volatile
    private var lastAddArgs: List<Any?>? = null

    /** 最近一次被拦/被放行时的控制器实例，供"切到状态栏模式立即摘岛"用。 */
    @Volatile
    private var controllerRef: WeakReference<Any>? = null

    /** SystemUI Application 的 Context，用来判断媒体是否在播（对齐"播放时隐藏"语义）。 */
    @Volatile
    private var appContextRef: WeakReference<Context>? = null

    /** 决策日志用：只在策略翻转时打一条，避免每次更新刷屏。 */
    @Volatile
    private var lastDecision: String = ""

    @Volatile
    private var blockCount: Long = 0L

    @Volatile
    private var lastBlockLogAt: Long = 0L

    /**
     * 安装 Hook。可重复调用（幂等）。
     * @return true=已安装或本次安装成功；false=类/方法找不到（静默跳过）
     */
    fun hook(module: XposedModule, cl: ClassLoader, context: Context? = null): Boolean {
        if (installed) return true

        if (context != null) appContextRef = WeakReference(context)

        val controller = loadController(cl)
        if (controller == null) {
            HookLogger.d(TAG, "未找到 $CONTROLLER_CLASS，跳过音乐岛拦截")
            return false
        }

        // 宽松筛选：不同 OS 版本参数个数不同（上游 18.3.2.5.0 是 3 个），只锁"末参是 MediaData、返回 void"。
        val addMethods = controller.methods.filter { m ->
            m.name.startsWith("addDynamicIslandView") &&
                !m.name.endsWith("\$default") &&
                m.returnType == Void.TYPE &&
                m.parameterCount >= 1 &&
                runCatching {
                    m.parameterTypes.last().simpleName.contains("MediaData")
                }.getOrDefault(false)
        }
        if (addMethods.isEmpty()) {
            HookLogger.w(TAG, "addDynamicIslandView(*MediaData) 未找到，跳过音乐岛拦截")
            return false
        }

        synchronized(this) {
            if (installed) return true
            installed = true
        }

        runCatching {
            removeMethod = findRemoveMethod(controller)
            currentKeyField = findField(controller, "currentKey")
        }.onFailure { e ->
            HookLogger.e(TAG, "移除方法/currentKey 反射失败（拦截仍生效，只是不能立即摘岛）", e)
        }

        // 切回超级岛时"让系统重建音乐岛"所需的素材：建岛方法 + 控制器里缓存的 MediaData/ViewHolder。
        // 系统在 remove 之后不会自己再 add（只有媒体数据变化才 add），所以切模式这一步必须由我们推一把。
        runCatching {
            addMethodRef = addMethods.first()
            topMediaDataField = findField(controller, "topMediaData")
            playerHolderField = findField(controller, "miuiPlayerHolder")
            dummyHolderField = findField(controller, "miuiDummyPlayerHolder")
        }.onFailure { e ->
            HookLogger.e(TAG, "建岛素材反射失败（切回超级岛不会自动重建）", e)
        }

        runCatching {
            val mediaDataClass = addMethods.first().parameterTypes.last()
            mediaPackageNameField = findField(mediaDataClass, "packageName")
            mediaNotificationKeyField = findField(mediaDataClass, "notificationKey")
        }.onFailure { e ->
            HookLogger.e(TAG, "MediaData 字段反射失败（拦截仍生效，只是日志里没有包名）", e)
        }

        addMethods.forEach { method ->
            runCatching {
                module.deoptimize(method)
                module.hook(method).intercept(CreationGateHook())
            }.onFailure { e ->
                HookLogger.e(TAG, "hook ${method.name} 失败", e)
            }
        }

        HookLogger.i(
            TAG,
            "音乐岛拦截已安装: methods=${addMethods.size}, remove=${removeMethod?.name ?: "null"}, " +
                "currentKey=${currentKeyField != null}"
        )
        return true
    }

    /**
     * 切进状态栏模式时立即摘掉正在显示的音乐岛（不依赖系统下一次更新）。
     * 由 StatusBarLyricHooker.hideIslandViews() 调用。
     */
    fun removeCurrentIsland(reason: String): Boolean {
        val controller = controllerRef?.get() ?: return false
        val method = removeMethod ?: return false
        val key = runCatching {
            @Suppress("UNCHECKED_CAST")
            currentKeyField?.get(controller) as? String
        }.getOrNull() ?: return false
        return runCatching {
            method.invoke(controller, key, true)
            HookLogger.i(TAG, "状态栏模式 → 已移除当前音乐岛 key=$key（$reason）")
            true
        }.getOrElse { e ->
            HookLogger.e(TAG, "移除当前音乐岛失败", e)
            false
        }
    }

    /**
     * 切回超级岛模式时，把被摘掉的音乐岛重新建回来。
     *
     * 为什么必须自己推一把：`removeDynamicIslandView` 之后系统只在"媒体数据变化"
     * （播放/暂停/切歌）时才再调 `addDynamicIslandView`，单纯切模块模式系统不会自己 add，
     * 表现就是"切回超级岛后岛不回来，要等下一首歌"。
     * 这里直接拿控制器自己缓存的 `topMediaData` + 两个 ViewHolder 回调它的建岛方法，
     * 等价于系统自己那次 add；手里没素材（尚未建立过/已被 reset）就跳过，不硬造。
     */
    fun forceRecreate(reason: String): Boolean {
        if (!installed) return false
        val controller = controllerRef?.get() ?: return false
        val add = addMethodRef ?: return false

        val currentKey = runCatching {
            @Suppress("UNCHECKED_CAST")
            currentKeyField?.get(controller) as? String
        }.getOrNull()
        if (currentKey != null) return false // 岛还在，不重复建

        if (!isMusicPlaying()) {
            HookLogger.d(TAG, "切回超级岛：媒体未在播，跳过重建（$reason）")
            return false
        }

        val cachedArgs = lastAddArgs
        val cachedData = cachedArgs?.lastOrNull()
        var data: Any? = cachedData ?: runCatching { topMediaDataField?.get(controller) }.getOrNull()
        var holderA: Any? = runCatching { playerHolderField?.get(controller) }.getOrNull()
        var holderB: Any? = runCatching { dummyHolderField?.get(controller) }.getOrNull()
        if (holderA == null && holderB == null && cachedArgs != null && cachedArgs.size >= 3) {
            holderA = cachedArgs[0]
            holderB = cachedArgs[1]
        }

        if (data == null || (holderA == null && holderB == null)) {
            HookLogger.i(TAG, "切回超级岛：控制器没缓存建岛素材，跳过重建（等下一次媒体更新）")
            return false
        }

        return runCatching {
            add.invoke(controller, holderA, holderB, data)
            HookLogger.i(TAG, "切回超级岛 → 已让系统重建音乐岛（$reason）")
            true
        }.getOrElse { e ->
            HookLogger.e(TAG, "切回超级岛重建音乐岛失败（等下一次媒体更新）", e)
            false
        }
    }

    /** 采集控制器 classloader：SystemUI 侧通常在 app classLoader，插件 loader 兜底试一次。 */
    private fun loadController(cl: ClassLoader): Class<*>? {
        val candidates = listOfNotNull(
            runCatching { cl.loadClass(CONTROLLER_CLASS) }.getOrNull(),
            runCatching { Class.forName(CONTROLLER_CLASS) }.getOrNull(),
            runCatching {
                Class.forName(CONTROLLER_CLASS, false, ClassLoader.getSystemClassLoader())
            }.getOrNull()
        )
        return candidates.firstOrNull()
    }

    private fun findRemoveMethod(controller: Class<*>): Method? {
        return controller.methods.firstOrNull { m ->
            m.name.startsWith("removeDynamicIslandView") &&
                !m.name.endsWith("\$default") &&
                m.parameterCount == 2 &&
                m.parameterTypes[0] == String::class.java &&
                (m.parameterTypes[1] == java.lang.Boolean.TYPE || m.parameterTypes[1].simpleName == "boolean")
        }?.also { it.isAccessible = true }
            ?: controller.methods.firstOrNull { it.name.startsWith("removeDynamicIslandView") }
                ?.also { it.isAccessible = true }
    }

    /** Kotlin 里 currentKey 可能在超类/伴生类，沿继承链找一次。 */
    private fun findField(owner: Class<*>, name: String): Field? {
        var c: Class<*>? = owner
        while (c != null) {
            val f = runCatching { c.getDeclaredField(name) }.getOrNull()
            if (f != null) {
                f.isAccessible = true
                return f
            }
            c = c.superclass
        }
        return null
    }

    /**
     * 当前是否属于"状态栏歌词模式"：
     *   1) 超级岛总开关开着 → 超级岛模式，放行；
     *   2) 状态栏歌词关着 → 两个模式都没开，不干预系统自身音乐岛；
     *   3) 用户把"状态栏歌词显示时隐藏超级岛"设成"不隐藏"→ 尊重用户，不拦截。
     */
    /** 当前是否真的在拦音乐岛创建（已安装 + 处于状态栏歌词模式）。供隐藏逻辑判断要不要"压容器"。 */
    fun isBlockingNow(): Boolean = installed && isStatusBarLyricMode()

    /**
     * 当前是否处于「状态栏歌词模式且需要隐藏音乐岛」——只判模式，不判播放态。
     * 供隐藏逻辑用来决定"还压不压容器"：模式在，就不要压（否则会误伤充电岛等其他岛）。
     */
    fun isStatusBarIslandMode(): Boolean {
        if (!installed) return false
        val prefs = HookEntry.instance?.prefs ?: return false
        val superIslandOn = runCatching {
            prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, false)
        }.getOrElse { false }
        if (superIslandOn) return false

        val sbPrefs = HookEntry.instance?.statusBarPrefs ?: return false
        val statusBarOn = runCatching {
            sbPrefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC, true)
        }.getOrElse { true }
        if (!statusBarOn) return false

        val hideBehavior = runCatching {
            sbPrefs.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR
            )
        }.getOrElse { RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR }
        return hideBehavior != RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE
    }

    /** 新增档（暂停后行为 = 2）：暂停期间不生成超级岛，恢复播放再让系统弹出来。 */
    fun isPauseNoCreateMode(): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        val superIslandOn = runCatching {
            prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, false)
        }.getOrDefault(false)
        if (!superIslandOn) return false
        val behavior = runCatching {
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
                RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
            )
        }.getOrDefault(0)
        return behavior == 2
    }

    private fun isStatusBarLyricMode(): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        val superIslandOn = runCatching {
            prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, false)
        }.getOrElse { false }
        if (superIslandOn) return false

        val sbPrefs = HookEntry.instance?.statusBarPrefs ?: return false
        val statusBarOn = runCatching {
            sbPrefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC, true)
        }.getOrElse { true }
        if (!statusBarOn) return false

        val hideBehavior = runCatching {
            sbPrefs.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR
            )
        }.getOrElse { RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR }

        // 与既有"隐藏超级岛"三档语义对齐：不隐藏→放行；始终→无条件拦；播放时→只在播放中拦。
        return when (hideBehavior) {
            RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE -> false
            RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_ALWAYS -> true
            else -> isMusicPlaying()
        }
    }

    /** 媒体是否在播（AudioManager.isMusicActive）。拿不到就按"在播"处理，宁可拦也不闪岛。 */
    private fun isMusicPlaying(): Boolean {
        val ctx = appContextRef?.get() ?: return true
        return runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.isMusicActive ?: true
        }.getOrElse { true }
    }

    private fun readString(field: Field?, target: Any?): String? {
        if (field == null || target == null) return null
        return runCatching { field.get(target) as? String }.getOrNull()
    }

    private class CreationGateHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val controller = chain.thisObject
            controllerRef = WeakReference(controller)
            lastAddArgs = chain.args.toList()

            val media = chain.args.lastOrNull()

            return runCatching {
                val pkg = readString(mediaPackageNameField, media)
                val key = readString(mediaNotificationKeyField, media)

                if (!installed) {
                    if (lastDecision != "release") {
                        lastDecision = "release"
                        HookLogger.i(TAG, "音乐岛策略=放行（未安装）pkg=${pkg ?: "-"}")
                    }
                    return chain.proceed()
                }

                // 状态栏歌词模式：按既有三档语义拦；
                // 新增「暂停后行为=暂停时不生成」：超级岛模式下，非播放期间一律不建岛。
                val statusBarBlock = isStatusBarLyricMode()
                val pauseBlock = !statusBarBlock && isPauseNoCreateMode() && !isMusicPlaying()

                if (!statusBarBlock && !pauseBlock) {
                    if (lastDecision != "release") {
                        lastDecision = "release"
                        HookLogger.i(
                            TAG,
                            "音乐岛策略=放行（超级岛模式/未启用状态栏歌词）pkg=${pkg ?: "-"}"
                        )
                    }
                    return chain.proceed()
                }

                if (lastDecision != "block" && statusBarBlock) {
                    lastDecision = "block"
                    HookLogger.i(TAG, "音乐岛策略=拦截（状态栏歌词模式）pkg=${pkg ?: "-"}")
                } else if (lastDecision != "block-pause" && pauseBlock) {
                    lastDecision = "block-pause"
                    HookLogger.i(TAG, "音乐岛策略=拦截（暂停时不生成超级岛）pkg=${pkg ?: "-"}")
                }
                blockCount++

                // 已经显示着的那颗岛（key 与 currentKey 相同）先摘掉，再阻断本次创建。
                if (key != null) {
                    val currentKey = runCatching {
                        @Suppress("UNCHECKED_CAST")
                        currentKeyField?.get(controller) as? String
                    }.getOrNull()
                    if (currentKey == key) {
                        runCatching { removeMethod?.invoke(controller, key, true) }
                            .onFailure { e -> HookLogger.e(TAG, "拦截时移除旧音乐岛失败", e) }
                    }
                }

                val now = System.currentTimeMillis()
                if (now - lastBlockLogAt > 5000L) {
                    lastBlockLogAt = now
                    HookLogger.i(
                        TAG,
                        "已拦截音乐岛创建（累计 $blockCount 次）pkg=${pkg ?: "-"} key=${key ?: "-"}"
                    )
                }
                null
            }.getOrElse { e ->
                // 任何反射/读取异常都退回系统默认行为，绝不因为本 Hook 让系统建不了岛。
                HookLogger.e(TAG, "音乐岛拦截判定异常，退回系统行为", e)
                chain.proceed()
            }
        }
    }
}
