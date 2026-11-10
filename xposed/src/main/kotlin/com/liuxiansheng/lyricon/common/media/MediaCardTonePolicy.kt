/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common.media

import com.liuxiansheng.lyricon.hyper.common.RootConstants

/**
 * 媒体卡色调策略（V3 自研）。
 */
object MediaCardTonePolicy {
    fun resolveSoftCoverTone(configuredTone: Int, isSystemDark: Boolean): Int =
        when (configuredTone) {
            RootConstants.MEDIA_SOFT_COVER_TONE_FOLLOW_SYSTEM -> {
                if (isSystemDark) {
                    RootConstants.MEDIA_SOFT_COVER_TONE_DARK
                } else {
                    RootConstants.MEDIA_SOFT_COVER_TONE_LIGHT
                }
            }

            RootConstants.MEDIA_SOFT_COVER_TONE_LIGHT,
            RootConstants.MEDIA_SOFT_COVER_TONE_DARK -> configuredTone

            else -> RootConstants.MEDIA_SOFT_COVER_TONE_DARK
        }

    fun isSoftCoverDark(configuredTone: Int, isSystemDark: Boolean): Boolean =
        resolveSoftCoverTone(configuredTone, isSystemDark) ==
            RootConstants.MEDIA_SOFT_COVER_TONE_DARK
}
