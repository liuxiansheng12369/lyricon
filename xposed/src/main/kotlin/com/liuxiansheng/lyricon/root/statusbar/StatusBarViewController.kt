/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */

package com.liuxiansheng.lyricon.hyper.root.statusbar

/**
 * 状态栏视图控制器接口
 * 每个状态栏视图实现这个接口，由 StatusBarViewManager 统一管理生命周期。
 */
interface StatusBarViewController {
    /** 创建时回调 */
    fun onCreate() {}

    /** 销毁时回调 */
    fun onDestroy() {}
}
