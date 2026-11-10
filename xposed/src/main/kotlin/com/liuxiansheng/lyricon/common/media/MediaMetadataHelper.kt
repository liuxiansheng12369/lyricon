/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.liuxiansheng.lyricon.hyper.common.HyperLogger

/**
 * 媒体元数据辅助类。
 * 负责从 MediaSession 中提取歌曲信息及封面图片，提供多级兜底逻辑。
 */
object MediaMetadataHelper {

    private val sessionLock = Any()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val debugStates = HashMap<String, String>()

    @Volatile
    private var mediaSessionManager: MediaSessionManager? = null

    @Volatile
    private var activeControllers: List<MediaController> = emptyList()

    /**
     * 回调推送的完整 metadata 缓存（含封面 bitmap）。
     * MediaController.getMetadata() 快照的 Bitmap 字段为空，必须注册
     * MediaController.Callback 等 onMetadataChanged 推送才拿得到封面。
     * 与老词幕 SystemUIMediaUtils 同机制：只读 MediaSession，不 hook 音乐 App。
     */
    private val metadataCache =
        java.util.concurrent.ConcurrentHashMap<MediaController, MediaMetadata>()

    /** 已注册回调的控制器，便于会话移除时注销 */
    private val controllerCallbacks =
        java.util.concurrent.ConcurrentHashMap<MediaController, MediaController.Callback>()

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        activeControllers = controllers.orEmpty()
        refreshControllerCallbacks(controllers.orEmpty())
    }

    /** A media snapshot assembled from one controller. */
    data class MediaInfo(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArt: Bitmap? = null,
        val artworkSource: String = "none",
        val duration: Long = -1L,
        val identity: MediaIdentity = MediaIdentity()
    )

    data class PlaybackProgress(
        val position: Long = -1L,
        val duration: Long = -1L,
        val isPlaying: Boolean = false,
        val playbackSpeed: Float = 0f
    ) {
        val fraction: Float
            get() = if (position >= 0L && duration > 0L) {
                (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                -1f
            }
    }

    /**
     * 与老词幕 SystemUIMediaUtils 相同：为每个活动 MediaSession 注册
     * MediaController.Callback。注册后系统立即推送一次完整 metadata（含封面 bitmap）。
     */
    private fun refreshControllerCallbacks(controllers: List<MediaController>) {
        val current = controllers.toSet()
        controllerCallbacks.forEach { (controller, callback) ->
            if (controller !in current) {
                runCatching { controller.unregisterCallback(callback) }
                controllerCallbacks.remove(controller)
                metadataCache.remove(controller)
            }
        }
        controllers.forEach { controller ->
            if (!controllerCallbacks.containsKey(controller)) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        if (metadata != null) metadataCache[controller] = metadata
                    }
                }
                runCatching {
                    controller.registerCallback(
                        callback,
                        Handler(android.os.Looper.getMainLooper())
                    )
                }
                controllerCallbacks[controller] = callback
            }
        }
    }

    /**
     * 获取指定包名的当前媒体信息
     */
    fun getMediaInfo(
        context: Context,
        packageName: String,
        logger: HyperLogger? = null,
        preferredSessionToken: MediaSession.Token? = null
    ): MediaInfo {
        if (packageName.isEmpty()) return MediaInfo()

        return try {
            val controller = findController(context, packageName, preferredSessionToken)
            if (controller != null) {
                android.util.Log.i(
                    "LyriconCover",
                    "getMediaInfo: pkg=$packageName ctrl=ok " +
                            "cache=${metadataCache.containsKey(controller)} " +
                            "meta=${controller.metadata != null}"
                )
            } else {
                android.util.Log.i(
                    "LyriconCover",
                    "getMediaInfo: pkg=$packageName ctrl=null sessions=${activeControllers.size}"
                )
            }
            if (controller == null) {
                debug(
                    logger,
                    packageName,
                    "no_controller"
                ) {
                    "媒体信息未读取: package=$packageName, reason=no_matching_controller"
                }
                return MediaInfo(identity = MediaIdentity(packageName = packageName))
            }
            val cached = metadataCache[controller]
            val metadata = if (cached != null) {
                cached
            } else {
                // V3 修复：同包名任一会话的缓存兜底（getActiveSessions 可能返回
                // 新实例，旧实例的回调缓存仍然有效，按包名匹配避免失配）
                metadataCache.entries.firstOrNull {
                    it.key.packageName == controller.packageName
                }?.value ?: controller.metadata
            }
            if (metadata == null) {
                debug(
                    logger,
                    packageName,
                    "no_metadata"
                ) {
                    "媒体信息未读取: package=$packageName, reason=controller_metadata_null"
                }
                return MediaInfo(
                    identity = MediaIdentity(
                        packageName = packageName,
                        sessionToken = controller.sessionToken
                    )
                )
            }
            val mediaInfo = metadata.toMediaInfo().copy(
                identity = MediaIdentity(
                    packageName = packageName,
                    sessionToken = controller.sessionToken,
                    mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                )
            )
            debug(
                logger,
                packageName,
                "ok|${mediaInfo.title.hashCode()}|${mediaInfo.artist.hashCode()}|" +
                        "${mediaInfo.albumArt?.width}x${mediaInfo.albumArt?.height}|${mediaInfo.artworkSource}"
            ) {
                "媒体信息已读取: package=$packageName, title=\"${debugText(mediaInfo.title)}\", " +
                        "artist=\"${debugText(mediaInfo.artist)}\", album=\"${debugText(mediaInfo.album)}\", " +
                        "albumArt=${mediaInfo.albumArt != null}, " +
                        "artworkSource=${mediaInfo.artworkSource}, " +
                        "bitmap=${mediaInfo.albumArt?.width}x${mediaInfo.albumArt?.height}"
            }
            mediaInfo
        } catch (e: Exception) {
            logger?.e("MediaMetadataHelper", "获取媒体信息失败 ($packageName)", e)
            MediaInfo(identity = MediaIdentity(packageName = packageName))
        }
    }

    /**
     * 获取指定包名的当前播放位置（毫秒）。未播放或无控制器时返回 -1。
     */
    fun getPlaybackPosition(context: Context, packageName: String): Long {
        if (packageName.isEmpty()) return -1
        return try {
            estimatePlaybackPosition(findController(context, packageName)?.playbackState)
        } catch (_: Exception) {
            -1
        }
    }

    fun getPlaybackProgress(context: Context, packageName: String): PlaybackProgress {
        if (packageName.isEmpty()) return PlaybackProgress()
        return try {
            val controller = findController(context, packageName)
                ?: return PlaybackProgress()
            val state = controller.playbackState
            val duration = controller.metadata?.extractDuration() ?: -1L
            PlaybackProgress(
                position = estimatePlaybackPosition(state),
                duration = duration,
                isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                playbackSpeed = state?.playbackSpeed ?: 0f
            )
        } catch (_: Exception) {
            PlaybackProgress()
        }
    }

    /**
     * 通过系统 MediaSession 判断指定包名是否正在播放。
     */
    fun isPackagePlaying(context: Context, packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            ensureSessionSnapshot(context)
            activeControllers.any {
                it.packageName == packageName && it.playbackState?.state == PlaybackState.STATE_PLAYING
            }
        } catch (_: Exception) {
            false
        }
    }

    fun estimatePlaybackPosition(state: PlaybackState?): Long {
        state ?: return -1L
        val basePosition = state.position
        if (basePosition < 0L) return -1L
        if (state.state != PlaybackState.STATE_PLAYING || state.lastPositionUpdateTime <= 0L) {
            return basePosition
        }
        val elapsed = (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0L)
        return (basePosition + elapsed * state.playbackSpeed).toLong().coerceAtLeast(0L)
    }

    private fun findController(
        context: Context,
        packageName: String,
        preferredSessionToken: MediaSession.Token? = null
    ): MediaController? {
        ensureSessionSnapshot(context)
        selectController(activeControllers, packageName, preferredSessionToken)?.let { return it }
        refreshSessionSnapshot()
        return selectController(activeControllers, packageName, preferredSessionToken)
    }

    private fun selectController(
        controllers: List<MediaController>,
        packageName: String,
        preferredSessionToken: MediaSession.Token? = null
    ): MediaController? {
        if (preferredSessionToken != null) {
            return controllers.firstOrNull {
                it.packageName == packageName && it.sessionToken == preferredSessionToken
            }
        }
        var latestController: MediaController? = null
        var latestUpdateTime = Long.MIN_VALUE
        controllers.forEach { controller ->
            if (controller.packageName != packageName) return@forEach
            val state = controller.playbackState
            if (state?.state == PlaybackState.STATE_PLAYING) return controller
            val updateTime = state?.lastPositionUpdateTime ?: 0L
            if (latestController == null || updateTime > latestUpdateTime) {
                latestController = controller
                latestUpdateTime = updateTime
            }
        }
        return latestController
    }

    private fun ensureSessionSnapshot(context: Context) {
        if (mediaSessionManager != null) return
        synchronized(sessionLock) {
            if (mediaSessionManager != null) return
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaSessionManager = manager
            activeControllers = runCatching { manager.getActiveSessions(null) }.getOrDefault(emptyList())
            refreshControllerCallbacks(activeControllers)
            val registerListener: () -> Unit = {
                runCatching {
                    manager.addOnActiveSessionsChangedListener(activeSessionsListener, null)
                }
                Unit
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                registerListener()
            } else {
                mainHandler.post(registerListener)
            }
        }
    }

    private fun refreshSessionSnapshot() {
        val manager = mediaSessionManager ?: return
        val updated = runCatching { manager.getActiveSessions(null) }.getOrDefault(activeControllers)
        activeControllers = updated
        // V3 修复：getActiveSessions 可能返回新的 MediaController 实例，
        // 若不重新注册回调，metadataCache 永远只有旧实例的键，
        // 封面 bitmap 缓存对新实例失配导致取色拿不到封面。
        refreshControllerCallbacks(updated)
    }

    /**
     * 扩展方法：多级兜底提取封面
     * 优先级：ALBUM_ART > ART > DISPLAY_ICON
     */
    private fun MediaMetadata.extractAlbumArt(): ArtworkResult {
        runCatching {
            val albumArtUri = getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            val displayUri = getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            val hasBitmapArt = getBitmap(MediaMetadata.METADATA_KEY_ART) != null
            val hasAlbumBitmap = getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null
            val title = getString(MediaMetadata.METADATA_KEY_TITLE)
            android.util.Log.i(
                "LyriconCover",
                "封面诊断: albumArtUri=${albumArtUri?.take(80)} displayIconUri=${displayUri?.take(80)} " +
                        "artBitmap=$hasBitmapArt albumBitmap=$hasAlbumBitmap title=${title?.take(20)}"
            )
        }
        return try {
            getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let {
                ArtworkResult(it, "album_art")
            } ?: getBitmap(MediaMetadata.METADATA_KEY_ART)?.let {
                ArtworkResult(it, "art")
            } ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let {
                ArtworkResult(it, "display_icon")
            } ?: ArtworkResult(null, "none")
        } catch (_: Exception) {
            ArtworkResult(null, "error")
        }
    }

    private fun MediaMetadata.toMediaInfo(): MediaInfo {
        val mediaDescription = description
        val artwork = extractAlbumArt()
        return MediaInfo(
            title = getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: mediaDescription.title?.toString().orEmpty(),
            artist = getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: getString(MediaMetadata.METADATA_KEY_AUTHOR)
                ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: mediaDescription.subtitle?.toString().orEmpty(),
            album = getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
                ?: mediaDescription.description?.toString().orEmpty(),
            albumArt = artwork.bitmap,
            artworkSource = artwork.source,
            duration = extractDuration()
        )
    }

    private data class ArtworkResult(
        val bitmap: Bitmap?,
        val source: String
    )

    private fun debug(
        logger: HyperLogger?,
        packageName: String,
        state: String,
        message: () -> String
    ) {
        if (logger == null) return
        val changed = synchronized(debugStates) {
            if (debugStates[packageName] == state) {
                false
            } else {
                debugStates[packageName] = state
                true
            }
        }
        if (changed) logger.d("MediaMetadataHelper", message())
    }

    private fun MediaMetadata.extractDuration(): Long {
        return try {
            getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0L } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun debugText(value: String): String {
        val normalized = value
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .trim()
        if (normalized.isEmpty()) return "<empty>"
        return normalized.take(MAX_DEBUG_TEXT_LENGTH).let {
            if (normalized.length > MAX_DEBUG_TEXT_LENGTH) "$it…" else it
        }
    }

    private const val MAX_DEBUG_TEXT_LENGTH = 80

}
