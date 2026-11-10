/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.lyricon.common

import android.content.SharedPreferences

interface StateSharedPreferences : SharedPreferences {
    /**
     * 检测是否发生了变更
     */
    fun hasChanged(): Boolean

    /**
     * 重新加载数据。
     */
    fun reload(): Boolean
}

/**
 * 一个包装类，将标准的 SharedPreferences 转换为 StateSharedPreferences。
 * 由于标准实现通常不暴露变更检测逻辑，故 hasChanged 和 reload 默认返回 false。
 */
open class StateSharedPreferencesWrapper(private val preferences: SharedPreferences) :
    StateSharedPreferences {

    override fun hasChanged(): Boolean = false

    override fun reload(): Boolean = false

    override fun contains(key: String?): Boolean = preferences.contains(key)

    override fun edit(): SharedPreferences.Editor = preferences.edit()

    override fun getAll(): Map<String, *> = preferences.all

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        preferences.getBoolean(key, defValue)

    override fun getFloat(key: String?, defValue: Float): Float =
        preferences.getFloat(key, defValue)

    override fun getInt(key: String?, defValue: Int): Int =
        preferences.getInt(key, defValue)

    override fun getLong(key: String?, defValue: Long): Long =
        preferences.getLong(key, defValue)

    override fun getString(key: String?, defValue: String?): String? =
        preferences.getString(key, defValue)

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        preferences.getStringSet(key, defValues)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}