/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 高亮宽度动画器（V3 自研，目标驱动插值）。
 * 每次只设置"目标宽度"，从当前宽度线性（可带 sustain 缓动）插值到目标；
 * 宽度单调前进、从不回退，词尾自然衔接下一词，无重锚跳变、无闪烁。
 * 由渲染容器每帧调用 [step] 推进。
 */

package com.liuxiansheng.lyricon.lyric.render.anim

internal class ProgressAnimator {

    var currentWidth = 0f
        private set
    var targetWidth = 0f
        private set
    var isAnimating = false
        private set
    var hasStarted = false
    var hasFinished = false

    private var startWidth = 0f
    private var elapsedNanos = 0L
    private var durationNanos = 1L
    private var sustainStrength = 0f

    /** 直接跳到指定宽度（seek / 行首行尾 / 词间保持） */
    fun jumpTo(width: Float) {
        currentWidth = width
        targetWidth = width
        isAnimating = false
        sustainStrength = 0f
        if (currentWidth > 0f) hasStarted = true
    }

    /**
     * 平滑推进到目标宽度：从当前宽度开始，在 [durationMs] 内插值到 [target]。
     * 只向前推进，不回退。
     */
    fun animateTo(target: Float, durationMs: Long, sustainStrength: Float) {
        startWidth = currentWidth
        targetWidth = target
        durationNanos = maxOf(1L, durationMs) * 1_000_000L
        elapsedNanos = 0L
        this.sustainStrength = sustainStrength.coerceIn(0f, 1f)
        isAnimating = true
        if (currentWidth > 0f) hasStarted = true
    }

    /** 帧推进。返回高亮宽度是否有变化（供容器决定是否重绘）。 */
    fun step(deltaNanos: Long): Boolean {
        if (!isAnimating) return false
        elapsedNanos += deltaNanos.coerceAtLeast(0L)
        val t = (elapsedNanos.toFloat() / durationNanos.toFloat()).coerceIn(0f, 1f)
        val mapped = WordProgressMapper.map(t, sustainStrength)
        currentWidth = startWidth + (targetWidth - startWidth) * mapped
        if (t >= 1f) {
            currentWidth = targetWidth
            isAnimating = false
            finishIfAtEnd()
        }
        return true
    }

    /** 暂停在当前宽度 */
    fun stopAtCurrent() {
        isAnimating = false
    }

    fun reset() {
        currentWidth = 0f
        targetWidth = 0f
        isAnimating = false
        hasStarted = false
        hasFinished = false
        startWidth = 0f
        elapsedNanos = 0L
        durationNanos = 1L
        sustainStrength = 0f
    }

    /** 从另一动画器同步状态（双行 / 分离模式同步用） */
    fun syncFrom(other: ProgressAnimator) {
        currentWidth = other.currentWidth
        targetWidth = other.targetWidth
        isAnimating = other.isAnimating
        hasStarted = other.hasStarted
        hasFinished = other.hasFinished
        startWidth = other.startWidth
        elapsedNanos = other.elapsedNanos
        durationNanos = other.durationNanos
        sustainStrength = other.sustainStrength
    }

    private fun finishIfAtEnd() {
        if (currentWidth > 0f) hasStarted = true
        if (targetWidth > 0f && currentWidth >= targetWidth) {
            hasFinished = true
        }
    }
}
