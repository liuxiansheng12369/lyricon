/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 歌词动画效果渲染器（V3 自研）。
 * 26 种持续/循环动画效果，逐字绘制整行文本。
 * 效果模式下歌词以整行文本动画呈现（保留逐字高亮：已唱区用高亮色），
 * 设置选"无/静态"则走正常逐字渲染。
 */

package com.liuxiansheng.lyricon.lyric.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.TextPaint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 歌词动画效果渲染器（独立于逐字渲染，仅画整行文本 + 动画） */
internal class LyricEffectRenderer {

    companion object {
        const val EFFECT_STATIC = 0
        const val EFFECT_WAVE = 1
        const val EFFECT_DOTS = 2
        const val EFFECT_BOUNCE_DOTS = 3
        const val EFFECT_TYPEWRITER = 4
        const val EFFECT_NEON = 5
        const val EFFECT_BLUR = 6
        const val EFFECT_FADE_LOOP = 7
        const val EFFECT_SCALE_BREATH = 8
        const val EFFECT_SLIDE_LR = 9
        const val EFFECT_COLOR_FLOW = 10
        const val EFFECT_CHAR_ZOOM = 11
        const val EFFECT_FLOAT = 12
        const val EFFECT_FLIP_3D = 13
        const val EFFECT_SPOTLIGHT = 14
        const val EFFECT_MIRROR = 15
        const val EFFECT_PIXELATE = 16
        const val EFFECT_LASER_SCAN = 17
        const val EFFECT_PARTICLE = 18
        const val EFFECT_GLITCH = 19
        const val EFFECT_HANDWRITE = 20
        const val EFFECT_DANMAKU = 21
        const val EFFECT_WAVEFORM_BG = 22
        const val EFFECT_STARS = 23
        const val EFFECT_ROTATE_APPEAR = 24
        const val EFFECT_INDIA_CAMERA = 25

        /** 效果名称（对齐设置页选项） */
        fun nameOf(value: Int): String = when (value) {
            EFFECT_STATIC -> "静态"
            EFFECT_WAVE -> "波浪起伏"
            EFFECT_DOTS -> "三点倒计时"
            EFFECT_BOUNCE_DOTS -> "歌名+三点"
            EFFECT_TYPEWRITER -> "打字机"
            EFFECT_NEON -> "霓虹灯"
            EFFECT_BLUR -> "模糊入场"
            EFFECT_FADE_LOOP -> "渐入淡出"
            EFFECT_SCALE_BREATH -> "缩放呼吸"
            EFFECT_SLIDE_LR -> "左右滑动"
            EFFECT_COLOR_FLOW -> "颜色流动"
            EFFECT_CHAR_ZOOM -> "逐字放大"
            EFFECT_FLOAT -> "上下浮动"
            EFFECT_FLIP_3D -> "3D翻转"
            EFFECT_SPOTLIGHT -> "聚光灯"
            EFFECT_MIRROR -> "镜像倒影"
            EFFECT_PIXELATE -> "像素化"
            EFFECT_LASER_SCAN -> "激光扫描"
            EFFECT_PARTICLE -> "粒子聚合"
            EFFECT_GLITCH -> "故障艺术"
            EFFECT_HANDWRITE -> "手写效果"
            EFFECT_DANMAKU -> "弹幕滚动"
            EFFECT_WAVEFORM_BG -> "波形背景"
            EFFECT_STARS -> "星星散落"
            EFFECT_ROTATE_APPEAR -> "旋转出现"
            EFFECT_INDIA_CAMERA -> "印度运镜"
            else -> "波浪起伏"
        }
    }

    private var phase = 0f
    private var baseSpeed = 0.05f

    private var charWidths = FloatArray(0)
    private var cachedText = ""
    private var cachedTextSize = 0f
    private var cachedTypeface: android.graphics.Typeface? = null
    private val colorFlowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val tintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val glitchLinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val neonColors = intArrayOf(
        0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
        0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt()
    )
    private val laserPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    /** 装饰层（波形背景/星星）专用画笔：取高亮亮色，不借用用户那两套画笔 */
    private val decorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private class Particle {
        var x = 0f
        var y = 0f
        var targetX = 0f
        var targetY = 0f
        var vx = 0f
        var vy = 0f
        var size = 3f
        var alpha = 255
        var life = 0f
        var maxLife = 1f
    }

    private val particles = Array(50) { Particle() }
    private val starParticles = Array(15) { Particle() }
    private val waveformHeights = FloatArray(15)

