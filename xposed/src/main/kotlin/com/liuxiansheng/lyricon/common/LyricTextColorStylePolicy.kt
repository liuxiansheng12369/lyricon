/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common

import android.content.SharedPreferences

/**
 * 文字颜色样式策略（V3 自研）。
 */
object LyricTextColorStylePolicy {
    fun read(prefs: SharedPreferences): Int {
        return prefs.getInt(
            RootConstants.KEY_HOOK_TEXT_COLOR_STYLE,
            RootConstants.DEFAULT_HOOK_TEXT_COLOR_STYLE
        ).coerceIn(
            RootConstants.TEXT_COLOR_STYLE_DEFAULT,
            RootConstants.TEXT_COLOR_STYLE_CUSTOM
        )
    }

    /** 是否使用彩虹 7 色渐变。 */
    fun usesRainbow(style: Int): Boolean =
        style == RootConstants.TEXT_COLOR_STYLE_RAINBOW

    /** 是否从封面提取文字颜色。 */
    fun usesCoverColor(style: Int): Boolean =
        style == RootConstants.TEXT_COLOR_STYLE_COVER_COLOR ||
                style == RootConstants.TEXT_COLOR_STYLE_COVER_GRADIENT

    /** 是否从封面提取渐变颜色。 */
    fun usesCoverGradient(style: Int): Boolean =
        style == RootConstants.TEXT_COLOR_STYLE_COVER_GRADIENT

    /** 是否跟随状态栏文字颜色。 */
    fun followsStatusBar(style: Int): Boolean =
        style == RootConstants.TEXT_COLOR_STYLE_FOLLOW_STATUS_BAR
}
