/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 当前时间内容提供者
 */
class TimeContentProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var updateRunnable: Runnable? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = timeFormat.format(Date())
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = timeFormat.format(Date())
            updateRunnable?.let { handler.removeCallbacks(it) }
            updateRunnable = Runnable {
                view.text = timeFormat.format(Date())
                handler.postDelayed(updateRunnable!!, 60000)
            }
            handler.postDelayed(updateRunnable!!, 60000)
        }
    }

    override fun destroy(view: View) {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }
}
