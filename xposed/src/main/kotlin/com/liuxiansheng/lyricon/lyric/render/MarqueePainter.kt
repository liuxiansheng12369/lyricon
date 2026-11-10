/*
 * 词岛 · 无缝跑马灯渲染器（自研重写版）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * 文本超宽无逐字时使用。幽灵循环：行宽 + 幽灵间距为一周期，
 * 画本体 + 幽灵副本，滚完 wrap 回 0，视觉无缝。
 */

package com.liuxiansheng.lyricon.lyric.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.liuxiansheng.lyricon.lyric.render.model.LyricRenderModel

internal class MarqueePainter : LineRenderer {

    val plainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    // —— 跑马灯参数 ——
    var pacePxPerSec = 120f
    var gapAfterLoopMs = 800L
    var haltAtLastPass = false
    var startDelayMs = 400L
    var ghostGapPx = 70f
    var maxLoops = -1   // -1 = 无限

    override var centerIfPossible = false
    override var rightIfPossible = false

    // —— 内部状态 ——
    private var cycleOffset = 0f
    private var cycleLen = 0f
    private var rolling = false
    private var inDelay = false
    private var delayNanosLeft = 0L
    private var loopsDone = 0
    private var done = false

    override var lastPosition = Long.MIN_VALUE
        private set

    override val isAnimating: Boolean get() = rolling || inDelay
    override val isStarted: Boolean get() = rolling || inDelay || cycleOffset > 0f
    override val isFinished: Boolean get() = done

    fun applyFontSettings(size: Float, tf: Typeface?, varSettings: String?) {
        plainPaint.textSize = size
        plainPaint.typeface = tf
    }

    fun setColor(color: Int) {
        plainPaint.color = color
    }

    /** 立即从头启动 */
    fun kickstart() {
        resetState()
        if (cycleLen <= 0f) return
        if (startDelayMs > 0L) {
            armDelay(startDelayMs)
        } else {
            rolling = true
            inDelay = false
        }
    }

    override fun updateLayout(
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        cycleLen = model.width + ghostGapPx
        state.reset()
    }

    override fun seek(
        model: LyricRenderModel,
        state: LineRenderState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        lastPosition = posMs
        if (model.width <= viewWidth) {
            state.scrollOffset = 0f
            state.isScrollFinished = true
            markDone()
        } else {
            restartCycle(model, state)
        }
    }

    override fun update(
        model: LyricRenderModel,
        state: LineRenderState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int,
        playbackSpeed: Float
    ) {
        // 媒体回退 → 从头跑
        if (lastPosition != Long.MIN_VALUE && posMs < lastPosition) {
            restartCycle(model, state)
        }
        lastPosition = posMs
        cycleLen = model.width + ghostGapPx
        if (model.width > viewWidth) {
            autoStartIfIdle()
        }
    }

    override fun step(
        deltaNanos: Long,
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int
    ): Boolean {
        cycleLen = model.width + ghostGapPx
        if (done) return false

        val vw = viewWidth.toFloat()
        // 文本不超宽：无滚动
        if (model.width <= vw) {
            state.scrollOffset = 0f
            state.isScrollFinished = true
            markDone()
            return false
        }

        // 延迟期：只倒计时
        if (inDelay) {
            delayNanosLeft -= deltaNanos.coerceAtLeast(0L)
            if (delayNanosLeft <= 0L) {
                inDelay = false
                rolling = true
            }
            return false
        }
        if (!rolling) return false

        // 推进偏移
        val advance = pacePxPerSec * (deltaNanos.coerceAtLeast(0L) / 1_000_000_000f)
        cycleOffset += advance

        // 停在末尾模式：滚到文本末尾即停
        val isLastLoop = maxLoops > 0 && (loopsDone + 1) >= maxLoops
        if (haltAtLastPass && isLastLoop) {
            val target = model.width - vw
            if (cycleOffset >= target) {
                cycleOffset = target
                state.scrollOffset = -target
                state.isScrollFinished = true
                markDone()
                return true
            }
        }

        // 滚完一圈 wrap
        if (cycleOffset >= cycleLen) {
            cycleOffset -= cycleLen
            loopsDone++
            if (maxLoops < 0 || loopsDone < maxLoops) {
                armDelay(gapAfterLoopMs)
                state.scrollOffset = 0f
                state.isScrollFinished = false
            } else {
                state.scrollOffset = 0f
                state.isScrollFinished = true
                markDone()
            }
            return true
        }

        state.scrollOffset = -cycleOffset
        state.isScrollFinished = false
        return true
    }

