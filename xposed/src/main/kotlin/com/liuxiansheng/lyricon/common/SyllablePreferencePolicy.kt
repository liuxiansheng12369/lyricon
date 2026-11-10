/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common

import android.content.SharedPreferences

data class SyllablePreferenceState(
    val relativeProgress: Boolean,
    val relativeHighlight: Boolean,
    val lineDisplay: Boolean
)

/**
 * 逐字偏好策略（V3 自研）。
 */
object SyllablePreferencePolicy {
    fun read(prefs: SharedPreferences): SyllablePreferenceState = normalize(
        relativeProgress = prefs.getBoolean(
            RootConstants.KEY_HOOK_SYLLABLE_RELATIVE,
            RootConstants.DEFAULT_HOOK_SYLLABLE_RELATIVE
        ),
        relativeHighlight = prefs.getBoolean(
            RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT,
            RootConstants.DEFAULT_HOOK_SYLLABLE_HIGHLIGHT
        ),
        lineDisplay = prefs.getBoolean(
            RootConstants.KEY_HOOK_SYLLABLE_LINE_DISPLAY,
            RootConstants.DEFAULT_HOOK_SYLLABLE_LINE_DISPLAY
        )
    )

    /** 整行显示模式下相对进度强制 false。 */
    fun normalize(
        relativeProgress: Boolean,
        relativeHighlight: Boolean,
        lineDisplay: Boolean
    ): SyllablePreferenceState = if (lineDisplay) {
        SyllablePreferenceState(
            relativeProgress = false,
            relativeHighlight = relativeHighlight,
            lineDisplay = true
        )
    } else {
        SyllablePreferenceState(
            relativeProgress = relativeProgress,
            relativeHighlight = relativeHighlight,
            lineDisplay = false
        )
    }

    fun write(editor: SharedPreferences.Editor, state: SyllablePreferenceState) {
        editor.putBoolean(RootConstants.KEY_HOOK_SYLLABLE_LINE_DISPLAY, state.lineDisplay)
        editor.putBoolean(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, state.relativeProgress)
        editor.putBoolean(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, state.relativeHighlight)
    }

    /** 就地归一化：若偏好组合非法则写回归一化结果。 */
    fun normalizeInPlace(prefs: SharedPreferences) {
        val current = SyllablePreferenceState(
            relativeProgress = prefs.getBoolean(
                RootConstants.KEY_HOOK_SYLLABLE_RELATIVE,
                RootConstants.DEFAULT_HOOK_SYLLABLE_RELATIVE
            ),
            relativeHighlight = prefs.getBoolean(
                RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT,
                RootConstants.DEFAULT_HOOK_SYLLABLE_HIGHLIGHT
            ),
            lineDisplay = prefs.getBoolean(
                RootConstants.KEY_HOOK_SYLLABLE_LINE_DISPLAY,
                RootConstants.DEFAULT_HOOK_SYLLABLE_LINE_DISPLAY
            )
        )
        val normalized = normalize(
            relativeProgress = current.relativeProgress,
            relativeHighlight = current.relativeHighlight,
            lineDisplay = current.lineDisplay
        )
        if (current == normalized) return

        val editor = prefs.edit()
        write(editor, normalized)
        editor.apply()
    }
}
