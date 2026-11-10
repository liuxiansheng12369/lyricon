/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.source

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.common.media.MediaIdentity
import com.liuxiansheng.lyricon.hyper.common.media.MediaMetadataHelper
import com.liuxiansheng.lyricon.hyper.lyric.model.LyricMediaMetadata
import com.liuxiansheng.lyricon.hyper.lyric.model.Song
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.IRichLyricLine
import com.liuxiansheng.lyricon.hyper.lyric.source.LyricSink
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.content.IslandSlotContentFacade
import com.liuxiansheng.lyricon.hyper.root.island.effects.color.IslandMusicWaveColorHooker
import com.liuxiansheng.lyricon.hyper.root.island.renderer.IslandRenderer
import com.liuxiansheng.lyricon.hyper.root.media.CurrentMediaInfoResolver
import com.liuxiansheng.lyricon.hyper.root.plugin.PluginProcessingResult
import com.liuxiansheng.lyricon.hyper.root.plugin.PluginProcessingRequestKey
import com.liuxiansheng.lyricon.hyper.root.plugin.PluginProcessingRequestTracker
import com.liuxiansheng.lyricon.hyper.root.plugin.PluginRuntime
import com.liuxiansheng.lyricon.hyper.root.plugin.PluginSongMapper
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginMediaInfo
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginProcessingContext
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginSong
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginSongField
import com.liuxiansheng.lyricon.hyper.root.utils.CoverColorHelper
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicLong

