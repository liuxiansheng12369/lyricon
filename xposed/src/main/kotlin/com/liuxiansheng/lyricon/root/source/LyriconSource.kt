/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.source

import android.app.Application
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.lyric.model.LyricMediaMetadata
import com.liuxiansheng.lyricon.hyper.lyric.source.LyricSink
import com.liuxiansheng.lyricon.hyper.lyric.source.LyricSource
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.renderer.BaseIslandRenderer
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.LyriconFactory
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.subscriber.ProviderInfo
import kotlinx.serialization.json.Json

class LyriconSource : LyricSource {

    companion object {
        private const val TAG = "LyriconSource"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }

    private fun Song.toLocalSong(): com.liuxiansheng.lyricon.hyper.lyric.model.Song {
        val jsonString = json.encodeToString(this)
        return json.decodeFromString(jsonString)
    }

    override val id = "lyricon"
    override val displayName = "Lyricon"

    @Volatile
    private var sink: LyricSink? = null
    private var app: Application? = null

    @Volatile
    private var subscriber: LyriconSubscriber? = null

    private var activeProviderPackageName: String? = null
    private var activePlayerPackageName: String? = null
    private var activeProviderDelayMs: Int = RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY
    private var prefs: android.content.SharedPreferences? = null


    override fun isAvailable(): Boolean = true

    override fun start(sink: LyricSink) {
        if (this.subscriber != null) {
            HookLogger.d(TAG, "跳过重复启动: reason=already_running")
            return
        }
        this.sink = sink
        val application = app ?: run {
            HookLogger.w(TAG, "数据源启动延后: reason=application_unavailable")
            return
        }
        initializeSubscriber(application)
        HookLogger.d(TAG, "数据源已启动")
    }

    override fun stop() {
        try {
            subscriber?.unsubscribeActivePlayer(activePlayerListener)
            subscriber?.unregister()
            subscriber?.destroy()
        } catch (e: Exception) {
            HookLogger.e(TAG, "清理歌词订阅连接失败", e)
        } finally {
            KeepSessionAlive.stop()
            subscriber = null
            activeProviderPackageName = null
            activePlayerPackageName = null
            sink?.onStop()
            sink = null
        }
        HookLogger.d(TAG, "数据源已停止")
    }

    fun initialize(app: Application, prefs: android.content.SharedPreferences?) {
        this.app = app
        this.prefs = prefs
    }

    fun onPreferenceChanged(key: String?) {
        val packageName = activeProviderPackageName ?: return
        if (key == providerDelayKey(packageName)) {
            activeProviderDelayMs = readProviderDelay(packageName)
        }
    }

    /**
     * 加强连接：断连检测触发后的重连动作。
     * 仅重新注册订阅端（unregister + register），不触碰 sink ——
     * 避免 stop() 连带 sink.onStop() 清空显示端状态（歌名/歌词/进度全清）。
     * subscriber 库在重新 register 时会走 onRegistered(reconnect=true) → onReconnected，
     * 数据流自动恢复；重连期间显示端保留原内容，新数据到达后无缝续上。
     */
    fun restart() {
        val sub = subscriber ?: return
        runCatching {
            sub.unregister()
            sub.register()
            HookLogger.i(TAG, "订阅端已重新注册（自动重连）")
        }.onFailure { e ->
            HookLogger.e(TAG, "订阅端重新注册失败", e)
        }
    }

    private fun providerDelayKey(packageName: String): String {
        return RootConstants.KEY_HOOK_LYRICON_PROVIDER_DELAY_PREFIX + packageName
    }

    private fun readProviderDelay(packageName: String): Int {
        return prefs?.getInt(
            providerDelayKey(packageName),
            RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY
        )?.coerceIn(
            RootConstants.MIN_HOOK_LYRICON_PROVIDER_DELAY,
            RootConstants.MAX_HOOK_LYRICON_PROVIDER_DELAY
        ) ?: RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY
    }


    private fun initializeSubscriber(app: Application) {
        val sub = LyriconFactory.createSubscriber(app)
        subscriber = sub

        sub.addConnectionListener(connectionListener)
        sub.subscribeActivePlayer(activePlayerListener)
        sub.register()
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnected(subscriber: LyriconSubscriber) {
            HookLogger.d(TAG, "订阅连接已建立")
        }

        override fun onReconnected(subscriber: LyriconSubscriber) {
            HookLogger.d(TAG, "订阅连接已恢复")
        }

        override fun onDisconnected(subscriber: LyriconSubscriber) {
            HookLogger.w(TAG, "订阅连接已断开")
        }

        override fun onConnectTimeout(subscriber: LyriconSubscriber) {
            HookLogger.w(TAG, "订阅连接超时")
        }
    }

    private val activePlayerListener = object : ActivePlayerListener {
        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            if (providerInfo == null) {
                // 原代码是立即 sink.onStop()（清屏）。这里改成立即判定，但不看时间看会话：
                //   - 会话还在播 / 还暂停着 → 词还读得到，画面一律不动
                //   - 会话真没了（播放器退出、或 STOPPED）→ 才真清
                sink?.onPlaybackStateChanged(false)
                if (!LyricInfoSource.hasLiveLyricInfo()) {
                    sink?.onStop()
                }
            }
            // provider 有变（null→有，或 A→B）：都不清屏，等新的 song 事件自然替换内容
            activeProviderPackageName = providerInfo?.providerPackageName
            activePlayerPackageName = providerInfo?.playerPackageName
            activeProviderDelayMs = providerInfo?.providerPackageName
                ?.let(::readProviderDelay)
                ?: RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY
        }


        override fun onSongChanged(song: Song?) {
            val localSong = song?.toLocalSong()
            sink?.onSongChanged(localSong)
            sink?.onMetadata(
                localSong?.let {
                    LyricMediaMetadata(
                        sourceId = id,
                        packageName = activePlayerPackageName,
                        songId = it.id,
                        title = it.name,
                        artist = it.artist,
                        album = it.album,
                        duration = it.duration.takeIf { duration -> duration > 0L }
                    )
                }
            )
            BaseIslandRenderer.refreshActiveIsland()
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            sink?.onPlaybackStateChanged(isPlaying)
            // B1 暂停防冻结：暂停且开关开启 → 心跳保活防进程冻结；恢复播放/开关关 → 停
            app?.let { KeepSessionAlive.onPlaybackStateChanged(it, isPlaying) }
        }

        override fun onPositionChanged(position: Long) {
            val adjustedPosition = (position - activeProviderDelayMs).coerceAtLeast(0L)
            sink?.onPositionChanged(adjustedPosition)
        }


        override fun onSeekTo(position: Long) {}

        override fun onReceiveText(text: String?) {
            sink?.onPlainText(text)
        }

        // 提供器只负责提供歌词内容；翻译和罗马音是否显示由 HyperLyric 显示端配置决定。
        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

        override fun onDisplayRomaChanged(isDisplayRoma: Boolean) = Unit
    }
}
