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

class EarthquakeAlertProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var eqText = "地震 无"
    private var isUpdating = false

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = eqText
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = eqText
            if (runnable == null) {
                checkEarthquake(view)
                runnable = Runnable {
                    checkEarthquake(view)
                    handler.postDelayed(runnable!!, 60_000)
                }
                handler.postDelayed(runnable!!, 60_000)
            }
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun checkEarthquake(tv: TextView) {
        if (isUpdating) return
        isUpdating = true
        Thread {
            try {
                val url = URL("https://api.cenc.ac.cn/recent?format=json")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val raw = conn.inputStream.bufferedReader().readText()
                val arr = org.json.JSONArray(raw)
                if (arr.length() > 0) {
                    val latest = arr.getJSONObject(0)
                    val mag = latest.getDouble("M")
                    val loc = latest.getString("LOCATION_C")
                    if (mag >= 3.0) {
                        // 取地点前6字，避免太长
                        val shortLoc = loc.take(6)
                        eqText = "⚠ $shortLoc M$mag"
                    } else {
                        eqText = "地震 无"
                    }
                } else {
                    eqText = "地震 无"
                }
                handler.post { tv.text = eqText }
            } catch (_: Exception) {
                eqText = "地震 无"
                handler.post { tv.text = eqText }
            } finally {
                isUpdating = false
            }
        }.start()
    }
}
