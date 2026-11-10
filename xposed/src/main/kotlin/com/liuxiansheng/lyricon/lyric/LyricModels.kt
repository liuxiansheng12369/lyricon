/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.lyric

data class LrcLine(val startTimeMs: Long, val content: String)

data class LyricSearchParams(
    val title: String,
    val artist: String,
    val album: String,
    val packageName: String,
    val duration: Long
)

interface ILyricProvider {
    suspend fun fetchLyrics(params: LyricSearchParams): List<LrcLine>?
}
