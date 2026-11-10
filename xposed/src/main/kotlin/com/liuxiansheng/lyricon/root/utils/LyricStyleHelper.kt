/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.utils

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.util.TypedValue
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.common.LyricTextColorStylePolicy
import com.liuxiansheng.lyricon.hyper.common.SyllablePreferencePolicy
import com.liuxiansheng.lyricon.lyric.render.view.Highlight
import com.liuxiansheng.lyricon.lyric.render.view.LyricViewStyle
import com.liuxiansheng.lyricon.lyric.render.view.TextLook
import com.liuxiansheng.lyricon.lyric.render.view.WordMotion

/**
 * 歌词样式构建助手（V3 自研）
 * 负责根据用户配置和歌曲信息（如封面）生成 RichLyricLineView 所需的样式对象
 */
object LyricStyleHelper {
    /** 老词幕同款半透明：保留 RGB 色相/亮度，只把 alpha 乘 factor（封面背景用 0.7 = 70% 半透明区分播放进度） */
    private fun withAlphaScale(color: Int, factor: Float): Int {
        val alpha = (android.graphics.Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return android.graphics.Color.argb(
            alpha,
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color)
        )
    }

    /** 彩虹 16 色（亮色系，1.0.9 从 7 色扩到 16 色：按色相插过渡色，逐字过渡更细腻） */
    private val RAINBOW_COLORS = intArrayOf(
        android.graphics.Color.parseColor("#FF6B6B"), // 亮红
        android.graphics.Color.parseColor("#FF8787"), // 珊瑚红
        android.graphics.Color.parseColor("#FFA94D"), // 亮橙
        android.graphics.Color.parseColor("#FFC078"), // 蜜橙
        android.graphics.Color.parseColor("#FFE066"), // 亮黄
        android.graphics.Color.parseColor("#FFEC99"), // 奶油黄
        android.graphics.Color.parseColor("#C0EB75"), // 黄绿
        android.graphics.Color.parseColor("#69DB7C"), // 亮绿
        android.graphics.Color.parseColor("#38D9A9"), // 青绿
        android.graphics.Color.parseColor("#66D9E8"), // 青蓝
        android.graphics.Color.parseColor("#74C0FC"), // 亮蓝
        android.graphics.Color.parseColor("#4DABF7"), // 天蓝
        android.graphics.Color.parseColor("#9775FA"), // 亮靛
        android.graphics.Color.parseColor("#B197FC"), // 淡紫
        android.graphics.Color.parseColor("#DA77F2"), // 亮紫
        android.graphics.Color.parseColor("#F783AC"), // 粉红
    )

    /**
     * 超级岛未唱字：自带深色底 + 玻璃背景，不能套状态栏那套 RGB×0.5 重压，
     * 只把亮度轻微下调（色相/透明度不变）。1.0.8：×0.82。
     */
    private const val ISLAND_UNPLAYED_DIM_FACTOR = 0.82f

