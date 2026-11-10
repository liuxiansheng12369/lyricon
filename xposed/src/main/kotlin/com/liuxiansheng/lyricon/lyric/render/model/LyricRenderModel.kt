/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 歌词行渲染模型（V3 自研）。
 * 职责：把数据链的歌词行（ILyricLine）转换为渲染器可直接消费的布局数据。
 * 有词数据 → 逐词布局；无词数据 → 整行折叠为一个词 / 按整行时间轴走。
 */

package com.liuxiansheng.lyricon.lyric.render.model

import android.text.TextPaint
import com.liuxiansheng.lyricon.hyper.lyric.model.LyricWord
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.ILyricLine

/**
 * 单个词（或整句折叠词）的渲染布局数据。
 *
 * [startPosition]/[endPosition] 是该词在整行文本中的像素范围；
 * [charStartPositions]/[charEndPositions] 是词内每个字符的像素范围，
 * 用于逐字动效与逐字高亮。
 */
internal class WordRenderModel(
    val text: String,
    val begin: Long,
    val end: Long,
    val duration: Long,
    val startPosition: Float,
    val endPosition: Float,
    val textWidth: Float,
    val charStartPositions: FloatArray,
    val charEndPositions: FloatArray,
    val chars: List<String>,
    val containsCjk: Boolean,
    val sustainStrength: Float
) {
    /** 词中心像素位置（动效相位用） */
    val centerPosition: Float get() = (startPosition + endPosition) / 2f
}

/**
 * 歌词行渲染模型。
 *
 * - 有词数据：逐词布局（[words]）
 * - 无词数据：整行折叠为一个词，或按整行时间轴走（[lineTimeline]）
 * - [navigator] 提供"媒体时间 → 当前词"的导航
 */
internal class LyricRenderModel(
    val text: String?,
    val begin: Long,
    val end: Long,
    val duration: Long,
    val words: List<WordRenderModel>,
    val width: Float,
    val isAlignedRight: Boolean,
    val isCountdownLine: Boolean,
    val isPlainText: Boolean,
    val lineTimeline: Boolean,
    val isTitleLine: Boolean
) {
    /** 时间导航器：按词 begin 排序，提供 first()/findPreviousEntry() */
    val navigator: TimingNavigator by lazy { TimingNavigator(words) }

    /** 整行文本（words 折叠后的完整串） */
    val fullText: String
        get() = text ?: words.joinToString("") { it.text }

    companion object {

        /**
         * 从数据链歌词行构建渲染模型。
         *
         * @param paint 测量用画笔（必须与最终绘制字号一致）
         * @param line 歌词行数据
         * @param lineTimeline 是否按整行时间轴渲染（line-by-line 模式）
         * @param isTitleLine 是否为歌名占位行
         * @param isCountdownLine 是否为倒计时行
         */
        fun build(
            paint: TextPaint,
            line: ILyricLine,
            lineTimeline: Boolean,
            isTitleLine: Boolean,
            isCountdownLine: Boolean
        ): LyricRenderModel {
            val lineText = line.text
            val lineWords = line.words
            val isPlain = lineWords.isNullOrEmpty() && lineText.isNullOrBlank()
            val text = lineText?.takeIf { it.isNotBlank() }
                ?: lineWords?.joinToString("") { it.text.orEmpty() }

            val builtWords = if (lineTimeline || isPlain) {
                emptyList()
            } else {
                WordLayoutBuilder.build(paint, line, text, lineWords)
            }

            val width = text?.let { paint.measureText(it) } ?: 0f
            return LyricRenderModel(
                text = text,
                begin = line.begin,
                end = line.end,
                duration = line.duration,
                words = builtWords,
                width = width,
                isAlignedRight = line.isAlignedRight,
                isCountdownLine = isCountdownLine,
                isPlainText = isPlain,
                lineTimeline = lineTimeline,
                isTitleLine = isTitleLine
            )
        }

        /** 空模型（无内容时兜底） */
        fun empty(): LyricRenderModel = LyricRenderModel(
            text = null, begin = 0L, end = 0L, duration = 0L,
            words = emptyList(), width = 0f,
            isAlignedRight = false, isCountdownLine = false,
            isPlainText = true, lineTimeline = false, isTitleLine = false
        )
    }
}

/**
 * 词布局构建器：把原始词列表转换为带像素位置的词模型。
 *
 * 词按 begin 排序；若整行只有词间时间而无词文本则折叠为整句词；
 * 逐词/逐字用画笔测量宽度，累加得到像素范围。
 */
internal object WordLayoutBuilder {

    fun build(
        paint: TextPaint,
        line: ILyricLine,
        lineText: String?,
        lineWords: List<LyricWord>?
    ): List<WordRenderModel> {
        if (lineWords.isNullOrEmpty()) {
            val folded = foldWholeLine(paint, line, lineText) ?: return emptyList()
            return listOf(folded)
        }

        val words = lineWords
            .filter { !it.text.isNullOrBlank() }
            .sortedBy { it.begin }

        if (words.isEmpty()) {
            val folded = foldWholeLine(paint, line, lineText) ?: return emptyList()
            return listOf(folded)
        }

        var cursor = 0f
        val result = ArrayList<WordRenderModel>(words.size)
        for (word in words) {
            val text = word.text ?: continue
            val wordWidth = paint.measureText(text)
            val start = cursor
            val end = cursor + wordWidth
            cursor = end

            val (charStarts, charEnds, chars) = layoutChars(paint, text, start)
            result.add(
                WordRenderModel(
                    text = text,
                    begin = word.begin,
                    end = word.end,
                    duration = word.duration,
                    startPosition = start,
                    endPosition = end,
                    textWidth = wordWidth,
                    charStartPositions = charStarts,
                    charEndPositions = charEnds,
                    chars = chars,
                    containsCjk = text.any { isCjk(it) },
                    sustainStrength = 0f
                )
            )
        }
        return result
    }

    /** 无词行折叠为整句词（占满整行宽度） */
    private fun foldWholeLine(
        paint: TextPaint,
        line: ILyricLine,
        lineText: String?
    ): WordRenderModel? {
        val text = lineText?.takeIf { it.isNotBlank() } ?: return null
        val width = paint.measureText(text)
        val (charStarts, charEnds, chars) = layoutChars(paint, text, 0f)
        return WordRenderModel(
            text = text,
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            startPosition = 0f,
            endPosition = width,
            textWidth = width,
            charStartPositions = charStarts,
            charEndPositions = charEnds,
            chars = chars,
            containsCjk = text.any { isCjk(it) },
            sustainStrength = 0f
        )
    }

    /** 逐字测量，返回每字的像素范围与文本切片 */
    private fun layoutChars(
        paint: TextPaint,
        text: String,
        startX: Float
    ): Triple<FloatArray, FloatArray, List<String>> {
        val n = text.length
        if (n == 0) return Triple(FloatArray(0), FloatArray(0), emptyList())

        val starts = FloatArray(n)
        val ends = FloatArray(n)
        val chars = ArrayList<String>(n)
        var x = startX
        for (i in 0 until n) {
            val ch = text.substring(i, i + 1)
            starts[i] = x
            x += paint.measureText(ch)
            ends[i] = x
            chars.add(ch)
        }
        return Triple(starts, ends, chars)
    }

    private fun isCjk(c: Char): Boolean =
        Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN
}
