/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.lyric.render.view

import com.liuxiansheng.lyricon.hyper.lyric.model.LyricLine
import com.liuxiansheng.lyricon.hyper.lyric.model.LyricWord
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.IRichLyricLine

internal const val METADATA_NEXT_LINE_PREVIEW = "nextLinePreview"

internal class LyricLineAssembler(
    private var displayTranslation: Boolean = true,
    private var displayRoma: Boolean = true,
    private var enableRelativeProgress: Boolean = false,
    private var enableRelativeHighlight: Boolean = false,
    private var displayLineByLine: Boolean = false
) {

    data class MainResult(
        val line: LyricLine,
        val isScrollOnly: Boolean,
        val sustainAwareProgress: Boolean,
        val isLineTimeline: Boolean
    )

    data class SecondaryResult(
        val line: LyricLine,
        val alwaysShow: Boolean,
        val isScrollOnly: Boolean,
        val isNextLinePreview: Boolean,
        val sustainAwareProgress: Boolean,
        val isLineTimeline: Boolean
    )

    /** 刷新组装开关（样式重设时调用） */
    fun updateFlags(
        displayTranslation: Boolean,
        displayRoma: Boolean,
        enableRelativeProgress: Boolean,
        enableRelativeHighlight: Boolean,
        displayLineByLine: Boolean
    ) {
        this.displayTranslation = displayTranslation
        this.displayRoma = displayRoma
        this.enableRelativeProgress = enableRelativeProgress
        this.enableRelativeHighlight = enableRelativeHighlight
        this.displayLineByLine = displayLineByLine
    }

    // —— 主行：歌词原文 ——

    fun buildMain(source: IRichLyricLine?): MainResult {
        if (source == null) return MainResult(LyricLine(), false, false, false)

        val hasOriginalWords = !source.words.isNullOrEmpty()
        val useLineTimeline = shouldUseLineTimeline(source, source.words, source.text)

        // 逐字开关（3.0.907 修复）：
        // 开(enableRelativeProgress)= 有原始词逐字、无词折叠整行平滑；
        // 关 = 一律折叠整行为一个词 → 整段（整行）高亮，唱到该段亮该段。
        // 标题行（歌名占位）始终保留逐字拆词。
        val words = when {
            useLineTimeline -> emptyList()
            isTitleLine(source) -> source.words ?: emptyList()
            !source.words.isNullOrEmpty() && enableRelativeProgress -> source.words
            else -> foldWholeLine(source) ?: emptyList()
        }

        val line = LyricLine(
            begin = source.begin,
            end = source.end,
            duration = source.duration,
            isAlignedRight = source.isAlignedRight,
            metadata = source.metadata,
            text = if (useLineTimeline) textForLine(source.text, source.words) else source.text,
            words = words
        )
        return MainResult(
            line = line,
            // 折叠整行/逐字都逐字高亮推进（整段亮），不因 relativeHighlight 退化为纯滚动
            isScrollOnly = false,
            sustainAwareProgress = hasOriginalWords && !useLineTimeline,
            isLineTimeline = useLineTimeline
        )
    }

    // —— 副行：secondary 原文 > 翻译 > 罗马音（按开关优先级） ——

    fun buildSecondary(source: IRichLyricLine?): SecondaryResult {
        if (source == null) return SecondaryResult(LyricLine(), false, false, false, false, false)

        val isNextLinePreview = source.metadata?.getBoolean(METADATA_NEXT_LINE_PREVIEW, false) == true
        val line = LyricLine().apply {
            begin = source.begin
            end = source.end
            duration = source.duration
            isAlignedRight = source.isAlignedRight
        }

        // 副行内容来源解析：返回文本、词列表、是否整行时间轴、是否生成了词
        val content = resolveSecondaryContent(source, isNextLinePreview)
        line.text = content.text
        line.words = content.words
        line.metadata = if (isNextLinePreview) source.metadata else null

        val hasContent = line.text?.isNotBlank() == true || !line.words.isNullOrEmpty()
        val isPlain = line.words.isNullOrEmpty()
        // 副行是否常驻显示：纯文本，或首词与主行几乎同时开始（避免副行只在唱到一半才冒出来）
        val alwaysShow = hasContent && (
            isPlain ||
                line.words?.firstOrNull()?.let { (it.begin - source.begin) < 500L } == true
            )

        return SecondaryResult(
            line = line,
            alwaysShow = alwaysShow,
            isScrollOnly = content.generated && !enableRelativeHighlight,
            isNextLinePreview = isNextLinePreview,
            sustainAwareProgress = content.hasOriginalWords && !content.lineTimelineGenerated,
            isLineTimeline = content.lineTimelineGenerated
        )
    }

    /** 副行内容解析：按 下一句预览 > secondary > 翻译 > 罗马音 的优先级取内容 */
    private fun resolveSecondaryContent(
        source: IRichLyricLine,
        isNextLinePreview: Boolean
    ): SecondaryContent {
        // 3.0.619 翻译诊断：副行来源各候选值
        val secText = source.secondary?.take(30)
        val transText = source.translation?.take(30)
        val romaText = source.roma?.take(30)
        // 下一句预览：只显示文本，不建词（不能继承当前行时间轴）
        if (isNextLinePreview && (!source.secondary.isNullOrBlank() || !source.secondaryWords.isNullOrEmpty())) {
            android.util.Log.i(
                "LyricTrans",
                "resolveSecondary -> NEXT_PREVIEW sec=$secText trans=$transText displayTrans=$displayTranslation displayRoma=$displayRoma"
            )
            return SecondaryContent(
                text = source.secondary,
                words = emptyList(),
                generated = false,
                hasOriginalWords = false,
                lineTimelineGenerated = false
            )
        }

        // secondary 原文（同歌词结构：可逐字）
        if (!source.secondary.isNullOrBlank() || !source.secondaryWords.isNullOrEmpty()) {
            android.util.Log.i(
                "LyricTrans",
                "resolveSecondary -> SECONDARY sec=$secText trans=$transText displayTrans=$displayTranslation displayRoma=$displayRoma"
            )
            return buildWordContent(source, source.secondary, source.secondaryWords)
        }

        // 翻译（同歌词结构：可逐字）
        if (displayTranslation && (!source.translation.isNullOrBlank() || !source.translationWords.isNullOrEmpty())) {
            android.util.Log.i(
                "LyricTrans",
                "resolveSecondary -> TRANSLATION trans=$transText displayTrans=$displayTranslation displayRoma=$displayRoma"
            )
            return buildWordContent(source, source.translation, source.translationWords)
        }

        // 罗马音：纯文本，无词
        if (displayRoma && !source.roma.isNullOrBlank()) {
            android.util.Log.i(
                "LyricTrans",
                "resolveSecondary -> ROMA roma=$romaText displayTrans=$displayTranslation displayRoma=$displayRoma"
            )
            return SecondaryContent(
                text = source.roma,
                words = null,
                generated = true,
                hasOriginalWords = false,
                lineTimelineGenerated = false
            )
        }

        android.util.Log.i(
            "LyricTrans",
            "resolveSecondary -> EMPTY sec=$secText trans=$transText roma=$romaText " +
                "displayTrans=$displayTranslation displayRoma=$displayRoma preview=$isNextLinePreview"
        )
        return SecondaryContent(null, null, false, false, false)
    }

    /** 带词内容的副行（secondary / 翻译共用）：按整行时间轴判定展开 */
    private fun buildWordContent(
        source: IRichLyricLine,
        rawText: String?,
        rawWords: List<LyricWord>?
    ): SecondaryContent {
        val useLineTimeline = shouldUseLineTimeline(source, rawWords, rawText)
        return SecondaryContent(
            text = if (useLineTimeline) textForLine(rawText, rawWords) else rawText,
            words = if (useLineTimeline) emptyList() else rawWords,
            generated = !useLineTimeline && !rawWords.isNullOrEmpty(),
            hasOriginalWords = !useLineTimeline && !rawWords.isNullOrEmpty(),
            lineTimelineGenerated = useLineTimeline
        )
    }

    /** 整行时间轴判定：逐行模式 且 非标题行 且 有有效时间轴 且 有内容 */
    private fun shouldUseLineTimeline(
        source: IRichLyricLine,
        contentWords: List<LyricWord>?,
        contentText: String?
    ): Boolean {
        val lineDuration = (source.end - source.begin).takeIf { it > 0L } ?: source.duration
        // 3.0.907：整行时间轴（整段高亮）绑定到"逐字开关关闭"（!enableRelativeProgress）
        return !enableRelativeProgress && !isTitleLine(source) &&
            source.begin >= 0 && lineDuration > 0L &&
            !contentText.isNullOrBlank() &&
            (!contentWords.isNullOrEmpty() || !source.words.isNullOrEmpty())
    }

    /** 无词时把整行折叠为一个整句词（时间轴为整行） */
    private fun foldWholeLine(source: IRichLyricLine): List<LyricWord>? {
        val text = source.text?.takeIf { it.isNotBlank() } ?: return null
        if (source.begin >= source.end || source.begin < 0) return null
        return listOf(
            LyricWord(
                begin = source.begin,
                end = source.end,
                duration = source.duration,
                text = text
            )
        )
    }

    private fun textForLine(text: String?, words: List<LyricWord>?): String? =
        text?.takeIf { it.isNotBlank() } ?: words?.joinToString("") { it.text.orEmpty() }

    private fun isTitleLine(source: IRichLyricLine): Boolean =
        source.metadata?.getBoolean("TitleLine", false) == true

    /** 副行解析结果 */
    private data class SecondaryContent(
        val text: String?,
        val words: List<LyricWord>?,
        val generated: Boolean,
        val hasOriginalWords: Boolean,
        val lineTimelineGenerated: Boolean
    )
}
