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
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import kotlin.math.abs
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class ChargingWattageProvider : IslandContentProvider {
    private var receiver: BroadcastReceiver? = null
    private var lastVoltage = 0f // 伏特

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "充电 --"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) view.text = getWattText(view.context)
        if (receiver == null) {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (view is TextView && intent != null) {
                        // 从广播获取电压（毫伏 → 伏特）
                        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f
                        if (voltage > 0) lastVoltage = voltage
                        view.text = getWattText(view.context)
                    }
                }
            }
            view.context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
    }

    override fun destroy(view: View) {
        receiver?.let { view.context.unregisterReceiver(it) }
        receiver = null
    }

    private fun getWattText(ctx: Context): String {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            if (status != BatteryManager.BATTERY_STATUS_CHARGING) return "充电 --"

            // 电流：微安 → 安
            val current = abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) / 1_000_000f
            // 电压：伏特（从广播获取，默认 4.0V）
            val voltage = if (lastVoltage > 0) lastVoltage else 4.0f
            // 功率 = 电压 × 电流
            val watts = voltage * current

            if (watts > 0.5f) "⚡ ${"%.1f".format(watts)}W" else "充电 --"
        } catch (_: Exception) {
            "充电 --"
        }
    }
}
