/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.net.wifi.WifiManager
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class WifiFreqBandProvider : IslandContentProvider {

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getBandText(context)
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) view.text = getBandText(view.context)
    }

    private fun getBandText(ctx: Context): String {
        return try {
            val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val freq = wifi.connectionInfo.frequency
            when {
                freq in 2400..2500 -> "2.4G"
                freq in 5000..5900 -> "5G"
                freq in 5900..7200 -> "6G"
                else -> ""
            }
        } catch (_: Exception) { "" }
    }
}
