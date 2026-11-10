/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.config

import android.content.SharedPreferences
import com.liuxiansheng.lyricon.hyper.common.LyricTextColorStylePolicy
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.common.SuperIslandContentStylePolicy
import com.liuxiansheng.lyricon.hyper.common.SuperIslandWidthPolicy
import com.liuxiansheng.lyricon.hyper.common.SyllablePreferencePolicy
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentMode
import com.liuxiansheng.lyricon.hyper.root.island.sizing.IslandSlotGeometryConfig
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger

/**
 * 槽位运行时配置读取器（V3 自研）。
 * 读取全部渲染相关偏好（模式/宽度/文字/动画/跑马灯/逐字/翻译/颜色/字体/词动效）。
 */
internal object IslandSlotRuntimeConfigReader {
    /** 固定模式下的岛宽（dp）——原"右侧内容最大宽"设置已按用户要求移除，固定使用默认值 */
    private const val FIXED_ISLAND_WIDTH_DP = 72

    fun read(prefs: SharedPreferences): IslandSlotRuntimeConfig {
        val syllableSettings = SyllablePreferencePolicy.read(prefs)
        val activeMode = prefs.getInt(
            RootConstants.KEY_HOOK_LYRIC_MODE,
            RootConstants.DEFAULT_HOOK_LYRIC_MODE
        )
        val showAlbum = SuperIslandContentStylePolicy.isAlbumCoverVisible(
            SuperIslandContentStylePolicy.readAlbumCoverStyle(prefs)
        )
        val showRhythm = SuperIslandContentStylePolicy.isMusicWaveVisible(
            SuperIslandContentStylePolicy.readMusicWaveStyle(prefs)
        )
        val disableWidthLimit = prefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_DISABLE_WIDTH_LIMIT,
            RootConstants.DEFAULT_HOOK_ISLAND_DISABLE_WIDTH_LIMIT
        )
        val minIslandWidth = SuperIslandWidthPolicy.minIslandWidth(showAlbum, showRhythm)
        val maxIslandWidth = SuperIslandWidthPolicy.maxIslandWidth(
            showRhythm = showRhythm,
            disableWidthLimit = disableWidthLimit
        )
        val fixedIslandWidth = SuperIslandWidthPolicy.normalizeIslandWidth(
            islandWidth = FIXED_ISLAND_WIDTH_DP,
            showAlbum = showAlbum,
            showRhythm = showRhythm,
            disableWidthLimit = disableWidthLimit
        )
        val widthMode = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_WIDTH_MODE,
            RootConstants.DEFAULT_HOOK_ISLAND_WIDTH_MODE
        ).coerceIn(
            RootConstants.ISLAND_WIDTH_MODE_FIXED,
            RootConstants.ISLAND_WIDTH_MODE_DYNAMIC
        )
        val dynamicWidthEnabled =
            widthMode == RootConstants.ISLAND_WIDTH_MODE_DYNAMIC
        val dynamicMinWidth = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MIN_WIDTH,
            minIslandWidth.coerceAtLeast(RootConstants.DEFAULT_HOOK_ISLAND_DYNAMIC_MIN_WIDTH)
        ).coerceIn(minIslandWidth, maxIslandWidth)
        val dynamicMaxWidth = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MAX_WIDTH,
            fixedIslandWidth.coerceIn(
                RootConstants.DEFAULT_HOOK_ISLAND_DYNAMIC_MIN_WIDTH,
                maxIslandWidth
            )
        ).coerceIn(dynamicMinWidth, maxIslandWidth)
        val effectiveMinWidth = if (dynamicWidthEnabled) {
            dynamicMinWidth
        } else {
            minIslandWidth
        }
        val effectiveMaxWidth = if (dynamicWidthEnabled) {
            dynamicMaxWidth
        } else {
            fixedIslandWidth
        }
        val dynamicWidthBasis = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS,
            RootConstants.DEFAULT_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS
        ).coerceIn(
            RootConstants.ISLAND_DYNAMIC_WIDTH_BASIS_ALL,
            RootConstants.ISLAND_DYNAMIC_WIDTH_BASIS_LYRIC_ONLY
        )
        val config = IslandSlotRuntimeConfig(
            activeMode = activeMode,
            leftMode = if (activeMode == 1) {
                RootConstants.ISLAND_CONTENT_MODE_LYRIC
            } else if (!prefs.getBoolean(RootConstants.KEY_ISLAND_LEFT_ENABLED, true)) {
                RootConstants.ISLAND_CONTENT_MODE_NONE
            } else {
                readContentModeFromString(
                    prefs,
                    RootConstants.KEY_ISLAND_LEFT_CONTENT_MODE,
                    RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT,
                    RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT
                )
            },
            rightMode = if (activeMode == 1) {
                RootConstants.ISLAND_CONTENT_MODE_LYRIC
            } else if (!prefs.getBoolean(RootConstants.KEY_ISLAND_RIGHT_ENABLED, true)) {
                RootConstants.ISLAND_CONTENT_MODE_NONE
            } else {
                readContentModeFromString(
                    prefs,
                    RootConstants.KEY_ISLAND_RIGHT_CONTENT_MODE,
                    RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT,
                    RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT
                )
            },
            geometry = IslandSlotGeometryConfig(
                isDynamicWidth = dynamicWidthEnabled,
                showAlbum = showAlbum,
                showRhythm = showRhythm,
                leftPaddingLeftDp = prefs.getInt(
                    RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT,
                    RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT
                ),
                leftPaddingRightDp = prefs.getInt(
                    RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT,
                    RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT
                ),
                rightPaddingLeftDp = prefs.getInt(
                    RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT,
                    RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT
                ),
                rightPaddingRightDp = prefs.getInt(
                    RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT,
                    RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT
                ),
                leftMinWidthDp = SuperIslandWidthPolicy.leftContentWidth(
                    islandWidth = effectiveMinWidth,
                    showAlbum = showAlbum,
                    showRhythm = showRhythm,
                    disableWidthLimit = disableWidthLimit
                ),
                rightMinWidthDp = effectiveMinWidth,
                leftMaxWidthDp = SuperIslandWidthPolicy.leftContentWidth(
                    islandWidth = effectiveMaxWidth,
                    showAlbum = showAlbum,
                    showRhythm = showRhythm,
                    disableWidthLimit = disableWidthLimit
                ),
                rightMaxWidthDp = effectiveMaxWidth
            ),
            dynamicWidthBasis = dynamicWidthBasis,
            pauseBehavior = prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
                RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
            ),
            textSizeSp = prefs.getInt(
                RootConstants.KEY_HOOK_TEXT_SIZE,
                RootConstants.DEFAULT_HOOK_TEXT_SIZE
            ),
            textSizeRatio = prefs.getFloat(
                RootConstants.KEY_HOOK_TEXT_SIZE_RATIO,
                RootConstants.DEFAULT_HOOK_TEXT_SIZE_RATIO
            ),
            fontWeight = prefs.getInt(
                RootConstants.KEY_HOOK_FONT_WEIGHT,
                RootConstants.DEFAULT_HOOK_FONT_WEIGHT
            ),
            fontItalic = prefs.getBoolean(
                RootConstants.KEY_HOOK_FONT_ITALIC,
                RootConstants.DEFAULT_HOOK_FONT_ITALIC
            ),
            gradientProgress = prefs.getBoolean(
                RootConstants.KEY_HOOK_GRADIENT_PROGRESS,
                RootConstants.DEFAULT_HOOK_GRADIENT_PROGRESS
            ),
            centerLyric = prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_LYRIC,
                RootConstants.DEFAULT_HOOK_CENTER_LYRIC
            ),
            centerMusicInfo = prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_MUSIC_INFO,
                prefs.getBoolean(
                    RootConstants.KEY_HOOK_CENTER_LYRIC,
                    RootConstants.DEFAULT_HOOK_CENTER_MUSIC_INFO
                )
            ),
            rightLyric = prefs.getBoolean(
                RootConstants.KEY_HOOK_RIGHT_LYRIC,
                RootConstants.DEFAULT_HOOK_RIGHT_LYRIC
            ),
            lyricAnimationEnabled = prefs.getBoolean(
                RootConstants.KEY_HOOK_ANIM_ENABLE,
                RootConstants.DEFAULT_HOOK_ANIM_ENABLE
            ),
            lyricAnimationId = prefs.getString(
                RootConstants.KEY_HOOK_ANIM_ID,
                RootConstants.DEFAULT_HOOK_ANIM_ID
            ) ?: RootConstants.DEFAULT_HOOK_ANIM_ID,
            syllableRelative = syllableSettings.relativeProgress,
            syllableHighlight = syllableSettings.relativeHighlight,
            syllableLineDisplay = syllableSettings.lineDisplay,
            disableTranslation = prefs.getBoolean(
                RootConstants.KEY_HOOK_DISABLE_TRANSLATION,
                RootConstants.DEFAULT_HOOK_DISABLE_TRANSLATION
            ),
            translationOnly = prefs.getBoolean(
                RootConstants.KEY_HOOK_TRANSLATION_ONLY,
                RootConstants.DEFAULT_HOOK_TRANSLATION_ONLY
            ),
            swapTranslation = prefs.getBoolean(
                RootConstants.KEY_HOOK_SWAP_TRANSLATION,
                RootConstants.DEFAULT_HOOK_SWAP_TRANSLATION
            ),
            nextLyricLine = prefs.getBoolean(
                RootConstants.KEY_HOOK_NEXT_LYRIC_LINE,
                RootConstants.DEFAULT_HOOK_NEXT_LYRIC_LINE
            ),
            autoSwitchTranslation = prefs.getBoolean(
                RootConstants.KEY_HOOK_AUTO_SWITCH_TRANSLATION,
                RootConstants.DEFAULT_HOOK_AUTO_SWITCH_TRANSLATION
            ),
            textColorStyle = LyricTextColorStylePolicy.read(prefs),
            customFontPath = prefs.getString(RootConstants.KEY_HOOK_CUSTOM_FONT_PATH, null)
                .orEmpty(),
            narrowLatinFont = prefs.getBoolean(
                RootConstants.KEY_HOOK_NARROW_LATIN_FONT,
                RootConstants.DEFAULT_HOOK_NARROW_LATIN_FONT
            ),
            wordMotionEnabled = prefs.getBoolean(
                RootConstants.KEY_HOOK_WORD_MOTION_ENABLED,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_ENABLED
            )
        )
        if (!HookLogger.isDebugEnabled) return config
        val summary = listOf(
            "mode=${config.activeMode}/${config.leftMode}/${config.rightMode}",
            "width=${config.geometry.isDynamicWidth}:${config.geometry.leftMinWidthDp}-" +
                    "${config.geometry.leftMaxWidthDp}/${config.geometry.rightMinWidthDp}-" +
                    "${config.geometry.rightMaxWidthDp}",
            "album=${config.geometry.showAlbum}",
            "rhythm=${config.geometry.showRhythm}",
            "pause=${config.pauseBehavior}",
            "text=${config.textSizeSp}/${config.textSizeRatio}/${config.fontWeight}/${config.fontItalic}",
            "animation=${config.lyricAnimationEnabled}:${config.lyricAnimationId}",
            "translation=${config.disableTranslation}/${config.translationOnly}/${config.swapTranslation}",
            "syllable=${config.syllableLineDisplay}/${config.syllableRelative}/${config.syllableHighlight}",
            "next=${config.nextLyricLine}/${config.autoSwitchTranslation}",
            "color=${config.textColorStyle}",
            "font=${config.customFontPath.isNotBlank()}/${config.narrowLatinFont}",
            "wordMotion=${config.wordMotionEnabled}"
        ).joinToString(",")
        HookLogger.dState(
            stateId = "IslandSlotRuntimeConfig",
            tag = "IslandSlotRuntimeConfig",
            state = summary
        ) {
            "超级岛歌词实际配置: $summary, styleSignature=${config.styleSignature.hashCode()}"
        }
        return config
    }

    private fun readContentMode(
        prefs: SharedPreferences,
        key: String,
        defaultValue: Int
    ): Int {
        return prefs.getInt(key, defaultValue).takeIf {
            it == RootConstants.ISLAND_CONTENT_MODE_NONE ||
                    it == RootConstants.ISLAND_CONTENT_MODE_LYRIC ||
                    it == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO ||
                    it >= 100
        } ?: defaultValue
    }

    /** 3.0.881：优先读设置页字符串模式键（55 种内容模式），未设置时回退旧 int 键。 */
    private fun readContentModeFromString(
        prefs: SharedPreferences,
        stringKey: String,
        legacyIntKey: String,
        defaultInt: Int
    ): Int {
        val str = prefs.getString(stringKey, null)
        if (!str.isNullOrBlank()) {
            return IslandContentMode.fromValue(str).toInt()
        }
        return readContentMode(prefs, legacyIntKey, defaultInt)
    }
}
