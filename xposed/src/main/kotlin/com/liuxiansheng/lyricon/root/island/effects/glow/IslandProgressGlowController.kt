/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.effects.glow

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.common.media.MediaMetadataHelper
import com.liuxiansheng.lyricon.hyper.root.SystemUiEnhancementGate
import com.liuxiansheng.lyricon.hyper.root.utils.CoverColorHelper
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 进度光效控制器（V3 自研）。 */
internal object IslandProgressGlowController {
    private const val TAG = "IslandProgressGlowController"
    private const val BACKGROUND_VIEW_NAME = "DynamicIslandBackgroundView"
    private const val DEFAULT_PROGRESS_COLOR = 0xFF5B8CFF.toInt()
    private const val MIN_PROGRESS_UPDATE_INTERVAL_MS = 100L
    private const val PLAYBACK_SAMPLE_INTERVAL_MS = 500L
    private const val MAX_CACHED_PACKAGES = 8

    /** 老词幕同款 7 色彩虹（亮色系，与歌词彩虹一致），进度条"彩虹渐变"模式固定使用 */
    private val RAINBOW_COLORS = intArrayOf(
        android.graphics.Color.parseColor("#FF6B6B"), // 亮红
        android.graphics.Color.parseColor("#FFA94D"), // 亮橙
        android.graphics.Color.parseColor("#FFE066"), // 亮黄
        android.graphics.Color.parseColor("#69DB7C"), // 亮绿
        android.graphics.Color.parseColor("#74C0FC"), // 亮蓝
        android.graphics.Color.parseColor("#9775FA"), // 亮靛
        android.graphics.Color.parseColor("#DA77F2"), // 亮紫
    )

    /** 与字体取色一致的明黄亮度基准（HSV V=1.0） */
    private const val BRIGHT_HIGHLIGHT_V = 1.0f

    /** 与字体取色一致：保留色相/饱和度，只把 HSV 亮度提到 targetV */
    private fun brightenTo(color: Int, targetV: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        if (hsv[2] >= targetV) return color
        hsv[2] = targetV
        return android.graphics.Color.HSVToColor(hsv)
    }

    /** 与字体取色一致：老词幕同款暗化（亮度 × factor），保留色相/饱和度，alpha 不透明 */
    private fun darkenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private val backgroundViewsByRoot = WeakHashMap<ViewGroup, WeakReference<View>>()
    private val lastUpdateByRoot = WeakHashMap<ViewGroup, Long>()
    private val playbackProgressByPackage = HashMap<String, TimedPlaybackProgress>()

    fun update(
        rootView: ViewGroup,
        packageName: String,
        mediaInfo: MediaMetadataHelper.MediaInfo?,
        prefs: SharedPreferences
    ) {
        runCatching {
            updateInternal(rootView, packageName, mediaInfo, prefs)
        }.onFailure { e ->
            clear(rootView)
            HookLogger.e(TAG, "更新边缘光效进度条失败", e)
        }
    }

