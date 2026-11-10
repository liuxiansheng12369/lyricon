/*
 * 词岛 · 歌词画布绘制器（自研重写版）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * 职责：把渲染模型（文本+词+宽度）画到 Canvas 上。
 * 双层结构：未播暗色层 + 已播亮色层，支持彩虹、逐字波浪、拉长音发光。
 */

package com.liuxiansheng.lyricon.lyric.render.draw

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.TextPaint
import androidx.core.graphics.withSave
import com.liuxiansheng.lyricon.lyric.render.anim.GradientShaderFactory
import com.liuxiansheng.lyricon.lyric.render.model.LyricRenderModel
import kotlin.math.max
import kotlin.math.min

/** 拉长音发光状态（自研） */
internal data class SustainGlow(
    val x0: Float,
    val x1: Float,
    val radius: Float,
    val alpha: Int,
    val strength: Float
)

internal class LyricCanvasPainter {

    // —— 颜色 ——
    private var dimColors = intArrayOf(Color.GRAY)
    private var litColors = intArrayOf(Color.WHITE)

    /** 1.0.9：整句连续配色偏移。超级岛左右分段时，右段接着左段的色序继续（只旋转调色板，不改绘制方式） */
    var paletteOffset: Int = 0

    // —— 波浪动效参数 ——
    private var hanByChar = true
    private var hanLift = 0.4f
    private var hanWave = 0.9f
    private var latinByChar = false
    private var latinLift = 0.3f
    private var latinWave = 0.7f

    // —— 发光开关 ——
    var glowEnabled = false

    // —— 字体度量 ——
    private val fm = Paint.FontMetrics()
    private var baselineY = 0f

    // —— 渐变缓存 ——
    private val dimGrad = GradientHolder()
    private val litGrad = GradientHolder()

    val hasRainbowDim get() = dimColors.size > 1
    val hasRainbowLit get() = litColors.size > 1

    fun applyColors(dim: IntArray, lit: IntArray) {
        if (dim.isNotEmpty()) dimColors = dim
        if (lit.isNotEmpty()) litColors = lit
        dimGrad.invalidate()
        litGrad.invalidate()
    }

    fun syncMetrics(paint: TextPaint) {
        paint.getFontMetrics(fm)
        baselineY = -(fm.descent + fm.ascent) / 2f
    }

    fun dropGradientCache() {
        dimGrad.invalidate()
        litGrad.invalidate()
    }

    /** 逐字动效底部留白 */
    fun liftReserve(model: LyricRenderModel, size: Float): Float {
        if (model.words.isEmpty()) return 0f
        return max(hanLift, latinLift) * size
    }

