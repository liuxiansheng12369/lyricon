/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.lyricon.common.util

import android.os.Parcelable
import android.view.View
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.lang.ref.WeakReference

@Serializable
@Parcelize
data class ViewTreeNode(
    var id: String? = null,
    var name: String,
    var children: List<ViewTreeNode>? = null,
    @Transient
    @IgnoredOnParcel
    var view: WeakReference<View>? = null
) : Parcelable