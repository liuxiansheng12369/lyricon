/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
@file:Suppress("unused")

package com.liuxiansheng.lyricon.xposed.systemui.media

import com.liuxiansheng.lyricon.lyric.model.LyricWord
import com.liuxiansheng.lyricon.lyric.model.RichLyricLine
import com.liuxiansheng.lyricon.lyric.model.Song
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * 歌词信息解析器（移植自 HyperLyric LyricInfoParser）
 *
 * 从 MediaSession 的 lyricInfo 字段解析 JSON 格式歌词数据，
 * 支持通过相同时间戳的两行匹配翻译（第一行原文，第二行翻译）。
 *
 * 用于补充 Lyricon Provider 接口未提供翻译的音乐软件（如汽水音乐）。
 */
object LyricInfoParser {

    private val LRC_TIME_RE = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]")

    /** 词级时间标签（卡拉OK逐字）：<mm:ss.ms>。只用尖括号形式，避免与 LRC 行标签混淆。 */
    private val ELRC_WORD_RE = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>")

    /** LRC 元信息标签（仅纯文本兜底解析用）：[ti:歌名] [ar:歌手] 等。 */
    private val LRC_META_RE =
        Pattern.compile("^\\[(ti|ar|al|by|offset|length|re|ve):(.*)]$", Pattern.CASE_INSENSITIVE)

    fun parse(json: String): Song? = parsePayload(json)?.song

    fun parsePayload(json: String): LyricInfoPayload? {
        // 兼容：部分播放器/Provider 直接把 LRC/ELRC 纯文本塞进 lyricInfo，不套 JSON
        val obj = try {
            JSONObject(json)
        } catch (_: Exception) {
            null
        }
        if (obj == null) {
            val song = parseRawText(json) ?: return null
            return LyricInfoPayload(
                song = song,
                songId = null,
                title = song.name,
                artist = song.artist,
                album = null
            )
        }
        return try {
            // 新版协议：rawLyric=逐字 ELRC，lyric=逐行 LRC，translationLyric=翻译 LRC
            // 旧版协议：lyric 里直接是 ELRC/LRC，translation 是格式说明
            val rawLyric = obj.optString("rawLyric", "").trim()
            val lyricRaw = obj.optString("lyric", "").trim()
            val translationLyric = obj.optString("translationLyric", "").trim()
                .ifBlank { obj.optString("translatedLyric", "").trim() }
                .ifBlank { obj.optString("transLyric", "").trim() }

            // 优先用逐字 rawLyric，没有再用 lyric；逐字/逐行按行内词级标签自动识别，
            // 不依赖 format 字段（ColorOS Provider 常缺省/写错 format，导致卡拉OK逐字丢失）
            val hasRawLyric = rawLyric.isNotBlank()
            val mainLyric = if (hasRawLyric) rawLyric else lyricRaw
            if (mainLyric.isBlank()) return null

            val title = obj.optionalText("songName")
            val artist = obj.optionalText("artist")
            val album = obj.optionalText("album")
            val songId = obj.optionalText("songId")

            val allLines = mainLyric.lines().filter { it.isNotBlank() }

            // 解析翻译 LRC：timeMs -> 翻译文本
            val transMap = mutableMapOf<Long, String>()
            if (translationLyric.isNotBlank()) {
                translationLyric.lines().filter { it.isNotBlank() }.forEach { tline ->
                    val tMs = extractTimeMs(tline)
                    if (tMs >= 0) {
                        val tText = extractLrcText(tline)
                        if (!tText.isNullOrBlank()) transMap[tMs] = tText
                    }
                }
            }
            val hasTranslation = transMap.isNotEmpty()

            // 提取每行的时间戳和文本
            val parsedLines = allLines.map { line ->
                val timeMs = extractTimeMs(line)
                ParsedLine(timeMs, line)
            }

            val resultLines = mutableListOf<RichLyricLine>()
            for (current in parsedLines) {
                val mainLine = parseLine(current.raw) ?: continue
                if (hasTranslation && current.timeMs >= 0) {
                    transMap[current.timeMs]?.let { t ->
                        resultLines.add(mainLine.copy(translation = t))
                        continue
                    }
                }
                resultLines.add(mainLine)
            }

            // 补全 end/duration，修正最后一个词的时间（卡拉OK句尾高亮延长到下一句）
            finalizeLines(resultLines)

            if (resultLines.isEmpty()) return null
            LyricInfoPayload(
                song = Song(
                    id = songId,
                    name = title,
                    artist = artist,
                    lyrics = resultLines
                ),
                songId = songId,
                title = title,
                artist = artist,
                album = album
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 解析单行为 RichLyricLine，按行内是否含词级时间标签自动识别 ELRC/LRC。
     * 不依赖 format 字段（ColorOS Provider 常缺省/写错 format，导致卡拉OK逐字丢失）；
     * ELRC 解析失败时降级 LRC，避免整行丢失。
     */
    private fun parseLine(raw: String): RichLyricLine? {
        return if (hasWordTags(raw)) {
            parseElrcLine(raw) ?: parseLrcLine(raw)
        } else {
            parseLrcLine(raw)
        }
    }

    private fun hasWordTags(raw: String): Boolean = ELRC_WORD_RE.matcher(raw).find()

    /** 提取纯 LRC 行文本（去掉行首全部时间戳，兼容一行多时间戳） */
    private fun extractLrcText(raw: String): String? {
        val trimmed = raw.trim()
        val m = LRC_TIME_RE.matcher(trimmed)
        if (!m.find()) return null
        var lastEnd = m.end()
        while (m.find()) lastEnd = m.end()
        return trimmed.substring(lastEnd).trim().takeIf { it.isNotBlank() }
    }

    /**
     * 提取行文本（去掉时间戳和词级标签）。
     */
    private fun extractText(raw: String, format: String): String? {
        return when (format) {
            "elrc" -> {
                val lineRe = Pattern.compile("\\[\\d{2}:\\d{2}\\.\\d{2,3}]\\s*(.*)")
                val lm = lineRe.matcher(raw.trim())
                if (!lm.matches()) return null
                val wordPart = lm.group(1) ?: ""
                val wordRe = Pattern.compile("<\\d{2}:\\d{2}\\.\\d{2,3}>")
                wordRe.matcher(wordPart).replaceAll("").trim().takeIf { it.isNotBlank() }
            }
            else -> parseLrcLine(raw)?.text
        }
    }

    /**
     * 提取行首时间戳（毫秒），用于翻译匹配。
     * 取 [mm:ss.ms] 行首标签，不取词级标签。
     */
    private fun extractTimeMs(raw: String): Long {
        val m = LRC_TIME_RE.matcher(raw)
        if (!m.find()) return -1
        return m.group(1)!!.toLong() * 60000 +
                m.group(2)!!.toLong() * 1000 +
                (if (m.group(3)!!.length == 2) m.group(3)!!.toLong() * 10 else m.group(3)!!.toLong())
    }

    /**
     * 解析 ELRC 单行：[mm:ss.ms] <mm:ss.ms>word <mm:ss.ms>word...
     */
    private fun parseElrcLine(raw: String): RichLyricLine? {
        val lineRe = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]\\s*(.*)")
        val wordRe = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)")
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val lm = lineRe.matcher(trimmed)
        if (!lm.matches()) return null

        val wordPart = lm.group(4) ?: ""

        val wm = wordRe.matcher(wordPart)
        val words = mutableListOf<LyricWord>()
        while (wm.find()) {
            val wordBegin = wm.group(1)!!.toLong() * 60000 +
                    wm.group(2)!!.toLong() * 1000 +
                    (if (wm.group(3)!!.length == 2) wm.group(3)!!.toLong() * 10 else wm.group(3)!!.toLong())
            val wordText = wm.group(4) ?: ""
            if (wordText.isBlank()) continue
            words.add(LyricWord(begin = wordBegin, end = wordBegin + 500, duration = 500, text = wordText))
        }

        if (words.isEmpty()) return null

        // 修正每个词的 end 为下一个词的 begin
        for (i in 0 until words.size - 1) {
            words[i].end = words[i + 1].begin
            words[i].duration = words[i].end - words[i].begin
        }
        words.last().end = words.last().begin + 500
        words.last().duration = 500

        // 行级时间以第一个词的 begin 为准（行首时间戳可能与词级时间不一致）
        val lineBegin = words.first().begin
        val lineEnd = words.last().end
        val lineText = words.joinToString("") { it.text.orEmpty() }
        return RichLyricLine(begin = lineBegin, end = lineEnd, duration = lineEnd - lineBegin, text = lineText, words = words)
    }

    /**
     * 解析 LRC 单行：[mm:ss.xx]文本。
     * 兼容一行多个时间戳（取第一个时间，文本取最后一个标签之后），
     * 并清理 ELRC 降级残留的词级标签。
     */
    private fun parseLrcLine(raw: String): RichLyricLine? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val m = LRC_TIME_RE.matcher(trimmed)
        if (!m.find()) return null
        val ms = m.group(1)!!.toLong() * 60000 + m.group(2)!!.toLong() * 1000 +
                (if (m.group(3)!!.length == 2) m.group(3)!!.toLong() * 10 else m.group(3)!!.toLong())
        var lastEnd = m.end()
        while (m.find()) lastEnd = m.end()
        val text = ELRC_WORD_RE.matcher(trimmed.substring(lastEnd)).replaceAll("").trim()
        if (text.isBlank()) return null
        return RichLyricLine(begin = ms, text = text)
    }

    /**
     * 补全行 end/duration：
     * - 行 end 无效或早于下一句 begin → 延长到下一句 begin（末句 +5000ms）；
     * - 末词 end 早于行 end → 延长到行 end（卡拉OK句尾逐字高亮完整走到下一句，不再提前掐断）。
     */
    private fun finalizeLines(resultLines: MutableList<RichLyricLine>) {
        for (idx in resultLines.indices) {
            val cur = resultLines[idx]
            val nextBegin = resultLines.getOrNull(idx + 1)?.begin
            val naturalEnd = nextBegin ?: (cur.begin + 5000)
            if (cur.end <= cur.begin || cur.end < naturalEnd) {
                cur.end = naturalEnd
                cur.duration = cur.end - cur.begin
            }
            cur.words?.lastOrNull()?.let { lastWord ->
                if (lastWord.end < cur.end) {
                    lastWord.end = cur.end
                    lastWord.duration = lastWord.end - lastWord.begin
                }
            }
        }
    }

    /**
     * 纯文本兜底：lyricInfo 直接是 LRC/ELRC 文本（不套 JSON）时使用。
     * 提取 [ti:]/[ar:] 元信息；逐字行自动按 ELRC 解析，其余按 LRC。
     */
    private fun parseRawText(raw: String): Song? {
        return try {
            var title: String? = null
            var artist: String? = null
            val resultLines = mutableListOf<RichLyricLine>()
            raw.lines().filter { it.isNotBlank() }.forEach { line ->
                val meta = LRC_META_RE.matcher(line.trim())
                if (meta.matches()) {
                    val value = meta.group(2)!!.trim()
                    when (meta.group(1)!!.lowercase()) {
                        "ti" -> title = value
                        "ar" -> artist = value
                    }
                    return@forEach
                }
                parseLine(line)?.let { resultLines.add(it) }
            }
            if (resultLines.isEmpty()) return null
            finalizeLines(resultLines)
            Song(id = null, name = title, artist = artist, lyrics = resultLines)
        } catch (_: Exception) {
            null
        }
    }

    fun diagnose(json: String): LyricInfoDiagnosis? {
        return try {
            val obj = JSONObject(json)
            LyricInfoDiagnosis(
                songName = obj.optString("songName", ""),
                artist = obj.optString("artist", ""),
                songId = obj.optString("songId", ""),
                format = obj.optString("format", ""),
                translationFormat = obj.optString("translation", ""),
                lyricLength = obj.optString("lyric", "").length,
                lyricPreview = obj.optString("lyric", "").lines().filter { it.isNotBlank() }.drop(3).take(10)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.optionalText(key: String): String? =
        optString(key, "").trim().takeIf { it.isNotEmpty() }
}

private data class ParsedLine(val timeMs: Long, val raw: String)

data class LyricInfoPayload(
    val song: Song,
    val songId: String?,
    val title: String?,
    val artist: String?,
    val album: String?
)

data class LyricInfoDiagnosis(
    val songName: String,
    val artist: String,
    val songId: String,
    val format: String,
    val translationFormat: String,
    val lyricLength: Int,
    val lyricPreview: List<String>
)
