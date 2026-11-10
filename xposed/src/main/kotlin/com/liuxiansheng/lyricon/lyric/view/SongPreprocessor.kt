/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.lyric.view

import com.liuxiansheng.lyricon.hyper.lyric.model.LyricWord
import com.liuxiansheng.lyricon.hyper.lyric.model.RichLyricLine
import com.liuxiansheng.lyricon.hyper.lyric.model.Song
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.IRichLyricLine
import com.liuxiansheng.lyricon.hyper.lyric.model.lyricMetadataOf

internal class SongPreprocessor(private val placeholder: TitleSlot) {

    companion object {
        /** 首句歌词前至少留 1 秒才插入占位行（歌名/倒计时）；0 秒无空窗不插 */
        internal const val MIN_PLACEHOLDER_DURATION_MS = 1_000L
        internal const val METADATA_TITLE_LINE = "TitleLine"
        internal const val METADATA_COUNTDOWN_LINE = "CountdownLine"

        /** 无歌词时副行提示文案（纯音乐/无歌词歌曲） */
        internal const val PURE_MUSIC_HINT = "纯音乐，请欣赏"
    }

    fun prepare(song: Song): List<TimedLine> {
        val filled = fillGap(song)
        val lines = mutableListOf<TimedLine>()
        var prev: TimedLine? = null
        filled.lyrics?.forEach { lyric ->
            val tl = TimedLine(lyric).also {
                it.previous = prev
                prev?.next = it
            }
            lines.add(tl)
            prev = tl
        }
        return lines
    }

    private fun fillGap(song: Song): Song {
        val lyrics = song.lyrics?.toMutableList() ?: mutableListOf()
        if (lyrics.isEmpty()) {
            val title = songTitle(song) ?: return song
            val d = if (song.duration > 0) song.duration else Long.MAX_VALUE
            // 整首没有歌词（纯音乐/无歌词歌曲）：显示"歌名 + 纯音乐，请欣赏"，
            // 不让空歌词去匹配别的歌的歌词，也不让岛里一直空着。
            lyrics.add(titleLine(d, d, title, PURE_MUSIC_HINT, PURE_MUSIC_HINT))
        } else {
            val first = lyrics.first()
            if (first.begin < MIN_PLACEHOLDER_DURATION_MS) return song

            var end = first.begin
            if (end > 1) end--
            val line = when (placeholder) {
                TitleSlot.NONE -> null
                TitleSlot.NAME_ARTIST,
                TitleSlot.NAME -> songTitle(song)?.let { titleLine(end, end, it) }
            } ?: return song
            lyrics.add(0, line)
        }
        song.lyrics = lyrics
        return song
    }

    private fun titleLine(
        end: Long,
        duration: Long,
        text: String,
        secondary: String? = null,
        translation: String? = null
    ) =
        RichLyricLine(
            end = end,
            duration = duration,
            text = text,
            secondary = secondary,
            translation = translation
        ).apply {
            metadata = lyricMetadataOf(METADATA_TITLE_LINE to "true")
            // 歌名按字符拆词：与歌词逐字效果一致（每字一个词、时长均分），
            // 否则整行折叠词是线性渐变，不是歌词那种逐字推进。
            if (duration > 0L && text.isNotBlank()) {
                val charCount = text.length
                val stepMs = duration / charCount
                val startMs = (end - duration).coerceAtLeast(0L)
                words = text.mapIndexed { i, ch ->
                    val b = startMs + i * stepMs
                    LyricWord(
                        begin = b,
                        end = (b + stepMs).coerceAtMost(end),
                        duration = stepMs.toLong(),
                        text = ch.toString()
                    )
                }
            }
        }

    private fun songTitle(song: Song): String? {
        val name = song.name
        val artist = song.artist
        return when (placeholder) {
            TitleSlot.NONE -> null
            TitleSlot.NAME_ARTIST -> when {
                !name.isNullOrBlank() && !artist.isNullOrBlank() -> "$name - $artist"
                !name.isNullOrBlank() -> name
                else -> null
            }

            TitleSlot.NAME -> name?.takeIf { it.isNotBlank() }
        }
    }
}

internal class TimedLine(val line: IRichLyricLine) : IRichLyricLine by line {
    var previous: TimedLine? = null
    var next: TimedLine? = null
}


