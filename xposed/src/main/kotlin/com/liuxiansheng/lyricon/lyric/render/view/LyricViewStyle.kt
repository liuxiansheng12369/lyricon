/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.lyric.render.view

import android.graphics.Typeface

/**
 * 文字外观（主行 / 副行共用）。
 *
 * @param colors 多色渐变数组（单色数组即纯色）
 * @param size 字号（sp）
 * @param typeface 字体；null = 系统默认
 * @param fontVariationSettings 可变字体参数（可选）
 * @param relativeProgress 是否逐字进度（按词时间轴高亮）
 * @param relativeHighlight 是否逐字高亮
 * @param shadowRadius 可读性软阴影半径；0 = 不开启（超级岛有背景，默认 0）
 * @param shadowDx 阴影 X 偏移
 * @param shadowDy 阴影 Y 偏移
 * @param shadowColor 阴影颜色
 */
internal class TextLook(
    val colors: IntArray,
    val size: Float,
    val typeface: Typeface?,
    val fontVariationSettings: String? = null,
    val relativeProgress: Boolean = false,
    val relativeHighlight: Boolean = false,
    val shadowRadius: Float = 0f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 1.5f,
    val shadowColor: Int = 0xAA000000.toInt()
) {
    /** 挂载点兼容别名 */
    val color: IntArray get() = colors
}

/** 高亮颜色对：背景色（已唱区底色）与前景色（已唱区文字色） */
internal class Highlight(
    val background: IntArray,
    val foreground: IntArray
)

/** 逐字动效参数（字上跳总开关） */
internal class WordMotion(
    val enabled: Boolean = true
)

/** 行切换动画参数（当前预留，渲染端统一处理） */
internal class AnimParams(
    val durationMs: Int = 260,
    val interpolator: Int = 0
)

/** 占位行配置（歌词未到时显示歌名 / 倒计时） */
internal class Placeholder(
    val enabled: Boolean = false,
    val countdown: Boolean = false,
    val text: String? = null
)

/** 歌名占位槽（歌词未到时在岛内显示歌名） */
internal class TitleSlot(
    val enabled: Boolean = false
)

/**
 * 整行样式：渲染挂载点构建后下发给歌词行视图。
 *
 * @param primary 主行（歌词原文）外观
 * @param secondary 副行（翻译 / 罗马音 / 下一句预览）外观
 * @param highlight 高亮颜色对
 * @param gradient 已唱区是否渐变过渡
 * @param wordMotion 逐字动效参数
 * @param lineDisplay 整行时间轴模式（逐行高亮，非逐字）
 * @param transitionConfig 行切换过渡配置（预留）
 * @param centerIfPossible 无挖孔时居中
 * @param rightIfPossible 右对齐（分离模式右侧容器用）
 * @param mirror 歌词镜像倒影
 * @param lyricEffect 歌词动画效果编号（0 = 静态）
 * @param placeholder 占位行配置
 * @param titleSlot 歌名占位槽配置
 * @param animParams 行切换动画参数
 */
internal data class LyricViewStyle(
    val primary: TextLook,
    val secondary: TextLook,
    val highlight: Highlight,
    val gradient: Boolean,
    val wordMotion: WordMotion,
    val lineDisplay: Boolean,
    val transitionConfig: String?,
    val centerIfPossible: Boolean,
    val rightIfPossible: Boolean,
    val mirror: Boolean = false,
    val lyricEffect: Int = 0,
    /** 效果模式统一使用的单色（用户设置的歌词文字颜色；跟随设置，不做独立配色） */
    val effectSolidColor: Int = 0xFFFFFFFF.toInt(),
    val placeholder: Placeholder = Placeholder(),
    val titleSlot: TitleSlot = TitleSlot(),
    val animParams: AnimParams = AnimParams()
) {
    companion object {
        /** 默认样式（无内容 / 未配置时兜底） */
        fun defaultStyle(): LyricViewStyle = LyricViewStyle(
            primary = TextLook(
                colors = intArrayOf(0xFFFFFFFF.toInt()),
                size = 16f,
                typeface = null
            ),
            secondary = TextLook(
                colors = intArrayOf(0xFFCCCCCC.toInt()),
                size = 13f,
                typeface = null
            ),
            highlight = Highlight(
                background = intArrayOf(0xFFFFFFFF.toInt()),
                foreground = intArrayOf(0xFF69DB7C.toInt())
            ),
            gradient = true,
            wordMotion = WordMotion(),
            lineDisplay = false,
            transitionConfig = null,
            centerIfPossible = false,
            rightIfPossible = false
        )
    }
}
