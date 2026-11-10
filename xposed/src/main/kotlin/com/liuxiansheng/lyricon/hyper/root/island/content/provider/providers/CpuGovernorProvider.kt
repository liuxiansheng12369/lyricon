/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import java.io.File
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class CpuGovernorProvider : IslandContentProvider {
    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getGovernor()
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) view.text = getGovernor()
    }

    private fun getGovernor(): String {
        return try {
            val file = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
            if (file.exists()) file.readText().trim() else "调度 --"
        } catch (_: Exception) {
            "调度 --"
        }
    }
}
