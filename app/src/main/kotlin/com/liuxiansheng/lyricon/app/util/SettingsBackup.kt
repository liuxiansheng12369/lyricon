/*
 * 词岛 · 设置备份 / 恢复（settings backup & restore）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * 1.0.3：原先「导出设置 / 导入设置」只是两个空壳入口（onClick 里是 TODO），
 * 现改为真实可用的 JSON 备份。
 *
 * 备份范围：超级岛主设置（com.liuxiansheng.lyricon_preferences）+
 *          状态栏歌词设置（com.liuxiansheng.lyricon_status_bar_preferences）。
 * 写入方式：走 PrefsBridge（remote + 本地文件双写），恢复后 SystemUI 立即能读到。
 * 值类型：boolean / int / string（工程内设置项只有这三类；其余类型兜底存字符串）。
 */

package com.liuxiansheng.lyricon.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object SettingsBackup {

    private const val VERSION = 1

    private const val TYPE_BOOLEAN = "b"
    private const val TYPE_INT = "i"
    private const val TYPE_STRING = "s"
    private const val TYPE_FLOAT = "f"

    /** 导出为 JSON 文本（调用方可直接写入 SAF 选择的文件） */
    fun exportJson(context: Context): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("generatedAt", System.currentTimeMillis())
        root.put("main", encodeFile(context, MasterPrefs.PREF_NAME))
        root.put("statusBar", encodeFile(context, StatusBarPrefs.PREF_NAME))
        return root.toString(2)
    }

    /**
     * 从 JSON 文本恢复设置。
     * @return 成功写入的设置项数量
     */
    fun importJson(context: Context, text: String): Int {
        val root = JSONObject(text)
        var count = 0
        root.optJSONObject("main")?.let { section ->
            count += applySection(context, MasterPrefs, section)
        }
        root.optJSONObject("statusBar")?.let { section ->
            count += applySection(context, StatusBarPrefs, section)
        }
        return count
    }

    /** 备份文件建议文件名（带时间戳，便于多份并存） */
    fun suggestedFileName(): String {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
        return "词岛设置备份-${fmt.format(java.util.Date())}.json"
    }

    // ================= 内部实现 =================

    private fun prefsOf(context: Context, name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun encodeFile(context: Context, name: String): JSONObject {
        val section = JSONObject()
        val all = prefsOf(context, name).all
        all.forEach { (key, value) ->
            val wrapped = JSONObject()
            when (value) {
                is Boolean -> {
                    wrapped.put("t", TYPE_BOOLEAN)
                    wrapped.put("v", value)
                }
                is Int -> {
                    wrapped.put("t", TYPE_INT)
                    wrapped.put("v", value)
                }
                is Long -> {
                    // 工程内没有 long 型设置项：能塞进 int 的按 int，否则存字符串
                    if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                        wrapped.put("t", TYPE_INT)
                        wrapped.put("v", value.toInt())
                    } else {
                        wrapped.put("t", TYPE_STRING)
                        wrapped.put("v", value.toString())
                    }
                }
                is Float -> {
                    wrapped.put("t", TYPE_FLOAT)
                    wrapped.put("v", value.toDouble())
                }
                is String -> {
                    wrapped.put("t", TYPE_STRING)
                    wrapped.put("v", value)
                }
                is Set<*> -> {
                    // 字符串集合没有 PrefsBridge 写入口：按字符串保存，避免整份备份失败
                    wrapped.put("t", TYPE_STRING)
                    wrapped.put("v", value.joinToString(","))
                }
                else -> {
                    wrapped.put("t", TYPE_STRING)
                    wrapped.put("v", value.toString())
                }
            }
            section.put(key, wrapped)
        }
        return section
    }

    private fun applySection(context: Context, bridge: PrefsBridge, section: JSONObject): Int {
        var count = 0
        for (key in section.keys()) {
            val item = section.optJSONObject(key) ?: continue
            val type = item.optString("t", TYPE_STRING)
            runCatching {
                when (type) {
                    TYPE_BOOLEAN -> bridge.setBoolean(context, key, item.optBoolean("v", false))
                    TYPE_INT -> bridge.setInt(context, key, item.optInt("v", 0))
                    TYPE_FLOAT -> bridge.setFloat(context, key, item.optDouble("v", 0.0).toFloat())
                    else -> bridge.setString(context, key, item.optString("v", ""))
                }
                count++
            }
        }
        return count
    }
}
