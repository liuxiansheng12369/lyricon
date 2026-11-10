/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import java.io.RandomAccessFile
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class CpuUsageProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    // 上一次采样的 CPU 时间
    private var lastIdle: Long = 0
    private var lastTotal: Long = 0
    private var hasLastSample = false

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getCpuUsage()
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = getCpuUsage()
            runnable?.let { handler.removeCallbacks(it) }
            runnable = Runnable {
                view.text = getCpuUsage()
                handler.postDelayed(runnable!!, 2000)
            }
            handler.postDelayed(runnable!!, 2000)
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        hasLastSample = false
    }

    private fun getCpuUsage(): String {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()
            val parts = line.split("\\s+".toRegex()).drop(1).map { it.toLong() }
            val idle = parts[3]
            val total = parts.sum()

            if (!hasLastSample) {
                // 第一次采样，只记录不计算
                lastIdle = idle
                lastTotal = total
                hasLastSample = true
                "CPU --%"
            } else {
                // 计算两次采样之间的差值
                val idleDiff = idle - lastIdle
                val totalDiff = total - lastTotal
                lastIdle = idle
                lastTotal = total
                if (totalDiff <= 0) {
                    "CPU 0%"
                } else {
                    val pct = 100 - (idleDiff * 100 / totalDiff)
                    "CPU ${pct}%"
                }
            }
        } catch (_: Exception) { "CPU ?" }
    }
}
