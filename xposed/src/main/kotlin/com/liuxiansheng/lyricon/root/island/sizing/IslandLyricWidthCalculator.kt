/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.sizing

import com.liuxiansheng.lyricon.hyper.common.SuperIslandWidthPolicy
import kotlin.math.ceil

/**
 * 歌词宽度规格（V3 自研）——宽度计算的输入参数。
 * [spectrumReservedDp] 为频谱封面占位（显示频谱=22dp，隐藏=0dp），
 * 宽度公式显式包含它：开关切换后宽度重算结果立即差 22dp → 岛宽立即变、歌词立即重排。
 */
internal data class IslandLyricWidthSpec(
    val density: Float,
    val paddingLeftPx: Int,
    val paddingRightPx: Int,
    val minWidthDp: Int,
    val maxWidthDp: Int,
    val isLeft: Boolean,
    val showAlbum: Boolean,
    val showRhythm: Boolean,
    val spectrumReservedDp: Int = 0
)

/** 歌词宽度计算器（V3 自研）。 */
internal object IslandLyricWidthCalculator {

    /** 基础宽度（dp）：内容宽 + 内边距 + 频谱占位，左侧再按封面/律动偏移换算。 */
    fun baseWidthDp(contentWidthPx: Float, spec: IslandLyricWidthSpec): Float? {
        if (spec.maxWidthDp <= 0 || spec.density <= 0f) return null

        val paddingPx = spec.paddingLeftPx.coerceAtLeast(0) +
                spec.paddingRightPx.coerceAtLeast(0)
        val requiredWidthPx = (
                ceil(contentWidthPx.coerceAtLeast(0f)).toInt() + paddingPx
                ).coerceAtLeast(1)
        // 频谱占位显式加入宽度：显示频谱时歌词让出 22dp，隐藏时释放给歌词。
        val requiredWidthDp = requiredWidthPx / spec.density +
                spec.spectrumReservedDp.coerceAtLeast(0)
        return if (spec.isLeft) {
            SuperIslandWidthPolicy.baseWidthFromLeftContentWidth(
                leftContentWidthDp = requiredWidthDp,
                showAlbum = spec.showAlbum,
                showRhythm = spec.showRhythm
            )
        } else {
            requiredWidthDp
        }
    }

    /** 目标宽度（dp）：基础宽度 + 左侧偏移，约束到 [minWidthDp, maxWidthDp]。 */
    fun targetWidthDp(baseWidthDp: Float, spec: IslandLyricWidthSpec): Float? {
        if (spec.maxWidthDp <= 0) return null

        val adjustmentDp = if (spec.isLeft) {
            SuperIslandWidthPolicy.leftContentWidthOffsetDp(
                showAlbum = spec.showAlbum,
                showRhythm = spec.showRhythm
            )
        } else {
            0
        }
        return (baseWidthDp + adjustmentDp).coerceIn(
            spec.minWidthDp.toFloat(),
            spec.maxWidthDp.toFloat()
        )
    }

    /** 目标宽度（px）。 */
    fun targetWidthPx(baseWidthDp: Float, spec: IslandLyricWidthSpec): Int? {
        if (spec.density <= 0f) return null
        val targetWidthDp = targetWidthDp(baseWidthDp, spec) ?: return null
        return ceil(targetWidthDp * spec.density).toInt().coerceAtLeast(1)
    }
}
