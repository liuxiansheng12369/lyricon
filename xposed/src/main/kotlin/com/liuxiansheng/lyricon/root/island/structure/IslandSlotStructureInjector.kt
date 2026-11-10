/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.structure

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView
import com.liuxiansheng.lyricon.lyric.render.view.SpaceGateRichLyricLineView
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.content.IslandContentProviderManager
import com.liuxiansheng.lyricon.hyper.root.island.content.IslandSlotContentFacade
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentMode
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandHostFacade
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandProbeUtils
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandViewHelper
import com.liuxiansheng.lyricon.hyper.root.island.sizing.IslandDynamicWidthCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.view.MaxWidthFrameLayout
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger

/**
 * Owns the structure of lyric slots injected into a Super Island host.
 *
 * Content refresh and playback progress are handled by their dedicated controllers. This class
 * only creates, restores, replaces and validates the injected slot views, while preserving the
 * existing synchronous preparation and relayout timing.
 */
internal object IslandSlotStructureInjector {
    private const val TAG = "IslandSlotStructureInjector"

    /**
     * 1.2.41：丢弃当前注入层，按**现存上屏树**重新建一遍歌词层。
     * 等价于用户手动来回切一次「状态栏歌词 / 超级岛」——那一下为什么会把歌词切回来：
     * 因为结构被拆掉重建，View 挂到的才是系统当前真正在显示的那棵树。
     * 锁屏重建/解冻后旧树残留 View 仍 attached，幂等判定误判"还活着"，结构就再也不重建 → 空壳。
     */
    fun forceReinject(rootView: ViewGroup): Boolean {
        val removedLeft = removeInjectedSlot(rootView, IslandProbeUtils.LEFT_TEST_WRAPPER_TAG)
        val removedRight = removeInjectedSlot(rootView, IslandProbeUtils.RIGHT_TEST_WRAPPER_TAG)
        val changed = injectSlots(rootView, reconfigureExisting = false)
        HookLogger.i(
            TAG,
            "歌词层重建: root=${rootView.javaClass.simpleName} 移除(left=$removedLeft right=$removedRight) 重建=$changed"
        )
        return changed
    }

    /** 1.2.41：关屏时把注入层整个摘掉，岛恢复系统原样（"关屏不使用超级岛"） */
    fun detachInjected(rootView: ViewGroup): Boolean {
        val l = removeInjectedSlot(rootView, IslandProbeUtils.LEFT_TEST_WRAPPER_TAG)
        val r = removeInjectedSlot(rootView, IslandProbeUtils.RIGHT_TEST_WRAPPER_TAG)
        return l || r
    }

    fun injectSlots(
        rootView: ViewGroup,
        reconfigureExisting: Boolean = true,
        suppressAnimation: Boolean = false
    ): Boolean {
        val prefs = HookEntry.instance?.prefs ?: run {
            HookLogger.dState(
                stateId = "IslandSlotStructureInjector:${System.identityHashCode(rootView)}:all",
                tag = TAG,
                state = "prefs_missing"
            ) {
                "歌词结构未注入: reason=remote_preferences_missing, root=${System.identityHashCode(rootView)}"
            }
            return false
        }
        val config = IslandSlotRuntimeConfig.from(prefs)

        var changed = false
        if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = injectSlot(
                rootView,
                IslandProbeUtils.LEFT_PARENT_NAME,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG,
                config.leftMode,
                reconfigureExisting,
                config,
                suppressAnimation
            ) || changed
        } else {
            changed = removeInjectedSlot(
                rootView,
                IslandProbeUtils.LEFT_TEST_WRAPPER_TAG
            ) || changed
        }