    private fun updateInternal(
        rootView: ViewGroup,
        packageName: String,
        mediaInfo: MediaMetadataHelper.MediaInfo?,
        prefs: SharedPreferences
    ) {
        if (!SystemUiEnhancementGate.isEnabled()) {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.gate",
                tag = TAG,
                state = "system_ui_disabled"
            ) {
                "进度光效未生效: reason=system_ui_enhancement_disabled"
            }
            clear(rootView)
            return
        }
        val progressEnabled = prefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_GLOW,
            RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_GLOW
        )
        val trackEnabled = prefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_TRACK,
            RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_TRACK
        )
        // 进度条与底圈各自独立：两者都关才整体清除
        if (!progressEnabled && !trackEnabled) {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.gate",
                tag = TAG,
                state = "preference_disabled"
            ) {
                "进度光效未生效: reason=preference_disabled"
            }
            clear(rootView)
            return
        }

        if (mediaInfo == null) {
            val now = SystemClock.uptimeMillis()
            val previous = synchronized(lastUpdateByRoot) { lastUpdateByRoot[rootView] }
            if (previous != null && now - previous < MIN_PROGRESS_UPDATE_INTERVAL_MS) return
            synchronized(lastUpdateByRoot) { lastUpdateByRoot[rootView] = now }
        } else {
            synchronized(lastUpdateByRoot) {
                lastUpdateByRoot[rootView] = SystemClock.uptimeMillis()
            }
        }

        val playbackProgress = resolvePlaybackProgress(
            rootView = rootView,
            packageName = packageName,
            forceRefresh = mediaInfo != null
        )

        val backgroundView = cachedBackgroundView(rootView) ?: findBackgroundView(rootView)?.also {
            replaceBackgroundView(rootView, it)
        } ?: run {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.input",
                tag = TAG,
                state = "background_missing"
            ) {
                "进度光效未生效: reason=background_view_missing"
            }
            clear(rootView)
            return
        }

        if (playbackProgress.fraction < 0f) {
            if (!trackEnabled) {
                HookLogger.dState(
                    stateId = "IslandProgressGlowController.input",
                    tag = TAG,
                    state = "invalid_progress"
                ) {
                    "进度光效未生效: reason=invalid_playback_progress"
                }
                clear(rootView)
                return
            }
            // 进度不可用但底圈独立开启：仅画轨道圈，不画进度弧
            val trackOnlyColors = resolveProgressColors(prefs, packageName, mediaInfo)
            val trackOnlyStyle = prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_PROGRESS_STYLE,
                RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_STYLE
            )
            IslandProgressGlowHooker.setMediaProgress(
                backgroundView,
                0f,
                trackOnlyColors.progress,
                trackOnlyColors.track,
                trackOnlyStyle,
                progressEnabled = false,
                trackEnabled = true
            )
            HookLogger.dState(
                stateId = "IslandProgressGlowController.track_only",
                tag = TAG,
                state = "track_only"
            ) {
                "仅底圈生效: reason=progress_unavailable_or_disabled"
            }
            return
        }

        val colors = resolveProgressColors(prefs, packageName, mediaInfo)
        val progressStyle = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_STYLE,
            RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_STYLE
        )
        IslandProgressGlowHooker.setMediaProgress(
            backgroundView,
            playbackProgress.fraction,
            colors.progress,
            colors.track,
            progressStyle,
            progressEnabled,
            trackEnabled
        )
        HookLogger.dState(
            stateId = "IslandProgressGlowController.applied",
            tag = TAG,
            state = "applied|$progressStyle|${colors.progress.size}|${colors.track}"
        ) {
            "进度光效已应用: style=$progressStyle, progressColors=${colors.progress.size}, " +
                    "track=#${Integer.toHexString(colors.track)}"
        }
    }

    fun clear(rootView: ViewGroup) {
        val backgroundView = synchronized(backgroundViewsByRoot) {
            backgroundViewsByRoot.remove(rootView)
        }?.get() ?: return
        IslandProgressGlowHooker.clearMediaProgress(backgroundView)
    }

    fun clearAll() {
        synchronized(backgroundViewsByRoot) { backgroundViewsByRoot.clear() }
        synchronized(lastUpdateByRoot) { lastUpdateByRoot.clear() }
        synchronized(playbackProgressByPackage) { playbackProgressByPackage.clear() }
        IslandProgressGlowHooker.clearAllMediaProgress()
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        val now = SystemClock.elapsedRealtime()
        synchronized(playbackProgressByPackage) {
            if (isPlaying) {
                playbackProgressByPackage.clear()
                return
            }
            val frozen = playbackProgressByPackage.mapValues { (_, sample) ->
                TimedPlaybackProgress(
                    progress = sample.estimate(now).copy(
                        isPlaying = false,
                        playbackSpeed = 0f
                    ),
                    sampledAt = now
                )
            }
            playbackProgressByPackage.clear()
            playbackProgressByPackage.putAll(frozen)
        }
    }

    private fun resolvePlaybackProgress(
        rootView: ViewGroup,
        packageName: String,
        forceRefresh: Boolean
    ): MediaMetadataHelper.PlaybackProgress {
        val now = SystemClock.elapsedRealtime()
        if (!forceRefresh) {
            val cached = synchronized(playbackProgressByPackage) {
                playbackProgressByPackage[packageName]
            }
            if (cached != null && now - cached.sampledAt < PLAYBACK_SAMPLE_INTERVAL_MS) {
                return cached.estimate(now)
            }
        }

        val progress = MediaMetadataHelper.getPlaybackProgress(rootView.context, packageName)
        synchronized(playbackProgressByPackage) {
            if (
                playbackProgressByPackage.size >= MAX_CACHED_PACKAGES &&
                packageName !in playbackProgressByPackage
            ) {
                playbackProgressByPackage.clear()
            }
            playbackProgressByPackage[packageName] = TimedPlaybackProgress(progress, now)
        }
        return progress
    }

    private fun cachedBackgroundView(rootView: ViewGroup): View? {
        val cached = synchronized(backgroundViewsByRoot) {
            backgroundViewsByRoot[rootView]
        }?.get() ?: return null
        var current: View? = rootView
        while (current != null) {
            if (current === cached) return cached
            current = current.parent as? View
        }
        return null
    }

    private fun findBackgroundView(rootView: ViewGroup): View? {
        (invokeNoArg(rootView, "getBackgroundView") as? View)?.let { return it }
        var current: View? = rootView
        while (current != null) {
            if (current.javaClass.simpleName == BACKGROUND_VIEW_NAME) return current
            current = current.parent as? View
        }
        return null
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? {
        return runCatching {
            target.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterTypes.isEmpty()
            }?.invoke(target)
        }.getOrNull()
    }

    private fun replaceBackgroundView(rootView: ViewGroup, backgroundView: View) {
        val previous = synchronized(backgroundViewsByRoot) {
            backgroundViewsByRoot.put(rootView, WeakReference(backgroundView))
        }?.get()
        if (previous != null && previous !== backgroundView) {
            IslandProgressGlowHooker.clearMediaProgress(previous)
        }
    }

    private fun resolveProgressColors(
        prefs: SharedPreferences,
        packageName: String,
        mediaInfo: MediaMetadataHelper.MediaInfo?
    ): ProgressColors {
        // 底圈始终跟随封面取色（占优色暗化 0.5 半透明），不受"彩虹渐变"开关控制；
        // 彩虹开关只决定进度弧用彩虹 7 色还是封面提亮色。
        val track = resolveCoverTrackColor(packageName, mediaInfo)

        // 彩虹模式：进度弧=固定老词幕 7 色彩虹（不依赖封面，纯固定色带，横向渐变）
        val useGradient = prefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_GRADIENT,
            RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_GRADIENT
        )
        if (useGradient) {
            return ProgressColors(
                progress = RAINBOW_COLORS,
                track = track
            ).also {
                HookLogger.dState(
                    stateId = "IslandProgressGlowController.colors",
                    tag = TAG,
                    state = "rainbow|fixed|${it.progress.size}"
                ) {
                    "进度光效颜色已解析: source=rainbow_fixed, colors=${it.progress.size}, " +
                            "track=#${Integer.toHexString(track)}(跟随封面)"
                }
            }
        }

        // 跟随封面：优先读共享调色板，进度弧=封面占优色提亮(V=1.0)
        val sharedPalette = CoverColorHelper.currentSession(packageName)
            ?.let { CoverColorHelper.getCachedColors(useGradient = false, session = it) }
        val sharedDominant = sharedPalette?.first?.firstOrNull()
        if (sharedDominant != null) {
            val bright = brightenTo(sharedDominant, BRIGHT_HIGHLIGHT_V)
            return ProgressColors(
                progress = intArrayOf(bright),
                track = track
            ).also {
                HookLogger.dState(
                    stateId = "IslandProgressGlowController.colors",
                    tag = TAG,
                    state = "cover|shared|#${Integer.toHexString(sharedDominant)}"
                ) {
                    "进度光效颜色已解析: source=shared_palette(同字体取色), " +
                            "dominant=#${Integer.toHexString(sharedDominant)}"
                }
            }
        }

        // 兜底：直接从当前封面 Bitmap 采样主色（共享调色板未就绪时），同样提亮
        val dominant = resolveOwnCoverDominant(mediaInfo)
        if (dominant != null) {
            val bright = brightenTo(dominant, BRIGHT_HIGHLIGHT_V)
            return ProgressColors(
                progress = intArrayOf(bright),
                track = track
            ).also {
                HookLogger.dState(
                    stateId = "IslandProgressGlowController.colors",
                    tag = TAG,
                    state = "cover|direct|#${Integer.toHexString(dominant)}"
                ) {
                    "进度光效颜色已解析: source=cover_direct, " +
                            "color=#${Integer.toHexString(dominant)}"
                }
            }
        }

        return ProgressColors(
            intArrayOf(DEFAULT_PROGRESS_COLOR),
            DEFAULT_TRACK_COLOR
        ).also {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.colors",
                tag = TAG,
                state = "fallback|no_cover"
            ) {
                "进度光效颜色回退: reason=no_album_art, package=$packageName"
            }
        }
    }

    /** 底圈取色：始终跟随封面（占优色提亮后暗化 0.5 半透明）；封面取不到时回退进度条默认色暗化，保证底圈始终有颜色 */
    private fun resolveCoverTrackColor(
        packageName: String,
        mediaInfo: MediaMetadataHelper.MediaInfo?
    ): Int {
        val sharedDominant = CoverColorHelper.currentSession(packageName)
            ?.let { CoverColorHelper.getCachedColors(useGradient = false, session = it) }
            ?.first?.firstOrNull()
        if (sharedDominant != null) {
            return withAlpha(darkenColor(brightenTo(sharedDominant, BRIGHT_HIGHLIGHT_V), 0.5f), COVER_TRACK_ALPHA)
        }
        val dominant = resolveOwnCoverDominant(mediaInfo)
        if (dominant != null) {
            return withAlpha(darkenColor(brightenTo(dominant, BRIGHT_HIGHLIGHT_V), 0.5f), COVER_TRACK_ALPHA)
        }
        // 封面取色关闭/取不到：用进度条默认色（与进度弧同色系）暗化 0.5 半透明，不再回退灰色
        return withAlpha(darkenColor(brightenTo(DEFAULT_PROGRESS_COLOR, BRIGHT_HIGHLIGHT_V), 0.5f), COVER_TRACK_ALPHA)
    }

    /** 从当前封面 Bitmap 独立采样主色（24x24 平均，忽略透明像素） */
    private fun resolveOwnCoverDominant(mediaInfo: MediaMetadataHelper.MediaInfo?): Int? {
        return runCatching {
            val bitmap = mediaInfo?.albumArt ?: return@runCatching null
            if (bitmap.isRecycled) return@runCatching null
            val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
            var r = 0L
            var g = 0L
            var b = 0L
            var count = 0L
            for (x in 0 until 24) {
                for (y in 0 until 24) {
                    val c = scaled.getPixel(x, y)
                    if (android.graphics.Color.alpha(c) < 16) continue
                    r += android.graphics.Color.red(c)
                    g += android.graphics.Color.green(c)
                    b += android.graphics.Color.blue(c)
                    count++
                }
            }
            scaled.recycle()
            if (count == 0L) return@runCatching null
            android.graphics.Color.rgb(
                (r / count).toInt(),
                (g / count).toInt(),
                (b / count).toInt()
            )
        }.getOrNull()
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    private data class ProgressColors(
        val progress: IntArray,
        val track: Int
    )

    private data class TimedPlaybackProgress(
        val progress: MediaMetadataHelper.PlaybackProgress,
        val sampledAt: Long
    ) {
        fun estimate(now: Long): MediaMetadataHelper.PlaybackProgress {
            if (
                !progress.isPlaying ||
                progress.position < 0L ||
                progress.playbackSpeed <= 0f
            ) {
                return progress
            }
            val elapsed = (now - sampledAt).coerceAtLeast(0L)
            val estimated = (
                    progress.position + elapsed * progress.playbackSpeed
                    ).toLong().coerceAtLeast(0L)
            val bounded = if (progress.duration > 0L) {
                estimated.coerceAtMost(progress.duration)
            } else {
                estimated
            }
            return progress.copy(position = bounded)
        }
    }

    private const val DEFAULT_TRACK_COLOR = 0x66757575
    private const val COVER_TRACK_ALPHA = 112
}