class RootLyricSink(
    private val renderer: IslandRenderer,
    private val context: Context,
    private val prefs: SharedPreferences? = null,
    private val pluginRuntime: PluginRuntime? = null,
    /** 加强连接：断连检测触发后的重连动作（HookEntry 注入 lyriconSource.restart()） */
    private val onConnectionLost: (() -> Unit)? = null
) : LyricSink {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectCheckStarted = false
    private var lastPositionDispatchTimeMs = 0L
    private var pendingPosition: PositionSample? = null
    private var positionDispatchScheduled = false
    /** 最后一次收到"位置回调"的时间（含重复值），仅诊断，不参与外推基准 */
    private var lastCallbackTimeMs = 0L
    /** 本地时间轴 ticker 是否已排程 */
    private var positionTickScheduled = false
    private var playbackActive = false
    private var lastReceivedPosition = Long.MIN_VALUE
    private var lastReceivedPositionTimeMs = 0L
    private var lastReceivedPlaybackSpeed = Float.NaN
    private var lastDispatchedPosition = Long.MIN_VALUE
    private var lastDispatchedPlaybackSpeed = Float.NaN
    private var currentPlaybackSpeed = 1f
    private var activeMediaIdentity: MediaIdentity? = null
    /** 连续停滞重连次数：重连到第 3 次仍无数据就提示用户重启音乐 App */
    private var consecutiveStallCount = 0
    /** 本次断连是否已提示过（数据恢复后重置） */
    private var stallHintShown = false
    private var activeMediaSourceId: String? = null
    @Volatile
    private var latestPluginMediaInfo: PluginMediaInfo? = null
    private var sourcePluginSong: PluginSong? = null
    private var pendingRepeatedSourceSong: Song? = null
    private val pluginRequestTracker = PluginProcessingRequestTracker()
    private var activePluginRequestKey: PluginProcessingRequestKey? = null
    private var pluginStartScheduled = false
    private val pluginRequestGeneration = AtomicLong(0L)
    private val artworkColorRefreshRunnable = Runnable { renderer.updateTextColors() }
    private val pluginStartRunnable = Runnable {
        pluginStartScheduled = false
        startPluginProcessing()
    }
    private val positionDispatchRunnable = Runnable {
        positionDispatchScheduled = false
        val latest = pendingPosition ?: return@Runnable
        pendingPosition = null
        dispatchPosition(latest)
    }
    /** 本地时间轴：provider 不再推新位置时，用 elapsed × speed 把位置继续走 */
    private val positionTickRunnable = object : Runnable {
        override fun run() {
            positionTickScheduled = false
            if (!playbackActive) return
            val base = lastReceivedPosition
            if (base == Long.MIN_VALUE || lastReceivedPositionTimeMs <= 0L) return

            val now = SystemClock.uptimeMillis()
            val elapsed = now - lastReceivedPositionTimeMs
            if (elapsed > EXTRAPOLATE_MAX_MS) {
                // 位置值长期不更新：不再无限外推，改读 MediaSession 的真实位置重新锚定
                val sample = LyricInfoSource.currentPlaybackSample()
                if (sample != null) {
                    onPositionChanged(sample.first, sample.second)
                }
                schedulePositionTick()
                return
            }

            val speed = currentPlaybackSpeed
            dispatchPosition(PositionSample(base + (elapsed * speed).toLong(), speed), now)
            schedulePositionTick()
        }
    }

    private fun schedulePositionTick() {
        if (!playbackActive || positionTickScheduled) return
        if (lastReceivedPosition == Long.MIN_VALUE) return
        positionTickScheduled = true
        mainHandler.postDelayed(positionTickRunnable, POSITION_TICK_INTERVAL_MS)
    }

    private fun cancelPositionTick() {
        positionTickScheduled = false
        mainHandler.removeCallbacks(positionTickRunnable)
    }
    /** 加强连接：每 5 秒检查一次播放中位置是否推进（数据停滞判定断连） */
    private val reconnectCheckRunnable = object : Runnable {
        override fun run() {
            if (!reconnectCheckStarted) return
            // 仅播放态检测：暂停/未播放不视为断连（避免切歌间隙、长句停顿误判）
            if (playbackActive && lastReceivedPositionTimeMs > 0L) {
                val now = SystemClock.uptimeMillis()
                val stallMs = now - lastReceivedPositionTimeMs
                if (stallMs > RECONNECT_STALL_THRESHOLD_MS) {
                    // 先尝试用 MediaSession 的真实位置把链条接回来；接不回来才重连订阅
                    val sample = LyricInfoSource.currentPlaybackSample()
                    if (sample != null) {
                        HookLogger.i(TAG, "位置值停滞 ${stallMs}ms，已从 MediaSession 重新锚定")
                        resetStallState()
                        onPositionChanged(sample.first, sample.second)
                    } else {
                        HookLogger.w(
                            TAG,
                            "检测到歌词数据停滞（播放中 ${stallMs}ms 无位置推进），触发自动重连"
                        )
                        // 刷新基线防连发；重连成功前不重复触发
                        lastReceivedPositionTimeMs = now
                        consecutiveStallCount += 1
                        if (consecutiveStallCount >= STALL_HINT_THRESHOLD && !stallHintShown) {
                            stallHintShown = true
                            showStallHint()
                        }
                        runCatching { onConnectionLost?.invoke() }
                            .onFailure { e -> HookLogger.e(TAG, "自动重连动作执行失败", e) }
                    }
                }
            }
            reconnectHandler.postDelayed(this, RECONNECT_CHECK_INTERVAL_MS)
        }
    }

    /** 加强连接：启动断连检测（在 RootLyricSink 创建后调用一次） */
    fun startReconnectCheck() {
        if (reconnectCheckStarted) return
        reconnectCheckStarted = true
        reconnectHandler.post(reconnectCheckRunnable)
    }

    /** 加强连接：停止断连检测（进程清理时调用） */
    fun stopReconnectCheck() {
        reconnectCheckStarted = false
        reconnectHandler.removeCallbacks(reconnectCheckRunnable)
    }

    /** 数据恢复：清掉停滞计数与提示标记 */
    private fun resetStallState() {
        consecutiveStallCount = 0
        stallHintShown = false
    }

    /**
     * 连续多次自动重连仍拿不到歌词：提示用户重启音乐 App。
     * 原因：模块只能重连订阅通道，音乐 App 内的歌词钩子脱钩后必须重启该 App 才会重新注入。
     */
    private fun showStallHint() {
        HookLogger.w(
            TAG,
            "连续 $consecutiveStallCount 次重连仍无歌词，提示用户重启音乐 App（" +
                "pkg=${LyriconDataBridge.currentLyricPackageName.orEmpty()}）"
        )
        mainHandler.post {
            runCatching {
                android.widget.Toast.makeText(
                    context,
                    "词岛：歌词源已断开，岛里的歌词不会更新。请把音乐 App 强制停止后重新打开即可恢复",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }.onFailure { e -> HookLogger.e(TAG, "停滞提示 Toast 失败", e) }
        }
    }

    private companion object {
        const val TAG = "RootLyricSink"
        const val MIN_POSITION_DISPATCH_INTERVAL_MS = 33L
        const val MIN_VALID_PLAYBACK_SPEED = 0.1f
        const val MAX_VALID_PLAYBACK_SPEED = 4f
        const val SPEED_CHANGE_EPSILON = 0.01f
        const val INFERRED_SPEED_BLEND = 0.75f
        const val MAX_DEBUG_TEXT_LENGTH = 80
        const val ARTWORK_COLOR_REFRESH_DELAY_MS = 100L
        /** 加强连接：断连检测周期 */
        const val RECONNECT_CHECK_INTERVAL_MS = 5_000L
        /** 加强连接：播放中位置停滞超过该阈值判定断连 */
        const val RECONNECT_STALL_THRESHOLD_MS = 10_000L
        /** 连续停滞重连到第 3 次仍无数据，给一次"重启音乐 App"提示 */
        const val STALL_HINT_THRESHOLD = 3
        /** 本地时间轴外推周期 */
        const val POSITION_TICK_INTERVAL_MS = 60L
        /** 位置值超过该时长没变化，停止外推，改从 MediaSession 重新锚定 */
        const val EXTRAPOLATE_MAX_MS = 2_000L
    }

    private data class PositionSample(val position: Long, val playbackSpeed: Float)

    override fun onSongChanged(song: Song?) {
        cancelArtworkColorRefresh()
        cancelPendingPositionDispatch()
        cancelPositionTick()
        // 换歌视为新会话，重新计数停滞次数、允许再次提示
        resetStallState()
        lastReceivedPosition = Long.MIN_VALUE
        lastReceivedPositionTimeMs = 0L
        lastReceivedPlaybackSpeed = Float.NaN
        lastDispatchedPosition = Long.MIN_VALUE
        lastDispatchedPlaybackSpeed = Float.NaN
        currentPlaybackSpeed = 1f
        val ownedSong = song?.deepCopy()
        val incomingPluginSong = ownedSong?.let(PluginSongMapper::toPluginSong)
        val repeatedSourceEvent = incomingPluginSong != null &&
                incomingPluginSong == sourcePluginSong &&
                LyriconDataBridge.currentSong != null &&
                activeMediaIdentity != null
        if (repeatedSourceEvent) {
            // The DTO may be repeated for a replay or a source callback refresh. Keep the Core
            // state path alive so timing/current-line state is reset, but do not throw away an
            // accepted plugin enhancement before the following metadata event identifies the
            // media item. If that identity changes, onMetadata commits the raw Song below.
            pendingRepeatedSourceSong = ownedSong
            LyriconDataBridge.refreshSongEvent()
            renderer.updateLyricLine()
            HookLogger.i(
                TAG,
                "plugin_request event=request_deduplicated reason=repeated_source_event " +
                        "fingerprint=${incomingPluginSong.hashCode()}"
            )
            return
        }

        pendingRepeatedSourceSong = null
        cancelPendingPluginStart()
        invalidatePluginRequest(reason = if (song == null) "song_cleared" else "song_changed")
        // 歌曲切换：清空岛上残留的上一首歌词行，宽度回到最小（新歌词到达再撑开）。
        // 不清歌名/音乐信息层。
        renderer.onSongSwitched()
        activeMediaIdentity = null
        activeMediaSourceId = null
        latestPluginMediaInfo = null
        sourcePluginSong = incomingPluginSong
        LyriconDataBridge.updateSong(
            song = ownedSong,
            placeholderFormat = prefs?.getInt(
                RootConstants.KEY_HOOK_PLACEHOLDER_FORMAT,
                RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
            ) ?: RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
        )
        if (song == null) {
            sourcePluginSong = null
        } else {
            schedulePluginProcessing()
        }
        if (song == null) {
            endColorSession()
        }
    }

    override fun onLyricLine(line: IRichLyricLine) {
        LyriconDataBridge.updateLyricLine(line)
        renderer.updateLyricLine()
    }

    override fun onPlainText(text: String?) {

        LyriconDataBridge.updateLyric(text)
        renderer.updateLyricLine()
    }

    override fun onStop() {
        cancelArtworkColorRefresh()
        playbackActive = false
        cancelPendingPositionDispatch()
        cancelPositionTick()
        lastReceivedPosition = Long.MIN_VALUE
        lastReceivedPositionTimeMs = 0L
        lastReceivedPlaybackSpeed = Float.NaN
        lastDispatchedPosition = Long.MIN_VALUE
        lastDispatchedPlaybackSpeed = Float.NaN
        currentPlaybackSpeed = 1f
        lastCallbackTimeMs = 0L
        activeMediaIdentity = null
        activeMediaSourceId = null
        latestPluginMediaInfo = null
        sourcePluginSong = null
        pendingRepeatedSourceSong = null
        cancelPendingPluginStart()
        invalidatePluginRequest(reason = "stopped")
        endColorSession()
        renderer.clearAllViews()
        LyriconDataBridge.clearState()
    }

    override fun onMetadata(metadata: LyricMediaMetadata?) {
        val normalized = metadata?.normalized()
        normalized?.packageName?.let(LyriconDataBridge::updateLyricPackage)
        if (normalized?.isPackageOnlySnapshot() == true) {
            HookLogger.dState(
                stateId = "RootLyricSink.metadata",
                tag = TAG,
                state = "pending|${normalized.sourceId}|${normalized.packageName}"
            ) {
                "媒体元数据暂不可用: source=${normalized.sourceId}, " +
                        "package=${normalized.packageName ?: "<empty>"}, reason=fields_missing"
            }
            return
        }
        LyriconDataBridge.updateMediaMetadata(normalized)
        if (normalized == null) {
            latestPluginMediaInfo = null
            activeMediaIdentity = null
            activeMediaSourceId = null
            pendingRepeatedSourceSong = null
            cancelPendingPluginStart()
            invalidatePluginRequest(reason = "metadata_cleared")
            endColorSession()
            renderer.updateMetadata()
            return
        }
        // This is deliberately captured before Core supplements the internal media state. A
        // plugin may only receive the package supplied by this lyric source event, never a
        // MediaSession/identity package or the previous lyric package retained by the bridge.
        val sourcePackageName = normalized.packageName?.takeIf { it.isNotBlank() }
        val packageName = normalized.packageName
            ?: LyriconDataBridge.currentLyricPackageName
            ?: ""
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(
            context = context,
            packageName = packageName,
            logger = HookLogger,
            sourceMetadata = normalized
        )
        val mediaChanged = activeMediaIdentity?.isCompatibleWith(mediaInfo.identity) == false
        val sourceChanged = activeMediaSourceId != null &&
                activeMediaSourceId != normalized.sourceId
        val repeatedSourceSong = pendingRepeatedSourceSong
        pendingRepeatedSourceSong = null
        if (repeatedSourceSong != null && (mediaChanged || sourceChanged)) {
            HookLogger.i(
                TAG,
                "plugin_request event=request_cancelled reason=source_media_identity_changed"
            )
            cancelPendingPluginStart()
            invalidatePluginRequest(reason = "source_media_identity_changed")
            activeMediaIdentity = null
            activeMediaSourceId = null
            latestPluginMediaInfo = null
            LyriconDataBridge.updateSong(
                song = repeatedSourceSong,
                placeholderFormat = prefs?.getInt(
                    RootConstants.KEY_HOOK_PLACEHOLDER_FORMAT,
                    RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
                ) ?: RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
            )
            renderer.updateLyricLine()
        }
        LyriconDataBridge.applyResolvedMediaInfo(mediaInfo)
        latestPluginMediaInfo = mediaInfo.toPluginMediaInfo(sourcePackageName)
        if (mediaChanged && LyriconDataBridge.currentSong == null) {
            LyriconDataBridge.resetLyricContentForMediaChange()
            renderer.updateLyricLine()
        }
        activeMediaIdentity = mediaInfo.identity
        activeMediaSourceId = normalized.sourceId
        LyriconDataBridge.currentSongName = LyriconDataBridge.currentSong?.name
            ?.takeIf { it.isNotBlank() }
            ?: mediaInfo.title.takeIf { it.isNotBlank() }
        invalidateIfPluginInputChanged()
        schedulePluginProcessing()
        val colorSessionReady = updateColorSession(mediaInfo, reason = "metadata_changed")
        renderer.updateMetadata()
        if (colorSessionReady) scheduleArtworkColorRefresh()
    }

    /** Coalesces the synchronous onSongChanged -> onMetadata event chain on the main handler. */
    private fun schedulePluginProcessing() {
        if (pluginRuntime == null || pluginStartScheduled) return
        pluginStartScheduled = true
        mainHandler.post(pluginStartRunnable)
    }

    /** Starts one plugin pass for the current Core-owned Song snapshot. */
    private fun startPluginProcessing() {
        val baseSong = LyriconDataBridge.currentSong ?: return
        val expectedVersion = LyriconDataBridge.versionCounter.get()
        val requestKey = currentPluginRequestKey() ?: return
        if (activePluginRequestKey != null && activePluginRequestKey != requestKey) {
            invalidatePluginRequest(reason = "effective_input_changed")
        }
        if (pluginRequestTracker.isDuplicate(requestKey)) {
            HookLogger.i(
                TAG,
                "plugin_request event=request_deduplicated " +
                        "fingerprint=${requestKey.hashCode()}"
            )
            return
        }
        pluginRequestTracker.markStarted(requestKey)
        activePluginRequestKey = requestKey
        val expectedRequest = pluginRequestGeneration.incrementAndGet()
        val pluginSnapshot = PluginSongMapper.toPluginSong(baseSong.deepCopy())
        val processingMediaInfo = latestPluginMediaInfo
        HookLogger.i(
            TAG,
            "plugin_request event=request_started " +
                    "fingerprint=${requestKey.hashCode()} " +
                    "generation=${expectedRequest}"
        )
        pluginRuntime?.processSong(
            song = pluginSnapshot,
            processingContext = PluginProcessingContext(mediaInfo = processingMediaInfo)
        ) { processingResult: PluginProcessingResult? ->
            mainHandler.post {
                if (pluginRequestGeneration.get() != expectedRequest) {
                    logStalePluginResult(requestKey, "generation_changed")
                    return@post
                }
                if (LyriconDataBridge.versionCounter.get() != expectedVersion ||
                    LyriconDataBridge.currentSong !== baseSong
                ) {
                    logStalePluginResult(requestKey, "song_snapshot_changed")
                    if (activePluginRequestKey == requestKey) activePluginRequestKey = null
                    return@post
                }
                if (processingResult == null) {
                    if (activePluginRequestKey == requestKey) activePluginRequestKey = null
                    HookLogger.i(
                        TAG,
                        "plugin_request event=request_completed status=no_result " +
                                "fingerprint=${requestKey.hashCode()}"
                    )
                    return@post
                }
                val enhancedSong = PluginSongMapper.toInternalSong(
                    base = baseSong,
                    result = processingResult.result
                ) ?: run {
                    if (activePluginRequestKey == requestKey) activePluginRequestKey = null
                    HookLogger.i(
                        TAG,
                        "plugin_request event=request_completed status=invalid_result " +
                                "fingerprint=${requestKey.hashCode()}"
                    )
                    return@post
                }
                if (LyriconDataBridge.applyPluginEnhancement(
                        enhancedSong = enhancedSong,
                        expectedVersion = expectedVersion,
                        expectedBaseSong = baseSong,
                        changedFields = processingResult.result.changedFields
                    )
                ) {
                    val changedFields = processingResult.result.changedFields
                    if (changedFields.any { it in MEDIA_SONG_FIELDS }) {
                        renderer.updateMetadata()
                    }
                    if (changedFields.any { it in LYRIC_RENDER_FIELDS }) {
                        renderer.updateLyricLine()
                    }
                    if (activePluginRequestKey == requestKey) activePluginRequestKey = null
                    HookLogger.i(
                        TAG,
                        "plugin_request event=request_completed status=applied " +
                                "fingerprint=${requestKey.hashCode()}"
                    )
                } else {
                    logStalePluginResult(requestKey, "writeback_rejected")
                    if (activePluginRequestKey == requestKey) activePluginRequestKey = null
                }
            }
        }
    }

    private fun currentPluginRequestKey(): PluginProcessingRequestKey? {
        val song = sourcePluginSong ?: LyriconDataBridge.currentSong
            ?.deepCopy()
            ?.let(PluginSongMapper::toPluginSong)
            ?: return null
        return PluginProcessingRequestKey(
            sourceSong = song,
            mediaIdentity = activeMediaIdentity?.normalized(),
            mediaInfo = latestPluginMediaInfo,
            processorSetFingerprint = pluginRuntime?.processingSetFingerprint().orEmpty()
        )
    }

    private fun invalidateIfPluginInputChanged() {
        val active = activePluginRequestKey ?: return
        val current = currentPluginRequestKey() ?: return
        if (active != current) {
            invalidatePluginRequest(reason = "effective_input_changed")
        }
    }

    private fun invalidatePluginRequest(reason: String) {
        val active = activePluginRequestKey
        if (active != null) {
            HookLogger.i(
                TAG,
                "plugin_request event=request_cancelled reason=${reason} " +
                        "fingerprint=${active.hashCode()}"
            )
        }
        activePluginRequestKey = null
        pluginRequestTracker.reset()
        pluginRequestGeneration.incrementAndGet()
        pluginRuntime?.cancelActiveProcessing()
    }

    private fun cancelPendingPluginStart() {
        mainHandler.removeCallbacks(pluginStartRunnable)
        pluginStartScheduled = false
    }

    private fun logStalePluginResult(
        requestKey: PluginProcessingRequestKey,
        reason: String
    ) {
        HookLogger.i(
            TAG,
            "plugin_request event=stale_result_ignored reason=${reason} " +
                    "fingerprint=${requestKey.hashCode()}"
        )
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean, playbackSpeed: Float) {
        playbackActive = isPlaying
        explicitPlaybackSpeed(playbackSpeed)?.let { currentPlaybackSpeed = it }
        if (!isPlaying) {
            cancelPendingPositionDispatch()
            cancelPositionTick()
        } else {
            schedulePositionTick()
        }
        renderer.onPlaybackStateChanged(isPlaying)
    }

    override fun onPositionChanged(position: Long, playbackSpeed: Float) {
        val now = SystemClock.uptimeMillis()
        val resolvedSpeed = resolvePlaybackSpeed(position, playbackSpeed, now)
        lastCallbackTimeMs = now
        // 位置重新推进 = 歌词源活着，清掉停滞计数与提示标记
        if (consecutiveStallCount > 0 || stallHintShown) resetStallState()

        val sameValue = position == lastReceivedPosition &&
                abs(resolvedSpeed - lastReceivedPlaybackSpeed) < SPEED_CHANGE_EPSILON
        if (sameValue) {
            // provider 还在推，但位置值没前进：不刷新外推基准，交给本地时间轴继续走
            schedulePositionTick()
            return
        }

        lastReceivedPosition = position
        lastReceivedPositionTimeMs = now
        lastReceivedPlaybackSpeed = resolvedSpeed
        val lyricChanged = LyriconDataBridge.updatePosition(position)
        if (lyricChanged) {
            renderer.updateLyricLine()
        }
        val sample = PositionSample(position, resolvedSpeed)
        if (playbackActive) {
            dispatchPositionThrottled(sample, now)
        } else {
            dispatchPosition(sample, now)
        }
        schedulePositionTick()
    }

    private fun dispatchPositionThrottled(position: PositionSample, now: Long) {
        val elapsed = now - lastPositionDispatchTimeMs
        if (elapsed >= MIN_POSITION_DISPATCH_INTERVAL_MS) {
            dispatchPosition(position, now)
            return
        }

        pendingPosition = position
        if (positionDispatchScheduled) return

        positionDispatchScheduled = true
        mainHandler.postDelayed(
            positionDispatchRunnable,
            MIN_POSITION_DISPATCH_INTERVAL_MS - elapsed
        )
    }

    private fun dispatchPosition(
        sample: PositionSample,
        now: Long = SystemClock.uptimeMillis()
    ) {
        if (sample.position == lastDispatchedPosition &&
            abs(sample.playbackSpeed - lastDispatchedPlaybackSpeed) < SPEED_CHANGE_EPSILON
        ) return
        lastPositionDispatchTimeMs = now
        lastDispatchedPosition = sample.position
        lastDispatchedPlaybackSpeed = sample.playbackSpeed
        pendingPosition = null
        renderer.updatePosition(sample.position, sample.playbackSpeed)
    }

    private fun cancelPendingPositionDispatch() {
        mainHandler.removeCallbacks(positionDispatchRunnable)
        pendingPosition = null
        positionDispatchScheduled = false
    }

    private fun resolvePlaybackSpeed(position: Long, reportedSpeed: Float, now: Long): Float {
        explicitPlaybackSpeed(reportedSpeed)?.let {
            currentPlaybackSpeed = it
            return it
        }

        if (playbackActive && lastReceivedPosition != Long.MIN_VALUE &&
            lastReceivedPositionTimeMs > 0L && now > lastReceivedPositionTimeMs &&
            position >= lastReceivedPosition
        ) {
            val elapsedMs = now - lastReceivedPositionTimeMs
            val inferred = (position - lastReceivedPosition).toFloat() / elapsedMs.toFloat()
            if (inferred in MIN_VALID_PLAYBACK_SPEED..MAX_VALID_PLAYBACK_SPEED) {
                currentPlaybackSpeed = currentPlaybackSpeed * (1f - INFERRED_SPEED_BLEND) +
                        inferred * INFERRED_SPEED_BLEND
            }
        }
        return currentPlaybackSpeed
    }

    private fun explicitPlaybackSpeed(speed: Float): Float? =
        speed.takeIf { it.isFinite() && it in MIN_VALID_PLAYBACK_SPEED..MAX_VALID_PLAYBACK_SPEED }

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

    private fun updateColorSession(
        mediaInfo: MediaMetadataHelper.MediaInfo,
        reason: String
    ): Boolean {
        val packageName = mediaInfo.identity.packageName
        val previousRevision = CoverColorHelper.currentSession()?.revision
        val current = CoverColorHelper.activateSession(
            mediaInfo = mediaInfo
        ) ?: run {
            endColorSession()
            HookLogger.dState(
                stateId = "RootLyricSink.colorSession.invalid",
                tag = TAG,
                state = "$packageName|${mediaInfo.title}|${mediaInfo.artist}|${mediaInfo.identity}"
            ) {
                "颜色会话未更新: reason=$reason, package=${packageName.ifEmpty { "<empty>" }}, " +
                        "title=\"${debugText(mediaInfo.title)}\", " +
                        "artist=\"${debugText(mediaInfo.artist)}\", identity=${mediaInfo.identity}"
            }
            return false
        }
        HookLogger.dState(
            stateId = "RootLyricSink.colorSession",
            tag = TAG,
            state = "${current.revision}|${current.mediaKey}|${current.title}|${current.artist}"
        ) {
            "颜色会话已同步: reason=$reason, revision=${current.revision}, " +
                    "revisionChanged=${previousRevision != current.revision}, " +
                    "package=${packageName.ifEmpty { "<empty>" }}, " +
                    "title=\"${debugText(mediaInfo.title)}\", " +
                    "artist=\"${debugText(mediaInfo.artist)}\", " +
                    "album=\"${debugText(mediaInfo.album)}\", " +
                    "identity=${mediaInfo.identity}, mediaKeyHash=${current.mediaKey.hashCode()}"
        }
        if (previousRevision != current.revision) {
            IslandSlotContentFacade.invalidate()
            IslandMusicWaveColorHooker.refresh()
            renderer.updateTextColors()
        }
        return true
    }

    private fun endColorSession() {
        cancelArtworkColorRefresh()
        if (CoverColorHelper.endSession()) {
            IslandSlotContentFacade.invalidate()
            IslandMusicWaveColorHooker.refresh()
        }
    }

    private fun scheduleArtworkColorRefresh() {
        mainHandler.removeCallbacks(artworkColorRefreshRunnable)
        mainHandler.postDelayed(
            artworkColorRefreshRunnable,
            ARTWORK_COLOR_REFRESH_DELAY_MS
        )
    }

    private fun cancelArtworkColorRefresh() {
        mainHandler.removeCallbacks(artworkColorRefreshRunnable)
    }

    private fun LyricMediaMetadata.isPackageOnlySnapshot(): Boolean =
        songId == null &&
                title == null &&
                artist == null &&
                album == null &&
                duration == null &&
                sessionToken == null &&
                mediaId == null

    private val MEDIA_SONG_FIELDS = setOf(
        PluginSongField.ID,
        PluginSongField.NAME,
        PluginSongField.ARTIST,
        PluginSongField.ALBUM,
        PluginSongField.DURATION,
        PluginSongField.METADATA
    )

    private val LYRIC_RENDER_FIELDS = setOf(
        PluginSongField.NAME,
        PluginSongField.ARTIST,
        PluginSongField.DURATION,
        PluginSongField.METADATA,
        PluginSongField.LYRICS
    )

}

/**
 * Builds the plugin DTO from resolved Core media fields while retaining the lyric source's package
 * boundary. [sourcePackageName] is intentionally supplied separately so a MediaIdentity package
 * cannot leak into plugins when the source did not provide one.
 */
internal fun MediaMetadataHelper.MediaInfo.toPluginMediaInfo(
    sourcePackageName: String?
): PluginMediaInfo? = PluginMediaInfo(
    title = title.takeIf { it.isNotBlank() },
    artist = artist.takeIf { it.isNotBlank() },
    album = album.takeIf { it.isNotBlank() },
    duration = duration.takeIf { it > 0L },
    sourcePackageName = sourcePackageName?.takeIf { it.isNotBlank() }
).takeIf { info ->
    info.title != null ||
            info.artist != null ||
            info.album != null ||
            info.duration != null ||
            info.sourcePackageName != null
}


