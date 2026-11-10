/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root

import com.liuxiansheng.lyricon.hyper.common.RootConstants

/**
 * 超级岛总开关。
 *
 * HyperLyric 原版由 App 进程把配置写入 remote prefs（SystemUI 进程只读）。
 * V3 设置页"歌词岛总开关"通过 XposedService 写入 remote prefs，
 * 本 Gate 在 SystemUI 进程读取该开关决定是否注入/显示超级岛歌词。
 * 默认开启：enableSuperIslandForV3() 首次启动会写入 true（尊重用户已设值）。
 *
 * V3 修复：读取失败（App 进程被系统杀后台 / 跨进程同步暂不可用）时
 * 回退"最近一次成功读到的值"，而不是回退默认 true——否则用户关闭的
 * 总开关会被吞掉（关了没效果/时好时坏）。
 */
internal object SystemUiEnhancementGate {
    @Volatile
    private var lastReadValue: Boolean? = null

    fun isEnabled(): Boolean {
        val value = runCatching {
            HookEntry.instance?.prefs?.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                true
            )
        }.getOrNull()
        return if (value != null) {
            lastReadValue = value
            value
        } else {
            lastReadValue ?: true
        }
    }

    /** 设置变更时清除缓存，强制下次实时重读 */
    fun invalidateCache() {
        lastReadValue = null
    }
}
