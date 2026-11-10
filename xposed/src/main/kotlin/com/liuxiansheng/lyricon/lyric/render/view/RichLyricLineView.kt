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
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.graphics.withScale
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.IRichLyricLine
import com.liuxiansheng.lyricon.lyric.render.model.LyricRenderModel

internal interface UpdatableColor {
    fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray)
}

/**
 * 双行歌词视图（V3 自研）。
 *
 * 布局：垂直 LinearLayout，上方主行（歌词原文）、下方副行（翻译 / 罗马音 / 预览）。
 * 行切换流程：组装主副行 → 候选宽度预检（挂载点据此决定是否加宽岛）→ 同步应用。
 */
internal open class RichLyricLineView(
    context: Context,
    var displayTranslation: Boolean = true,
    var enableRelativeProgress: Boolean = false,
    var enableRelativeProgressHighlight: Boolean = false,
    var displayRoma: Boolean = true
) : LinearLayout(context), UpdatableColor {

    val main = LyricLineView(context)
    val secondary = LyricLineView(context).apply { visibleIfChanged = false }

    var renderScale = 1.0f
        private set

    /** 用户在设置里要求的倒影开关（真实是否画还要看副行有没有内容） */
    private var mirrorDesired = false

    /** 副行有内容（翻译 / 罗马音 / 下一句预览）时倒影一律不画，
     *  否则倒影会和副行叠在一起；副行没了（正常歌词行）自动恢复。 */
    private fun updateMirrorState() {
        val hasSecondary = !secondary.model.text.isNullOrEmpty()
        main.mirrorEnabled = mirrorDesired && !hasSecondary
    }

    /** 行切换动画效果：0=无动画（原始切换）/ 1=副行放大（默认）；
     *  由外部（样式装配/状态栏）按用户配置设置，与设置页下拉选择索引对应。 */
    var lineSwitchAnimation: Int = 1

    private val assembler = LyricLineAssembler(
        displayTranslation, displayRoma,
        enableRelativeProgress, enableRelativeProgressHighlight
    )
    private var displayLineByLine = false

    private var rawLine: IRichLyricLine? = null
    private var currentMainText: String? = null

    private var pendingWillApply: ((Float) -> Boolean)? = null
    private var pendingApplied: (() -> Unit)? = null
    private var pendingCancelled: (() -> Unit)? = null
    private var preflightDeferred = false

    private var lastPosition = Long.MIN_VALUE
    private var lastPlaybackSpeed = 1f

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        addView(main, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(secondary, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    var line: IRichLyricLine?
        get() = rawLine
        set(value) {
            setLineInternal(value)
        }

    fun setLineWithCallbacks(
        value: IRichLyricLine?,
        onMainLineWillApply: ((Float) -> Boolean)? = null,
        onMainLineApplied: (() -> Unit)? = null,
        onMainLineCancelled: (() -> Unit)? = null
    ) {
        pendingWillApply = onMainLineWillApply
        pendingApplied = onMainLineApplied
        pendingCancelled = onMainLineCancelled
        setLineInternal(value)
    }

    private fun setLineInternal(value: IRichLyricLine?) {
        rawLine = value
        refreshLines()
    }

    /** 元数据行（歌名等）更新：保留当前显示状态 */
    fun updateMetadataLine(value: IRichLyricLine?) {
        rawLine = value
        refreshLines(preserveDisplay = true)
    }

    // —— 行切换 ——

    private fun refreshLines(preserveDisplay: Boolean = false) {
        val mainResult = assembler.buildMain(rawLine)
        val secResult = assembler.buildSecondary(rawLine)

        // 预检：候选宽度（主/副行最大），供挂载点决定是否加宽岛
        val candidateWidth = maxOf(
            main.measureLineWidth(mainResult.line),
            secondary.measureLineWidth(secResult.line)
        )
        // 挂载点要求延迟（等岛宽度先变化）：挂一帧后重试，避免宽度未就绪时误排版
        if (pendingWillApply?.invoke(candidateWidth) == true && !preflightDeferred) {
            preflightDeferred = true
            post { refreshLines(preserveDisplay) }
            return
        }
        preflightDeferred = false
        pendingWillApply = null

        // 3.0.624：行切换动画——主/副行内容真正变化（非跑马灯保留、非样式刷新）才触发
        val lineSwitched = !preserveDisplay && mainResult.line.text != currentMainText
        applyMain(mainResult, preserveDisplay)
        applySecondary(secResult, preserveDisplay)
        // 有副行（翻译/罗马音/下一句预览）就不画倒影，副行消失后自动恢复
        updateMirrorState()
        if (lineSwitched) {
            // 等一帧布局：副行高度/主行宽度就绪后再算起点
            post { animateLineSwitch() }
        }

        pendingApplied?.invoke()
        pendingApplied = null
        pendingCancelled = null
    }

    /** 行切换动画（移植自 HyperLyric animateNextLinePromotion，3.0.1107 扩展为多档效果）：
     *  0=无动画；1=副行放大（默认）；2=淡入淡出；3=上滑；4=下滑；5=缩放淡入；6=3D 翻转。
     *  动画结束后统一重置位置/缩放/透明度/旋转。 */
    private fun animateLineSwitch() {
        // 用户选择"无动画（原始切换）"时直接跳过
        if (lineSwitchAnimation == 0) return
        if (main.width <= 0) return

        val targetTranslationY = (main.top - secondary.top).toFloat()
        val secondaryTextStartX = secondary.currentTextStartX()
        val targetMainTextStartX = main.textStartX(currentMainText, false)
        val targetTranslationX = (main.left - secondary.left).toFloat() +
                targetMainTextStartX - secondaryTextStartX
        val targetScale = (main.textSize / secondary.textSize).coerceIn(0.5f, 2f)

        main.animate().cancel()
        secondary.animate().cancel()

        fun resetAll() {
            main.alpha = 1f
            main.translationX = 0f
            main.translationY = 0f
            main.scaleX = 1f
            main.scaleY = 1f
            main.rotationY = 0f
            main.rotationX = 0f
            main.pivotX = main.width / 2f
            main.pivotY = main.height / 2f
            secondary.alpha = 1f
            secondary.translationX = 0f
            secondary.translationY = 0f
            secondary.scaleX = 1f
            secondary.scaleY = 1f
            secondary.rotationY = 0f
            secondary.rotationX = 0f
            secondary.pivotX = secondary.width / 2f
            secondary.pivotY = secondary.height / 2f
        }

        // 每次切换前先复位：上一次动画若被中断，旋转/缩放/透明度会残留，
        // 竖向翻转（rotationX 归零漏了）就会让整行一直侧对镜头 → 歌词看着丢了
        resetAll()

        val duration = 320L
        when (lineSwitchAnimation) {
            // 副行放大：主行淡出上移，副行移动到主行位置并放大
            1 -> {
                // 主行（当前歌词）：淡出向上移
                main.animate()
                    .alpha(0f)
                    .translationY(-main.height * 0.65f)
                    .setDuration(340)
                    .withLayer()
                    .start()

                // 副行（下一行预览）：移动到主行位置并放大
                // 左侧容器 rightIfPossible=true → 朝左放大（pivotX = width，以右边为锚点）
                // 右侧容器 rightIfPossible=false → 朝右放大（pivotX = 0，以左边为锚点）
                secondary.pivotX = if (main.rightIfPossible) secondary.width.toFloat() else 0f
                secondary.pivotY = 0f
                secondary.animate()
                    .translationX(targetTranslationX)
                    .translationY(targetTranslationY)
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(340)
                    .withLayer()
                    .withEndAction { resetAll() }
                    .start()
            }
            // 淡入淡出：两位都不动，主行淡出、副行淡入
            2 -> {
                main.animate().alpha(0f).setDuration(duration).withLayer().start()
                secondary.alpha = 0f
                secondary.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { resetAll() }
                    .start()
            }
            // 上滑：主行上移淡出，副行自下而上滑入
            3 -> {
                main.animate()
                    .alpha(0f)
                    .translationY(-main.height * 0.6f)
                    .setDuration(duration)
                    .withLayer()
                    .start()
                secondary.alpha = 0f
                secondary.translationY = secondary.height * 0.6f
                secondary.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { resetAll() }
                    .start()
            }
            // 下滑：主行下移淡出，副行自上而下滑入
            4 -> {
                main.animate()
                    .alpha(0f)
                    .translationY(main.height * 0.6f)
                    .setDuration(duration)
                    .withLayer()
                    .start()
                secondary.alpha = 0f
                secondary.translationY = -secondary.height * 0.6f
                secondary.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { resetAll() }
                    .start()
            }
            // 缩放淡入：主行缩小淡出，副行放大淡入
            5 -> {
                main.pivotX = main.width / 2f
                main.pivotY = main.height / 2f
                main.animate()
                    .alpha(0f)
                    .scaleX(0.86f)
                    .scaleY(0.86f)
                    .setDuration(duration)
                    .withLayer()
                    .start()
                secondary.pivotX = secondary.width / 2f
                secondary.pivotY = secondary.height / 2f
                secondary.alpha = 0f
                secondary.scaleX = 1.12f
                secondary.scaleY = 1.12f
                secondary.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { resetAll() }
                    .start()
            }
            // 3D 翻转：主行沿纵轴翻出，副行翻入
            6 -> {
                main.cameraDistance = 8000f * resources.displayMetrics.density
                secondary.cameraDistance = 8000f * resources.displayMetrics.density
                main.pivotX = main.width / 2f
                main.pivotY = main.height / 2f
                main.animate()
                    .alpha(0f)
                    .rotationY(-90f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { main.rotationY = 0f }
                    .start()
                secondary.pivotX = secondary.width / 2f
                secondary.pivotY = secondary.height / 2f
                secondary.alpha = 0f
                secondary.rotationY = 90f
                secondary.animate()
                    .alpha(1f)
                    .rotationY(0f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { resetAll() }
                    .start()
            }
            // 左滑：主行向左滑出，副行从右侧滑入
            7 -> {
                main.animate()
                    .alpha(0f)
                    .translationX(-main.width * 0.45f)
                    .setDuration(duration)
                    .withLayer()
                    .start()
                secondary.alpha = 0f
                secondary.translationX = secondary.width * 0.45f
                secondary.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { resetAll() }
                    .start()
            }
            // 右滑：主行向右滑出，副行从左侧滑入
            8 -> {
                main.animate()
                    .alpha(0f)
                    .translationX(main.width * 0.45f)
                    .setDuration(duration)
                    .withLayer()
                    .start()
                secondary.alpha = 0f
                secondary.translationX = -secondary.width * 0.45f
                secondary.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { resetAll() }
                    .start()
            }
            // 弹性放大：副行从 0.7 带过冲弹到 1，主行同步缩淡
            9 -> {
                main.pivotX = main.width / 2f
                main.pivotY = main.height / 2f
                main.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(duration)
                    .withLayer()
                    .start()
                secondary.pivotX = secondary.width / 2f
                secondary.pivotY = secondary.height / 2f
                secondary.alpha = 0f
                secondary.scaleX = 0.7f
                secondary.scaleY = 0.7f
                secondary.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { resetAll() }
                    .start()
            }
            // 竖向翻转：沿横轴（rotationX）上下翻出翻入
            10 -> {
                main.cameraDistance = 8000f * resources.displayMetrics.density
                secondary.cameraDistance = 8000f * resources.displayMetrics.density
                main.pivotX = main.width / 2f
                main.pivotY = main.height / 2f
                main.animate()
                    .alpha(0f)
                    .rotationX(-90f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { main.rotationX = 0f }
                    .start()
                secondary.pivotX = secondary.width / 2f
                secondary.pivotY = secondary.height / 2f
                secondary.alpha = 0f
                secondary.rotationX = 90f
                secondary.animate()
                    .alpha(1f)
                    .rotationX(0f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction { resetAll() }
                    .start()
            }
            // 模糊淡入：主行糊掉淡出，副行由糊变清晰（Android 12+ 用 RenderEffect，低版本退化为淡入淡出）
            11 -> {
                val blurAnimator: android.animation.ValueAnimator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
                    val maxPx = 22f * resources.displayMetrics.density
                    android.animation.ValueAnimator.ofFloat(0f, maxPx).apply {
                        setDuration(320L)
                        addUpdateListener { anim ->
                            val radius = anim.animatedValue as Float
                            main.setRenderEffect(
                                android.graphics.RenderEffect.createBlurEffect(
                                    radius, radius, android.graphics.Shader.TileMode.CLAMP
                                )
                            )
                            secondary.setRenderEffect(
                                android.graphics.RenderEffect.createBlurEffect(
                                    radius * 0.65f, radius * 0.65f, android.graphics.Shader.TileMode.CLAMP
                                )
                            )
                        }
                        start()
                    }
                } else null

                main.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .withLayer()
                    .start()
                secondary.alpha = 0f
                secondary.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .withLayer()
                    .withEndAction {
                        blurAnimator?.cancel()
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            main.setRenderEffect(null)
                            secondary.setRenderEffect(null)
                        }
                        resetAll()
                    }
                    .start()
            }
            else -> Unit
        }
    }

    private fun applyMain(result: LyricLineAssembler.MainResult, preserveDisplay: Boolean) {
        main.isSustainProgressEnabled = result.sustainAwareProgress
        main.isScrollOnly = result.isScrollOnly
        if (preserveDisplay) {
            main.setLyricPreservingScroll(result.line, result.isLineTimeline)
        } else {
            main.setLyric(result.line, result.isLineTimeline)
        }
        currentMainText = result.line.text
    }

    private fun applySecondary(result: LyricLineAssembler.SecondaryResult, preserveDisplay: Boolean) {
        secondary.isSustainProgressEnabled = result.sustainAwareProgress
        secondary.isScrollOnly = result.isScrollOnly
        secondary.visibleIfChanged = result.alwaysShow
        secondary.isStaticPreview = result.isNextLinePreview
        if (preserveDisplay) {
            secondary.setLyricPreservingScroll(result.line, result.isLineTimeline)
        } else {
            secondary.setLyric(result.line, result.isLineTimeline)
        }
    }

    // —— 播放驱动 ——

    fun setPosition(position: Long, playbackSpeed: Float = 1f) {
        val speed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) playbackSpeed else 1f
        if (lastPosition == position && lastPlaybackSpeed == speed) return
        lastPosition = position
        lastPlaybackSpeed = speed
        main.updatePosition(position, speed)
        secondary.updatePosition(position, speed)
    }

    fun seekTo(position: Long) {
        lastPosition = position
        main.seekTo(position)
        secondary.seekTo(position)
    }

    fun setPlaybackActive(active: Boolean) {
        main.setPlaybackActive(active)
        secondary.setPlaybackActive(active)
    }

    /**
     * 1.0.9：整句连续配色偏移（主行/副行一起设）。
     * 超级岛左右分段时由内容组装器按左侧字数设置，右侧接着左侧色序继续。
     */
    var paletteOffset: Int = 0
        set(value) {
            field = value
            main.paletteOffset = value
            secondary.paletteOffset = value
        }

    // —— 样式 ——

    open fun setStyle(style: LyricViewStyle) {
        displayLineByLine = style.lineDisplay
        assembler.updateFlags(
            displayTranslation, displayRoma,
            style.primary.relativeProgress, style.primary.relativeHighlight,
            displayLineByLine
        )
        mirrorDesired = style.mirror
        applyLineStyle(
            main, style.primary, style.highlight,
            style.gradient, style.wordMotion,
            style.centerIfPossible, style.rightIfPossible, style.mirror, style.lyricEffect,
            style.effectSolidColor
        )
        applyLineStyle(
            secondary, style.secondary, style.highlight,
            style.gradient, style.wordMotion,
            style.centerIfPossible, style.rightIfPossible,
            // 副行（翻译/预览）不做倒影：倒影只跟主行走，副行再翻一遍会叠在一起
            false, style.lyricEffect,
            style.effectSolidColor
        )
        refreshLines()
        updateMirrorState()
    }

    private fun applyLineStyle(
        view: LyricLineView,
        text: TextLook,
        highlight: Highlight,
        gradient: Boolean,
        wordMotion: WordMotion,
        centerIfPossible: Boolean,
        rightIfPossible: Boolean,
        mirror: Boolean,
        lyricEffect: Int,
        effectSolidColor: Int
    ) {
        // 效果模式的统一色要在 configureWith 之前设好（configureWith 会据此重建效果调色板）
        view.effectSolidColor = effectSolidColor
        view.configureWith(
            text, highlight, gradient,
            centerIfPossible, rightIfPossible, wordMotion
        )
        view.mirrorEnabled = mirror
        view.lyricEffectMode = lyricEffect
    }
    override fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        main.updateColor(primary, background, highlight)
        secondary.updateColor(primary, background, highlight)
    }

    // —— 岛缩放 ——

    fun setRenderScale(scale: Float) {
        if (renderScale != scale) {
            renderScale = scale
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (renderScale != 1.0f && renderScale > 0f) {
            val origW = MeasureSpec.getSize(widthMeasureSpec)
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val compW = (origW / renderScale).toInt()
            super.onMeasure(MeasureSpec.makeMeasureSpec(compW, mode), heightMeasureSpec)
            setMeasuredDimension(origW, measuredHeight)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (renderScale != 1.0f) {
            canvas.withScale(renderScale, renderScale, 0f, height / 2f) {
                super.dispatchDraw(this)
            }
        } else {
            super.dispatchDraw(canvas)
        }
    }

    // —— 生命周期 ——

    fun reset() {
        rawLine = null
        renderScale = 1.0f
        currentMainText = null
        pendingWillApply = null
        pendingApplied = null
        pendingCancelled = null
        preflightDeferred = false
        lastPosition = Long.MIN_VALUE
        lastPlaybackSpeed = 1f
        main.reset()
        secondary.reset()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }
}
