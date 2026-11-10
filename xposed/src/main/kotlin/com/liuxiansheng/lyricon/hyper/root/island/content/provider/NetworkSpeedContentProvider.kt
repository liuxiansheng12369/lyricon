/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider

import android.content.Context
import android.graphics.Color
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import java.util.Locale

/**
 * 实时网速内容提供者
 * 显示当前下载/上传速度
 */
class NetworkSpeedContentProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastUpdateTime: Long = 0

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "0 KB/s"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            lastRxBytes = TrafficStats.getTotalRxBytes()
            lastTxBytes = TrafficStats.getTotalTxBytes()
            lastUpdateTime = System.currentTimeMillis()

            updateRunnable?.let { handler.removeCallbacks(it) }
            updateRunnable = Runnable {
                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()
                val currentTime = System.currentTimeMillis()
                val timeDiff = (currentTime - lastUpdateTime) / 1000.0

                if (timeDiff > 0) {
                    val rxSpeed = (currentRx - lastRxBytes) / timeDiff
                    val txSpeed = (currentTx - lastTxBytes) / timeDiff
                    val totalSpeed = rxSpeed + txSpeed
                    view.text = formatSpeed(totalSpeed)
                }

                lastRxBytes = currentRx
                lastTxBytes = currentTx
                lastUpdateTime = currentTime

                handler.postDelayed(updateRunnable!!, 2000)
            }
            handler.postDelayed(updateRunnable!!, 2000)
        }
    }

    override fun destroy(view: View) {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSecond / (1024 * 1024))
            bytesPerSecond >= 1024 -> String.format(Locale.getDefault(), "%.0f KB/s", bytesPerSecond / 1024)
            else -> String.format(Locale.getDefault(), "%.0f B/s", bytesPerSecond)
        }
    }
}
