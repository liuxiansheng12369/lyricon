/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.lyric.render.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.TextPaint
import android.view.Choreographer
import android.view.View
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.ILyricLine
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import com.liuxiansheng.lyricon.lyric.render.CountdownPainter
import com.liuxiansheng.lyricon.lyric.render.LineRenderState
import com.liuxiansheng.lyricon.lyric.render.LineRenderer
import com.liuxiansheng.lyricon.lyric.render.LineTimelinePainter
import com.liuxiansheng.lyricon.lyric.render.LyricEffectRenderer
import com.liuxiansheng.lyricon.lyric.render.MarqueePainter
import com.liuxiansheng.lyricon.lyric.render.ProgressivePainter
import com.liuxiansheng.lyricon.lyric.render.model.LyricRenderModel
import com.liuxiansheng.lyricon.lyric.render.model.WordRenderModel

/**
 * 单行歌词渲染视图（V3 自研）。
 *
 * 渲染流程：模型构建 → 渲染器选择 → 渲染器布局 → 帧驱动推进 → 绘制。
 * 帧驱动采用双通道：主帧（逐字/时间轴/效果动画）与跑马灯帧（纯滚动），
 * 两者独立挂帧，避免歌名行（非播放态）跑马灯不启动。
 */
internal class LyricLineView(context: Context) : View(context) {

    // —— 渲染器注册表 ——
    private val progressive = ProgressivePainter()
    private val timeline = LineTimelinePainter()
    private val marquee = MarqueePainter()
    private val countdownPainter = CountdownPainter()
    private var activeRenderer: LineRenderer = marquee

    // —— 歌词动画效果（26 种，0 = 静态） ——
    private val effectRenderer = LyricEffectRenderer()
    // 效果模式用的调色板（直接取 LyricViewStyle.Highlight：彩虹=多色逐字轮换、
    // 固定色=单色，完全跟随用户设置，不再自己造配色）
    private var effectBasePalette: IntArray = IntArray(0)
    private var effectHighPalette: IntArray = IntArray(0)
    /** 兼容保留：效果单色（仅在调色板为空时兜底） */
    var effectSolidColor: Int = 0xFFFFFFFF.toInt()
    var lyricEffectMode = LyricEffectRenderer.EFFECT_STATIC
        set(value) {
            if (field != value) {
                field = value
                if (value == LyricEffectRenderer.EFFECT_STATIC) effectRenderer.reset()
                invalidate()
            }
        }

    /**
     * 1.0.9：整句连续配色偏移。
     * 超级岛把一句歌词拆成左右两个视图渲染，若各自从色序第 1 个色开始，
     * 观感就是"两套彩虹"。右段用左段字数做偏移，色序就能从左贯到右。
     */
    var paletteOffset: Int = 0
        set(value) {
            if (field == value) return
            field = value
            com.liuxiansheng.lyricon.hyper.root.utils.HookLogger.i(
                "IslandColorOffset",
                "LyricLineView.paletteOffset=$value renderer=${activeRenderer.javaClass.simpleName}"
            )
            progressive.setPaletteOffset(value)
            timeline.setPaletteOffset(value)
            marquee.setPaletteOffset(value)
            countdownPainter.setPaletteOffset(value)
            effectRenderer.paletteOffset = value
            invalidate()
        }

    /** 效果调色板兜底：样式没给调色板时用单色（未唱 70% 透明 / 已唱纯色） */
    private fun applyEffectPalette() {
        val solid = effectSolidColor
        val dim = (0xB3 shl 24) or (solid and 0x00FFFFFF)
        effectBasePalette = intArrayOf(dim)
        effectHighPalette = intArrayOf(solid)
    }

    /** 效果模式是否激活：效果开启 且 当前有歌词文本 */
    private val effectActive: Boolean
        get() = lyricEffectMode != LyricEffectRenderer.EFFECT_STATIC &&
            !model.text.isNullOrEmpty()

