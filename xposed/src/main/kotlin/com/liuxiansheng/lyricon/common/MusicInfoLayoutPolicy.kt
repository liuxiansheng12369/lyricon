/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common

import android.content.SharedPreferences

/**
 * 音乐信息布局策略（V3 自研）。
 */
object MusicInfoLayoutPolicy {
    const val FIELD_TITLE = "title"
    const val FIELD_ARTIST = "artist"
    const val FIELD_ALBUM = "album"
    const val FIELD_DURATION = "duration"
    const val FIELD_ELAPSED = "elapsed"
    const val FIELD_REMAINING = "remaining"
    const val FIELD_PROGRESS_PERCENT = "progress_percent"

    const val SEPARATOR_PLUS = "plus"
    const val SEPARATOR_SPACE = "space"
    const val SEPARATOR_COMMA = "comma"
    const val SEPARATOR_IDEOGRAPHIC_COMMA = "ideographic_comma"
    const val SEPARATOR_SLASH = "slash"
    const val SEPARATOR_HYPHEN = "hyphen"
    const val SEPARATOR_NONE = "none"

    val supportedFields: Set<String> = setOf(
        FIELD_TITLE,
        FIELD_ARTIST,
        FIELD_ALBUM,
        FIELD_DURATION,
        FIELD_ELAPSED,
        FIELD_REMAINING,
        FIELD_PROGRESS_PERCENT
    )

    val defaultFirstLine: List<String> = listOf(FIELD_TITLE)
    val defaultSecondLine: List<String> = listOf(FIELD_ARTIST)

    /** 读取字段列表（按存储顺序、去重、过滤非法字段）。 */
    fun readFields(
        prefs: SharedPreferences,
        key: String,
        defaultValue: List<String>
    ): List<String> {
        val storedValue = prefs.getString(key, null) ?: return defaultValue
        return storedValue
            .split(',')
            .map(String::trim)
            .filter { it in supportedFields }
            .distinct()
    }

    /** 分隔符已按用户要求移除设置项，固定使用默认连字符。 */
    fun readSeparator(prefs: SharedPreferences): String = SEPARATOR_HYPHEN

    /** 分隔符 ID → 显示文本。 */
    fun separatorValue(separator: String): String = when (separator) {
        SEPARATOR_PLUS -> " + "
        SEPARATOR_SPACE -> " "
        SEPARATOR_COMMA -> ", "
        SEPARATOR_IDEOGRAPHIC_COMMA -> "、"
        SEPARATOR_SLASH -> " / "
        SEPARATOR_NONE -> ""
        else -> " - "
    }

    fun isSupportedSeparator(separator: String): Boolean = separator in setOf(
        SEPARATOR_PLUS,
        SEPARATOR_SPACE,
        SEPARATOR_COMMA,
        SEPARATOR_IDEOGRAPHIC_COMMA,
        SEPARATOR_SLASH,
        SEPARATOR_HYPHEN,
        SEPARATOR_NONE
    )
}
