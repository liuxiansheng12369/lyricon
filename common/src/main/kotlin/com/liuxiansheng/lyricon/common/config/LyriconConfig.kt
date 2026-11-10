/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


package com.liuxiansheng.lyricon.common.config

import kotlinx.serialization.Serializable

/**
 * 歌词岛配置（M3 扩展更多字段）
 */
@Serializable
data class IslandConfig(
    /** 歌词岛总开关 */
    val enabled: Boolean = true,
    /** 动态模式开关 */
    val dynamicMode: Boolean = true,
    /** 歌词字体大小（sp） */
    val textSizeSp: Float = 14f,
    /** 歌词颜色 */
    val textColor: String = "#FFFFFFFF",
    /** 岛最大宽度（dp） */
    val maxWidthDp: Int = 300,
    /** 频谱联动歌词宽度开关 */
    val spectrumLinkWidthEnabled: Boolean = true,
    /** 显示频谱时文本容器收窄量（dp） */
    val spectrumVisibleMarginDp: Int = 22,
    /** 隐藏频谱时文本容器收窄量（dp） */
    val spectrumHiddenMarginDp: Int = 4,
    /** 进度条开关 */
    val progressBarEnabled: Boolean = true,
    /** 外发光开关 */
    val outerGlowEnabled: Boolean = true,
    /** 封面显示开关 */
    val coverEnabled: Boolean = true
)

/**
 * 状态栏歌词配置（M5 扩展更多字段）
 */
@Serializable
data class StatusBarConfig(
    /** 状态栏歌词总开关 */
    val enabled: Boolean = true,
    /** 歌词字体大小（sp） */
    val textSizeSp: Float = 12f,
    /** 歌词颜色 */
    val textColor: String = "#FFFFFFFF",
    /** 左右边距（dp） */
    val horizontalMarginDp: Int = 8,
    /** 垂直偏移（dp） */
    val verticalOffsetDp: Int = 0
)

/**
 * 全局配置（聚合所有域配置）
 *
 * 性能铁律：配置对象不可变（data class + val），
 * 热路径直接读 @Volatile 引用，不需要加锁。
 * 配置变更 = 替换整个对象引用（原子性由 @Volatile 保证）。
 */
@Serializable
data class LyriconConfig(
    val island: IslandConfig = IslandConfig(),
    val statusBar: StatusBarConfig = StatusBarConfig(),
    /** 调试日志开关 */
    val debugLog: Boolean = false,
    /** 信息日志开关 */
    val infoLog: Boolean = false
)
