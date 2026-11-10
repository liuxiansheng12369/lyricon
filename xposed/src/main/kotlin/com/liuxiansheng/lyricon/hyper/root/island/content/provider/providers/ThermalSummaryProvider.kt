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

class ThermalSummaryProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getThermalText()
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = getThermalText()
            runnable?.let { handler.removeCallbacks(it) }
            runnable = Runnable {
                view.text = getThermalText()
                handler.postDelayed(runnable!!, 3000)
            }
            handler.postDelayed(runnable!!, 3000)
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun getThermalText(): String {
        var maxTemp = 0
        val thermalDir = File("/sys/class/thermal/")
        if (thermalDir.isDirectory) {
            for (zone in thermalDir.listFiles() ?: emptyArray()) {
                if (zone.name.startsWith("thermal_zone")) {
                    try {
                        val temp = File(zone, "temp").readText().trim().toInt() / 1000
                        if (temp in 1..150 && temp > maxTemp) maxTemp = temp
                    } catch (_: Exception) {}
                }
            }
        }
        return if (maxTemp > 0) "🔥 ${maxTemp}°C" else "温度 --"
    }
}
