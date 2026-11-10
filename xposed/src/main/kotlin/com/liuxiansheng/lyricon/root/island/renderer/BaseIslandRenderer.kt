/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.renderer

import android.os.Handler
import android.os.Looper
import android.view.View
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandViewHelper
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView
import com.liuxiansheng.lyricon.lyric.render.view.SpaceGateRichLyricLineView
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.effects.blur.IslandBackgroundBlurHooker
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandHostFacade
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandProbeUtils
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandPresentationCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandReconcileReason

/**
 * 基础渲染器（V3 自研）。
 *
 * 渲染事件统一走这里，经 32ms 防抖后分发：
 * - 内容刷新（refreshActiveIsland）
 * - 元数据更新（updateMetadata）
 * - 歌词行更新（updateLyricLine）
 * - 颜色更新（updateTextColors）
 * - 进度更新（updatePosition）
 * - 播放状态（onPlaybackStateChanged）
 * - 清空视图（clearAllViews）
 */
object BaseIslandRenderer : IslandRenderer {

    private const val REFRESH_DEBOUNCE_MS = 32L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val presentationStateLock = Any()
    private val refreshRunnable = Runnable { performRefreshActiveIsland() }
    private val metadataRefreshRunnable = Runnable { performUpdateMetadata() }
    private val textColorRefreshRunnable = Runnable { performUpdateTextColors() }

    /**
     * 完整结构恢复属于源/展示会话边界。首个有效行恢复宿主后，
     * 后续流式行只更新内容与宽度。
     */
    private var presentationRestorePending = true

    /**
     * 源生命周期事件是歌词渲染状态的权威。Hook 路径不得在此重查 MediaSession：
     * 歌词刷新期间源可能已停止，而播放器会话仍上报 STATE_PLAYING。
     */
    fun shouldRenderInjectedIsland(): Boolean {
        return IslandPresentationCoordinator.shouldRenderInjectedIsland()
    }

