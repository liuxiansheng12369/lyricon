/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.hyper.root.island.hooks

import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandProbeUtils
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandTextHookerSupport
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandTextHookerSupport.TAG
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandViewHelper
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandInjectionReconciler
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandNativeRefreshCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandPresentationCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandReconcileReason
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandRenderPolicy
import com.liuxiansheng.lyricon.hyper.root.island.renderer.BaseIslandRenderer
import com.liuxiansheng.lyricon.hyper.root.island.renderer.IslandContentUpdateCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.structure.IslandSlotStructureInjector
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandStateResolver
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandType
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker

internal object RealIslandHooker {

    /**
     * 1.2.55：屏幕开关租约方案总开关。
     * true = 关屏不用超级岛、锁屏切状态栏歌词、解锁后主动重建（1.2.36~1.2.54 那套）；
     * false = 完全交还系统（当前值），用来单独验证"更新岛视图"这条路。
     */
    const val SCREEN_LEASE_MODE = false

    /** 最近一次系统媒体岛 update 捕获的宿主根 + 包名，供切换超导后主动重新注册宿主。 */
    @Volatile
    var lastMediaHost: ViewGroup? = null
    @Volatile
    var lastMediaPackage: String? = null

    /** 1.1.14：缓存最近一次媒体岛的 DynamicIslandData，重建时用它重新塞回系统。 */
    private var lastMediaData = java.lang.ref.WeakReference<Any>(null)

