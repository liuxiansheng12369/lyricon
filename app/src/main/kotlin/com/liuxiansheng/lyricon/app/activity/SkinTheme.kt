/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * SkinTheme：二级页（听歌记录 / 歌词源 / 更多设置 / 关于 / 状态栏歌词…）统一继承首页主题皮肤。
 * MainNav 会把当前皮肤的 ThemeController 放进 LocalSkinThemeController；
 * 二级页原来的 `MiuixTheme { }` 换成 `SkinTheme { }`，就不再各自回到默认浅色主题。
 */

package com.liuxiansheng.lyricon.app.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 当前全局主题控制器；null = 没有外层（独立 Activity 兜底走默认） */
val LocalSkinThemeController = staticCompositionLocalOf<ThemeController?> { null }

@Composable
fun SkinTheme(content: @Composable () -> Unit) {
    val controller = LocalSkinThemeController.current
    if (controller != null) {
        MiuixTheme(controller = controller) { content() }
    } else {
        MiuixTheme { content() }
    }
}
