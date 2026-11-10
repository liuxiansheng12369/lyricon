/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.hyper.root.plugin

import com.liuxiansheng.lyricon.hyper.common.media.MediaIdentity
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginMediaInfo
import com.liuxiansheng.lyricon.hyper.plugin.api.PluginSong

/**
 * Core-only request identity. The source snapshot is kept separately from the current enhanced
 * Song so a repeated source event cannot re-run a chain merely because an earlier plugin changed
 * the current Song. Media identity is deliberately kept outside the public plugin API.
 */
internal data class PluginProcessingRequestKey(
    val sourceSong: PluginSong,
    val mediaIdentity: MediaIdentity?,
    val mediaInfo: PluginMediaInfo?,
    val processorSetFingerprint: String = "",
)

/** Small deterministic last-request guard used by the root sink and local unit tests. */
internal class PluginProcessingRequestTracker {
    private var lastStarted: PluginProcessingRequestKey? = null

    fun isDuplicate(key: PluginProcessingRequestKey): Boolean = lastStarted == key

    fun markStarted(key: PluginProcessingRequestKey) {
        lastStarted = key
    }

    fun reset() {
        lastStarted = null
    }
}
