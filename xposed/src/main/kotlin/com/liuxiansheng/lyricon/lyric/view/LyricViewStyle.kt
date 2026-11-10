/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.lyric.view

import android.graphics.Color
import android.graphics.Typeface

data class LyricViewStyle(
    val primary: TextLook = TextLook(),
    val secondary: TextLook = TextLook(size = 14f),
    val highlight: Highlight = Highlight(),
    val wordMotion: WordMotion = WordMotion(),
    val gradient: Boolean = true,
    val lineDisplay: Boolean = false,
    val scaleMultiLine: Float = 1f,
    val animation: AnimParams = AnimParams(),
    val placeholder: TitleSlot = TitleSlot.NAME_ARTIST,
    val transitionConfig: String = "smooth",
    val centerIfPossible: Boolean = false,
    val rightIfPossible: Boolean = false,
)

data class TextLook(
    val color: IntArray = intArrayOf(Color.WHITE),
    val size: Float = 16f,
    val typeface: Typeface = Typeface.DEFAULT,
    val fontVariationSettings: String? = null,
    val relativeProgress: Boolean = false,
    val relativeHighlight: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextLook) return false
        return size == other.size && typeface == other.typeface &&
                fontVariationSettings == other.fontVariationSettings &&
                relativeProgress == other.relativeProgress &&
                relativeHighlight == other.relativeHighlight &&
                color.contentEquals(other.color)
    }

    override fun hashCode(): Int {
        var r = size.hashCode()
        r = 31 * r + typeface.hashCode()
        r = 31 * r + (fontVariationSettings?.hashCode() ?: 0)
        r = 31 * r + relativeProgress.hashCode()
        r = 31 * r + relativeHighlight.hashCode()
        r = 31 * r + color.contentHashCode()
        return r
    }
}

data class Highlight(
    val background: IntArray = intArrayOf(Color.GRAY),
    val foreground: IntArray = intArrayOf(Color.WHITE),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Highlight) return false
        return background.contentEquals(other.background) && foreground.contentEquals(other.foreground)
    }

    override fun hashCode(): Int = 31 * background.contentHashCode() + foreground.contentHashCode()
}

data class WordMotion(
    val enabled: Boolean = true,
)

data class AnimParams(
    val enabled: Boolean = false,
    val presetId: String = "stack_flow",
)

enum class TitleSlot { NAME_ARTIST, NAME, NONE }

