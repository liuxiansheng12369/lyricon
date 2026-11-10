/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * MaxWidthLinearLayout：限制最大测量宽度的 LinearLayout 容器。
 * Xposed 环境对 View.setMaxWidth 不可用（编译期 Unresolved），
 * 故在 onMeasure 内手动施加宽度上限（对齐 HyperLyric MaxWidthFrameLayout 思路）。
 */

package com.liuxiansheng.lyricon.hyper.root.statusbar

import android.content.Context
import android.widget.LinearLayout

internal class MaxWidthLinearLayout(context: Context) : LinearLayout(context) {

    /** 最大宽度（像素）。<=0 表示不限制。 */
    var maxWidthPx: Int = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (maxWidthPx <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val givenWidth = MeasureSpec.getSize(widthMeasureSpec)
        val width = if (givenWidth == 0 || givenWidth > maxWidthPx) maxWidthPx else givenWidth
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
            heightMeasureSpec
        )
        if (measuredWidth > maxWidthPx) {
            setMeasuredDimension(maxWidthPx, measuredHeight)
        }
    }
}
