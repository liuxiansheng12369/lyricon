/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.lyricon.colorextractor.palette

/**
 * 图片颜色提取结果
 */
data class ColorPaletteResult(
    val lightModeColors: ThemeColors,   // 亮主题配色
    val darkModeColors: ThemeColors     // 暗主题配色
)