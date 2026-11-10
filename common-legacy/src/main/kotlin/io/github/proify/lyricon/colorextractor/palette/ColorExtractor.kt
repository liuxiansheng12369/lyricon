/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.lyricon.colorextractor.palette

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import io.github.proify.lyricon.colorextractor.palette.ColorExtractorImpl.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object ColorExtractor {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val caches = ConcurrentHashMap<String, ColorPaletteResult>()
    private const val TAG = "ColorExtractor"

    fun extractAsync(
        bitmap: Bitmap,
        cacheKey: (suspend () -> String)? = null,
        callback: (ColorPaletteResult?) -> Unit
    ) {
        scope.launch {
            val key = cacheKey?.invoke()

            if (key != null && caches.containsKey(key)) {
                Log.d(TAG, "Cache hit: $key")
                withContext(Dispatchers.Main) {
                    callback(caches[key])
                }
                return@launch
            }
            Log.d(TAG, "Cache miss: $key")

            try {
                val start = System.currentTimeMillis()
                val palette = ColorExtractorImpl.extractThemePalette(bitmap)

                //约50ms
                Log.d(TAG, "Extracted in ${System.currentTimeMillis() - start}ms")

                fun buildColor(r: ThemePalette, isDark: Boolean): ThemeColors {
                    val color = if (isDark) r.onBlackBackground else r.onWhiteBackground
                    return ThemeColors(
                        primary = color.firstOrNull() ?: Color.BLACK,
                        swatches = color.toIntArray()
                    )
                }

                val result = ColorPaletteResult(
                    buildColor(palette, false),
                    buildColor(palette, true)
                )

                key?.let { caches[it] = result }

                withContext(Dispatchers.Main) {
                    callback(result)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }

}