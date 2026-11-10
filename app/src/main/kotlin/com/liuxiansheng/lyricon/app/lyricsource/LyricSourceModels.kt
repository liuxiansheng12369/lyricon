/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * LyricSourceModels：歌词源管理页数据模型。
 * 扫描系统内已安装的歌词 provider 插件（metaData lyricon_module 标记），
 * 展示其名称/版本/作者/描述/标签，并做签名认证标记。
 */

package com.liuxiansheng.lyricon.app.lyricsource

/**
 * 歌词源模块（一个已安装的歌词 provider 应用）。
 */
data class LyricSourceModule(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val author: String?,
    val description: String?,
    val category: String?,
    val isCertified: Boolean,
    val tags: List<String>,
    val homeUrl: String?,
    /** LSPosed 激活状态：false 表示 Xposed 模块未激活；null 表示无法检测（不展示激活提示）。 */
    val activation: Boolean? = null,
)

/**
 * 歌词源分类（未声明分类的归入"其他"，始终排最后）。
 */
data class LyricSourceCategory(
    val name: String,
    val items: List<LyricSourceModule>,
)

/**
 * 歌词源列表视图模式。
 */
object LyricSourceViewMode {
    const val SHORT = 0
    const val FULL = 1
}
