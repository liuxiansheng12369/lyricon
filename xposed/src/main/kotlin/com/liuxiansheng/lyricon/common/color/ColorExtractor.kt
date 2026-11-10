/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common.color

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette

/**
 * 封面主色提取（V3 自研）。
 */
object ColorExtractor {

    /**
     * 从封面同步提取主题色板。
     *
     * @param bitmap 封面位图
     * @param maxColors 最多提取的代表色数量
     */
    fun extractThemePalette(bitmap: Bitmap, maxColors: Int): ThemePalette {
        val palette = Palette.Builder(bitmap).maximumColorCount(maxColors).generate()
        val raw = palette.swatches
            .sortedByDescending { it.population }
            .take(maxColors)
            .map { it.rgb }
        if (raw.isEmpty()) {
            val fallback = intArrayOf(
                Color.rgb(0x30, 0x30, 0x30),
                Color.rgb(0x66, 0x66, 0x66),
                Color.rgb(0x99, 0x99, 0x99)
            ).take(maxColors)
            return ThemePalette(fallback, fallback, fallback)
        }
        val onWhite = raw.map { adaptForBackground(it, isDarkBg = false, minContrastRatio = 3.0f) }
        val onBlack = raw.map { adaptForBackground(it, isDarkBg = true, minContrastRatio = 3.0f) }
        return ThemePalette(raw, onWhite, onBlack)
    }

    /** 提高对比度：白色背景压暗、黑色背景提亮，直到满足最小对比度。 */
    private fun adaptForBackground(color: Int, isDarkBg: Boolean, minContrastRatio: Float): Int {
        var r = Color.red(color)
        var g = Color.green(color)
        var b = Color.blue(color)
        repeat(24) {
            val c = if (isDarkBg) contrast(r, g, b, 0, 0, 0) else contrast(r, g, b, 255, 255, 255)
            if (c >= minContrastRatio) return@adaptForBackground Color.rgb(r, g, b)
            if (isDarkBg) {
                r = (r + 12).coerceAtMost(255)
                g = (g + 12).coerceAtMost(255)
                b = (b + 12).coerceAtMost(255)
            } else {
                r = (r - 12).coerceAtLeast(0)
                g = (g - 12).coerceAtLeast(0)
                b = (b - 12).coerceAtLeast(0)
            }
        }
        return Color.rgb(r, g, b)
    }

    private fun contrast(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Float {
        val l1 = relativeLuminance(r1, g1, b1)
        val l2 = relativeLuminance(r2, g2, b2)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return ((hi + 0.05f) / (lo + 0.05f)).toFloat()
    }

    private fun relativeLuminance(r: Int, g: Int, b: Int): Float {
        fun lin(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).let { it * it * it * 1.0f }
        }
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
    }
}

/**
 * 主题色板结果。
 *
 * @param rawColors 从图片直接提取的原始代表色列表
 * @param onWhiteBackground 适合显示在白色背景上的颜色（高对比度暗色）
 * @param onBlackBackground 适合显示在黑色背景上的颜色（高对比度亮色）
 */
data class ThemePalette(
    val rawColors: List<Int>,
    val onWhiteBackground: List<Int>,
    val onBlackBackground: List<Int>
)
