/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * 分组排序持久化：主页区块 / 设置页分组 的长按排序顺序（纯 UI 顺序，xposed 端不读）。
 */
package com.liuxiansheng.lyricon.app.util

import android.content.Context
import org.json.JSONArray

object SectionOrder {

    /** 读取排序顺序；无记录或脏数据时回退默认顺序（只保留已知 id + 去重）。 */
    fun read(context: Context, key: String, defaults: List<String>): List<String> {
        val prefs = context.getSharedPreferences(MasterPrefs.PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(key, null)
        android.util.Log.i("LyriconPrefsWrite", "SectionOrder.read key=$key raw=${raw ?: "null"}")
        if (raw == null) return defaults
        return runCatching {
            val arr = JSONArray(raw)
            val parsed = (0 until arr.length()).map { arr.getString(it) }
            val valid = parsed.filter { it in defaults }.distinct()
            // 补上缺失的默认项，保证所有区块都在列表里
            defaults.filter { it !in valid } + valid
        }.getOrDefault(defaults)
    }

    /** 写入排序顺序（JSON 数组字符串）。 */
    fun write(context: Context, key: String, order: List<String>) {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        android.util.Log.i("LyriconPrefsWrite", "SectionOrder.write key=$key order=$order")
        context.getSharedPreferences(MasterPrefs.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, arr.toString())
            .apply()
        android.util.Log.i("LyriconPrefsWrite", "SectionOrder.write applied key=$key")
    }
}
