/*
 * IslandContentBridge — 超级岛内容桥接框架
 * 状态栏图标组合：蓝牙状态 + 电池电量 + 实时网速
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

/**
 * 状态栏图标组合提供者
 * 水平排列：蓝牙状态 | 电池电量 | 实时网速
 */
class StatusBarIconsProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var headset: BluetoothHeadset? = null
    private var serviceListener: BluetoothProfile.ServiceListener? = null
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastUpdateTime: Long = 0

    private lateinit var btText: TextView
    private lateinit var batteryText: TextView
    private lateinit var speedText: TextView

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            btText = createItem(context, config.textSize)
            batteryText = createItem(context, config.textSize)
            speedText = createItem(context, config.textSize)

            addView(btText)
            addView(createSeparator(context, config.textSize))
            addView(batteryText)
            addView(createSeparator(context, config.textSize))
            addView(speedText)
        }
    }

    private fun createItem(context: Context, textSize: Float): TextView {
        return TextView(context).apply {
            this.textSize = textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            setPadding(0, 0, 0, 0)
        }
    }

    private fun createSeparator(context: Context, textSize: Float): TextView {
        return TextView(context).apply {
            text = " "
            this.textSize = textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        // 初始化蓝牙代理
        initBluetooth(view.context)

        // 注册电池广播
        if (batteryReceiver == null) {
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    batteryText.text = getBatteryText(view.context)
                }
            }
            view.context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }

        // 初始化网速
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastUpdateTime = System.currentTimeMillis()

        // 立即更新一次
        updateAll(view.context)

        // 定时更新（网速2秒，蓝牙10秒）
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = Runnable {
            updateAll(view.context)
            handler.postDelayed(updateRunnable!!, 2000)
        }
        handler.postDelayed(updateRunnable!!, 2000)
    }

    private fun updateAll(ctx: Context) {
        // 更新网速
        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()
        val currentTime = System.currentTimeMillis()
        val timeDiff = (currentTime - lastUpdateTime) / 1000.0
        if (timeDiff > 0) {
            val rxSpeed = (currentRx - lastRxBytes) / timeDiff
            val txSpeed = (currentTx - lastTxBytes) / timeDiff
            speedText.text = formatSpeed(rxSpeed + txSpeed)
        }
        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastUpdateTime = currentTime

        // 更新电池
        batteryText.text = getBatteryText(ctx)

        // 更新蓝牙（每10秒更新一次，这里简化为每次都更新）
        btText.text = getBtText(ctx)
    }

    override fun destroy(view: View) {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
        batteryReceiver?.let {
            runCatching { view.context.unregisterReceiver(it) }
        }
        batteryReceiver = null
        serviceListener?.let {
            runCatching {
                BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.HEADSET, headset)
            }
        }
        headset = null
        serviceListener = null
    }

    private fun initBluetooth(ctx: Context) {
        if (serviceListener != null) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) return
        serviceListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                headset = proxy as BluetoothHeadset
            }
            override fun onServiceDisconnected(profile: Int) {
                headset = null
            }
        }
        adapter.getProfileProxy(ctx, serviceListener, BluetoothProfile.HEADSET)
    }

    private fun getBatteryText(ctx: Context): String {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            "${level}%"
        } catch (e: Throwable) {
            "--%"
        }
    }

    private fun getBtText(ctx: Context): String {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return "BT"
            if (!adapter.isEnabled) return "BT"
            val hs = headset
            if (hs != null) {
                val devices = hs.connectedDevices
                if (devices.isNotEmpty()) {
                    var bestLevel = -1
                    for (device in devices) {
                        val level = getBatteryFromHeadset(hs, device)
                        if (level in 0..100 && level > bestLevel) bestLevel = level
                    }
                    if (bestLevel >= 0) return "BT${bestLevel}%"
                }
            }
            "BT"
        } catch (e: Throwable) {
            "BT"
        }
    }

    private fun getBatteryFromHeadset(hs: BluetoothHeadset, device: android.bluetooth.BluetoothDevice): Int {
        return try {
            val method = BluetoothHeadset::class.java.getMethod("getBatteryLevel", android.bluetooth.BluetoothDevice::class.java)
            method.invoke(hs, device) as? Int ?: -1
        } catch (e: Throwable) {
            -1
        }
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1fM", bytesPerSecond / (1024 * 1024))
            bytesPerSecond >= 1024 -> String.format(java.util.Locale.getDefault(), "%.0fK", bytesPerSecond / 1024)
            else -> String.format(java.util.Locale.getDefault(), "%.0fB", bytesPerSecond)
        }
    }
}
