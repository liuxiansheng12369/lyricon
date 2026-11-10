/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


package com.liuxiansheng.lyricon.common.data

import kotlinx.serialization.Serializable

/**
 * 活跃播放器提供者信息
 *
 * @property packageName 播放器应用包名
 * @property appName 应用名称
 * @property providerType 提供者类型（如 "media_session"、"coloros_lyric" 等）
 */
@Serializable
data class ProviderInfo(
    val packageName: String,
    val appName: String? = null,
    val providerType: String? = null
)
