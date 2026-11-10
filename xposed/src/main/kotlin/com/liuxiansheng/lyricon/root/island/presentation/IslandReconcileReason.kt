/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.presentation

/** 对账原因（V3 自研）。 */
internal enum class IslandReconcileReason {
    PRE_SYSTEM_UPDATE,
    SYSTEM_UPDATE_COMPLETE,
    VISIBILITY_CHANGED,
    MODULE_FIRST_BIND,
    MODULE_UPDATED,
    STABLE_REFRESH,
    LYRIC_SELF_HEAL,
    PLAYBACK_RESUME,
    FAKE_SNAPSHOT,
    FAKE_FINISHED
}
