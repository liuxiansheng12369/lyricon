/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.renderer

import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.hooks.IslandWidthLimitHooker
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandNativeRefreshCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandPresentationCoordinator

/**
 * 设置刷新协调器（V3 自研）。
 *
 * 任意超级岛设置变更 → 强制重建当前宿主歌词槽样式与内容（实时生效）。
 */
internal object IslandSettingsRefreshCoordinator {
    /**
     * 防重入守卫：compact(applyCompact) → request → refreshContentForView → maybeUpdate → applyCompact
     * 这条链会形成无限递归导致 SystemUI 栈溢出崩溃。request 入口置位，同步递归回来直接跳过，
     * 仅执行最外层一次。异步 onComplete 回调在 request 返回后才走，不受影响。
     */
    @Volatile
    private var requesting = false

    fun request() {
        if (requesting) return
        requesting = true
        try {
            IslandPresentationCoordinator.invalidatePresentation()
            // 直接对当前宿主歌词槽强制重建（不依赖 native refresh 回调——否则系统岛未 rebind 时
            // onComplete 不触发，歌词样式/效果设置要等下一次歌词才生效。设置变更即重排，实时生效）。
            val packageName = LyriconDataBridge.currentLyricPackageName
                ?.takeIf { it.isNotEmpty() }
            if (packageName != null) {
                IslandPresentationCoordinator.snapshotAttachedHosts(packageName).forEach { token ->
                    if (IslandPresentationCoordinator.isCurrentHost(token)) {
                        refreshHyperLyricContent(token.root)
                    }
                }
            }
            IslandNativeRefreshCoordinator.request(
                onComplete = ::refreshHyperLyricContent
            )
        } finally {
            requesting = false
        }
    }

    private fun refreshHyperLyricContent(root: ViewGroup) {
        // force=true：任意超级岛设置变更都强制重建 style + 内容，实时生效，
        // 无需再为单个设置项手动加入 styleSignature/特判。设置变更是低频 + 有 16ms 防抖，不会闪。
        IslandWidthLimitHooker.refresh(root)
        IslandContentUpdateCoordinator.refreshContentForView(
            view = root,
            force = true
        )
    }
}