    /**
     * 核心绘制（自研重写：双层裁剪 + 逐字波浪 + 发光描边）
     */
    fun render(
        canvas: Canvas,
        model: LyricRenderModel,
        viewW: Int,
        viewH: Int,
        scrollOff: Float,
        overflow: Boolean,
        litWidth: Float,
        fadeIn: Boolean,
        marqueeOnly: Boolean,
        waveOn: Boolean,
        centerOn: Boolean,
        rightOn: Boolean,
        dimPaint: TextPaint,
        litPaint: TextPaint,
        plainPaint: TextPaint,
        glows: List<SustainGlow>
    ) {
        val reserve = if (waveOn && !marqueeOnly) liftReserve(model, dimPaint.textSize) else 0f
        val h = (viewH.toFloat() - reserve).coerceAtLeast(0f)
        val baseY = h / 2f + baselineY

        canvas.withSave {
            clipRect(0f, 0f, viewW.toFloat(), viewH.toFloat())

            // 计算 X 偏移（居中/右对齐/滚动）
            val ox = when {
                model.isAlignedRight -> if (overflow) scrollOff else viewW - model.width
                centerOn -> if (overflow) scrollOff else (viewW - model.width) / 2f
                rightOn -> if (overflow) scrollOff else viewW - model.width
                overflow -> scrollOff
                else -> 0f
            }
            translate(ox, 0f)

            val visStart = (-ox).coerceAtLeast(0f)
            val visEnd = (viewW - ox).coerceAtMost(model.width)
            if (visEnd <= visStart) return@withSave

            val str = model.fullText

            // 纯跑马灯：整行一次画完
            if (marqueeOnly) {
                canvas.drawText(str, 0f, baseY, plainPaint)
                return@withSave
            }

            // —— 暗色层（未播部分）——
            if (hasRainbowDim) {
                // 一字一色：每个字画自己的纯色，但边界按已唱宽度在字内部切（高亮连续推进）
                paintCharColored(canvas, model, dimColors, lit = false,
                    litWidth = litWidth, clipL = max(litWidth, visStart), clipR = visEnd,
                    viewH = viewH, baseY = baseY, paint = dimPaint,
                    waveOn = waveOn)
            } else {
                dimPaint.shader = null
                if (waveOn) {
                    paintWaveUnits(canvas, model, litWidth, max(litWidth, visStart), visEnd,
                        viewH, baseY, dimPaint)
                } else {
                    canvas.withSave {
                        clipRect(litWidth, 0f, Float.MAX_VALUE, viewH.toFloat())
                        canvas.drawText(str, 0f, baseY, dimPaint)
                    }
                }
            }

            // —— 亮色层（已播部分）——
            if (litWidth > 0f) {
                val litEnd = min(model.width, litWidth)
                // 发光词的排除区间（避免亮色与发光描边重叠发糊）
                val glowRanges = glows.mapNotNull {
                    val s = it.x0.coerceAtLeast(0f)
                    val e = it.x1.coerceAtMost(model.width)
                    if (e > s) s to e else null
                }.sortedBy { it.first }

                if (hasRainbowLit) {
                    paintCharColored(canvas, model, litColors, lit = true,
                        litWidth = litWidth, clipL = visStart, clipR = min(litEnd, visEnd),
                        viewH = viewH, baseY = baseY, paint = litPaint,
                        waveOn = waveOn)
                } else {
                    canvas.withSave {
                        clipRect(0f, 0f, litEnd, viewH.toFloat())
                        litPaint.shader = null
                        if (waveOn) {
                            paintWaveUnits(canvas, model, litWidth,
                                visStart, min(litEnd, visEnd), viewH, baseY, litPaint)
                        } else {
                            paintWithExclusion(canvas, str, baseY, litPaint,
                                0f, litEnd, glowRanges, viewH)
                        }
                    }
                }

                // —— 拉长音发光层 ——
                glows.forEach { drawGlow(canvas, model, baseY, viewH, it, litPaint) }
            }
        }
    }

    /** 一个字的着色画笔（复用，避免每帧 new） */
    private val unitPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private fun unitPaintOf(base: TextPaint, colors: IntArray, index: Int): TextPaint {
        unitPaint.set(base)
        unitPaint.shader = null
        unitPaint.color = colors[if (index >= 0) (index + paletteOffset) % colors.size else 0]
        return unitPaint
    }

    /**
     * 一字一色 + 连续推进：
     * 每个字用调色板里自己的颜色画，但 clip 边界按已唱宽度 litWidth 在字内部切，
     * 所以高亮沿着一行平滑往前推，不会"整字跳"。
     * @param lit true=已播层（裁 字左端→litWidth）；false=未播层（裁 litWidth→字右端）
     */
    private fun paintCharColored(
        canvas: Canvas,
        model: LyricRenderModel,
        colors: IntArray,
        lit: Boolean,
        litWidth: Float,
        clipL: Float,
        clipR: Float,
        viewH: Int,
        baseY: Float,
        paint: TextPaint,
        waveOn: Boolean
    ) {
        var idx = 0
        for (word in model.words) {
            val byChar = word.containsCjk || latinByChar
            val liftF = if (word.containsCjk) hanLift else latinLift
            val waveF = if (word.containsCjk) hanWave else latinWave

            if (!byChar || word.charStartPositions.size < word.chars.size) {
                drawOneColored(canvas, word.text, 0, word.text.length,
                    word.startPosition, word.startPosition, word.endPosition,
                    colors, idx, lit, litWidth, clipL, clipR, viewH, baseY, paint,
                    liftF, waveF, waveOn)
                idx++
                continue
            }
            for (i in word.chars.indices) {
                drawOneColored(canvas, word.text, i, i + 1,
                    word.charStartPositions[i],
                    word.charStartPositions[i], word.charEndPositions[i],
                    colors, idx, lit, litWidth, clipL, clipR, viewH, baseY, paint,
                    liftF, waveF, waveOn)
                idx++
            }
        }
    }

