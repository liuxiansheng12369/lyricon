/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 渲染器统一接口与公共状态（V3 自研）。
 * 四种渲染器（逐字同步 / 整行时间轴 / 纯滚动 / 倒计时）实现同一接口，
 * 由歌词行视图按模型类型分发。渲染器不持有 View 引用，只做绘制与进度，
 * 重绘与播放事件由视图层统一处理。
 */

package com.liuxiansheng.lyricon.lyric.render

import android.graphics.Canvas
import android.text.TextPaint
import com.liuxiansheng.lyricon.lyric.render.model.LyricRenderModel

/** 单行渲染的瞬时状态（滚动偏移等），一次换行重置 */
internal class LineRenderState {
    var scrollOffset = 0f
    var isScrollFinished = false

    fun reset() {
        scrollOffset = 0f
        isScrollFinished = false
    }
}

/**
 * 滚动步进器（V3 自研）。
 *
 * 文本超宽时，高亮推进到可见区一定比例即开始滚动（铺开滚，不堆到最后几个字，
 * 也不提前滚）；滚到自然到底，不用 finished 硬跳回末尾（否则行尾会"蹦"）。
 */
internal class ScrollStepper {

    /** 滚动触发点：唱到可见区该比例处开始滚 */
    private val triggerRatio = 0.85f

    /**
     * 计算当前滚动偏移。
     * @param highlightWidth 已唱区宽度（px）
     * @param totalWidth 整行文本宽度（px）
     * @param viewWidth 可视区宽度（px）
     * @return 滚动偏移（≤0；未超宽恒为 0）
     */
    fun compute(
        highlightWidth: Float,
        totalWidth: Float,
        viewWidth: Float,
        finished: Boolean,
        scrollFinished: Boolean
    ): Float {
        if (totalWidth <= viewWidth) return 0f
        val minScroll = -(totalWidth - viewWidth)
        val trigger = viewWidth * triggerRatio
        return if (highlightWidth > trigger) {
            val range = totalWidth - trigger
            val progress = ((highlightWidth - trigger) / range).coerceIn(0f, 1f)
            (minScroll * progress).coerceIn(minScroll, 0f)
        } else {
            0f
        }
    }
}

/** 渲染器统一接口 */
internal interface LineRenderer {
    var centerIfPossible: Boolean
    var rightIfPossible: Boolean

    /** 当前媒体位置（渲染器内部记录，用于回退判断） */
    val lastPosition: Long

    /** 动画是否正在推进（容器据此决定是否挂帧驱动） */
    val isAnimating: Boolean

    /** 是否已开始播放 */
    val isStarted: Boolean

    /** 是否已到行尾 */
    val isFinished: Boolean

    /** 跳转到指定位置（拖动进度条） */
    fun seek(
        model: LyricRenderModel,
        state: LineRenderState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    )

    /** 媒体位置推进 */
    fun update(
        model: LyricRenderModel,
        state: LineRenderState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int,
        playbackSpeed: Float
    )

    /** 帧推进；返回是否需要重绘 */
    fun step(
        deltaNanos: Long,
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int
    ): Boolean

    fun draw(
        canvas: Canvas,
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int,
        viewHeight: Int
    )

    /** 布局/尺寸变化时调用 */
    fun updateLayout(
        model: LyricRenderModel,
        state: LineRenderState,
        viewWidth: Int,
        viewHeight: Int
    )

    fun reset(state: LineRenderState)

    /** 暂停在当前进度 */
    fun freeze(model: LyricRenderModel, state: LineRenderState, viewWidth: Int)

    /** 复制另一渲染器状态（双行同步用） */
    fun syncFrom(other: LineRenderer)

    /** 整句连续配色偏移：默认渲染器单色/无逐字调色板，不需要处理 */
    fun setPaletteOffset(offset: Int) {}
}
