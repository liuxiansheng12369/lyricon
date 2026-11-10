/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.content

import android.content.SharedPreferences
import android.view.View
import com.liuxiansheng.lyricon.hyper.common.MusicInfoLayoutPolicy
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.common.media.MediaMetadataHelper
import com.liuxiansheng.lyricon.hyper.lyric.model.RichLyricLine
import com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView
import com.liuxiansheng.lyricon.lyric.render.view.SpaceGateRichLyricLineView
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * 元数据内容组装器（V3 自研）。
 *
 * 在歌词未到时显示歌名 / 作者 / 专辑 / 时长（自定义布局可配字段），
 * 播放中按秒更新已播放 / 剩余 / 进度百分比，并支持歌名跑马灯。
 */
internal object IslandMetadataContentAssembler {

    private val dynamicFields = setOf(
        MusicInfoLayoutPolicy.FIELD_ELAPSED,
        MusicInfoLayoutPolicy.FIELD_REMAINING,
        MusicInfoLayoutPolicy.FIELD_PROGRESS_PERCENT
    )
    private val metadataStates = WeakHashMap<View, MetadataState>()

    private data class MetadataState(
        val mode: Int,
        val firstLineFields: List<String>,
        val secondLineFields: List<String>,
        val dynamicFieldOrder: List<String>,
        val separator: String,
        val fieldValues: MutableMap<String, String>,
        val durationMs: Long,
        var dynamicSignature: String,
        var lastElapsedSecond: Long,
        var lastRemainingSecond: Long,
        var lastProgressPercent: Int
    )

    data class ConfiguredMusicInfoLines(
        val firstLine: String,
        val secondLine: String
    )

    /**
     * 解析与元数据渲染器相同的自定义音乐信息行。
     * 拖拽分享也用它，保证分享标题/内容与岛上显示一致。
     */
    fun buildConfiguredMusicInfoLines(
        prefs: SharedPreferences,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        playbackPosition: Long = LyriconDataBridge.currentPosition,
        playbackDuration: Long = mediaInfo.duration
    ): ConfiguredMusicInfoLines {
        val songName = mediaInfo.title.takeIf { it.isNotBlank() }
            ?: LyriconDataBridge.currentSongName.orEmpty()
        val durationText = mediaInfo.duration.takeIf { it > 0L }
            ?.let { formatMediaTime(it, it) }
            .orEmpty()
        val resolvedDuration = playbackDuration.takeIf { it > 0L } ?: mediaInfo.duration
        val firstLineFields = MusicInfoLayoutPolicy.readFields(
            prefs,
            RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE,
            MusicInfoLayoutPolicy.defaultFirstLine
        )
        val secondLineFields = MusicInfoLayoutPolicy.readFields(
            prefs,
            RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE,
            MusicInfoLayoutPolicy.defaultSecondLine
        )
        val dynamicFieldOrder = readDynamicFields(firstLineFields, secondLineFields)
        val fieldValues = linkedMapOf(
            MusicInfoLayoutPolicy.FIELD_TITLE to songName,
            MusicInfoLayoutPolicy.FIELD_ARTIST to mediaInfo.artist,
            MusicInfoLayoutPolicy.FIELD_ALBUM to mediaInfo.album,
            MusicInfoLayoutPolicy.FIELD_DURATION to durationText
        ).apply {
            if (dynamicFieldOrder.isNotEmpty()) {
                putAll(buildPlaybackFieldValues(playbackPosition, resolvedDuration))
            }
        }
        val separator = MusicInfoLayoutPolicy.separatorValue(
            MusicInfoLayoutPolicy.readSeparator(prefs)
        )
        return ConfiguredMusicInfoLines(
            firstLine = joinFields(firstLineFields, fieldValues, separator),
            secondLine = joinFields(secondLineFields, fieldValues, separator)
        )
    }

