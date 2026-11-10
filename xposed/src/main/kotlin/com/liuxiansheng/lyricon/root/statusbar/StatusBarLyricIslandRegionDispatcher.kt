/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * StatusBarLyricIslandRegionDispatcher：原生超级岛区域快照。
 * hook SystemUI StatusBarIslandControllerImpl 提交的 islandRect（与小米状态栏
 * 图标容器同一数据源），供状态栏歌词"避让超级岛"与"岛出现时隐藏时钟"使用。
 */

package com.liuxiansheng.lyricon.hyper.root.statusbar

import android.graphics.Rect
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger

/** 原生超级岛区域：SystemUI 提交的岛 bounds 快照（写端为 hook 线程，读端为状态栏帧循环）。 */
internal object StatusBarLyricIslandRegionDispatcher {

    data class IslandBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    @Volatile
    private var islandBounds: IslandBounds? = null

    /** 接收 SystemUI 提交的岛 Rect；空/空矩形视为岛消失。 */
    fun onNativeIslandRegionChanged(rect: Rect?) {
        val snapshot = rect
            ?.takeUnless(Rect::isEmpty)
            ?.let { IslandBounds(it.left, it.top, it.right, it.bottom) }
        val previous = islandBounds
        islandBounds = snapshot
        if ((previous == null) != (snapshot == null)) {
            HookLogger.d(TAG, "原生超级岛状态变更: visible=${snapshot != null}")
        }
    }

    /** 当前岛 bounds；无岛时 null。 */
    fun visibleIslandBounds(): IslandBounds? = islandBounds

    fun hasIsland(): Boolean = islandBounds != null

    private const val TAG = "StatusBarLyricIslandRegion"
}