    private fun drawOneColored(
        canvas: Canvas, text: String, sIdx: Int, eIdx: Int,
        drawX: Float, unitL: Float, unitR: Float,
        colors: IntArray, index: Int,
        lit: Boolean, litWidth: Float,
        clipL: Float, clipR: Float,
        viewH: Int, baseY: Float, paint: TextPaint,
        liftF: Float, waveF: Float, waveOn: Boolean
    ) {
        // 层的边界：已播层只到 litWidth，未播层从 litWidth 起
        val layerR = if (lit) min(unitR, litWidth) else unitR
        val layerL = if (lit) unitL else max(unitL, litWidth)
        val vL = max(layerL, clipL)
        val vR = min(layerR, clipR)
        if (vR <= vL) return
        val liftY = if (waveOn) waveOffset(litWidth, unitL, unitR, paint.textSize, liftF, waveF) else 0f
        val p = unitPaintOf(paint, colors, index)
        canvas.withSave {
            clipRect(vL, 0f, vR, viewH.toFloat())
            drawText(text, sIdx, eIdx, drawX, baseY + liftY, p)
        }
    }

    /** 逐字波浪绘制：每个字独立裁剪 + 高亮扫过时抬升 */
    private fun paintWaveUnits(
        canvas: Canvas,
        model: LyricRenderModel,
        litWidth: Float,
        clipL: Float,
        clipR: Float,
        viewH: Int,
        baseY: Float,
        paint: TextPaint
    ) {
        for (word in model.words) {
            val byChar = if (word.containsCjk) hanByChar else latinByChar
            val liftF = if (word.containsCjk) hanLift else latinLift
            val waveF = if (word.containsCjk) hanWave else latinWave

            if (!byChar) {
                paintOneUnit(canvas, word.text, 0, word.text.length,
                    word.startPosition, word.startPosition, word.endPosition,
                    litWidth, clipL, clipR, viewH, baseY, paint, liftF, waveF)
                continue
            }
            for (i in word.chars.indices) {
                paintOneUnit(canvas, word.text, i, i + 1,
                    word.charStartPositions[i],
                    word.charStartPositions[i], word.charEndPositions[i],
                    litWidth, clipL, clipR, viewH, baseY, paint, liftF, waveF)
            }
        }
    }

    private fun paintOneUnit(
        canvas: Canvas, text: String, sIdx: Int, eIdx: Int,
        drawX: Float, unitL: Float, unitR: Float,
        litWidth: Float, clipL: Float, clipR: Float,
        viewH: Int, baseY: Float, paint: TextPaint,
        liftF: Float, waveF: Float
    ) {
        if (unitR <= clipL || unitL >= clipR) return
        val vL = unitL.coerceAtLeast(clipL)
        val vR = unitR.coerceAtMost(clipR)
        val liftY = waveOffset(litWidth, unitL, unitR, paint.textSize, liftF, waveF)
        canvas.withSave {
            clipRect(vL, 0f, vR, viewH.toFloat())
            drawText(text, sIdx, eIdx, drawX, baseY + liftY, paint)
        }
    }

    /** 高亮扫过时波浪抬升量（easeOutQuint 衰减） */
    private fun waveOffset(
        litWidth: Float, unitL: Float, unitR: Float,
        size: Float, liftF: Float, waveF: Float
    ): Float {
        if (liftF <= 0f || waveF <= 0f) return 0f
        val maxOff = size * liftF
        val center = (unitL + unitR) / 2f
        val waveLen = size * waveF
        val phase = ((litWidth - center) / waveLen).coerceIn(0f, 1f)
        return maxOff * (1f - easeOut5(phase))
    }

    private fun easeOut5(v: Float): Float {
        val inv = 1f - v
        return 1f - inv * inv * inv * inv * inv
    }

