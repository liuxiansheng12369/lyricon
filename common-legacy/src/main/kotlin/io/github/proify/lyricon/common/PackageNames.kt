/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.lyricon.common

object PackageNames {
    const val APPLICATION: String = BuildConfig.APP_PACKAGE_NAME
    const val SYSTEM_UI: String = "com.android.systemui"
    const val MIUI_SYSTEM_UI_PLUGIN: String = "miui.systemui.plugin"
}