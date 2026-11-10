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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class SecondsTimeProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var runnable: Runnable? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = format.format(Date())
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = format.format(Date())
            runnable?.let { handler.removeCallbacks(it) }
            runnable = Runnable {
                view.text = format.format(Date())
                handler.postDelayed(runnable!!, 1000)
            }
            handler.postDelayed(runnable!!, 1000)
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }
}
