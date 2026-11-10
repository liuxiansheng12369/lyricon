/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.app.AlarmManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class AlarmTimeProvider : IslandContentProvider {

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getAlarmText(context)
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) view.text = getAlarmText(view.context)
    }

    private fun getAlarmText(ctx: Context): String {
        return try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val info = am.nextAlarmClock
            if (info != null) {
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                "⏰ ${fmt.format(Date(info.triggerTime))}"
            } else ""
        } catch (_: Exception) { "" }
    }
}
