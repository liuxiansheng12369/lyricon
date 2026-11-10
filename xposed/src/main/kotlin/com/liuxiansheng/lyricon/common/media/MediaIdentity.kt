/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common.media

import android.media.session.MediaSession

/**
 * 媒体会话身份（V3 自研）。
 */
data class MediaIdentity(
    val packageName: String = "",
    val sessionToken: MediaSession.Token? = null,
    val songId: String? = null,
    val mediaId: String? = null
) {
    fun normalized(): MediaIdentity = copy(
        packageName = packageName.trim(),
        songId = songId.normalizeIdentityText(),
        mediaId = mediaId.normalizeIdentityText()
    )

    /**
     * 只从另一个快照补齐未知字段；接收方对已知字段保持权威。
     */
    fun fillMissingFrom(fallback: MediaIdentity): MediaIdentity = copy(
        packageName = packageName.ifEmpty { fallback.packageName },
        sessionToken = sessionToken ?: fallback.sessionToken,
        songId = songId ?: fallback.songId,
        mediaId = mediaId ?: fallback.mediaId
    ).normalized()

    /**
     * 两个快照是否证明属于同一 MediaSession。`null` 表示来源未暴露足够信息；
     * 仅包名相同从不作为同一会话的证据。
     */
    fun sameSessionAs(other: MediaIdentity): Boolean? {
        val left = normalized()
        val right = other.normalized()
        if (left.packageName.isNotEmpty() && right.packageName.isNotEmpty() &&
            left.packageName != right.packageName
        ) {
            return false
        }
        if (left.sessionToken != null && right.sessionToken != null) {
            return left.sessionToken == right.sessionToken
        }
        return null
    }

    /**
     * 仅当两侧暴露了互相冲突的所有权或条目身份时返回 false。
     * 缺失字段视为未知，允许较弱的来源稍后被补齐。
     */
    fun isCompatibleWith(other: MediaIdentity): Boolean {
        val left = normalized()
        val right = other.normalized()
        if (left.sameSessionAs(right) == false) return false
        if (left.songId != null && right.songId != null && left.songId != right.songId) {
            return false
        }
        if (left.mediaId != null && right.mediaId != null && left.mediaId != right.mediaId) {
            return false
        }
        return true
    }

    private fun String?.normalizeIdentityText(): String? = this
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != "0" }
}
