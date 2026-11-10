/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


package com.liuxiansheng.lyricon.bridge

/**
 * Bridge 通信常量
 *
 * V3 架构决策：所有计算在 SystemUI 侧，Bridge 只做低频设置下发，不做高频数据推送。
 * 通信方式：Broadcast（简单可靠，无需绑定服务）。
 */
object BridgeConstants {
    /** 配置变更广播 action（APP → SystemUI） */
    const val ACTION_CONFIG_CHANGED = "com.liuxiansheng.lyricon.v3.CONFIG_CHANGED"

    /** 配置 JSON extra key */
    const val EXTRA_CONFIG_JSON = "config_json"

    /** 请求当前配置广播 action（SystemUI → APP，用于启动时同步） */
    const val ACTION_REQUEST_CONFIG = "com.liuxiansheng.lyricon.v3.REQUEST_CONFIG"

    /** 配置响应广播 action（APP → SystemUI） */
    const val ACTION_CONFIG_RESPONSE = "com.liuxiansheng.lyricon.v3.CONFIG_RESPONSE"

    /** 广播权限（只允许本应用发送/接收） */
    const val PERMISSION = "com.liuxiansheng.lyricon.v3.permission.BRIDGE"
}
