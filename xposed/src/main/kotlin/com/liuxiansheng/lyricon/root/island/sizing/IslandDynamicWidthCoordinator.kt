/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.sizing

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView
import com.liuxiansheng.lyricon.lyric.render.view.SpaceGateRichLyricLineView
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandHostFacade
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandProbeUtils
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandViewRegistry
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandViewHelper
import com.liuxiansheng.lyricon.hyper.root.island.view.MaxWidthFrameLayout
import java.util.WeakHashMap

/**
 * 动态宽度协调器（V3 自研）。
 *
 * 触发动态宽度重算并调度系统重排：
 * - 同一根视图的并发请求合并（防 post 风暴）；
 * - 动态宽度重算不依赖播放态——暂停/切句瞬间宽度也要按当前内容重算；
 * - 开关切换（force）时即使宽度不变也强制重排（切换即重排）。
 */
internal object IslandDynamicWidthCoordinator {
    private val refreshPending = WeakHashMap<ViewGroup, Boolean>()
    private val relayoutPending = WeakHashMap<ViewGroup, Boolean>()

    private val metadataContentWidths = WeakHashMap<ViewGroup, MutableMap<String, Float>>()

    /**
     * 触发动态宽度重算。
     * @param force 开关切换等场景传 true：即使宽度计算结果不变也强制重排
     * （切开关立即重排，不等下一句）。
     */
    fun requestRefresh(rootView: ViewGroup, force: Boolean = false) {
        val shouldPost = synchronized(refreshPending) {
            if (refreshPending[rootView] == true) {
                false
            } else {
                refreshPending[rootView] = true
                true
            }
        }
        if (!shouldPost) return

        val posted = rootView.post {
            synchronized(refreshPending) {
                refreshPending.remove(rootView)
            }
            if (IslandViewRegistry.tokenFor(rootView) == null) return@post
            // 动态宽度重算不依赖播放态——暂停/启动瞬间/切句时宽度冻结在旧值
            // 或预飞行值，与当前歌词宽度不符导致"四个字只显示三个半"。
            // 只要 token 有效就按当前内容重算（内容变化低频触发，不会空转耗电）。
            val prefs = HookEntry.instance?.prefs ?: return@post
            val config = IslandSlotRuntimeConfig.from(prefs)
            if (!config.geometry.isDynamicWidth) return@post
            if (refreshDynamicSlotWidths(rootView, config, force)) {
                scheduleSystemRelayout(rootView)
            }
        }
        if (!posted) {
            synchronized(refreshPending) {
                refreshPending.remove(rootView)
            }
        }
    }

    /**
     * 缓存当前渲染的元数据宽度，供后续歌词刷新使用。
     * 媒体元数据只在切歌时变化，歌词行频繁变化；
     * 按宿主缓存可避免每次歌词更新都重读元数据视图。
     */
    fun cacheMetadataWidth(rootView: ViewGroup, viewTag: String): Boolean {
        val view = rootView.findViewWithTag<View>(viewTag) ?: return false
        val contentWidthPx = metadataContentWidthPx(view) ?: return false
        return synchronized(metadataContentWidths) {
            val rootWidths = metadataContentWidths.getOrPut(rootView) { hashMapOf() }
            if (rootWidths[viewTag] == contentWidthPx) {
                false
            } else {
                rootWidths[viewTag] = contentWidthPx
                true
            }
        }
    }

