/*
 * Lyricon V3 - Copyright (C) 2026 liuxiansheng
 * Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root.island.effects.blur

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * 岛背景图：从 Hub 预设写入的 pref_island_bg_* 路径解码为 Bitmap。
 * 失败/类型不符一律安全返回 null（无背景图则回退纯毛玻璃/柔光），绝不在 SystemUI 进程抛异常。
 */
internal object IslandBackgroundImage {

    fun load(prefs: SharedPreferences, key: String): Bitmap? {
        val path = runCatching { prefs.getString(key, "") }.getOrNull().orEmpty()
        if (path.isBlank()) return null
        return runCatching {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return null
            // 采样：先读尺寸，按需降采样，避免大图 OOM
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxSide / (sample * 2) >= 512) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }
}
