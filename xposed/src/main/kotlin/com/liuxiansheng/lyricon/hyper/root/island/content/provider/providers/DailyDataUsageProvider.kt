/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.net.TrafficStats
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class DailyDataUsageProvider : IslandContentProvider {
    private var dayStartRx: Long = 0
    private var dayStartTx: Long = 0

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        dayStartRx = TrafficStats.getTotalRxBytes()
        dayStartTx = TrafficStats.getTotalTxBytes()
        return TextView(context).apply {
            text = "0MB"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            val rx = TrafficStats.getTotalRxBytes() - dayStartRx
            val tx = TrafficStats.getTotalTxBytes() - dayStartTx
            val total = rx + tx
            view.text = when {
                total >= 1024 * 1024 * 1024 -> "%.1fGB".format(total / (1024.0 * 1024 * 1024))
                total >= 1024 * 1024 -> "%.0fMB".format(total / (1024.0 * 1024))
                else -> "%.0fKB".format(total / 1024.0)
            }
        }
    }
}
