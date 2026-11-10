/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.presentation

/**
 * 渲染策略（V3 自研）——纯决策，无副作用。
 */
internal object IslandRenderPolicy {

    sealed interface OwnerEvidence {
        data class Media(val packageName: String) : OwnerEvidence
        data object Pending : OwnerEvidence
        data object NotMedia : OwnerEvidence
    }

    data class Input(
        val owner: OwnerEvidence,
        val lyricPackageName: String?,
        val hasLyricsForPresentation: Boolean,
        val enabled: Boolean,
        val playbackActive: Boolean,
        val pauseBehavior: Int
    )

    enum class Decision {
        TARGET,
        PENDING,
        OTHER_PACKAGE,
        SUPPRESSED,
        NOT_MEDIA
    }

    fun evaluate(input: Input): Decision {
        if (input.owner == OwnerEvidence.NotMedia) return Decision.NOT_MEDIA
        if (!input.enabled) return Decision.SUPPRESSED
        if (!input.hasLyricsForPresentation) return Decision.SUPPRESSED

        val mediaOwner = input.owner as? OwnerEvidence.Media
            ?: return Decision.PENDING
        val lyricPackageName = input.lyricPackageName
            ?.takeIf(String::isNotEmpty)
            ?: return Decision.PENDING

        if (mediaOwner.packageName != lyricPackageName) {
            return Decision.OTHER_PACKAGE
        }
        // 0=暂停时隐藏；2=暂停时不生成超级岛 —— 暂停期间都不上岛（2 是连岛窗口都不建）。
        if (!input.playbackActive && (input.pauseBehavior == 0 || input.pauseBehavior == 2)) {
            return Decision.SUPPRESSED
        }
        return Decision.TARGET
    }

    fun isPresentationAllowed(
        enabled: Boolean,
        playbackActive: Boolean,
        pauseBehavior: Int
    ): Boolean {
        // 只有 1（暂停时保留）允许非播放态继续呈现。
        return enabled && (playbackActive || pauseBehavior == 1)
    }
}
