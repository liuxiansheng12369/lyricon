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

class AirQualityProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var aqiText = "空气质量 --"
    private var isUpdating = false

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = aqiText
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = aqiText
            if (runnable == null) {
                fetchAqi(view)
                runnable = Runnable {
                    fetchAqi(view)
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

    private fun fetchAqi(tv: TextView) {
        if (isUpdating) return
        isUpdating = true
        Thread {
            try {
                val url = URL("https://api.openweathermap.org/data/2.5/air_pollution?lat=35&lon=117&appid=YOUR_API_KEY")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val raw = conn.inputStream.bufferedReader().readText()
                val obj = org.json.JSONObject(raw)
                val list = obj.getJSONArray("list")
                val aqi = list.getJSONObject(0).getJSONObject("main").getInt("aqi")
                val label = when (aqi) {
                    1 -> "优"
                    2 -> "良"
                    3 -> "轻度"
                    4 -> "中度"
                    5 -> "重度"
                    else -> "未知"
                }
                aqiText = "AQI $label"
                handler.post { tv.text = aqiText }
            } catch (_: Exception) {
                aqiText = "空气质量 --"
                handler.post { tv.text = aqiText }
            } finally {
                isUpdating = false
            }
        }.start()
    }
}
