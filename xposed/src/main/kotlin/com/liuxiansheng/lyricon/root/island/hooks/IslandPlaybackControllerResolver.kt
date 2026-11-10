/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.hyper.root.island.hooks

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandProbeUtils

/**
 * Resolves the MediaController represented by the visible Super Island.
 *
 * Xiaomi exposes the island's StatusBarNotification in DynamicIslandData, so its media-session
 * token is stronger than a package-name lookup. When the notification does not expose a token,
 * a source token or a unique media-id match may still prove the controller. A package with
 * several unresolved sessions is deliberately rejected instead of controlling an arbitrary one.
 */
internal object IslandPlaybackControllerResolver {

    fun resolve(context: Context, data: Any?): MediaController? {
        val islandInfo = IslandProbeUtils.extractMediaIslandInfo(data) ?: return null
        val lyricPackageName = LyriconDataBridge.currentLyricPackageName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        if (islandInfo.packageName != lyricPackageName) return null

        val sourceMetadata = LyriconDataBridge.currentLyricMediaMetadata?.normalized()
        if (sourceMetadata?.packageName?.let { it != islandInfo.packageName } == true) {
            return null
        }

        val statusBarNotification = IslandProbeUtils.extractStatusBarNotification(data)
        if (statusBarNotification?.packageName?.let { it != islandInfo.packageName } == true) {
            return null
        }

        val notificationToken = IslandProbeUtils.extractMediaSessionToken(data)
        val sourceToken = sourceMetadata?.sessionToken
        if (notificationToken != null && sourceToken != null && notificationToken != sourceToken) {
            return null
        }

        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager
            ?: return null
        val controllers = runCatching { manager.getActiveSessions(null) }
            .getOrNull()
            .orEmpty()
            .filter { it.packageName == islandInfo.packageName }
        if (controllers.isEmpty()) return null

        val preferredToken = notificationToken ?: sourceToken
        if (preferredToken != null) {
            return controllers.firstOrNull { controller ->
                controller.sessionToken == preferredToken &&
                        matchesSourceMedia(controller, sourceMetadata)
            }
        }

        val sourceMediaId = sourceMetadata?.mediaId
        if (sourceMediaId != null) {
            return controllers.singleOrNull { controller ->
                controllerMediaId(controller) == sourceMediaId
            }
        }

        return controllers.singleOrNull()
    }

    private fun matchesSourceMedia(
        controller: MediaController,
        sourceMetadata: com.liuxiansheng.lyricon.hyper.lyric.model.LyricMediaMetadata?
    ): Boolean {
        val sourceMediaId = sourceMetadata?.mediaId ?: return true
        return controllerMediaId(controller) == sourceMediaId
    }

    private fun controllerMediaId(controller: MediaController): String? {
        return runCatching {
            controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }.getOrNull()
    }
}
