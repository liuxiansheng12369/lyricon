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
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class BatteryHealthProvider : IslandContentProvider {
    private var receiver: BroadcastReceiver? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "健康 --"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            if (receiver == null) {
                receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent != null) {
                            val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                            view.text = getHealthText(health)
                        }
                    }
                }
                view.context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        }
    }

    override fun destroy(view: View) {
        receiver?.let { view.context.unregisterReceiver(it) }
        receiver = null
    }

    private fun getHealthText(health: Int): String {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "健康 良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "健康 过热"
            BatteryManager.BATTERY_HEALTH_DEAD -> "健康 损坏"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "健康 过压"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "健康 故障"
            BatteryManager.BATTERY_HEALTH_COLD -> "健康 过冷"
            else -> "健康 未知"
        }
    }
}
