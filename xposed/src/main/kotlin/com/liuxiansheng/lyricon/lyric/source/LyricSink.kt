/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.lyric.source

import com.liuxiansheng.lyricon.hyper.lyric.model.LyricMediaMetadata
import com.liuxiansheng.lyricon.hyper.lyric.model.Song
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.IRichLyricLine

interface LyricSink {
    fun onSongChanged(song: Song?)
    fun onLyricLine(line: IRichLyricLine)
    fun onPlainText(text: String?)
    fun onStop()
    fun onMetadata(metadata: LyricMediaMetadata?)
    fun onPlaybackStateChanged(isPlaying: Boolean, playbackSpeed: Float = Float.NaN)
    fun onPositionChanged(position: Long, playbackSpeed: Float = Float.NaN)
}
