/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.liuxiansheng.lyricon.hyper.common.media.MediaIdentity
import com.liuxiansheng.lyricon.hyper.common.media.MediaMetadataHelper
import kotlin.math.abs

/**
 * 封面取色助手（V3 自研）。
 */
object CoverColorHelper {
    private const val TAG = "CoverColorHelper"

    // ============ 像素平均取色（V3 自研主色通道） ============
    /** 采样尺寸（宽=高）。 */
    private const val SAMPLE_SIZE = 24

    /** 忽略低于该透明度的像素。 */
    private const val MIN_ALPHA = 80

    /** 暗化系数：保留色相，略压暗亮度。 */
    private const val DARKEN_FACTOR = 0.5f

    /** 无有效像素时的回退颜色（深靛蓝黑）。 */
    private const val DEFAULT_COLOR = 0xFF20242E.toInt()

    /** 封面主色暗化后的背景色。 */
    fun backdropColor(bitmap: Bitmap): Int = darken(dominantColor(bitmap), DARKEN_FACTOR)

    /**
     * 像素级占优色统计：缩小到 ~24x24 采样求平均，归一到完整 alpha。
     * 不依赖外部聚类库，SystemUI 内稳定运行。
     */
    private fun dominantColor(src: Bitmap): Int {
        return try {
            if (src.width <= 0 || src.height <= 0) return DEFAULT_COLOR

            val small = Bitmap.createScaledBitmap(src, SAMPLE_SIZE, SAMPLE_SIZE, true)
            val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
            small.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
            if (small !== src) small.recycle()

            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0L
            for (p in pixels) {
                // 忽略接近透明的像素
                val a = (p shr 24) and 0xFF
                if (a < MIN_ALPHA) continue
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
            if (n == 0L) return DEFAULT_COLOR
            Color.rgb(
                (r / n).toInt().coerceIn(0, 255),
                (g / n).toInt().coerceIn(0, 255),
                (b / n).toInt().coerceIn(0, 255)
            )
        } catch (_: Exception) {
            DEFAULT_COLOR
        }
    }

    /** 把颜色压暗到深色区间（保留色相，仅降低亮度）。 */
    private fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(255, r, g, b)
    }

