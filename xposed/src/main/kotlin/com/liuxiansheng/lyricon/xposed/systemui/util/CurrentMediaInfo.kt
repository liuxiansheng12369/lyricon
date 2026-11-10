/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.xposed.systemui.util

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.liuxiansheng.lyricon.xposed.systemui.media.LyricInfoParser
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 当前播放媒体信息管理器
 * 保存当前播放的歌名、艺术家、专辑、时长、播放位置，供超级岛内容模式使用
 *
 * Ported from lyricon-master (Proify, Tomakino).
 */
object CurrentMediaInfo {
    private const val TAG = "CurrentMediaInfo"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun com.liuxiansheng.lyricon.lyric.model.Song.toHyperSong(): com.liuxiansheng.lyricon.hyper.lyric.model.Song {
        val jsonString = json.encodeToString(this)
        return json.decodeFromString(jsonString)
    }

    /** 轮询次数 */
    private var pollCount = 0

    /** 轮询读 lyricInfo 的 Runnable */
    private var pollRunnable: Runnable? = null

    /** 轮询检查 App 是否退出的 Runnable */
    private var exitCheckRunnable: Runnable? = null

    /** 当前歌名 */
    @Volatile
    var title: String = "未在播放"
        private set

    /** 当前艺术家 */
    @Volatile
    var artist: String = ""
        private set

    /** 当前专辑 */
    @Volatile
    var album: String = ""
        private set

    /** 歌曲总时长（毫秒） */
    @Volatile
    var duration: Long = 0L
        private set

    /** 当前播放位置（毫秒） */
    @Volatile
    var position: Long = 0L
        private set

    /** 是否正在播放 */
    @Volatile
    var isPlaying: Boolean = false
        private set

    /** 当前播放应用包名 */
    @Volatile
    var packageName: String = ""
        private set

    /** 信息更新监听器 */
    private val listeners = CopyOnWriteArraySet<OnMediaInfoChangeListener>()

    /** 是否已初始化 */
    private var initialized = false

    /** 最后一个处于播放中状态的会话包名 */
    @Volatile
    private var lastPlayingPackage: String = ""

    /** 当前 MediaController 引用 */
    @Volatile
    private var currentController: MediaController? = null

    /** 已注册过回调的会话（按 token 去重，避免重复注册/越注册越多） */
    private val registeredTokens = java.util.concurrent.ConcurrentHashMap<android.media.session.MediaSession.Token, Boolean>()

    /** 上次推过的 provider 包名 */
    private var lastLyricInfoPkg: String = ""

    /** Session 是否销毁 */
    private var sessionDestroyed = false

