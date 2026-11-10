/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.TextView

/**
 * 自定义文字内容提供者
 */
class CustomTextContentProvider : IslandContentProvider {
    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        val text = if (side == "right") config.rightCustomText else config.leftCustomText
        return TextView(context).apply {
            this.text = text.ifEmpty { "自定义文字" }
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            if (config.typefaceBold) setTypeface(typeface, Typeface.BOLD)
            if (config.typefaceItalic) setTypeface(typeface, Typeface.ITALIC)
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            val text = if (side == "right") config.rightCustomText else config.leftCustomText
            view.text = text.ifEmpty { "自定义文字" }
        }
    }
}
