/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.lyric.render.anim

import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import com.liuxiansheng.lyricon.lyric.render.model.LyricRenderModel
import com.liuxiansheng.lyricon.lyric.render.model.WordRenderModel

internal class WordProgressController {

    val animator = ProgressAnimator()

    /** 是否启用长音缓动（sustain 感知） */
    var sustainAware = false

    var lastPosition = Long.MIN_VALUE
        private set

    private var lastDiagLogMs = Long.MIN_VALUE

    private var activeWord: WordRenderModel? = null

    /** update 锚定的真实媒体时间；帧驱动在其上自推进，实现字尾无缝切字 */
    private var anchorPosMs = 0L

    /** 自上次 update 锚定以来帧推进的虚拟毫秒 */
    private var advancePosMs = 0f

    /** 直接跳到精确位置（拖动 / 回退） */
    fun seek(posMs: Long, model: LyricRenderModel): Boolean {
        val previousWidth = animator.currentWidth
        animator.jumpTo(exactTargetWidth(posMs, model))
        activeWord = null
        lastPosition = posMs
        anchorPosMs = posMs
        advancePosMs = 0f
        reconcileLifecycleFlags(model)
        return animator.currentWidth != previousWidth
    }

    /** 随播放推进；返回高亮宽度是否变化 */
    fun update(posMs: Long, playbackSpeed: Float, model: LyricRenderModel): Boolean {
        if (lastPosition != Long.MIN_VALUE && posMs < lastPosition) {
            return seek(posMs, model)
        }

        anchorPosMs = posMs
        advancePosMs = 0f
        val previousWidth = animator.currentWidth
        val word = model.navigator.first(posMs)
        // 诊断日志（节流）：词时间轴命中 + 高亮宽度
        if (lastDiagLogMs == Long.MIN_VALUE || posMs - lastDiagLogMs >= 500L) {
            lastDiagLogMs = posMs
            val w = word
            HookLogger.i(
                "WordProgressController",
                "pos=$posMs word=${w?.text?.take(12) ?: "null"} " +
                    "wBegin=${w?.begin} wEnd=${w?.end} " +
                    "modelBegin=${model.begin} modelEnd=${model.end} " +
                    "words=${model.words.size} width=${animator.currentWidth}"
            )
        }
        val target = targetWidth(posMs, model, word)
        // 行已唱完（posMs 越过行尾且行时间有效）：高亮直接置满，不做插值动画。
        // 否则刷新（切开关触发 apply→reset 清零高亮）后，末词/行尾会用 1ms 从 0
        // 快速重扫到整行满，一帧内高亮闪一遍 = "播放完/全高亮行字体抖动"。
        // 未唱完行（posMs 在行内）走下方正常词动画，不受影响。
        // 行无有效时间轴（end<=begin）时绝不置满：避免"播放完的跟未播放完的同时唱"。
        // 词间空隙（word==null）不截断动画：保持向上一词词尾推进，
        // 否则动画未完成时进空隙会被重锚到当前宽度，下一词从旧词中途起步，
        // 高亮跨越旧词尾部 = "前面还没唱完，后面就亮了"。
        if (model.end > model.begin && posMs >= model.end && target >= model.width) {
            animator.jumpTo(target)
        } else if (word != null && target != animator.targetWidth) {
            animator.animateTo(
                target,
                durationOf(word),
                if (sustainAware) word.sustainStrength else 0f
            )
        }

        activeWord = word
        lastPosition = posMs
        reconcileLifecycleFlags(model)
        return animator.currentWidth != previousWidth
    }

