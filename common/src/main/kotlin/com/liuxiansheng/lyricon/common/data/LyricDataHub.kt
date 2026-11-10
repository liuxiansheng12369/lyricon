/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


@file:Suppress("unused")

package com.liuxiansheng.lyricon.common.data

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.liuxiansheng.lyricon.common.log.YLog
import com.liuxiansheng.lyricon.lyric.model.Song
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/**
 * 歌词数据总线（单一数据源）
 *
 * 核心设计：
 * 1. 单一数据源：所有歌词相关状态集中维护，避免多源不同步
 * 2. 版本号机制：每次数据变更 version+1，订阅者可校验版本防空转
 * 3. 主线程分发：所有回调通过 mainHandler post 到主线程
 * 4. 热重载支持：currentSongJson / restoreCurrentSong 跨 ClassLoader 传递
 *
 * 性能铁律：
 * - 帧路径只读 @Volatile 字段，不触发分发
 * - 只有数据真正变化才分发（去重）
 * - 分发用 mainHandler.post，不用 postDelayed
 */
object LyricDataHub {
    private const val TAG = "LyricDataHub"

    private val listeners = CopyOnWriteArraySet<LyricDataListener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 数据版本号，每次变更 +1，用于订阅者校验防空转 */
    private val versionCounter = AtomicInteger(0)

    // --- 当前状态（@Volatile 保证多线程可见性，热路径只读这些） ---

    @Volatile var currentSong: Song? = null
        private set

    @Volatile var currentPosition: Long = 0
        private set

    @Volatile var isPlaying: Boolean = false
        private set

    @Volatile var currentProvider: ProviderInfo? = null
        private set

    @Volatile var currentCover: Bitmap? = null
        private set

    @Volatile var displayTranslation: Boolean = false
        private set

    @Volatile var displayRoma: Boolean = false
        private set

    /** 当前版本号（只读） */
    val version: Int get() = versionCounter.get()

    // --- 订阅管理 ---

    fun addListener(listener: LyricDataListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: LyricDataListener) {
        listeners.remove(listener)
    }

    // --- 数据写入入口（由 hook 层/数据来源调用） ---

    fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        if (currentProvider == providerInfo) return
        currentProvider = providerInfo
        versionCounter.incrementAndGet()
        post { listeners.forEach { it.onActiveProviderChanged(providerInfo) } }
    }

    fun onSongChanged(song: Song?) {
        currentSong = song
        currentPosition = 0
        versionCounter.incrementAndGet()
        post { listeners.forEach { it.onSongChanged(song) } }
    }

    fun onReceiveText(text: String?) {
        versionCounter.incrementAndGet()
        post { listeners.forEach { it.onReceiveText(text) } }
    }

    fun onPlaybackStateChanged(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
        versionCounter.incrementAndGet()
        post { listeners.forEach { it.onPlaybackStateChanged(playing) } }
    }

    fun onPositionChanged(position: Long) {
        currentPosition = position
        // 进度更新高频，不递增版本号（避免版本号爆炸），订阅者按需读取 currentPosition
        post { listeners.forEach { it.onPositionChanged(position) } }
    }

    fun onSeekTo(position: Long) {
        currentPosition = position
        versionCounter.incrementAndGet()
        post { listeners.forEach { it.onSeekTo(position) } }
    }

    fun onDisplayTranslationChanged(enabled: Boolean) {
        if (displayTranslation == enabled) return
        displayTranslation = enabled
        versionCounter.incrementAndGet()
        post { listeners.forEach { it.onDisplayTranslationChanged(enabled) } }
    }

    fun onDisplayRomaChanged(enabled: Boolean) {
        if (displayRoma == enabled) return
        displayRoma = enabled
        versionCounter.incrementAndGet()
        post { listeners.forEach { it.onDisplayRomaChanged(enabled) } }
    }

    fun onCoverChanged(cover: Bitmap?) {
        currentCover = cover
        versionCounter.incrementAndGet()
        // 通知监听器刷新封面视图（否则换歌时旧封面残留、新封面到达不刷新，与首句歌词切换撞一起闪烁）
        post { listeners.forEach { it.onCoverChanged(cover) } }
    }

    // --- 热重载支持 ---

    /** 导出当前歌曲为 JSON 字符串（跨 ClassLoader 传递用） */
    fun currentSongJson(): String? = runCatching {
        currentSong?.let { json.encodeToString(Song.serializer(), it) }
    }.getOrNull()

    /** 从 JSON 恢复当前歌曲并重走分发（热重载后调用） */
    fun restoreCurrentSong(jsonText: String?) {
        if (jsonText.isNullOrBlank()) return
        runCatching {
            val song = json.decodeFromString(Song.serializer(), jsonText)
            currentSong = song
            versionCounter.incrementAndGet()
            post { listeners.forEach { it.onSongChanged(song) } }
            YLog.info(TAG, "restoreCurrentSong: name=${song.name}")
        }.onFailure { t ->
            YLog.error(TAG, "restoreCurrentSong failed", t)
        }
    }

    // --- 工具方法 ---

    /**
     * 校验版本号是否仍为最新（用于异步任务完成后检查数据是否已过期）
     * @param snapshotVersion 任务开始时的版本号
     * @return true 表示数据未变，false 表示已过期应丢弃
     */
    fun isCurrentVersion(snapshotVersion: Int): Boolean = snapshotVersion == versionCounter.get()

    /** 重置所有状态（模块卸载/测试用） */
    fun reset() {
        currentSong = null
        currentPosition = 0
        isPlaying = false
        currentProvider = null
        currentCover = null
        versionCounter.incrementAndGet()
        post {
            listeners.forEach {
                it.onSongChanged(null)
                it.onPlaybackStateChanged(false)
                it.onActiveProviderChanged(null)
            }
        }
    }

    private fun post(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}
