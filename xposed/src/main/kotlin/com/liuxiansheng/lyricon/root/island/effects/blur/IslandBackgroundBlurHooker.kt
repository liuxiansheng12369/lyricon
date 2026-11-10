/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 超级岛毛玻璃：hook 系统超级岛背景层触发刷新，毛玻璃挂在词岛宿主根
 * （岛内容胶囊，bounds=岛区域），避免整条状态栏被模糊；
 * 支持系统高斯模糊与小米 Bionics 柔光玻璃双通道。
 *
 * 3.0.706（改回官方，稳定性优先）：彻底停用自绘 LiquidGlass 引擎与六层防护 hook
 * （updateDarkLightMode/updateBackgroundBg/updateContainer/setDrawable/alphaAnimation
 * 字段替换等）——自绘 hook 太深、反复干扰系统状态机（黑罩反复、歌词丢失、大岛被清）。
 * 恢复 665 官方通道：材质自定义 → 系统原生 setMiGlass（Bionics 柔光）；
 * 非自定义 → 系统高斯。挂载成功后仅轻量清一次 container/mask 黑罩（不每帧、不干预生命周期）。
 * 稳定性 = 系统官方；黑罩靠挂载时轻量清除兜底。
 */

package com.liuxiansheng.lyricon.hyper.root.island.effects.blur

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.island.effects.blur.softglass.SoftGlassConfig
import com.liuxiansheng.lyricon.hyper.root.island.effects.blur.softglass.SoftGlassController
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandPresentationCoordinator
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import java.util.WeakHashMap

/**
 * 词岛超级岛毛玻璃（高斯模糊）（V3 自研）：
 * hook 系统超级岛背景层触发刷新，但毛玻璃挂在**词岛宿主根**（岛内容胶囊，bounds=岛区域），
 * 避免挂在全宽背景容器 island_container 导致整条状态栏被模糊。
 */
object IslandBackgroundBlurHooker : XposedInterface.Hooker {

    private const val TAG = "IslandBackgroundBlur"
    // 背景视图用于触发刷新时机（onAttachedToWindow 可靠触发）；真正挂毛玻璃用词岛宿主根
    private const val BG_CLASS =
        "miui.systemui.dynamicisland.DynamicIslandBackgroundView"

