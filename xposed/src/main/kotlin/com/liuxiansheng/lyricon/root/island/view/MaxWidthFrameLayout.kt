/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.hyper.root.island.view

import android.content.Context
import android.util.Log
import android.widget.FrameLayout

/**
 * 限制最大测量宽度的 FrameLayout 容器。
 * 取代了原先以匿名类实现的测量逻辑，规避了 Xposed 对 maxWidthPx 的反射调用。
 */
class MaxWidthFrameLayout(context: Context) : FrameLayout(context) {

    /**
     * 最大宽度（像素）。设置为 -1（默认）则不限制。
     */
    var maxWidthPx: Int = -1

    /**
     * Used only by injected Super Island test blocks.
     */
    var keepVisible: Boolean = false

    override fun setVisibility(visibility: Int) {
        if (keepVisible && visibility != VISIBLE) {
            super.setVisibility(VISIBLE)
            return
        }
        super.setVisibility(visibility)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val givenWidth = MeasureSpec.getSize(widthMeasureSpec)
        // 动态宽度语义：wrapper 跟随父容器给宽（given），超过 maxWidthPx 才压到上限。
        // 回退 3.0.460 的"始终 maxWidthPx"——那会破坏动态宽度（歌词变宽岛不变）并导致
        // 长歌词被父容器裁剪显示不全。
        val newWidth =
            if (maxWidthPx > 0 && (givenWidth == 0 || givenWidth > maxWidthPx)) maxWidthPx else givenWidth
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.AT_MOST),
            heightMeasureSpec
        )
        Log.e("LyriconDebug", "MWFL onMeasure given=$givenWidth max=$maxWidthPx new=$newWidth measured=$measuredWidth")
        if (maxWidthPx > 0 && measuredWidth > maxWidthPx) {
            setMeasuredDimension(maxWidthPx, measuredHeight)
        }
    }
}
