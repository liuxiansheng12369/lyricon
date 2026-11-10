/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common.lyric

import android.graphics.Paint
import com.liuxiansheng.lyricon.hyper.lyric.model.LyricWord
import com.liuxiansheng.lyricon.hyper.lyric.model.RichLyricLine
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.IRichLyricLine
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs

/**
 * 富歌词行拆分器（V3 自研）。
 *
 * 容器策略决定左右两栏的目标宽度：
 * - [Fixed]：左右栏宽度固定，歌词超宽时左栏取满，其余留在右栏滚动。
 * - [Dynamic]：左右栏从各自最小宽度按歌词宽度伸缩到最大宽度（动态收缩）。
 */
object RichLyricLineSplitter {

    /** 拆分结果：左栏行 + 右栏行 */
    data class SplitLineResult(
        val left: IRichLyricLine,
        val right: IRichLyricLine
    )

    /**
     * 容器宽度策略 —— 分离模式仅有的两种行为。
     *
     * [targetSplitWidthPx] 返回期望的左栏拆分宽度（像素）。
     */
    sealed interface ContainerWidthSpec {
        fun targetSplitWidthPx(totalTextWidthPx: Float): Float

        /** 固定模式：左右栏宽度固定 */
        data class Fixed(
            val leftWidthPx: Float,
            val rightWidthPx: Float
        ) : ContainerWidthSpec {
            override fun targetSplitWidthPx(totalTextWidthPx: Float): Float {
                val textWidth = totalTextWidthPx.coerceAtLeast(0f)
                if (textWidth <= 0f) return 0f

                val leftWidth = leftWidthPx.coerceAtLeast(0f)
                val totalWidth = (leftWidth + rightWidthPx.coerceAtLeast(0f))
                    .coerceAtLeast(0f)
                if (totalWidth <= 0f) return textWidth / 2f

                return if (textWidth >= totalWidth) {
                    leftWidth
                } else {
                    (textWidth * leftWidth / totalWidth).coerceIn(0f, textWidth)
                }
            }
        }

        /** 动态模式：随歌词宽度从最小伸缩到最大 */
        data class Dynamic(
            val leftMinWidthPx: Float,
            val leftMaxWidthPx: Float,
            val rightMinWidthPx: Float,
            val rightMaxWidthPx: Float
        ) : ContainerWidthSpec {
            private val minTotalWidthPx: Float
                get() = (leftMinWidthPx.coerceAtLeast(0f) +
                        rightMinWidthPx.coerceAtLeast(0f)).coerceAtLeast(0f)

            private val maxTotalWidthPx: Float
                get() = (leftMaxWidthPx.coerceAtLeast(0f) +
                        rightMaxWidthPx.coerceAtLeast(0f)).coerceAtLeast(minTotalWidthPx)

            override fun targetSplitWidthPx(totalTextWidthPx: Float): Float {
                val textWidth = totalTextWidthPx.coerceAtLeast(0f)
                if (textWidth <= 0f) return 0f

                // 歌词宽度达到整体上限：左栏取满自己的最大宽度，剩余全留右栏。
                if (textWidth >= maxTotalWidthPx) {
                    return leftMaxWidthPx.coerceAtLeast(0f)
                }

                // 未达到整体最小宽度：按最小宽度比例分摊。
                if (textWidth <= minTotalWidthPx) {
                    if (minTotalWidthPx <= 0f) return textWidth / 2f
                    return (textWidth * leftMinWidthPx.coerceAtLeast(0f) /
                            minTotalWidthPx).coerceIn(0f, textWidth)
                }

                // 最小与最大之间：按进度线性伸缩左栏。
                val range = (maxTotalWidthPx - minTotalWidthPx).coerceAtLeast(1f)
                val progress = (textWidth - minTotalWidthPx) / range
                return (leftMinWidthPx +
                        (leftMaxWidthPx - leftMinWidthPx) * progress)
                    .coerceIn(0f, textWidth)
            }
        }
    }

