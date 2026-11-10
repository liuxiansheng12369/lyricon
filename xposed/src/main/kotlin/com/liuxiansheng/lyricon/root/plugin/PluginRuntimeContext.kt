/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.hyper.root.plugin

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.liuxiansheng.lyricon.hyper.plugin.api.HYPERLYRIC_PLUGIN_API_VERSION
import com.liuxiansheng.lyricon.hyper.plugin.api.HyperLyricExtension
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginCache
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginConfig
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginContext
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginLogger
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginStorage
import com.liuxiansheng.lyricon.hyper.plugin.core.PluginCacheFileLayout
import com.liuxiansheng.lyricon.hyper.plugin.core.PluginConstants
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger

/** Host-owned context and adapter implementations exposed to one loaded plugin. */
internal class RuntimePluginContext(
    override val pluginId: String,
    application: Application,
    preferences: SharedPreferences,
) : PluginContext {
    private val extensionLock = Any()
    private val extensions = mutableListOf<HyperLyricExtension>()

    override val hostApiVersion: Int = HYPERLYRIC_PLUGIN_API_VERSION
    override val config: PluginConfig = SharedPreferencesPluginConfig(preferences)
    override val logger: PluginLogger = RuntimePluginLogger(pluginId)
    override val cache: PluginCache = FilePluginCache(
        directory = PluginCacheFileLayout.directory(application, pluginId),
        legacyPreferences = application.getSharedPreferences(
            PluginConstants.cachePreferences(pluginId),
            Context.MODE_PRIVATE
        ),
        logger = logger.withTag("PluginCache")
    )
    override val storage: PluginStorage = SharedPreferencesPluginStorage(
        application.getSharedPreferences(
            PluginConstants.storagePreferences(pluginId),
            Context.MODE_PRIVATE
        )
    )

    override fun registerExtension(extension: HyperLyricExtension) {
        require(extension.id.isNotBlank()) { "Plugin extension id is blank" }
        synchronized(extensionLock) {
            require(extensions.none { it.id == extension.id }) {
                "Duplicate plugin extension id: ${extension.id}"
            }
            extensions += extension
        }
    }

    fun registeredExtensions(): List<HyperLyricExtension> = synchronized(extensionLock) {
        extensions.toList()
    }
}

private class SharedPreferencesPluginConfig(
    private val preferences: SharedPreferences
) : PluginConfig {
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun getString(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Long =
        preferences.getLong(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float =
        preferences.getFloat(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        preferences.getStringSet(key, defaultValue)?.toSet() ?: defaultValue
}

private class SharedPreferencesPluginStorage(
    private val preferences: SharedPreferences
) : PluginStorage {
    override fun getString(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }
}

private class RuntimePluginLogger(private val pluginId: String) : PluginLogger {
    private fun tag() = pluginId

    override fun debug(message: String) = HookLogger.d(tag(), message)

    override fun info(message: String) = HookLogger.i(tag(), message)

    override fun warn(message: String, throwable: Throwable?) {
        if (throwable == null) HookLogger.w(tag(), message) else HookLogger.w(tag(), message, throwable)
    }

    override fun error(message: String, throwable: Throwable?) {
        if (throwable == null) HookLogger.e(tag(), message) else HookLogger.e(tag(), message, throwable)
    }

    override fun withTag(tag: String): PluginLogger =
        TaggedRuntimePluginLogger(pluginId, tag)
}

private class TaggedRuntimePluginLogger(
    private val pluginId: String,
    private val componentTag: String,
) : PluginLogger {
    private val logTag = "$pluginId/$componentTag"

    override fun debug(message: String) = HookLogger.d(logTag, message)

    override fun info(message: String) = HookLogger.i(logTag, message)

    override fun warn(message: String, throwable: Throwable?) {
        if (throwable == null) HookLogger.w(logTag, message)
        else HookLogger.w(logTag, message, throwable)
    }

    override fun error(message: String, throwable: Throwable?) {
        if (throwable == null) HookLogger.e(logTag, message)
        else HookLogger.e(logTag, message, throwable)
    }

    override fun withTag(tag: String): PluginLogger =
        TaggedRuntimePluginLogger(pluginId, "$componentTag/$tag")
}
