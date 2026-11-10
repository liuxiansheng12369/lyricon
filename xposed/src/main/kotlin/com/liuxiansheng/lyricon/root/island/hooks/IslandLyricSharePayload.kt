/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.hooks

import android.content.SharedPreferences
import android.view.View
import com.liuxiansheng.lyricon.hyper.common.media.MediaMetadataHelper
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.content.IslandMetadataContentAssembler
import com.liuxiansheng.lyricon.hyper.root.media.CurrentMediaInfoResolver
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger

internal data class IslandLyricSharePayload(
    val title: String,
    val content: String,
    val shareContent: String
)

internal object IslandLyricSharePayloadBuilder {

    fun build(view: View, prefs: SharedPreferences): IslandLyricSharePayload? {
        val packageName = LyriconDataBridge.currentLyricPackageName
            ?.takeIf(String::isNotBlank)
            ?: return null
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(
            context = view.context,
            packageName = packageName,
            logger = HookLogger
        )
        val lines = IslandMetadataContentAssembler.buildConfiguredMusicInfoLines(
            prefs = prefs,
            mediaInfo = mediaInfo
        )
        val currentLyric = currentLyricText() ?: return null
        val shareFields = listOfNotNull(
            lines.firstLine.takeIf(String::isNotBlank),
            lines.secondLine.takeIf(String::isNotBlank),
            formatField("当前歌词", currentLyric)
        )
        if (shareFields.isEmpty()) return null

        return IslandLyricSharePayload(
            title = lines.firstLine,
            content = lines.secondLine,
            shareContent = shareFields.joinToString("\n")
        )
    }

    private fun currentLyricText(): String? {
        return LyriconDataBridge.currentLyric
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: LyriconDataBridge.currentLyricLine?.let { line ->
                line.text?.trim()?.takeIf(String::isNotEmpty)
                    ?: line.words
                        ?.joinToString("") { word -> word.text.orEmpty() }
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
            }
    }

    private fun formatField(label: String, value: String?): String? {
        return value
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { "$label：$it" }
    }
}
