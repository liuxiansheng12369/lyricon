/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 歌词播放控制器：
 * 假快照显示或真实宿主恢复期间，控制注入歌词视图的播放状态。
 */

package com.liuxiansheng.lyricon.hyper.root.island.presentation

import android.view.View
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView
import com.liuxiansheng.lyricon.lyric.render.view.SpaceGateRichLyricLineView
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandProbeUtils

/**
 * 歌词播放控制器（V3 自研）。
 */
internal object IslandLyricPlaybackController {

    fun freezeInjectedLyricProgress(rootView: ViewGroup, position: Long) {
        val prefs = HookEntry.instance?.prefs ?: return
        val config = IslandSlotRuntimeConfig.from(prefs)

        if (config.leftMode == RootConstants.ISLAND_CONTENT_MODE_LYRIC) {
            freezeLyricView(rootView.findViewWithTag(IslandProbeUtils.LEFT_TEST_VIEW_TAG), position)
        }
        if (config.rightMode == RootConstants.ISLAND_CONTENT_MODE_LYRIC) {
            freezeLyricView(
                rootView.findViewWithTag(IslandProbeUtils.RIGHT_TEST_VIEW_TAG),
                position
            )
        }
    }

    fun resumeInjectedLyricProgress(rootView: ViewGroup, position: Long) {
        val prefs = HookEntry.instance?.prefs ?: return
        val config = IslandSlotRuntimeConfig.from(prefs)

        if (config.leftMode == RootConstants.ISLAND_CONTENT_MODE_LYRIC) {
            resumeLyricView(rootView.findViewWithTag(IslandProbeUtils.LEFT_TEST_VIEW_TAG), position)
        }
        if (config.rightMode == RootConstants.ISLAND_CONTENT_MODE_LYRIC) {
            resumeLyricView(
                rootView.findViewWithTag(IslandProbeUtils.RIGHT_TEST_VIEW_TAG),
                position
            )
        }
    }

    private fun freezeLyricView(view: View?, position: Long) {
        when (view) {
            is RichLyricLineView -> {
                view.setPlaybackActive(false)
                view.setPosition(position)
                view.setPlaybackActive(false)
            }

            is SpaceGateRichLyricLineView -> {
                view.setPlaybackActive(false)
                view.setPosition(position)
                view.setPlaybackActive(false)
            }
        }
    }

    private fun resumeLyricView(view: View?, position: Long) {
        when (view) {
            is RichLyricLineView -> {
                view.setPlaybackActive(true)
                view.setPosition(position)
            }

            is SpaceGateRichLyricLineView -> {
                view.setPlaybackActive(true)
                view.setPosition(position)
            }
        }
    }
}
