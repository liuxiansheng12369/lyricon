/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

@file:Suppress("unused")

package io.github.proify.android.extensions

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout.LayoutParams

fun View.ensureMarginLayoutParams(): MarginLayoutParams {
    val lp = layoutParams as? MarginLayoutParams
        ?: MarginLayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        )
    if (layoutParams == null) layoutParams = lp
    return lp
}