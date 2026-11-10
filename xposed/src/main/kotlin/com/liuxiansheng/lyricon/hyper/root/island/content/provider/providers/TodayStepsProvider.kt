/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Gravity
import android.view.View
import android.text.TextUtils
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class TodayStepsProvider : IslandContentProvider {
    private var listener: SensorEventListener? = null

    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = "步数 ?"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView && listener == null) {
            val sm = view.context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.let { view.text = "👟 ${it.values[0].toInt()}" }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun destroy(view: View) {
        listener?.let {
            val sm = view.context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.unregisterListener(it)
        }
        listener = null
    }
}
