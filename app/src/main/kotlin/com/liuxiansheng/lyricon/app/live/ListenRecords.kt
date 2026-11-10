/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.app.live

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一条听歌记录（只统计"听够一半或 4 分钟"的有效播放）。 */
data class ListenRecord(
    val title: String,
    val artist: String,
    val packageName: String,
    val time: Long,
    val lastTime: Long,
    val listenedMs: Long,
    val durationMs: Long,
)

object ListenRecordsStore {
    fun load(context: Context): List<ListenRecord> {
        val raw = LiveStateProvider.recordsPrefs(context)
            .getString(LiveStateProvider.KEY_RECORDS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val out = ArrayList<ListenRecord>(array.length())
        for (i in 0 until array.length()) {
            val o: JSONObject = array.optJSONObject(i) ?: continue
            val title = o.optString("title")
            if (title.isBlank()) continue
            out.add(
                ListenRecord(
                    title = title,
                    artist = o.optString("artist"),
                    packageName = o.optString("package"),
                    time = o.optLong("time"),
                    lastTime = o.optLong("lastTime", o.optLong("time")),
                    listenedMs = o.optLong("listenedMs", 0L),
                    durationMs = o.optLong("durationMs", 0L),
                )
            )
        }
        return out.sortedByDescending { it.time }
    }

    fun clear(context: Context) {
        LiveStateProvider.recordsPrefs(context).edit()
            .remove(LiveStateProvider.KEY_RECORDS)
            .apply()
    }

    /** 删除单条（按"歌名+歌手+播放时间"定位，避免列表刷新后下标错位）。 */
    fun remove(context: Context, record: ListenRecord) {
        val prefs = LiveStateProvider.recordsPrefs(context)
        val raw = prefs.getString(LiveStateProvider.KEY_RECORDS, null) ?: return
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val same = o.optString("title") == record.title &&
                o.optString("artist") == record.artist &&
                o.optLong("time") == record.time
            if (!same) kept.put(o)
        }
        prefs.edit().putString(LiveStateProvider.KEY_RECORDS, kept.toString()).apply()
    }

    fun isEnabled(context: Context): Boolean =
        LiveStateProvider.recordsPrefs(context).getBoolean(LiveStateProvider.KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        LiveStateProvider.recordsPrefs(context).edit()
            .putBoolean(LiveStateProvider.KEY_ENABLED, enabled)
            .apply()
    }

    /** 播放来源 App 的展示名（未知则回退包名）。 */
    fun appLabel(packageName: String): String = when (packageName) {
        "com.luna.music" -> "汽水音乐"
        "com.netease.cloudmusic" -> "网易云音乐"
        "com.tencent.qqmusic" -> "QQ 音乐"
        "com.kugou.android" -> "酷狗音乐"
        "com.ximalaya.ting.android" -> "喜马拉雅"
        "com.spotify.music" -> "Spotify"
        else -> packageName
    }

    /**
     * 只记录"音乐类"App。
     * 视频、短视频、听书、浏览器网页播放这些也会在系统里登记媒体会话，
     * 不做过滤就会被当成"听歌"记进来。
     */
    val MUSIC_PACKAGES: Set<String> = setOf(
        "com.luna.music",                    // 汽水音乐
        "com.netease.cloudmusic",            // 网易云音乐
        "com.tencent.qqmusic",               // QQ 音乐
        "com.kugou.android",                 // 酷狗音乐
        "com.kuwo.music",                    // 酷我音乐
        "com.migu.music",                    // 咪咕音乐
        "com.spotify.music",                 // Spotify
        "com.apple.android.music",           // Apple Music
        "com.google.android.apps.youtube.music", // YouTube Music
        "com.ximalaya.ting.android",         // 喜马拉雅（音频）
        "com.tencent.karaoke",               // 全民K歌
    )

    fun isMusicApp(packageName: String): Boolean = packageName in MUSIC_PACKAGES

    /** "听了 2:13" 这种时长文案。 */
    fun listenedLabel(ms: Long): String {
        if (ms <= 0) return ""
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "听了 $min:${sec.toString().padStart(2, '0')}"
    }
}
