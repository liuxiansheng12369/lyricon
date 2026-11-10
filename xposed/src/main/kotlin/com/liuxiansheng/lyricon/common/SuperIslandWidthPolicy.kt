/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common

/** 超级岛宽度策略（V3 自研）。 */
object SuperIslandWidthPolicy {
    const val SIDE_COMPONENT_WIDTH_DP = 22
    const val MIN_ISLAND_WIDTH_DP = 22
    const val MAX_SIDE_WIDTH_DP = 170
    const val SYSTEM_LIMITED_MAX_SIDE_WIDTH_DP = 114

    /** 最小岛宽：左组件（封面/律动）占位相抵后的下限。 */
    fun minIslandWidth(showAlbum: Boolean, showRhythm: Boolean): Int =
        (componentWidth(showAlbum) - componentWidth(showRhythm))
            .coerceAtLeast(MIN_ISLAND_WIDTH_DP)

    /** 最大岛宽：解除上限则取 170dp，否则受系统限制 114dp，再减去右组件占位。 */
    fun maxIslandWidth(showRhythm: Boolean, disableWidthLimit: Boolean): Int =
        (if (disableWidthLimit) MAX_SIDE_WIDTH_DP else SYSTEM_LIMITED_MAX_SIDE_WIDTH_DP) -
                componentWidth(showRhythm)

    /** 把岛宽约束到 [minIslandWidth, maxIslandWidth] 区间。 */
    fun normalizeIslandWidth(
        islandWidth: Int,
        showAlbum: Boolean,
        showRhythm: Boolean,
        disableWidthLimit: Boolean = false
    ): Int = islandWidth.coerceIn(
        minIslandWidth(showAlbum, showRhythm),
        maxIslandWidth(showRhythm, disableWidthLimit)
    )

    /** 左侧内容可用宽 = 归一化岛宽 + 左组件偏移。 */
    fun leftContentWidth(
        islandWidth: Int,
        showAlbum: Boolean,
        showRhythm: Boolean,
        disableWidthLimit: Boolean = false
    ): Int {
        val normalizedWidth = normalizeIslandWidth(
            islandWidth = islandWidth,
            showAlbum = showAlbum,
            showRhythm = showRhythm,
            disableWidthLimit = disableWidthLimit
        )
        return normalizedWidth + leftContentWidthOffsetDp(showAlbum, showRhythm)
    }

    /** 左侧内容宽与岛宽之间的偏移：右组件（律动）占位减左组件（封面）占位。 */
    fun leftContentWidthOffsetDp(showAlbum: Boolean, showRhythm: Boolean): Int =
        componentWidth(showRhythm) - componentWidth(showAlbum)

    /** 由左侧内容宽反推岛宽（与 [leftContentWidth] 互逆）。 */
    fun baseWidthFromLeftContentWidth(
        leftContentWidthDp: Float,
        showAlbum: Boolean,
        showRhythm: Boolean
    ): Float = leftContentWidthDp - leftContentWidthOffsetDp(showAlbum, showRhythm)

    private fun componentWidth(visible: Boolean): Int =
        if (visible) SIDE_COMPONENT_WIDTH_DP else 0
}
