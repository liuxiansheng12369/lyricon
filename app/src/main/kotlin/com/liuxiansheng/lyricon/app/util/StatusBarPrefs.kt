/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * StatusBarPrefs：状态栏歌词【独立】prefs 桥。
 * 与超级岛设置完全分开：独立文件 `com.liuxiansheng.lyricon_status_bar_preferences`，
 * 独立读写、独立控制，不受超级岛总开关 gate。
 * - 读：本进程刚写的值 → remote（SystemUI 实际读取）→ 本地同名文件
 * - 写：remote 实时生效 + 本地文件持久镜像
 *   （2026-10-29 修复：此前服务绑定时只写 remote，本地文件长期不更新）
 */

package com.liuxiansheng.lyricon.app.util

import android.content.Context
import android.content.SharedPreferences
import com.liuxiansheng.lyricon.app.LyriconApp

object StatusBarPrefs : PrefsBridge {

    /** 状态栏歌词独立 prefs 文件（与超级岛 `MasterPrefs.PREF_NAME` 不同） */
    const val PREF_NAME = "com.liuxiansheng.lyricon_status_bar_preferences"

    /** 状态栏歌词总开关（与超级岛同键名，但存在独立文件里，互不影响） */
    const val KEY_HOOK_ENABLE_STATUS_BAR_LYRIC = "key_hook_enable_status_bar_lyric"

    /** 本进程刚写入的值：remote 的 apply 是异步提交，保证"写后立即读"拿到新值。 */
    private val recentWrites = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** 服务未绑定期间只写进本地文件的键，绑定后补推 remote。 */
    private val pendingRemote = java.util.concurrent.ConcurrentHashMap<String, Any>()

    private fun localPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun remotePrefs(context: Context): SharedPreferences? {
        val remote = runCatching {
            LyriconApp.xposedService?.getRemotePreferences(PREF_NAME)
        }.getOrNull() ?: return null
        if (pendingRemote.isNotEmpty()) {
            val editor = remote.edit()
            pendingRemote.forEach { (key, value) -> editor.putValue(key, value) }
            runCatching { editor.apply() }
            pendingRemote.clear()
        }
        return remote
    }

    private fun SharedPreferences.Editor.putValue(key: String, value: Any): SharedPreferences.Editor =
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Float -> putFloat(key, value)
            is Long -> putLong(key, value)
            is String -> putString(key, value)
            else -> this
        }

    private fun write(context: Context, key: String, value: Any?) {
        val remote = remotePrefs(context)
        if (value == null) {
            recentWrites.remove(key)
            pendingRemote.remove(key)
            if (remote != null) runCatching { remote.edit().remove(key).apply() }
        } else {
            recentWrites[key] = value
            if (remote != null) {
                runCatching { remote.edit().putValue(key, value).apply() }
            } else {
                pendingRemote[key] = value
            }
        }
        runCatching {
            val editor = localPrefs(context).edit()
            if (value == null) editor.remove(key) else editor.putValue(key, value)
            editor.apply()
        }
    }

    override fun contains(context: Context, key: String): Boolean =
        recentWrites.containsKey(key) ||
            remotePrefs(context)?.contains(key) == true ||
            localPrefs(context).contains(key)

    override fun getBoolean(context: Context, key: String, default: Boolean): Boolean {
        (recentWrites[key] as? Boolean)?.let { return it }
        val remote = remotePrefs(context)
        if (remote != null && remote.contains(key)) {
            return runCatching { remote.getBoolean(key, default) }.getOrDefault(default)
        }
        return runCatching { localPrefs(context).getBoolean(key, default) }.getOrDefault(default)
    }

    override fun getInt(context: Context, key: String, default: Int): Int {
        (recentWrites[key] as? Int)?.let { return it }
        val remote = remotePrefs(context)
        if (remote != null && remote.contains(key)) {
            return runCatching { remote.getInt(key, default) }.getOrDefault(default)
        }
        return runCatching { localPrefs(context).getInt(key, default) }.getOrDefault(default)
    }

    override fun getFloat(context: Context, key: String, default: Float): Float {
        (recentWrites[key] as? Number)?.let { return it.toFloat() }
        val remote = remotePrefs(context)
        if (remote != null && remote.contains(key)) {
            return runCatching { remote.getFloat(key, default) }.getOrDefault(default)
        }
        return runCatching { localPrefs(context).getFloat(key, default) }.getOrDefault(default)
    }

    override fun getString(context: Context, key: String, default: String?): String? {
        val cached = recentWrites[key]
        if (cached != null) return cached as? String ?: default
        val remote = remotePrefs(context)
        if (remote != null && remote.contains(key)) {
            return runCatching { remote.getString(key, default) }.getOrDefault(default)
        }
        return runCatching { localPrefs(context).getString(key, default) }.getOrDefault(default)
    }

    override fun setBoolean(context: Context, key: String, value: Boolean) = write(context, key, value)
    override fun setInt(context: Context, key: String, value: Int) = write(context, key, value)
    override fun setFloat(context: Context, key: String, value: Float) = write(context, key, value)
    override fun setString(context: Context, key: String, value: String?) = write(context, key, value)

    /** 服务绑定后调用一次：用 remote 回填本地文件（文件是持久副本）。 */
    fun syncFromRemote(context: Context) {
        val remote = remotePrefs(context) ?: return
        val local = localPrefs(context)
        val existing = local.all
        val editor = local.edit()
        var changed = false
        remote.all.forEach { (key, value) ->
            if (value != null && existing[key] != value) {
                editor.putValue(key, value)
                changed = true
            }
        }
        if (changed) runCatching { editor.apply() }
    }
}
