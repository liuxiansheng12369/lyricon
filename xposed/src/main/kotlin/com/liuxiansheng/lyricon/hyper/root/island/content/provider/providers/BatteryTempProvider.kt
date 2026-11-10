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

class BatteryTempProvider : IslandContentProvider {
    private var receiver: BroadcastReceiver? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "电池 --°C"
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
                            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
                            view.text = if (temp > 0) "电池 ${temp.toInt()}°C" else "电池 --°C"
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
}
