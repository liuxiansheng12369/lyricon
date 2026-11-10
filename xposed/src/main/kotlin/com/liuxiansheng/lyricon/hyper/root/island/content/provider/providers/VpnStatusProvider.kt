/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class VpnStatusProvider : IslandContentProvider {
    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "VPN 关"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) view.text = getVpnText(view.context)
    }

    private fun getVpnText(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = cm.activeNetwork ?: return "VPN 关"
            val caps = cm.getNetworkCapabilities(active) ?: return "VPN 关"
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) "🔒 VPN"
            else "VPN 关"
        } catch (_: Exception) {
            "VPN 关"
        }
    }
}
