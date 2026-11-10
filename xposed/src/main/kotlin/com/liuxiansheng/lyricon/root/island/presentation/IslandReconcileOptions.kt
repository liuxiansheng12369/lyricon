/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.presentation

/**
 * 对账选项（V3 自研）——原因 → ShowOptions 映射。
 */
internal object IslandReconcileOptions {
    fun realRoot(
        reason: IslandReconcileReason
    ): IslandInjectionReconciler.ShowOptions {
        return when (reason) {
            IslandReconcileReason.PRE_SYSTEM_UPDATE,
            IslandReconcileReason.VISIBILITY_CHANGED ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_EXISTING,
                    content = IslandInjectionReconciler.ContentMode.WHEN_LAYOUT_CHANGED,
                    suppressAnimation = false,
                    reconfigureExisting = false
                )

            IslandReconcileReason.FAKE_FINISHED ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_EXISTING,
                    content = IslandInjectionReconciler.ContentMode.ALWAYS,
                    suppressAnimation = false,
                    reconfigureExisting = false
                )

            IslandReconcileReason.SYSTEM_UPDATE_COMPLETE ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.ENSURE,
                    content = IslandInjectionReconciler.ContentMode.ALWAYS,
                    suppressAnimation = false,
                    reconfigureExisting = false
                )

            IslandReconcileReason.STABLE_REFRESH ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.ENSURE,
                    content = IslandInjectionReconciler.ContentMode.NONE,
                    suppressAnimation = false,
                    reconfigureExisting = true
                )

            IslandReconcileReason.LYRIC_SELF_HEAL ->
                IslandInjectionReconciler.ShowOptions(
                    // 流式源可能在上一生命周期隐藏 wrapper 后更新行。
                    // 该状态下 tag 仍存在，自愈必须恢复可见性，
                    // 不能把"结构存在"当作就绪。
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_OR_ENSURE,
                    content = IslandInjectionReconciler.ContentMode.NONE,
                    suppressAnimation = true,
                    reconfigureExisting = false
                )

            IslandReconcileReason.PLAYBACK_RESUME ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_OR_ENSURE,
                    content = IslandInjectionReconciler.ContentMode.NONE,
                    suppressAnimation = true,
                    reconfigureExisting = false
                )

            IslandReconcileReason.MODULE_FIRST_BIND,
            IslandReconcileReason.MODULE_UPDATED,
            IslandReconcileReason.FAKE_SNAPSHOT ->
                error("Unsupported real-root reason: $reason")
        }
    }

    fun module(
        reason: IslandReconcileReason
    ): IslandInjectionReconciler.ShowOptions {
        return when (reason) {
            IslandReconcileReason.MODULE_FIRST_BIND ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.ENSURE,
                    content = IslandInjectionReconciler.ContentMode.WHEN_RESTORING_EXISTING,
                    suppressAnimation = true,
                    reconfigureExisting = false
                )

            IslandReconcileReason.MODULE_UPDATED ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_EXISTING,
                    content = IslandInjectionReconciler.ContentMode.WHEN_LAYOUT_CHANGED,
                    suppressAnimation = false,
                    reconfigureExisting = false
                )

            else -> error("Unsupported module reason: $reason")
        }
    }
}
