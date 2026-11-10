/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

@file:Suppress("unused")

package io.github.proify.android.extensions

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible


var View.isVisibleIfChanged: Boolean
    get() = isVisible
    set(value) {
        if (isVisible != value) isVisible = value
    }

var View.visibilityIfChanged: Int
    get() = visibility
    set(value) {
        if (visibility != value) visibility = value
    }

fun ViewGroup.getChildAtOrNull(index: Int): View? =
    if (childCount > index) {
        try {
            getChildAt(index)
        } catch (e: Exception) {
            null
        }
    } else null