    private fun dimRgbKeepAlpha(color: Int, factor: Float): Int = android.graphics.Color.argb(
        android.graphics.Color.alpha(color),
        (android.graphics.Color.red(color) * factor).toInt().coerceIn(0, 255),
        (android.graphics.Color.green(color) * factor).toInt().coerceIn(0, 255),
        (android.graphics.Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )

    /**
     * 构建歌词样式对象
     */
    internal fun buildStyle(
        prefs: SharedPreferences,
        res: Resources,
        mode: Int,
        colorSession: CoverColorHelper.ColorSession? = CoverColorHelper.currentSession(),
        artworkRequest: CoverColorHelper.ArtworkRequest? = null,
        textColorOverride: Int? = null
    ): LyricViewStyle {
        val syllableSettings = SyllablePreferencePolicy.read(prefs)
        val fontSize =
            prefs.getInt(RootConstants.KEY_HOOK_TEXT_SIZE, RootConstants.DEFAULT_HOOK_TEXT_SIZE)
        val font = FontHelper.loadFont(prefs)

        val textSizeRatio = prefs.getFloat(
            RootConstants.KEY_HOOK_TEXT_SIZE_RATIO,
            RootConstants.DEFAULT_HOOK_TEXT_SIZE_RATIO
        )
        val primarySizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            fontSize.toFloat(),
            res.displayMetrics
        )

        // Style 层永远允许 secondary 显示；翻译开关通过 view.displayTranslation/displayRoma
        // 控制 assembler 选什么内容，无内容时 assembler 返回 alwaysShow=false → secondary GONE
        val showSecondary = mode == RootConstants.ISLAND_CONTENT_MODE_LYRIC ||
                mode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO

        val isLyricMode = mode == RootConstants.ISLAND_CONTENT_MODE_LYRIC
        val centerIfPossible = if (isLyricMode) {
            prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_LYRIC,
                RootConstants.DEFAULT_HOOK_CENTER_LYRIC
            )
        } else {
            prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_MUSIC_INFO,
                prefs.getBoolean(
                    RootConstants.KEY_HOOK_CENTER_LYRIC,
                    RootConstants.DEFAULT_HOOK_CENTER_MUSIC_INFO
                )
            )
        }

        val textColorStyle = LyricTextColorStylePolicy.read(prefs)
        // 动画总开关：关掉时下面所有动画相关（动画效果/镜像/换句动画/逐字跳动）一律停用
        val animEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ANIM_ENABLE, true)
        val useCoverColor = LyricTextColorStylePolicy.usesCoverColor(textColorStyle)
        val useCoverGradient = LyricTextColorStylePolicy.usesCoverGradient(textColorStyle)

        // V3 扩展：彩虹（颜色样式 4）优先于一切颜色策略（老词幕行为）；
        // 其次用户自定义固定色；然后才是原策略（状态栏跟随 / 封面取色 / 默认白）。
        // 彩虹已并入"歌词颜色样式"选择器，不再使用独立开关 KEY_HOOK_RAINBOW_ENABLED。
        val fixedTextColor = if (prefs.contains(RootConstants.KEY_HOOK_TEXT_COLOR)) {
            prefs.getInt(RootConstants.KEY_HOOK_TEXT_COLOR, android.graphics.Color.WHITE)
        } else {
            null
        }

        val primaryColors: IntArray
        val bgColors: IntArray
        val hlColors: IntArray

        if (LyricTextColorStylePolicy.usesRainbow(textColorStyle)) {
            primaryColors = RAINBOW_COLORS
            // 未唱（bg）：同色相、80% 不透明 + 亮度轻微下调（超级岛深色底专用，不等同状态栏的 50% 重压）
            bgColors = RAINBOW_COLORS.map {
                dimRgbKeepAlpha((it and 0x00FFFFFF) or (0xCC shl 24), ISLAND_UNPLAYED_DIM_FACTOR)
            }.toIntArray()
            hlColors = RAINBOW_COLORS
        } else if (useCoverColor) {
            // V3 修复：封面取色优先于自定义固定色。
            // 之前 fixedTextColor 分支在前，只要 prefs 里存在 key_hook_text_color
            // （设置过自定义颜色），封面取色就永远轮不到执行。
            val palette = if (artworkRequest != null) {
                CoverColorHelper.getCachedColors(useCoverGradient, artworkRequest)
            } else {
                colorSession?.let {
                    CoverColorHelper.getCachedColors(useCoverGradient, it)
                }
            }
            // 完整取色方式：主色=占优色(first)，背景=暗化色(second)
            val lightColors = palette?.first
            val darkColors = palette?.second
            val dominant = lightColors?.firstOrNull()
            val backdrop = darkColors?.firstOrNull()
            if (dominant != null) {
                // 对齐老词幕 2.9.78（SuperIslandConfig.buildLyricViewStyle）：
                // 封面取色 → 高亮/主色 = 封面主色原样（不提亮不暗化，贴近封面原色）；
                // 背景 = 主色 70% 半透明（alpha×0.7，色相/亮度不变）——用透明度区分播放进度。
                // 此前 V3 用提亮 V=1.0 做高亮 + 暗化 0.35 做背景，偏离封面原色、
                // 深色封面下背景黑化看不清 → 替换为老词幕做法。
                if (useCoverGradient && lightColors != null && lightColors.size > 1) {
                    primaryColors = lightColors
                    bgColors = lightColors.map { withAlphaScale(it, 0.7f) }.toIntArray()
                    hlColors = lightColors
                } else {
                    primaryColors = intArrayOf(dominant)              // 无逐字/标题 -> 封面占优色(原样)
                    bgColors = intArrayOf(withAlphaScale(dominant, 0.7f)) // 未唱到 -> 主色 70% 半透明
                    hlColors = intArrayOf(dominant)                   // 已唱到 -> 封面占优色(原样)
                }
            } else {
                primaryColors = intArrayOf(Color.WHITE)
                bgColors = intArrayOf(Color.argb(128, 255, 255, 255))
                hlColors = intArrayOf(Color.WHITE)
            }
        } else if (fixedTextColor != null) {
            primaryColors = intArrayOf(fixedTextColor)
            bgColors = intArrayOf(withAlphaScale(fixedTextColor, 0.7f)) // 对齐老词幕：背景=主色 70% 半透明
            hlColors = intArrayOf(fixedTextColor)
        } else if (textColorOverride != null) {
            primaryColors = intArrayOf(textColorOverride)
            bgColors = intArrayOf(withAlphaScale(textColorOverride, 0.7f)) // 对齐老词幕：背景=主色 70% 半透明
            hlColors = intArrayOf(textColorOverride)
        } else {
            primaryColors = intArrayOf(Color.WHITE)
            bgColors = intArrayOf(withAlphaScale(Color.WHITE, 0.7f)) // 对齐老词幕：背景=白色 70% 半透明
            hlColors = intArrayOf(Color.WHITE)
        }

        return LyricViewStyle(
            primary = TextLook(
                colors = primaryColors,
                size = primarySizePx,
                typeface = font.typeface,
                fontVariationSettings = font.variationSettings,
                relativeProgress = syllableSettings.relativeProgress,
                relativeHighlight = syllableSettings.relativeHighlight,
            ),
            secondary = TextLook(
                colors = if (showSecondary) primaryColors else intArrayOf(Color.TRANSPARENT),
                size = if (showSecondary) primarySizePx * textSizeRatio else 0f,
                typeface = font.typeface,
                fontVariationSettings = font.variationSettings,
            ),
            highlight = Highlight(
                background = bgColors,
                foreground = hlColors,
            ),
            // V3 修复：彩虹只是换颜色，不改变渲染方式。
            // 之前 gradient=rainbowEnabled||gradient_progress，一开彩虹就强制羽化渐变，
            // 高亮层走 ComposeShader(DST_IN) 羽化分支，在 SystemUI 硬件加速下兼容性差，
            // 导致已播高亮层画不出来——彩色出现但无逐字。
            // 现在 gradient 仅由用户自己的羽化开关控制，彩虹与单色渲染路径完全一致。
            // 【删除羽化功能】3.0.546：羽化（渐变高亮柔化）在 SystemUI 硬件加速下兼容性差，
            // 按用户要求彻底移除——硬编码 false，设置里想开也开不了。
            gradient = false,
            lineDisplay = syllableSettings.lineDisplay,
            wordMotion = WordMotion(
                enabled = animEnabled && prefs.getBoolean(
                    RootConstants.KEY_HOOK_WORD_MOTION_ENABLED,
                    RootConstants.DEFAULT_HOOK_WORD_MOTION_ENABLED
                ),
            ),
            centerIfPossible = centerIfPossible,
            rightIfPossible = isLyricMode && prefs.getBoolean(
                RootConstants.KEY_HOOK_RIGHT_LYRIC,
                RootConstants.DEFAULT_HOOK_RIGHT_LYRIC
            ),
            // 歌词镜像倒影（与设置页 key 一致：island_lyric_mirror；动画总开关关掉时一并停用）
            // 开了"预显示下一句"时默认不给倒影：副行就在下面，倒影会和下一句挤在一起
            mirror = animEnabled && !prefs.getBoolean(
                RootConstants.KEY_HOOK_NEXT_LYRIC_LINE,
                RootConstants.DEFAULT_HOOK_NEXT_LYRIC_LINE
            ) && prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_LYRIC_MIRROR,
                RootConstants.DEFAULT_HOOK_ISLAND_LYRIC_MIRROR
            ),
            // 歌词动画效果（0=静态，1~25=老词幕效果，与设置页 key 一致：island_lyric_effect）
            // 动画总开关关掉 → 一律静态，回到正常逐字渲染
            lyricEffect = if (animEnabled) prefs.getInt(
                RootConstants.KEY_HOOK_LYRIC_EFFECT,
                RootConstants.DEFAULT_HOOK_LYRIC_EFFECT
            ) else 0,
            // 效果模式统一色：用户设置的歌词文字颜色；没设置过就用白色。
            // 效果里不再用彩虹/封面多色（避免"效果里默认红色"、自成一套配色）。
            effectSolidColor = fixedTextColor ?: Color.WHITE,
            transitionConfig = null,
        )
    }

    /**
     * 兼容读取"下一句动画"索引：旧版曾写入 String（"scale_up"/"none"），
     * 直接 getInt 会 ClassCastException 崩 SystemUI——先按类型分发，平滑迁移到 Int。
     */
    fun readLineSwitchAnim(prefs: SharedPreferences): Int {
        // 动画总开关关掉时换句动画一并停用（等效"无动画"）
        if (!prefs.getBoolean(RootConstants.KEY_HOOK_ANIM_ENABLE, true)) {
            return RootConstants.LINE_SWITCH_ANIM_NONE
        }
        val v = prefs.all[RootConstants.KEY_HOOK_LINE_SWITCH_ANIM]
        return when (v) {
            is Int -> v
            is String -> if (v == "none") {
                RootConstants.LINE_SWITCH_ANIM_NONE
            } else {
                RootConstants.LINE_SWITCH_ANIM_SCALE_UP
            }
            else -> RootConstants.DEFAULT_HOOK_LINE_SWITCH_ANIM
        }
    }
}