    override fun refreshActiveIsland() {
        mainHandler.removeCallbacks(metadataRefreshRunnable)
        IslandPresentationCoordinator.invalidatePresentation()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, REFRESH_DEBOUNCE_MS)
    }

    private fun performRefreshActiveIsland() {
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            )
        ) {
            // 切回状态栏不再 clearAllViews 清超导宿主（用户指出不该清）。
            // clearAllViews 的 forceClearHost→triggerSystemRelayout 会把宿主窗口释放，
            // 再切超导时宿主 detached、refreshActiveIsland 注入不了 → 必须换歌才恢复。
            // 宿主保留 attached，歌词由系统 hideIslandLayout 置 GONE，再切超导直接注入。
            return
        }
        if (!shouldRenderInjectedIsland()) {
            IslandPlaybackStateCoordinator.clearActiveViewsForPause()
            return
        }

        val lyricPkg =
            LyriconDataBridge.currentLyricPackageName?.takeIf { it.isNotEmpty() } ?: return
        // 切换超导确保播放状态 true（此前 clearAllViews 误清过、或被暂停态残留），
        // 否则 evaluate 判"没播放"→ SUPPRESSED 拦截、不注入歌词。切换超导=用户要显示歌词的强意图。
        // 真正暂停（onPlaybackStateChanged(false)）时才置 false；这里只防误清。
        if (!IslandPresentationCoordinator.isPlaybackActive()) {
            IslandPresentationCoordinator.updatePlaybackState(true)
        }
        val expectedLyricVersion = LyriconDataBridge.versionCounter.get()
        val expectedPresentationRevision =
            IslandPresentationCoordinator.currentPresentationRevision()

        IslandContentUpdateCoordinator.invalidate()

        // 切回超级岛/宿主收起时宿主可能 detached（窗口外），attached 快照为空 →
        // 用全量快照（含 detached/PENDING）兜底，否则切回灵动岛不注入歌词（要等换歌/重启）。
        var activeViews = IslandPresentationCoordinator.snapshotAttachedHosts(lyricPkg)
        if (activeViews.isEmpty()) {
            activeViews = IslandPresentationCoordinator.snapshotAllHosts(lyricPkg)
        }
        if (activeViews.isEmpty()) {
            activeViews = IslandPresentationCoordinator.snapshotAllHosts()
        }
        activeViews.forEach { token ->
            if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)) {
                // 切换超导时宿主残留 fake transition 冻结 → 主动解冻再注入。
                // 此前直接 return 跳过，宿主冻结时 refreshActiveIsland 永远注入不了歌词，
                // 必须换歌（过渡结束解冻）或重启才恢复 —— 即"切回超导歌词不过"根因。
                IslandPresentationCoordinator.unfreezeHostForFakeTransition(token)
            }
            val cv = token.root
            cv.post {
                if (LyriconDataBridge.versionCounter.get() != expectedLyricVersion ||
                    LyriconDataBridge.currentLyricPackageName != lyricPkg
                ) {
                    return@post
                }
                val result = IslandPresentationCoordinator.reconcileRegisteredHost(
                    token,
                    IslandReconcileReason.STABLE_REFRESH,
                    expectedPresentationRevision
                )
                if (!result.isTarget) return@post
                // 注入前确保歌词可见。clearAllViews 改为只隐藏歌词(GONE)、不清宿主后，
                // 切换超导 refresh 注入时须把歌词置回 VISIBLE，否则滞留 GONE 不显示（"切回超导没歌词"）。
                // 不依赖系统 showIslandLayout（切换超导不一定触发），refresh 即实时显隐。
                IslandViewHelper.setInjectedLyricVisibility(cv, View.VISIBLE)
                val currentPrefs = HookEntry.instance?.prefs ?: return@post
                val currentConfig = IslandSlotRuntimeConfig.from(currentPrefs)
                IslandContentUpdateCoordinator.updateContentForView(
                    view = cv,
                    packageName = lyricPkg,
                    prefs = currentPrefs,
                    config = currentConfig,
                    playbackActive = IslandPresentationCoordinator.isPlaybackActive()
                )
                // 宿主出现/歌词刷新时同步挂毛玻璃到胶囊状态视图。
                // 3.0.736：上游 HyperIsland 外观引擎已完整接管毛玻璃（高斯/柔光/液态/高光
                // 四种材质 + 六层遮罩对抗），此处退役 V3 自研 IslandBackgroundBlurHooker，
                // 否则两套毛玻璃同时挂载、互相覆盖（V3 旧 radius=0 会把上游柔光/高斯盖成透明）。
                // IslandBackgroundBlurHooker.maybeAttach(cv)
            }
        }

    }

    override fun updateMetadata() {
        // MediaController 回调可能在源清除与新播放状态提交期间到达。
        // 回到 SystemUI 主队列后再读实时展示状态，并把播放器的多次中间快照回调合并。
        mainHandler.removeCallbacks(metadataRefreshRunnable)
        mainHandler.post(metadataRefreshRunnable)
    }

    private fun performUpdateMetadata() {
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            )
        ) {
            refreshActiveIsland()
            return
        }
        if (!shouldRenderInjectedIsland()) {
            refreshActiveIsland()
            return
        }

        val lyricPkg = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                refreshActiveIsland()
                return
            }
        val expectedLyricVersion = LyriconDataBridge.versionCounter.get()
        val expectedPresentationRevision =
            IslandPresentationCoordinator.currentPresentationRevision()
        val snapshots = IslandPresentationCoordinator.snapshotAttachedInjectedHosts(lyricPkg)
        if (snapshots.isEmpty()) {
            refreshActiveIsland()
            return
        }

        snapshots.forEach { snapshot ->
            val token = snapshot.host
            if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)) {
                return@forEach
            }
            val cv = token.root
            cv.post {
                if (!IslandPresentationCoordinator.isCurrentHost(token) ||
                    IslandPresentationCoordinator.isHostFrozenForFakeTransition(token) ||
                    !IslandPresentationCoordinator.isCurrentPresentation(
                        expectedPresentationRevision
                    ) ||
                    LyriconDataBridge.versionCounter.get() != expectedLyricVersion ||
                    LyriconDataBridge.currentLyricPackageName != lyricPkg ||
                    !shouldRenderInjectedIsland()
                ) {
                    return@post
                }
                val currentPrefs = HookEntry.instance?.prefs ?: return@post
                IslandContentUpdateCoordinator.updateMetadataForView(
                    view = cv,
                    packageName = lyricPkg,
                    prefs = currentPrefs,
                    config = IslandSlotRuntimeConfig.from(currentPrefs),
                    playbackActive = IslandPresentationCoordinator.isPlaybackActive()
                )
            }
        }
    }

    override fun updateLyricLine() {
        if ((HookEntry.instance?.prefs?.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            )) != true
        ) return
        if (!shouldRenderInjectedIsland()) return
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        if (lyricPkg.isNullOrEmpty()) return

        val expectedLyricVersion = LyriconDataBridge.versionCounter.get()
        val expectedPresentationRevision =
            IslandPresentationCoordinator.currentPresentationRevision()

        val activeViews = IslandPresentationCoordinator.snapshotAttachedHosts(lyricPkg)
        val restorePresentation = synchronized(presentationStateLock) {
            if (!presentationRestorePending ||
                activeViews.none { token ->
                    !IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)
                }
            ) {
                false
            } else {
                presentationRestorePending = false
                true
            }
        }

        activeViews
            .forEach { token ->
                if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)) {
                    return@forEach
                }
                val cv = token.root
                cv.post {
                    if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token) ||
                        LyriconDataBridge.versionCounter.get() != expectedLyricVersion ||
                        LyriconDataBridge.currentLyricPackageName != lyricPkg
                    ) {
                        return@post
                    }
                    if (restorePresentation) {
                        val result = IslandPresentationCoordinator.reconcileRegisteredHost(
                            token,
                            IslandReconcileReason.LYRIC_SELF_HEAL,
                            expectedPresentationRevision
                        )
                        if (!result.isTarget) {
                            synchronized(presentationStateLock) {
                                presentationRestorePending = true
                            }
                            return@post
                        }
                    }
                    val currentPrefs = HookEntry.instance?.prefs ?: return@post
                    val currentConfig = IslandSlotRuntimeConfig.from(currentPrefs)
                    IslandContentUpdateCoordinator.updateLyricContentForView(
                        view = cv,
                        prefs = currentPrefs,
                        config = currentConfig,
                        playbackActive = IslandPresentationCoordinator.isPlaybackActive()
                    )
                }
            }
    }

    override fun onSongSwitched() {
        // 无条件清空歌词槽（不管超级岛开关、shouldRenderInjectedIsland 等前置条件）
        // 防止切歌后残留上一首的歌词
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        if (lyricPkg.isNullOrEmpty()) return
        val expectedLyricVersion = LyriconDataBridge.versionCounter.get()
        val expectedPresentationRevision =
            IslandPresentationCoordinator.currentPresentationRevision()
        IslandPresentationCoordinator.snapshotAttachedHosts(lyricPkg)
            .forEach { token ->
                if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)) {
                    return@forEach
                }
                val cv = token.root
                cv.post {
                    if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token) ||
                        LyriconDataBridge.versionCounter.get() != expectedLyricVersion ||
                        LyriconDataBridge.currentLyricPackageName != lyricPkg
                    ) {
                        return@post
                    }
                    val currentPrefs = HookEntry.instance?.prefs ?: return@post
                    IslandContentUpdateCoordinator.clearLyricContentForView(
                        view = cv,
                        prefs = currentPrefs,
                        config = IslandSlotRuntimeConfig.from(currentPrefs),
                        playbackActive = IslandPresentationCoordinator.isPlaybackActive()
                    )
                }
            }
    }

    override fun updateTextColors() {
        mainHandler.removeCallbacks(textColorRefreshRunnable)
        mainHandler.post(textColorRefreshRunnable)
    }

    private fun performUpdateTextColors() {
        IslandContentUpdateCoordinator.forEachActiveHost { view, packageName, prefs, config ->
            IslandContentUpdateCoordinator.updateTextColorsForView(
                view = view,
                packageName = packageName,
                prefs = prefs,
                config = config
            )
        }
    }

    override fun updatePosition(position: Long, playbackSpeed: Float) {
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            )
        ) return
        if (!shouldRenderInjectedIsland()) return
        val lyricPkg = LyriconDataBridge.currentLyricPackageName ?: return
        val expectedLyricVersion = LyriconDataBridge.versionCounter.get()
        val expectedPresentationRevision =
            IslandPresentationCoordinator.currentPresentationRevision()

        IslandPresentationCoordinator.snapshotAttachedInjectedHosts(lyricPkg)
            .forEach { snapshot ->
                val token = snapshot.host
                if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)) {
                    return@forEach
                }
                val cv = token.root
                val indexedViews = snapshot.injectedViews
                cv.post {
                    if (!IslandPresentationCoordinator.isCurrentHost(token) ||
                        IslandPresentationCoordinator.isHostFrozenForFakeTransition(token) ||
                        !IslandPresentationCoordinator.isCurrentPresentation(
                            expectedPresentationRevision
                        ) ||
                        LyriconDataBridge.versionCounter.get() != expectedLyricVersion ||
                        LyriconDataBridge.currentLyricPackageName != lyricPkg ||
                        !shouldRenderInjectedIsland()
                    ) {
                        return@post
                    }
                    val currentPrefs = HookEntry.instance?.prefs ?: return@post
                    val playbackViews: Iterable<View>
                    if (indexedViews.isEmpty()) {
                        val leftView = cv.findViewWithTag<View>(IslandProbeUtils.LEFT_TEST_VIEW_TAG)
                        val rightView = cv.findViewWithTag<View>(IslandProbeUtils.RIGHT_TEST_VIEW_TAG)
                        setPosition(leftView, position, playbackSpeed)
                        setPosition(rightView, position, playbackSpeed)
                        playbackViews = listOfNotNull(leftView, rightView)
                        IslandPresentationCoordinator.refreshInjectedViewIndex(token)
                    } else {
                        indexedViews.forEach { view ->
                            setPosition(view, position, playbackSpeed)
                        }
                        playbackViews = indexedViews
                    }
                    IslandContentUpdateCoordinator.updatePlaybackProgressForViews(
                        rootView = cv,
                        slotViews = playbackViews,
                        position = position
                    )
                    IslandHostFacade.updateProgressGlow(cv, lyricPkg, currentPrefs)
                }
            }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        IslandPlaybackStateCoordinator.onPlaybackStateChanged(
            isPlaying = isPlaying,
            onRefreshRequested = { refreshActiveIsland() },
            onClearRequested = { clearAllViews() }
        )
    }

    private fun setPosition(view: View?, position: Long, playbackSpeed: Float) {
        when (view) {
            is RichLyricLineView -> view.setPosition(position, playbackSpeed)
            is SpaceGateRichLyricLineView -> view.setPosition(position, playbackSpeed)
        }
    }

    override fun clearAllViews() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.removeCallbacks(metadataRefreshRunnable)
        mainHandler.removeCallbacks(textColorRefreshRunnable)
        synchronized(presentationStateLock) {
            presentationRestorePending = true
        }
        val wasPlaybackActive = IslandPresentationCoordinator.isPlaybackActive()
        if (wasPlaybackActive) {
            // 不再 updatePlaybackState(false)。
            // 此前逻辑在切回状态栏时把"播放状态"清成 false，再切超导 evaluate 判"没播放"
            // → SUPPRESSED 拦截、不注入歌词 → 必须换歌/重启才恢复（超导会检测播放状态）。
            // 播放状态改由源 onPlaybackStateChanged(isPlaying) 独立维护：播放中一直 true，
            // 暂停时由 onPlaybackStateChanged(false) 置 false 并冻结歌词。切换超导只显隐、不清播放态。
            IslandPresentationCoordinator.invalidatePresentation()
        }
        IslandPlaybackStateCoordinator.markClearedByPause()
        // 全量宿主快照（含 detached/PENDING_ATTACH）+ 强制隐藏。
        // 不再 forceClearHost —— 其内部 triggerSystemRelayout（系统宽度重算）会把
        // 宿主窗口释放（detach），切换超导时宿主 detached、refreshActiveIsland 注入不显示，
        // 必须换歌/重启重建才恢复（根本不该清，应一直注入、只显隐）。
        // 改为只隐藏注入歌词视图（GONE），宿主保留 attached，切换只控制显示。
        IslandPresentationCoordinator.snapshotAllHosts()
            .forEach { token ->
                val cv = token.root
                cv.post {
                    IslandViewHelper.setInjectedLyricVisibility(cv, View.GONE)
                }
            }
    }

}