    private val renderer = NativeBlurRenderer()
    private val configReader = IslandMaterialConfigReader()
    private val attachedBlurs = WeakHashMap<View, OwnedBlur>()
    // 3.0.707：挂载防抖——系统刷新高频触发 maybeAttach，避免对同一状态视图
    // 短时间重复 apply setMiGlass（日志实测 30+ 次重复），减少对系统渲染的干扰。
    private val lastApplyStamp = WeakHashMap<View, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshAll()
    }

    @Volatile
    private var prefsRegistered = false

    /**
     * 3.0.736：上游 HyperIsland 外观引擎已完整接管毛玻璃（高斯/柔光/液态/高光 +
     * 六层遮罩对抗）。此标志置位后，旧 hook 的 intercept/attachBlur 全部跳过，
     * 避免两套毛玻璃同时挂载互相覆盖（V3 旧 radius=0 会把上游柔光/高斯盖成透明）。
     * 由 SystemUIHookRegistry 在注册上游三件套后置位。
     */
    @Volatile
    var supersededByUpstream = false

    fun hook(module: XposedModule, cl: ClassLoader) {
        // 3.0.561：恢复毛玻璃/柔光玻璃（3.0.548 曾因怀疑导致切超导歌词不过而彻底禁用；
        // 真根因已定位为"播放状态关卡"并修复，毛玻璃不再背锅，恢复挂钩让设置可正常开启）
        val clazz = runCatching { cl.loadClass(BG_CLASS) }.getOrNull() ?: run {
            HookLogger.w(TAG, "未找到背景类 $BG_CLASS（跳过毛玻璃）")
            return
        }
        runCatching {
            val method = SystemUiReflection.findMethod(clazz, "onAttachedToWindow")
                ?: error("onAttachedToWindow not found")
            method.isAccessible = true
            module.deoptimize(method)
            module.hook(method).intercept(this)
            HookLogger.i(TAG, "毛玻璃：背景视图已 hook")
        }.onFailure {
            HookLogger.e(TAG, "毛玻璃 hook 失败: ${it.message}", it)
        }
        // 3.0.706（防脏引用）：视图销毁（锁屏/AOD）时释放玻璃引用，避免干扰系统回收重建
        runCatching {
            val detachMethod = SystemUiReflection.findMethod(clazz, "onDetachedFromWindow")
                ?: error("onDetachedFromWindow not found")
            detachMethod.isAccessible = true
            module.deoptimize(detachMethod)
            module.hook(detachMethod).intercept(this)
            HookLogger.i(TAG, "毛玻璃：背景视图 detach 已 hook")
        }.onFailure {
            HookLogger.e(TAG, "毛玻璃 detach hook 失败: ${it.message}", it)
        }
        // 绑定小米 Bionics 柔光材质 runtime（OS4 专用；拿不到 API 则安全回退高斯）
        runCatching { initSoftGlass(module, cl) }
            .onFailure { HookLogger.e(TAG, "柔光玻璃 runtime 绑定失败: ${it.message}", it) }
    }

    /** 绑定小米原生 Bionics 仿生材质：让「柔光玻璃」能走 setMiGlass(42参数) 真柔光。 */
    private fun initSoftGlass(module: XposedModule, cl: ClassLoader) {
        val contentClass = runCatching {
            Class.forName(
                "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView",
                false,
                cl,
            )
        }.getOrNull() ?: return
        val windowClass = runCatching {
            Class.forName(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowView",
                false,
                cl,
            )
        }.getOrNull() ?: return
        val compatClass = sequenceOf(
            "miui.systemui.util.MiBlurCompat",
            "miui.util.MiBlurCompat",
        ).mapNotNull { runCatching { Class.forName(it, false, cl) }.getOrNull() }
            .firstOrNull() ?: return
        if (!SoftGlassController.bindRuntime(module, contentClass, compatClass)) {
            HookLogger.w(TAG, "柔光玻璃 runtime 不可用（OS3 或无 Bionics），继续高斯")
            return
        }
        SoftGlassController.observeWindowLifecycle(module, windowClass)
        HookLogger.i(TAG, "柔光玻璃 Bionics runtime 已绑定")
    }

    override fun intercept(chain: XposedInterface.Chain): Any? {
        // 上游接管后旧 hook 整体退役：只透传，不再挂 V3 旧毛玻璃
        if (supersededByUpstream) return chain.proceed()
        val view = chain.thisObject as? View
        val result = chain.proceed()
        if (view != null) {
            if (view.isAttachedToWindow) {
                // onAttachedToWindow：视图挂载/重建（亮屏解锁重建岛）→ 挂载官方玻璃
                val prefs = HookEntry.instance?.prefs
                if (prefs != null) {
                    registerPrefsListener(prefs)
                    mainHandler.post { attachBlur(prefs) }
                }
            } else {
                // onDetachedFromWindow：视图销毁（锁屏/AOD/收起）→ 释放玻璃引用，
                // 避免脏引用残留干扰系统视图回收重建
                releaseNativeBlur(view)
            }
        }
        return result
    }

    private fun registerPrefsListener(prefs: SharedPreferences) {
        if (prefsRegistered) return
        prefsRegistered = true
        runCatching { prefs.registerOnSharedPreferenceChangeListener(prefsListener) }
    }

    /** 挂毛玻璃到词岛宿主根（岛内容胶囊），不触碰全宽背景容器。 */
    private fun attachBlur(prefs: SharedPreferences) {
        configReader.reload(prefs)
        val hosts = IslandPresentationCoordinator.snapshotAttachedHosts()
        if (hosts.isEmpty()) {
            HookLogger.d(TAG, "毛玻璃：当前无已挂载岛宿主")
            return
        }
        hosts.forEach { token ->
            maybeAttach(token.root)
        }
    }

    /** 供渲染刷新链调用：宿主出现时挂毛玻璃到该宿主根（主线程）。 */
    fun maybeAttach(root: ViewGroup?) {
        if (root == null) return
        if (!root.isAttachedToWindow || root.visibility != View.VISIBLE) return
        val prefs = HookEntry.instance?.prefs ?: return
        configReader.reload(prefs)
        val config = configReader.blurFor(IslandType.BIG)
        // 挂状态视图（findStateView 递归可靠）
        val stateView = findStateView(root) ?: run {
            HookLogger.d(TAG, "maybeAttach: 未找到状态视图（${root.javaClass.simpleName}）")
            return
        }
        if (!stateView.isAttachedToWindow || stateView.visibility != View.VISIBLE) {
            releaseNativeBlur(stateView)
            SoftGlassController.release(stateView, restoreBackground = true)
            return
        }
        if (!config.isActive) {
            releaseNativeBlur(stateView)
            SoftGlassController.release(stateView, restoreBackground = true)
            return
        }
        // 3.0.707：挂载防抖——同一状态视图 1s 内不重复挂载（避免每次刷新都重新 apply）
        val now = SystemClock.uptimeMillis()
        val lastApply = lastApplyStamp[stateView]
        if (lastApply != null && now - lastApply < 1000L) return
        lastApplyStamp[stateView] = now
        val material = configReader.materialFor(IslandType.BIG)
        // 对齐上游 HyperIsland：只有柔光玻璃（SOFT）走小米原生 Bionics（setMiGlass 42 参数）；
        // 高斯/液态/高光一律走系统模糊渲染器（系统 BackgroundBlurDrawable 通道），不走自绘引擎。
        if (material.type == MaterialType.SOFT) {
            releaseNativeBlur(stateView)
            if (!applySoftGlass(stateView, prefs)) {
                // Bionics 不可用则回退系统模糊
                attachNativeBlur(stateView, config)
            }
            return
        }
        // 非柔光：先释放已挂的 Bionics 材质，再走系统模糊
        SoftGlassController.release(stateView, restoreBackground = true)
        attachNativeBlur(stateView, config)
    }

    /** 从词岛材质配置构造 Bionics 柔光参数，并应用到状态视图。返回是否成功。 */
    private fun applySoftGlass(view: View, prefs: SharedPreferences): Boolean {
        val raw = prefs.getString(RootConstants.KEY_ISLAND_MATERIAL_BIG, "").orEmpty()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
        val material = configReader.materialFor(IslandType.BIG)
        val soft = SoftGlassConfig.fromMaterial(json, material)
        val ok = runCatching { SoftGlassController.apply(view, soft) }.getOrDefault(false)
        if (ok) {
            // 提交材质后必须持有窗口采样器（acquire sampler），否则背景实时模糊不开、柔光显示不完整
            runCatching { SoftGlassController.beginRendering(view) }
                .onFailure { HookLogger.w(TAG, "柔光渲染租约失败: ${it.message}") }
            HookLogger.i(TAG, "官方柔光玻璃已应用到状态视图")
            // 3.0.706：挂载成功后轻量清一次 container/mask 黑罩（不每帧、不干预生命周期）
            clearBlackShade(view)
            // 3.0.708（黑罩定位）：挂载后 dump 视图树（向上6层+向下3层），
            // 定位黑罩具体来源层（container/mask 已清仍黑 → 来自背景视图/祖先链）
            dumpViewTree(view)
        } else {
            HookLogger.w(TAG, "柔光玻璃 Bionics 不可用，回退高斯模糊")
        }
        return ok
    }

    /**
     * 3.0.706（官方玻璃黑罩轻量清除）：挂载成功后，从状态视图向上找
     * DynamicIslandBaseContentView，清其 container / mask 的黑底一次。
     * 只挂载时清一次（不每帧、不干预系统生命周期），黑罩清除效果靠这一次 + 系统后续
     * 若自己写回则不强对抗——稳定性优先于强制清黑。
     */
    private fun clearBlackShade(stateView: View) {
        runCatching {
            var up: View? = stateView
            var guard = 0
            while (up != null && guard < 8) {
                guard++
                // 3.0.707 修复：内容视图实际实例是 DynamicIslandContentView（基类的子类），
                // 用 == 精确匹配基类名永远匹配不上 → 黑罩从来没清掉。放宽为前缀匹配。
                if (up.javaClass.name.startsWith(
                        "miui.systemui.dynamicisland.window.content.DynamicIsland"
                    )
                ) {
                    val container = runCatching {
                        up.javaClass.getMethod("getContainer").apply { isAccessible = true }
                            .invoke(up) as? View
                    }.getOrNull()
                    val mask = runCatching {
                        up.javaClass.getMethod("getMask").apply { isAccessible = true }
                            .invoke(up) as? View
                    }.getOrNull()
                    if (container != null || mask != null) {
                        container?.let(::clearMaskForView)
                        mask?.let(::clearMaskForView)
                        HookLogger.i(TAG, "官方玻璃挂载后已轻量清黑罩（container/mask）")
                        break
                    }
                }
                up = up.parent as? View
            }
        }.onFailure { HookLogger.w(TAG, "清黑罩失败: ${it.message}") }
    }

    /** 轻量清单个 View 的黑底：background=null + 清 blur mode/blend（只这一层，不动生命周期）。 */
    private fun clearMaskForView(view: View) {
        runCatching { view.background = null }
        runCatching {
            val cl = view.javaClass.classLoader ?: return@runCatching
            val blurClass = sequenceOf(
                "miui.systemui.util.MiBlurCompat",
                "miui.util.MiBlurCompat",
            ).mapNotNull { name -> runCatching { cl.loadClass(name) }.getOrNull() }
                .firstOrNull() ?: return@runCatching
            val setBlurMode = blurClass.getDeclaredMethod(
                "setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType,
            )
            val clearBlend = blurClass.getDeclaredMethod(
                "clearMiBackgroundBlendColorCompat", View::class.java,
            )
            runCatching { setBlurMode.invoke(null, view, 0) }
            runCatching { clearBlend.invoke(null, view) }
        }
    }

    /** 视图树诊断：向上 6 层 + 向下 3 层，每层背景/颜色，定位黑块来源（前 12 次）。 */
    private var viewTreeDiagCount = 0
    private fun dumpViewTree(view: View) {
        if (viewTreeDiagCount >= 12) return
        viewTreeDiagCount++
        val sb = StringBuilder("黑块定位视图树（root=${view.javaClass.simpleName}）:\n")
        var up: View? = view
        var d = 0
        while (up != null && d < 6) {
            val bg = up.background
            val bgColor = (bg as? android.graphics.drawable.ColorDrawable)?.color
            sb.append("UP$d ${up.javaClass.simpleName} bg=${bg?.javaClass?.simpleName} " +
                "bgColor=${bgColor?.let { String.format("#%08X", it) }} vis=${up.visibility} " +
                "alpha=${up.alpha}\n")
            up = up.parent as? View
            d++
        }
        fun children(v: View, depth: Int) {
            val vg = v as? android.view.ViewGroup ?: return
            for (i in 0 until vg.childCount) {
                val c = vg.getChildAt(i)
                val bg = c.background
                val bgColor = (bg as? android.graphics.drawable.ColorDrawable)?.color
                sb.append("  ".repeat(depth + 1) + "DOWN$depth ${c.javaClass.simpleName} " +
                    "bg=${bg?.javaClass?.simpleName} " +
                    "bgColor=${bgColor?.let { String.format("#%08X", it) }} vis=${c.visibility} " +
                    "alpha=${c.alpha}\n")
                if (depth < 2) children(c, depth + 1)
            }
        }
        children(view, 0)
        HookLogger.i(TAG, sb.toString())
    }

    /** 挂系统实时模糊（BackgroundBlurDrawable）到状态视图。 */
    private fun attachNativeBlur(stateView: View, config: BlurConfig) {
        synchronized(attachedBlurs) {
            val existing = attachedBlurs[stateView]
            if (existing != null) {
                renderer.update(stateView, existing, config)
                existing.active = true
                return@synchronized
            }
            val prefs = HookEntry.instance?.prefs ?: return
            val bgBitmap = IslandBackgroundImage.load(prefs, RootConstants.KEY_ISLAND_BG_BIG)
            val owned = renderer.create(
                stateView,
                IslandType.BIG,
                config.materialType,
                bgBitmap,
            ) ?: return
            attachedBlurs[stateView] = owned
            owned.active = true
            renderer.update(stateView, owned, config)
            runCatching { stateView.background = owned.drawable }
                .onFailure { HookLogger.e(TAG, "设置岛背景毛玻璃失败: ${it.message}", it) }
            // 3.0.706：挂载成功后轻量清一次黑罩
            clearBlackShade(stateView)
            HookLogger.i(TAG, "毛玻璃已挂载到状态视图：radius=${config.radius} bg=${bgBitmap != null}")
        }
    }

    private fun releaseNativeBlur(view: View) {
        val owned = synchronized(attachedBlurs) { attachedBlurs.remove(view) } ?: return
        runCatching { owned.release() }
        // 还原系统默认背景（对齐毛玻璃释放流程）
        runCatching { view.background = null }
        HookLogger.i(TAG, "系统模糊毛玻璃已释放并还原背景")
    }

    /** 从宿主根 BFS 找胶囊状态视图（资源名含 big_island/small_island/expanded 或类名 ExpandedView）。 */
    @SuppressLint("DiscouragedApi")
    private fun findStateView(root: ViewGroup): View? {
        val deque = ArrayDeque<View>()
        deque.add(root)
        var guard = 0
        while (deque.isNotEmpty() && guard < 600) {
            guard++
            val v = deque.removeFirst()
            val rn = runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull().orEmpty()
            val isStateView = rn.contains("big_island") || rn.contains("small_island") ||
                rn.contains("expanded") || v.javaClass.name.contains("ExpandedView")
            // 只挂可见且有尺寸的状态视图；INVISIBLE 的 expanded（未展开）跳过
            if (isStateView && v.width > 0 && v.height > 0 &&
                v.visibility == View.VISIBLE
            ) return v
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) deque.add(v.getChildAt(i))
            }
        }
        return null
    }

    private fun refreshAll() {
        val prefs = HookEntry.instance?.prefs ?: return
        mainHandler.post { attachBlur(prefs) }
    }
}
