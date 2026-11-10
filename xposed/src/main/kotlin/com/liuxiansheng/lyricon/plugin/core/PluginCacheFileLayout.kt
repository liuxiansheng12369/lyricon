/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.hyper.plugin.core

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Private SystemUI cache layout shared by the runtime and the App's Root inspector. */
internal object PluginCacheFileLayout {
    const val ROOT_DIRECTORY = "hyperlyric_plugin_cache"
    const val CACHE_FILE_EXTENSION = ".cache"

    private val pluginIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

    fun isValidPluginId(pluginId: String): Boolean = pluginIdPattern.matches(pluginId)

    fun directory(context: Context, pluginId: String): File {
        require(isValidPluginId(pluginId)) { "Invalid plugin cache directory id" }
        return File(File(context.filesDir, ROOT_DIRECTORY), pluginId)
    }

    fun rootRelativeDirectory(pluginId: String): String {
        require(isValidPluginId(pluginId)) { "Invalid plugin cache directory id" }
        return "files/$ROOT_DIRECTORY/$pluginId"
    }

    fun fileNameForKey(key: String): String = sha256(key) + CACHE_FILE_EXTENSION

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