    /**
     * 按容器策略拆分一行歌词。
     *
     * 主行（原文）、副行（罗马音）、翻译行分别按各自画笔拆分；
     * 拆分目标是"期望位置"，允许在相邻字词边界轻微超出，超宽部分由歌词视图滚动处理。
     */
    fun split(
        line: IRichLyricLine,
        primaryPaint: Paint,
        secondaryPaint: Paint,
        containerWidthSpec: ContainerWidthSpec
    ): SplitLineResult {
        val text = line.text
            ?: return SplitLineResult(toRichLyricLine(line), RichLyricLine())

        val primary = splitText(
            text = text,
            words = line.words,
            paint = primaryPaint,
            containerWidthSpec = containerWidthSpec
        )
        val secondary = splitText(
            text = line.secondary,
            words = line.secondaryWords,
            paint = secondaryPaint,
            containerWidthSpec = containerWidthSpec
        )
        val translation = splitText(
            text = line.translation,
            words = line.translationWords,
            paint = secondaryPaint,
            containerWidthSpec = containerWidthSpec
        )

        val leftLine = RichLyricLine(
            begin = line.begin,
            end = line.end,
            duration = 0,
            isAlignedRight = false,
            metadata = line.metadata,
            text = primary.leftText.orEmpty(),
            words = primary.leftWords,
            secondary = secondary.leftText,
            secondaryWords = secondary.leftWords,
            translation = translation.leftText,
            translationWords = translation.leftWords,
            roma = null
        )

        val rightLine = RichLyricLine(
            begin = primary.rightWords?.firstOrNull()?.begin ?: line.begin,
            end = line.end,
            duration = 0,
            isAlignedRight = false,
            metadata = line.metadata,
            text = primary.rightText.orEmpty(),
            words = primary.rightWords,
            secondary = secondary.rightText,
            secondaryWords = secondary.rightWords,
            translation = translation.rightText,
            translationWords = translation.rightWords,
            roma = null
        )

        return SplitLineResult(leftLine, rightLine)
    }

    private data class TextParts(
        val leftText: String? = null,
        val rightText: String? = null,
        val leftWords: List<LyricWord>? = null,
        val rightWords: List<LyricWord>? = null
    )

    /**
     * 拆分单段文本。V3 规则：
     * - 短歌词（≤1 字符）不拆，整句放左栏，避免单字拆出怪样；
     * - 默认按总宽度对半拆分（左右各一半），避免左侧因避让摄像头偏窄导致分词失衡；
     * - 带逐字时间轴的歌词按 UTF-16 边界拆，跨边界的字按时间比例均分。
     */
    private fun splitText(
        text: String?,
        words: List<LyricWord>?,
        paint: Paint,
        containerWidthSpec: ContainerWidthSpec
    ): TextParts {
        if (text.isNullOrEmpty()) return TextParts()

        // 短歌词不拆分，整句放左栏主显示区。
        if (text.length <= 1) {
            return TextParts(leftText = text, leftWords = words)
        }

        val totalWidth = paint.measureText(text)
        // 歌词平均分配：左右各占一半宽度。
        val targetWidth = totalWidth / 2f
        val splitIndex = computeSplitIndex(text, paint, targetWidth)

        if (splitIndex <= 0) {
            return TextParts(rightText = text, rightWords = words)
        }
        if (splitIndex >= text.length) {
            return TextParts(leftText = text, leftWords = words)
        }

        if (words.isNullOrEmpty()) {
            return TextParts(
                leftText = text.substring(0, splitIndex),
                rightText = text.substring(splitIndex)
            )
        }

        val (leftWords, rightWords) = splitWordsAtCharIndex(words, splitIndex)
        return TextParts(
            leftText = leftWords.joinToString("") { it.text.orEmpty() }
                .takeIf { it.isNotEmpty() },
            rightText = rightWords.joinToString("") { it.text.orEmpty() }
                .takeIf { it.isNotEmpty() },
            leftWords = leftWords,
            rightWords = rightWords
        )
    }

    private fun toRichLyricLine(line: IRichLyricLine): RichLyricLine {
        return line as? RichLyricLine ?: RichLyricLine(
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            isAlignedRight = line.isAlignedRight,
            metadata = line.metadata,
            text = line.text,
            words = line.words,
            secondary = line.secondary,
            secondaryWords = line.secondaryWords,
            translation = line.translation,
            translationWords = line.translationWords,
            roma = line.roma
        )
    }

