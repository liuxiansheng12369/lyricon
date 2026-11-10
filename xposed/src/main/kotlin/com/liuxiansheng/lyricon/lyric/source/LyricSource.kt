/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.lyric.source

interface LyricSource {
    val id: String
    val displayName: String
    fun start(sink: LyricSink)
    fun stop()
    fun isAvailable(): Boolean
}