    // 逐字高亮：已唱区用高亮色，未唱区用正文色（与逐字渲染一致）
    private var basePaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var highPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var highlightWidth = 0f
    // 用户设置的多色调色板（彩虹/封面渐变会是多色数组）；为空则回落到画笔单色
    private var basePalette: IntArray = IntArray(0)
    private var highPalette: IntArray = IntArray(0)
    private val charTintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    /** 1.0.9：整句连续配色偏移（超级岛左右分段时右段接着左段色序） */
    var paletteOffset: Int = 0

    /** 高亮分遍强制档位：-1 = 按 x 判（老行为）；0 = 强制未唱色；1 = 强制已唱色。
     *  字内切高亮时，同一帧分别用 0/1 画两遍再各自裁剪。 */
    private var forceLit: Int = -1

    /** 按当前 x 位置 + 字符序号选字色：已唱区用高亮调色板，未唱区用正文调色板；
     *  多色时按字轮换（彩虹/封面渐变的每一色都用上，不再"全篇首色"）。 */
    private fun charPaint(x: Float, index: Int): TextPaint {
        val lit = when (forceLit) {
            0 -> false
            1 -> true
            else -> x <= highlightWidth
        }
        val p = if (lit) highPaint else basePaint
        val palette = if (lit) highPalette else basePalette
        if (palette.isEmpty()) return p
        // 关键：调色板色是不透明的，直接 set 颜色会把效果临时设的 alpha 冲掉，
        // 于是聚光灯/渐入淡出/镜像这类"靠透明度做变化"的效果全都看不出变化。
        val keepAlpha = p.alpha
        val keepFilter = p.maskFilter
        charTintPaint.set(p)
        charTintPaint.color = palette[if (index >= 0) (index + paletteOffset) % palette.size else 0]
        charTintPaint.alpha = keepAlpha
        charTintPaint.maskFilter = keepFilter
        return charTintPaint
    }

    fun setSpeed(speedMultiplier: Float) {
        baseSpeed = 0.05f * speedMultiplier.coerceAtLeast(0.3f)
    }

    /** 帧推进（效果动画计时） */
    fun step(deltaNanos: Long, effectMode: Int) {
        if (effectMode == EFFECT_STATIC) return
        phase += baseSpeed
        if (phase > 10000f) phase = 0f
    }

    fun reset() {
        phase = 0f
        charWidths = FloatArray(0)
        cachedText = ""
        cachedTextSize = 0f
        for (p in particles) p.life = 0f
        for (s in starParticles) s.life = 0f
    }

    fun draw(
        canvas: Canvas,
        txt: String,
        paint: TextPaint,
        hlPaint: TextPaint,
        viewWidth: Int,
        viewHeight: Int,
        effectMode: Int,
        highlightWidth: Float,
        basePalette: IntArray = IntArray(0),
        highPalette: IntArray = IntArray(0)
    ) {
        basePaint = paint
        highPaint = hlPaint
        this.highlightWidth = highlightWidth
        this.basePalette = basePalette
        this.highPalette = highPalette
        if (txt.isEmpty()) {
            if (effectMode == EFFECT_DOTS) drawDotsOnly(canvas, paint, viewWidth, viewHeight)
            return
        }
        val y = (viewHeight / 2f) - (paint.descent() + paint.ascent()) / 2f

        if (charWidths.size != txt.length || cachedText != txt || cachedTextSize != paint.textSize ||
            cachedTypeface !== paint.typeface
        ) {
            // 关键：字宽用"整行前缀测量"的差值，而不是单字 measureText。
            // 单字测量会丢掉字体自身的连字/字距（kerning）与整行排版规则，
            // 动画效果下的字看起来就像换了一套字体、字距也发虚。
            charWidths = FloatArray(txt.length)
            var prev = 0f
            for (i in txt.indices) {
                val prefix = paint.measureText(txt, 0, i + 1)
                charWidths[i] = prefix - prev
                prev = prefix
            }
            cachedText = txt
            cachedTextSize = paint.textSize
            cachedTypeface = paint.typeface
        }

        val startX = 0f
        // 部分效果会临时改画笔的颜色/透明度/滤镜（如霓虹脉冲、聚光灯、故障叠色），
        // 收尾若不还原，就会污染下一帧的未唱色（表现为"颜色一直是某个固定色"）。
        // 这里统一快照 + 还原，保证效果只借用画笔、不改用户设置的颜色。
        val savedBaseColor = paint.color
        val savedBaseAlpha = paint.alpha
        val savedHighColor = hlPaint.color
        val savedHighAlpha = hlPaint.alpha
        try {
            // 高亮推进改成"字内切"：同一帧画两遍——未唱色画 [已唱宽度, 行尾]、
            // 已唱色画 [0, 已唱宽度]，边界字就只亮一半，高亮是一点点往前推而不是整字跳。
            if (supportsIntraCharHighlight(effectMode)) {
                forceLit = 0
                canvas.save()
                canvas.clipRect(highlightWidth, 0f, viewWidth.toFloat(), viewHeight.toFloat())
                drawEffect(canvas, txt, startX, y, effectMode, paint, viewWidth, viewHeight)
                canvas.restore()

                if (highlightWidth > 0f) {
                    forceLit = 1
                    canvas.save()
                    canvas.clipRect(0f, 0f, highlightWidth, viewHeight.toFloat())
                    drawEffect(canvas, txt, startX, y, effectMode, paint, viewWidth, viewHeight)
                    canvas.restore()
                }
                forceLit = -1
            } else {
                forceLit = -1
                drawEffect(canvas, txt, startX, y, effectMode, paint, viewWidth, viewHeight)
            }
        } finally {
            forceLit = -1
            paint.color = savedBaseColor
            paint.alpha = savedBaseAlpha
            paint.maskFilter = null
            paint.shader = null
            hlPaint.color = savedHighColor
            hlPaint.alpha = savedHighAlpha
            hlPaint.maskFilter = null
            hlPaint.shader = null
        }
    }

