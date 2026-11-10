/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.presentation

import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandProbeUtils

/**
 * 展示决策评估器（V3 自研）。
 */
internal class IslandPresentationDecisionEvaluator(
    private val presentationState: IslandPresentationState
) {
    fun ownerEvidence(data: Any?): IslandRenderPolicy.OwnerEvidence {
        if (data == null) return IslandRenderPolicy.OwnerEvidence.Pending
        val mediaInfo = IslandProbeUtils.extractMediaIslandInfo(data)
        if (mediaInfo != null) {
            return IslandRenderPolicy.OwnerEvidence.Media(mediaInfo.packageName)
        }
        return if (IslandProbeUtils.isMediaIsland(data)) {
            IslandRenderPolicy.OwnerEvidence.Pending
        } else {
            IslandRenderPolicy.OwnerEvidence.NotMedia
        }
    }

    fun isCurrentLyricOwner(mediaInfo: IslandProbeUtils.MediaIslandInfo): Boolean {
        val lyricPackageName = LyriconDataBridge.currentLyricPackageName
            ?.takeIf(String::isNotEmpty)
            ?: return false
        return mediaInfo.packageName == lyricPackageName
    }

    /**
     * 长按仍绑定当前歌词岛（暂停时也如此）。展示策略可能暂停后隐藏注入视图，
     * 但不能让仍可见的原生岛失去拖拽分享/播放切换动作。
     */
    fun isCurrentLyricLongPressTarget(data: Any?): Boolean {
        if (!IslandProbeUtils.isSuperIslandEnabled()) return false
        if (!LyriconDataBridge.hasLyricsForPresentation()) return false
        val mediaInfo = IslandProbeUtils.extractMediaIslandInfo(data) ?: return false
        return isCurrentLyricOwner(mediaInfo)
    }

    fun shouldRenderInjectedIsland(): Boolean {
        return IslandRenderPolicy.isPresentationAllowed(
            enabled = IslandProbeUtils.isSuperIslandEnabled(),
            playbackActive = presentationState.isPlaybackActive(),
            pauseBehavior = currentPauseBehavior()
        )
    }

    fun evaluate(owner: IslandRenderPolicy.OwnerEvidence): IslandRenderPolicy.Decision {
        return IslandRenderPolicy.evaluate(
            IslandRenderPolicy.Input(
                owner = owner,
                lyricPackageName = LyriconDataBridge.currentLyricPackageName,
                hasLyricsForPresentation = LyriconDataBridge.hasLyricsForPresentation(),
                enabled = IslandProbeUtils.isSuperIslandEnabled(),
                playbackActive = presentationState.isPlaybackActive(),
                pauseBehavior = currentPauseBehavior()
            )
        )
    }

    private fun currentPauseBehavior(): Int {
        return HookEntry.instance?.prefs?.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        ) ?: RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
    }
}