    /**
     * 1.1.14：锁屏时系统每 4~5 秒对媒体岛调一次 `IslandTemplateFactory.hideView(prop 2)`，
     * 把封面/歌名/歌词三个模块藏掉，亮屏后没人再 show → 大岛小岛全空。
     * 这里对媒体岛这条 hideView **直接不执行**：内容一直挂着，只是锁屏期间窗口不可见，
     * 亮屏时窗口一恢复内容就在，不需要重建。
     */
    class KeepContentHideViewHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val data = chain.args.getOrNull(0)
            val key = runCatching {
                data?.javaClass?.getMethod("getKey")?.invoke(data) as? String
            }.getOrNull()
            if (isMediaIslandKey(key)) {
                logKeepContent("拦下 hideView：$key")
                return null
            }
            return chain.proceed()
        }
    }

    /**
     * 1.1.14：同上，`hideIslandLayout()` 是"锁屏前把整块内容布局收起"的入口，
     * 有媒体岛在播时跳过它，内容布局不被收起。
     */
    class KeepContentHideLayoutHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (lastMediaPackage != null) {
                logKeepContent("拦下 hideIslandLayout")
                return null
            }
            return chain.proceed()
        }
    }

    /**
     * 1.1.15：用缓存的媒体岛数据让系统"重新加一遍岛数据"。
     * 用户实测：打开音乐 App 再退出，岛就正常了 —— 那是系统重新 add 了一次岛数据。
     * 这里等价地调用 `DynamicIslandWindowView.addDynamicIslandData(data,false,true)`，
     * 源码里 z3=true 分支会先 removeIf 同 key 再 add，等于把这份数据重新插进岛里。
     */
    fun requestRebuildMediaIsland(reason: String, force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastRebuildAt < 10000L) return
        val data = lastMediaData.get() ?: lastMediaDataStrong
        if (data == null) {
            HookLogger.w(TAG, "重建岛数据跳过：没有缓存的 DynamicIslandData（$reason）")
            return
        }
        // 1.2.45：优先用 hook 时保住的岛窗口对象。旧写法从 lastMediaHost 往上爬，
        // 锁屏/解锁后那棵树被系统换掉，爬到的已不是 DynamicIslandWindowView
        // → 打不到 addDynamicIslandData = "主动生成"其实是空操作（日志里那条
        // "没找到 addDynamicIslandData(3参)"就是它）。手动切一次能成，是因为走了
        // 设置变更→系统 update 的活路径。
        var windowView: android.view.View? = cachedIslandWindow
        if (windowView == null) {
            var root: android.view.View? = lastMediaHost
            while (root?.parent is android.view.View) {
                root = root.parent as android.view.View
            }
            windowView = root
        }
        val target = windowView ?: run {
            HookLogger.w(TAG, "重建岛数据跳过：没有可用的岛窗口对象（$reason）")
            return
        }
        val method = target.javaClass.methods.firstOrNull {
            it.name == "addDynamicIslandData" && it.parameterCount == 3
        }
        if (method == null) {
            HookLogger.w(TAG, "重建岛数据跳过：没找到 addDynamicIslandData(3参)（$reason）")
            return
        }
        lastRebuildAt = now
        runCatching {
            method.isAccessible = true
            method.invoke(target, data, false, true)
            HookLogger.i(TAG, "已让系统重新挂媒体岛数据（$reason）")
        }.onFailure { e ->
            HookLogger.e(TAG, "重建岛数据失败（$reason）", e)
        }
    }

    /** 1.2.45：把 DynamicIslandWindowView 强引用保住，供解锁后 "主动生成" 使用 */
    private fun rememberIslandWindow(view: android.view.View?) {
        var v: android.view.View? = view
        var found: android.view.View? = null
        while (v != null) {
            if (v.javaClass.name.contains("DynamicIslandWindow")) {
                found = v
                break
            }
            v = v.parent as? android.view.View
        }
        if (found != null && found !== cachedIslandWindow) {
            cachedIslandWindow = found
            HookLogger.i(TAG, "已记住岛窗口对象: ${found.javaClass.simpleName}")
        }
    }

    private var cachedIslandWindow: android.view.View? = null
    private var lastMediaDataStrong: Any? = null

    private var lastRebuildAt = 0L

    /** 1.2.54：记下系统自己调 updateDynamicIslandView 时用的真实宽度，供解锁后原样重放 */
    private var lastSystemUpdateWidth = 0f

    /**
     * 1.2.54：走"更新岛视图"这条路 —— 直接调系统的
     * `DynamicIslandWindowView.updateDynamicIslandView(data,false,width,false)`。
     * 这条是系统自己的完整更新路径（数据→显示决策→show→建树），
     * 和"下一首能出岛""手动来回切能出岛"走的是同一条；
     * 而 addDynamicIslandData 只塞数据、不过显示决策，所以岛不出现。
     */
    fun requestSystemUpdateIslandView(reason: String) {
        val win = cachedIslandWindow
        if (win == null) {
            HookLogger.w(TAG, "更新岛视图跳过：没有岛窗口对象（$reason）")
            return
        }
        val data = lastMediaData.get() ?: lastMediaDataStrong
        if (data == null) {
            HookLogger.w(TAG, "更新岛视图跳过：没有缓存的媒体岛数据（$reason）")
            return
        }
        // 反编译实证（MIUISystemUIPlugin 3.0.x）：
        //   控制器 DynamicIslandWindowViewController.updateDynamicIslandView(data, expanded)
        //     → requestMaxWidth → getIslandTemplate → handleDismissIsland → view.updateDynamicIslandView(data, expanded, true)
        //   窗口 DynamicIslandWindowView.updateDynamicIslandView(data, expanded, reInflated)（3 参）
        //     → 协程里 showImmerseIsland + updateDynamicIslandViewSuspend（真正建树/显示）
        // 所以先打控制器那层（最完整），没有再退窗口 3 参那层。
        runCatching {
            val controller = IslandTextHookerSupport.callNoArgMethodResult(win, "getWindowViewController")
            if (controller != null) {
                val m = findUpdateIslandViewMethod(controller.javaClass, 2)
                if (m != null) {
                    m.isAccessible = true
                    m.invoke(controller, data, false)
                    HookLogger.i(TAG, "已让系统走完整更新岛视图（控制器层，$reason）")
                    // 1.2.58：系统重建后岛体是"系统的原始媒体岛"，我们注入的歌词层没了
                    //（真机表现为胶囊里显示系统默认"暂无歌词/请您欣赏"）。
                    // 这里补一次歌词恢复链：把当前歌词重新注进新树。延迟一点等新 contentView 建好。
                    lyricRestoreHandler.postDelayed({ scheduleLyricRestore() }, 300L)
                    return
                }
            }
            val m3 = findUpdateIslandViewMethod(win.javaClass, 3)
            if (m3 != null) {
                m3.isAccessible = true
                m3.invoke(win, data, false, true)
                HookLogger.i(TAG, "已让系统走完整更新岛视图（窗口层 3 参，$reason）")
                lyricRestoreHandler.postDelayed({ scheduleLyricRestore() }, 300L)
                return
            }
            HookLogger.w(TAG, "更新岛视图跳过：控制器/窗口两层都没找到 updateDynamicIslandView（$reason）")
        }.onFailure { e ->
            HookLogger.e(TAG, "更新岛视图失败（$reason）", e)
        }
    }

    private fun findUpdateIslandViewMethod(clazz: Class<*>, paramCount: Int): java.lang.reflect.Method? {
        var c: Class<*>? = clazz
        while (c != null) {
            val found = c.declaredMethods.firstOrNull {
                it.name == "updateDynamicIslandView" && it.parameterCount == paramCount
            }
            if (found != null) return found
            c = c.superclass
        }
        return null
    }

    /**
     * 1.2.54：hook 系统自己的 updateDynamicIslandView，记账：
     * 岛窗口对象、媒体数据、真实宽度。解锁后就按这份"系统原话"重放。
     */
    class UpdateDynamicIslandViewHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            runCatching {
                val winView = chain.thisObject as? android.view.View ?: return@runCatching
                if (!winView.javaClass.name.contains("DynamicIslandWindow")) return@runCatching
                rememberIslandWindow(winView)
                (chain.args.getOrNull(2) as? Number)?.toFloat()?.let { w ->
                    if (w > 0f) lastSystemUpdateWidth = w
                }
                val data = chain.args.getOrNull(0)
                if (data != null) {
                    val owner = IslandPresentationCoordinator.ownerEvidence(data)
                    if (owner is IslandRenderPolicy.OwnerEvidence.Media) {
                        lastMediaData = java.lang.ref.WeakReference(data)
                        lastMediaDataStrong = data
                        lastMediaPackage = owner.packageName
                    }
                }
            }.onFailure { e ->
                HookLogger.e(TAG, "记录系统岛更新参数失败", e)
            }
            return chain.proceed()
        }
    }

    private fun isMediaIslandKey(key: String?): Boolean {
        if (key == null) return false
        val pkg = lastMediaPackage
        if (pkg != null && key.contains(pkg)) return true
        return key.contains("music", ignoreCase = true)
    }

    private var lastKeepLogAt = 0L

    private fun logKeepContent(action: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastKeepLogAt < 10000L) return
        lastKeepLogAt = now
        HookLogger.i(TAG, "保内容：$action")
    }

    class UpdateBigIslandViewHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            var owner: IslandRenderPolicy.OwnerEvidence? = null
            runCatching {
                val contentView = chain.thisObject as? ViewGroup ?: return@runCatching
                val data = chain.args.getOrNull(0)
                owner = IslandPresentationCoordinator.ownerEvidence(data)
                val mediaOwner = owner as? IslandRenderPolicy.OwnerEvidence.Media
                if (mediaOwner != null) {
                    lastMediaHost = contentView
                    lastMediaPackage = mediaOwner.packageName
                    (data as? Any)?.let {
                        lastMediaData = java.lang.ref.WeakReference(it)
                        lastMediaDataStrong = it
                    }
                    rememberIslandWindow(contentView)
                    IslandPresentationCoordinator.onRealBeforeSystemUpdate(
                        contentView,
                        mediaOwner
                    )
                }
            }.onFailure { e ->
                HookLogger.e(TAG, "预恢复歌词视图失败", e)
            }

            val result = chain.proceed()
            // The old two-argument API returns Boolean directly. In the newer suspend API,
            // the extra continuation argument returns a non-Boolean marker while creation waits.
            if (chain.args.size >= 3 && result !is Boolean) return result

            runCatching {
                val contentView = chain.thisObject as? ViewGroup ?: return@runCatching
                val argumentData = chain.args.getOrNull(0)
                // Newer SystemUI versions resume this suspend method by invoking it again with
                // a null data argument. Resolve the data held by the content view before treating
                // the call as a real island clear.
                val argumentOwner = owner
                    ?: IslandPresentationCoordinator.ownerEvidence(argumentData)
                val fallbackData = if (
                    argumentData == null ||
                    argumentOwner == IslandRenderPolicy.OwnerEvidence.Pending
                ) {
                    IslandTextHookerSupport.extractIslandDataFromContentOrReal(contentView)
                } else {
                    null
                }
                val fallbackOwner = fallbackData?.let(
                    IslandPresentationCoordinator::ownerEvidence
                )
                val resolvedOwner = when {
                    argumentOwner is IslandRenderPolicy.OwnerEvidence.Media -> argumentOwner
                    fallbackOwner is IslandRenderPolicy.OwnerEvidence.Media -> fallbackOwner
                    argumentData != null -> argumentOwner
                    fallbackData != null -> fallbackOwner
                        ?: IslandRenderPolicy.OwnerEvidence.Pending
                    // 3.0.771：数据瞬时提取不到但该宿主之前是媒体岛（lastMediaHost）
                    // → 降级 Pending 不清。此前判 NotMedia → removeRealHost 清岛
                    // → 宿主 unregister、歌词回不来（锁屏后随机清岛根因）。
                    // 真非媒体岛数据稳定时会带 argumentData 继续走 NotMedia 分支，不受影响。
                    contentView === lastMediaHost -> IslandRenderPolicy.OwnerEvidence.Pending

                    else -> IslandRenderPolicy.OwnerEvidence.NotMedia
                }
                if (resolvedOwner is IslandRenderPolicy.OwnerEvidence.Media) {
                    lastMediaHost = contentView
                    lastMediaPackage = resolvedOwner.packageName
                }
                IslandPresentationCoordinator.onRealSystemUpdateComplete(
                    root = contentView,
                    owner = resolvedOwner
                )
                IslandNativeRefreshCoordinator.onSystemUpdateComplete(contentView)
            }.onFailure { e ->
                HookLogger.e(TAG, "注入歌词视图失败", e)
            }

            return result
        }
    }

    class WidthUpdateVisibilityGuardHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
                val contentView = chain.thisObject as? ViewGroup ?: return@runCatching
                // 无条件纠偏（与超级岛总开关 gate 无关）：
                // 系统重算大岛宽度（updateBigIslandViewWidth）后，会在同步调用栈里继续走
                // UpdateDynamicIslandWidth 事件链并把背景层置 GONE（checkError 异常态）。
                // 因此这里 postDelayed 延迟，等事件链完成背景置 GONE 之后再检查并恢复，
                // 消除"背景GONE+窗口VISIBLE"的大岛透明。
                // 注意：不依赖 isShown/windowVisible（背景 GONE 时视图树父节点可能同时 GONE
                // 导致 isShown=false 误跳过恢复）；媒体在播实时查 MediaSession（不依赖模块
                // gate/播放记录，超级岛关闭时也生效）。
                contentView.postDelayed({
                    runCatching {
                        // 用户主动开透明模式时跳过纠偏（背景透明是用户要的效果）
                        if (isTransparentModeEnabled()) return@runCatching
                        if (!isMediaPlaying(contentView.context)) return@runCatching
                        if (IslandViewHelper.isIslandBackgroundVisible(contentView)) return@runCatching
                        IslandViewHelper.restoreIslandBackground(contentView)
                        IslandViewHelper.setInjectedLyricVisibility(
                            contentView, android.view.View.VISIBLE
                        )
                        HookLogger.i(
                            TAG,
                            "宽度重算后检测到 背景GONE+媒体在播 → 已恢复背景（防透明纠偏）"
                        )
                    }.onFailure { e ->
                        HookLogger.e(TAG, "宽度重算背景恢复失败", e)
                    }
                }, 50L)
            }.onFailure { e ->
                HookLogger.e(TAG, "宽度重算背景恢复失败", e)
            }
            return result
        }
    }

    /**
     * 实时查询系统媒体会话是否在播（不依赖模块自己的播放状态记录——
     * 超级岛总开关关闭时模块不记录播放态，但纠偏仍须生效）。
     */
    fun isMediaPlaying(context: android.content.Context?): Boolean {
        if (context == null) return false
        return runCatching {
            val msm = context.getSystemService(
                android.content.Context.MEDIA_SESSION_SERVICE
            ) as? android.media.session.MediaSessionManager ?: return false
            msm.getActiveSessions(null).any { session ->
                runCatching {
                    session.playbackState?.state ==
                            android.media.session.PlaybackState.STATE_PLAYING
                }.getOrDefault(false)
            }
        }.getOrDefault(false)
    }

    /**
     * 用户是否主动开启了"岛透明模式"。
     * 透明模式开启时，背景保持透明是用户要的效果，所有防透明纠偏必须跳过。
     * 键与将来设置页透明开关共用（RootConstants.KEY_ISLAND_TRANSPARENT_ENABLED）。
     */
    fun isTransparentModeEnabled(): Boolean {
        return runCatching {
            HookEntry.instance?.prefs?.getBoolean(
                RootConstants.KEY_ISLAND_TRANSPARENT_ENABLED,
                RootConstants.DEFAULT_ISLAND_TRANSPARENT_ENABLED
            ) == true
        }.getOrDefault(false)
    }

    /**
     * 监听系统把岛背景层（DynamicIslandBackgroundView）置 GONE 的动作——
     * 无论触发来源（宽度重算事件/系统隐藏流程/其他），只要最终落点是
     * "背景GONE + 窗口可见 + 媒体在播"，就延迟一帧把背景恢复 VISIBLE，
     * 消除大岛透明。与超级岛总开关 gate 无关。
     *
     * 3.0.701（锁屏恢复歌词）：OS4 锁屏/亮屏恢复岛走 IslandTempHiddenChanged +
     * UpdateDynamicIslandWidth，**不走 showIslandLayout / UpdateBigIslandView**，
     * 歌词恢复 reconcile 链不触发 → 锁屏后歌词停在隐藏态（重进播放器才回来）。
     * 背景 setVisibility 是锁屏/亮屏必经之路：GONE=防透明纠偏（原有）；
     * VISIBLE=亮屏/解锁/岛恢复 → 媒体在播时主动触发歌词恢复 reconcile。
     */
    class BackgroundVisibilityGuardHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            runCatching {
                val bgView = chain.thisObject as? android.view.View ?: return@runCatching
                val newVisibility = chain.args.getOrNull(0) as? Int ?: return@runCatching
                val parent = bgView.parent as? ViewGroup ?: return@runCatching
                lastBgView = bgView
                lastParent = parent
                startWatchdog()
                if (newVisibility == android.view.View.GONE) {
                    // 【事前拦截】透明条件：窗口可见 + 媒体在播 + 未开透明模式 →
                    // 禁止系统把背景隐藏（返回 null 跳过原方法，背景保持 VISIBLE）。
                    // 窗口真隐藏（息屏/锁屏）时放行，系统正常隐藏。
                    // ★ 3.0.893：状态栏隐藏超级岛标记置位时放行（隐藏是我们要的效果，
                    //   不能拦）——否则"隐藏超级岛"压 GONE 被这里拦成保持 VISIBLE → 玻璃清不掉。
                    val forcedHidden = IslandInjectionReconciler.statusBarLyricIslandForcedHidden
                    if (!isTransparentModeEnabled() && !forcedHidden) {
                        val windowVisible = parent.visibility == android.view.View.VISIBLE &&
                                parent.isShown
                        if (windowVisible && isMediaPlaying(parent.context)) {
                            HookLogger.i(
                                TAG,
                                "拦截系统隐藏背景：窗口可见+媒体在播 → 背景保持VISIBLE"
                            )
                            return null
                        }
                    }
                }
            }.onFailure { e ->
                HookLogger.e(TAG, "背景状态监听拦截失败", e)
            }
            val result = chain.proceed()
            runCatching {
                val bgView = chain.thisObject as? android.view.View ?: return@runCatching
                val newVisibility = chain.args.getOrNull(0) as? Int ?: return@runCatching
                val parent = bgView.parent as? ViewGroup ?: return@runCatching
                if (newVisibility == android.view.View.GONE) {
                    // ★ 状态栏隐藏标记置位：放行 GONE（隐藏是目的），跳过"窗口可见+媒体在播→恢复"。
                    if (IslandInjectionReconciler.statusBarLyricIslandForcedHidden) {
                        return result
                    }
                    // 原有：系统正常隐藏（息屏/锁屏/收起）时窗口同步隐藏，靠"窗口可见"条件挡住；
                    // 只有窗口仍可见（异常透明）才恢复。
                    parent.postDelayed({
                        runCatching {
                            // 用户主动开透明模式时跳过纠偏（背景透明是用户要的效果）
                            if (isTransparentModeEnabled()) return@runCatching
                            val windowVisible = parent.visibility == android.view.View.VISIBLE &&
                                    parent.isShown
                            if (!windowVisible) return@runCatching
                            if (!isMediaPlaying(parent.context)) return@runCatching
                            if (bgView.visibility == android.view.View.VISIBLE) return@runCatching
                            bgView.visibility = android.view.View.VISIBLE
                            HookLogger.i(
                                TAG,
                                "背景被系统置GONE但窗口可见+媒体在播 → 已恢复背景（防透明监听）"
                            )
                        }.onFailure { e ->
                            HookLogger.e(TAG, "背景状态监听恢复失败", e)
                        }
                    }, 50L)
                } else if (newVisibility == android.view.View.VISIBLE) {
                    // 3.0.701 新增：背景从 GONE/INVISIBLE 恢复 VISIBLE（亮屏/解锁/岛恢复）。
                    // 系统走 IslandTempHiddenChanged + UpdateDynamicIslandWidth 恢复岛，
                    // 不触发 showIslandLayout/UpdateBigIslandView → 歌词 reconcile 缺失 →
                    // 歌词停留 GONE。这里补触发：媒体在播 + 找到内容视图 → 恢复歌词 + reconcile。
                    // ★ 3.0.893：状态栏隐藏标记置位时系统恢复 VISIBLE → 强制压回 GONE
                    //   （玻璃岛清不掉元凶：隐藏后系统/守卫把岛拉回可见）。
                    if (IslandInjectionReconciler.statusBarLyricIslandForcedHidden) {
                        // ★3.0.1100：音乐岛已被源头拦截（状态栏模式下根本建不出来）时，
                        // 不再把这张共享背景强制压回 GONE——它属于"整块岛窗口"，
                        // 压它会把充电岛/其他应用岛一起藏掉（用户实测：充电时岛没了）。
                        if (MediaIslandCreationGate.isStatusBarIslandMode()) {
                            return result
                        }
                        parent.postDelayed({
                            runCatching {
                                if (bgView.visibility == android.view.View.VISIBLE) {
                                    bgView.visibility = android.view.View.GONE
                                    HookLogger.i(
                                        TAG,
                                        "状态栏隐藏期间系统恢复背景VISIBLE → 已强制压回GONE"
                                    )
                                }
                            }.onFailure { e ->
                                HookLogger.e(TAG, "状态栏隐藏强制GONE失败", e)
                            }
                        }, 30L)
                        return result
                    }
                    parent.postDelayed({
                        runCatching {
                            if (!isMediaPlaying(parent.context)) return@runCatching
                            val contentView = IslandViewHelper.findViewByName(
                                parent, "island_content"
                            ) as? ViewGroup ?: return@runCatching
                            val currentData = IslandProbeUtils.getCurrentIslandData(contentView)
                            val owner = IslandPresentationCoordinator.ownerEvidence(currentData)
                            if (owner !is IslandRenderPolicy.OwnerEvidence.Media) return@runCatching
                            IslandViewHelper.setInjectedLyricVisibility(
                                contentView, android.view.View.VISIBLE
                            )
                            IslandPresentationCoordinator.onRealVisibilityChanged(contentView, owner)
                            // 3.0.746+：锁屏/亮屏重建媒体岛后，歌词内容可能被系统清空，
                            // reconcile 只恢复可见性、不重注入歌词文字。这里补全量重注入：
                            // 重注册宿主 + 直注入。锁屏重建时机不确定（宿主可能还没就绪），
                            // 防重启动多段重试（最多 3 次），确保宿主重建后注入成功。
                            scheduleLyricRestore()
                            HookLogger.i(
                                TAG,
                                "背景恢复VISIBLE+媒体在播 → 已触发歌词恢复 reconcile+直注入"
                            )
                        }.onFailure { e ->
                            HookLogger.e(TAG, "背景恢复后歌词恢复失败", e)
                        }
                    }, 80L)
                }
            }.onFailure { e ->
                HookLogger.e(TAG, "背景状态监听恢复失败", e)
            }
            return result
        }

        // ---- 周期兜底（3.0.753+）：开屏后若系统不再发背景事件（背景仍 GONE），
        // 窗口可见+媒体在播时每 2 秒检查一次，强制恢复背景+歌词，杜绝"时间长了透明"。----
        private var lastBgView: android.view.View? = null
        private var lastParent: ViewGroup? = null
        private var watchdogStarted = false
        private val watchdogHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private val watchdogRunnable = Runnable { watchdogCheck() }

        private fun startWatchdog() {
            if (watchdogStarted) return
            watchdogStarted = true
            watchdogHandler.postDelayed(watchdogRunnable, 2000L)
        }

        private fun watchdogCheck() {
            try {
                val bg = lastBgView
                val parent = lastParent
                if (bg != null && parent != null && !isTransparentModeEnabled()) {
                    val windowVisible = parent.visibility == android.view.View.VISIBLE &&
                            parent.isShown
                    if (windowVisible && isMediaPlaying(bg.context)) {
                        if (bg.visibility != android.view.View.VISIBLE) {
                            bg.visibility = android.view.View.VISIBLE
                            HookLogger.i(
                                TAG,
                                "周期兜底：窗口可见+媒体在播+背景非VISIBLE → 恢复背景"
                            )
                        }
                        // 同步保证歌词显示层 VISIBLE；歌词活着则不再拉恢复链
                        // （恢复链含结构协调，每2秒跑=反复 remove/add → 系统清 → 自激循环）
                        val contentView = IslandViewHelper.findViewByName(
                            parent, "island_content"
                        ) as? ViewGroup
                        if (contentView != null) {
                            IslandViewHelper.setInjectedLyricVisibility(
                                contentView, android.view.View.VISIBLE
                            )
                            val lyricAlive = runCatching {
                                val prefs = HookEntry.instance?.prefs ?: return@runCatching false
                                val config = IslandSlotRuntimeConfig.from(prefs)
                                val needLeft = config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE
                                val needRight = config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE
                                val left = contentView.findViewWithTag<android.view.View>(
                                    IslandProbeUtils.LEFT_TEST_VIEW_TAG
                                )
                                val right = contentView.findViewWithTag<android.view.View>(
                                    IslandProbeUtils.RIGHT_TEST_VIEW_TAG
                                )
                                (!needLeft || (left?.isAttachedToWindow == true && left.parent != null)) &&
                                    (!needRight || (right?.isAttachedToWindow == true && right.parent != null))
                            }.getOrElse { false }
                            if (lyricAlive) {
                                HookLogger.i(TAG, "周期兜底：歌词活着，仅保持VISIBLE，不拉恢复链")
                            } else {
                                scheduleLyricRestore()
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                HookLogger.e(TAG, "周期兜底检查失败", e)
            }
            watchdogHandler.postDelayed(watchdogRunnable, 2000L)
        }
    }

    /**
     * 打断系统 DynamicIslandEventCoordinator.checkError 恢复循环（3.0.754+）。
     *
     * 实锤日志（repro_capture.log 33MB）：系统每 300ms~2s 打印
     * `E/DynamicIslandEventCoordinator checkError true 背景GONE+窗口VISIBLE`，
     * 触发"恢复背景 → hide/清内容 → V3 守卫恢复 → 又报错"死循环；
     * 歌词 View 正是在循环的 hide/清内容阶段被系统从岛容器移除（View removed from parent）。
     * 拦截方式：直接跳过原方法（void），系统永不判定"布局异常"，
     * 循环源头被截断；透明纠偏与歌词恢复由 BackgroundVisibilityGuardHook
     * （事前拦截+事后恢复+2s watchdog）兜底，不依赖系统自愈。
     */
    class CheckErrorBlockHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            return null
        }
    }

    /**
     * 新插件(miui.systemui.plugin 18.3.2.57.0+)黑底拦截（3.0.769+）。
     *
     * 插件全量方法级对比已实证：新版 DynamicIslandWindowView.dispatchUpdateEvent
     * 比老版(18.2.1.88.0)多一段 densityChanged 处理——岛缩回（尺寸/密度变化）时
     * 读 DynamicIslandData.extras 的 "miui.island.densityChanged" 为 true，把 key
     * 记入 islandOrderAppliedKeys 并 setUpdateOrder → 触发岛重建 → 系统垫默认
     * 深色背景（黑底）。老插件没有这段，缩回后不碰背景，所以透明。
     * 用户观察"缩的过程中透明、缩完加层"与此时序吻合。
     *
     * 拦截方式：before 阶段把 data.extras 里的 densityChanged 置 false，跳过重建分支。
     * 老插件无此 key，getBoolean 恒 false，零副作用；新插件下该分支被永久跳过，
     * 缩回不再触发重建垫底，黑底不出现。
     */
    class DensityChangedBlockHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            runCatching {
                val windowView = chain.thisObject ?: return@runCatching
                var params: Any? = null
                for (f in windowView.javaClass.declaredFields) {
                    if (f.type.name.contains("DynamicIslandParams")) {
                        f.isAccessible = true
                        params = f.get(windowView)
                        break
                    }
                }
                if (params == null) return@runCatching
                val getIslandData = params.javaClass.methods.firstOrNull {
                    it.name == "getIslandData" && it.parameterCount == 0
                } ?: return@runCatching
                val data = getIslandData.invoke(params) ?: return@runCatching
                val getExtras = data.javaClass.methods.firstOrNull {
                    it.name == "getExtras" && it.parameterCount == 0
                } ?: return@runCatching
                val extras = getExtras.invoke(data) as? android.os.Bundle ?: return@runCatching
                if (extras.getBoolean("miui.island.densityChanged", false)) {
                    extras.putBoolean("miui.island.densityChanged", false)
                    HookLogger.i(TAG, "拦截新插件 densityChanged 岛重建（防黑底）")
                }
            }.onFailure { e ->
                HookLogger.e(TAG, "densityChanged 拦截异常", e)
            }
            return chain.proceed()
        }
    }

    // ===== 歌词恢复链（3.0.755+）：锁屏/亮屏/岛窗口根重建 attach 共用 =====
    private val lyricRestoreHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lyricRestoreAttempts = 0
    private val lyricRestoreRunnable = Runnable { performLyricRestore() }

    /**
     * 触发歌词恢复 reconcile+直注入。每次触发先取消旧链重置计数，最多重试 3 次，
     * 避免高频触发时多链叠加、无限空转。
     *
     * 不走 refreshActiveIsland —— 其 invalidatePresentation 制造 presentation
     * revision 风暴，高频刷新下每次 post 注入执行时 revision 已过期，
     * reconcile 判 PENDING → 永远不注入 = 歌词永远回不来（此前死循环根因）。
     * 这里直接对当前宿主 reconcile + 注入（reconcile 不传 revision，
     * 只按 isCurrent 判定，revision 风暴不再作废注入）。
     */
    internal fun scheduleLyricRestore() {
        lyricRestoreHandler.removeCallbacks(lyricRestoreRunnable)
        lyricRestoreAttempts = 3
        lyricRestoreHandler.postDelayed(lyricRestoreRunnable, 100L)
    }

    private fun performLyricRestore() {
        runCatching {
            IslandPresentationCoordinator.reRegisterLastMediaHost()
            val pkg = LyriconDataBridge.currentLyricPackageName
                ?.takeIf { it.isNotEmpty() } ?: return@runCatching
            val hosts = IslandPresentationCoordinator.snapshotAllHosts(pkg)
                .ifEmpty { IslandPresentationCoordinator.snapshotAllHosts() }
            hosts.forEach { token ->
                if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)) {
                    IslandPresentationCoordinator.unfreezeHostForFakeTransition(token)
                }
                val cv = token.root
                cv.post {
                    // 幂等（3.0.761+）：歌词 View 还活着 → 只更新文本，绝不碰结构。
                    // reconcile/结构协调会 remove/add → requestLayout → 系统清内容，
                    // 与恢复链互相打架 = "歌词回来又被清"。活着就不动结构，从根上断循环。
                    val prefs = HookEntry.instance?.prefs
                    if (prefs != null) {
                        val config = IslandSlotRuntimeConfig.from(prefs)
                        val needLeft = config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE
                        val needRight = config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE
                        val left = cv.findViewWithTag<android.view.View>(
                            IslandProbeUtils.LEFT_TEST_VIEW_TAG
                        )
                        val right = cv.findViewWithTag<android.view.View>(
                            IslandProbeUtils.RIGHT_TEST_VIEW_TAG
                        )
                        val alive = (!needLeft || viewInShownTree(left, cv) &&
                            (left as? com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView)?.line != null) &&
                            (!needRight || viewInShownTree(right, cv) &&
                                (right as? com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView)?.line != null)
                        if (alive) {
                            IslandViewHelper.setInjectedLyricVisibility(
                                cv, android.view.View.VISIBLE
                            )
                            IslandContentUpdateCoordinator.updateContentForView(
                                view = cv,
                                packageName = pkg,
                                prefs = prefs,
                                config = config,
                                playbackActive = true
                            )
                            HookLogger.i(TAG, "歌词活着：仅刷新文本，不碰结构（幂等）")
                            return@post
                        }
                    }
                    val result = IslandPresentationCoordinator.reconcileRegisteredHost(
                        token, IslandReconcileReason.STABLE_REFRESH
                    )
                    if (!result.isTarget) return@post
                    IslandViewHelper.setInjectedLyricVisibility(cv, android.view.View.VISIBLE)
                    val postPrefs = HookEntry.instance?.prefs ?: return@post
                    IslandContentUpdateCoordinator.updateContentForView(
                        view = cv,
                        packageName = pkg,
                        prefs = postPrefs,
                        config = IslandSlotRuntimeConfig.from(postPrefs),
                        playbackActive = true
                    )
                }
            }
            HookLogger.i(TAG, "歌词恢复直注入 remaining=$lyricRestoreAttempts")
        }.onFailure { e ->
            HookLogger.e(TAG, "歌词恢复直注入失败", e)
        }
        lyricRestoreAttempts -= 1
        if (lyricRestoreAttempts > 0) {
            lyricRestoreHandler.postDelayed(lyricRestoreRunnable, 300L)
        }
    }

    // ===== 自动重建检测（3.0.756+）：检测到"岛窗口活着但歌词 View 被系统清掉"
    // → 自动重注入（等效手动切换超级岛，无需重建窗口） =====
    private val autoRebuildHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoRebuildStarted = false
    private val autoRebuildRunnable = Runnable { autoRebuildCheck() }

    // ===== 1.2.36 租约：关屏交还 / 亮屏重挂 =====
    // 关屏（含锁屏）后停止自动重建：锁屏期间不再"原生刷新"和系统抢状态，
    // 避免抢出一个 alpha=0 的空壳岛；亮屏解锁由 HookEntry 的亮屏监听放行并重挂。
    @Volatile private var leaseSuspended = false

    internal fun suspendForScreenOff() {
        if (leaseSuspended) return
        leaseSuspended = true
        // 1.2.41：关屏时「不使用超级岛」——摘掉注入层、停掉一切待办，岛回系统原样
        releaseHandler.removeCallbacksAndMessages(null)
        lyricRestoreHandler.removeCallbacksAndMessages(null)
        runCatching {
            IslandPresentationCoordinator.snapshotAllHosts().forEach { token ->
                val r = token.root ?: return@forEach
                rememberIslandWindow(r)
                r.post { runCatching { IslandSlotStructureInjector.detachInjected(r) } }
            }
        }
        HookLogger.i(TAG, "租约：关屏 → 摘掉歌词层、交还系统，暂停自动重建")
    }

    internal fun resumeForScreenOn(allowRelease: Boolean = true) {
        val wasSuspended = leaseSuspended
        leaseSuspended = false
        if (wasSuspended) {
            HookLogger.i(
                TAG,
                if (allowRelease) "租约：解锁 → 释放超级岛（主动生成 + 重挂歌词）"
                else "租约：亮屏（仍锁屏）→ 只放行，不生成岛"
            )
        }
        if (!allowRelease) return
        scheduleLyricRestore()
        releaseIslandForScreenOn()
    }

    // ===== 1.2.38 主动释放超级岛（用户方案：关屏不用，亮屏主动生成）=====
    // 等价"状态切换时重新生成岛"：用缓存的 DynamicIslandData 让系统再 add 一遍，
    // 岛不存在/被系统清掉时都能长回来；最多试 2 次，不做轮询。
    private var releaseAttempt = 0
    private val releaseHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val releaseRetryRunnable = Runnable { releaseIfMissing("解锁兜底 2.2s") }

    private fun isIslandAlive(): Boolean {
        val hosts = IslandPresentationCoordinator.snapshotAllHosts()
        return hosts.any { token ->
            val r = token.root ?: return@any false
            r.isAttachedToWindow && IslandViewHelper.isIslandBackgroundVisible(r)
        }
    }

    /**
     * 1.2.39：View 是否在**真正上屏的那棵树**里。
     * 旧判定只看 isAttachedToWindow + visibility，解析锁屏重建后旧树残留的 View 仍然 attached，
     * 于是判"歌词活着"→不做重注入，实际屏上那棵新树里一个我们的 View 都没有 = 空胶囊壳。
     * 这里补三条：同根视图、宽高>0、整条祖先链可见且 alpha 不为 0。
     */
    private fun viewInShownTree(view: android.view.View?, root: android.view.ViewGroup): Boolean {
        if (view == null || !view.isAttachedToWindow) return false
        if (root.rootView !== view.rootView) return false
        if (view.width <= 0 || view.height <= 0) return false
        if (view.visibility != android.view.View.VISIBLE) return false
        var p: android.view.ViewParent? = view.parent
        while (p is android.view.View) {
            if (p.visibility != android.view.View.VISIBLE) return false
            if (p.alpha == 0f) return false
            p = p.parent
        }
        return true
    }

    /** 歌词是否"真的看得见"：在当前宿主的上屏树里、有行数据、宽高>0、祖先链不被隐藏 */
    private fun hasVisibleLyric(): Boolean {
        val hosts = IslandPresentationCoordinator.snapshotAllHosts()
        if (hosts.isEmpty()) return false
        return hosts.any { token ->
            val r = token.root ?: return@any false
            val left = r.findViewWithTag<android.view.View>(IslandProbeUtils.LEFT_TEST_VIEW_TAG)
            val right = r.findViewWithTag<android.view.View>(IslandProbeUtils.RIGHT_TEST_VIEW_TAG)
            fun ok(v: android.view.View?): Boolean =
                viewInShownTree(v, r) &&
                    (v as? com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView)?.line != null
            ok(left) || ok(right)
        }
    }

    private fun forceReinjectAllHosts(reason: String) {
        val hosts = IslandPresentationCoordinator.snapshotAllHosts()
        if (hosts.isEmpty()) {
            HookLogger.w(TAG, "重建歌词层跳过：没有任何岛宿主（$reason）")
            return
        }
        hosts.forEach { token ->
            val r = token.root ?: return@forEach
            r.post {
                runCatching {
                    IslandSlotStructureInjector.forceReinject(r)
                }.onFailure { e ->
                    HookLogger.e(TAG, "重建歌词层失败（$reason）", e)
                }
            }
        }
    }

    private fun releaseIslandForScreenOn() {
        releaseAttempt = 0
        pokedNextAfterUnlock = false
        releaseHandler.removeCallbacksAndMessages(null)
        // 1.2.41：解锁后先把歌词层拆掉、按当前上屏树重建一次（等价手动来回切状态），
        // 再走一次内容刷新；仅在仍看不到时才升级到"重建岛数据"。
        releaseHandler.postDelayed({
            HookLogger.i(
                TAG,
                "释放超级岛：解锁 600ms → 拆除并重建歌词层 lyricOn=${hasVisibleLyric()} islandOn=${isIslandAlive()}"
            )
            forceReinjectAllHosts("解锁 600ms")
            scheduleLyricRestore()
        }, 600L)
        // 1.2.54：解锁后先固定走一次系统"更新岛视图"（不看我们自己的判断，那套判断会骗人），
        // 再在 3.2s 无条件补一次，把"系统显示决策"这一步真正跑起来。
        releaseHandler.postDelayed({
            requestSystemUpdateIslandView("解锁 1.0s 固定更新岛视图")
        }, 1000L)
        // 1.2.49：不再靠"我们自己判岛在不在"——那套判断已经骗过我们好几次
        // （判"歌词已在上屏树"，用户眼睛看到的是没岛）。解锁后固定补一次"下一首"，
        // 借系统完整媒体岛更新流程把岛和歌词一起叫回来。每次解锁一次，关屏复位。
        releaseHandler.postDelayed({
            if (!pokedNextAfterUnlock) {
                pokedNextAfterUnlock = true
                pokeMediaNext("解锁 1.4s 固定补一刀")
            }
        }, 1400L)
        releaseHandler.postDelayed({
            requestSystemUpdateIslandView("解锁 3.2s 补更新岛视图")
        }, 3200L)
        // 岛常常是 2 秒后才被系统建回来：到那时再重建一次歌词层（只按上屏树建，不轮询）
        releaseHandler.postDelayed({ releaseIfMissing("解锁 2.2s") }, 2200L)
        releaseHandler.postDelayed({ releaseIfMissing("解锁 4.5s 兜底") }, 4500L)
    }

    private fun releaseIfMissing(reason: String, force: Boolean = false) {
        if (hasVisibleLyric()) {
            HookLogger.i(TAG, "释放超级岛：歌词已在上屏树（$reason），跳过")
            return
        }
        if (releaseAttempt >= 3) {
            HookLogger.i(TAG, "释放超级岛：重试已达上限 3 次（$reason），停止")
            return
        }
        releaseAttempt++
        val islandOn = isIslandAlive()
        HookLogger.i(
            TAG,
            "释放超级岛：第${releaseAttempt}次重注入（$reason）islandOn=$islandOn"
        )
        forceReinjectAllHosts(reason)
        scheduleLyricRestore()
        // 1.2.44：解锁后岛根本没建回来时（用户实测：要等下一首才出岛），
        // 每一轮都主动让系统把媒体岛重新挂一遍，不能干等下一次媒体更新。
        if (!islandOn || releaseAttempt >= 2) {
            // 1.2.54：先走系统完整的"更新岛视图"，再补一次数据重挂
            requestSystemUpdateIslandView("释放超级岛/$reason")
            requestRebuildMediaIsland("释放超级岛/$reason", force = true)
            IslandNativeRefreshCoordinator.request { scheduleLyricRestore() }
        }
        // 1.2.47：数据也塞了、壳还是没有 → 播一次"下一首"。
        // 1.2.49：改成解锁 1.4s 固定执行一次，不再等这里的条件（条件判断会骗人）。
        // 用户实测：下一首来时系统会跑完整媒体岛更新流程，岛立刻回来；
        // 我们手动塞数据复刻不了那条路径，只能借这一步把系统叫醒。
        if (!islandOn && releaseAttempt == 2 && !pokedNextAfterUnlock) {
            pokedNextAfterUnlock = true
            pokeMediaNext("解锁后岛没回来")
        }
    }

    /** 1.2.51：借"下一首"触发系统完整媒体岛更新流程（岛靠这一步回来） */
    private fun pokeMediaNext(reason: String) {
        // 三条路依次试，前一条不通就换下一条：
        // ① AudioManager.dispatchMediaKeyEvent → SystemUI 里 MediaSessionLegacyHelper 的静态 context 是 null，NPE（1.2.49）
        // ② MediaSession.skipToNext → 报 "packageName does not belong to the calling uid"（1.2.50）
        // ③ InputManager.injectInputEvent 注入 MEDIA_NEXT 按键；不行再退回 su -c input keyevent 87
        runCatching {
            val ctx = cachedIslandWindow?.context
                ?: lastMediaHost?.context
                ?: IslandPresentationCoordinator.snapshotAllHosts().firstNotNullOfOrNull { it.root?.context }
                ?: return

            // ③-a 直接注入按键（隐藏 API，全反射调）
            runCatching {
                val imClass = Class.forName("android.hardware.input.InputManager")
                val im = imClass.getMethod("getInstance").invoke(null)
                val inject = imClass.getMethod(
                    "injectInputEvent",
                    android.view.InputEvent::class.java,
                    Int::class.javaPrimitiveType
                )
                val mode = imClass.getField("INJECT_INPUT_EVENT_MODE_ASYNC").getInt(null)
                inject.invoke(
                    im,
                    android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                    ),
                    mode
                )
                inject.invoke(
                    im,
                    android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_UP,
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                    ),
                    mode
                )
                HookLogger.i(TAG, "解锁后岛没回来 → 自动切下一首（注入按键，$reason）")
                return
            }.onFailure { e ->
                HookLogger.w(TAG, "注入 MEDIA_NEXT 失败，改用 su（$reason）：${e.javaClass.simpleName}")
            }

            // ③-b 退回 AudioManager 派发媒体键。SystemUI 里 MediaSessionLegacyHelper 的静态
            // context 是 null → 直接 NPE（1.2.49）。先反射把它填上，再派发就通了。
            runCatching {
                val helperClass = Class.forName("android.media.session.MediaSessionLegacyHelper")
                runCatching {
                    val f = helperClass.getDeclaredField("mContext")
                    f.isAccessible = true
                    f.set(null, ctx.applicationContext)
                }
                val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE)
                    as? android.media.AudioManager ?: return
                am.dispatchMediaKeyEvent(
                    android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                    )
                )
                am.dispatchMediaKeyEvent(
                    android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_UP,
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                    )
                )
                HookLogger.i(TAG, "解锁后岛没回来 → 自动切下一首（AudioManager 派发，$reason）")
                return
            }.onFailure { e ->
                HookLogger.w(TAG, "AudioManager 派发失败（$reason）：${e.javaClass.simpleName}")
            }

            // ③-c 最后退回 su 命令
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 87"))
            p.waitFor()
            HookLogger.i(TAG, "解锁后岛没回来 → 自动切下一首（su input，$reason）")
        }.onFailure { e ->
            HookLogger.e(TAG, "自动切下一首失败（$reason）", e)
        }
    }

    private var pokedNextAfterUnlock = false

    internal fun startAutoRebuildWatchdog() {
        if (autoRebuildStarted) return
        autoRebuildStarted = true
        scheduleNextCheck()
    }

    // ===== 1.2.37 防风暴（修「解锁卡住」）=====
    // 现象：日志里 `自动重建检查` 每秒跑 1100~1500 次（正常 1.25 次/秒），14 秒写出 34MB 日志，
    // SystemUI 堆 268MB 被打满 → OutOfMemoryError → 进程崩 → 解锁时屏幕卡住。
    // 触发链：亮屏重挂后歌词 View 仍 vis=0 / 大岛 alpha=0.0 → 每轮都判成"内容丢了"
    // → 调原生刷新 → 刷新又引来下一轮检查 → 正反馈。
    // 三重刹车：① 队列去重 + 700ms 最小间隔；② 原生刷新 3s 冷却；③ 诊断日志按状态变化/5s 节流。

    private var lastCheckStamp = 0L
    private var lastNativeRefreshStamp = 0L
    private val throttledLogStamps = HashMap<String, Long>()
    private val throttledLogTexts = HashMap<String, String>()

    private fun scheduleNextCheck() {
        autoRebuildHandler.removeCallbacks(autoRebuildRunnable)
        autoRebuildHandler.postDelayed(autoRebuildRunnable, 800L)
    }

    private fun logThrottled(key: String, msg: String, intervalMs: Long = 5000L) {
        val now = android.os.SystemClock.uptimeMillis()
        val lastText = throttledLogTexts[key]
        val lastStamp = throttledLogStamps[key] ?: 0L
        if (msg == lastText && now - lastStamp < intervalMs) return
        throttledLogTexts[key] = msg
        throttledLogStamps[key] = now
        HookLogger.i(TAG, msg)
    }

    private fun findIslandViewByClass(root: ViewGroup, keyword: String): android.view.View? {
        val stack = ArrayDeque<ViewGroup>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val group = stack.removeFirst()
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child.javaClass.simpleName.contains(keyword)) return child
                if (child is ViewGroup) stack.add(child)
            }
        }
        return null
    }

    private fun findRealContentView(root: ViewGroup): Any? {
        // host.root 自身就是 DynamicIslandContentView（自动重建检查 root=DynamicIslandContentView），
        // 先判断自身，再 BFS 子节点兜底。
        if (root.javaClass.name ==
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
        ) return root
        val stack = ArrayDeque<ViewGroup>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val group = stack.removeFirst()
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child.javaClass.name ==
                    "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
                ) return child
                if (child is ViewGroup) stack.add(child)
            }
        }
        return null
    }

    private fun autoRebuildCheck() {
        if (leaseSuspended) {
            scheduleNextCheck()
            return
        }
        val stamp = android.os.SystemClock.uptimeMillis()
        if (stamp - lastCheckStamp < 700L) {
            // 队列里出现了重复/提前的唤起：直接顺延，不再做一次全树扫描
            scheduleNextCheck()
            return
        }
        lastCheckStamp = stamp
        runCatching {
            val pkg = LyriconDataBridge.currentLyricPackageName
                ?.takeIf { it.isNotEmpty() }
            val allHosts = IslandPresentationCoordinator.snapshotAllHosts()
            logThrottled(
                "scan",
                "自动重建检查: pkg=$pkg allHosts=${allHosts.size}"
            )
            if (pkg == null) return@runCatching
            val prefs = HookEntry.instance?.prefs ?: return@runCatching
            val config = IslandSlotRuntimeConfig.from(prefs)
            val needLeft = config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE
            val needRight = config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE
            if (!needLeft && !needRight) return@runCatching
            val hosts = IslandPresentationCoordinator.snapshotAllHosts(pkg)
                .ifEmpty { allHosts }
            logThrottled(
                "hosts",
                "自动重建检查: pkgHosts=${hosts.size} needLeft=$needLeft needRight=$needRight"
            )
            for (token in hosts) {
                val root = token.root ?: continue
                if (!root.isAttachedToWindow) {
                    logThrottled("root", "自动重建检查: root未attach 跳过")
                    continue
                }
                val bgVisible = IslandViewHelper.isIslandBackgroundVisible(root)
                val left = root.findViewWithTag<android.view.View>(IslandProbeUtils.LEFT_TEST_VIEW_TAG)
                val right = root.findViewWithTag<android.view.View>(IslandProbeUtils.RIGHT_TEST_VIEW_TAG)
                // 3.0.767+：内容活性——View 活着+VISIBLE 但歌词行数据（line）为 null
                // （内容被系统清空，只剩空壳 View）也算"丢"，触发等效切换重注入。
                val leftLineAlive = (left as? com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView)
                    ?.line != null
                val rightLineAlive = (right as? com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView)
                    ?.line != null
                // 3.0.764+：alive 判定必须含 VISIBLE——歌词 View 被置 GONE/INVISIBLE 时
                // isAttachedToWindow 仍为 true（View 还挂在树上），旧判定误判"活着"
                // → 不触发重注入，但用户看不到歌词（"没了"）。
                val leftAlive = needLeft && viewInShownTree(left, root) && leftLineAlive
                val rightAlive = needRight && viewInShownTree(right, root) && rightLineAlive
                logThrottled(
                    "detail",
                    "自动重建检查: root=${root.javaClass.simpleName} bgVisible=$bgVisible " +
                        "leftAttached=${left?.isAttachedToWindow == true} leftVis=${left?.visibility} leftLine=$leftLineAlive leftAlive=$leftAlive " +
                        "rightAttached=${right?.isAttachedToWindow == true} rightVis=${right?.visibility} rightLine=$rightLineAlive rightAlive=$rightAlive"
                )
                if (!bgVisible) continue
                // ===== 大岛（BigIslandView/ExpandedView）内容活性检查（3.0.886 修正）=====
                // 大岛=系统媒体信息界面（上一曲/下一曲/封面/进度条），不是 Lyricon 歌词注入层——
                // 3.0.883-885 误用 Lyricon tag 判活性（大岛本来就无 tag）→ 每 800ms 误触发。
                // 改为容器内容判定：大岛容器内有无可见内容子视图。
                var bigAlive = true
                var bigAlphaZero = false
                runCatching {
                    val contentView = findRealContentView(root)
                    // —— 3.0.886 大岛诊断：确认 getter 是否可用 + 全树扫描大岛/展开类视图 ——
                    val cvCls = contentView?.javaClass?.simpleName ?: "null"
                    val hasBigGetter = contentView?.javaClass?.methods?.any {
                        it.name == "getBigIslandView"
                    } == true
                    val hasExpandGetter = contentView?.javaClass?.methods?.any {
                        it.name == "getExpandedView"
                    } == true
                    val bigView = contentView?.let {
                        IslandStateResolver.concreteView(it, IslandType.BIG)
                    }
                    val expandView = contentView?.let {
                        IslandStateResolver.concreteView(it, IslandType.EXPAND)
                    }
                    fun vDesc(v: android.view.View?): String = v?.let {
                        "vis=${it.visibility} attach=${it.isAttachedToWindow} alpha=${it.alpha} c=${(it as? ViewGroup)?.childCount}"
                    } ?: "null"
                    val treeBig = findIslandViewByClass(root, "BigIslandView")
                    val treeExpand = findIslandViewByClass(root, "ExpandedView")
                    val activeBig = sequenceOf(
                        expandView, bigView, treeExpand, treeBig
                    ).firstOrNull { it?.isAttachedToWindow == true && it.visibility == android.view.View.VISIBLE } as? ViewGroup
                    if (activeBig != null) {
                        // 容器内容活性：存在 VISIBLE 且有内容的子视图（空壳时内容卡消失/变空）
                        val childrenDesc = (0 until activeBig.childCount).joinToString(";") { i ->
                            val c = activeBig.getChildAt(i)
                            val childTag = (c.tag as? String) ?: "-"
                            "${c.javaClass.simpleName}#$childTag(vis=${c.visibility} c=${(c as? ViewGroup)?.childCount})"
                        }
                        val hasContentChild = (0 until activeBig.childCount).any { i ->
                            val c = activeBig.getChildAt(i)
                            c.visibility == android.view.View.VISIBLE &&
                                (c !is ViewGroup || c.childCount > 0)
                        }
                        // 1.2.37：alpha=0.0 是系统自己的隐藏手段（锁屏/收起），不是内容丢失。
                        // 旧逻辑把它当"空壳"→ 每轮触发原生刷新 → 与系统抢状态 → 检查风暴 → OOM。
                        bigAlphaZero = activeBig.alpha == 0f
                        bigAlive = hasContentChild || bigAlphaZero
                        logThrottled(
                            "big",
                            "大岛内容检查: cv=$cvCls hasBigGetter=$hasBigGetter hasExpandGetter=$hasExpandGetter " +
                                "big=${vDesc(bigView)} expand=${vDesc(expandView)} " +
                                "active=${activeBig.javaClass.simpleName} children=[$childrenDesc] " +
                                "hasContent=$hasContentChild alphaZero=$bigAlphaZero alive=$bigAlive"
                        )
                    } else {
                        logThrottled("big", "大岛内容检查: 无展开大岛（big/expand 不可见）")
                    }
                }.onFailure { e ->
                    HookLogger.e(TAG, "大岛内容检查失败", e)
                }
                if (leftAlive && rightAlive && bigAlive) continue
                // 1.2.37：系统整层隐藏（alpha=0 / 内容视图不可见）时不要抢刷新
                if (bigAlphaZero) continue
                // 1.2.37：原生刷新冷却 3s，杜绝"刷新→再判丢→再刷新"的正反馈
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastNativeRefreshStamp < 3000L) continue
                lastNativeRefreshStamp = now
                HookLogger.i(
                    TAG,
                    "检测到歌词 View 被系统清（left=${left?.isAttachedToWindow == true} right=${right?.isAttachedToWindow == true} bigAlive=$bigAlive）→ 触发原生刷新（等效切换超级岛）"
                )
                // 3.0.762+：等效切换重放——调系统 updateDynamicIslandView 重走媒体岛更新路径
                // （切换时系统做的事：清旧档位树→建新树→发 showIslandLayout/updateBigIslandView 事件），
                // 系统重建后注入 hook 自动在新树重注入歌词；完成后兜底一次幂等恢复。
                IslandNativeRefreshCoordinator.request { root ->
                    HookLogger.i(TAG, "原生刷新完成（等效切换），兜底幂等恢复 remaining=$lyricRestoreAttempts")
                    scheduleLyricRestore()
                }
                return@runCatching
            }
        }.onFailure { e ->
            HookLogger.e(TAG, "自动重建检测失败", e)
        }
        // 1.2.37：无论上面从哪个分支 return，都必须续排下一轮（旧版在 pkg==null 等分支直接 return，
        // 会把 watchdog 永久停掉，之后再丢歌词就没人管了）
        scheduleNextCheck()
    }

    /**
     * 第三重保险（3.0.755+）：hook 全局 View.onAttachedToWindow——
     * 岛窗口根 DynamicIslandContentView（island_content，全屏窗口根）重建 attach
     * （锁屏/窗口重建/岛恢复必经）时，媒体在播则立即触发歌词恢复 reconcile+直注入。
     * 老词幕同款机制：attach 后延迟注入，覆盖事件守卫覆盖不到的"窗口重建"场景。
     * 触摸动画期间档位容器（BigIsland/Expanded）反复重建不触发本 hook（island_content
     * 不重建），由 updateBigIslandView 事件守卫兜底，两层互补。
     */
    class ReattachLyricInjectorHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
                val view = chain.thisObject as? android.view.View ?: return@runCatching
                if (!view.javaClass.name.startsWith("miui.systemui.dynamicisland")) return@runCatching
                if (view.javaClass.name != "miui.systemui.dynamicisland.window.content.DynamicIslandContentView") return@runCatching
                if (!isMediaPlaying(view.context)) return@runCatching
                HookLogger.i(TAG, "岛窗口根重建 attach + 媒体在播 → 触发歌词恢复（第三重保险）")
                scheduleLyricRestore()
            }.onFailure { e ->
                HookLogger.e(TAG, "attach 重注入失败", e)
            }
            return result
        }
    }

    class LayoutVisibilityHook(
        private val eventName: String
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()

            runCatching {
                val contentView = chain.thisObject as? ViewGroup ?: return@runCatching
                if (!IslandProbeUtils.isSuperIslandEnabled()) return@runCatching
                val currentData = IslandProbeUtils.getCurrentIslandData(contentView)
                val owner = IslandPresentationCoordinator.ownerEvidence(currentData)
                if (owner !is IslandRenderPolicy.OwnerEvidence.Media) return@runCatching
                if (eventName == "hideIslandLayout") {
                    // 系统正在隐藏岛布局（息屏/锁屏/岛收起）。**不再清歌词显示层**：
                    // ① 收起动画中间态会触发 hide，但岛仍在显示，歌词应该保留；
                    // ② 真隐藏（息屏/锁屏）时窗口整体不可见，歌词 VISIBLE 也看不到，无害；
                    // ③ 唯一风险"背景GONE+歌词VISIBLE=大岛透明"由 BackgroundVisibilityGuard
                    //    兜底（背景被藏但窗口可见+媒体在播 → 自动恢复背景 VISIBLE），
                    //    不需要 hide 联动把歌词清掉。
                    HookLogger.i(TAG, "hideIslandLayout 事件 → 歌词保持显示（不再清显示层）")
                } else {
                    // showIslandLayout：先恢复注入歌词可见，再走正常 reconcile，
                    // 避免结构已存在时 reconcile 为 NO_OP、歌词滞留 GONE。
                    IslandViewHelper.setInjectedLyricVisibility(contentView, android.view.View.VISIBLE)
                    IslandPresentationCoordinator.onRealVisibilityChanged(contentView, owner)
                    HookLogger.i(TAG, "showIslandLayout 事件 → 歌词置VISIBLE")
                }
            }.onFailure { e ->
                HookLogger.e(TAG, "$eventName 后恢复歌词视图失败", e)
            }

            return result
        }
    }
}