    /**
     * 提交元数据内容到视图。
     * 自定义布局模式按字段拼行；否则仅由调用方决定（模式非自定义时 newLine 为空）。
     */
    fun apply(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        force: Boolean,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        playbackPosition: Long = LyriconDataBridge.currentPosition,
        playbackDuration: Long = mediaInfo.duration
    ): Boolean {
        val songName = mediaInfo.title.takeIf { it.isNotBlank() }
            ?: LyriconDataBridge.currentSongName.orEmpty()
        val artistName = mediaInfo.artist
        val albumName = mediaInfo.album
        val durationText = mediaInfo.duration.takeIf { it > 0L }
            ?.let { formatMediaTime(it, it) }
            .orEmpty()
        val resolvedDuration = playbackDuration.takeIf { it > 0L } ?: mediaInfo.duration
        val customLayout = mode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO
        val firstLineFields = if (customLayout) {
            MusicInfoLayoutPolicy.readFields(
                prefs,
                RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE,
                MusicInfoLayoutPolicy.defaultFirstLine
            )
        } else {
            emptyList()
        }
        val secondLineFields = if (customLayout) {
            MusicInfoLayoutPolicy.readFields(
                prefs,
                RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE,
                MusicInfoLayoutPolicy.defaultSecondLine
            )
        } else {
            emptyList()
        }
        val separator = if (customLayout) {
            MusicInfoLayoutPolicy.readSeparator(prefs)
        } else {
            MusicInfoLayoutPolicy.SEPARATOR_HYPHEN
        }
        val dynamicFieldOrder = readDynamicFields(firstLineFields, secondLineFields)
        val fieldValues = linkedMapOf(
            MusicInfoLayoutPolicy.FIELD_TITLE to songName,
            MusicInfoLayoutPolicy.FIELD_ARTIST to artistName,
            MusicInfoLayoutPolicy.FIELD_ALBUM to albumName,
            MusicInfoLayoutPolicy.FIELD_DURATION to durationText
        ).apply {
            if (dynamicFieldOrder.isNotEmpty()) {
                putAll(buildPlaybackFieldValues(playbackPosition, resolvedDuration))
            }
        }
        val state = MetadataState(
            mode = mode,
            firstLineFields = firstLineFields,
            secondLineFields = secondLineFields,
            dynamicFieldOrder = dynamicFieldOrder,
            separator = separator,
            fieldValues = fieldValues,
            durationMs = resolvedDuration,
            dynamicSignature = dynamicSignature(
                dynamicFieldOrder,
                fieldValues
            ),
            lastElapsedSecond = elapsedDisplaySecond(playbackPosition),
            lastRemainingSecond = remainingDisplaySecond(playbackPosition, resolvedDuration),
            lastProgressPercent = progressDisplayPercent(playbackPosition, resolvedDuration)
        )
        val newLine = if (customLayout) {
            buildCustomLine(
                firstLineFields = firstLineFields,
                secondLineFields = secondLineFields,
                fieldValues = fieldValues,
                separator = separator
            )
        } else {
            null
        }
        val previousState = synchronized(metadataStates) { metadataStates[view] }
        val signature = buildSignature(state)
        synchronized(metadataStates) {
            metadataStates[view] = state
        }

        if (!force && IslandSlotContentSignatureCache.get(view) == signature) {
            if (previousState?.dynamicSignature != state.dynamicSignature) {
                applyLine(view, newLine, preserveDisplay = true)
                return true
            }
            return false
        }

        applyLine(view, newLine)
        IslandSlotContentSignatureCache.set(view, signature)
        val viewKey = view.tag?.toString() ?: view.javaClass.simpleName
        val debugState = listOf(
            mode,
            customLayout,
            newLine != null,
            firstLineFields.joinToString(","),
            secondLineFields.joinToString(","),
            separator,
            force
        ).joinToString("|")
        HookLogger.dState(
            stateId = "IslandMetadataContentAssembler:$viewKey",
            tag = "IslandMetadataContentAssembler",
            state = debugState
        ) {
            "媒体信息内容已提交: tag=$viewKey, mode=$mode, customLayout=$customLayout, " +
                    "line=${newLine != null}, " +
                    "firstFields=${firstLineFields.joinToString(",")}, " +
                    "secondFields=${secondLineFields.joinToString(",")}, separator=$separator, " +
                    "force=$force"
        }
        return true
    }

