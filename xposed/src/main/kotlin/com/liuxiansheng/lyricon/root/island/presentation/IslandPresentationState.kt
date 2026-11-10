/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.presentation

/**
 * 展示状态（V3 自研）。
 */
internal class IslandPresentationState {
    @Volatile
    private var playbackActive = true

    @Volatile
    private var presentationRevision = 0L

    private val lock = Any()

    /** 更新播放态；状态变化时递增展示修订号。 */
    fun updatePlaybackState(isPlaying: Boolean): Boolean {
        return synchronized(lock) {
            val changed = playbackActive != isPlaying
            playbackActive = isPlaying
            if (changed) presentationRevision++
            changed
        }
    }

    fun isPlaybackActive(): Boolean = playbackActive

    /** 使所有挂起回调失效，返回新修订号。 */
    fun invalidatePresentation(): Long {
        return synchronized(lock) {
            ++presentationRevision
        }
    }

    fun currentRevision(): Long = presentationRevision

    fun isCurrentRevision(revision: Long): Boolean {
        return presentationRevision == revision
    }
}
