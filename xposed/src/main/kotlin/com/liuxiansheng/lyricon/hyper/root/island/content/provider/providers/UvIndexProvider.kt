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
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import java.net.HttpURLConnection
import java.net.URL
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class UvIndexProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var uvText = "紫外线 --"
    private var isUpdating = false

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = uvText
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = uvText
            if (runnable == null) {
                fetchUv(view)
                runnable = Runnable {
                    fetchUv(view)
                    handler.postDelayed(runnable!!, 3600_000)
                }
                handler.postDelayed(runnable!!, 3600_000)
            }
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun fetchUv(tv: TextView) {
        if (isUpdating) return
        isUpdating = true
        Thread {
            try {
                val url = URL("https://api.openweathermap.org/data/2.5/uvi?lat=35&lon=117&appid=YOUR_API_KEY")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val raw = conn.inputStream.bufferedReader().readText()
                val obj = org.json.JSONObject(raw)
                val uvi = obj.getDouble("value")
                val label = when {
                    uvi <= 2 -> "低"
                    uvi <= 5 -> "中等"
                    uvi <= 7 -> "高"
                    uvi <= 10 -> "很高"
                    else -> "极高"
                }
                uvText = "UV${uvi.toInt()} $label"
                handler.post { tv.text = uvText }
            } catch (_: Exception) {
                uvText = "紫外线 --"
                handler.post { tv.text = uvText }
            } finally {
                isUpdating = false
            }
        }.start()
    }
}
