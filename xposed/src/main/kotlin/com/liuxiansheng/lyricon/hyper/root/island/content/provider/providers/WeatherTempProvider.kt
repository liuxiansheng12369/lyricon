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

class WeatherTempProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var currentTemp = "天气 --"
    private var isUpdating = false

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = currentTemp
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = currentTemp
            if (runnable == null) {
                fetchWeather(view)
                runnable = Runnable {
                    fetchWeather(view)
                    handler.postDelayed(runnable!!, 30 * 60 * 1000)
                }
                handler.postDelayed(runnable!!, 30 * 60 * 1000)
            }
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun fetchWeather(tv: TextView) {
        if (isUpdating) return
        isUpdating = true
        Thread {
            try {
                val url = URL("https://wttr.in/?format=%t")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val result = conn.inputStream.bufferedReader().readText().trim()
                currentTemp = if (result.isNotEmpty()) "天气 $result" else "天气 --"
                handler.post { tv.text = currentTemp }
            } catch (_: Exception) {
                currentTemp = "天气 --"
                handler.post { tv.text = currentTemp }
            } finally {
                isUpdating = false
            }
        }.start()
    }
}