    /**
     * 哪些效果支持"字内切高亮"（两遍绘制）。带满屏背景/粒子的效果不拆，
     * 否则拆两遍会把它们的背景也裁掉一半（粒子还会被推进两次）。
     */
    private fun supportsIntraCharHighlight(mode: Int): Boolean = when (mode) {
        EFFECT_WAVEFORM_BG, EFFECT_STARS, EFFECT_PARTICLE, EFFECT_LASER_SCAN -> false
        else -> true
    }

    /** 一遍效果绘制（高亮推进拆两遍时同一帧会调用两次） */
    private fun drawEffect(
        canvas: Canvas,
        txt: String,
        startX: Float,
        y: Float,
        effectMode: Int,
        paint: TextPaint,
        viewWidth: Int,
        viewHeight: Int
    ) {
        when (effectMode) {
            EFFECT_STATIC -> drawStatic(canvas, txt, startX, y, paint)
            EFFECT_WAVE -> drawWave(canvas, txt, startX, y, paint)
            EFFECT_DOTS -> drawDotsOnly(canvas, paint, viewWidth, viewHeight)
            EFFECT_BOUNCE_DOTS -> drawBounceWithDots(canvas, txt, startX, y, paint, viewWidth)
            EFFECT_TYPEWRITER -> drawTypewriter(canvas, txt, startX, y, paint)
            EFFECT_NEON -> drawNeon(canvas, txt, startX, y, paint)
            EFFECT_BLUR -> drawBlur(canvas, txt, startX, y, paint, viewHeight)
            EFFECT_FADE_LOOP -> drawFadeLoop(canvas, txt, startX, y, paint)
            EFFECT_SCALE_BREATH -> drawScaleBreath(canvas, txt, startX, y, paint, viewWidth)
            EFFECT_SLIDE_LR -> drawSlideLR(canvas, txt, startX, y, paint, viewWidth)
            EFFECT_COLOR_FLOW -> drawColorFlow(canvas, txt, startX, y, paint, viewWidth)
            EFFECT_CHAR_ZOOM -> drawCharZoom(canvas, txt, startX, y, paint)
            EFFECT_FLOAT -> drawFloat(canvas, txt, startX, y, paint)
            EFFECT_FLIP_3D -> drawFlip3D(canvas, txt, startX, y, paint, viewWidth)
            EFFECT_SPOTLIGHT -> drawSpotlight(canvas, txt, startX, y, paint, viewWidth)
            EFFECT_MIRROR -> drawMirror(canvas, txt, startX, y, paint, viewHeight)
            EFFECT_PIXELATE -> drawStatic(canvas, txt, startX, y, paint) // 已移除：只是模糊，和"模糊入场"重复
            EFFECT_LASER_SCAN -> drawLaserScan(canvas, txt, startX, y, paint, viewWidth, viewHeight)
            EFFECT_PARTICLE -> drawParticle(canvas, txt, startX, y, paint, viewWidth, viewHeight)
            EFFECT_GLITCH -> drawGlitch(canvas, txt, startX, y, paint, viewWidth)
            EFFECT_HANDWRITE -> drawHandwrite(canvas, txt, startX, y, paint)
            EFFECT_DANMAKU -> drawStatic(canvas, txt, startX, y, paint) // 已移除：两遍重复字容易错乱
            EFFECT_WAVEFORM_BG -> drawWaveformBg(canvas, txt, startX, y, paint, viewWidth, viewHeight)
            EFFECT_STARS -> drawStars(canvas, txt, startX, y, paint, viewWidth, viewHeight)
            EFFECT_ROTATE_APPEAR -> drawRotateAppear(canvas, txt, startX, y, paint, viewWidth)
            EFFECT_INDIA_CAMERA -> drawIndiaCamera(canvas, txt, startX, y, paint)
            else -> drawWave(canvas, txt, startX, y, paint)
        }
    }


