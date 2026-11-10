/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * SkinHost：独立 Activity（SettingsActivity 等）的皮肤宿主。
 * 这类页面不在 MainNav 的 CompositionLocalProvider 里，单独启动时会拿不到皮肤，
 * 于是背景与配色退回默认蓝紫。SkinHost 自己从 prefs 读 pref_home_skin，
 * 提供 LocalHomeSkin + LocalSkinThemeController，让独立启动的页面与首页同一套皮肤。
 */

package com.liuxiansheng.lyricon.app.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun SkinHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val pref = remember {
        context.getSharedPreferences("com.liuxiansheng.lyricon_preferences", android.content.Context.MODE_PRIVATE)
    }
    var skinId by remember { mutableStateOf(pref.getInt(HomeSkins.KEY, HomeSkins.DEFAULT)) }

    DisposableEffect(pref) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HomeSkins.KEY) skinId = pref.getInt(HomeSkins.KEY, HomeSkins.DEFAULT)
        }
        pref.registerOnSharedPreferenceChangeListener(listener)
        onDispose { pref.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val skin = HomeSkins.of(skinId)
    val mode = when {
        skin != null -> if (skin.dark) ColorSchemeMode.Dark else ColorSchemeMode.Light
        else -> ColorSchemeMode.System
    }
    val controller = remember(skinId) { ThemeController(mode) }

    CompositionLocalProvider(
        LocalHomeSkin provides skin,
        LocalSkinThemeController provides controller,
    ) {
        MiuixTheme(controller = controller) { content() }
    }
}
