/*
 * 词岛 · 倒计时渲染器（自研重写版）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * 占位倒计时行：显示 mm:ss.d，剩余过半白色、不足半黄色。
 */

package com.liuxiansheng.lyricon.lyric.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.liuxiansheng.lyricon.lyric.render.model.LyricRenderModel
import kotlin.math.max

internal class CountdownPainter : LineRenderer {

    val plainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    override var centerIfPossible = true
    override var rightIfPossible = false

    override var lastPosition = Long.MIN_VALUE
        private set
    private var shownText = ""
    private var leftMs = 0L

    override val isAnimating: Boolean get() = false
    override val isStarted: Boolean get() = shownText.isNotEmpty()
    override val isFinished: Boolean get() = leftMs <= 0L

    fun applyFontSettings(size: Float, tf: Typeface?, varSettings: String?) {
        plainPaint.textSize = size
        plainPaint.typeface = tf
    }

    override fun updateLayout(
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        state.reset()
        refresh(model, lastPosition)
    }

    override fun seek(
        model: LyricRenderModel,
        state: LineRenderState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        lastPosition = posMs
        refresh(model, posMs)
    }

    override fun update(
        model: LyricRenderModel,
        state: LineRenderState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int,
        playbackSpeed: Float
    ) {
        if (posMs != lastPosition) {
            lastPosition = posMs
            refresh(model, posMs)
        }
    }

    override fun step(
        deltaNanos: Long,
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int
    ): Boolean = false

    override fun draw(
        canvas: Canvas,
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        if (shownText.isEmpty()) return
        plainPaint.color = if (leftMs <= AMBER_THRESHOLD_MS) {
            Color.parseColor("#FFC107")
        } else {
            Color.WHITE
        }

        val fm = plainPaint.fontMetrics
        val baseY = (viewHeight - (fm.descent - fm.ascent)) / 2f - fm.ascent
        val textW = plainPaint.measureText(shownText)
        val x = when {
            model.isAlignedRight -> viewWidth - textW
            centerIfPossible -> (viewWidth - textW) / 2f
            rightIfPossible -> viewWidth - textW
            else -> 0f
        }

        canvas.save()
        canvas.clipRect(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        canvas.drawText(shownText, x, baseY, plainPaint)
        canvas.restore()
    }

    override fun reset(state: LineRenderState) {
        lastPosition = Long.MIN_VALUE
        shownText = ""
        state.reset()
    }

    override fun freeze(model: LyricRenderModel, state: LineRenderState, viewWidth: Int) {
        // 倒计时无动画
    }

    override fun syncFrom(other: LineRenderer) {
        if (other is CountdownPainter) {
            lastPosition = other.lastPosition
            shownText = other.shownText
            leftMs = other.leftMs
        }
    }

    private fun refresh(model: LyricRenderModel, posMs: Long) {
        if (posMs == Long.MIN_VALUE) {
            shownText = ""
            leftMs = 0L
            return
        }
        leftMs = max(0L, model.end - posMs)
        shownText = format(leftMs)
    }

    private fun format(ms: Long): String {
        val tenths = (ms + 99) / 100
        val min = tenths / 600
        val sec = (tenths / 10) % 60
        val tenthsDigit = tenths % 10
        return "%02d:%02d.%d".format(min, sec, tenthsDigit)
    }

    companion object {
        private const val AMBER_THRESHOLD_MS = 1500L
    }
}
