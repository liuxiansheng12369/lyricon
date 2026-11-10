/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.source

import android.app.Application
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.liuxiansheng.lyricon.hyper.lyric.model.LyricMediaMetadata
import com.liuxiansheng.lyricon.hyper.lyric.model.Song
import com.liuxiansheng.lyricon.hyper.lyric.source.LyricSink
import com.liuxiansheng.lyricon.hyper.lyric.source.LyricSource
import com.liuxiansheng.lyricon.hyper.root.island.renderer.BaseIslandRenderer
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import com.liuxiansheng.lyricon.xposed.systemui.media.LyricInfoParser
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * LyricInfo 歌词源：从 MediaSession 的 metadata["lyricInfo"] 读取歌词。
 *
 * 面向 ColorOS Live Lyrics Providers（io.github.andrealtb.coloroslyrics.provider.*）
 * 及网易云/汽水等会把歌词 JSON 塞进 MediaSession metadata 的音乐 APP。
 * 与老词目 CurrentMediaInfo 同机制：只读 MediaSession，不 Hook 音乐 App；
 * 读到 lyricInfo → LyricInfoParser 解析 → 推给 LyricSink（超级岛/状态栏歌词）。
 */
class LyricInfoSource : LyricSource {

    companion object {
        private const val TAG = "LyricInfoSource"
        private const val POLL_INTERVAL_MS = 3_000L
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        /** 供 LyriconSource 判断“会话里还有没有词”，避免按固定秒数瞎清屏 */
        @Volatile
        private var liveInstance: LyricInfoSource? = null

        fun hasLiveLyricInfo(): Boolean = liveInstance?.readableLyricInfo() != null

        /** 供 RootLyricSink 在位置值停滞时取一次真实位置（position, speed）；读不到返回 null */
        fun currentPlaybackSample(): Pair<Long, Float>? = liveInstance?.readCurrentPlaybackSample()
    }

    private fun com.liuxiansheng.lyricon.lyric.model.Song.toLocalSong(): Song {
        val jsonString = json.encodeToString(this)
        return json.decodeFromString(jsonString)
    }

    override val id = "lyricinfo"
    override val displayName = "LyricInfo"

    @Volatile
    private var sink: LyricSink? = null

    @Volatile
    private var manager: MediaSessionManager? = null

    @Volatile
    private var activeControllers: List<MediaController> = emptyList()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val controllerCallbacks =
        ConcurrentHashMap<MediaController, MediaController.Callback>()

    @Volatile
    private var lastLyricInfoPkg = ""

    /** 最近一次实际读到歌词的 controller，会话掉出 active 列表后仍可作为读源 */
    @Volatile
    private var lastKnownController: MediaController? = null

    /** 上一次 lyricInfo 的指纹，用于轮询去重（内容没变就不重复下发） */
    @Volatile
    private var lastLyricInfoHash: Int = 0

    /** 上一次见到的曲目身份，用于"换歌但没歌词"时顶掉上一首歌词 */
    @Volatile
    private var lastSeenTitle: String? = null
    @Volatile
    private var lastSeenArtist: String? = null
    @Volatile
    private var lastSeenDurationMs: Long = 0L

    @Volatile
    private var appInstance: Application? = null

    /** 与 LyriconSource.initialize(app, prefs) 相同：由 HookEntry 注入 Application。 */
    fun initialize(app: Application) {
        this.appInstance = app
    }

    override fun isAvailable(): Boolean = true

    override fun start(sink: LyricSink) {
        liveInstance = this
        if (manager != null) {
            HookLogger.d(TAG, "跳过重复启动: reason=already_running")
            return
        }
        this.sink = sink
        val app = appInstance ?: run {
            HookLogger.w(TAG, "数据源启动延后: reason=application_unavailable")
            return
        }
        val m = runCatching {
            app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        }.getOrNull()
        if (m == null) {
            HookLogger.w(TAG, "无法获取 MediaSessionManager")
            return
        }
        manager = m
        val active = runCatching { m.getActiveSessions(null) }.getOrDefault(emptyList())
        activeControllers = active
        registerCallbacks(active)
        runCatching { m.addOnActiveSessionsChangedListener(activeSessionsListener, null) }
        // 启动后延迟读一次当前 metadata（音乐还在播放的话）
        mainHandler.postDelayed({ reReadActiveLyricInfo() }, 800)
        mainHandler.postDelayed(lyricInfoPollRunnable, POLL_INTERVAL_MS)
        HookLogger.i(TAG, "LyricInfo 歌词源已启动: sessions=${active.size}")
    }