    /**
     * 按 UTF-16 字符边界切分逐字时间轴。
     * 跨边界的字按时间比例拆成两半，保证左右两栏各自拥有有效的时间线。
     */
    private fun splitWordsAtCharIndex(
        words: List<LyricWord>,
        charIndex: Int
    ): Pair<List<LyricWord>, List<LyricWord>> {
        val leftWords = mutableListOf<LyricWord>()
        val rightWords = mutableListOf<LyricWord>()
        var charPos = 0

        for (word in words) {
            val wordText = word.text.orEmpty()
            val wordEnd = charPos + wordText.length

            when {
                wordEnd <= charIndex -> leftWords.add(word)
                charPos >= charIndex -> rightWords.add(word)
                else -> {
                    val leftLength = charIndex - charPos
                    val rightLength = wordText.length - leftLength
                    val duration = word.end - word.begin
                    val splitMs = word.begin + (duration * leftLength) / wordText.length

                    if (leftLength > 0) {
                        leftWords.add(
                            LyricWord(
                                begin = word.begin,
                                end = splitMs,
                                duration = splitMs - word.begin,
                                text = wordText.substring(0, leftLength),
                                metadata = word.metadata
                            )
                        )
                    }
                    if (rightLength > 0) {
                        rightWords.add(
                            LyricWord(
                                begin = splitMs,
                                end = word.end,
                                duration = word.end - splitMs,
                                text = wordText.substring(leftLength),
                                metadata = word.metadata
                            )
                        )
                    }
                }
            }
            charPos = wordEnd
        }
        return leftWords to rightWords
    }

    /** 二分查找最接近目标像素宽度的可见字符边界。 */
    private fun computeSplitIndex(
        text: String,
        paint: Paint,
        targetWidthPx: Float
    ): Int {
        if (text.isEmpty()) return 0

        val totalWidth = paint.measureText(text)
        if (targetWidthPx <= 0f) return 0
        if (targetWidthPx >= totalWidth) return text.length

        val target = targetWidthPx.coerceIn(0f, totalWidth)
        val boundaries = characterBoundaries(text)
        var lowerIndex = 0
        var lowerWidth = 0f
        var upperIndex = text.length
        var upperWidth = totalWidth

        for (boundary in boundaries) {
            if (boundary == 0) continue
            val width = paint.measureText(text, 0, boundary)
            if (width <= target) {
                lowerIndex = boundary
                lowerWidth = width
            } else {
                upperIndex = boundary
                upperWidth = width
                break
            }
        }

        val lowerDistance = abs(lowerWidth - target)
        val upperDistance = abs(upperWidth - target)
        val nearestIndex = if (lowerDistance <= upperDistance) lowerIndex else upperIndex
        return movePastEnglishWord(text, nearestIndex)
    }

    /**
     * 若目标落在英文单词中间，把拆分点移到该单词末尾，
     * 保证 "i love you" 拆成 "i love" + "you" 而不是从 love 中间切断。
     */
    private fun movePastEnglishWord(
        text: String,
        originalIndex: Int
    ): Int {
        if (originalIndex <= 0 || originalIndex >= text.length) return originalIndex

        val isAsciiAlnum = { c: Char -> c.isLetterOrDigit() && c.code < 128 }
        if (!isAsciiAlnum(text[originalIndex - 1]) || !isAsciiAlnum(text[originalIndex])) {
            return originalIndex
        }

        var forwardIndex = originalIndex
        while (forwardIndex < text.length && isAsciiAlnum(text[forwardIndex])) forwardIndex++
        return forwardIndex
    }

    /** Android 文本布局基于 UTF-16，拆分不得切断字素簇（emoji/组合字符）。 */
    private fun characterBoundaries(text: String): List<Int> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val boundaries = ArrayList<Int>(text.length + 1)
        var boundary = iterator.first()
        while (boundary != BreakIterator.DONE) {
            boundaries.add(boundary)
            boundary = iterator.next()
        }
        return boundaries
    }
}
