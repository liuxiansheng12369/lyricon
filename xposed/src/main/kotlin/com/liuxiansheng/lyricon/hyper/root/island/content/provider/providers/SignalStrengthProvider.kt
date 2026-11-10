/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class SignalStrengthProvider : IslandContentProvider {
    private var listener: PhoneStateListener? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "信号 --"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView && listener == null) {
            val tm = view.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            listener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val dbm = signalStrength.getCellSignalStrengths().firstOrNull()?.dbm ?: 0
                    view.text = "信号 ${dbm}dBm"
                }
            }
            tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        }
    }

    override fun destroy(view: View) {
        listener?.let {
            val tm = view.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        listener = null
    }
}
