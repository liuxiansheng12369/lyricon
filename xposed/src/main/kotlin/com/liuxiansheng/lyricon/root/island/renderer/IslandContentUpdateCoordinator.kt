/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.renderer

import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.common.LyricTextColorStylePolicy
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.common.SuperIslandContentStylePolicy
import com.liuxiansheng.lyricon.hyper.common.media.MediaMetadataHelper
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.content.IslandSlotContentFacade
import com.liuxiansheng.lyricon.hyper.root.island.effects.color.IslandMusicWaveColorHooker
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandHostFacade
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandProbeUtils
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandPresentationCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.sizing.IslandCompactModeController
import com.liuxiansheng.lyricon.hyper.root.island.sizing.IslandDynamicWidthCoordinator
import com.liuxiansheng.lyricon.hyper.root.media.CurrentMediaInfoResolver
import com.liuxiansheng.lyricon.hyper.root.utils.CoverColorHelper
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger

/**
 * 内容更新协调器（V3 自研）。
 *
 * 更新已注入岛槽的内容与视觉样式：
 * - 完整内容刷新（updateContentForView / refreshContentForView）
 * - 元数据槽更新（updateMetadataForView）
 * - 高频进度只更新动态字段（updatePlaybackProgressForViews）
 * - 歌词行更新（updateLyricContentForView）
 * - 颜色更新（updateTextColorsForView / forEachActiveHost）
 * - 歌曲切换清行（clearLyricContentForView）
 */
internal object IslandContentUpdateCoordinator {

    fun invalidate() {
        IslandSlotContentFacade.invalidate()
    }

    /** 完整内容刷新：左右槽 + 光效 + 律动色 + 紧凑模式。 */
    fun updateContentForView(
        view: ViewGroup,
        packageName: String,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        playbackActive: Boolean
    ) {
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(view.context, packageName, HookLogger)
        prepareSharedCoverPalette(packageName, mediaInfo, prefs)
        IslandHostFacade.updateProgressGlow(view, packageName, mediaInfo, prefs)
        val leftChanged = updateSlot(
            view,
            IslandProbeUtils.LEFT_TEST_VIEW_TAG,
            config.leftMode,
            prefs,
            config,
            mediaInfo,
            playbackActive
        )
        val rightChanged = updateSlot(
            view,
            IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
            config.rightMode,
            prefs,
            config,
            mediaInfo,
            playbackActive
        )
        if ((config.leftMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
                    leftChanged) ||
            (config.rightMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
                    rightChanged)
        ) {
            IslandDynamicWidthCoordinator.requestRefresh(view)
        }
        IslandMusicWaveColorHooker.refresh()
        // 频谱封面隐藏：设置变更/媒体更新时按模式/开关刷新隐藏状态。
        IslandCompactModeController.maybeUpdate(view, prefs, config)
    }

