/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class CurrentAppProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getTopApp(context)
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = getTopApp(view.context)
            runnable?.let { handler.removeCallbacks(it) }
            runnable = Runnable {
                view.text = getTopApp(view.context)
                handler.postDelayed(runnable!!, 2000)
            }
            handler.postDelayed(runnable!!, 2000)
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun getTopApp(ctx: Context): String {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = am.appTasks
            if (tasks.isNotEmpty()) {
                val taskInfo = tasks[0].taskInfo
                val topActivity = taskInfo?.topActivity
                val name = topActivity?.className?.substringAfterLast('.') ?: "当前 --"
                name.take(10)
            } else "当前 --"
        } catch (_: Exception) {
            "当前 --"
        }
    }
}
