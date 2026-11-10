/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.lyricon.colorextractor.palette

data class ThemeColors(
    val primary: Int,
    val swatches: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ThemeColors

        if (primary != other.primary) return false
        if (!swatches.contentEquals(other.swatches)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = primary
        result = 31 * result + swatches.contentHashCode()
        return result
    }

}