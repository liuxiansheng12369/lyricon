/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.lyricon.common.util

import android.view.View
import android.view.ViewGroup
import androidx.core.view.forEach
import io.github.proify.lyricon.common.util.ResourceMapper.getIdName
import java.lang.ref.WeakReference

object ViewHierarchyParser {
    fun buildNodeTree(viewGroup: ViewGroup): ViewTreeNode {
        val name = viewGroup.javaClass.name

        val children = mutableListOf<ViewTreeNode>()

        viewGroup.forEach { view ->
            createNodeFromView(view)?.let { node ->
                children.add(node)
            }
        }

        return ViewTreeNode(
            id = getResourceName(viewGroup),
            name = name,
            children = children,
            view = WeakReference(viewGroup),
        )
    }

    private fun createNodeFromView(view: View): ViewTreeNode? =
        if (view is ViewGroup) {
            buildNodeTree(view)
        } else {
            val name = view.javaClass.name
            ViewTreeNode(
                id = getResourceName(view),
                name = name,
                view = WeakReference(view),
            )
        }

    fun getResourceName(view: View): String? = getIdName(view)
}