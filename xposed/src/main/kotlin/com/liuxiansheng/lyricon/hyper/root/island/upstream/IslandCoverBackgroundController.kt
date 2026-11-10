/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 小岛封面平铺背景（对齐老词幕 IslandCoverBackgroundController / BG_COVER）：
 * 收起胶囊（小岛）背景用当前歌曲专辑封面图铺满，切歌自动切换（只作用于小岛）。
 * 封面 Drawable 按胶囊高度等比缩放后 center-crop 铺满，圆角裁剪适配胶囊。
 */

package com.liuxiansheng.lyricon.hyper.root.island.upstream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.media.CurrentMediaInfoResolver

/**
 * 小岛封面背景：从当前媒体会话取专辑封面 bitmap，包成圆角铺满 Drawable。
 * 切歌后下一次 setDrawable 自然取到新封面（不缓存 bitmap，避免回收）。
 */
internal object IslandCoverBackgroundController {

    /** 当前是否有可用封面（供调用方决定是否回退纯黑） */
    fun hasCover(context: Context?): Boolean {
        val bmp = resolveCurrentCover(context) ?: return false
        return !bmp.isRecycled
    }

    /** 取当前歌曲专辑封面（recycled 返回 null） */
    fun resolveCurrentCover(context: Context?): Bitmap? {
        val ctx = context ?: run {
            android.util.Log.e("CoverBgDebug", "resolveCurrentCover: ctx=null")
            return null
        }
        val pkg = LyriconDataBridge.currentLyricPackageName
        if (pkg.isNullOrBlank()) {
            android.util.Log.e("CoverBgDebug", "resolveCurrentCover: pkg=null/blank")
            return null
        }
        val info = CurrentMediaInfoResolver.getMediaInfo(ctx, pkg)
        val art = info.albumArt?.takeUnless { it.isRecycled }
        android.util.Log.e(
            "CoverBgDebug",
            "resolveCurrentCover: pkg=$pkg art=${art?.width}x${art?.height} artNull=${art == null} src=${info.artworkSource}"
        )
        return art
    }

    /**
     * 小岛封面背景 Drawable：封面按宽度等比缩放，高度方向 center-crop 铺满整个胶囊，
     * 圆角裁剪适配胶囊形状（圆角 = 高度/2，两端半圆）。
     */
    class CoverDrawable(private val bitmap: Bitmap) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        private val clipPath = Path()
        private val clipRect = RectF()

        override fun onBoundsChange(bounds: Rect) {
            clipRect.set(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat()
            )
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            var bw = bitmap.width.toFloat()
            var bh = bitmap.height.toFloat()
            if (bw <= 0f || bh <= 0f) return

            // 修正封面旋转：竖图（高>宽）旋转 90 度适配横胶囊
            val src = if (bh > bw) {
                val m = Matrix()
                m.postRotate(90f)
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            } else {
                bitmap
            }
            bw = src.width.toFloat()
            bh = src.height.toFloat()

            clipPath.reset()
            val corner = b.height() / 2f
            clipPath.addRoundRect(clipRect, corner, corner, Path.Direction.CW)

            val save = canvas.save()
            canvas.clipPath(clipPath)

            // 一张封面铺满整个胶囊：按宽度等比缩放，高度方向 center-crop（严格不变形）
            val scale = b.width() / bw
            val dy = b.top + (b.height() - bh * scale) / 2f
            canvas.drawBitmap(
                src,
                null,
                RectF(b.left.toFloat(), dy, b.right.toFloat(), dy + bh * scale),
                paint
            )

            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
