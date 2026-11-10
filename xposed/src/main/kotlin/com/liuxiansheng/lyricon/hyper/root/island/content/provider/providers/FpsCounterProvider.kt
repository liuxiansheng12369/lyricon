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
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class FpsCounterProvider : IslandContentProvider {
    private var lastFrameTime = 0L
    private var frameCount = 0
    private var currentFps = 0
    private var callback: Choreographer.FrameCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "FPS 0"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            startTracking(view)
        }
    }

    override fun destroy(view: View) {
        callback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        updateRunnable?.let { handler.removeCallbacks(it) }
        callback = null
        updateRunnable = null
    }

    private fun startTracking(tv: TextView) {
        if (callback != null) return
        lastFrameTime = System.nanoTime()
        frameCount = 0
        callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameCount++
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(callback!!)
        updateRunnable = object : Runnable {
            override fun run() {
                val now = System.nanoTime()
                val elapsed = (now - lastFrameTime) / 1_000_000_000.0
                if (elapsed > 0) {
                    currentFps = (frameCount / elapsed).toInt()
                    tv.text = "FPS $currentFps"
                }
                frameCount = 0
                lastFrameTime = now
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(updateRunnable!!, 1000)
    }
}
