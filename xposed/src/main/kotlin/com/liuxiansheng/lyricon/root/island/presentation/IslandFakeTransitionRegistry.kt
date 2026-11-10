/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 假转场注册表：
 * 为每个假宿主保存最新的假→真转场记录。
 * SystemUI 不在转场结束回调里返回我们的代数，因此假所有者的最新记录保持权威。
 * 本注册表只持有该状态；转场决策与视图变更留在展示协调器。
 */

package com.liuxiansheng.lyricon.hyper.root.island.presentation

import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandViewRegistry
import java.util.WeakHashMap

/**
 * 假转场注册表（V3 自研）。
 */
internal object IslandFakeTransitionRegistry {
    data class Record(
        val generation: Long,
        val realHost: IslandViewRegistry.HostToken,
        val frozenPosition: Long,
        val lyricVersion: Int
    )

    private val lock = Any()
    private val transitions = WeakHashMap<ViewGroup, Record>()
    private var nextGeneration = 0L

    fun remember(
        fakeOwner: ViewGroup,
        realHost: IslandViewRegistry.HostToken,
        frozenPosition: Long,
        lyricVersion: Int
    ): Record {
        return synchronized(lock) {
            Record(
                generation = ++nextGeneration,
                realHost = realHost,
                frozenPosition = frozenPosition,
                lyricVersion = lyricVersion
            ).also { transitions[fakeOwner] = it }
        }
    }

    fun find(fakeOwner: ViewGroup): Record? {
        return synchronized(lock) {
            transitions[fakeOwner]
        }
    }

    fun remove(fakeOwner: ViewGroup): Record? {
        return synchronized(lock) {
            transitions.remove(fakeOwner)
        }
    }

    fun isHostFrozen(token: IslandViewRegistry.HostToken): Boolean {
        return synchronized(lock) {
            transitions.values.any { transition ->
                transition.realHost == token
            }
        }
    }

    fun discardForHost(token: IslandViewRegistry.HostToken) {
        synchronized(lock) {
            val iterator = transitions.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value.realHost == token) {
                    iterator.remove()
                }
            }
        }
    }
}
