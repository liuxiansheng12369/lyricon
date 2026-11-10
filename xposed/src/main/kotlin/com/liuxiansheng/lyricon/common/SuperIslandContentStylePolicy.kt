/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common

import android.content.SharedPreferences

/**
 * 超级岛内容样式策略（V3 自研）。
 */
object SuperIslandContentStylePolicy {
    fun readAlbumCoverStyle(prefs: SharedPreferences): Int {
        return prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_ALBUM_COVER_STYLE,
            RootConstants.DEFAULT_HOOK_ISLAND_ALBUM_COVER_STYLE
        ).coerceIn(
            RootConstants.ISLAND_ALBUM_COVER_STYLE_DEFAULT,
            RootConstants.ISLAND_ALBUM_COVER_STYLE_HIDDEN
        )
    }

    fun isAlbumCoverVisible(style: Int): Boolean =
        style != RootConstants.ISLAND_ALBUM_COVER_STYLE_HIDDEN

    fun readMusicWaveStyle(prefs: SharedPreferences): Int {
        return prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_STYLE,
            RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_WAVE_STYLE
        ).coerceIn(
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_DEFAULT,
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_HIDDEN
        )
    }

    fun isMusicWaveVisible(style: Int): Boolean =
        style != RootConstants.ISLAND_MUSIC_WAVE_STYLE_HIDDEN

    fun usesMusicWaveCoverColor(style: Int): Boolean =
        style == RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_COLOR ||
                style == RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT

    fun usesMusicWaveGradient(style: Int): Boolean =
        style == RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT
}