    override fun draw(
        canvas: Canvas,
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        val vw = viewWidth.toFloat()
        val fm = plainPaint.fontMetrics
        val baseY = (viewHeight - (fm.descent - fm.ascent)) / 2f - fm.ascent

        canvas.save()
        canvas.clipRect(0f, 0f, vw, viewHeight.toFloat())

        // 不超宽：静态对齐
        if (model.width <= vw) {
            val ox = when {
                model.isAlignedRight || rightIfPossible -> vw - model.width
                centerIfPossible -> (vw - model.width) / 2f
                else -> 0f
            }
            canvas.drawText(model.fullText, ox, baseY, plainPaint)
            canvas.restore()
            return
        }

        // 超宽：无缝跑马灯（本体 + 幽灵副本）
        val scroll = state.scrollOffset
        val startX = if (model.isAlignedRight || rightIfPossible) {
            (vw - model.width) + scroll
        } else {
            scroll
        }
        canvas.drawText(model.fullText, startX, baseY, plainPaint)
        val ghostX = startX + (model.width + ghostGapPx)
        if (ghostX < vw) {
            canvas.drawText(model.fullText, ghostX, baseY, plainPaint)
        }
        canvas.restore()
    }

    override fun reset(state: LineRenderState) {
        resetState()
        lastPosition = Long.MIN_VALUE
        state.reset()
    }

    override fun freeze(model: LyricRenderModel, state: LineRenderState, viewWidth: Int) {
        rolling = false
        inDelay = false
    }

    override fun syncFrom(other: LineRenderer) {
        if (other is MarqueePainter) {
            cycleOffset = other.cycleOffset
            cycleLen = other.cycleLen
            lastPosition = other.lastPosition
            rolling = other.rolling
            inDelay = other.inDelay
            delayNanosLeft = other.delayNanosLeft
            loopsDone = other.loopsDone
            done = other.done
            pacePxPerSec = other.pacePxPerSec
            haltAtLastPass = other.haltAtLastPass
            startDelayMs = other.startDelayMs
            ghostGapPx = other.ghostGapPx
            maxLoops = other.maxLoops
        }
    }

    // —— 私有辅助 ——

    private fun restartCycle(model: LyricRenderModel, state: LineRenderState) {
        resetState()
        cycleLen = model.width + ghostGapPx
        state.reset()
        if (cycleLen <= 0f) return
        if (maxLoops == 0) {
            markDone()
            return
        }
        if (startDelayMs > 0L) {
            armDelay(startDelayMs)
        } else {
            rolling = true
            inDelay = false
        }
    }

    private fun autoStartIfIdle() {
        if (done || rolling || inDelay) return
        if (startDelayMs > 0L) {
            armDelay(startDelayMs)
        } else {
            rolling = true
            inDelay = false
        }
    }

    private fun armDelay(delayMs: Long) {
        if (delayMs <= 0L) {
            rolling = true
            inDelay = false
        } else {
            delayNanosLeft = delayMs * 1_000_000L
            inDelay = true
            rolling = false
        }
    }

    private fun resetState() {
        cycleOffset = 0f
        rolling = false
        inDelay = false
        delayNanosLeft = 0L
        loopsDone = 0
        done = false
    }

    private fun markDone() {
        rolling = false
        inDelay = false
        done = true
    }
}