    override fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(activeSessionsListener) }
        controllerCallbacks.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        controllerCallbacks.clear()
        mainHandler.removeCallbacksAndMessages(null)
        manager = null
        activeControllers = emptyList()
        lastLyricInfoPkg = ""
        lastKnownController = null
        lastLyricInfoHash = 0
        lastSeenTitle = null
        lastSeenArtist = null
        lastSeenDurationMs = 0L
        sink?.onStop()
        sink = null
        liveInstance = null
        HookLogger.d(TAG, "LyricInfo 歌词源已停止")
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        activeControllers = controllers.orEmpty()
        registerCallbacks(controllers.orEmpty())
    }

    private fun registerCallbacks(controllers: List<MediaController>) {
        // 暂停久了会话会掉出 getActiveSessions()，此时不能注销回调：
        // 保留引用，暂停期间/恢复瞬间的 metadata 更新仍然收得到。
        controllers.forEach { controller ->
            lastKnownController = controller
            if (!controllerCallbacks.containsKey(controller)) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        if (metadata != null) handleMetadata(controller, metadata)
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        handlePlayback(controller, state)
                    }
                }
                runCatching {
                    controller.registerCallback(callback, Handler(Looper.getMainLooper()))
                }
                controllerCallbacks[controller] = callback
            }
        }
    }

    private fun handleMetadata(controller: MediaController, metadata: MediaMetadata) {
        val lyricInfo = metadata.getString("lyricInfo")
        // 换歌识别：即使这首次/这一轮没有 lyricInfo，也要按歌名把"上一首歌词"顶掉。
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val duration = if (metadata.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
            metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        } else 0L
        val identityChanged = title != null && (
            title != lastSeenTitle ||
                artist != lastSeenArtist ||
                (duration > 0L && duration != lastSeenDurationMs)
            )
        lastSeenTitle = title
        lastSeenArtist = artist
        lastSeenDurationMs = duration
        if (lyricInfo.isNullOrBlank()) {
            // 无歌词（抖音/纯音乐/未取到词）：推一条只有歌名、歌词为空的 Song，
            // 由 SongPreprocessor 填「纯音乐，请欣赏」，避免岛里一直挂着上一首的歌词。
            if (identityChanged && controller == pickReadableController()) {
                notifyNoLyrics(controller, title, artist, album, duration)
            }
            return
        }
        lastKnownController = controller
        val hash = lyricInfo.hashCode()
        if (hash == lastLyricInfoHash) return      // 轮询到同样的内容，不重复下发
        lastLyricInfoHash = hash
        val pkg = controller.packageName ?: ""
        val song = LyricInfoParser.parse(lyricInfo)
        if (song != null) {
            if (pkg != lastLyricInfoPkg) {
                lastLyricInfoPkg = pkg
                HookLogger.d(TAG, "收到 lyricInfo 歌词: pkg=$pkg, song=${song.name}, lines=${song.lyrics?.size ?: 0}")
            }
            val local = song.toLocalSong()
            sink?.onSongChanged(local)
            sink?.onMetadata(
                local.let {
                    LyricMediaMetadata(
                        sourceId = id,
                        packageName = pkg,
                        songId = it.id,
                        title = it.name,
                        artist = it.artist,
                        album = it.album,
                        duration = it.duration.takeIf { d -> d > 0L }
                    )
                }
            )
            BaseIslandRenderer.refreshActiveIsland()
        }
    }

    /** 当前媒体没有歌词：只推歌名，歌词留空 → 显示「歌名 + 纯音乐，请欣赏」。 */
    private fun notifyNoLyrics(
        controller: MediaController,
        title: String?,
        artist: String?,
        album: String?,
        duration: Long,
    ) {
        if (title == null) return
        val pkg = controller.packageName ?: ""
        lastLyricInfoHash = 0
        val song = com.liuxiansheng.lyricon.hyper.lyric.model.Song(
            id = "$pkg:$title",
            name = title,
            artist = artist,
            album = album,
            duration = duration,
        )
        sink?.onSongChanged(song)
        sink?.onMetadata(
            LyricMediaMetadata(
                sourceId = id,
                packageName = pkg,
                songId = song.id,
                title = title,
                artist = artist,
                album = album,
                duration = duration.takeIf { d -> d > 0L }
            )
        )
        BaseIslandRenderer.refreshActiveIsland()
        HookLogger.d(TAG, "无 lyricInfo，按歌名占位: pkg=$pkg title=$title")
    }

    private fun handlePlayback(controller: MediaController, state: PlaybackState?) {
        val playing = state?.state == PlaybackState.STATE_PLAYING
        lastKnownController = controller
        sink?.onPlaybackStateChanged(playing)
        if (playing) {
            sink?.onPositionChanged(state?.position ?: 0L)
        }
    }

    /** 兜底轮询：内容指纹没变就跳过，暂停/锁屏/会话掉队都能自愈。 */
    private val lyricInfoPollRunnable = object : Runnable {
        override fun run() {
            runCatching { pollOnce() }
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun pollOnce() {
        val ctrl = pickReadableController() ?: return
        val md = ctrl.metadata ?: return
        handleMetadata(ctrl, md)
    }

    /** 优先级：active 里正在播的 → active 里第一个 → 最近一次读到的 */
    private fun pickReadableController(): MediaController? {
        val playing = activeControllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        return playing
            ?: activeControllers.firstOrNull()
            ?: lastKnownController
    }

    /** 从最合适的 controller 里真读一次 lyricInfo；读不到返回 null。 */
    private fun readableLyricInfo(): String? {
        val ctrl = pickReadableController() ?: return null
        return ctrl.metadata?.getString("lyricInfo")?.takeIf { it.isNotBlank() }
    }

    /** 读一次 MediaSession 的真实播放位置（只在播放态有效），给 sink 做重锚 */
    private fun readCurrentPlaybackSample(): Pair<Long, Float>? {
        val ctrl = pickReadableController() ?: return null
        val state = ctrl.playbackState ?: return null
        if (state.state != PlaybackState.STATE_PLAYING) return null
        val speed = state.playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
        return state.position to speed
    }

    /** 启动后读一次当前活跃会话的 lyricInfo（重启 SystemUI 时歌词还在播放的话） */
    private fun reReadActiveLyricInfo() {
        val ctrl = pickReadableController() ?: return
        val md = ctrl.metadata ?: return
        handleMetadata(ctrl, md)
    }
}
