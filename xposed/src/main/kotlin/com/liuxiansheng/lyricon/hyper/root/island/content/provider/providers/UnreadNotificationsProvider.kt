/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class UnreadNotificationsProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getCountText(context)
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = getCountText(view.context)
            runnable?.let { handler.removeCallbacks(it) }
            runnable = Runnable {
                view.text = getCountText(view.context)
                handler.postDelayed(runnable!!, 10_000)
            }
            handler.postDelayed(runnable!!, 10_000)
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun getCountText(ctx: Context): String {
        return try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val active = nm.activeNotifications
            val count = active.size
            if (count > 0) "📬 $count" else ""
        } catch (_: Exception) { "" }
    }
}
