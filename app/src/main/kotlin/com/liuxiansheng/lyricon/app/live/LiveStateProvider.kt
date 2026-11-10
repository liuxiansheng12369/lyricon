/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.app.live

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 接收 SystemUI（模块侧 LiveStatePusher）推送的"正在播放"实时状态。
 *
 * 只允许 system uid / root 写入（其它应用即使知道 authority 也写不进来）。
 * 数据落到 App 自己的 prefs：
 *  - `live_state`：最新一帧（歌名/歌手/是否在播/包名/当前歌词/下一句）
 *  - `listen_records`：听歌记录（换歌时追加，最多 200 条）
 */
class LiveStateProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_PUBLISH) return null
        val uid = Binder.getCallingUid()
        if (!isTrustedCaller(uid)) {
            Log.w(TAG, "拒绝非系统来源写入 uid=$uid")
            return null
        }
        val context = context ?: return null
        val title = extras?.getString(KEY_TITLE).orEmpty()
        val artist = extras?.getString(KEY_ARTIST).orEmpty()
        val playing = extras?.getBoolean(KEY_PLAYING, false) ?: false
        val pkg = extras?.getString(KEY_PKG).orEmpty()
        val lyric = extras?.getString(KEY_LYRIC).orEmpty()
        val next = extras?.getString(KEY_NEXT).orEmpty()
        val song = extras?.getString(KEY_SONG).orEmpty()

        val isFinalize = extras?.getBoolean(KEY_FINALIZE, false) ?: false

        // 播放结算帧：只用来记听歌历史，不改实时状态（那一帧可能已经换歌了）
        if (isFinalize) {
            runCatching {
                acceptFinalize(
                    context = context,
                    title = title,
                    artist = artist,
                    pkg = pkg,
                    startedAt = extras?.getLong(KEY_F_STARTED_AT, 0L) ?: 0L,
                    listenedMs = extras?.getLong(KEY_F_LISTENED_MS, 0L) ?: 0L,
                    durationMs = extras?.getLong(KEY_F_DURATION_MS, 0L) ?: 0L,
                )
            }.onFailure { Log.w(TAG, "写入听歌记录失败", it) }
            return Bundle().apply { putBoolean("accepted", true) }
        }

        runCatching {
            livePrefs(context).edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_ARTIST, artist)
                .putBoolean(KEY_PLAYING, playing)
                .putString(KEY_PKG, pkg)
                .putString(KEY_LYRIC, lyric)
                .putString(KEY_NEXT, next)
                .putString(KEY_SONG, song)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply()
        }.onFailure { Log.w(TAG, "写入实时状态失败", it) }

        return Bundle().apply { putBoolean("accepted", true) }
    }

    /**
     * 播放结算：按 Last.fm 的 scrobble 口径判断"算不算一次有效播放"。
     *  - 曲长 < 30 秒的（短片/电台 jingle）不记
     *  - 实际听了 ≥ 一半，或 ≥ 4 分钟（长曲封顶）才算
     *  - 同一首 30 分钟内重复出现，只把记录移到最前并累加时长，不新增一条
     */
    private fun acceptFinalize(
        context: Context,
        title: String,
        artist: String,
        pkg: String,
        startedAt: Long,
        listenedMs: Long,
        durationMs: Long,
    ) {
        if (title.isBlank() || title == NOT_PLAYING) {
            Log.d(TAG, "结算忽略: 空歌名 title=$title")
            return
        }
        if (!recordsEnabled(context)) {
            Log.d(TAG, "结算忽略: 自动记录关闭 title=$title")
            return
        }
        if (!ListenRecordsStore.isMusicApp(pkg)) {
            Log.d(TAG, "结算忽略: 非音乐类 App pkg=$pkg title=$title")
            return
        }
        if (durationMs < MIN_TRACK_MS) {
            Log.d(TAG, "结算忽略: 曲长不足 title=$title dur=${durationMs}ms")
            return
        }
        val need = minOf(durationMs / 2, MAX_COUNT_MS)
        // 位置回调偶尔会超过曲长（拖动进度条/网络续播），按曲长封顶
        val listened = if (durationMs > 0) minOf(listenedMs, durationMs) else listenedMs
        if (listened < need) {
            Log.d(TAG, "结算忽略: 听得不够 title=$title listened=${listened}ms need=${need}ms dur=${durationMs}ms")
            return
        }
        Log.d(TAG, "结算收录: title=$title listened=${listened}ms dur=${durationMs}ms")

        val now = System.currentTimeMillis()
        val prefs = recordsPrefs(context)
        val raw = prefs.getString(KEY_RECORDS, null)
        val array = runCatching { JSONArray(raw ?: "[]") }.getOrDefault(JSONArray())

        val newArray = JSONArray()
        var merged = false
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (!merged &&
                o.optString("title") == title &&
                o.optString("artist") == artist &&
                now - o.optLong("time", 0L) < SAME_TRACK_GAP_MS
            ) {
                // 同一首：合并成一条（时长累加，播放时间取最近一次）
                o.put("time", now)
                o.put("lastTime", now)
                o.put("listenedMs", minOf(o.optLong("listenedMs", 0L) + listened, durationMs))
                o.put("durationMs", durationMs)
                if (pkg.isNotBlank()) o.put("package", pkg)
                newArray.put(o)
                merged = true
            } else {
                newArray.put(o)
            }
        }
        if (!merged) {
            val record = JSONObject().apply {
                put("title", title)
                put("artist", artist)
                put("package", pkg)
                put("time", if (startedAt > 0) startedAt else now)
                put("lastTime", now)
                put("listenedMs", listened)
                put("durationMs", durationMs)
            }
            val withNew = JSONArray()
            withNew.put(record)
            for (i in 0 until minOf(newArray.length(), MAX_RECORDS - 1)) {
                withNew.put(newArray.opt(i))
            }
            prefs.edit().putString(KEY_RECORDS, withNew.toString()).apply()
            return
        }
        // 合并后按时间倒序重排，保证刚听的在最前
        val sorted = (0 until newArray.length())
            .mapNotNull { newArray.optJSONObject(it) }
            .sortedByDescending { it.optLong("time") }
        val result = JSONArray()
        for (i in 0 until minOf(sorted.size, MAX_RECORDS)) result.put(sorted[i])
        prefs.edit().putString(KEY_RECORDS, result.toString()).apply()
    }

    private fun recordsEnabled(context: Context): Boolean =
        recordsPrefs(context).getBoolean(KEY_ENABLED, true)

    /**
     * 只认 root 或 SystemUI 进程写入。
     * 真机上 SystemUI 不是 uid 1000（澎湃 OS 里是普通应用 uid），
     * 所以按"调用 uid 是否属于 com.android.systemui"判断更可靠。
     */
    private fun isTrustedCaller(uid: Int): Boolean {
        if (uid == Process.ROOT_UID || uid == Process.SYSTEM_UID) return true
        val context = context ?: return false
        val packages = runCatching {
            context.packageManager.getPackagesForUid(uid)
        }.getOrNull() ?: return false
        return packages.any { it == "com.android.systemui" }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val TAG = "LiveStateProvider"
        private const val METHOD_PUBLISH = "publish_live"

        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_PLAYING = "playing"
        const val KEY_PKG = "package"
        const val KEY_LYRIC = "lyric"
        const val KEY_NEXT = "next"
        const val KEY_SONG = "song"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_RECORDS = "records"
        const val KEY_ENABLED = "records_enabled"

        const val KEY_FINALIZE = "finalize"
        const val KEY_F_STARTED_AT = "f_started_at"
        const val KEY_F_LISTENED_MS = "f_listened_ms"
        const val KEY_F_DURATION_MS = "f_duration_ms"

        const val LIVE_PREFS = "live_state"
        const val RECORDS_PREFS = "listen_records"

        const val NOT_PLAYING = "未在播放"
        const val MAX_RECORDS = 200

        /** 短于 30 秒的音频不当成一首歌记。 */
        private const val MIN_TRACK_MS = 30_000L

        /** 一次播放最多按 4 分钟计（Last.fm 口径）。 */
        private const val MAX_COUNT_MS = 4 * 60 * 1000L

        /** 同一首歌两次出现在记录里、间隔小于 30 分钟，视为同一次播放。 */
        private const val SAME_TRACK_GAP_MS = 30 * 60 * 1000L

        fun livePrefs(context: Context) =
            context.getSharedPreferences(LIVE_PREFS, Context.MODE_PRIVATE)

        fun recordsPrefs(context: Context) =
            context.getSharedPreferences(RECORDS_PREFS, Context.MODE_PRIVATE)
    }
}