    /** 缩到 96px 内给聚类取色用，避免大图聚类卡顿（保留足够颜色信息）。 */
    private fun scaleForPalette(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val scale = minOf(1f, PALETTE_MAX_DIM.toFloat() / maxOf(w, h))
        if (scale >= 1f) return src
        return Bitmap.createScaledBitmap(
            src,
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1),
            true
        )
    }


    /**
     * 歌词源确认的颜色会话。身份和缓存键只在这里解释，渲染层不再拼接媒体字段。
     */
    class ColorSession internal constructor(
        val revision: Long,
        internal val cacheKey: ColorCacheKey,
        internal val title: String,
        internal val artist: String,
        internal val album: String
    ) {
        /** Stable diagnostic token for consumers that need to invalidate animations. */
        val mediaKey: String
            get() = cacheKey.debugKey()
    }

    class ArtworkRequest internal constructor(
        val colorSession: ColorSession,
        val revision: Long,
        internal val fingerprint: ArtworkFingerprint
    )

    private data class CacheEntry(
        val artworkFingerprint: ArtworkFingerprint,
        val colors: Pair<IntArray, IntArray>
    )

    internal data class ColorCacheKey(
        val identity: MediaIdentity,
        val fallbackSessionKey: String?,
    ) {
        fun debugKey(): String {
            val trackKey = when {
                identity.songId != null && identity.mediaId != null ->
                    "song:${identity.songId}|media:${identity.mediaId}"

                identity.songId != null -> "song:${identity.songId}"
                identity.mediaId != null -> "media:${identity.mediaId}"
                else -> ""
            }
            val key = if (identity.sessionToken != null) {
                listOf("session", identity.sessionToken.hashCode(), trackKey)
            } else {
                listOf("fallback", trackKey, fallbackSessionKey.orEmpty())
            }
            return "${identity.packageName}\u001F${key.joinToString("\u001F")}"
        }
    }

    internal class ArtworkFingerprint(
        val pixels: IntArray
    ) {
        fun isSimilarTo(other: ArtworkFingerprint): Boolean {
            if (pixels.size != other.pixels.size || pixels.isEmpty()) return false
            var totalDelta = 0L
            pixels.indices.forEach { index ->
                val first = pixels[index]
                val second = other.pixels[index]
                totalDelta += abs((first ushr 16 and 0xFF) - (second ushr 16 and 0xFF))
                totalDelta += abs((first ushr 8 and 0xFF) - (second ushr 8 and 0xFF))
                totalDelta += abs((first and 0xFF) - (second and 0xFF))
            }
            return totalDelta <= pixels.size * RGB_CHANNEL_COUNT * MAX_AVERAGE_CHANNEL_DELTA
        }
    }

    private var sessionRevision = 0L
    private var artworkRevision = 0L
    private var activeSession: ColorSession? = null
    private var activeArtworkRequest: ArtworkRequest? = null
    private val keyedCache = LinkedHashMap<ColorCacheKey, CacheEntry>()

    /**
     * 只有歌词源生命周期可以推进当前颜色会话。SystemUI 的封面、进度和动画回调
     * 都只能读取此状态，避免迟到的上一首歌回调把活动歌曲切回去。
     */
    @Synchronized
    fun activateSession(mediaInfo: MediaMetadataHelper.MediaInfo): ColorSession? {
        val identity = mediaInfo.identity.normalized()
        val normalizedTitle = mediaInfo.title.normalizeMediaText()
        val normalizedArtist = mediaInfo.artist.normalizeMediaText()
        val normalizedAlbum = mediaInfo.album.normalizeMediaText()
        val trackKey = buildTrackKey(identity.songId, identity.mediaId)
        if (identity.packageName.isEmpty() ||
            (identity.sessionToken == null && trackKey == null &&
                    normalizedAlbum.isEmpty())
        ) {
            return null
        }

        val fallbackSessionKey = if (identity.sessionToken == null) {
            listOf(identity.packageName, normalizedAlbum)
                .joinToString("\u001F")
        } else {
            null
        }
        val cacheKey = ColorCacheKey(
            identity = identity,
            fallbackSessionKey = fallbackSessionKey,
        )
        val current = activeSession
        if (current?.cacheKey == cacheKey) {
            val updated = ColorSession(
                revision = current.revision,
                cacheKey = current.cacheKey,
                title = normalizedTitle.ifEmpty { current.title },
                artist = normalizedArtist.ifEmpty { current.artist },
                album = normalizedAlbum.ifEmpty { current.album }
            )
            activeSession = updated
            return updated
        }

        return ColorSession(
            revision = ++sessionRevision,
            cacheKey = cacheKey,
            title = normalizedTitle,
            artist = normalizedArtist,
            album = normalizedAlbum
        ).also {
            activeSession = it
            activeArtworkRequest = null
        }
    }

    @Synchronized
    fun endSession(): Boolean {
        if (activeSession == null) return false
        val revision = activeSession?.revision
        activeSession = null
        activeArtworkRequest = null
        sessionRevision++
        HookLogger.dState(
            stateId = "CoverColorHelper.session",
            tag = TAG,
            state = "ended|$revision|$sessionRevision"
        ) {
            "颜色会话结束: previousRevision=$revision, nextRevision=$sessionRevision"
        }
        return true
    }

    @Synchronized
    fun currentSession(packageName: String? = null): ColorSession? {
        val current = activeSession ?: return null
        if (packageName == null) return current
        return current.takeIf {
            it.cacheKey.identity.packageName == packageName.normalizeMediaText()
        }
    }

    @Synchronized
    fun isCurrentSession(session: ColorSession): Boolean {
        return isCurrentSessionLocked(session)
    }

    /**
     * 将系统媒体元数据与歌词源确认的歌曲进行匹配。会话 Token 或稳定媒体 ID
     * 可以直接确认归属；没有这些身份时，只使用包名和专辑进行保守兜底，
     * 不让可变的标题影响颜色会话。
     */
    private fun resolveArtworkRequest(
        mediaInfo: MediaMetadataHelper.MediaInfo
    ): ArtworkRequest? {
        val bitmap = mediaInfo.albumArt ?: return null
        if (bitmap.isRecycled) {
            HookLogger.dState(
                stateId = "CoverColorHelper.artwork",
                tag = TAG,
                state = "recycled"
            ) {
                "封面取色跳过: reason=bitmap_recycled"
            }
            return null
        }
        val fingerprint = bitmapFingerprint(bitmap)
        return synchronized(this) {
            val current = activeSession ?: run {
                HookLogger.dState(
                    stateId = "CoverColorHelper.artwork",
                    tag = TAG,
                    state = "no_session"
                ) {
                    "封面取色跳过: reason=no_active_color_session"
                }
                return@synchronized null
            }
            if (!matchesArtworkMetadataLocked(current, mediaInfo)) {
                HookLogger.dState(
                    stateId = "CoverColorHelper.artwork",
                    tag = TAG,
                    state = "metadata_mismatch|${current.revision}|" +
                            "${mediaInfo.identity.packageName}|${mediaInfo.title}|${mediaInfo.artist}"
                ) {
                    "封面取色跳过: reason=metadata_mismatch, sessionRevision=${current.revision}, " +
                        "sessionTitle=\"${debugText(current.title)}\", " +
                        "sessionArtist=\"${debugText(current.artist)}\", " +
                        "mediaTitle=\"${debugText(mediaInfo.title.normalizeMediaText())}\", " +
                        "mediaArtist=\"${debugText(mediaInfo.artist.normalizeMediaText())}\", " +
                        "packageMatches=${current.cacheKey.identity.packageName == mediaInfo.identity.packageName}, " +
                        "mediaPackage=${mediaInfo.identity.packageName.ifEmpty { "<empty>" }}"
                }
                return@synchronized null
            }

            val activeRequest = activeArtworkRequest
            if (activeRequest != null &&
                isCurrentSessionLocked(activeRequest.colorSession) &&
                activeRequest.fingerprint.isSimilarTo(fingerprint)
            ) {
                return@synchronized activeRequest
            }

            ArtworkRequest(
                colorSession = current,
                revision = ++artworkRevision,
                fingerprint = fingerprint
            ).also { activeArtworkRequest = it }
        }
    }

    /**
     * 解析当前 MediaSession 封面并填充共享调色板（每个请求一次）。
     * 渲染层与各取色消费者必须复用此缓存，不把组件回调当作取色来源。
     */
    fun ensureArtworkColors(mediaInfo: MediaMetadataHelper.MediaInfo): ArtworkRequest? {
        HookLogger.i(
            TAG, "封面取色诊断: entry art=${mediaInfo.albumArt != null} " +
                "session=${activeSession != null} pkg=${mediaInfo.identity.packageName}"
        )
        val bitmap = mediaInfo.albumArt
        if (bitmap == null) {
            HookLogger.i(
                TAG, "封面取色诊断: albumArt=null title='${mediaInfo.title}' " +
                    "pkg=${mediaInfo.identity.packageName}"
            )
            return null
        }
        val request = resolveArtworkRequest(mediaInfo) ?: return null
        val cachedColors = getCachedColors(
            useGradient = true,
            request = request
        )
        val colors = cachedColors ?: extractColors(
            bitmap = bitmap,
            useGradient = true,
            request = request
        )
        val current = request.takeIf(::isCurrentArtwork)
        val paletteState = when {
            current == null -> "stale_request|${request.revision}"
            colors == null -> "no_palette|${request.revision}"
            colors.first.isEmpty() || colors.second.isEmpty() ->
                "empty_palette|${request.revision}"

            cachedColors != null -> "cache_hit|${request.revision}|${colors.first.size}|${colors.second.size}"
            else -> "extracted|${request.revision}|${colors.first.size}|${colors.second.size}"
        }
        HookLogger.i(
            TAG, "封面取色诊断: result=$paletteState"
        )
        HookLogger.dState(
            stateId = "CoverColorHelper.palette",
            tag = TAG,
            state = paletteState
        ) {
            "封面调色板结果: sessionRevision=${request.colorSession.revision}, " +
                    "artworkRevision=${request.revision}, source=${paletteState.substringBefore('|')}, " +
                    "onWhite=${colors?.first?.size ?: 0}, onBlack=${colors?.second?.size ?: 0}"
        }
        return current
    }

    @Synchronized
    fun isCurrentArtwork(request: ArtworkRequest): Boolean {
        return isCurrentArtworkLocked(request)
    }

    @Synchronized
    fun currentArtworkRequest(): ArtworkRequest? {
        return activeArtworkRequest?.takeIf(::isCurrentArtworkLocked)
    }

    private fun matchesArtworkMetadataLocked(
        current: ColorSession,
        mediaInfo: MediaMetadataHelper.MediaInfo
    ): Boolean {
        val currentIdentity = current.cacheKey.identity
        val incomingIdentity = mediaInfo.identity.normalized()
        val sessionMatch = currentIdentity.sameSessionAs(incomingIdentity)
        if (sessionMatch == false) return false

        val currentMediaId = currentIdentity.mediaId
        val incomingMediaId = incomingIdentity.mediaId
        if (currentMediaId != null && incomingMediaId != null &&
            currentMediaId != incomingMediaId
        ) {
            return false
        }
        val currentSongId = currentIdentity.songId
        val incomingSongId = incomingIdentity.songId
        if (currentSongId != null && incomingSongId != null &&
            currentSongId != incomingSongId
        ) {
            return false
        }

        // 会话 Token 或稳定条目 ID 比可变的展示元数据更可靠。
        // 一旦任一匹配，播放器可自由改变标题/作者展示字段。
        val mediaIdMatches = currentMediaId != null && currentMediaId == incomingMediaId
        val songIdMatches = currentSongId != null && currentSongId == incomingSongId
        if (sessionMatch == true || mediaIdMatches || songIdMatches) return true

        val mediaAlbum = mediaInfo.album.normalizeMediaText()
        if (current.album.isEmpty() || mediaAlbum.isEmpty()) {
            return false
        }
        return isCompatibleAlbum(current.album, mediaAlbum)
    }

    /**
     * 同一歌曲与同一封面只生成一次调色板。使用归一化内容指纹而不是 Bitmap
     * 实例或 generationId，暂停/恢复得到新 Bitmap 时仍可复用；若过渡期封面
     * 随后被真实封面替换，则允许纠正该歌曲的缓存。
     */
    private fun extractColors(
        bitmap: Bitmap,
        useGradient: Boolean,
        request: ArtworkRequest
    ): Pair<IntArray, IntArray>? {
        if (bitmap.isRecycled) return null
        val cachedColors = synchronized(this) {
            if (!isCurrentArtworkLocked(request)) return@synchronized null
            keyedCache[request.colorSession.cacheKey]
                ?.takeIf {
                    it.artworkFingerprint.isSimilarTo(request.fingerprint)
                }
                ?.colors
        }
        if (cachedColors != null) {
            return cachedColors.forGradient(useGradient)
        }

        val readableBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: run {
                HookLogger.dState(
                    stateId = "CoverColorHelper.extract",
                    tag = TAG,
                    state = "hardware_copy_failed"
                ) {
                    "封面调色板提取跳过: reason=hardware_bitmap_copy_failed"
                }
                return null
            }
        } else {
            bitmap
        }
        val paletteBitmap = scaleForPalette(readableBitmap)
        // V3 取色：自研 k-means 聚类，取图片原始代表色，
        // 不做白底压暗的人工暗色——亮红就是亮红
        val palette = io.github.proify.lyricon.colorextractor.palette.ColorExtractorImpl
            .extractThemePalette(paletteBitmap, MAX_PALETTE_COLORS)
        if (paletteBitmap !== readableBitmap) paletteBitmap.recycle()
        if (readableBitmap !== bitmap) readableBitmap.recycle()
        val rawColorsList = palette.rawColors
        // 3.0.935 修复：封面渐变色失效——之前只取 firstOrNull() 单色，
        // 渐变分支 size>1 永远不成立，"封面渐变"退化成和"封面取色"一样的单色。
        // 现在缓存完整代表色数组（最多 MAX_PALETTE_COLORS 个），
        // 渐变用多色，单色取首色（forGradient(false) 负责裁剪）。
        val rawColors = if (rawColorsList.isEmpty()) {
            intArrayOf(DEFAULT_COLOR)
        } else {
            rawColorsList.toIntArray()
        }
        val extractedColors = Pair(
            rawColors,                    // 主色 = 图片原始代表色（亮色保持亮，渐变保留多色）
            IntArray(rawColors.size) { i -> darken(rawColors[i], DARKEN_FACTOR) }  // 背景 = 各主色暗化
        )
        val colors = synchronized(this) {
            if (!isCurrentArtworkLocked(request)) return@synchronized null
            val latest = keyedCache[request.colorSession.cacheKey]
            if (latest?.artworkFingerprint?.isSimilarTo(request.fingerprint) == true) {
                latest.colors
            } else {
                extractedColors.also {
                    keyedCache[request.colorSession.cacheKey] = CacheEntry(
                        request.fingerprint,
                        it
                    )
                    trimCache()
                }
            }
        }
        if (colors == null) {
            HookLogger.dState(
                stateId = "CoverColorHelper.extract",
                tag = TAG,
                state = "discarded|${request.colorSession.revision}|${request.revision}"
            ) {
                "封面调色板未写入缓存: reason=stale_artwork_request, " +
                        "sessionRevision=${request.colorSession.revision}, artworkRevision=${request.revision}"
            }
        }
        return colors?.forGradient(useGradient)
    }

    @Synchronized
    fun getCachedColors(
        useGradient: Boolean,
        session: ColorSession
    ): Pair<IntArray, IntArray>? {
        if (!isCurrentSessionLocked(session)) return null
        return keyedCache[session.cacheKey]?.colors?.forGradient(useGradient)
    }

    @Synchronized
    fun getCachedColors(
        useGradient: Boolean,
        request: ArtworkRequest
    ): Pair<IntArray, IntArray>? {
        if (!isCurrentArtworkLocked(request)) return null
        return keyedCache[request.colorSession.cacheKey]
            ?.takeIf {
                it.artworkFingerprint.isSimilarTo(request.fingerprint)
            }
            ?.colors
            ?.forGradient(useGradient)
    }

    @Synchronized
    fun clearCache() {
        activeSession = null
        activeArtworkRequest = null
        sessionRevision++
        keyedCache.clear()
    }

    private fun isCurrentSessionLocked(session: ColorSession): Boolean {
        val current = activeSession ?: return false
        return current.revision == session.revision && current.cacheKey == session.cacheKey
    }

    private fun isCurrentArtworkLocked(request: ArtworkRequest): Boolean {
        val current = activeArtworkRequest ?: return false
        return isCurrentSessionLocked(request.colorSession) &&
                current.revision == request.revision &&
                current.colorSession.revision == request.colorSession.revision &&
                current.colorSession.cacheKey == request.colorSession.cacheKey
    }

    private fun buildTrackKey(songId: String?, mediaId: String?): String? {
        return when {
            songId != null && mediaId != null -> "song:$songId|media:$mediaId"
            songId != null -> "song:$songId"
            mediaId != null -> "media:$mediaId"
            else -> null
        }
    }

    private fun Pair<IntArray, IntArray>.forGradient(
        useGradient: Boolean
    ): Pair<IntArray, IntArray> {
        if (useGradient) return copyColors()
        val light = first.firstOrNull() ?: return copyColors()
        val dark = second.firstOrNull() ?: return copyColors()
        return Pair(
            intArrayOf(light),
            intArrayOf(dark)
        )
    }

    private fun Pair<IntArray, IntArray>.copyColors(): Pair<IntArray, IntArray> {
        return Pair(first.copyOf(), second.copyOf())
    }

    private fun String.normalizeMediaText(): String {
        return trim().lowercase().replace(WHITESPACE_REGEX, " ")
    }

    private fun debugText(value: String): String {
        if (value.isEmpty()) return "<empty>"
        return value.take(MAX_DEBUG_TEXT_LENGTH).let {
            if (value.length > MAX_DEBUG_TEXT_LENGTH) "$it…" else it
        }
    }

    private fun isCompatibleAlbum(first: String, second: String): Boolean {
        return first == second
    }

    /**
     * 将封面缩放成固定网格后比较平均像素差，忽略同一封面在尺寸、压缩上的轻微变化。
     */
    private fun bitmapFingerprint(bitmap: Bitmap): ArtworkFingerprint {
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            FINGERPRINT_GRID_SIZE,
            FINGERPRINT_GRID_SIZE,
            true
        )
        val readable = if (scaled.config == Bitmap.Config.HARDWARE) {
            scaled.copy(Bitmap.Config.ARGB_8888, false) ?: scaled
        } else {
            scaled
        }
        return try {
            val pixels = IntArray(FINGERPRINT_GRID_SIZE * FINGERPRINT_GRID_SIZE)
            readable.getPixels(
                pixels,
                0,
                FINGERPRINT_GRID_SIZE,
                0,
                0,
                FINGERPRINT_GRID_SIZE,
                FINGERPRINT_GRID_SIZE
            )
            ArtworkFingerprint(pixels)
        } finally {
            if (readable !== scaled && readable !== bitmap) readable.recycle()
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun trimCache() {
        while (keyedCache.size > MAX_CACHED_SONGS) {
            val firstKey = keyedCache.keys.firstOrNull() ?: return
            keyedCache.remove(firstKey)
        }
    }

    private val WHITESPACE_REGEX = Regex("\\s+")
    private const val MAX_PALETTE_COLORS = 4
    private const val PALETTE_MAX_DIM = 96
    private const val MAX_CACHED_SONGS = 16
    private const val FINGERPRINT_GRID_SIZE = 8
    private const val RGB_CHANNEL_COUNT = 3
    private const val MAX_AVERAGE_CHANNEL_DELTA = 12L
    private const val MAX_DEBUG_TEXT_LENGTH = 80
}
