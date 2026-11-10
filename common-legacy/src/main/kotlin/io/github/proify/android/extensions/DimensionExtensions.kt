/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.android.extensions

import android.content.res.Resources
import android.util.TypedValue
import kotlin.math.roundToInt

private val displayMetrics get() = Resources.getSystem().displayMetrics

/**
 *  将 DP 转换为像素 (PX) 整数。适用于布局尺寸。
 */
val Float.dp: Int
    get() = if (this == 0f) 0 else TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        displayMetrics
    ).roundToInt()

/**
 *  @see [Float.dp]
 * */
val Int.dp: Int get() = if (this == 0) 0 else toFloat().dp

/**
 * 将 SP 转换为像素 (PX) 浮点数。
 */
val Float.sp: Float
    get() = if (this == 0f) 0f else TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        this,
        displayMetrics
    )

/** @see [Float.sp] */
val Int.sp: Float get() = if (this == 0) 0f else toFloat().sp