        if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = injectSlot(
                rootView,
                IslandProbeUtils.RIGHT_PARENT_NAME,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
                config.rightMode,
                reconfigureExisting,
                config,
                suppressAnimation
            ) || changed
        } else {
            changed = removeInjectedSlot(
                rootView,
                IslandProbeUtils.RIGHT_TEST_WRAPPER_TAG
            ) || changed
        }

        if (config.isSplitMode) {
            linkViews(rootView)
        }

        IslandHostFacade.applyHostSettings(rootView, prefs)
        IslandDynamicWidthCoordinator.requestRefresh(rootView)
        return changed
    }

    private fun removeInjectedSlot(rootView: ViewGroup, wrapperTag: String): Boolean {
        val wrapper = rootView.findViewWithTag<View>(wrapperTag) ?: return false
        val parent = wrapper.parent as? ViewGroup ?: return false
        parent.removeView(wrapper)
        IslandSlotContentFacade.invalidate(wrapper)
        return true
    }

    fun restoreExistingSlotsLightweight(rootView: ViewGroup): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        val config = IslandSlotRuntimeConfig.from(prefs)

        var changed = false
        if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = restoreExistingSlotLightweight(
                rootView,
                IslandProbeUtils.LEFT_PARENT_NAME,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG
            ) || changed
        }
        if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = restoreExistingSlotLightweight(
                rootView,
                IslandProbeUtils.RIGHT_PARENT_NAME,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG
            ) || changed
        }
        IslandHostFacade.applyHostSettings(rootView, prefs)
        return changed
    }

    fun restoreExistingModuleSlotLightweight(rootView: ViewGroup, moduleType: String?): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        val config = IslandSlotRuntimeConfig.from(prefs)

        var changed = false
        if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE &&
            (moduleType == null || moduleType.endsWith("_1"))
        ) {
            changed = restoreExistingSlotByTagLightweight(
                rootView,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG
            ) || changed
        }
        if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE &&
            (moduleType == null || moduleType.endsWith("_2"))
        ) {
            changed = restoreExistingSlotByTagLightweight(
                rootView,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG
            ) || changed
        }
        if (!changed && moduleType != null && !moduleType.endsWith("_1") && !moduleType.endsWith("_2")) {
            if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
                changed = restoreExistingSlotByTagLightweight(
                    rootView,
                    IslandProbeUtils.LEFT_TEST_VIEW_TAG
                ) || changed
            }
            if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
                changed = restoreExistingSlotByTagLightweight(
                    rootView,
                    IslandProbeUtils.RIGHT_TEST_VIEW_TAG
                ) || changed
            }
        }

        IslandHostFacade.applyHostSettings(rootView, prefs)
        return changed
    }

    fun hasInjectedLyricText(rootView: ViewGroup): Boolean {
        return rootView.findViewWithTag<View>(IslandProbeUtils.LEFT_TEST_WRAPPER_TAG) != null ||
                rootView.findViewWithTag<View>(IslandProbeUtils.RIGHT_TEST_WRAPPER_TAG) != null ||
                rootView.findViewWithTag<View>(IslandProbeUtils.LEFT_TEST_VIEW_TAG) != null ||
                rootView.findViewWithTag<View>(IslandProbeUtils.RIGHT_TEST_VIEW_TAG) != null
    }

    fun hasInjectedLyricSlot(rootView: ViewGroup, viewTag: String): Boolean {
        return rootView.findViewWithTag<View>("${viewTag}_WRAPPER") != null ||
                rootView.findViewWithTag<View>(viewTag) != null
    }

    fun hasAllConfiguredSlots(
        rootView: ViewGroup,
        moduleType: String? = null
    ): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        val config = IslandSlotRuntimeConfig.from(prefs)
        val checksLeft = moduleType?.endsWith("_2") != true
        val checksRight = moduleType?.endsWith("_1") != true
        val leftReady = !checksLeft || config.leftMode == RootConstants.ISLAND_CONTENT_MODE_NONE ||
                hasInjectedLyricSlot(rootView, IslandProbeUtils.LEFT_TEST_VIEW_TAG)
        val rightReady = !checksRight || config.rightMode == RootConstants.ISLAND_CONTENT_MODE_NONE ||
                hasInjectedLyricSlot(rootView, IslandProbeUtils.RIGHT_TEST_VIEW_TAG)
        return leftReady && rightReady
    }

    private fun injectSlot(
        rootView: ViewGroup,
        parentName: String,
        viewTag: String,
        mode: Int,
        reconfigureExisting: Boolean,
        config: IslandSlotRuntimeConfig,
        suppressAnimation: Boolean
    ): Boolean {
        val widthPx = config.geometry.widthPx(rootView, parentName) ?: run {
            logSlotSkip(rootView, viewTag, "width_missing")
            return false
        }

        val parent = IslandViewHelper.findViewByName(rootView, parentName) as? ViewGroup ?: run {
            logSlotSkip(rootView, viewTag, "parent_missing")
            return false
        }
        val container = IslandViewHelper.findViewByName(
            parent,
            IslandProbeUtils.TEXT_CONTAINER_NAME
        ) as? ViewGroup ?: run {
            logSlotSkip(rootView, viewTag, "text_container_missing")
            return false
        }

        val wrapperTag = "${viewTag}_WRAPPER"

        IslandViewHelper.showForInjection(container)

        HookEntry.instance?.prefs ?: run {
            logSlotSkip(rootView, viewTag, "remote_preferences_missing")
            return false
        }

        val taggedWrapper = container.findViewWithTag<View>(wrapperTag)
        val existingWrapper = taggedWrapper as? MaxWidthFrameLayout
        if (taggedWrapper != null && existingWrapper == null) {
            (taggedWrapper.parent as? ViewGroup)?.removeView(taggedWrapper)
            HookLogger.d(TAG, "已移除热重载遗留的旧歌词容器: tag=$wrapperTag")
        }
        if (existingWrapper != null) {
            existingWrapper.keepVisible = true
            var changed = updateWrapper(existingWrapper, widthPx, config, parentName)
            val targetView = existingWrapper.findViewWithTag<View>(viewTag)

            if (targetView == null) {
                existingWrapper.addView(
                    createLyricView(
                        rootView,
                        viewTag,
                        config,
                        mode,
                        suppressAnimation
                    ), createLyricTextLayoutParams()
                )
                changed = true
            } else if (!isViewTypeCorrect(targetView, config.activeMode, mode)) {
                existingWrapper.removeView(targetView)
                IslandSlotContentFacade.invalidate(targetView)
                existingWrapper.addView(
                    createLyricView(
                        rootView,
                        viewTag,
                        config,
                        mode,
                        suppressAnimation
                    ), createLyricTextLayoutParams()
                )
                changed = true
            } else {
                changed = restoreTargetView(
                    targetView,
                    config,
                    mode,
                    reconfigureExisting,
                    suppressAnimation
                ) || changed
            }

            if (existingWrapper.visibility != View.VISIBLE) {
                existingWrapper.visibility = View.VISIBLE
                changed = true
            }
            changed = forceWrapperLayout(existingWrapper, container, widthPx) || changed
            IslandViewHelper.hideNativeChildren(container, existingWrapper)
            IslandViewHelper.hideNativeTextViews(parent, container)
            return changed
        }

        val wrapperView = MaxWidthFrameLayout(rootView.context).apply {
            tag = wrapperTag
            clipChildren = true
            maxWidthPx = widthPx
            keepVisible = true
        }
        updateWrapper(wrapperView, widthPx, config, parentName)
        wrapperView.addView(
            createLyricView(rootView, viewTag, config, mode, suppressAnimation),
            createLyricTextLayoutParams()
        )

        container.addView(
            wrapperView,
            FrameLayout.LayoutParams(
                wrapperLayoutWidth(config),
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
        IslandViewHelper.hideNativeChildren(container, wrapperView)
        IslandViewHelper.hideNativeTextViews(parent, container)

        forceWrapperLayout(wrapperView, container, widthPx)

        return true
    }

    private fun logSlotSkip(rootView: ViewGroup, viewTag: String, reason: String) {
        HookLogger.dState(
            stateId = "IslandSlotStructureInjector:${System.identityHashCode(rootView)}:$viewTag",
            tag = TAG,
            state = reason
        ) {
            "歌词结构未注入: root=${System.identityHashCode(rootView)}, tag=$viewTag, reason=$reason"
        }
    }

    private fun restoreExistingSlotLightweight(
        rootView: ViewGroup,
        parentName: String,
        viewTag: String
    ): Boolean {
        val parent =
            IslandViewHelper.findViewByName(rootView, parentName) as? ViewGroup ?: return false
        val container = IslandViewHelper.findViewByName(
            parent,
            IslandProbeUtils.TEXT_CONTAINER_NAME
        ) as? ViewGroup ?: return false
        val wrapper = container.findViewWithTag<View>("${viewTag}_WRAPPER") as? MaxWidthFrameLayout
            ?: return false
        val targetView = wrapper.findViewWithTag<View>(viewTag) ?: return false

        var changed = false
        wrapper.keepVisible = true
        if (container.visibility != View.VISIBLE) {
            IslandViewHelper.showForInjection(container)
            changed = true
        }
        if (wrapper.visibility != View.VISIBLE) {
            wrapper.visibility = View.VISIBLE
            changed = true
        }
        if (targetView.visibility != View.VISIBLE) {
            targetView.visibility = View.VISIBLE
            changed = true
        }
        IslandViewHelper.hideNativeChildren(container, wrapper)
        IslandViewHelper.hideNativeTextViews(parent, container)
        return changed
    }

    private fun restoreExistingSlotByTagLightweight(rootView: ViewGroup, viewTag: String): Boolean {
        val wrapper = rootView.findViewWithTag<View>("${viewTag}_WRAPPER") as? MaxWidthFrameLayout
            ?: return false
        val targetView = wrapper.findViewWithTag<View>(viewTag) ?: return false
        val container = wrapper.parent as? ViewGroup ?: return false

        var changed = false
        wrapper.keepVisible = true
        if (container.visibility != View.VISIBLE) {
            IslandViewHelper.showForInjection(container)
            changed = true
        }
        if (wrapper.visibility != View.VISIBLE) {
            wrapper.visibility = View.VISIBLE
            changed = true
        }
        if (targetView.visibility != View.VISIBLE) {
            targetView.visibility = View.VISIBLE
            changed = true
        }
        IslandViewHelper.hideNativeChildren(container, wrapper)
        (container.parent as? ViewGroup)?.let {
            IslandViewHelper.hideNativeTextViews(it, container)
        }
        return changed
    }

    private fun updateWrapper(
        wrapper: MaxWidthFrameLayout,
        widthPx: Int,
        config: IslandSlotRuntimeConfig,
        parentName: String
    ): Boolean {
        var changed = false
        val paddingLeft = config.geometry.paddingLeftPx(wrapper, parentName)
        val paddingRight = config.geometry.paddingRightPx(wrapper, parentName)
        // V3 修复：支持负偏移——负值转负 margin（容器整体向摄像头方向外扩），
        // 避免 View.setPadding 传入负值引发绘制异常；正值仍走 padding。
        val padL = if (paddingLeft >= 0) paddingLeft else 0
        val padR = if (paddingRight >= 0) paddingRight else 0
        if (wrapper.paddingLeft != padL || wrapper.paddingRight != padR) {
            wrapper.setPadding(padL, wrapper.paddingTop, padR, wrapper.paddingBottom)
            changed = true
        }
        val marginLp = wrapper.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        if (marginLp != null) {
            val marginL = if (paddingLeft < 0) paddingLeft else 0
            val marginR = if (paddingRight < 0) paddingRight else 0
            if (marginLp.leftMargin != marginL || marginLp.rightMargin != marginR) {
                marginLp.leftMargin = marginL
                marginLp.rightMargin = marginR
                wrapper.layoutParams = marginLp
                changed = true
            }
        }
        if (wrapper.minimumWidth != 0) {
            wrapper.minimumWidth = 0
            changed = true
        }
        // Dynamic width is refined from the current lyric line after the wrapper is attached.
        // Keep that measured value during a lifecycle re-ensure (especially pause/keep),
        // otherwise the paused path skips the width refresh and leaves the wrapper at the
        // configured maximum until playback resumes.
        val targetMaxWidthPx = if (config.geometry.isDynamicWidth && wrapper.maxWidthPx > 0) {
            minOf(wrapper.maxWidthPx, widthPx)
        } else {
            widthPx
        }
        if (wrapper.maxWidthPx != targetMaxWidthPx) {
            wrapper.maxWidthPx = targetMaxWidthPx
            changed = true
        }
        val layoutParams = wrapper.layoutParams
        val expectedWidth = wrapperLayoutWidth(config)
        if (layoutParams != null && (layoutParams.width != expectedWidth || layoutParams.height != FrameLayout.LayoutParams.MATCH_PARENT)) {
            layoutParams.width = expectedWidth
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            wrapper.layoutParams = layoutParams
            changed = true
        }
        if (changed) wrapper.requestLayout()
        return changed
    }

    private fun isViewTypeCorrect(view: View, activeMode: Int, mode: Int): Boolean {
        if (IslandContentProviderManager.isProviderMode(mode)) {
            return IslandContentProviderManager.isBoundWithMode(
                view,
                IslandContentMode.fromInt(mode)
            )
        }
        return if (activeMode == 1) {
            view is SpaceGateRichLyricLineView
        } else {
            view is RichLyricLineView
        }
    }

    private fun restoreTargetView(
        targetView: View,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        reconfigure: Boolean,
        suppressAnimation: Boolean = false
    ): Boolean {
        var changed = false
        val layoutParams = targetView.layoutParams
        if (layoutParams != null &&
            (layoutParams.width != FrameLayout.LayoutParams.MATCH_PARENT ||
                    layoutParams.height != FrameLayout.LayoutParams.MATCH_PARENT)
        ) {
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            targetView.layoutParams = layoutParams
            changed = true
        }
        if (targetView.visibility != View.VISIBLE) {
            targetView.visibility = View.VISIBLE
            changed = true
        }

        val prefs = HookEntry.instance?.prefs
        if (reconfigure && prefs != null) {
            changed = IslandSlotContentFacade.applySlotContent(
                targetView,
                prefs,
                config,
                mode,
                force = true,
                suppressAnimation = suppressAnimation
            ) || changed
        }
        return changed
    }

    private fun forceWrapperLayout(
        wrapper: MaxWidthFrameLayout,
        container: ViewGroup,
        widthPx: Int
    ): Boolean {
        val wasZeroWidth = wrapper.width == 0 || wrapper.measuredWidth == 0
        if (!wasZeroWidth) {
            return false
        }

        val heightPx = if (container.height > 0) container.height else container.measuredHeight
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.AT_MOST)
        val heightSpec = if (heightPx > 0) {
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        } else {
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        }
        wrapper.measure(widthSpec, heightSpec)
        val finalHeight = if (heightPx > 0) heightPx else wrapper.measuredHeight
        wrapper.layout(0, 0, wrapper.measuredWidth, finalHeight)
        return wasZeroWidth
    }

    private fun wrapperLayoutWidth(config: IslandSlotRuntimeConfig): Int {
        return if (config.isSplitMode) {
            FrameLayout.LayoutParams.WRAP_CONTENT
        } else {
            FrameLayout.LayoutParams.MATCH_PARENT
        }
    }

    private fun createLyricTextLayoutParams(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun createLyricView(
        rootView: ViewGroup,
        tagValue: String,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        suppressAnimation: Boolean = false
    ): View {
        if (IslandContentProviderManager.isProviderMode(mode)) {
            return IslandContentProviderManager.createProviderView(
                context = rootView.context,
                mode = IslandContentMode.fromInt(mode),
                providerConfig = IslandContentProviderManager.buildConfig(config),
                side = if (tagValue == IslandProbeUtils.LEFT_TEST_VIEW_TAG) "left" else "right",
                tag = tagValue
            )
        }
        val prefs = HookEntry.instance?.prefs
        val view = if (config.isSplitMode) {
            SpaceGateRichLyricLineView(rootView.context)
        } else {
            RichLyricLineView(rootView.context)
        }
        view.tag = tagValue

        if (prefs != null) {
            IslandSlotContentFacade.applySlotContent(
                view,
                prefs,
                config,
                mode,
                force = true,
                suppressAnimation = true
            )
        }
        return view
    }

    fun linkViews(rootView: ViewGroup) {
        val leftView =
            rootView.findViewWithTag<View>(IslandProbeUtils.LEFT_TEST_VIEW_TAG) as? SpaceGateRichLyricLineView
        val rightView =
            rootView.findViewWithTag<View>(IslandProbeUtils.RIGHT_TEST_VIEW_TAG) as? SpaceGateRichLyricLineView

        leftView?.setSplitGradientConfig(isRightSide = false, sibling = rightView)
        rightView?.setSplitGradientConfig(isRightSide = true, sibling = leftView)

        leftView?.main?.spaceGateEnabled = false
        leftView?.secondary?.spaceGateEnabled = false
        rightView?.main?.spaceGateEnabled = false
        rightView?.secondary?.spaceGateEnabled = false

    }
}