    /** position 定时推送 Handler */
    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionRunnable = object : Runnable {
        override fun run() {
            val ctrl = currentController
            val state = ctrl?.playbackState
            if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                val base = state.position
                val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
                val pos = (base + elapsed * state.playbackSpeed).toLong().coerceAtLeast(0L)
                position = pos
                // 推给上岛
                com.liuxiansheng.lyricon.hyper.root.HookEntry.activeSink?.onPositionChanged(pos, state.playbackSpeed)
            }
            positionHandler.postDelayed(this, 100)
        }
    }

    /**
     * 初始化，注册媒体会话监听器
     */
    fun initialize(context: android.content.Context) {
        if (initialized) return
        initialized = true

        Log.d(TAG, "=== CurrentMediaInfo 初始化 ===")

        val mediaSessionManager = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        val activeSessions = mediaSessionManager.getActiveSessions(null) ?: emptyList()

        Log.d(TAG, "活跃媒体会话数: ${activeSessions.size}")

        // 注册监听
        mediaSessionManager.addOnActiveSessionsChangedListener({ controllers ->
            Log.d(TAG, "活跃媒体会话变化: ${controllers?.size ?: 0}")
            controllers?.forEach { controller ->
                registerController(controller)
            }
        }, null)

        // 注册当前活跃的会话
        activeSessions.forEach { controller ->
            registerController(controller)
        }

        // 重启后延迟读一次当前 metadata
        positionHandler.postDelayed({
            Log.d(TAG, "=== 重启后重新读 metadata ===")
            val c = currentController ?: run {
                Log.d(TAG, "currentController 为 null")
                return@postDelayed
            }
            // V3 修复：重启后也确保 position 周期推送在跑（媒体变化监听可能不触发）
            positionHandler.removeCallbacks(positionRunnable)
            positionHandler.postDelayed(positionRunnable, 300)
            val md = c.metadata ?: run {
                Log.d(TAG, "metadata 为 null")
                return@postDelayed
            }
            val newTitle = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val newArtist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val newPackage = c.packageName ?: ""
            Log.d(TAG, "title=$newTitle, artist=$newArtist, pkg=$newPackage")

            if (newTitle.isNotBlank()) {
                title = newTitle
                artist = newArtist
                packageName = newPackage

                val lyricInfo = md.getString("lyricInfo") ?: ""
                Log.d(TAG, "lyricInfo=${lyricInfo.take(100)}...")

                if (lyricInfo.isNotBlank()) {
                    val song = LyricInfoParser.parse(lyricInfo)
                    if (song != null) {
                        lastLyricInfoPkg = newPackage
                        Log.d(TAG, "解析成功: song=${song.name}, lines=${song.lyrics?.size}")
                        // 推给上岛
                        com.liuxiansheng.lyricon.hyper.root.HookEntry.activeSink?.onSongChanged(song.toHyperSong())
                    } else {
                        Log.w(TAG, "解析失败")
                    }
                } else {
                    Log.w(TAG, "lyricInfo 为空")
                }
            }
        }, 1000)
    }

    private fun registerController(controller: MediaController) {
        val token = controller.sessionToken
        if (registeredTokens.containsKey(token)) {
            // 同一个会话重复回调：只有它真在播放时才接管，避免把已选主会话又打乱
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) takeOver(controller)
            return
        }
        registeredTokens[token] = true
        Log.d(TAG, "注册 controller: pkg=${controller.packageName}")

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                if (metadata != null) handleMetadata(controller, metadata)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (state != null) handlePlaybackState(controller, state)
            }
        }

        controller.registerCallback(callback, Handler(Looper.getMainLooper()))

        // V3 修复（ColorOS 卡拉OK歌词源）：启动 position 周期推送。
        // 老词梦在 onMediaChanged 里 postDelayed(positionRunnable)，
        // V3 移植后该启动动作丢失，导致歌词行/逐字永远停在第一句。
        positionHandler.removeCallbacks(positionRunnable)
        positionHandler.postDelayed(positionRunnable, 300)

        // 只有"正在播放"的会话才算当前会话；暂停的会话不能顶替正在播的那个
        val state = controller.playbackState
        if (state != null && state.state == PlaybackState.STATE_PLAYING) {
            takeOver(controller)
        } else if (currentController == null) {
            // 还没有主会话时，先拿它兜底（暂停态也要能显示歌名）
            Log.d(TAG, "兜底 controller: pkg=${controller.packageName}")
            currentController = controller
            controller.metadata?.let { handleMetadata(controller, it, force = true) }
        }
    }

    /** 把正在播放的会话设为主会话，并重读它的 metadata */
    private fun takeOver(controller: MediaController) {
        if (currentController !== controller) {
            Log.d(TAG, "主会话切换 -> pkg=${controller.packageName}")
            currentController = controller
        }
        positionHandler.removeCallbacks(positionRunnable)
        positionHandler.postDelayed(positionRunnable, 300)
        controller.metadata?.let { handleMetadata(controller, it, force = true) }
        val state = controller.playbackState
        if (state != null && state.state == PlaybackState.STATE_PLAYING) {
            position = state.position
            isPlaying = true
            lastPlayingPackage = controller.packageName ?: ""
        }
    }

    private fun handleMetadata(controller: MediaController, metadata: MediaMetadata, force: Boolean = false) {
        // 非主会话且它没在播放时，不许覆盖当前歌名/时长/位置（否则岛上的歌词、听歌记录会张冠李戴）
        val playingNow = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (!force && controller !== currentController && !playingNow) return
        if (playingNow && controller !== currentController) currentController = controller
        val newTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val newArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val newAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val newDuration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 } ?: 0L
        val newPackage = controller.packageName ?: ""

        Log.d(TAG, "=== onMetadataChanged ===")
        Log.d(TAG, "title=$newTitle, artist=$newArtist, pkg=$newPackage")

        if (newTitle != title || newArtist != artist || newAlbum != album || newDuration != duration) {
            title = newTitle
            artist = newArtist
            album = newAlbum
            duration = newDuration
            packageName = newPackage
            listeners.forEach { it.onMediaInfoChanged(newTitle, newArtist, newPackage) }
        }

        // 读 lyricInfo
        val lyricInfoJson = metadata.getString("lyricInfo")
        Log.d(TAG, "lyricInfoJson=${lyricInfoJson?.take(100)}...")

        if (lyricInfoJson.isNullOrBlank()) {
            // 没 lyricInfo，启动轮询等
            if (pollRunnable == null) {
                pollRunnable = Runnable {
                    pollCount++
                    if (pollCount >= 50) return@Runnable
                    currentController?.metadata?.let { md ->
                        val li = md.getString("lyricInfo")
                        if (!li.isNullOrBlank()) {
                            handleMetadata(currentController!!, md)
                        } else {
                            positionHandler.postDelayed(pollRunnable!!, 200)
                        }
                    } ?: run {
                        positionHandler.postDelayed(pollRunnable!!, 200)
                    }
                }
            }
            pollCount = 0
            positionHandler.removeCallbacks(pollRunnable!!)
            positionHandler.postDelayed(pollRunnable!!, 500)
        } else {
            val song = LyricInfoParser.parse(lyricInfoJson)
            val lines = song?.lyrics
            if (song != null && !lines.isNullOrEmpty()) {
                Log.d(TAG, "Got lyricInfo from $newPackage, song=${song.name}, lines=${lines.size}")
                pollRunnable?.let { positionHandler.removeCallbacks(it) }
                lastLyricInfoPkg = newPackage
                // 推给上岛
                com.liuxiansheng.lyricon.hyper.root.HookEntry.activeSink?.onSongChanged(song.toHyperSong())
            } else {
                Log.w(TAG, "解析失败或歌词为空")
                if (lastLyricInfoPkg.isNotBlank()) {
                    lastLyricInfoPkg = ""
                    com.liuxiansheng.lyricon.hyper.root.HookEntry.activeSink?.onSongChanged(null)
                }
            }
        }
    }

    private fun handlePlaybackState(controller: MediaController, state: PlaybackState) {
        val pkg = controller.packageName ?: ""
        val playing = state.state == PlaybackState.STATE_PLAYING
        if (playing) {
            // 谁在播谁就是主会话（避免被暂停的会话长期占着位置，导致歌词/记录全错位）
            if (controller !== currentController) takeOver(controller)
            lastPlayingPackage = pkg
            position = state.position
            isPlaying = true
            com.liuxiansheng.lyricon.hyper.root.HookEntry.activeSink?.onPlaybackStateChanged(true, state.playbackSpeed)
            listeners.forEach { it.onPlaybackStateChanged(position, isPlaying) }
        } else if (controller === currentController || pkg == lastPlayingPackage) {
            position = state.position
            isPlaying = false
            com.liuxiansheng.lyricon.hyper.root.HookEntry.activeSink?.onPlaybackStateChanged(false, state.playbackSpeed)
            listeners.forEach { it.onPlaybackStateChanged(position, isPlaying) }
        }
    }

    // ==================== 监听器 ====================

    fun registerListener(listener: OnMediaInfoChangeListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: OnMediaInfoChangeListener) {
        listeners.remove(listener)
    }

    fun interface OnMediaInfoChangeListener {
        fun onMediaInfoChanged(title: String, artist: String, packageName: String)
        fun onPlaybackStateChanged(position: Long, isPlaying: Boolean) {}
    }
}
