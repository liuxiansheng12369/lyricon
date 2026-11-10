/*
 * This file is based on HyperIsland by 1812z.
 * Original: https://github.com/1812z/HyperIsland
 * Ported to Lyricon V3. Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root.island.upstream

import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandMaterialConfigStore
import com.liuxiansheng.lyricon.hyper.root.island.upstream.NativeBlurRenderer
import com.liuxiansheng.lyricon.hyper.root.island.upstream.OuterBlurRegistry
import com.liuxiansheng.lyricon.hyper.root.island.upstream.TransitionBlurController

/** Shared renderer graph used by the hook entry and transition visual hook. */
internal object IslandBlurRuntime {
    val configStore = IslandMaterialConfigStore()
    val nativeBlurRenderer = NativeBlurRenderer(configStore)
    val outerBlurRegistry = OuterBlurRegistry(configStore, nativeBlurRenderer)
    val transitionBlurController = TransitionBlurController(configStore, nativeBlurRenderer)
}