/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * PrefsBridge：设置页 prefs 读写桥接口。
 * 默认实现 MasterPrefs（超级岛），状态栏歌词用独立实现 StatusBarPrefs（独立文件），
 * 两者各是各的文本、各是各的控制。
 */

package com.liuxiansheng.lyricon.app.util

import android.content.Context

interface PrefsBridge {
    fun contains(context: Context, key: String): Boolean
    fun getBoolean(context: Context, key: String, default: Boolean): Boolean
    fun setBoolean(context: Context, key: String, value: Boolean)
    fun getInt(context: Context, key: String, default: Int): Int
    fun setInt(context: Context, key: String, value: Int)
    fun getFloat(context: Context, key: String, default: Float): Float
    fun setFloat(context: Context, key: String, value: Float)
    fun getString(context: Context, key: String, default: String?): String?
    fun setString(context: Context, key: String, value: String?)
}
