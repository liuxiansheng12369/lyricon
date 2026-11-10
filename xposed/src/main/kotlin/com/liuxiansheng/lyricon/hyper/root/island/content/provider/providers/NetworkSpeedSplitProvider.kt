/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import java.util.Locale
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class NetworkSpeedSplitProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var lastRx: Long = 0
    private var lastTx: Long = 0
    private var lastTime: Long = 0

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "↓ 0 KB/s"
                textSize = config.textSize * 0.8f
                setTextColor(Color.WHITE)
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(context).apply {
                text = "↑ 0 KB/s"
                textSize = config.textSize * 0.8f
                setTextColor(Color.WHITE)
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            })
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is LinearLayout && view.childCount >= 2) {
            lastRx = TrafficStats.getTotalRxBytes()
            lastTx = TrafficStats.getTotalTxBytes()
            lastTime = System.currentTimeMillis()
            runnable?.let { handler.removeCallbacks(it) }
            runnable = Runnable {
                val nowRx = TrafficStats.getTotalRxBytes()
                val nowTx = TrafficStats.getTotalTxBytes()
                val nowTime = System.currentTimeMillis()
                val diff = (nowTime - lastTime) / 1000.0
                if (diff > 0) {
                    (view.getChildAt(0) as? TextView)?.text =
                        "↓ ${formatSpeed((nowRx - lastRx) / diff)}"
                    (view.getChildAt(1) as? TextView)?.text =
                        "↑ ${formatSpeed((nowTx - lastTx) / diff)}"
                }
                lastRx = nowRx; lastTx = nowTx; lastTime = nowTime
                handler.postDelayed(runnable!!, 2000)
            }
            handler.postDelayed(runnable!!, 2000)
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun formatSpeed(bps: Double): String = when {
        bps >= 1024 * 1024 -> "%.1f MB/s".format(bps / (1024 * 1024))
        bps >= 1024 -> "%.0f KB/s".format(bps / 1024)
        else -> "%.0f B/s".format(bps)
    }
}