    /**
     * 只更新已渲染元数据视图的动态字段（已播放 / 剩余 / 进度百分比）。
     * 字段顺序与元数据快照来自最近一次完整元数据更新。
     */
    fun updatePlaybackProgress(view: View, position: Long): Boolean {
        val state = synchronized(metadataStates) { metadataStates[view] } ?: return false
        if (state.mode != RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO) return false

        if (state.dynamicFieldOrder.isEmpty()) return false

        val timeFormatReference = if (
            MusicInfoLayoutPolicy.FIELD_ELAPSED in state.dynamicFieldOrder ||
            MusicInfoLayoutPolicy.FIELD_REMAINING in state.dynamicFieldOrder
        ) {
            maxOf(position, state.durationMs, 0L)
        } else {
            0L
        }
        var contentChanged = false

        if (MusicInfoLayoutPolicy.FIELD_ELAPSED in state.dynamicFieldOrder) {
            val elapsedSecond = elapsedDisplaySecond(position)
            if (elapsedSecond != state.lastElapsedSecond) {
                state.lastElapsedSecond = elapsedSecond
                val elapsed = if (elapsedSecond == INVALID_DISPLAY_SECOND) {
                    ""
                } else {
                    formatMediaTime(position, timeFormatReference)
                }
                if (state.fieldValues[MusicInfoLayoutPolicy.FIELD_ELAPSED] != elapsed) {
                    state.fieldValues[MusicInfoLayoutPolicy.FIELD_ELAPSED] = elapsed
                    contentChanged = true
                }
            }
        }

        if (MusicInfoLayoutPolicy.FIELD_REMAINING in state.dynamicFieldOrder) {
            val remainingSecond = remainingDisplaySecond(position, state.durationMs)
            if (remainingSecond != state.lastRemainingSecond) {
                state.lastRemainingSecond = remainingSecond
                val remaining = if (remainingSecond == INVALID_DISPLAY_SECOND) {
                    ""
                } else {
                    formatMediaTime(
                        (state.durationMs - position).coerceAtLeast(0L),
                        timeFormatReference
                    )
                }
                if (state.fieldValues[MusicInfoLayoutPolicy.FIELD_REMAINING] != remaining) {
                    state.fieldValues[MusicInfoLayoutPolicy.FIELD_REMAINING] = remaining
                    contentChanged = true
                }
            }
        }

        if (MusicInfoLayoutPolicy.FIELD_PROGRESS_PERCENT in state.dynamicFieldOrder) {
            val progressPercent = progressDisplayPercent(position, state.durationMs)
            if (progressPercent != state.lastProgressPercent) {
                state.lastProgressPercent = progressPercent
                val progress = if (progressPercent == INVALID_PROGRESS_PERCENT) {
                    ""
                } else {
                    "$progressPercent%"
                }
                if (state.fieldValues[MusicInfoLayoutPolicy.FIELD_PROGRESS_PERCENT] != progress) {
                    state.fieldValues[MusicInfoLayoutPolicy.FIELD_PROGRESS_PERCENT] = progress
                    contentChanged = true
                }
            }
        }

        if (!contentChanged) return false

        state.dynamicSignature = dynamicSignature(
            state.dynamicFieldOrder,
            state.fieldValues
        )
        applyLine(
            view,
            buildCustomLine(
                firstLineFields = state.firstLineFields,
                secondLineFields = state.secondLineFields,
                fieldValues = state.fieldValues,
                separator = state.separator
            ),
            preserveDisplay = true
        )
        IslandSlotContentSignatureCache.set(view, buildSignature(state))
        return true
    }

    private fun buildSignature(state: MetadataState): String {
        return listOf(
            "metadata",
            state.mode,
            state.fieldValues[MusicInfoLayoutPolicy.FIELD_TITLE].orEmpty(),
            state.fieldValues[MusicInfoLayoutPolicy.FIELD_ARTIST].orEmpty(),
            state.fieldValues[MusicInfoLayoutPolicy.FIELD_ALBUM].orEmpty(),
            state.fieldValues[MusicInfoLayoutPolicy.FIELD_DURATION].orEmpty(),
            state.firstLineFields.joinToString(","),
            state.secondLineFields.joinToString(","),
            state.separator
        ).joinToString("|")
    }

