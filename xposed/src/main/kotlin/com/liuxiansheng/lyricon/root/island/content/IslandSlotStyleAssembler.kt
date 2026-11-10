/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 槽位样式组装器：
 * 把歌词样式（文字颜色/封面取色/彩虹/状态栏色）应用到超级岛歌词视图。
 * 通过样式签名与颜色签名判断是否真的需要重建，避免无谓刷新。
 */

package com.liuxiansheng.lyricon.hyper.root.island.content

import android.content.SharedPreferences
import android.graphics.Color
import android.view.View
import com.liuxiansheng.lyricon.hyper.common.LyricTextColorStylePolicy
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.common.media.MediaMetadataHelper
import com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView
import com.liuxiansheng.lyricon.lyric.render.view.SpaceGateRichLyricLineView
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.effects.color.StatusBarTextColorHooker
import com.liuxiansheng.lyricon.hyper.root.utils.CoverColorHelper
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import com.liuxiansheng.lyricon.hyper.root.utils.LyricStyleHelper
import com.liuxiansheng.lyricon.hyper.root.utils.TranslationHelper
import java.util.WeakHashMap

/**
 * 槽位样式组装器（V3 自研）。
 *
 * 负责把"文字颜色 / 封面取色 / 彩虹 / 状态栏文字色"解析成最终样式并应用到视图。
 * 签名机制保证只有样式或颜色真正变化时才重建，性能开销最小。
 */
internal object IslandSlotStyleAssembler {
    private val lastStyleSignatures = WeakHashMap<View, String>()
    private val lastColorSignatures = WeakHashMap<View, String>()

    /** 清空签名缓存：全部清或只清单个视图。 */
    fun invalidate(view: View? = null) {
        if (view == null) {
            synchronized(lastStyleSignatures) { lastStyleSignatures.clear() }
            synchronized(lastColorSignatures) { lastColorSignatures.clear() }
            return
        }
        synchronized(lastStyleSignatures) { lastStyleSignatures.remove(view) }
        synchronized(lastColorSignatures) { lastColorSignatures.remove(view) }
    }

