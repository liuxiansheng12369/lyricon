/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.root.media

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.liuxiansheng.lyricon.hyper.lyric.model.Song as HyperSong
import com.liuxiansheng.lyricon.hyper.lyric.source.LyricSink
import com.liuxiansheng.lyricon.xposed.systemui.media.LyricInfoParser
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArraySet

/**
 * ColorOS 歌词源：从 MediaSession 的 lyricInfo key 读取歌词。
 *
 * 移植自老词幕 CurrentMediaInfo：
 * 1. 监听 MediaSession 变化
 * 2. 读取 metadata["lyricInfo"]
 * 3. LyricInfoParser 解析 JSON
 * 4. 推给 LyricSink
 */
object ColorOsLyricSource {
    private const val TAG = "ColorOsLyricSource"

    @Volatile
    private var sink: LyricSink? = null

    @Volatile
    private var manager: MediaSessionManager? = null

    @Volatile
    private var activeControllers: List<MediaController> = emptyList()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val controllerCallbacks =
        CopyOnWriteArraySet<MediaController.Callback>()

    @Volatile
    private var lastLyricInfoPkg = ""

    @Volatile
    private var appContext: Context? = null

    private var pollRunnable: Runnable? = null
    private var pollCount = 0

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
    }

    fun start(sink: LyricSink) {
        if (manager != null) {
            Log.i(TAG, "跳过重复启动: already_running")
            return
        }
        this.sink = sink
        val ctx = appContext ?: run {
            Log.w(TAG, "无法启动: context_unavailable")
            return
        }
        val m = runCatching {
            ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        }.getOrNull()
        if (m == null) {
            Log.w(TAG, "无法获取 MediaSessionManager")
            return
        }
        manager = m
        val active = runCatching { m.getActiveSessions(null) }.getOrDefault(emptyList())
        activeControllers = active
        registerCallbacks(active)
        runCatching { m.addOnActiveSessionsChangedListener(activeSessionsListener, null) }
        // 启动后延迟读一次当前 metadata
        mainHandler.postDelayed({ reReadActiveLyricInfo() }, 800)
        Log.i(TAG, "ColorOsLyricSource 已启动: sessions=${active.size}")
    }

    fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(activeSessionsListener) }
        controllerCallbacks.forEach { callback ->
            activeControllers.forEach { controller ->
                runCatching { controller.unregisterCallback(callback) }
            }
        }
        controllerCallbacks.clear()
        mainHandler.removeCallbacksAndMessages(null)
        manager = null
        activeControllers = emptyList()
        lastLyricInfoPkg = ""
        sink?.onStop()
        sink = null
        Log.i(TAG, "ColorOsLyricSource 已停止")
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        activeControllers = controllers.orEmpty()
        registerCallbacks(controllers.orEmpty())
    }

    private fun registerCallbacks(controllers: List<MediaController>) {
        val current = controllers.toSet()
        controllerCallbacks.forEach { callback ->
            // 简化：不逐个反注册，统一清理
        }
        controllers.forEach { controller ->
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
            controllerCallbacks.add(callback)
        }
    }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun com.liuxiansheng.lyricon.lyric.model.Song.toHyperSong(): HyperSong {
        val jsonString = json.encodeToString(this)
        return json.decodeFromString(jsonString)
    }

    private fun handleMetadata(controller: MediaController, metadata: MediaMetadata) {
        val lyricInfo = metadata.getString("lyricInfo")
        if (lyricInfo.isNullOrBlank()) {
            // 没 lyricInfo，启动轮询等
            startPolling(controller, metadata)
            return
        }

        val rawSong = LyricInfoParser.parse(lyricInfo)
        if (rawSong == null) {
            Log.w(TAG, "解析 lyricInfo 失败")
            return
        }

        val song = rawSong.toHyperSong()
        val pkg = controller.packageName ?: ""
        val lines = song.lyrics?.size ?: 0

        if (pkg != lastLyricInfoPkg) {
            lastLyricInfoPkg = pkg
            Log.i(TAG, "收到 lyricInfo: pkg=$pkg, song=${song.name}, lines=$lines")
        }

        // 停止轮询
        pollRunnable?.let { mainHandler.removeCallbacks(it) }

        sink?.onSongChanged(song)
    }

    private fun startPolling(controller: MediaController, metadata: MediaMetadata) {
        if (pollRunnable != null) return
        pollCount = 0
        pollRunnable = Runnable {
            pollCount++
            if (pollCount >= 50) {
                pollRunnable = null
                return@Runnable
            }
            val md = controller.metadata
            val li = md?.getString("lyricInfo")
            if (!li.isNullOrBlank()) {
                pollRunnable = null
                handleMetadata(controller, md)
            } else {
                mainHandler.postDelayed(pollRunnable!!, 200)
            }
        }
        mainHandler.postDelayed(pollRunnable!!, 500)
    }

    private fun handlePlayback(controller: MediaController, state: PlaybackState?) {
        val playing = state?.state == PlaybackState.STATE_PLAYING
        sink?.onPlaybackStateChanged(playing, state?.playbackSpeed ?: 1f)
        if (playing) {
            sink?.onPositionChanged(state?.position ?: 0L, state?.playbackSpeed ?: 1f)
        }
    }

    private fun reReadActiveLyricInfo() {
        val ctrl = activeControllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: activeControllers.firstOrNull()
            ?: return
        val md = ctrl.metadata ?: return
        handleMetadata(ctrl, md)
    }
}