    private fun drawStatic(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint) {
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
    }

    /** 效果临时改的透明度/滤镜必须同时落到"未唱/已唱"两套画笔上，
     *  否则只有未唱字被改，已唱字完全看不出效果（聚光灯、模糊、霓虹都踩过这个坑）。 */
    private fun effectAlpha(alpha: Int) {
        basePaint.alpha = alpha
        highPaint.alpha = alpha
    }

    private fun effectBlur(radius: Float, alpha: Int, style: BlurMaskFilter.Blur = BlurMaskFilter.Blur.NORMAL) {
        val filter = if (radius > 0.5f) BlurMaskFilter(radius, style) else null
        basePaint.alpha = alpha
        basePaint.maskFilter = filter
        highPaint.alpha = alpha
        highPaint.maskFilter = filter
    }

    /** 用同一支画笔（不参与高亮配色）把整行逐字画一遍，用于外发光/描边等附加层 */
    private fun drawCharsSolid(canvas: Canvas, txt: String, startX: Float, y: Float, p: TextPaint) {
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, p)
            x += charWidths[i]
        }
    }

    private fun drawWave(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint) {
        var x = startX
        for (i in txt.indices) {
            val offsetY = sin(phase + i * 0.5f) * 8f
            canvas.drawText(txt, i, i + 1, x, y - offsetY, charPaint(x, i))
            x += charWidths[i]
        }
    }

    private fun drawBounceWithDots(
        canvas: Canvas, txt: String, startX: Float, y: Float,
        paint: TextPaint, viewWidth: Int
    ) {
        drawWave(canvas, txt, startX, y, paint)
        val totalWidth = charWidths.sum()
        val dotsX = startX + totalWidth + paint.textSize * 0.5f
        drawThreeDots(canvas, dotsX, y, paint.textSize * 0.25f, paint)
    }

    private fun drawDotsOnly(canvas: Canvas, paint: TextPaint, viewWidth: Int, viewHeight: Int) {
        val y = (viewHeight / 2f) - (paint.descent() + paint.ascent()) / 2f
        val dotR = paint.textSize * 0.25f
        val gap = dotR * 2.5f
        val totalW = dotR * 2 * 3 + gap * 2
        val startX = (viewWidth - totalW) / 2f
        drawThreeDots(canvas, startX, y, dotR, paint)
    }

    private fun drawThreeDots(canvas: Canvas, startX: Float, y: Float, dotR: Float, paint: TextPaint) {
        val gap = dotR * 2.5f
        val cx = startX + dotR
        val cy = y - paint.textSize * 0.15f
        for (i in 0..2) {
            val scale = 0.5f + 0.5f * (0.5f + 0.5f * sin(phase + i * 1.2f))
            val alpha = (120 + 135 * (0.5f + 0.5f * sin(phase + i * 1.2f))).toInt()
            paint.alpha = alpha
            canvas.drawCircle(cx + i * (dotR * 2 + gap), cy, dotR * scale, paint)
        }
        paint.alpha = 255
    }

    private fun drawTypewriter(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint) {
        val charSpeed = 0.15f
        val visibleCount = ((phase / charSpeed).toInt() % (txt.length + 2))
        val showCursor = visibleCount <= txt.length
        val count = visibleCount.coerceAtMost(txt.length)
        var x = startX
        for (i in 0 until count) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
        if (showCursor && ((phase * 4).toInt() % 2 == 0)) {
            val cursorH = paint.textSize * 0.8f
            canvas.drawRect(x, y - cursorH, x + paint.textSize * 0.08f, y, paint)
        }
    }

    /**
     * 模糊：按用户要求固定成"已唱清晰 / 未唱很模糊"，不再随时间变化。
     * （旧版是会推进的入场动画，几秒后卡住不动，等于没效果。）
     * 逐字高亮会拆两遍画：forceLit=1 那遍画已唱区（不模糊），
     * forceLit=0 那遍画未唱区（固定强模糊、压低透明度）。
     */
    private fun drawBlur(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewHeight: Int) {
        when (forceLit) {
            1 -> effectBlur(0f, 255)
            0 -> effectBlur(paint.textSize * 0.55f, 110)
            else -> effectBlur(paint.textSize * 0.55f, 130)
        }
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
    }

    private fun drawFadeLoop(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint) {
        val alpha = (((sin(phase) + 1f) / 2f) * 255f).toInt().coerceIn(0, 255)
        effectAlpha(alpha)
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
    }

    private fun drawScaleBreath(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int) {
        val scale = 1f + sin(phase) * 0.1f
        val totalWidth = charWidths.sum()
        val centerX = startX + totalWidth / 2f
        val centerY = y
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
        canvas.restore()
    }

    private fun drawSlideLR(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int) {
        val totalWidth = charWidths.sum()
        val cycle = (phase % 3f) / 3f
        val offsetX: Float
        when {
            cycle < 0.2f -> {
                val p = cycle / 0.2f
                offsetX = -totalWidth * (1f - p)
            }
            cycle < 0.7f -> offsetX = 0f
            else -> {
                val p = (cycle - 0.7f) / 0.3f
                offsetX = viewWidth * p
            }
        }
        var x = startX + offsetX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
    }

    private fun drawColorFlow(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int) {
        val totalWidth = charWidths.sum().coerceAtLeast(1f)
        val offset = (phase * 0.5f) % 1f
        val left = startX - totalWidth * offset
        val right = left + totalWidth * 2f
        // 用用户设置的调色板做流动（彩虹=16 色全用上；固定色=单色，不再写死红→紫）
        val flowColors = if (highPalette.size > 2) {
            highPalette.copyOf(highPalette.size + 1).also { it[it.size - 1] = highPalette[0] }
        } else {
            intArrayOf(basePaint.color, highPaint.color, basePaint.color)
        }
        val shader = LinearGradient(
            left, 0f, right, 0f,
            flowColors, null, Shader.TileMode.CLAMP
        )
        colorFlowPaint.set(paint)
        colorFlowPaint.shader = shader
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, colorFlowPaint)
            x += charWidths[i]
        }
        colorFlowPaint.shader = null
    }

    /**
     * 霓虹灯：外发光（大半径模糊）+ 细描边（饱和色）+ 清晰核心（用户配色）。
     * 之前只是把整行糊掉，看起来就是"模糊"，不是霓虹。
     * 颜色仍取用户设置的高亮色，不写死。
     */
    private fun drawNeon(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint) {
        val pulse = 0.5f + 0.5f * sin(phase * 1.5f)
        val size = paint.textSize

        // 1) 外发光：同一行放大半径模糊，垫在最底下
        val outer = TextPaint(highPaint).apply {
            shader = null
            style = Paint.Style.FILL
            color = highPaint.color
            alpha = (110 + 90 * pulse).toInt().coerceIn(0, 255)
            maskFilter = BlurMaskFilter(size * (0.28f + 0.12f * pulse), BlurMaskFilter.Blur.OUTER)
        }
        drawCharsSolid(canvas, txt, startX, y, outer)
        outer.maskFilter = null

        // 2) 细描边：管灯的样子（细、亮）
        val stroke = TextPaint(highPaint).apply {
            shader = null
            style = Paint.Style.STROKE
            strokeWidth = (size * 0.07f).coerceAtLeast(1.2f)
            color = highPaint.color
            alpha = 255
            maskFilter = null
        }
        drawCharsSolid(canvas, txt, startX, y, stroke)

        // 3) 清晰核心：仍然按用户配色逐字取色，文字本体不发虚
        effectAlpha(255)
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
    }

    private fun drawCharZoom(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint) {
        var x = startX
        for (i in txt.indices) {
            val charPhase = phase + i * 0.3f
            val zoom = 1f + sin(charPhase).coerceAtLeast(0f) * 0.3f
            val charW = charWidths[i]
            val centerX = x + charW / 2f
            canvas.save()
            canvas.scale(zoom, zoom, centerX, y)
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            canvas.restore()
            x += charW
        }
    }

    private fun drawFloat(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint) {
        val offsetY = sin(phase) * 10f
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y - offsetY, charPaint(x, i))
            x += charWidths[i]
        }
    }

    /**
     * 3D 翻转（竖向翻）。
     * 早期版本用 android.graphics.Camera + rotateY，但没设相机距离：像素坐标下
     * camera 矩阵会把整行放大到画布外，看着就是"歌词丢了"。
     * 改成纯 Canvas 缩放翻折（cos 驱动，翻到背面时镜像），
     * 最小幅度留 0.03，永远不会整行消失。
     */
    private fun drawFlip3D(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int) {
        val totalWidth = charWidths.sum()
        val centerX = startX + totalWidth / 2f
        val angle = phase * 1.6f
        val cosA = cos(angle)
        val squash = abs(cosA).coerceAtLeast(0.03f)
        val pivotY = y - paint.textSize * 0.35f
        canvas.save()
        canvas.clipRect(
            0f,
            0f,
            viewWidth.toFloat(),
            (pivotY + paint.textSize * 1.6f)
        )
        canvas.scale(1f, if (cosA >= 0f) squash else -squash, centerX, pivotY)
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
        canvas.restore()
    }

    private fun drawSpotlight(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int) {
        val totalWidth = charWidths.sum()
        val spotlightX = startX + ((phase * 0.5f) % 1f) * totalWidth
        val spotlightRadius = totalWidth * 0.3f
        var x = startX
        for (i in txt.indices) {
            val charCenterX = x + charWidths[i] / 2f
            val distance = abs(charCenterX - spotlightX)
            // 两套画笔都要改：只改未唱那套的话，已唱字完全不参与聚光灯（之前就是这个毛病）
            val alpha = if (distance < spotlightRadius) {
                (255f * (1f - distance / spotlightRadius)).toInt()
            } else {
                40
            }.coerceIn(0, 255)
            effectAlpha(alpha)
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
        effectAlpha(255)
    }

    /**
     * 镜像倒影（效果库里的那一版）：同样以基线翻折并按剩余高度压扁，
     * 否则倒影只有一小截露在视图内。
     */
    private fun drawMirror(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewHeight: Int) {
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
        val gap = paint.textSize * 0.03f
        val glyphHeight = (paint.descent() - paint.ascent()).coerceAtLeast(1f)
        val spaceBelow = (viewHeight - y - gap).coerceAtLeast(1f)
        // 岛内容高度有限，倒影务必压扁到可见空间内，否则会被岛底边裁掉
        val squash = (spaceBelow / glyphHeight).coerceIn(0.18f, 0.35f)
        canvas.save()
        canvas.clipRect(0f, y, Short.MAX_VALUE.toFloat(), viewHeight.toFloat())
        canvas.scale(1f, -squash, 0f, y + gap)
        effectAlpha(70)
        val waveOffset = sin(phase) * 2f
        x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y + waveOffset, charPaint(x, i))
            x += charWidths[i]
        }
        effectAlpha(255)
        canvas.restore()
    }

    private fun drawLaserScan(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int, viewHeight: Int) {
        val scanY = ((phase * 0.6f) % 1f) * viewHeight
        val textBaseline = y
        laserPaint.set(paint)
        laserPaint.alpha = 255
        // 扫描光线跟随用户设置的高亮色（原来是写死的红色）
        val laserColor = highPaint.color
        val laserRgb = laserColor and 0x00FFFFFF
        laserPaint.shader = LinearGradient(
            0f, scanY - 4f, 0f, scanY + 4f,
            intArrayOf(laserRgb, laserColor, laserColor, laserRgb),
            floatArrayOf(0f, 0.4f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, scanY - 3f, viewWidth.toFloat(), scanY + 3f, laserPaint)
        laserPaint.shader = null
        val revealProgress = (scanY / viewHeight).coerceIn(0f, 1f)
        val visibleHeight = viewHeight * revealProgress
        val textTop = textBaseline - paint.textSize
        if (textTop < visibleHeight) {
            val clipBottom = (visibleHeight - textTop).coerceAtMost(paint.textSize * 1.5f)
            canvas.save()
            canvas.clipRect(startX - 10f, textTop, startX + charWidths.sum() + 10f, textTop + clipBottom)
            var x = startX
            for (i in txt.indices) {
                canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
                x += charWidths[i]
            }
            canvas.restore()
        }
    }

    private fun drawParticle(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int, viewHeight: Int) {
        val cycle = (phase % 4f) / 4f
        val totalWidth = charWidths.sum()
        val particleCount = (txt.length * 6).coerceAtMost(50)
        for (i in 0 until particleCount) {
            val charIndex = i % txt.length
            val p = particles[i]
            p.targetX = startX + charWidths.take(charIndex).sum() + charWidths[charIndex] / 2f
            p.targetY = y
            p.size = 2f + (i % 3)
            if (p.life <= 0f) {
                p.x = (i * 37 % viewWidth).toFloat()
                p.y = (i * 53 % viewHeight).toFloat()
                p.life = 1f
            }
        }
        val isGathering = cycle < 0.6f
        for (i in 0 until particleCount) {
            val p = particles[i]
            if (isGathering) {
                p.vx += (p.targetX - p.x) * 0.05f
                p.vy += (p.targetY - p.y) * 0.05f
            } else {
                p.vx += ((i % 7 - 3) * 0.5f)
                p.vy += ((i % 5 - 2) * 0.5f)
            }
            p.vx *= 0.9f
            p.vy *= 0.9f
            p.x += p.vx
            p.y += p.vy
            p.alpha = if (isGathering) 255 else (255 * (1f - (cycle - 0.6f) / 0.4f)).toInt().coerceIn(0, 255)
        }
        for (i in 0 until particleCount) {
            val p = particles[i]
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }
        paint.alpha = 255
        if (cycle > 0.98f) {
            for (i in 0 until particleCount) particles[i].life = 0f
        }

        // 之前这里只画粒子、从来不画字，所以"粒子聚合"永远聚合不出文字。
        // 现在粒子聚到字上之后把文字显出来，散开阶段再淡出。
        val textAlpha = when {
            cycle < 0.45f -> 0
            cycle < 0.62f -> (((cycle - 0.45f) / 0.17f) * 255f).toInt()
            cycle < 0.85f -> 255
            else -> ((1f - (cycle - 0.85f) / 0.15f) * 255f).toInt()
        }.coerceIn(0, 255)
        if (textAlpha > 0) {
            effectAlpha(textAlpha)
            var x = startX
            for (i in txt.indices) {
                canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
                x += charWidths[i]
            }
        }
    }

    private fun drawGlitch(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int) {
        val glitchActive = (sin(phase * 2.1f) > 0.7f) || (sin(phase * 3.7f) > 0.85f)
        val glitchOffset = if (glitchActive) (sin(phase * 10f) * 6f).toInt() else 0
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
        if (glitchActive && glitchOffset != 0) {
            // 错位叠色跟随用户设置（已唱高亮色 / 未唱色），不再固定红蓝双影
            drawTintedChars(canvas, txt, startX + glitchOffset, y, paint, highPaint.color, 120)
            drawTintedChars(canvas, txt, startX - glitchOffset, y, paint, basePaint.color, 120)
            val scanY = y - paint.textSize / 2f + (sin(phase * 5f) * paint.textSize * 0.5f)
            glitchLinePaint.set(paint)
            glitchLinePaint.color = highPaint.color
            glitchLinePaint.alpha = 80
            canvas.drawRect(0f, scanY - 1f, viewWidth.toFloat(), scanY + 1f, glitchLinePaint)
        }
    }

    /** 用指定颜色/透明度逐字画一层文字（共享位置与字宽，用于叠影类效果）。 */
    private fun drawTintedChars(
        canvas: Canvas,
        txt: String,
        startX: Float,
        y: Float,
        base: TextPaint,
        color: Int,
        alpha: Int
    ) {
        tintPaint.set(base)
        tintPaint.color = color
        tintPaint.alpha = alpha
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, tintPaint)
            x += charWidths[i]
        }
    }

    private fun drawHandwrite(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint) {
        val totalWidth = charWidths.sum()
        val cycle = (phase % 3f) / 3f
        val revealWidth = totalWidth * cycle
        canvas.save()
        canvas.clipRect(startX - 5f, y - paint.textSize, startX + revealWidth, y + paint.textSize)
        var x = startX
        for (i in txt.indices) {
            val jitterY = sin(phase * 8f + i * 1.5f) * 2f
            canvas.drawText(txt, i, i + 1, x, y + jitterY, charPaint(x, i))
            x += charWidths[i]
        }
        canvas.restore()
    }

    private fun drawWaveformBg(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int, viewHeight: Int) {
        // 旧版：12 根 6px 的细条 + 用户"未唱暗色" + alpha 40，黑底上基本看不见 → 用户报"没有效果"。
        // 现在用独立画笔取"高亮亮色"，条更粗更高、透明度足够，且不污染用户配色。
        val barCount = 14
        val barWidth = 8f
        val totalWidth = charWidths.sum()
        val barSpacing = totalWidth / barCount
        val centerY = y - paint.textSize * 0.5f
        decorPaint.set(paint)
        decorPaint.shader = null
        decorPaint.maskFilter = null
        decorPaint.color = highPaint.color
        decorPaint.alpha = 150
        for (i in 0 until barCount) {
            waveformHeights[i] = (sin(phase * 3f + i * 0.7f) * 0.5f + 0.5f) * paint.textSize * 1.3f +
                paint.textSize * 0.15f
        }
        for (i in 0 until barCount) {
            val barX = startX + i * barSpacing + barSpacing / 2f
            val h = waveformHeights[i]
            canvas.drawRect(
                barX - barWidth / 2f, centerY - h / 2f,
                barX + barWidth / 2f, centerY + h / 2f, decorPaint
            )
        }
        decorPaint.alpha = 255
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
    }

    private fun drawStars(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int, viewHeight: Int) {
        // 旧版：4px 小点、用户"未唱暗色"、每帧 life -= 0.015（0.7 秒就淡完）→ 肉眼几乎看不到。
        // 现在 6~10px、取高亮亮色、寿命放慢，并只落在岛宽范围内。
        val starCount = 14
        decorPaint.set(paint)
        decorPaint.shader = null
        decorPaint.maskFilter = null
        decorPaint.color = highPaint.color
        for (i in 0 until starCount) {
            val s = starParticles[i]
            if (s.life <= 0f) {
                val usableW = viewWidth.coerceAtLeast(40) - 24
                s.x = ((i * 47) % usableW + 12).toFloat()
                s.y = ((i * 53) % (viewHeight.coerceAtLeast(30) - 16) + 8).toFloat()
                s.life = 1f
                s.maxLife = 1f
                s.size = 0f
            }
            s.life -= 0.005f
            val grow = (1f - s.life).coerceIn(0f, 1f)
            val maxSize = 6f + (i % 3) * 2f
            s.size = if (grow < 0.5f) grow * 2f * maxSize else (1f - (grow - 0.5f) * 2f) * maxSize
            s.alpha = (255 * (1f - grow)).toInt().coerceIn(0, 255)
            if (s.size > 0.5f && s.alpha > 0) {
                decorPaint.alpha = s.alpha
                canvas.save()
                canvas.translate(s.x, s.y)
                canvas.rotate(45f)
                canvas.drawRect(-s.size / 2f, -s.size / 6f, s.size / 2f, s.size / 6f, decorPaint)
                canvas.drawRect(-s.size / 6f, -s.size / 2f, s.size / 6f, s.size / 2f, decorPaint)
                canvas.restore()
            }
        }
        decorPaint.alpha = 255
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
    }

    private fun drawRotateAppear(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint, viewWidth: Int) {
        val cycle = (phase % 4f) / 4f
        val totalWidth = charWidths.sum()
        val centerX = startX + totalWidth / 2f
        val rotation: Float
        val scale: Float
        val alpha: Int
        when {
            cycle < 0.3f -> {
                val p = cycle / 0.3f
                rotation = 180f * (1f - p)
                scale = 0.5f + 0.5f * p
                alpha = (255 * p).toInt()
            }
            cycle < 0.7f -> {
                rotation = 0f
                scale = 1f
                alpha = 255
            }
            else -> {
                val p = (cycle - 0.7f) / 0.3f
                rotation = -180f * p
                scale = 1f - 0.5f * p
                alpha = (255 * (1f - p)).toInt()
            }
        }
        effectAlpha(alpha.coerceIn(0, 255))
        canvas.save()
        canvas.rotate(rotation, centerX, y)
        canvas.scale(scale, scale, centerX, y)
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
        canvas.restore()
        effectAlpha(255)
    }

    private fun drawIndiaCamera(canvas: Canvas, txt: String, startX: Float, y: Float, paint: TextPaint) {
        val totalWidth = charWidths.sum()
        val centerX = startX + totalWidth / 2f
        val cycle = phase % 1f
        canvas.save()
        val rotation = cycle * 720f
        canvas.rotate(rotation, centerX, y)
        val scale = 0.5f + sin(cycle * Math.PI).toFloat() * 1.0f
        canvas.scale(scale, scale, centerX, y)
        val transX = sin(cycle * 3 * Math.PI).toFloat() * 20f
        val transY = cos(cycle * 2 * Math.PI).toFloat() * 15f
        canvas.translate(transX, transY)
        var x = startX
        for (i in txt.indices) {
            canvas.drawText(txt, i, i + 1, x, y, charPaint(x, i))
            x += charWidths[i]
        }
        canvas.restore()
    }
}
