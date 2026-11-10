/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */

package com.liuxiansheng.lyricon.hyper.lyric

import android.util.Log
import com.liuxiansheng.lyricon.hyper.lyric.model.Song
import com.liuxiansheng.lyricon.hyper.lyric.processor.LyricDataProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/**
 * 歌词数据监听接口
 */
interface LyricDataListener {
    fun onSongChanged(song: Song?) {}
    fun onPlaybackStateChanged(isPlaying: Boolean) {}
    fun onPositionChanged(position: Long) {}
    fun onSeekTo(position: Long) {}
}

/**
 * 歌词数据调度中枢
 * 管理歌词生命周期：获取原始数据 -> 后台加工 -> 第一次分发 -> 后台增强 -> 最终分发。
 *
 * V3 简化版：去掉了 Provider 策略、AI 翻译、文本模式等，保留核心流水线。
 */
object LyricDataHub {
    private const val TAG = "LyricDataHub"

    private val listeners = CopyOnWriteArraySet<LyricDataListener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 状态版本号，用于在异步恢复后校验数据是否已过时 */
    private val versionCounter = AtomicInteger(0)

    /** 缓存当前的原始歌曲，用于配置变更时重走流程 */
    private var cachedRawSong: Song? = null

    /** 当前执行中的流水线任务 */
    private var activePipelineJob: Job? = null

    private var lastDispatchSongId: Int? = null

    fun addListener(listener: LyricDataListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: LyricDataListener) {
        listeners.remove(listener)
    }

    /**
     * 接收到新歌曲，启动后台加工流水线。
     */
    @Synchronized
    fun onSongChanged(song: Song?) {
        this.cachedRawSong = song?.deepCopy()
        runProcessingPipeline(song)
    }

    /**
     * 重走加工流程
     * 当配置变更时调用，无需切歌即可应用新设置。
     */
    @Synchronized
    fun reprocessCurrentSong() {
        runProcessingPipeline(cachedRawSong)
    }

    /**
     * 启动后台加工流水线。
     * 所有歌词加工都在后台协程执行，避免阻塞播放器回调线程。
     */
    @Synchronized
    private fun runProcessingPipeline(rawSong: Song?) {
        val song = rawSong?.deepCopy()
        val currentVersion = versionCounter.incrementAndGet()
        activePipelineJob?.cancel()

        activePipelineJob = scope.launch {
            try {
                if (song == null) {
                    dispatchSong(null, currentVersion)
                    return@launch
                }

                // 1. 后台前置加工：处理繁简、屏蔽词等基础显示数据
                val preProcessed = LyricDataProcessor.executePreProcessing(song)
                if (!isCurrentVersion(currentVersion)) return@launch

                // 第一次分发：基础处理完成后尽快刷新 UI
                dispatchSong(LyricDataProcessor.executeDisplayProcessing(preProcessed), currentVersion)

                // 2. 后台后置流水线：处理 AI 翻译等耗时扩展
                val finalSong = LyricDataProcessor.executePostProcessingPipeline(preProcessed)

                if (isCurrentVersion(currentVersion)) {
                    dispatchSong(LyricDataProcessor.executeDisplayProcessing(finalSong), currentVersion)
                } else {
                    Log.d(TAG, "Pipeline $currentVersion outdated, skipped. ${song.name}")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Pipeline $currentVersion cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline $currentVersion error", e)
            }
        }
    }

    private fun isCurrentVersion(version: Int): Boolean = version == versionCounter.get()

    /**
     * 分发歌曲给所有监听者
     */
    @Synchronized
    private fun dispatchSong(song: Song?, version: Int) {
        if (!isCurrentVersion(version)) return
        val normalized = song?.normalize()

        val hashCode = normalized?.hashCode() ?: 0
        if (hashCode == lastDispatchSongId) return
        lastDispatchSongId = hashCode

        listeners.forEach { it.onSongChanged(normalized) }
    }

    /**
     * 播放状态变化
     */
    @Synchronized
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        listeners.forEach { it.onPlaybackStateChanged(isPlaying) }
    }

    /**
     * 位置变化
     */
    fun onPositionChanged(position: Long) {
        listeners.forEach { it.onPositionChanged(position) }
    }

    /**
     * 拖动进度
     */
    @Synchronized
    fun onSeekTo(position: Long) {
        listeners.forEach { it.onSeekTo(position) }
    }
}
