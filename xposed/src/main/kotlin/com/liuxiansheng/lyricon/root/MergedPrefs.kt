/*
 * Lyricon V3 - Copyright (C) 2026 liuxiansheng
 * Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * 双层配置读取（V3 设置持久化修复）。
 *
 * 真机实证：libxposed 的 remote prefs 是内存态——App 侧设置写入只进
 * SystemUI 进程内存，SystemUI 重启 / 重装 APK 后 remote 清空，所有设置
 * 回默认（SystemUI 的 shared_prefs 下没有 V3 的落盘文件）。
 *
 * 修复：App 侧 MasterPrefs 双写（remote 实时生效 + App 本地文件持久化），
 * 并用 su 把本地文件同步到 SystemUI 的 shared_prefs 落盘路径；
 * SystemUI 侧这里做双层读取：
 * - primary  = remote prefs（内存）：运行时的实时值；
 * - fallback = SystemUI 进程本地文件：SystemUI 重启后 remote 清空，
 *   所有已保存设置从本地文件自动恢复。
 */
class MergedPrefs(
    private val primary: SharedPreferences,
    private val fallback: SharedPreferences
) : SharedPreferences {

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        if (primary.contains(key)) primary.getBoolean(key, defValue)
        else fallback.getBoolean(key, defValue)

    override fun getInt(key: String, defValue: Int): Int =
        if (primary.contains(key)) primary.getInt(key, defValue)
        else fallback.getInt(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float =
        if (primary.contains(key)) primary.getFloat(key, defValue)
        else fallback.getFloat(key, defValue)

    override fun getLong(key: String, defValue: Long): Long =
        if (primary.contains(key)) primary.getLong(key, defValue)
        else fallback.getLong(key, defValue)

    override fun getString(key: String, defValue: String?): String? =
        if (primary.contains(key)) primary.getString(key, defValue)
        else fallback.getString(key, defValue)

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        if (primary.contains(key)) primary.getStringSet(key, defValues)
        else fallback.getStringSet(key, defValues)

    override fun contains(key: String): Boolean =
        primary.contains(key) || fallback.contains(key)

    override fun getAll(): MutableMap<String, *> {
        val map = ConcurrentHashMap<String, Any?>()
        // fallback 先填，primary 覆盖：实时值优先于重启恢复值
        fallback.all.forEach { (k, v) -> map[k] = v }
        primary.all.forEach { (k, v) -> map[k] = v }
        return map
    }

    override fun edit(): SharedPreferences.Editor {
        // SystemUI 进程只读（对齐老词幕：写入抛 Read only implementation，
        // App 侧 MasterPrefs 是唯一写入者）
        throw UnsupportedOperationException("Read only implementation")
    }

    override fun registerOnSharedPreferenceChangeListener(
        l: SharedPreferences.OnSharedPreferenceChangeListener
    ) = primary.registerOnSharedPreferenceChangeListener(l)

    override fun unregisterOnSharedPreferenceChangeListener(
        l: SharedPreferences.OnSharedPreferenceChangeListener
    ) = primary.unregisterOnSharedPreferenceChangeListener(l)
}
