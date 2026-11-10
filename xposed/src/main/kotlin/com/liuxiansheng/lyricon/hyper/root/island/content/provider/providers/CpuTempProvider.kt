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
import java.io.File
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class CpuTempProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private val thermalPaths = arrayOf(
        "/sys/class/thermal/thermal_zone0/temp",
        "/sys/class/thermal/thermal_zone1/temp",
        "/sys/class/thermal/thermal_zone2/temp",
        "/sys/class/thermal/thermal_zone3/temp"
    )

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getCpuTemp()
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = getCpuTemp()
            runnable?.let { handler.removeCallbacks(it) }
            runnable = Runnable {
                view.text = getCpuTemp()
                handler.postDelayed(runnable!!, 3000)
            }
            handler.postDelayed(runnable!!, 3000)
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun getCpuTemp(): String {
        var maxTemp = 0
        for (path in thermalPaths) {
            try {
                val temp = File(path).readText().trim().toInt() / 1000
                if (temp > maxTemp) maxTemp = temp
            } catch (_: Exception) {}
        }
        return if (maxTemp > 0) "CPU ${maxTemp}°C" else "CPU --"
    }
}
