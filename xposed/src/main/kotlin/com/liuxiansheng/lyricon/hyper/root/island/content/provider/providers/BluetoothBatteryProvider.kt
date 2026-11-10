/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class BluetoothBatteryProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var headset: BluetoothHeadset? = null
    private var serviceListener: BluetoothProfile.ServiceListener? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "蓝牙 --"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = getBtBattery(view.context)
            if (runnable == null) {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter?.isEnabled == true && serviceListener == null) {
                    serviceListener = object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            headset = proxy as BluetoothHeadset
                        }
                        override fun onServiceDisconnected(profile: Int) {
                            headset = null
                        }
                    }
                    adapter.getProfileProxy(view.context, serviceListener, BluetoothProfile.HEADSET)
                }
                runnable = Runnable {
                    view.text = getBtBattery(view.context)
                    handler.postDelayed(runnable!!, 10_000)
                }
                handler.postDelayed(runnable!!, 10_000)
            }
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        serviceListener?.let {
            runCatching {
                BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.HEADSET, headset)
            }
        }
        headset = null
        serviceListener = null
    }

    private fun getBtBattery(ctx: Context): String {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return "蓝牙 --"
            if (!adapter.isEnabled) return "蓝牙 --"

            var bestLevel = -1
            var bestName = ""

            // 方式1：BluetoothHeadset.getBatteryLevel(device) 反射
            val hs = headset
            if (hs != null) {
                val devices = hs.connectedDevices
                for (device in devices) {
                    val level = getBatteryFromHeadset(hs, device)
                    if (level in 0..100 && level > bestLevel) {
                        bestLevel = level
                        bestName = device.name?.take(4) ?: ""
                    }
                }
            }

            // 方式2：BluetoothDevice.getBatteryLevel() 反射
            if (bestLevel < 0) {
                val devices = adapter.bondedDevices
                for (device in devices) {
                    val connected = isDeviceConnected(device)
                    if (connected) {
                        val level = getBatteryFromDevice(device)
                        if (level in 0..100 && level > bestLevel) {
                            bestLevel = level
                            bestName = device.name?.take(4) ?: ""
                        }
                    }
                }
            }

            if (bestLevel >= 0) {
                "🎧${bestLevel}%"
            } else {
                "蓝牙 --"
            }
        } catch (_: Exception) {
            "蓝牙 --"
        }
    }

    private fun getBatteryFromHeadset(hs: BluetoothHeadset, device: BluetoothDevice): Int {
        return try {
            val method = hs.javaClass.getMethod("getBatteryLevel", BluetoothDevice::class.java)
            val result = method.invoke(hs, device)
            when (result) {
                is Int -> result
                is Short -> result.toInt()
                is Byte -> result.toInt()
                else -> -1
            }
        } catch (_: Exception) { -1 }
    }

    private fun getBatteryFromDevice(device: BluetoothDevice): Int {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val result = method.invoke(device)
            when (result) {
                is Int -> result
                is Short -> result.toInt()
                is Byte -> result.toInt()
                else -> -1
            }
        } catch (_: Exception) { -1 }
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val m = device.javaClass.getMethod("isConnected")
            m.invoke(device) as? Boolean ?: false
        } catch (_: Exception) { false }
    }
}
