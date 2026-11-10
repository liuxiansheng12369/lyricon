/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 上游 HyperIsland 外观引擎配置适配器：替代上游 ConfigManager，
 * 统一读 V3 的 SharedPreferences（HookEntry.instance.prefs）。
 * 上游与 V3 配置 key 完全一致（pref_island_material_big_config 等），无需映射。
 */

package com.liuxiansheng.lyricon.hyper.root.island.upstream

import android.content.SharedPreferences
import com.liuxiansheng.lyricon.hyper.root.HookEntry

/** 上游外观引擎的配置读取入口（V3 prefs 适配）。 */
internal object UpstreamConfig {
    private val prefs: SharedPreferences?
        get() = HookEntry.instance?.prefs

    fun getString(key: String, def: String = ""): String =
        prefs?.getString(key, def) ?: def

    fun getBoolean(key: String, def: Boolean): Boolean =
        prefs?.getBoolean(key, def) ?: def

    fun getInt(key: String, def: Int): Int =
        prefs?.getInt(key, def) ?: def

    fun contains(key: String): Boolean =
        prefs?.contains(key) ?: false
}