    /** 亮色文本绘制（发光词区间排除，避免与描边发光重叠） */
    private fun paintWithExclusion(
        canvas: Canvas, text: String, baseY: Float, paint: TextPaint,
        clipL: Float, clipR: Float,
        ranges: List<Pair<Float, Float>>, viewH: Int
    ) {
        val L = clipL.coerceAtLeast(0f)
        val R = clipR.coerceAtLeast(L)
        if (R <= L) return
        val clipped = ranges.mapNotNull { (s, e) ->
            if (e <= s || e <= L || s >= R) null
            else s.coerceIn(L, R) to e.coerceIn(L, R)
        }.sortedBy { it.first }
        if (clipped.isEmpty()) {
            canvas.withSave {
                clipRect(L, 0f, R, viewH.toFloat())
                canvas.drawText(text, 0f, baseY, paint)
            }
            return
        }
        var cursor = L
        clipped.forEach { (s, e) ->
            if (s > cursor) {
                canvas.withSave { clipRect(cursor, 0f, s, viewH.toFloat()); canvas.drawText(text, 0f, baseY, paint) }
            }
            cursor = max(cursor, e)
        }
        if (cursor < R) {
            canvas.withSave { clipRect(cursor, 0f, R, viewH.toFloat()); canvas.drawText(text, 0f, baseY, paint) }
        }
    }

    /** 拉长音发光：外圈描边 + 内圈描边 + 核心填充 */
    private fun drawGlow(
        canvas: Canvas, model: LyricRenderModel,
        baseY: Float, viewH: Int, glow: SustainGlow, litPaint: TextPaint
    ) {
        val L = glow.x0.coerceAtLeast(0f)
        val R = glow.x1.coerceAtMost(model.width)
        if (R <= L) return
        val base = (litColors.firstOrNull() ?: litPaint.color) and 0x00FFFFFF
        val tint = lighten(base, 0.22f)
        val rainbow = if (hasRainbowLit) getGrad(0f, model.width, litColors, litGrad) else null
        val d = litPaint.density.takeIf { it > 0f } ?: 1f
        val outerW = (glow.radius * 0.18f).coerceAtLeast(d * 0.25f)
        val innerW = (glow.radius * 0.1f).coerceAtLeast(d * 0.18f)
        val outerA = (glow.alpha * 0.28f * glow.strength).toInt().coerceIn(0, 255)
        val innerA = (glow.alpha * 0.46f).toInt().coerceIn(0, 255)
        val coreColor = (0xFF shl 24) or base

        val p = TextPaint(litPaint)
        p.shader = null
        canvas.withSave {
            clipRect(L, 0f, R, viewH.toFloat())
            // 外圈
            p.style = Paint.Style.STROKE; p.strokeWidth = outerW
            if (rainbow != null) { p.shader = rainbow; p.alpha = outerA; p.color = Color.WHITE }
            else { p.shader = null; p.alpha = 255; p.color = (outerA shl 24) or tint }
            canvas.drawText(model.fullText, 0f, baseY, p)
            // 内圈
            p.strokeWidth = innerW
            if (rainbow != null) { p.shader = rainbow; p.alpha = innerA; p.color = Color.WHITE }
            else { p.shader = null; p.alpha = 255; p.color = (innerA shl 24) or tint }
            canvas.drawText(model.fullText, 0f, baseY, p)
            // 核心
            p.style = Paint.Style.FILL; p.strokeWidth = 0f
            if (rainbow != null) { p.shader = rainbow; p.alpha = 255; p.color = Color.WHITE }
            else { p.shader = null; p.alpha = 255; p.color = coreColor }
            canvas.drawText(model.fullText, 0f, baseY, p)
        }
    }

    private fun lighten(color: Int, ratio: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * ratio).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    // —— 渐变缓存 ——
    private fun getGrad(startX: Float, endX: Float, colors: IntArray, holder: GradientHolder): Shader {
        // 色序偏移：只按 paletteOffset 旋转调色板，绘制方式保持老版（整行平滑渐变 + 裁剪）
        val hash = colors.contentHashCode() * 31 + paletteOffset
        if (holder.shader == null || holder.startX != startX || holder.endX != endX || holder.hash != hash) {
            val rotated = if (paletteOffset == 0 || colors.isEmpty()) {
                colors
            } else {
                IntArray(colors.size) { colors[(it + paletteOffset) % colors.size] }
            }
            holder.shader = GradientShaderFactory.create(startX, endX, rotated)
            holder.startX = startX
            holder.endX = endX
            holder.hash = hash
        }
        return holder.shader!!
    }

    private class GradientHolder {
        var shader: LinearGradient? = null
        var startX = Float.NaN
        var endX = Float.NaN
        var hash = 0
        fun invalidate() { shader = null; startX = Float.NaN; endX = Float.NaN; hash = 0 }
    }
}
