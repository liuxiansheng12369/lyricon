/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class StorageSpaceProvider : IslandContentProvider {

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getStorageText()
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) view.text = getStorageText()
    }

    private fun getStorageText(): String {
        return try {
            val stat = StatFs("/data")
            val total = stat.blockCountLong * stat.blockSizeLong
            val avail = stat.availableBlocksLong * stat.blockSizeLong
            val used = total - avail
            val pct = used * 100 / total
            "💾 ${pct}%"
        } catch (_: Exception) { "💾 ?" }
    }
}
