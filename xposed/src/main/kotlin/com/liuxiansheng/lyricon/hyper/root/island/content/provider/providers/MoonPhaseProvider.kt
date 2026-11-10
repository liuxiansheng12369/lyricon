/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import java.util.Calendar
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class MoonPhaseProvider : IslandContentProvider {

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getMoonPhase()
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) view.text = getMoonPhase()
    }

    private fun getMoonPhase(): String {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val phase = calcPhase(y, m, d)
        return when {
            phase < 1.84566 -> "🌑 新月"
            phase < 5.53699 -> "🌒 蛾眉"
            phase < 9.22831 -> "🌓 上弦"
            phase < 12.91963 -> "🌔 盈凸"
            phase < 16.61096 -> "🌕 满月"
            phase < 20.30228 -> "🌖 亏凸"
            phase < 23.99361 -> "🌗 下弦"
            phase < 27.68493 -> "🌘 残月"
            else -> "🌑 新月"
        }
    }

    // Simple moon phase calculation (days since new moon)
    private fun calcPhase(y: Int, m: Int, d: Int): Double {
        var yy = if (m <= 2) y - 1 else y
        var mm = if (m <= 2) m + 12 else m
        val k = (365.25 * yy).toLong()
        val c = yy / 100
        val jd = k + (30.6001 * (mm + 1)).toLong() + d + 1720995.0 + 2 - c + c / 4
        val daysSinceNew = (jd - 2451550.1) % 29.53058867
        return if (daysSinceNew < 0) daysSinceNew + 29.53058867 else daysSinceNew
    }
}
