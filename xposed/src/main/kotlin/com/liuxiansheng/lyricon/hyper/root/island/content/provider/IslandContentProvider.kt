/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider

import android.content.Context
import android.view.View

/**
 * 超级岛内容提供者接口
 * 不同的左侧内容（歌词/歌名/时间/自定义文字）实现此接口
 */
interface IslandContentProvider {
    /**
     * 创建内容视图
     * @param side "left" 或 "right"
     */
    fun createView(context: Context, config: ContentProviderConfig, side: String): View

    /**
     * 更新内容（可选，用于需要定时刷新的内容如时间）
     */
    fun update(view: View, config: ContentProviderConfig, side: String) {}

    /**
     * 释放资源
     */
    fun destroy(view: View) {}
}