    private fun buildPlaybackFieldValues(
        position: Long,
        duration: Long
    ): Map<String, String> {
        val timeFormatReference = maxOf(position, duration, 0L)
        val elapsed = formatMediaTime(position, timeFormatReference)
        val remaining = if (position >= 0L && duration > 0L) {
            formatMediaTime((duration - position).coerceAtLeast(0L), timeFormatReference)
        } else {
            ""
        }
        val progressPercent = if (position >= 0L && duration > 0L) {
            val fraction = (position.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
            "${(fraction * 100.0).roundToInt()}%"
        } else {
            ""
        }
        return mapOf(
            MusicInfoLayoutPolicy.FIELD_ELAPSED to elapsed,
            MusicInfoLayoutPolicy.FIELD_REMAINING to remaining,
            MusicInfoLayoutPolicy.FIELD_PROGRESS_PERCENT to progressPercent
        )
    }

    private fun dynamicSignature(
        dynamicFieldOrder: List<String>,
        fieldValues: Map<String, String>
    ): String {
        return dynamicFieldOrder
            .joinToString("|") { field -> "$field=${fieldValues[field].orEmpty()}" }
    }

    private fun readDynamicFields(
        firstLineFields: List<String>,
        secondLineFields: List<String>
    ): List<String> {
        return (firstLineFields + secondLineFields)
            .filter { it in dynamicFields }
            .distinct()
    }

    private fun elapsedDisplaySecond(position: Long): Long {
        return if (position >= 0L) position / 1_000L else INVALID_DISPLAY_SECOND
    }

    private fun remainingDisplaySecond(position: Long, duration: Long): Long {
        return if (position >= 0L && duration > 0L) {
            (duration - position).coerceAtLeast(0L) / 1_000L
        } else {
            INVALID_DISPLAY_SECOND
        }
    }

    private fun progressDisplayPercent(position: Long, duration: Long): Int {
        if (position < 0L || duration <= 0L) return INVALID_PROGRESS_PERCENT
        val fraction = (position.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
        return (fraction * 100.0).roundToInt()
    }

    private fun buildCustomLine(
        firstLineFields: List<String>,
        secondLineFields: List<String>,
        fieldValues: Map<String, String>,
        separator: String
    ): RichLyricLine? {
        val separatorValue = MusicInfoLayoutPolicy.separatorValue(separator)
        val firstLine = joinFields(firstLineFields, fieldValues, separatorValue)
        val secondLine = joinFields(secondLineFields, fieldValues, separatorValue)

        return when {
            firstLine.isNotBlank() -> RichLyricLine(
                text = firstLine,
                words = emptyList(),
                secondary = secondLine.takeIf { it.isNotBlank() },
                secondaryWords = emptyList()
            )

            secondLine.isNotBlank() -> RichLyricLine(
                text = secondLine,
                words = emptyList()
            )

            else -> null
        }
    }

    private fun joinFields(
        fields: List<String>,
        fieldValues: Map<String, String>,
        separator: String
    ): String {
        return fields.mapNotNull { field ->
            fieldValues[field]?.takeIf { it.isNotBlank() }
        }.joinToString(separator)
    }

    private fun applyLine(
        view: View,
        line: RichLyricLine?,
        preserveDisplay: Boolean = false
    ) {
        when (view) {
            is RichLyricLineView -> if (preserveDisplay) {
                view.updateMetadataLine(line)
            } else {
                view.line = line
            }

            is SpaceGateRichLyricLineView -> if (preserveDisplay) {
                view.updateMetadataLine(line)
            } else {
                view.line = line
            }
        }
    }

    private fun formatMediaTime(valueMs: Long, referenceDurationMs: Long): String {
        if (valueMs < 0L) return ""

        val totalSeconds = valueMs / 1_000L
        val seconds = totalSeconds % 60L
        val showHours = referenceDurationMs >= HOUR_MS
        val minutes = if (showHours) {
            (totalSeconds / 60L) % 60L
        } else {
            totalSeconds / 60L
        }

        return if (showHours) {
            "${(totalSeconds / 3_600L).twoDigits()}:${minutes.twoDigits()}:${seconds.twoDigits()}"
        } else {
            "${minutes.twoDigits()}:${seconds.twoDigits()}"
        }
    }

    private fun Long.twoDigits(): String = toString().padStart(2, '0')

    private const val HOUR_MS = 3_600_000L
    private const val INVALID_DISPLAY_SECOND = Long.MIN_VALUE
    private const val INVALID_PROGRESS_PERCENT = Int.MIN_VALUE

    fun invalidate(view: View? = null) {
        IslandSlotContentSignatureCache.invalidate(view)
        synchronized(metadataStates) {
            if (view == null) {
                metadataStates.clear()
            } else {
                metadataStates.remove(view)
            }
        }
    }

    fun clearState(view: View) {
        synchronized(metadataStates) {
            metadataStates.remove(view)
        }
    }
}