    /** 重算左右两栏动态宽度；任一栏变化则返回 true。 */
    private fun refreshDynamicSlotWidths(
        rootView: ViewGroup,
        config: IslandSlotRuntimeConfig,
        force: Boolean = false
    ): Boolean {
        if (!config.geometry.isDynamicWidth) return false

        val lyricOnly = config.dynamicWidthBasis ==
                RootConstants.ISLAND_DYNAMIC_WIDTH_BASIS_LYRIC_ONLY
        val slotBaseWidthDp = listOf(
            dynamicSlotBaseWidthDp(
                rootView,
                IslandProbeUtils.LEFT_PARENT_NAME,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG,
                config,
                lyricOnly
            ),
            dynamicSlotBaseWidthDp(
                rootView,
                IslandProbeUtils.RIGHT_PARENT_NAME,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
                config,
                lyricOnly
            )
        ).filterNotNull().maxOrNull() ?: return false
        val baseWidthDp = slotBaseWidthDp.coerceIn(
            config.geometry.rightMinWidthDp.toFloat(),
            config.geometry.rightMaxWidthDp.toFloat()
        )

        var changed = false
        if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = updateDynamicSlotWidth(
                rootView,
                IslandProbeUtils.LEFT_PARENT_NAME,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG,
                config,
                baseWidthDp,
                force
            ) || changed
        }
        if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = updateDynamicSlotWidth(
                rootView,
                IslandProbeUtils.RIGHT_PARENT_NAME,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
                config,
                baseWidthDp,
                force
            ) || changed
        }
        return changed
    }

    /** 单栏基础宽度（dp）：取歌词/元数据实际内容宽度换算，再按基准模式过滤。 */
    private fun dynamicSlotBaseWidthDp(
        rootView: ViewGroup,
        parentName: String,
        viewTag: String,
        config: IslandSlotRuntimeConfig,
        lyricOnly: Boolean = false
    ): Float? {
        val contentMode = config.modeForTag(viewTag)
        if (lyricOnly && contentMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO) {
            return null
        }
        val contentWidthPx = when (contentMode) {
            RootConstants.ISLAND_CONTENT_MODE_LYRIC -> {
                val lyricView = rootView.findViewWithTag<View>(viewTag) ?: return null
                // 主行宽/副行宽各自取值，确认宽度来源（当前行 vs 副行/预览/残留）。
                val diagMainW = when (lyricView) {
                    is RichLyricLineView -> lyricView.main.lineWidth
                    is SpaceGateRichLyricLineView -> lyricView.main.lineWidth
                    else -> -1f
                }
                val diagSecW = when (lyricView) {
                    is RichLyricLineView -> lyricView.secondary.lineWidth
                    is SpaceGateRichLyricLineView -> lyricView.secondary.lineWidth
                    else -> -1f
                }
                Log.e("LyriconDebug", "WLine tag=$viewTag main=$diagMainW sec=$diagSecW")
                when (lyricView) {
                    // 主/副行（歌词+翻译）取最大宽度，避免翻译行更长时被裁"显示不全被挤"。
                    is RichLyricLineView -> (
                        // 下一句预览在副行：预览句还没正式显示，宽度不参与，
                        // 否则"歌词没到尺寸先到"。翻译/副行内容参与。
                        if (lyricView.secondary.isStaticPreview) {
                            lyricView.main.lineWidth
                        } else {
                            maxOf(lyricView.main.lineWidth, lyricView.secondary.lineWidth)
                        }
                    ) + lyricView.paddingLeft + lyricView.paddingRight
                    is SpaceGateRichLyricLineView -> (
                        if (lyricView.secondary.isStaticPreview) {
                            lyricView.main.lineWidth
                        } else {
                            maxOf(lyricView.main.lineWidth, lyricView.secondary.lineWidth)
                        }
                    ) + lyricView.paddingLeft + lyricView.paddingRight
                    else -> return null
                }
            }

            RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO -> {
                metadataContentWidthPx(rootView, viewTag) ?: return null
            }

            else -> return null
        }
        val baseWidthDp = IslandLyricWidthCalculator.baseWidthDp(
            contentWidthPx = contentWidthPx,
            spec = dynamicLyricWidthSpec(rootView, parentName, config)
        )
        // 无歌词瞬间看宽度来源（模式/候选/实际行宽/基础宽）。
        Log.e("LyriconDebug", "WBase tag=$viewTag mode=$contentMode content=$contentWidthPx baseDp=$baseWidthDp")
        return baseWidthDp
    }

    private fun metadataContentWidthPx(rootView: ViewGroup, viewTag: String): Float? {
        val cachedWidth = synchronized(metadataContentWidths) {
            metadataContentWidths[rootView]?.get(viewTag)
        }
        if (cachedWidth != null) return cachedWidth

        val view = rootView.findViewWithTag<View>(viewTag) ?: return null
        val measuredWidth = metadataContentWidthPx(view) ?: return null
        synchronized(metadataContentWidths) {
            metadataContentWidths.getOrPut(rootView) { hashMapOf() }[viewTag] = measuredWidth
        }
        return measuredWidth
    }

    private fun metadataContentWidthPx(view: View): Float? {
        return when (view) {
            is RichLyricLineView -> maxOf(view.main.lineWidth, view.secondary.lineWidth)
            is SpaceGateRichLyricLineView -> maxOf(
                view.main.lineWidth,
                view.secondary.lineWidth
            )
            else -> null
        }
    }

    /** 应用单栏目标宽度到 wrapper；宽度没变则跳过（省重排）。 */
    private fun updateDynamicSlotWidth(
        rootView: ViewGroup,
        parentName: String,
        viewTag: String,
        config: IslandSlotRuntimeConfig,
        baseWidthDp: Float,
        force: Boolean = false
    ): Boolean {
        val targetWidthPx = IslandLyricWidthCalculator.targetWidthPx(
            baseWidthDp = baseWidthDp,
            spec = dynamicLyricWidthSpec(rootView, parentName, config)
        ) ?: return false
        val wrapper = rootView.findViewWithTag<View>("${viewTag}_WRAPPER")
            as? MaxWidthFrameLayout ?: return false
        Log.e("LyriconDebug", "DynamicWidth tag=$viewTag target=$targetWidthPx current=${wrapper.maxWidthPx} force=$force")
        val finalTarget = targetWidthPx
        // 宽度没变就跳过（省重排）；开关切换（force）时即使宽度不变也强制
        // requestLayout + 系统重排——这就是"切换即重排"。
        if (!force && wrapper.maxWidthPx == finalTarget) return false

        wrapper.maxWidthPx = finalTarget
        wrapper.requestLayout()
        return true
    }

    /** 请求宿主系统重排（合并并发请求）。 */
    private fun scheduleSystemRelayout(rootView: ViewGroup) {
        val shouldPost = synchronized(relayoutPending) {
            if (relayoutPending[rootView] == true) {
                false
            } else {
                relayoutPending[rootView] = true
                true
            }
        }
        if (!shouldPost) return

        val posted = rootView.post {
            try {
                if (IslandViewRegistry.tokenFor(rootView) != null) {
                    // 系统重排不依赖播放态：暂停时宽度变化也要让岛重排。
                    IslandHostFacade.triggerSystemRelayout(rootView)
                }
            } finally {
                synchronized(relayoutPending) {
                    relayoutPending.remove(rootView)
                }
            }
        }
        if (!posted) {
            synchronized(relayoutPending) {
                relayoutPending.remove(rootView)
            }
        }
    }

    /** 构造当前栏的宽度计算规格。 */
    private fun dynamicLyricWidthSpec(
        rootView: ViewGroup,
        parentName: String,
        config: IslandSlotRuntimeConfig
    ): IslandLyricWidthSpec {
        // 频谱占位：与 IslandCompactModeController 同一开关联动，
        // 切换后 requestRefresh 立即重算。当前恒 0（保持现状）。
        val spectrumReservedDp = 0
        return IslandLyricWidthSpec(
            density = rootView.resources.displayMetrics.density,
            paddingLeftPx = config.geometry.paddingLeftPx(rootView, parentName),
            paddingRightPx = config.geometry.paddingRightPx(rootView, parentName),
            minWidthDp = config.geometry.minWidthDp(parentName),
            maxWidthDp = config.geometry.maxWidthDp(parentName),
            isLeft = config.geometry.isLeftParent(parentName),
            showAlbum = config.geometry.showAlbum,
            showRhythm = config.geometry.showRhythm,
            spectrumReservedDp = spectrumReservedDp
        )
    }
}