    // —— 模型与状态 ——
    var model: LyricRenderModel = LyricRenderModel.empty()
        private set
    private val renderState = LineRenderState()

    // —— 播放状态 ——
    private var playbackActive = false
    private var lastPosition = Long.MIN_VALUE
    private var playbackSpeed = 1f

    // —— 歌词镜像倒影 ——
    var mirrorEnabled = false
        set(value) {
            if (field != value) {
                field = value
                // 倒影带会影响测量高度，必须重新布局，否则视图不会变高
                requestLayout()
                invalidate()
            }
        }

    // —— 主帧驱动（逐字/时间轴/效果） ——
    private var frameScheduled = false
    private var lastFrameNanos = 0L

    /** invalidate 节流基准（3.0.862）：逐字动效开 30fps / 关 20fps，见 stepFrame。 */
    private var lastInvalidateMs = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameScheduled = false
            if (!isAttachedToWindow) return
            stepFrame(frameTimeNanos)
        }
    }

    // —— 行为开关 ——
    var isSustainProgressEnabled: Boolean
        get() = progressive.sustainAwareProgress
        set(value) {
            progressive.sustainAwareProgress = value
        }
    var isScrollOnly: Boolean
        get() = progressive.marqueeOnly
        set(value) {
            progressive.marqueeOnly = value
        }

    /** 右对齐（分离模式右侧容器用） */
    var rightIfPossible: Boolean
        get() = progressive.rightIfPossible
        set(value) {
            progressive.rightIfPossible = value
            timeline.rightIfPossible = value
            marquee.rightIfPossible = value
            countdownPainter.rightIfPossible = value
        }

    /** 居中（无挖孔时） */
    var centerIfPossible: Boolean
        get() = progressive.centerIfPossible
        set(value) {
            progressive.centerIfPossible = value
            timeline.centerIfPossible = value
            marquee.centerIfPossible = value
            countdownPainter.centerIfPossible = value
        }

    var visibleIfChanged = true
    var isStaticPreview = false
    var playListener: LyricPlayListener? = null

    /** 当前文本画笔（挂载点测量用） */
    val textPaint: TextPaint
        get() = when (activeRenderer) {
            is ProgressivePainter -> progressive.dimPaint
            is LineTimelinePainter -> timeline.dimPaint
            is MarqueePainter -> marquee.plainPaint
            else -> countdownPainter.plainPaint
        }

    /** 当前行渲染宽度（动态宽度决策用） */
    val lineWidth: Float
        get() = model.width

    /** 对侧行宽（分离模式协调用；当前仅记录） */
    var peerLineWidth: Float = 0f
        private set

    /** 分离模式开关（SpaceGate 容器使用；渲染行为由 isRightSide 控制） */
    var spaceGateEnabled = false

    fun setPeerLineWidth(width: Float) {
        peerLineWidth = width
    }

    // —— 行设置 ——

    fun setLyric(line: ILyricLine?, lineTimeline: Boolean) {
        setLyricInternal(line, lineTimeline, preserveScroll = false)
    }

    fun setLyricPreservingScroll(line: ILyricLine?, lineTimeline: Boolean) {
        setLyricInternal(line, lineTimeline, preserveScroll = true)
    }

    private fun setLyricInternal(
        line: ILyricLine?,
        lineTimeline: Boolean,
        preserveScroll: Boolean
    ) {
        if (line == null) {
            model = LyricRenderModel.empty()
            countdownMeasureContent = false
            activeRenderer.reset(renderState)
            lastPosition = Long.MIN_VALUE
            invalidate()
            return
        }

        val isCountdown = line.metadata?.getBoolean("CountdownLine", false) == true
        countdownMeasureContent = isCountdown
        val isTitle = line.metadata?.getBoolean("TitleLine", false) == true
        val newModel = LyricRenderModel.build(
            paint = textPaint,
            line = line,
            lineTimeline = lineTimeline,
            isTitleLine = isTitle,
            isCountdownLine = isCountdown
        )
        // 内容未变（同文本同宽度）直接跳过：避免逐帧重复刷新。
        // 倒计时行例外：文本为空可能与空行相同，必须应用（否则倒计时占位永不显示）。
        if (visibleIfChanged && !preserveScroll && !isCountdown) {
            if (model.fullText == newModel.fullText && model.width == newModel.width) {
                return
            }
        }
        model = newModel
        activeRenderer = when {
            isCountdown -> countdownPainter
            lineTimeline -> timeline
            model.words.isNotEmpty() -> progressive
            else -> marquee
        }
        // 3.0.621：内容变化后重新测量——否则 wrap_content 高度在首次测量后固定，
        // 副行（中文翻译）会被主行挤成 0 高、画出来看不见
        requestLayout()
        // 换行必须重置动画状态：否则上一句残留的宽度/锚定/词指针还在，
        // 新句高亮会向更小目标插值 = 回退（"下一句不从第一个字开始"的根因）
        activeRenderer.reset(renderState)
        HookLogger.i(
            "LyricLineView", "渲染器选择: ${activeRenderer.javaClass.simpleName} " +
                "words=${model.words.size} lineTimeline=$lineTimeline " +
                "countdown=$isCountdown text='${model.fullText?.take(30)}' " +
                "begin=${model.begin} end=${model.end} dur=${model.duration}"
        )
        activeRenderer.updateLayout(model, renderState, width, height)
        if (!preserveScroll) {
            lastPosition = Long.MIN_VALUE
        }
        invalidate()
        ensureFrameScheduled()
    }

    // —— 样式配置 ——

    fun configureWith(
        text: TextLook,
        highlight: Highlight,
        gradient: Boolean,
        centerIfPossible: Boolean,
        rightIfPossible: Boolean,
        wordMotion: WordMotion? = null
    ) {
        val typeface = text.typeface
        // 效果模式直接用样式调色板（彩虹多色逐字轮换 / 固定色单色），与普通渲染同源
        effectBasePalette = highlight.background
        effectHighPalette = highlight.foreground
        progressive.applyFontSettings(text.size, typeface, null)
        timeline.applyFontSettings(text.size, typeface, null)
        marquee.applyFontSettings(text.size, typeface, null)
        countdownPainter.applyFontSettings(text.size, typeface, null)

        // 可读性软阴影：无背景承载时（状态栏无黑底）让字在任何背景下弹出；
        // radius=0 时 clearShadowLayer（超级岛有背景，默认不开，不受影响）
        val shadowOn = text.shadowRadius > 0f
        listOf(
            progressive.dimPaint, progressive.litPaint,
            timeline.dimPaint, timeline.litPaint,
            marquee.plainPaint, countdownPainter.plainPaint
        ).forEach { p ->
            if (shadowOn) {
                p.setShadowLayer(text.shadowRadius, text.shadowDx, text.shadowDy, text.shadowColor)
            } else {
                p.clearShadowLayer()
            }
        }

        progressive.setColors(highlight.background, highlight.foreground)
        timeline.setColors(highlight.background, highlight.foreground)
        marquee.setColor(highlight.foreground.firstOrNull() ?: 0xFFFFFFFF.toInt())
        countdownPainter.plainPaint.color = highlight.foreground.firstOrNull() ?: 0xFFFFFFFF.toInt()

        progressive.fadeIn = gradient
        progressive.centerIfPossible = centerIfPossible
        progressive.rightIfPossible = rightIfPossible
        timeline.centerIfPossible = centerIfPossible
        timeline.rightIfPossible = rightIfPossible
        marquee.centerIfPossible = centerIfPossible
        marquee.rightIfPossible = rightIfPossible

        if (wordMotion != null) {
            progressive.waveOn = wordMotion.enabled
        }
    }

    // —— 播放驱动 ——

    fun updatePosition(posMs: Long, playbackSpeed: Float) {
        this.playbackSpeed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) playbackSpeed else 1f
        if (posMs == lastPosition && !activeRenderer.isAnimating) return
        lastPosition = posMs
        activeRenderer.update(model, renderState, posMs, width, height, this.playbackSpeed)
        invalidate()
        ensureFrameScheduled()
    }

    fun seekTo(posMs: Long) {
        lastPosition = posMs
        activeRenderer.seek(model, renderState, posMs, width, height)
        invalidate()
    }

    fun setPlaybackActive(active: Boolean) {
        if (playbackActive == active) return
        playbackActive = active
        if (active) {
            lastFrameNanos = 0L
            ensureFrameScheduled()
        } else {
            removeFrameCallback()
            if (model.words.isNotEmpty() || model.lineTimeline) {
                activeRenderer.freeze(model, renderState, width)
            }
            invalidate()
        }
    }

    // —— 动态换色（彩虹 / 固定色切换时） ——

    fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        progressive.setColors(background, highlight)
        timeline.setColors(background, highlight)
        marquee.setColor(highlight.firstOrNull() ?: 0xFFFFFFFF.toInt())
        countdownPainter.plainPaint.color = highlight.firstOrNull() ?: 0xFFFFFFFF.toInt()
        invalidate()
    }

    /** 测量某行渲染宽度（动态宽度预检用） */
    fun measureLineWidth(line: ILyricLine?): Float {
        if (line == null) return 0f
        return LyricRenderModel.build(textPaint, line, false, false, false).width
    }

    /** 当前文字起点 X（用于行切换动画：副行移动到主行位置时对齐文字起点） */
    fun currentTextStartX(): Float = resolveTextStartX(model?.width ?: 0f, model?.isAlignedRight == true)

    /** 指定文字的起点 X（用于行切换动画：计算主行文字起点位置） */
    fun textStartX(text: String?, isAlignedRight: Boolean): Float =
        resolveTextStartX(textPaint.measureText(text.orEmpty()), isAlignedRight)

    private fun resolveTextStartX(textWidth: Float, isAlignedRight: Boolean): Float {
        val availableWidth = width.toFloat()
        return when {
            textWidth >= availableWidth -> 0f
            isAlignedRight -> availableWidth - textWidth
            else -> 0f
        }
    }

    val textSize: Float get() = textPaint.textSize

    // —— 主帧驱动 ——

    private fun stepFrame(frameTimeNanos: Long) {
        val delta = if (lastFrameNanos == 0L) 0L else frameTimeNanos - lastFrameNanos
        lastFrameNanos = frameTimeNanos
        val changed = if (effectActive) {
            effectRenderer.step(delta, lyricEffectMode)
            // 效果模式也推进逐字高亮（供动画字选高亮色）
            activeRenderer.step(delta, model, renderState, width)
            true
        } else {
            activeRenderer.step(delta, model, renderState, width)
        }
        // 节流（3.0.862）：动画进度每帧都算（保证不跳），invalidate 动态帧率——
        // 逐字动效（往上跳）开启时 30fps 保证跳动中间帧连贯（20fps 会把快速位移
        // 的中间帧丢掉导致"跳没用了"）；无逐字动效时 20fps 省电。
        val motionActive = (activeRenderer as? ProgressivePainter)?.waveOn == true
        val effectiveInterval = if (motionActive) 33L else 50L
        val nowMs = System.currentTimeMillis()
        if (changed && nowMs - lastInvalidateMs >= effectiveInterval) {
            invalidate()
            lastInvalidateMs = nowMs
        }
        // 只要动画在跑就续帧（不依赖 playbackActive：歌名行 playbackActive=false
        // 也靠帧驱动平滑推进，否则只有低频 updatePosition 跳变）
        if (isAttachedToWindow && (activeRenderer.isAnimating || effectActive)) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
            frameScheduled = true
        } else {
            // 动画停了，最后补一次 invalidate 确保最终状态画出来
            if (changed) invalidate()
            lastFrameNanos = 0L
            lastInvalidateMs = 0L
        }
    }

    private fun ensureFrameScheduled() {
        if (!isAttachedToWindow) return
        if (!activeRenderer.isAnimating && !effectActive) return
        if (frameScheduled) return
        frameScheduled = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun removeFrameCallback() {
        if (frameScheduled) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            frameScheduled = false
        }
    }

    /** 倒计时行：文本为空但绘制倒计时数字，必须保留高度 */
    private var countdownMeasureContent = false

    // —— 绘制 ——

    /** 文本内容高度（按当前渲染器画笔的字高计算，双行布局用）。
     *  空内容（无文本无词且非倒计时）返回 0——没有翻译的歌副行不占高度，主行不被抬高。 */
    private fun measureTextHeight(): Int {
        val hasContent = !model.fullText.isNullOrEmpty() ||
            model.words.isNotEmpty() || countdownMeasureContent
        if (!hasContent) return 0
        val fm = textPaint.fontMetrics
        // 开倒影时给视图多留出"倒影带"：文字仍在视图正中（岛里位置不变），
        // 视图往下多出来的这一段只给倒影用，倒影一路铺到岛的黑底边。
        val mirrorBand = if (mirrorEnabled) (fm.descent - fm.ascent) else 0f
        return (fm.descent - fm.ascent + 4 + mirrorBand).toInt()
    }

    /** 文本内容宽度（当前行文本实际宽度；无内容为 0） */
    private fun measureTextWidth(): Int {
        val text = model.fullText
        if (text.isNullOrEmpty()) return 0
        return textPaint.measureText(text).toInt()
    }

    /** 岛伸缩/宽度变化时重算滚动偏移（唱完行防抖）：
     *  唱着时帧驱动每帧重绘、滚动跟随岛宽；唱完（全高亮）后 isAnimating=false
     *  帧驱动停止，scrollOffset 冻结在旧值——岛来回伸缩时 viewWidth 变了歌词
     *  还停在旧位置，被裁切/错位 = "唱完高亮行水平抖动"。宽度一变立即
     *  updateLayout 重算滚动偏移并重绘，让超宽行平滑跟随岛宽。 */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == oldw) return
        if (model.width <= 0f) return
        activeRenderer.updateLayout(model, renderState, w, h)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val contentW = measureTextWidth()
        val contentH = measureTextHeight()
        val w = when (wMode) {
            MeasureSpec.EXACTLY -> wSize
            MeasureSpec.AT_MOST -> minOf(contentW, wSize)
            else -> contentW
        }
        val h = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> minOf(contentH, hSize)
            else -> contentH
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (model.width <= 0f) return
        // 文字始终按视图整高居中：视图多出来的倒影带在下方，歌词不会被抬上去
        val textHeight = height
        // 效果模式：整行文本走动画渲染（保留逐字高亮——已唱区用高亮色），镜像同套用
        if (effectActive) {
            val txt = model.text ?: return
            val hlWidth = progressive.currentWidth()
            // 超长歌词：效果模式也要跟着跑马灯滚动，否则开动画后长歌词就不滚了
            val effectScroll = if (model.width > width) renderState.scrollOffset else 0f
            val ghostGap = marquee.ghostGapPx
            val drawEffect: (Canvas) -> Unit = { c ->
                val saves = c.save()
                c.translate(effectScroll, 0f)
                effectRenderer.draw(
                    c, txt, progressive.dimPaint, progressive.litPaint,
                    width, textHeight, lyricEffectMode, hlWidth,
                    effectBasePalette, effectHighPalette
                )
                // 无缝循环：本体滚出去后，把下一圈的幽灵副本接在后面
                if (effectScroll != 0f && model.width + ghostGap < width * 2f) {
                    if (model.width + effectScroll < width.toFloat()) {
                        c.translate(model.width + ghostGap, 0f)
                        effectRenderer.draw(
                            c, txt, progressive.dimPaint, progressive.litPaint,
                            width, textHeight, lyricEffectMode, hlWidth,
                            effectBasePalette, effectHighPalette
                        )
                    }
                }
                c.restoreToCount(saves)
            }
            drawEffect(canvas)
            if (mirrorEnabled) {
                drawMirrored(canvas, textHeight) { drawEffect(canvas) }
            }
            return
        }
        activeRenderer.draw(canvas, model, renderState, width, textHeight)
        if (mirrorEnabled) {
            drawMirrored(canvas, textHeight) {
                activeRenderer.draw(canvas, model, renderState, width, textHeight)
            }
        }
    }

    /** 文字区域高度（视图整高：倒影带加在下方，文字仍居中） */
    private fun textAreaHeight(): Int = height

    /**
     * 当前视图在"祖先可见区域"里能画到的最深 y（视图坐标）。
     * 岛内容高度是系统限死的，视图自己的 height 只够一行字，倒影必须按
     * 祖先真正露出来的高度压缩，否则会被岛底边裁掉。
     */
    private fun visibleBottomInViewSpace(): Float {
        var bottom = height.toFloat()
        var v: View = this
        while (true) {
            val p = v.parent as? View ?: break
            if (p.height <= 0) break
            val topInParent = v.top - p.scrollY
            val space = (p.height - topInParent).toFloat()
            if (space > 0f) bottom = minOf(bottom, space)
            v = p
        }
        return bottom
    }

    /**
     * 镜像倒影绘制：以文字基线为轴翻折，倒影一路铺到可见底边（岛的黑底边），
     * 按剩余空间压缩，保证既贴到底边又不被裁掉。
     */
    private fun drawMirrored(canvas: Canvas, textHeight: Int, drawContent: () -> Unit) {
        val paint = progressive.dimPaint
        val textY = textHeight / 2f - (paint.descent() + paint.ascent()) / 2f
        val gap = paint.textSize * 0.02f
        val glyphHeight = (paint.descent() - paint.ascent()).coerceAtLeast(1f)
        val visibleBottom = minOf(height.toFloat(), visibleBottomInViewSpace())
        val spaceBelow = (visibleBottom - textY - gap).coerceAtLeast(1f)
        val squash = (spaceBelow / glyphHeight).coerceIn(0.2f, 0.8f)
        val saveCount = canvas.saveLayerAlpha(
            0f, 0f, width.toFloat(), visibleBottom, 80
        )
        try {
            canvas.clipRect(0f, textY, width.toFloat(), visibleBottom)
            // 以 (0, textY + gap) 为支点，纵向翻转并压缩
            canvas.scale(1f, -squash, 0f, textY + gap)
            drawContent()
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    // —— 生命周期 ——

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureFrameScheduled()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeFrameCallback()
        activeRenderer.reset(renderState)
        lastPosition = Long.MIN_VALUE
        model = LyricRenderModel.empty()
    }

    fun reset() {
        removeFrameCallback()
        activeRenderer.reset(renderState)
        lastPosition = Long.MIN_VALUE
        playbackSpeed = 1f
        model = LyricRenderModel.empty()
        invalidate()
    }

    /** 播放进度通知（容器层统一派发） */
    fun notifyProgressIfChanged() {
        val listener = playListener ?: return
        val renderer = activeRenderer
        if (renderer.isStarted) listener.onPlayStarted(this)
        if (renderer.isFinished) listener.onPlayEnded(this)
        listener.onPlayProgress(this, model.width, progressOf())
    }

    private fun progressOf(): Float {
        return when (activeRenderer) {
            is ProgressivePainter -> progressive.currentWidth()
            is LineTimelinePainter -> timeline.currentWidth()
            else -> 0f
        }
    }
}

/** 歌词行播放进度监听 */
internal interface LyricPlayListener {
    fun onPlayStarted(view: LyricLineView)
    fun onPlayEnded(view: LyricLineView)
    fun onPlayProgress(view: LyricLineView, total: Float, progress: Float)
}
