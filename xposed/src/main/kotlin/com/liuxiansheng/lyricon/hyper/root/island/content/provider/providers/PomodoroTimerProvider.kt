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
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class PomodoroTimerProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var remainingSeconds = 25 * 60
    private var isRunning = false
    private var isBreak = false

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "🍅 25:00"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            if (!isRunning) {
                isRunning = true
                runnable = object : Runnable {
                    override fun run() {
                        remainingSeconds--
                        if (remainingSeconds <= 0) {
                            isBreak = !isBreak
                            remainingSeconds = if (isBreak) 5 * 60 else 25 * 60
                        }
                        val label = if (isBreak) "☕" else "🍅"
                        view.text = "$label ${remainingSeconds / 60}:${String.format("%02d", remainingSeconds % 60)}"
                        handler.postDelayed(this, 1000)
                    }
                }
                handler.post(runnable!!)
            }
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        isRunning = false
    }
}