    /**
     * 注入对账/恢复时的统一内容刷新入口。
     * 与 updateContentForView 共用同一套槽位装配，force/suppressAnimation 仅在对账恢复时使用。
     */
    fun refreshContentForView(
        view: ViewGroup,
        includeLyricSlots: Boolean = true,
        force: Boolean = false,
        suppressAnimation: Boolean = false
    ): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        val config = IslandSlotRuntimeConfig.from(prefs)
        val packageName = LyriconDataBridge.currentLyricPackageName.orEmpty()
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(view.context, packageName, HookLogger)
        var changed = false
        if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE &&
            (includeLyricSlots || config.leftMode != RootConstants.ISLAND_CONTENT_MODE_LYRIC)
        ) {
            changed = refreshSlotContentForView(
                view,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG,
                config.leftMode,
                prefs,
                config,
                force,
                suppressAnimation,
                mediaInfo
            ) || changed
        }
        if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE &&
            (includeLyricSlots || config.rightMode != RootConstants.ISLAND_CONTENT_MODE_LYRIC)
        ) {
            changed = refreshSlotContentForView(
                view,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
                config.rightMode,
                prefs,
                config,
                force,
                suppressAnimation,
                mediaInfo
            ) || changed
        }
        IslandDynamicWidthCoordinator.requestRefresh(view)
        IslandCompactModeController.maybeUpdate(view, prefs, config)
        return changed
    }

    private fun refreshSlotContentForView(
        rootView: ViewGroup,
        viewTag: String,
        mode: Int,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        force: Boolean,
        suppressAnimation: Boolean,
        mediaInfo: MediaMetadataHelper.MediaInfo
    ): Boolean {
        val view = rootView.findViewWithTag<android.view.View>(viewTag) ?: return false
        val contentChanged = IslandSlotContentFacade.applySlotContent(
            view,
            prefs,
            config,
            mode,
            force = force,
            suppressAnimation = suppressAnimation,
            mediaInfo = mediaInfo,
            onLineWillApply = { _ ->
                // 去预飞行：歌词上岛前不提前撑宽。
                true
            },
            onLineApplied = {
                // 3.0.852：降刷新率——歌词行应用后不强制宽度重排，改按需重排
                // （宽度实际变化才触发系统重排；同宽歌词连续切句不再反复 updateBigIslandViewWidth）。
                // 内容类型切换（歌名↔歌词）宽度必然变化，按需路径也能检测到并重排。
                IslandDynamicWidthCoordinator.requestRefresh(rootView)
            },
            onLineCancelled = {}
        )
        if (mode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
            IslandDynamicWidthCoordinator.cacheMetadataWidth(rootView, viewTag)
        ) {
            return true
        }
        return contentChanged
    }

    /** 元数据回调后只刷新自定义音乐信息槽；歌词槽不动（避免切歌名重启歌词内容/转场）。 */
    fun updateMetadataForView(
        view: ViewGroup,
        packageName: String,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        playbackActive: Boolean
    ) {
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(view.context, packageName, HookLogger)
        prepareSharedCoverPalette(packageName, mediaInfo, prefs)
        IslandHostFacade.updateProgressGlow(view, packageName, mediaInfo, prefs)
        val leftChanged = updateMetadataSlot(
            view = view,
            tag = IslandProbeUtils.LEFT_TEST_VIEW_TAG,
            mode = config.leftMode,
            prefs = prefs,
            config = config,
            mediaInfo = mediaInfo,
            playbackActive = playbackActive
        )
        val rightChanged = updateMetadataSlot(
            view = view,
            tag = IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
            mode = config.rightMode,
            prefs = prefs,
            config = config,
            mediaInfo = mediaInfo,
            playbackActive = playbackActive
        )
        if ((config.leftMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
                    leftChanged) ||
            (config.rightMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
                    rightChanged)
        ) {
            IslandDynamicWidthCoordinator.requestRefresh(view)
        }
        IslandMusicWaveColorHooker.refresh()
    }

    /**
     * 高频进度路径：只更新已渲染元数据槽的动态播放字段，不碰歌词内容。
     */
    fun updatePlaybackProgressForViews(
        rootView: ViewGroup,
        slotViews: Iterable<View>,
        position: Long
    ) {
        var contentChanged = false
        slotViews.forEach { slotView ->
            if (!IslandSlotContentFacade.updatePlaybackProgress(slotView, position)) {
                return@forEach
            }
            contentChanged = true
            slotView.tag?.toString()?.let { tag ->
                IslandDynamicWidthCoordinator.cacheMetadataWidth(rootView, tag)
            }
        }
        if (contentChanged) {
            IslandDynamicWidthCoordinator.requestRefresh(rootView)
        }
    }

    /** 歌词行刷新路径：先封面取色准备与颜色提交，再刷新左右歌词槽。 */
    fun updateLyricContentForView(
        view: ViewGroup,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        playbackActive: Boolean
    ) {
        // V3 修复：行刷新路径先执行封面取色准备与颜色提交（放在行内容刷新之前，
        // 避免行刷新异常导致取色代码不执行）。之前取色只在 updateTextColors
        // （设置变化）时执行，播放中行切换从不触发封面取色。
        val lyricPackageName = LyriconDataBridge.currentLyricPackageName
        if (!lyricPackageName.isNullOrEmpty()) {
            val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(
                view.context, lyricPackageName, HookLogger
            )
            prepareSharedCoverPalette(lyricPackageName, mediaInfo, prefs)
            HookLogger.i(
                "IslandContentUpdateCoordinator",
                "行刷新取色: pkg=$lyricPackageName art=${mediaInfo.albumArt != null} " +
                        "style=${LyricTextColorStylePolicy.read(prefs)}"
            )
            updateSlotColors(
                view,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG,
                config.leftMode,
                prefs,
                config,
                mediaInfo
            )
            updateSlotColors(
                view,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
                config.rightMode,
                prefs,
                config,
                mediaInfo
            )
        }
        updateLyricSlot(
            view,
            IslandProbeUtils.LEFT_TEST_VIEW_TAG,
            config.leftMode,
            prefs,
            config,
            playbackActive
        )
        updateLyricSlot(
            view,
            IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
            config.rightMode,
            prefs,
            config,
            playbackActive
        )
    }

    fun updateTextColorsForView(
        view: ViewGroup,
        packageName: String,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig
    ) {
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(view.context, packageName, HookLogger)
        prepareSharedCoverPalette(packageName, mediaInfo, prefs)
        updateSlotColors(
            view,
            IslandProbeUtils.LEFT_TEST_VIEW_TAG,
            config.leftMode,
            prefs,
            config,
            mediaInfo
        )
        updateSlotColors(
            view,
            IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
            config.rightMode,
            prefs,
            config,
            mediaInfo
        )
    }

    /** 遍历当前活动宿主执行更新（颜色刷新路径）。 */
    fun forEachActiveHost(
        update: (
            ViewGroup,
            String,
            SharedPreferences,
            IslandSlotRuntimeConfig
        ) -> Unit
    ) {
        if (!IslandPresentationCoordinator.shouldRenderInjectedIsland()) {
            HookLogger.dState(
                stateId = "IslandContentUpdateCoordinator.activeHosts",
                tag = "IslandContentUpdateCoordinator",
                state = "skip|presentation_not_target"
            ) {
                "颜色刷新未执行: reason=presentation_not_target"
            }
            return
        }
        val packageName = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                HookLogger.dState(
                    stateId = "IslandContentUpdateCoordinator.activeHosts",
                    tag = "IslandContentUpdateCoordinator",
                    state = "skip|no_lyric_package"
                ) {
                    "颜色刷新未执行: reason=no_lyric_package"
                }
                return
            }
        val expectedLyricVersion = LyriconDataBridge.versionCounter.get()
        val expectedPresentationRevision =
            IslandPresentationCoordinator.currentPresentationRevision()

        val hosts = IslandPresentationCoordinator.snapshotAttachedHosts(packageName)
        val frozenHostCount = hosts.count {
            IslandPresentationCoordinator.isHostFrozenForFakeTransition(it)
        }
        HookLogger.dState(
            stateId = "IslandContentUpdateCoordinator.activeHosts",
            tag = "IslandContentUpdateCoordinator",
            state = "hosts|$packageName|${hosts.size}|$frozenHostCount|$expectedLyricVersion|$expectedPresentationRevision"
        ) {
            "颜色刷新目标: package=$packageName, attachedHosts=${hosts.size}, " +
                    "frozenHosts=$frozenHostCount, lyricVersion=$expectedLyricVersion, " +
                    "presentationRevision=$expectedPresentationRevision"
        }

        hosts.forEach { token ->
            if (IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)) {
                return@forEach
            }
            token.root.post {
                val skipReason = when {
                    !IslandPresentationCoordinator.isCurrentHost(token) -> "host_stale"
                    IslandPresentationCoordinator.isHostFrozenForFakeTransition(token) ->
                        "fake_transition_frozen"

                    !IslandPresentationCoordinator.isCurrentPresentation(
                        expectedPresentationRevision
                    ) -> "presentation_revision_changed"

                    LyriconDataBridge.versionCounter.get() != expectedLyricVersion ->
                        "lyric_version_changed"

                    LyriconDataBridge.currentLyricPackageName != packageName ->
                        "lyric_owner_changed"

                    !IslandPresentationCoordinator.shouldRenderInjectedIsland() ->
                        "presentation_not_target"

                    else -> null
                }
                if (skipReason != null) {
                    HookLogger.dState(
                        stateId = "IslandContentUpdateCoordinator.colorRefreshSkip",
                        tag = "IslandContentUpdateCoordinator",
                        state = skipReason
                    ) {
                        "颜色刷新目标失效: reason=$skipReason, package=$packageName"
                    }
                    return@post
                }
                val prefs = HookEntry.instance?.prefs ?: return@post
                update(
                    token.root,
                    packageName,
                    prefs,
                    IslandSlotRuntimeConfig.from(prefs)
                )
            }
        }
    }

    /** 歌曲切换：清空左右歌词槽残留的上一首歌词行，触发宽度刷新回最小。 */
    fun clearLyricContentForView(
        view: ViewGroup,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        playbackActive: Boolean
    ) {
        // 歌名/音乐信息（CUSTOM_MUSIC_INFO 槽）独立于歌词行，不受影响。
        updateLyricSlot(
            view,
            IslandProbeUtils.LEFT_TEST_VIEW_TAG,
            config.leftMode,
            prefs,
            config,
            playbackActive,
            forceClear = true
        )
        updateLyricSlot(
            view,
            IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
            config.rightMode,
            prefs,
            config,
            playbackActive,
            forceClear = true
        )
    }

    private fun updateLyricSlot(
        view: ViewGroup,
        tag: String,
        mode: Int,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        playbackActive: Boolean,
        forceClear: Boolean = false
    ) {
        if (mode != RootConstants.ISLAND_CONTENT_MODE_LYRIC) return
        val lyricView = view.findViewWithTag<View>(tag) ?: return
        val line = if (forceClear) {
            // 歌曲切换清行：不上报预飞行（行已被清空，宽度按 min 计算）。
            null
        } else {
            IslandSlotContentFacade.buildSlotLyricLine(
                view = lyricView,
                prefs = prefs,
                config = config,
                isLeft = tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG
            )
        }
        IslandSlotContentFacade.applyLyricLineContent(
            view = lyricView,
            prefs = prefs,
            config = config,
            lineOverride = line,
            playbackActive = playbackActive,
            onLineWillApply = { _ ->
                // 去掉预飞行撑宽：歌词上岛前不提前撑宽度（避免"歌词没出现岛先变宽"）。
                true
            },
            onLineApplied = {
                IslandDynamicWidthCoordinator.requestRefresh(view)
            },
            onLineCancelled = {
            }
        )
    }

    private fun updateSlot(
        view: ViewGroup,
        tag: String,
        mode: Int,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        playbackActive: Boolean
    ): Boolean {
        if (mode == RootConstants.ISLAND_CONTENT_MODE_NONE) {
            view.findViewWithTag<View>(tag)?.let(IslandSlotContentFacade::clearMetadataState)
            return false
        }
        val slotView = view.findViewWithTag<View>(tag) ?: return false
        val contentChanged = IslandSlotContentFacade.applySlotContent(
            view = slotView,
            prefs = prefs,
            config = config,
            mode = mode,
            // 先让 applySlotContent 配置实际文字画笔，再用该画笔构建拆分行。
            // 保证拆分边界与自定义字体/字重变化同步（完整岛刷新时）。
            lineOverride = null,
            playbackActive = playbackActive,
            mediaInfo = mediaInfo,
            onLineWillApply = { _ ->
                // 去掉预飞行撑宽：歌词上岛前不提前撑宽度（避免"歌词没出现岛先变宽"）。
                true
            },
            onLineApplied = {
                IslandDynamicWidthCoordinator.requestRefresh(view)
            },
            onLineCancelled = {
            }
        )
        if (mode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
            IslandDynamicWidthCoordinator.cacheMetadataWidth(view, tag)
        ) {
            return true
        }
        return contentChanged
    }

    private fun updateMetadataSlot(
        view: ViewGroup,
        tag: String,
        mode: Int,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        playbackActive: Boolean
    ): Boolean {
        if (mode != RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO) return false
        val slotView = view.findViewWithTag<View>(tag) ?: return false
        val contentChanged = IslandSlotContentFacade.applySlotContent(
            view = slotView,
            prefs = prefs,
            config = config,
            mode = mode,
            playbackActive = playbackActive,
            mediaInfo = mediaInfo
        )
        if (IslandDynamicWidthCoordinator.cacheMetadataWidth(view, tag)) {
            return true
        }
        return contentChanged
    }

    private fun updateSlotColors(
        view: ViewGroup,
        tag: String,
        mode: Int,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mediaInfo: MediaMetadataHelper.MediaInfo
    ) {
        if (mode == RootConstants.ISLAND_CONTENT_MODE_NONE) {
            HookLogger.dState(
                stateId = "IslandContentUpdateCoordinator.slotColor:$tag",
                tag = "IslandContentUpdateCoordinator",
                state = "mode_none"
            ) {
                "歌词颜色未提交: tag=$tag, reason=content_mode_none"
            }
            return
        }
        val slotView = view.findViewWithTag<View>(tag) ?: run {
            HookLogger.dState(
                stateId = "IslandContentUpdateCoordinator.slotColor:$tag",
                tag = "IslandContentUpdateCoordinator",
                state = "slot_missing|$mode"
            ) {
                "歌词颜色未提交: tag=$tag, mode=$mode, reason=slot_view_missing"
            }
            return
        }
        IslandSlotContentFacade.configureView(
            view = slotView,
            prefs = prefs,
            config = config,
            mode = mode,
            mediaInfo = mediaInfo
        )
    }

    /**
     * MediaSession 封面是唯一颜色源。在各消费者渲染前填充共享调色板，
     * 使其颜色生命周期不依赖 MusicWave 回调（外部设备图标占用槽位时该回调消失）。
     */
    private fun prepareSharedCoverPalette(
        packageName: String,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        prefs: SharedPreferences
    ) {
        val usesCoverPalette =
            LyricTextColorStylePolicy.usesCoverColor(
                LyricTextColorStylePolicy.read(prefs)
            ) ||
                    SuperIslandContentStylePolicy.usesMusicWaveCoverColor(
                        SuperIslandContentStylePolicy.readMusicWaveStyle(prefs)
                    ) ||
                    // 进度弧"跟随封面色"（gradient=false）需要共享调色板
                    !prefs.getBoolean(
                        RootConstants.KEY_HOOK_ISLAND_PROGRESS_GRADIENT,
                        RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_GRADIENT
                    ) ||
                    // 底圈独立开关：底圈开启就需要共享调色板——
                    // 底圈永远跟随封面取色，不受进度条开关/彩虹渐变控制，只看底圈自己
                    prefs.getBoolean(
                        RootConstants.KEY_HOOK_ISLAND_PROGRESS_TRACK,
                        RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_TRACK
                    )
        if (!usesCoverPalette) return

        val rawAlbumArt = mediaInfo.albumArt
        val albumArt = rawAlbumArt?.takeUnless { it.isRecycled } ?: run {
            HookLogger.dState(
                stateId = "IslandContentUpdateCoordinator.coverInput",
                tag = "IslandContentUpdateCoordinator",
                state = "no_art|${rawAlbumArt == null}|${rawAlbumArt?.isRecycled == true}"
            ) {
                        "共享取色未执行: reason=${if (rawAlbumArt == null) "album_art_missing" else "album_art_recycled"}, " +
                        "package=$packageName, artworkSource=${mediaInfo.artworkSource}, " +
                        "titlePresent=${mediaInfo.title.isNotBlank()}, " +
                        "artistPresent=${mediaInfo.artist.isNotBlank()}"
            }
            return
        }
        CoverColorHelper.ensureArtworkColors(mediaInfo)
    }
}
