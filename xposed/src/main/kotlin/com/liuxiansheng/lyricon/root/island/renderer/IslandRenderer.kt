/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 超级岛渲染器接口：
 * 仅接收歌词源驱动的内容、播放状态和进度事件。
 * 结构注入和宿主恢复统一交给 IslandPresentationCoordinator 对账。
 */

package com.liuxiansheng.lyricon.hyper.root.island.renderer

/** 超级岛渲染器接口（V3 自研）。 */
interface IslandRenderer {
    fun refreshActiveIsland()
    fun updateMetadata()
    fun updateLyricLine()
    fun updateTextColors()
    fun updatePosition(position: Long, playbackSpeed: Float = 1f)
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun clearAllViews()

    /** 歌曲切换时调用：清空残留歌词行并触发宽度刷新（宽度回最小，歌名层不受影响）。 */
    fun onSongSwitched() {}
}
