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

class CustomApiProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var displayText = "自定义API --"
    private var isUpdating = false

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = displayText
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = displayText
            if (runnable == null) {
                fetchApi(view)
                runnable = Runnable {
                    fetchApi(view)
                    handler.postDelayed(runnable!!, 30_000)
                }
                handler.postDelayed(runnable!!, 30_000)
            }
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun fetchApi(tv: TextView) {
        if (isUpdating) return
        val prefs = tv.context.getSharedPreferences("island_custom_api", Context.MODE_PRIVATE)
        val urlStr = prefs.getString("api_url", null)
        if (urlStr.isNullOrEmpty()) {
            displayText = "自定义API 未配置"
            handler.post { tv.text = displayText }
            return
        }
        val jsonPath = prefs.getString("json_path", "") ?: ""
        isUpdating = true
        Thread {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val raw = conn.inputStream.bufferedReader().readText()
                val result = if (jsonPath.isNotEmpty()) {
                    try {
                        val obj = org.json.JSONObject(raw)
                        val parts = jsonPath.split(".")
                        var current: Any = obj
                        for (p in parts) {
                            current = when (current) {
                                is org.json.JSONObject -> current.get(p)
                                is org.json.JSONArray -> current.getJSONObject(p.toInt())
                                else -> current
                            }
                        }
                        current.toString()
                    } catch (_: Exception) { raw.take(15) }
                } else raw.take(15)
                displayText = "API $result"
                handler.post { tv.text = displayText }
            } catch (_: Exception) {
                displayText = "自定义API 失败"
                handler.post { tv.text = displayText }
            } finally {
                isUpdating = false
            }
        }.start()
    }
}
