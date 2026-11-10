/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.media

import android.content.Context
import com.liuxiansheng.lyricon.hyper.common.HyperLogger
import com.liuxiansheng.lyricon.hyper.common.media.MediaMetadataHelper
import com.liuxiansheng.lyricon.hyper.lyric.model.LyricMediaMetadata
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge

/**
 * Resolves the media snapshot consumed by root-side surfaces.
 *
 * Source-provided fields are preferred individually. MediaSession remains the fallback for
 * fields a lyric source does not expose only after the source and controller are confirmed to
 * describe the same current media item.
 */
internal object CurrentMediaInfoResolver {

    fun getMediaInfo(
        context: Context,
        packageName: String,
        logger: HyperLogger? = null,
        sourceMetadata: LyricMediaMetadata? = null
    ): MediaMetadataHelper.MediaInfo {
        val normalizedPackageName = packageName.trim()
        val sourceInfo = (sourceMetadata ?: LyriconDataBridge.currentLyricMediaMetadata)
            ?.normalized()
        val sourcePackage = sourceInfo?.packageName
        val sourcePackageMatches = sourcePackage.isNullOrEmpty() ||
                sourcePackage == normalizedPackageName
        val sessionInfo = MediaMetadataHelper.getMediaInfo(
            context = context,
            packageName = normalizedPackageName,
            logger = logger,
            preferredSessionToken = sourceInfo?.sessionToken
        )

        if (sourceInfo == null) return normalize(sessionInfo)

        // Keep a mismatched source snapshot authoritative for its own event. Do not let the
        // requested package's MediaSession leak into it merely because the source package was
        // different.
        if (!sourcePackageMatches) {
            return MediaMetadataHelper.MediaInfo(
                title = sourceInfo.title.orEmpty(),
                artist = sourceInfo.artist.orEmpty(),
                album = sourceInfo.album.orEmpty(),
                duration = sourceInfo.duration ?: -1L,
                identity = sourceInfo.toIdentity()
            )
        }

        val sessionMatches = isCurrentSession(
            source = sourceInfo,
            sessionInfo = sessionInfo,
            packageName = normalizedPackageName
        )
        // V3 修复：token/mediaId 与来源不精确匹配时，不再丢弃同包名会话的封面。
        // 用户正在播放该包名音乐，session 封面即可用于取色；identity 仍以来源为准，
        // 避免跨包名封面泄漏（MediaMetadataHelper 已按 packageName 过滤会话）。
        val fallback = if (sessionMatches) {
            sessionInfo
        } else {
            sessionInfo.copy(
                identity = sourceInfo.toIdentity(normalizedPackageName)
            )
        }

        return fallback.copy(
            title = sourceInfo.title ?: normalizeText(fallback.title),
            artist = sourceInfo.artist ?: normalizeText(fallback.artist),
            album = sourceInfo.album ?: normalizeText(fallback.album),
            duration = sourceInfo.duration ?: fallback.duration,
            identity = if (sessionMatches) {
                sourceInfo.toIdentity(normalizedPackageName)
                    .fillMissingFrom(sessionInfo.identity)
            } else {
                sourceInfo.toIdentity(normalizedPackageName)
            }
        )
    }

    /**
     * A package name alone is not proof that the MediaSession belongs to the lyric event. Prefer
     * an exact token or media id; otherwise require the stable album fallback to match.
     * Title and artist are display metadata and must not participate in ownership matching because
     * players may change them for translations, car Bluetooth displays, or other presentation
     * modes.
     */
    private fun isCurrentSession(
        source: LyricMediaMetadata,
        sessionInfo: MediaMetadataHelper.MediaInfo,
        packageName: String
    ): Boolean {
        val sessionIdentity = sessionInfo.identity.normalized()
        if (sessionIdentity.packageName != packageName || packageName.isEmpty()) return false

        source.sessionToken?.let { token ->
            if (sessionIdentity.sessionToken != token) return false
            source.mediaId?.let { mediaId ->
                sessionIdentity.mediaId?.let { currentMediaId ->
                    return currentMediaId == mediaId
                }
            }
            return true
        }
        source.mediaId?.let { mediaId ->
            if (sessionIdentity.mediaId != null) {
                return sessionIdentity.mediaId == mediaId
            }
        }

        val sourceAlbum = source.album?.let(::normalizeText)?.takeIf { it.isNotEmpty() }
            ?: return false
        val sessionAlbum = normalizeText(sessionInfo.album).takeIf { it.isNotEmpty() }
            ?: return false
        return sourceAlbum.equals(sessionAlbum, ignoreCase = true)
    }

    private fun normalize(info: MediaMetadataHelper.MediaInfo): MediaMetadataHelper.MediaInfo =
        info.copy(
            title = normalizeText(info.title),
            artist = normalizeText(info.artist),
            album = normalizeText(info.album),
            identity = info.identity.normalized()
        )

    private fun normalizeText(value: String): String = value
        .replace(WHITESPACE, " ")
        .trim()

    private val WHITESPACE = Regex("\\s+")
}
