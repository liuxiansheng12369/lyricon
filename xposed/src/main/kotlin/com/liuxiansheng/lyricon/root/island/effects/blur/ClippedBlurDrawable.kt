/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 裁剪模糊 Drawable：把系统模糊区域裁剪成与岛相同的圆角边框。
 */

package com.liuxiansheng.lyricon.hyper.root.island.effects.blur

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** 把系统模糊区域裁剪成与岛相同的圆角边框（V3 自研）。 */
internal class ClippedBlurDrawable(
    private val child: Drawable,
    private val inset: Int,
) : Drawable(), Drawable.Callback {

    private val clipPath = Path()
    private val clipRect = RectF()
    private var cornerRadius = 0f

    init {
        child.callback = this
    }

    fun setCornerRadius(radius: Float) {
        cornerRadius = radius
    }

    override fun onBoundsChange(bounds: Rect) {
        updateChildBounds(bounds)
    }

    private fun updateChildBounds(bounds: Rect): Boolean {
        val safeInset = inset.coerceAtMost(minOf(bounds.width(), bounds.height()) / 2)
        clipRect.set(
            (bounds.left + safeInset).toFloat(),
            (bounds.top + safeInset).toFloat(),
            (bounds.right - safeInset).toFloat(),
            (bounds.bottom - safeInset).toFloat(),
        )
        if (clipRect.isEmpty) return false
        child.setBounds(
            clipRect.left.toInt(),
            clipRect.top.toInt(),
            clipRect.right.toInt(),
            clipRect.bottom.toInt(),
        )
        return true
    }

    override fun draw(canvas: Canvas) {
        if (!updateChildBounds(bounds)) return
        clipPath.reset()
        clipPath.addRoundRect(clipRect, cornerRadius, cornerRadius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)
        child.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        child.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        child.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        scheduleSelf(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    fun release() {
        child.callback = null
        callback = null
    }
}
