/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class ScreenRefreshRateProvider : IslandContentProvider {
    private var listener: DisplayManager.DisplayListener? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getRateText(context)
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        val tv = view as? TextView ?: return
        tv.text = getRateText(tv.context)
        if (listener == null) {
            val dm = tv.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(id: Int) {}
                override fun onDisplayRemoved(id: Int) {}
                override fun onDisplayChanged(id: Int) {
                    tv.text = getRateText(tv.context)
                }
            }
            dm.registerDisplayListener(listener, null)
        }
    }

    override fun destroy(view: View) {
        listener?.let {
            val dm = view.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.unregisterDisplayListener(it)
        }
        listener = null
    }

    private fun getRateText(ctx: Context): String {
        return try {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(0)
            val mode = display?.mode
            "%.0fHz".format(mode?.refreshRate ?: 60f)
        } catch (_: Exception) {
            "60Hz"
        }
    }
}
