/*
 * Lyricon V3 · 超级岛内容模式框架（移植自老词幕 IslandContentBridge）
 * ContentProviderConfig：内容 Provider 运行时配置快照，
 * 由 IslandSlotRuntimeConfig 转换而来，仅含 Provider 渲染所需字段。
 */

package com.liuxiansheng.lyricon.hyper.root.island.content.provider

import android.graphics.Color

/** 内容 Provider 渲染配置（歌词/歌名/网速/电量等模式共用） */
data class ContentProviderConfig(
    val textSize: Float,
    val maxWidthPx: Int = 0,
    val displayTranslation: Boolean = true,
    val displayRoma: Boolean = true,
    val enableRelativeProgress: Boolean = false,
    val enableRelativeProgressHighlight: Boolean = false,
    val primaryColor: Int = Color.WHITE,
    val secondaryColor: Int = Color.GRAY,
    val fontWeight: Int = 0,
    val fontItalic: Boolean = false,
    val customFontPath: String = "",
    val rightCustomText: String = "",
    val leftCustomText: String = "",
    val typefaceBold: Boolean = false,
    val typefaceItalic: Boolean = false
)
