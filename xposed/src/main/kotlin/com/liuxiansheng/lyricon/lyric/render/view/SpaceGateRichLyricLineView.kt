/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 分离模式（挖孔避让）歌词视图（V3 自研）。
 * 左右两个岛容器各放一个本视图：左侧显示歌词开头，右侧显示剩余部分（右对齐）；
 * 歌词行由拆分器预先拆成左右两段，本视图只负责侧位对齐与样式下发。
 */

package com.liuxiansheng.lyricon.lyric.render.view

import android.content.Context

internal class SpaceGateRichLyricLineView(
    context: Context,
    displayTranslation: Boolean = true,
    enableRelativeProgress: Boolean = false,
    enableRelativeProgressHighlight: Boolean = false,
    displayRoma: Boolean = true
) : RichLyricLineView(
    context,
    displayTranslation,
    enableRelativeProgress,
    enableRelativeProgressHighlight,
    displayRoma
) {

    /** 是否为右侧容器（右侧歌词右对齐） */
    var isRightSide = false
        private set

    /** 设置侧位并应用对齐（右侧：主副行右对齐、禁居中） */
    fun setSpaceGateConfig(isRightSide: Boolean, sibling: SpaceGateRichLyricLineView?) {
        if (this.isRightSide == isRightSide) return
        this.isRightSide = isRightSide
        applySideAlignment()
    }

    /** 挂载点别名（渐变配置当前无独立行为，仅记录侧位） */
    fun setSplitGradientConfig(isRightSide: Boolean, sibling: SpaceGateRichLyricLineView?) {
        setSpaceGateConfig(isRightSide, sibling)
    }

    private fun applySideAlignment() {
        // 3.0.792：分离模式对齐方向——左侧容器右对齐（歌词贴右/靠中间挖孔侧）、
        // 右侧容器左对齐（歌词贴左/靠中间挖孔侧），两侧歌词都往中间靠。
        // 用户开"歌词居中"时渲染层 centerIfPossible 优先 → 两侧往中间去。
        main.rightIfPossible = !isRightSide
        secondary.rightIfPossible = !isRightSide
        requestLayout()
        invalidate()
    }

    override fun setStyle(style: LyricViewStyle) {
        setStyle(style, isLeftSplitSide = !isRightSide)
    }

    /** 挂载点调用：isLeftSplitSide=true 左容器，false 右容器（仅记录侧位，对齐由设置决定） */
    fun setStyle(style: LyricViewStyle, isLeftSplitSide: Boolean) {
        this.isRightSide = !isLeftSplitSide
        // 3.0.790：右侧不再强制右对齐/禁居中——直接下发 buildStyle 的设置，
        // 让"歌词居中/右对齐"在两侧一致生效（此前右侧被 copy 覆盖 → 用户反馈右侧无效）
        super.setStyle(style)
        applySideAlignment()
    }
}
