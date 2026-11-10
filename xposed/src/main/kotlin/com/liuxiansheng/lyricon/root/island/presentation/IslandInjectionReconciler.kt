/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.presentation

import android.view.View
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.root.island.renderer.IslandContentUpdateCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandHostFacade
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandViewHelper
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandViewRegistry
import com.liuxiansheng.lyricon.hyper.root.island.sizing.IslandDynamicWidthCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.structure.IslandSlotStructureInjector

/**
 * 注入对账器（V3 自研）。
 */
internal object IslandInjectionReconciler {

    /**
     * 状态栏歌词"隐藏超级岛"期间的强制隐藏标记。
     * 置 true 时统一守卫不恢复背景、不注入歌词（否则"播放中+背景GONE"守卫会把
     * 被我们压掉的 island_container 背景主动拉回，隐藏被抵消）。
     * 由 StatusBarLyricHooker 隐藏/恢复时置位。
     */
    @Volatile
    var statusBarLyricIslandForcedHidden = false

    sealed interface Target {
        data object RealRoot : Target
        data class RealModule(val moduleType: String?) : Target
        data object FakeSnapshot : Target
    }

    enum class StructureMode {
        ENSURE,
        ENSURE_IF_MISSING,
        RESTORE_EXISTING,
        RESTORE_OR_ENSURE
    }

    enum class ContentMode {
        NONE,
        WHEN_LAYOUT_CHANGED,
        WHEN_RESTORING_EXISTING,
        ALWAYS,
        FORCE
    }

    data class ShowOptions(
        val structure: StructureMode,
        val content: ContentMode,
        val suppressAnimation: Boolean,
        val reconfigureExisting: Boolean
    )

    enum class Outcome {
        APPLIED,
        RESTORED_NATIVE,
        NO_OP
    }

    data class Result(
        val outcome: Outcome,
        val layoutMayHaveChanged: Boolean,
        val contentChanged: Boolean,
        val injectedSlotsPresent: Boolean?,
        val relayoutRequested: Boolean
    ) {
        companion object {
            val NO_OP = Result(
                outcome = Outcome.NO_OP,
                layoutMayHaveChanged = false,
                contentChanged = false,
                injectedSlotsPresent = null,
                relayoutRequested = false
            )
        }
    }

    fun show(
        root: ViewGroup,
        target: Target,
        options: ShowOptions
    ): Result {
        // 状态栏歌词强制隐藏超级岛期间：不注入、不恢复背景（防止守卫抵消隐藏）。
        // 不清理歌词显示层：歌词保持现状，隐藏由系统窗口层完成（息屏本来就黑，看不见）。
        if (statusBarLyricIslandForcedHidden) {
            return Result.NO_OP
        }
        // 统一守卫：系统把岛背景（island_container）置 GONE 时，区分两种情况——
        // 1) 异常透明：媒体在播 + 窗口可见 + 背景 GONE（系统 checkError：背景GONE+窗口VISIBLE）
        //    → 主动恢复背景，避免大岛透明露出壁纸（打开设置页/取色刷新触发系统
        //    UpdateDynamicIslandWidth 事件时系统误判 Hidden 导致背景 GONE）。
        // 2) 正常隐藏：息屏/锁屏/岛收起 → 屏幕本身不渲染，歌词保持现状（不清理）。
        if (target == Target.RealRoot && !IslandViewHelper.isIslandBackgroundVisible(root)) {
            val windowVisible = root.visibility == View.VISIBLE && root.isShown
            val playing = IslandPresentationCoordinator.isPlaybackActive()
            if (windowVisible && playing) {
                IslandViewHelper.restoreIslandBackground(root)
            } else {
                return Result.NO_OP
            }
        }
        val hadInjectedSlots = IslandSlotStructureInjector.hasInjectedLyricText(root)
        val layoutMayHaveChanged = when (target) {
            Target.RealRoot,
            Target.FakeSnapshot -> reconcileRootStructure(
                root = root,
                hadInjectedSlots = hadInjectedSlots,
                options = options
            )

            is Target.RealModule -> reconcileModuleStructure(
                root = root,
                moduleType = target.moduleType,
                hadInjectedSlots = hadInjectedSlots,
                options = options
            )
        }

        var contentWasRefreshed = false
        val contentChanged = when (options.content) {
            ContentMode.NONE -> false
            ContentMode.WHEN_LAYOUT_CHANGED -> {
                if (!layoutMayHaveChanged) {
                    false
                } else {
                    contentWasRefreshed = true
                    IslandContentUpdateCoordinator.refreshContentForView(
                        root,
                        suppressAnimation = options.suppressAnimation
                    )
                }
            }

            ContentMode.WHEN_RESTORING_EXISTING -> {
                if (!hadInjectedSlots) {
                    false
                } else {
                    contentWasRefreshed = true
                    IslandContentUpdateCoordinator.refreshContentForView(
                        root,
                        force = true,
                        suppressAnimation = options.suppressAnimation
                    )
                }
            }

            ContentMode.ALWAYS -> {
                contentWasRefreshed = true
                IslandContentUpdateCoordinator.refreshContentForView(
                    root,
                    suppressAnimation = options.suppressAnimation
                )
            }

            ContentMode.FORCE -> {
                contentWasRefreshed = true
                IslandContentUpdateCoordinator.refreshContentForView(
                    root,
                    force = true,
                    suppressAnimation = options.suppressAnimation
                )
            }
        }

        if (contentWasRefreshed) {
            IslandSlotStructureInjector.linkViews(root)
        }
        IslandViewRegistry.refreshInjectedViews(root)
        val injectedSlotsPresent = IslandSlotStructureInjector.hasInjectedLyricText(root)
        if (target == Target.RealRoot && layoutMayHaveChanged) {
            // 恢复已隐藏 wrapper 后，歌词视图可测量时机改变。
            // 内容可能是签名 no-op，因此独立于内容请求宽度计算。
            IslandDynamicWidthCoordinator.requestRefresh(root)
        }
        val relayoutRequested = target == Target.RealRoot && layoutMayHaveChanged
        if (relayoutRequested) {
            IslandHostFacade.triggerSystemRelayout(root)
        }

        return Result(
            outcome = if (layoutMayHaveChanged || contentChanged) {
                Outcome.APPLIED
            } else {
                Outcome.NO_OP
            },
            layoutMayHaveChanged = layoutMayHaveChanged,
            contentChanged = contentChanged,
            injectedSlotsPresent = injectedSlotsPresent,
            relayoutRequested = relayoutRequested
        )
    }

