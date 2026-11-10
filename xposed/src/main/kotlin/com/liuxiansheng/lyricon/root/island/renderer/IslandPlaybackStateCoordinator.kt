/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.renderer

import android.view.View
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView
import com.liuxiansheng.lyricon.lyric.render.view.SpaceGateRichLyricLineView
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.effects.glow.IslandProgressGlowController
import com.liuxiansheng.lyricon.hyper.root.island.hooks.MediaIslandCreationGate
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandPresentationCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandReconcileReason
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger

/**
 * 播放状态协调器（V3 自研）。
 *
 * 播放策略（暂停隐藏 / 暂停保留）由这里执行：
 * - 暂停且行为=0（暂停隐藏）：清空活动视图（冻结歌词）；
 * - 暂停且行为≠0：只置 playbackActive=false（保留显示）；
 * - 恢复播放：若曾被暂停清空，走宿主对账恢复并置 playbackActive=true。
 */
internal object IslandPlaybackStateCoordinator {
    private var clearedByPause = false

    fun markClearedByPause() {
        clearedByPause = true
    }

    fun onPlaybackStateChanged(
        isPlaying: Boolean,
        onRefreshRequested: () -> Unit,
        onClearRequested: () -> Unit
    ) {
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            )
        ) {
            HookLogger.dState(
                stateId = "IslandPlaybackStateCoordinator.policy",
                tag = "IslandPlaybackStateCoordinator",
                state = "disabled|$isPlaying"
            ) {
                "播放策略未执行: reason=super_island_disabled, isPlaying=$isPlaying"
            }
            onClearRequested()
            return
        }

        val stateChanged = IslandPresentationCoordinator.updatePlaybackState(isPlaying)
        if (stateChanged) {
            IslandProgressGlowController.onPlaybackStateChanged(isPlaying)
        }
        val behavior = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        )
        val wasClearedByPause = clearedByPause

        if (isPlaying) {
            if (behavior == 2) {
                // 暂停时不生成超级岛：恢复播放后由系统 add（媒体数据变化）或主动 forceRecreate 弹回来。
                if (stateChanged) {
                    MediaIslandCreationGate.forceRecreate("恢复播放自动弹出")
                }
            } else if (clearedByPause) {
                clearedByPause = false
                restoreActiveViewsAfterPause(onRefreshRequested)
            } else if (stateChanged) {
                applyPlaybackStateToActiveViews(true)
            }
        } else if (behavior == 2) {
            // 暂停：直接让岛不存在（摘掉已显示那颗，后续创建被 MediaIslandCreationGate 源头拦掉）。
            if (stateChanged) {
                MediaIslandCreationGate.removeCurrentIsland("暂停不生成超级岛")
            }
        } else if (behavior == 0) {
            if (!clearedByPause) {
                clearActiveViewsForPauseInternal()
            }
        } else if (stateChanged) {
            applyPlaybackStateToActiveViews(false)
        }
        val action = when {
            behavior == 2 && !isPlaying && stateChanged -> "remove_island_on_pause"
            behavior == 2 && isPlaying && stateChanged -> "recreate_island_on_resume"
            isPlaying && wasClearedByPause -> "restore_after_pause"
            isPlaying && stateChanged -> "resume_active_views"
            !isPlaying && behavior == 0 && !wasClearedByPause -> "clear_on_pause"
            !isPlaying && stateChanged -> "freeze_active_views"
            else -> "no_change"
        }
        HookLogger.dState(
            stateId = "IslandPlaybackStateCoordinator.policy",
            tag = "IslandPlaybackStateCoordinator",
            state = "$isPlaying|$behavior|$stateChanged|$clearedByPause|$action"
        ) {
            "播放策略已处理: isPlaying=$isPlaying, pauseBehavior=$behavior, " +
                    "stateChanged=$stateChanged, clearedByPause=$clearedByPause, action=$action"
        }
    }

    /** 暂停后恢复：宿主对账 + 置 playbackActive=true。 */
    private fun restoreActiveViewsAfterPause(onRefreshRequested: () -> Unit) {
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: return
        val expectedLyricVersion = LyriconDataBridge.versionCounter.get()
        val expectedPresentationRevision =
            IslandPresentationCoordinator.currentPresentationRevision()
        val activeViews = IslandPresentationCoordinator.snapshotAttachedHosts(lyricPkg)
        if (activeViews.isEmpty()) {
            HookLogger.dState(
                stateId = "IslandPlaybackStateCoordinator.restore",
                tag = "IslandPlaybackStateCoordinator",
                state = "no_hosts"
            ) {
                "暂停恢复未找到宿主: reason=no_attached_hosts, package=$lyricPkg"
            }
            onRefreshRequested()
            return
        }

        activeViews.forEach { token ->
            if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)) {
                return@forEach
            }
            val view = token.root
            view.post {
                if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token) ||
                    !IslandPresentationCoordinator.shouldRenderInjectedIsland() ||
                    !IslandPresentationCoordinator.isCurrentPresentation(
                        expectedPresentationRevision
                    ) ||
                    LyriconDataBridge.versionCounter.get() != expectedLyricVersion ||
                    LyriconDataBridge.currentLyricPackageName != lyricPkg
                ) {
                    return@post
                }

                val result = IslandPresentationCoordinator.reconcileRegisteredHost(
                    token,
                    IslandReconcileReason.PLAYBACK_RESUME,
                    expectedPresentationRevision
                )
                if (!result.isTarget) return@post
                val currentPrefs = HookEntry.instance?.prefs ?: return@post
                val config = IslandSlotRuntimeConfig.from(currentPrefs)
                val expectsInjectedView =
                    config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE ||
                            config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE
                if (expectsInjectedView && result.mutation.injectedSlotsPresent == false) {
                    onRefreshRequested()
                    return@post
                }

                setPlaybackActiveRecursively(view, true)
            }
        }
    }

    fun clearActiveViewsForPause() {
        if (clearedByPause) return
        clearActiveViewsForPauseInternal()
    }

    private fun clearActiveViewsForPauseInternal() {
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        val expectedPresentationRevision =
            IslandPresentationCoordinator.currentPresentationRevision()
        IslandPresentationCoordinator.snapshotAttachedHosts()
            .filter { token -> lyricPkg == null || token.packageName == lyricPkg }
            .forEach { token ->
                token.root.post {
                    IslandPresentationCoordinator.clearRegisteredHostIfSuppressed(
                        token,
                        expectedPresentationRevision
                    )
                }
            }
        clearedByPause = true
    }

    private fun applyPlaybackStateToActiveViews(isPlaying: Boolean) {
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        val expectedPresentationRevision =
            IslandPresentationCoordinator.currentPresentationRevision()
        IslandPresentationCoordinator.snapshotAttachedInjectedHosts(lyricPkg)
            .forEach { snapshot ->
                val token = snapshot.host
                if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)) {
                    return@forEach
                }
                val view = token.root
                val indexedViews = snapshot.injectedViews
                view.post {
                    val currentLyricPackage = LyriconDataBridge.currentLyricPackageName
                    if (!IslandPresentationCoordinator.isCurrentHost(token) ||
                        IslandPresentationCoordinator.isHostFrozenForFakeTransition(token) ||
                        !IslandPresentationCoordinator.isCurrentPresentation(
                            expectedPresentationRevision
                        ) ||
                        IslandPresentationCoordinator.isPlaybackActive() != isPlaying ||
                        (currentLyricPackage != null &&
                                currentLyricPackage != token.packageName)
                    ) {
                        return@post
                    }
                    if (indexedViews.isEmpty()) {
                        setPlaybackActiveRecursively(view, isPlaying)
                        IslandPresentationCoordinator.refreshInjectedViewIndex(token)
                    } else {
                        indexedViews.forEach { injectedView ->
                            setPlaybackActive(injectedView, isPlaying)
                        }
                    }
                }
            }
    }

    private fun setPlaybackActive(view: View, isPlaying: Boolean) {
        when (view) {
            is RichLyricLineView -> view.setPlaybackActive(isPlaying)
            is SpaceGateRichLyricLineView -> view.setPlaybackActive(isPlaying)
        }
    }

    private fun setPlaybackActiveRecursively(view: View, isPlaying: Boolean) {
        when (view) {
            is RichLyricLineView,
            is SpaceGateRichLyricLineView -> setPlaybackActive(view, isPlaying)

            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    setPlaybackActiveRecursively(view.getChildAt(index), isPlaying)
                }
            }
        }
    }
}
