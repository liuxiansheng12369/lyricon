/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 渐变 Shader 工厂（V3 自研）。
 * 渐变主色保持 18% 宽度再渐入其余颜色，避免彩虹/多色退化成"色块拼接"。
 */

package com.liuxiansheng.lyricon.lyric.render.anim

import android.graphics.LinearGradient
import android.graphics.Shader

internal object GradientShaderFactory {

    /** 主色保持比例：让第一个颜色在渐变色中多停留，观感更稳 */
    private const val PRIMARY_COLOR_HOLD_FRACTION = 0.18f

    /**
     * 创建水平渐变 shader。
     *
     * @param startX 渐变起点（整行文本左端）
     * @param endX 渐变终点（整行文本右端）
     * @param colors 至少两个颜色
     */
    fun create(
        startX: Float,
        endX: Float,
        colors: IntArray
    ): LinearGradient {
        require(colors.size >= 2) { "渐变至少需要两个颜色" }

        // 第一个颜色先保持固定比例，其余颜色均分剩余区间
        val shaderColors = IntArray(colors.size + 1)
        val positions = FloatArray(colors.size + 1)
        shaderColors[0] = colors[0]
        shaderColors[1] = colors[0]
        positions[0] = 0f
        positions[1] = PRIMARY_COLOR_HOLD_FRACTION

        val remainingSpan = 1f - PRIMARY_COLOR_HOLD_FRACTION
        for (index in 1 until colors.size) {
            shaderColors[index + 1] = colors[index]
            positions[index + 1] = PRIMARY_COLOR_HOLD_FRACTION +
                remainingSpan * index / (colors.size - 1)
        }

        // 防御：非有限/退化区间不炸
        val safeStart = startX.takeIf { it.isFinite() } ?: 0f
        val safeEnd = endX.takeIf { it.isFinite() } ?: safeStart
        val resolvedEnd = if (safeEnd > safeStart) safeEnd else safeStart + 1f

        return LinearGradient(
            safeStart, 0f, resolvedEnd, 0f,
            shaderColors, positions, Shader.TileMode.CLAMP
        )
    }

    /**
     * 按"逐字色标"创建渐变：每个字中心一个色标，字与字之间自然过渡。
     * 既保留"一个字一个颜色"的观感，又不像整字跳色那样破坏"一点点往前推"的高亮。
     *
     * @param startX 行左端
     * @param endX 行右端
     * @param colors 每个字对应的颜色（已按色序偏移取好）
     * @param centers 每个字中心相对整行的比例位置（0~1，升序）
     */
    fun createCharAligned(
        startX: Float,
        endX: Float,
        colors: IntArray,
        centers: FloatArray
    ): LinearGradient {
        val safeStart = startX.takeIf { it.isFinite() } ?: 0f
        val safeEnd = endX.takeIf { it.isFinite() } ?: safeStart
        val resolvedEnd = if (safeEnd > safeStart) safeEnd else safeStart + 1f

        if (colors.size < 2 || centers.size != colors.size) {
            return create(safeStart, resolvedEnd, colors.takeIf { it.size >= 2 } ?: intArrayOf(colors[0], colors[0]))
        }

        // 首尾各补一个端点色标，避免第一/最后一个字之前出现端点纯色断层
        val shaderColors = IntArray(colors.size + 2)
        val positions = FloatArray(colors.size + 2)
        shaderColors[0] = colors[0]
        positions[0] = 0f
        for (i in colors.indices) {
            shaderColors[i + 1] = colors[i]
            positions[i + 1] = centers[i].coerceIn(0f, 1f)
        }
        shaderColors[colors.size + 1] = colors[colors.size - 1]
        positions[colors.size + 1] = 1f

        // 位置必须严格递增，否则 LinearGradient 会画错/抛异常
        for (i in 1 until positions.size) {
            if (positions[i] <= positions[i - 1]) {
                positions[i] = (positions[i - 1] + 0.0001f).coerceAtMost(1f)
            }
        }

        return LinearGradient(
            safeStart, 0f, resolvedEnd, 0f,
            shaderColors, positions, Shader.TileMode.CLAMP
        )
    }
}
