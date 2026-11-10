/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.wifi.WifiManager
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class WifiSsidProvider : IslandContentProvider {
    private var receiver: BroadcastReceiver? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getSsid(context)
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) view.text = getSsid(view.context)
        if (receiver == null) {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (view is TextView) view.text = getSsid(view.context)
                }
            }
            view.context.registerReceiver(receiver, IntentFilter("android.net.wifi.STATE_CHANGE"))
        }
    }

    override fun destroy(view: View) {
        receiver?.let { view.context.unregisterReceiver(it) }
        receiver = null
    }

    private fun getSsid(ctx: Context): String {
        return try {
            val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ssid = wifi.connectionInfo.ssid.trim('"')
            ssid.ifEmpty { "无连接" }
        } catch (_: Exception) { "--" }
    }
}
