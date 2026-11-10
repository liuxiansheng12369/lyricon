/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.lyric.render.anim

internal object WordProgressMapper {

    /**
     * 把线性媒体时间进度映射为高亮宽度进度。
     *
     * @param progress 线性媒体时间进度 [0,1]
     * @param sustainStrength 长音强度 [0,1]，0 表示不启用缓动
     */
    fun map(progress: Float, sustainStrength: Float): Float {
        val linear = progress.coerceIn(0f, 1f)
        val strength = sustainStrength.coerceIn(0f, 1f)
        if (strength <= 0f || linear <= 0f || linear >= 1f) return linear

        // 三次曲线：起点斜率 2.5（快），终点斜率 0.5（缓），约 26% 时间走完 50% 宽度
        val curve = linear * (2.5f - 2.5f * linear + linear * linear)
        return linear + (curve - linear) * strength
    }
}
