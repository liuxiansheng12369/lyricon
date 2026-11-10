/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 时间导航器（V3 自研）。
 * 负责"媒体时间 → 当前词"的二分查找。词列表按 begin 升序，查找 O(log n)。
 */

package com.liuxiansheng.lyricon.lyric.render.model

internal class TimingNavigator(private val words: List<WordRenderModel>) {

    /**
     * 返回 [posMs] 时刻正在播放的词（begin <= pos < end）。
     * 若 pos 落在词间空隙返回 null。
     */
    fun first(posMs: Long): WordRenderModel? {
        if (words.isEmpty()) return null
        var low = 0
        var high = words.size - 1
        var result: WordRenderModel? = null
        while (low <= high) {
            val mid = (low + high) ushr 1
            val word = words[mid]
            if (word.begin <= posMs) {
                result = word
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result?.takeIf { posMs < it.end || it.end <= it.begin }
    }

    /**
     * 返回 [posMs] 之前（含等于）最近一个已开始的词。
     * 用于词间空隙：保持前一词的结束宽度。
     */
    fun findPreviousEntry(posMs: Long): WordRenderModel? {
        if (words.isEmpty()) return null
        var low = 0
        var high = words.size - 1
        var result: WordRenderModel? = null
        while (low <= high) {
            val mid = (low + high) ushr 1
            val word = words[mid]
            if (word.begin <= posMs) {
                result = word
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /** 首个词 */
    val firstWord: WordRenderModel? get() = words.firstOrNull()
}