    fun prepareFrozenSnapshot(root: ViewGroup, position: Long): Result {
        return prepareFrozenTransitionHost(
            root = root,
            target = Target.FakeSnapshot,
            position = position
        )
    }

    fun prepareFrozenRealHost(root: ViewGroup, position: Long): Result {
        return prepareFrozenTransitionHost(
            root = root,
            target = Target.RealRoot,
            position = position,
            content = ContentMode.FORCE
        )
    }

    fun restoreFrozenRealHost(root: ViewGroup, position: Long): Result {
        return prepareFrozenTransitionHost(
            root = root,
            target = Target.RealRoot,
            position = position,
            content = ContentMode.NONE
        )
    }

    private fun prepareFrozenTransitionHost(
        root: ViewGroup,
        target: Target,
        position: Long,
        content: ContentMode = ContentMode.FORCE
    ): Result {
        val result = show(
            root = root,
            target = target,
            options = ShowOptions(
                structure = StructureMode.RESTORE_OR_ENSURE,
                content = content,
                suppressAnimation = true,
                reconfigureExisting = false
            )
        )
        if (result.injectedSlotsPresent == true) {
            IslandLyricPlaybackController.freezeInjectedLyricProgress(root, position)
        }
        return result
    }

    fun restoreNative(root: ViewGroup, target: Target): Result {
        val layoutMayHaveChanged = IslandHostFacade.clearInjectedViews(root)
        val relayoutRequested = target == Target.RealRoot && layoutMayHaveChanged
        if (relayoutRequested) {
            IslandHostFacade.triggerSystemRelayout(root)
        }
        return Result(
            outcome = if (layoutMayHaveChanged) {
                Outcome.RESTORED_NATIVE
            } else {
                Outcome.NO_OP
            },
            layoutMayHaveChanged = layoutMayHaveChanged,
            contentChanged = false,
            injectedSlotsPresent = IslandSlotStructureInjector.hasInjectedLyricText(root),
            relayoutRequested = relayoutRequested
        )
    }

    private fun reconcileRootStructure(
        root: ViewGroup,
        hadInjectedSlots: Boolean,
        options: ShowOptions
    ): Boolean {
        return when (options.structure) {
            StructureMode.ENSURE -> IslandSlotStructureInjector.injectSlots(
                root,
                reconfigureExisting = options.reconfigureExisting,
                suppressAnimation = options.suppressAnimation
            )

            StructureMode.ENSURE_IF_MISSING -> {
                if (IslandSlotStructureInjector.hasAllConfiguredSlots(root)) {
                    false
                } else {
                    IslandSlotStructureInjector.injectSlots(
                        root,
                        reconfigureExisting = false,
                        suppressAnimation = options.suppressAnimation
                    )
                }
            }

            StructureMode.RESTORE_EXISTING -> {
                IslandSlotStructureInjector.restoreExistingSlotsLightweight(root)
            }

            StructureMode.RESTORE_OR_ENSURE -> {
                if (hadInjectedSlots) {
                    IslandSlotStructureInjector.restoreExistingSlotsLightweight(root)
                } else {
                    IslandSlotStructureInjector.injectSlots(
                        root,
                        reconfigureExisting = false,
                        suppressAnimation = options.suppressAnimation
                    )
                }
            }
        }
    }

    private fun reconcileModuleStructure(
        root: ViewGroup,
        moduleType: String?,
        hadInjectedSlots: Boolean,
        options: ShowOptions
    ): Boolean {
        return when (options.structure) {
            StructureMode.RESTORE_EXISTING -> {
                IslandSlotStructureInjector.restoreExistingModuleSlotLightweight(root, moduleType)
            }

            StructureMode.ENSURE_IF_MISSING -> {
                if (IslandSlotStructureInjector.hasAllConfiguredSlots(root, moduleType)) {
                    false
                } else {
                    IslandSlotStructureInjector.injectSlots(
                        root,
                        reconfigureExisting = false,
                        suppressAnimation = options.suppressAnimation
                    )
                }
            }

            StructureMode.ENSURE -> IslandSlotStructureInjector.injectSlots(
                root,
                reconfigureExisting = options.reconfigureExisting,
                suppressAnimation = options.suppressAnimation
            )

            StructureMode.RESTORE_OR_ENSURE -> {
                if (hadInjectedSlots) {
                    IslandSlotStructureInjector.restoreExistingModuleSlotLightweight(root, moduleType)
                } else {
                    IslandSlotStructureInjector.injectSlots(
                        root,
                        reconfigureExisting = false,
                        suppressAnimation = options.suppressAnimation
                    )
                }
            }
        }
    }
}
