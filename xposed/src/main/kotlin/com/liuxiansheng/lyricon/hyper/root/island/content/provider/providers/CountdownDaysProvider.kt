/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class CountdownDaysProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "倒数日 --"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = getCountdownText(view.context)
            runnable?.let { handler.removeCallbacks(it) }
            runnable = Runnable {
                view.text = getCountdownText(view.context)
                handler.postDelayed(runnable!!, 60_000)
            }
            handler.postDelayed(runnable!!, 60_000)
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun getCountdownText(ctx: Context): String {
        return try {
            val prefs = ctx.getSharedPreferences("island_countdown", Context.MODE_PRIVATE)
            val label = prefs.getString("label", "倒数日") ?: "倒数日"
            val target = prefs.getString("target_date", null) ?: return "倒数日 --"
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val targetDate = fmt.parse(target) ?: return "倒数日 --"
            val diff = (targetDate.time - System.currentTimeMillis()) / 86400000
            when {
                diff > 0 -> "$label ${diff}天"
                diff == 0L -> "$label 今天"
                else -> "$label ${-diff}天前"
            }
        } catch (_: Exception) {
            "倒数日 --"
        }
    }
}
