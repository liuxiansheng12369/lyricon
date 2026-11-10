/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * ProviderPrefsBridge：B1 暂停防冻结配置的跨进程 prefs 桥。
 *
 * B1 配置存在 provider（Pro 端）自己的 b1_config.properties，通过 ContentProvider
 * （com.liuxiansheng.lyricon.provider.b1config）跨进程读写：
 * - 本桥（V3 歌词源管理页）用 ContentResolver 写 enable / minutes（写多少保多少，不改默认）
 * - 音乐 App 进程（KeepSessionAlive hook）读同一接口生效
 * 写成功后发广播通知音乐 App 立即刷新当前暂停的保持时长。
 *
 * 仅承载 B1 的两个 key（暂停防冻结开关 + 保持时长），其他 key 回退默认。
 */
package com.liuxiansheng.lyricon.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri

object ProviderPrefsBridge : PrefsBridge {

    private const val AUTHORITY = "com.liuxiansheng.lyricon.provider.b1config"
    private const val COL_ENABLE = "enable"
    private const val COL_MINUTES = "minutes"
    private const val ACTION_CONFIG_CHANGED = "com.liuxiansheng.lyricon.b1config.changed"
    private val uri = Uri.parse("content://$AUTHORITY")

    private fun isEnableKey(key: String) = key == MasterPrefs.KEY_LYRIC_SOURCE_KEEP_SESSION_ON_PAUSE
    private fun isMinutesKey(key: String) = key == MasterPrefs.KEY_LYRIC_SOURCE_KEEP_SESSION_MINUTES

    /** 读 provider 侧 B1 配置：enable / minutes。 */
    private fun queryValues(context: Context): Pair<Boolean, Int> {
        return try {
            var enable = false
            var minutes = 15
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ei = c.getColumnIndex(COL_ENABLE)
                    val mi = c.getColumnIndex(COL_MINUTES)
                    if (ei >= 0) enable = c.getInt(ei) != 0
                    if (mi >= 0) minutes = c.getInt(mi)
                }
            }
            enable to minutes
        } catch (e: Exception) {
            false to 15
        }
    }

    private fun updateValues(context: Context, values: ContentValues) {
        try {
            val resolver = context.contentResolver
            // B1 配置若 provider 侧还没有可更新行（首用/被清空），update 会 0 行受影响，
            // 值根本没写进 b1_config → 开关"开着但不保持"；必须补一次 insert 兜底
            val affected = resolver.update(uri, values, null, null)
            if (affected == 0) {
                resolver.insert(uri, values)
            }
            // 通知音乐 App 立即刷新（ContentObserver / 广播）
            context.sendBroadcast(Intent(ACTION_CONFIG_CHANGED))
        } catch (e: Exception) {
            // 不再完全静默：provider 未连通时打日志，便于定位"开关不保持"
            android.util.Log.w("ProviderPrefsBridge", "b1config write failed", e)
        }
    }

    override fun contains(context: Context, key: String): Boolean =
        isEnableKey(key) || isMinutesKey(key)

    override fun getBoolean(context: Context, key: String, default: Boolean): Boolean =
        if (isEnableKey(key)) queryValues(context).first else default

    override fun setBoolean(context: Context, key: String, value: Boolean) {
        if (isEnableKey(key)) updateValues(context, ContentValues().apply { put(COL_ENABLE, value) })
    }

    override fun getInt(context: Context, key: String, default: Int): Int =
        if (isMinutesKey(key)) queryValues(context).second else default

    override fun setInt(context: Context, key: String, value: Int) {
        if (isMinutesKey(key)) updateValues(context, ContentValues().apply { put(COL_MINUTES, value) })
    }

    override fun getFloat(context: Context, key: String, default: Float): Float = default
    override fun setFloat(context: Context, key: String, value: Float) = Unit
    override fun getString(context: Context, key: String, default: String?): String? = default
    override fun setString(context: Context, key: String, value: String?) = Unit
}
