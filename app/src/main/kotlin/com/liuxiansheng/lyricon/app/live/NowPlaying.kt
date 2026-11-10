/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.app.live

import android.content.Context

/** 一帧"正在播放"实时状态（由 SystemUI 侧推送）。 */
data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val packageName: String = "",
    val lyric: String = "",
    val next: String = "",
    val updatedAt: Long = 0L,
) {
    val hasTrack: Boolean get() = title.isNotBlank() && title != LiveStateProvider.NOT_PLAYING
}

object NowPlayingReader {
    fun read(context: Context): NowPlaying {
        val p = LiveStateProvider.livePrefs(context)
        return NowPlaying(
            title = p.getString(LiveStateProvider.KEY_TITLE, "").orEmpty(),
            artist = p.getString(LiveStateProvider.KEY_ARTIST, "").orEmpty(),
            playing = p.getBoolean(LiveStateProvider.KEY_PLAYING, false),
            packageName = p.getString(LiveStateProvider.KEY_PKG, "").orEmpty(),
            lyric = p.getString(LiveStateProvider.KEY_LYRIC, "").orEmpty(),
            next = p.getString(LiveStateProvider.KEY_NEXT, "").orEmpty(),
            updatedAt = p.getLong(LiveStateProvider.KEY_UPDATED_AT, 0L),
        )
    }

    /** 预览里显示的主行：有歌词显示歌词，没有就退回歌名。 */
    fun mainLine(state: NowPlaying): String = when {
        state.lyric.isNotBlank() -> state.lyric
        state.hasTrack -> state.title
        else -> LiveStateProvider.NOT_PLAYING
    }

    /** 预览里的副行：下一句歌词 / 歌名-歌手。 */
    fun subLine(state: NowPlaying): String = when {
        state.next.isNotBlank() -> state.next
        state.hasTrack && state.artist.isNotBlank() -> "${state.title} · ${state.artist}"
        state.hasTrack -> state.title
        else -> "打开音乐 App 播放即可实时显示"
    }
}
