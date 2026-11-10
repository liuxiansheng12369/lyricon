/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.app.activity

/**
 * M1 占位。
 * 真正的桌面入口是 AndroidManifest 中声明的 <activity-alias android:name=".activity.LauncherAlias">，
 * 它指向 [MainActivity]（MainActivity 本身只带 MAIN + category INFO，不在桌面显示图标）。
 * 此处仅为占位声明，便于 IDE 导航与类名校验，不包含任何运行时逻辑。
 */
object LauncherAlias
