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
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class ChargingStatusProvider : IslandContentProvider {
    private var receiver: BroadcastReceiver? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getStatusText(context)
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) view.text = getStatusText(view.context)
        if (receiver == null) {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (view is TextView) view.text = getStatusText(view.context)
                }
            }
            view.context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
    }

    override fun destroy(view: View) {
        receiver?.let { view.context.unregisterReceiver(it) }
        receiver = null
    }

    private fun getStatusText(ctx: Context): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "⚡ 充电中"
            BatteryManager.BATTERY_STATUS_FULL -> "✅ 已充满"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "🔋 放电中"
            else -> "🔌 未充电"
        }
    }
}
