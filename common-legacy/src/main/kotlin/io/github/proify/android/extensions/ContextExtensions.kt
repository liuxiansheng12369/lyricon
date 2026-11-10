/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.android.extensions

import android.content.Context
import android.content.SharedPreferences
import io.github.proify.lyricon.common.util.safe

fun Context.getPrivateSharedPreferences(name: String): SharedPreferences {
    return getSharedPreferences(name, Context.MODE_PRIVATE).safe()
}
//
///**
// * 尝试获取 world-readable 的 SharedPreferences
// */
//@SuppressLint("WorldReadableFiles")
//fun Context.getWorldReadableSharedPreferences(name: String): SharedPreferences = try {
//    @Suppress("DEPRECATION")
//    getSharedPreferences(name, Context.MODE_WORLD_READABLE).safe()
//} catch (_: Exception) {
//    getPrivateSharedPreferences(name)
//}
//
//@Suppress("unused")
//fun Context.getSharedPreferences(name: String, worldReadable: Boolean): SharedPreferences =
//    if (worldReadable) getWorldReadableSharedPreferences(name)
//    else getPrivateSharedPreferences(name)

val Context.defaultSharedPreferences: SharedPreferences
    get() = getPrivateSharedPreferences(defaultSharedPreferencesName)

val Context.defaultSharedPreferencesName: String get() = packageName + "_preferences"