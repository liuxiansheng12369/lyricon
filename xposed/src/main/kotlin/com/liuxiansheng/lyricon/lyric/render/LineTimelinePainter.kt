/*
 * 词岛 · 整行时间轴渲染器（自研重写版）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * 无逐字词、按整行 begin/end 做时间轴的纯文本行使用。
 * 高亮宽度 = 行时间进度 × 行宽；双层绘制与逐字模式一致。
 */

package com.liuxiansheng.lyricon.lyric.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.liuxiansheng.lyricon.lyric.render.anim.ProgressAnimator
import com.liuxiansheng.lyricon.lyric.render.draw.LyricCanvasPainter
import com.liuxiansheng.lyricon.lyric.render.draw.SustainGlow
import com.liuxiansheng.lyricon.lyric.render.model.LyricRenderModel

internal class LineTimelinePainter : LineRenderer {

    val dimPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    val litPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val widthAnim = ProgressAnimator()
    private val follower = ScrollStepper()
    private val painter = LyricCanvasPainter()

    override var centerIfPossible = false
    override var rightIfPossible = false

    override fun setPaletteOffset(offset: Int) {
        painter.paletteOffset = offset
    }

    override var lastPosition = Long.MIN_VALUE
        private set

    override val isAnimating: Boolean get() = widthAnim.isAnimating
    override val isStarted: Boolean get() = widthAnim.hasStarted
    override val isFinished: Boolean get() = widthAnim.hasFinished

    fun applyFontSettings(size: Float, tf: Typeface?, varSettings: String?) {
        dimPaint.textSize = size
        litPaint.textSize = size
        dimPaint.typeface = tf
        litPaint.typeface = tf
        painter.syncMetrics(dimPaint)
    }

    fun setColors(dim: IntArray, lit: IntArray) {
        if (dim.isNotEmpty()) dimPaint.color = dim[0]
        if (lit.isNotEmpty()) litPaint.color = lit[0]
        painter.applyColors(dim, lit)
    }

    /** 当前高亮宽度 */
    fun currentWidth(): Float = widthAnim.currentWidth

    override fun updateLayout(
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        painter.syncMetrics(dimPaint)
        if (widthAnim.hasFinished) {
            widthAnim.jumpTo(model.width)
        }
        refreshScroll(model, state, viewWidth)
    }

    override fun seek(
        model: LyricRenderModel,
        state: LineRenderState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        widthAnim.jumpTo(targetWidth(model, posMs))
        lastPosition = posMs
        reconcileFlags(model)
        refreshScroll(model, state, viewWidth)
    }

    override fun update(
        model: LyricRenderModel,
        state: LineRenderState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int,
        playSpeed: Float
    ) {
        // 媒体回退 → 直接跳转
        if (lastPosition != Long.MIN_VALUE && posMs < lastPosition) {
            seek(model, state, posMs, viewWidth, viewHeight)
            return
        }

        val dur = lineDuration(model)
        when {
            posMs <= model.begin -> widthAnim.jumpTo(0f)
            dur <= 0L || model.width <= 0f -> widthAnim.jumpTo(0f)
            posMs >= lineEnd(model, dur) -> widthAnim.jumpTo(model.width)
            else -> {
                // 整段高亮：唱到该行即整行一起亮
                widthAnim.jumpTo(model.width)
            }
        }

        lastPosition = posMs
        reconcileFlags(model)
        refreshScroll(model, state, viewWidth)
    }

    override fun step(
        deltaNanos: Long,
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int
    ): Boolean {
        val changed = widthAnim.step(deltaNanos)
        if (changed) {
            reconcileFlags(model)
            refreshScroll(model, state, viewWidth)
        }
        return changed
    }

    override fun draw(
        canvas: Canvas,
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        painter.render(
            canvas, model, viewWidth, viewHeight,
            state.scrollOffset, model.width > viewWidth,
            widthAnim.currentWidth,
            fadeIn = false, marqueeOnly = false, waveOn = false,
            centerIfPossible, rightIfPossible,
            dimPaint, litPaint, dimPaint,
            emptyList<SustainGlow>()
        )
    }

    override fun reset(state: LineRenderState) {
        widthAnim.reset()
        lastPosition = Long.MIN_VALUE
        state.reset()
        painter.dropGradientCache()
    }

    override fun freeze(model: LyricRenderModel, state: LineRenderState, viewWidth: Int) {
        widthAnim.stopAtCurrent()
        reconcileFlags(model)
        refreshScroll(model, state, viewWidth)
    }

    override fun syncFrom(other: LineRenderer) {
        if (other is LineTimelinePainter) {
            widthAnim.syncFrom(other.widthAnim)
            lastPosition = other.lastPosition
        }
    }

    /** 目标高亮宽度：行时间进度 × 行宽 */
    private fun targetWidth(model: LyricRenderModel, posMs: Long): Float {
        if (model.width <= 0f) return 0f
        val dur = lineDuration(model)
        if (dur <= 0L) return 0f
        val progress = ((posMs - model.begin).toFloat() / dur.toFloat())
            .coerceIn(0f, 1f)
        return model.width * progress
    }

    private fun lineDuration(model: LyricRenderModel): Long =
        (model.end - model.begin).takeIf { it > 0L } ?: model.duration

    private fun lineEnd(model: LyricRenderModel, dur: Long): Long =
        model.end.takeIf { it > model.begin } ?: (model.begin + dur)

    private fun reconcileFlags(model: LyricRenderModel) {
        widthAnim.hasStarted = widthAnim.currentWidth > 0f
        widthAnim.hasFinished =
            model.width > 0f && widthAnim.currentWidth >= model.width
    }

    private fun refreshScroll(model: LyricRenderModel, state: LineRenderState, viewWidth: Int) {
        state.scrollOffset = follower.compute(
            widthAnim.currentWidth,
            model.width,
            viewWidth.toFloat(),
            widthAnim.hasFinished,
            state.isScrollFinished
        )
        state.isScrollFinished = widthAnim.hasFinished || model.width <= viewWidth
    }
}
