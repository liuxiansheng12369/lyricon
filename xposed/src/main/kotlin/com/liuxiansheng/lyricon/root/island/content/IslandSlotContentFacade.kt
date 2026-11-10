/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 槽位内容路由外观：
 * 按显示模式把内容分发到歌词组装器或元数据组装器，
 * 并先应用样式组装。歌词模式清元数据状态，元数据模式不再走歌词链路。
 */

package com.liuxiansheng.lyricon.hyper.root.island.content

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.common.media.MediaMetadataHelper
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.IRichLyricLine
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.media.CurrentMediaInfoResolver
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger

/**
 * 槽位内容路由（V3 自研）。
 *
 * 槽位路由集中在这里；歌词内容、元数据内容、样式应用各自保有实现与缓存。
 * - 歌词模式：清元数据状态 → 走歌词组装器
 * - 元数据模式：走元数据组装器（歌名/作者/时间）
 */
internal object IslandSlotContentFacade {

    fun invalidate(view: View? = null) {
        IslandContentProviderManager.destroyAllIn(view)
        IslandMetadataContentAssembler.invalidate(view)
        IslandSlotStyleAssembler.invalidate(view)
    }

    fun configureView(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        mediaInfo: MediaMetadataHelper.MediaInfo = currentMediaInfo(view.context),
        force: Boolean = false
    ) {
        IslandSlotStyleAssembler.configureView(
            view = view,
            prefs = prefs,
            config = config,
            mode = mode,
            mediaInfo = mediaInfo,
            nextLinePreviewEnabled = IslandLyricContentAssembler.isNextLinePreviewEnabled(
                prefs,
                config
            ),
            force = force
        )
    }

    /** 按模式提交槽位内容：歌词模式走歌词组装，否则走元数据组装。 */
    fun applySlotContent(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        lineOverride: IRichLyricLine? = null,
        force: Boolean = false,
        playbackActive: Boolean = true,
        suppressAnimation: Boolean = false,
        mediaInfo: MediaMetadataHelper.MediaInfo = currentMediaInfo(view.context),
        playbackPosition: Long = LyriconDataBridge.currentPosition,
        playbackDuration: Long = mediaInfo.duration,
        onLineWillApply: ((Float) -> Boolean)? = null,
        onLineApplied: (() -> Unit)? = null,
        onLineCancelled: (() -> Unit)? = null
    ): Boolean {
        // 3.0.881：内容模式（网速/电量/时间等 53 种）走 Provider 渲染，
        // 只更新已挂载的 Provider 视图；重建由 IslandSlotStructureInjector 按类型检测处理。
        if (IslandContentProviderManager.isProviderMode(mode)) {
            return IslandContentProviderManager.updateProviderView(
                view = view,
                providerConfig = IslandContentProviderManager.buildConfig(config),
                side = if (config.isLeftTag(
                        view.tag?.toString() ?: ""
                    )
                ) "left" else "right"
            )
        }
        configureView(view, prefs, config, mode, mediaInfo, force)
        return if (mode == RootConstants.ISLAND_CONTENT_MODE_LYRIC) {
            IslandMetadataContentAssembler.clearState(view)
            IslandLyricContentAssembler.apply(
                view = view,
                prefs = prefs,
                config = config,
                lineOverride = lineOverride,
                force = force,
                playbackActive = playbackActive,
                suppressAnimation = suppressAnimation,
                onLineWillApply = onLineWillApply,
                onLineApplied = onLineApplied,
                onLineCancelled = onLineCancelled
            )
        } else {
            IslandMetadataContentAssembler.apply(
                view = view,
                prefs = prefs,
                config = config,
                mode = mode,
                force = force,
                mediaInfo = mediaInfo,
                playbackPosition = playbackPosition,
                playbackDuration = playbackDuration
            )
        }
    }

    fun clearMetadataState(view: View) {
        IslandMetadataContentAssembler.clearState(view)
    }

    /** 只更新已渲染元数据视图的动态字段（已播放/剩余/进度百分比）。 */
    fun updatePlaybackProgress(view: View, position: Long): Boolean {
        return IslandMetadataContentAssembler.updatePlaybackProgress(view, position)
    }

    fun applyLyricLineContent(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        lineOverride: IRichLyricLine?,
        playbackActive: Boolean = true,
        onLineWillApply: ((Float) -> Boolean)? = null,
        onLineApplied: (() -> Unit)? = null,
        onLineCancelled: (() -> Unit)? = null
    ): Boolean = IslandLyricContentAssembler.applyLine(
        view = view,
        prefs = prefs,
        config = config,
        lineOverride = lineOverride,
        playbackActive = playbackActive,
        onLineWillApply = onLineWillApply,
        onLineApplied = onLineApplied,
        onLineCancelled = onLineCancelled
    )

    fun buildSlotLyricLine(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        isLeft: Boolean
    ): IRichLyricLine? = IslandLyricContentAssembler.buildSlotLyricLine(
        view = view,
        prefs = prefs,
        config = config,
        isLeft = isLeft
    )

    fun processedRawLine(
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig? = null
    ): IRichLyricLine? = IslandLyricContentAssembler.processedRawLine(prefs, config)

    private fun currentMediaInfo(context: Context): MediaMetadataHelper.MediaInfo {
        val targetPkg = LyriconDataBridge.currentLyricPackageName ?: ""
        return CurrentMediaInfoResolver.getMediaInfo(context, targetPkg, HookLogger)
    }
}
