/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * HyperIsland-Hub 配置共享中心客户端（词岛版，对齐上游 HubClient）：
 * 只做公开的「列表 / 详情」操作，全部匿名。用于下载云端岛背景预设并应用到词岛毛玻璃。
 */

package com.liuxiansheng.lyricon.app.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object HubPresetClient {
    private const val TAG = "HubPreset"
    // 末尾不要带斜杠（与上游 API 文档一致）
    private const val BASE = "https://hyperisland-hub.1812z.top"
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 20_000

    /** 云端预设列表条目（仅元数据）。 */
    data class PresetSummary(
        val hubId: String,
        val title: String,
        val author: String,
        val version: String,
    )

    /** 拉取全部已发布配置的列表元数据（不含正文）。 */
    fun listAll(): List<PresetSummary> {
        val result = mutableListOf<PresetSummary>()
        val (firstCode, first) = request("GET", "/api/configs?page=1")
        if (firstCode !in 200..299) return result
        appendItems(first, result)
        val pages = first.optInt("pages", 1)
        for (page in 2..pages) {
            val (code, body) = request("GET", "/api/configs?page=$page")
            if (code in 200..299) appendItems(body, result)
        }
        return result
    }

    /**
     * 拉取配置详情，返回 `payload.sections`（sectionId → { pref_key: value }）。
     * 兼容 payload 顶层直接挂分组的形态。
     */
    fun detailSections(hubId: String): JSONObject {
        val (code, body) = request("GET", "/api/configs/$hubId")
        if (code !in 200..299) {
            throw IllegalStateException("Hub 详情请求失败 HTTP $code: ${body.optString("error")}")
        }
        val payload = body.optJSONObject("payload") ?: return JSONObject()
        val sections = payload.optJSONObject("sections")
        return sections ?: payload
    }

    /** 解析类型化 JSON 值，判断可写入的类型。 */
    private fun typedValue(value: Any?): Any? {
        if (value is JSONObject || value is org.json.JSONArray) return value.toString()
        if (value is Boolean || value is String) return value
        if (value is Int || value is Long) return (value as Number).toInt()
        if (value is Double || value is Float) {
            val d = (value as Number).toDouble()
            return if (d % 1.0 == 0.0) d.toInt() else d.toFloat()
        }
        if (value is Number) return value.toInt()
        return value?.toString()
    }

    /**
     * 应用「岛背景」section（appearance_background）到词岛 prefs：
     * 只写入 pref_island_bg_ / pref_island_material_ / pref_island_blur_ /
     * pref_island_glass_ / pref_island_refraction_ 前缀的键，并强制开启毛玻璃。
     * 返回用户可见的结果描述（成功：已应用 N 项；失败：说明该预设不含岛背景、包含哪些部分）。
     */
    fun applyIslandBackground(context: Context, sections: JSONObject): String {
        val section = sections.optJSONObject("appearance_background")
            ?: return "该预设不含岛背景配置" + describeOtherSections(sections)
        val prefixes = listOf(
            "pref_island_bg_",
            "pref_island_material_",
            "pref_island_blur_",
            "pref_island_glass_",
            "pref_island_refraction_",
        )
        var written = 0
        section.keys().forEach { key ->
            val prefixed = prefixes.any { key.startsWith(it) }
            if (!prefixed) return@forEach
            val raw = section.opt(key)
            val value = typedValue(raw)
            if (value == null) return@forEach
            writePref(context, key, value)
            Log.i(TAG, "应用岛背景预设键: $key = $value")
            written++
        }
        if (written > 0) {
            // 2026-11-08：不再擅自打开「毛玻璃」总开关——用户口径：总开关没开，岛背景写入一律无效。
            // 关总开关时 MasterPrefs 会直接丢弃这些键，所以这里也不再把总开关顶开。
            if (MasterPrefs.isMaterialMasterOn(context)) {
                // 与旧背景模式互斥（毛玻璃开启时关掉透明/封面）
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_TRANSPARENT_BIG_ENABLED, false)
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_COVER_BG_ENABLED, false)
            }
        }
        return if (written > 0) "已应用岛背景预设（$written 项）" else "该预设的岛背景部分无可写入项"
    }

    /**
     * 通用应用：把选中的 section 内所有 pref 键写入词岛 prefs（type 转换）。
     * 选中 appearance_background 时强制开启毛玻璃并关闭透明/封面（保证岛背景即时生效）。
     * 返回用户可见结果描述。
     */
    fun applySections(context: Context, sections: JSONObject, selectedIds: Set<String>): String {
        var written = 0
        var unsupported = 0
        selectedIds.forEach { id ->
            val section = sections.optJSONObject(id) ?: return@forEach
            section.keys().forEach { key ->
                val value = typedValue(section.opt(key))
                if (value == null) return@forEach
                // 只写入词岛认识的前缀键；其余记为「不支持」避免误导用户以为生效
                if (!isSupportedKey(key)) {
                    unsupported++
                    return@forEach
                }
                writePref(context, key, value)
                written++
            }
        }
        if (selectedIds.any { it == "appearance_background" } && written > 0) {
            // 2026-11-08：同上，不再擅自打开「毛玻璃」总开关。
            if (MasterPrefs.isMaterialMasterOn(context)) {
                // 与旧背景模式互斥（毛玻璃开启时关掉透明/封面）
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_TRANSPARENT_BIG_ENABLED, false)
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_COVER_BG_ENABLED, false)
            }
        }
        return buildString {
            append("已应用 $written 项")
            if (unsupported > 0) append("（$unsupported 项词岛暂不支持已跳过）")
        }
    }

    /** 词岛 xposed 认识的前缀键（写入才会真正生效）。 */
    private fun isSupportedKey(key: String): Boolean = key.startsWith("pref_island_bg_") ||
        key.startsWith("pref_island_material_") || key.startsWith("pref_island_blur_") ||
        key.startsWith("pref_island_glass_") || key.startsWith("pref_island_refraction_")

    /** section 中文标题（对齐 describeOtherSections 的 SECTION_ZH）。 */
    fun sectionTitleZh(id: String): String = SECTION_ZH[id] ?: id

    private fun describeOtherSections(sections: JSONObject): String {
        val names = sections.keys().asSequence().filter { it != "appearance_background" }.map { sec ->
            when {
                sec.startsWith("appconfig:") -> "通知"
                else -> SECTION_ZH[sec] ?: sec
            }
        }.toList()
        return if (names.isEmpty()) "" else "（其包含：${names.joinToString("、")}）"
    }

    private val SECTION_ZH = mapOf(
        "appearance_size" to "外观尺寸",
        "appearance_text" to "文字",
        "appearance_icon" to "图标",
        "appearance_outline" to "描边",
        "appearance_animation" to "动画",
        "appearance_background" to "背景",
        "ai" to "AI",
        "filter_rules" to "过滤规则",
        "default_config" to "默认配置",
        "hide_behavior" to "隐藏行为",
        "keep_island" to "常驻岛",
        "other" to "其他",
        "ext_system_ui" to "系统扩展",
        "ext_settings" to "设置扩展",
        "ext_security_center" to "安全中心",
        "ext_xmsf" to "小米服务",
        "ext_screen_recorder" to "录屏",
        "ext_download_manager" to "下载管理",
    )

    private fun writePref(context: Context, key: String, value: Any) {
        when (value) {
            is Boolean -> MasterPrefs.setBoolean(context, key, value)
            is Int -> MasterPrefs.setInt(context, key, value)
            is Float -> MasterPrefs.setFloat(context, key, value)
            is String -> MasterPrefs.setString(context, key, value)
            else -> MasterPrefs.setString(context, key, value.toString())
        }
    }

    private fun appendItems(root: JSONObject, into: MutableList<PresetSummary>) {
        val items = root.optJSONArray("items") ?: return
        for (index in 0 until items.length()) {
            val it = items.optJSONObject(index) ?: continue
            into += PresetSummary(
                hubId = it.optString("id"),
                title = it.optString("name"),
                author = it.optString("author"),
                version = it.optString("version"),
            )
        }
    }

    private fun request(method: String, path: String): Pair<Int, JSONObject> {
        val connection = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Lyricon-CiDao/3.0")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            code to runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        } finally {
            connection.disconnect()
        }
    }
}
