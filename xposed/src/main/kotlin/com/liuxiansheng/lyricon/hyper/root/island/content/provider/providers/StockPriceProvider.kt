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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class StockPriceProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var priceText = "¥ --"
    private var isUpdating = false

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = priceText
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = priceText
            if (runnable == null) {
                fetchPrice(view)
                runnable = Runnable {
                    fetchPrice(view)
                    handler.postDelayed(runnable!!, 30000)
                }
                handler.postDelayed(runnable!!, 30000)
            }
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun fetchPrice(tv: TextView) {
        if (isUpdating) return
        isUpdating = true
        Thread {
            try {
                val url = URL("https://hq.sinajs.cn/list=sh600519")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Referer", "https://finance.sina.com.cn")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "GBK"))
                val result = reader.readText()
                reader.close()
                val parts = result.split(",")
                if (parts.size > 3) {
                    val price = parts[3]
                    val priceInt = price.substringBefore(".")
                    priceText = "¥$priceInt"
                }
                handler.post { tv.text = priceText }
            } catch (_: Exception) {
                priceText = "¥ --"
                handler.post { tv.text = priceText }
            } finally {
                isUpdating = false
            }
        }.start()
    }
}
