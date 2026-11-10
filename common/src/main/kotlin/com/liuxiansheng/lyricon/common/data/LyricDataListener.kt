/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


package com.liuxiansheng.lyricon.common.data

import android.graphics.Bitmap
import com.liuxiansheng.lyricon.lyric.model.Song

/**
 * 歌词数据监听器
 *
 * 订阅 LyricDataHub 的数据变更，所有回调在主线程执行。
 * 性能铁律：监听器实现中避免耗时操作，帧路径 0 反射 / 0 new / 0 日志
 */
interface LyricDataListener {

    /**
     * 活跃播放器切换
     * @param providerInfo 新的提供者信息，null 表示无活跃播放器
     */
    fun onActiveProviderChanged(providerInfo: ProviderInfo?) {}

    /**
     * 歌曲信息变更
     * @param song 歌曲对象（含歌词行），null 表示无歌曲
     */
    fun onSongChanged(song: Song?) {}

    /**
     * 接收纯文本（无时间戳歌词）
     * @param text 文本内容
     */
    fun onReceiveText(text: String?) {}

    /**
     * 播放状态变更
     * @param isPlaying 是否正在播放
     */
    fun onPlaybackStateChanged(isPlaying: Boolean) {}

    /**
     * 播放进度更新
     * @param position 当前进度（毫秒）
     */
    fun onPositionChanged(position: Long) {}

    /**
     * 手动跳跃进度
     * @param position 目标进度（毫秒）
     */
    fun onSeekTo(position: Long) {}

    /**
     * 显示翻译配置变更
     * @param isDisplayTranslation 是否显示翻译
     */
    fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {}

    /**
     * 显示罗马音配置变更
     * @param isDisplayRoma 是否显示罗马音
     */
    fun onDisplayRomaChanged(isDisplayRoma: Boolean) {}

    /**
     * 封面变更（换歌/封面延迟加载完成后触发）
     * @param cover 封面位图，null 表示无封面
     */
    fun onCoverChanged(cover: Bitmap?) {}
}