    /**
     * 帧推进：虚拟 posMs 自推进 + 目标驱动自动切字。
     * 只靠 animator.step 时词尾 linear 走完动画停，高亮停在字尾等下一次
     * 低频 update 才切下一字——"字尾停留 + 突跳"= 闪烁。这里每帧推进虚拟
     * posMs 检查词边界，目标变了立即 animateTo，词尾无缝衔接下一词。
     */
    fun step(deltaNanos: Long, playbackSpeed: Float, model: LyricRenderModel): Boolean {
        val speed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) playbackSpeed else 1f
        advancePosMs += deltaNanos.toFloat() / 1_000_000f * speed
        val virtualPos = anchorPosMs + advancePosMs.toLong()
        val previousWidth = animator.currentWidth
        val word = model.navigator.first(virtualPos)
        val target = targetWidth(virtualPos, model, word)
        // 同 update：虚拟时间已越过行尾则直接跳满，不做 1ms 插值（避免全高亮行
        // 刷新后从 0 快速重扫的抖动）；行无有效时间轴时不置满；
        // 词间空隙不截断动画（原因见 update）。
        if (model.end > model.begin && virtualPos >= model.end && target >= model.width) {
            animator.jumpTo(target)
        } else if (word != null && target != animator.targetWidth) {
            animator.animateTo(
                target,
                durationOf(word),
                if (sustainAware) word.sustainStrength else 0f
            )
        }
        activeWord = word
        val changed = animator.step(deltaNanos)
        return changed || animator.currentWidth != previousWidth
    }

    fun freeze() {
        animator.stopAtCurrent()
        activeWord = null
    }

    fun reset() {
        animator.reset()
        activeWord = null
        lastPosition = Long.MIN_VALUE
        anchorPosMs = 0L
        advancePosMs = 0f
    }

    fun syncFrom(other: WordProgressController) {
        animator.syncFrom(other.animator)
        sustainAware = other.sustainAware
        lastPosition = other.lastPosition
        activeWord = null
        anchorPosMs = other.anchorPosMs
        advancePosMs = other.advancePosMs
    }

    /**
     * 目标宽度：词内=词尾（末词取整行宽，防字形溢出到不了高亮态），
     * 行尾=整行宽，行首=0，词间空隙=保持当前宽度不动。
     * 防御：词/行无有效时间轴（begin==end）时回退为行时间线性映射，
     * 绝不整行全亮（否则"播放完的跟未播放完的同时唱"）。
     */
    private fun targetWidth(
        posMs: Long,
        model: LyricRenderModel,
        word: WordRenderModel?
    ): Float = when {
        word != null && (word.end - word.begin) > 0L -> {
            if (word.end >= model.end) max(word.endPosition, model.width) else word.endPosition
        }
        word != null -> lineProgressWidth(model, posMs)
        posMs >= model.end && model.end > model.begin -> model.width
        posMs <= model.begin -> 0f
        else -> animator.currentWidth
    }

    /** 按行时间轴线性映射高亮宽度；行无有效时间轴返回 0（不亮） */
    private fun lineProgressWidth(model: LyricRenderModel, posMs: Long): Float {
        if (model.end <= model.begin) return 0f
        val linear = ((posMs - model.begin).toFloat() /
            (model.end - model.begin).toFloat()).coerceIn(0f, 1f)
        return model.width * linear
    }

    /** 精确目标宽度：当前词内的线性映射（含 sustain 缓动）；词/行时间无效时回退行线性/0 */
    private fun exactTargetWidth(posMs: Long, model: LyricRenderModel): Float {
        val word = model.navigator.first(posMs)
        return when {
            word != null -> {
                val duration = durationOf(word)
                if (duration <= 0L) {
                    if (model.end > model.begin) {
                        val linear = ((posMs - model.begin).toFloat() /
                            (model.end - model.begin).toFloat()).coerceIn(0f, 1f)
                        model.width * linear
                    } else {
                        0f
                    }
                } else {
                    val linear = ((posMs - word.begin).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    val mapped = WordProgressMapper.map(
                        linear,
                        if (sustainAware) word.sustainStrength else 0f
                    )
                    word.startPosition + word.textWidth * mapped
                }
            }

            posMs >= model.end && model.end > model.begin -> model.width
            posMs <= model.begin -> 0f
            else -> animator.currentWidth
        }
    }

    private fun durationOf(word: WordRenderModel): Long =
        (word.end - word.begin).takeIf { it > 0L } ?: word.duration

    private fun reconcileLifecycleFlags(model: LyricRenderModel) {
        if (animator.currentWidth < model.width) animator.hasFinished = false
        if (animator.currentWidth <= 0f) animator.hasStarted = false
    }

    private fun max(a: Float, b: Float): Float = if (a > b) a else b
}
