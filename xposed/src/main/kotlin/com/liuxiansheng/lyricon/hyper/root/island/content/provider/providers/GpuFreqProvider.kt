/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import java.io.File
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class GpuFreqProvider : IslandContentProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    // GPU 频率读取路径（按优先级排列，单位：Hz）
    private val gpuFreqPaths = arrayOf(
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/kernel/gpu/gpu_clock",
        "/sys/class/devfreq/fb0/cur_freq",
        "/sys/class/devfreq/gpufreq/cur_freq",
        "/sys/kernel/mali/mali_gpu_clock"
    )

    // GPU 最大频率路径（用于判断单位）
    private val gpuMaxFreqPaths = arrayOf(
        "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
        "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
        "/sys/kernel/gpu/max_gpu_clock"
    )

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getGpuFreq()
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) {
            view.text = getGpuFreq()
            runnable?.let { handler.removeCallbacks(it) }
            runnable = Runnable {
                view.text = getGpuFreq()
                handler.postDelayed(runnable!!, 1000)
            }
            handler.postDelayed(runnable!!, 1000)
        }
    }

    override fun destroy(view: View) {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun getGpuFreq(): String {
        // 先读取最大频率判断单位（Hz 还是 MHz）
        val maxFreq = readFirstLong(gpuMaxFreqPaths)
        val isHz = maxFreq <= 0 || maxFreq > 1000000 // 最大频率>1MHz 说明单位是 Hz

        for (path in gpuFreqPaths) {
            try {
                val freq = File(path).readText().trim().toLong()
                if (freq <= 0) continue
                val mhz = if (isHz) freq / 1_000_000 else freq
                if (mhz > 0) return "${mhz}MHz"
            } catch (_: Exception) {}
        }
        return "--MHz"
    }

    private fun readFirstLong(paths: Array<String>): Long {
        for (path in paths) {
            try {
                return File(path).readText().trim().toLong()
            } catch (_: Exception) {}
        }
        return -1
    }
}