    /**
     * 应用样式到视图。
     * [mode] 当前显示模式；[nextLinePreviewEnabled] 下一句预览是否生效；
     * [force] 为 true 时忽略签名强制重建。
     */
    fun configureView(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        nextLinePreviewEnabled: Boolean,
        force: Boolean
    ) {
        val disableAll = TranslationHelper.isTranslationDisabled(prefs) || nextLinePreviewEnabled
        val translationOnly = TranslationHelper.isTranslationOnly(prefs)
        // 3.0.796：自动切换翻译（上游 HyperLyric 语义）——当前整首歌无翻译时，
        // 自动回退翻译策略：翻译独占不再锁死副行，保留罗马音/下一句降级显示。
        val autoSwitch = TranslationHelper.isAutoSwitchTranslation(prefs)
        val songHasTranslation = TranslationHelper.songHasTranslation()
        val effectiveTranslationOnly = translationOnly && (!autoSwitch || songHasTranslation)
        // 3.0.619 翻译诊断：样式层 displayTranslation 开关来源
        android.util.Log.i(
            "LyricTrans",
            "styleAssembler disableAll=$disableAll translationOnly=$translationOnly " +
                "nextPreview=$nextLinePreviewEnabled -> displayTranslation=${!disableAll}"
        )
        val lyricPackage = LyriconDataBridge.currentLyricPackageName.orEmpty()
        val colorSession = CoverColorHelper.currentSession(lyricPackage)
        val albumBitmap = mediaInfo.albumArt
        val artworkRequest = if (config.extractCoverTextColor && albumBitmap != null) {
            CoverColorHelper.ensureArtworkColors(mediaInfo)
        } else {
            null
        }
        val statusBarTextColor = if (config.followStatusBarTextColor) {
            StatusBarTextColorHooker.currentTextColor()
        } else {
            null
        }
        val textColorStyle = LyricTextColorStylePolicy.read(prefs)
        val useCoverColor = LyricTextColorStylePolicy.usesCoverColor(textColorStyle)
        val useCoverGradient = LyricTextColorStylePolicy.usesCoverGradient(textColorStyle)
        // 彩虹已并入"歌词颜色样式"选择器（样式 4），由 LyricStyleHelper 读取；
        // 签名通过 config.textColorStyle 覆盖，不再单独读取 KEY_HOOK_RAINBOW_ENABLED。
        val fixedTextColor = if (prefs.contains(RootConstants.KEY_HOOK_TEXT_COLOR)) {
            prefs.getInt(RootConstants.KEY_HOOK_TEXT_COLOR, Color.WHITE)
        } else {
            null
        }
        val cachedCoverPalette = if (statusBarTextColor == null && useCoverColor) {
            if (artworkRequest != null) {
                CoverColorHelper.getCachedColors(useCoverGradient, artworkRequest)
            } else {
                colorSession?.let { CoverColorHelper.getCachedColors(useCoverGradient, it) }
            }
        } else {
            null
        }
        val styleSignature = listOf(
            config.styleSignature,
            mode,
            mediaInfo.title,
            mediaInfo.artist,
            mediaInfo.album,
            textColorStyle,
            fixedTextColor ?: -1,
            // 歌词效果类设置纳入签名：改镜像倒影/逐字动效等立即触发 style 重建，实时生效。
            prefs.getBoolean("island_lyric_mirror", false),
            prefs.getBoolean("key_hook_word_motion_enabled", false),
            // 显示效果 3 项纳入签名：改歌词居中/右对齐/音乐信息居中立即触发 style 重建
            // （3.0.787 修复：此前这 3 个键不在签名里，改设置不重建样式 → 永远不生效）
            prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_LYRIC,
                RootConstants.DEFAULT_HOOK_CENTER_LYRIC
            ),
            prefs.getBoolean(
                RootConstants.KEY_HOOK_RIGHT_LYRIC,
                RootConstants.DEFAULT_HOOK_RIGHT_LYRIC
            ),
            prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_MUSIC_INFO,
                prefs.getBoolean(
                    RootConstants.KEY_HOOK_CENTER_LYRIC,
                    RootConstants.DEFAULT_HOOK_CENTER_MUSIC_INFO
                )
            ),
            // 翻译 4 项纳入签名（3.0.795 修复"翻译不生效"）：
            // 显示翻译/翻译独占/交换翻译/自动切换翻译 改开关立即触发 style 重建，
            // 让 displayTranslation/displayRoma 与副行内容策略实时更新
            prefs.getBoolean(
                RootConstants.KEY_SHOW_TRANSLATION,
                true
            ),
            prefs.getBoolean(
                RootConstants.KEY_HOOK_TRANSLATION_ONLY,
                RootConstants.DEFAULT_HOOK_TRANSLATION_ONLY
            ),
            prefs.getBoolean(
                RootConstants.KEY_HOOK_SWAP_TRANSLATION,
                RootConstants.DEFAULT_HOOK_SWAP_TRANSLATION
            ),
            prefs.getBoolean(
                RootConstants.KEY_HOOK_AUTO_SWITCH_TRANSLATION,
                RootConstants.DEFAULT_HOOK_AUTO_SWITCH_TRANSLATION
            )
        ).joinToString("|")
        val colorSignature = listOf(
            config.textColorStyle,
            statusBarTextColor,
            colorSession?.revision,
            colorSession?.mediaKey,
            artworkRequest?.revision,
            albumBitmap?.generationId ?: 0,
            fixedTextColor ?: -1
        ).joinToString("|")

        val styleChanged = force || lastStyleSignatures[view] != styleSignature
        val colorChanged = force || lastColorSignatures[view] != colorSignature
        if (!styleChanged && !colorChanged) return

        val style = LyricStyleHelper.buildStyle(
            prefs = prefs,
            res = view.resources,
            mode = mode,
            colorSession = colorSession,
            artworkRequest = artworkRequest,
            textColorOverride = statusBarTextColor
        )
        var dispatch = "unsupported"
        when (view) {
            // SpaceGateRichLyricLineView 是 RichLyricLineView 的子类，必须先匹配，
            // 否则永远走 RichLyricLineView 分支，isLeftSplitSide 传不进去，
            // 右侧容器永远左对齐（用户反馈右容器未右对齐的根因）。
            is SpaceGateRichLyricLineView -> {
                if (styleChanged) {
                    view.displayTranslation = !disableAll
                    view.displayRoma = !disableAll && !effectiveTranslationOnly
                    view.lineSwitchAnimation = LyricStyleHelper.readLineSwitchAnim(prefs)
                    view.setStyle(
                        style,
                        isLeftSplitSide = config.isLeftTag(view.tag as? String ?: "")
                    )
                    dispatch = "set_style"
                } else {
                    view.updateColor(
                        style.primary.color,
                        style.highlight.background,
                        style.highlight.foreground
                    )
                    dispatch = "update_color"
                }
            }

            is RichLyricLineView -> {
                if (styleChanged) {
                    view.displayTranslation = !disableAll
                    view.displayRoma = !disableAll && !effectiveTranslationOnly
                    view.lineSwitchAnimation = LyricStyleHelper.readLineSwitchAnim(prefs)
                    view.setStyle(style)
                    dispatch = "set_style"
                } else {
                    view.updateColor(
                        style.primary.color,
                        style.highlight.background,
                        style.highlight.foreground
                    )
                    dispatch = "update_color"
                }
            }
        }
        lastStyleSignatures[view] = styleSignature
        lastColorSignatures[view] = colorSignature
        val appliedTextPaintColor = when (view) {
            is RichLyricLineView -> view.main.textPaint.color
            is SpaceGateRichLyricLineView -> view.main.textPaint.color
            else -> null
        }
        val colorSource = when {
            statusBarTextColor != null -> "status_bar"
            !useCoverColor -> "default"
            cachedCoverPalette?.second?.isNotEmpty() == true ->
                if (useCoverGradient) "cover_gradient" else "cover"

            else -> "cover_fallback"
        }
        val viewKey = view.tag?.toString() ?: view.javaClass.simpleName
        val debugState = listOf(
            colorSource,
            style.primary.color.contentToString(),
            style.highlight.background.contentToString(),
            style.highlight.foreground.contentToString(),
            appliedTextPaintColor,
            styleChanged,
            colorChanged,
            dispatch,
            view.visibility,
            view.isShown,
            view.isAttachedToWindow
        ).joinToString("|")
        HookLogger.dState(
            stateId = "IslandSlotStyleAssembler:$viewKey",
            tag = "IslandSlotStyleAssembler",
            state = debugState
        ) {
            "歌词样式已提交: tag=$viewKey, view=${view.javaClass.simpleName}, " +
                    "source=$colorSource, textStyle=$textColorStyle, " +
                    "sessionRevision=${colorSession?.revision}, " +
                    "artworkRevision=${artworkRequest?.revision}, " +
                    "albumArt=${albumBitmap != null}, " +
                    "palette=${cachedCoverPalette?.second?.size ?: 0}, " +
                    "primary=${style.primary.color.contentToString()}, " +
                    "background=${style.highlight.background.contentToString()}, " +
                    "highlight=${style.highlight.foreground.contentToString()}, " +
                    "paintColor=$appliedTextPaintColor, styleChanged=$styleChanged, " +
                    "colorChanged=$colorChanged, dispatch=$dispatch, " +
                    "attached=${view.isAttachedToWindow}, shown=${view.isShown}, alpha=${view.alpha}"
        }
    }
}
