/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


@file:Suppress("unused")

package com.liuxiansheng.lyricon.bridge

import android.content.Context
import android.content.Intent
import com.liuxiansheng.lyricon.common.config.ConfigCenter
import com.liuxiansheng.lyricon.common.log.YLog

/**
 * Bridge 发送端（APP 侧使用）
 *
 * 职责：APP 侧配置变更后，发送广播通知 SystemUI 侧更新配置。
 * 低频操作，不做高频推送。
 *
 * 使用方式：
 * ```
 * BridgeSender.initialize(context)
 * // 配置变更后调用
 * BridgeSender.sendConfigChanged()
 * ```
 */
object BridgeSender {
    private const val TAG = "BridgeSender"

    private var context: Context? = null

    fun initialize(ctx: Context) {
        this.context = ctx.applicationContext
        YLog.info(TAG, "initialized")
    }

    /**
     * 发送配置变更广播（携带当前配置 JSON）
     * SystemUI 侧的 BridgeReceiver 接收后调用 ConfigCenter.restoreConfig()
     */
    fun sendConfigChanged() {
        val ctx = context ?: run {
            YLog.warning(TAG, "sendConfigChanged: context is null, call initialize() first")
            return
        }
        val configJson = ConfigCenter.configJson()
        val intent = Intent(BridgeConstants.ACTION_CONFIG_CHANGED).apply {
            // 3.0.611 修复：不能 setPackage(自己包名)——那会把广播锁死在 APP 进程内，
            // SystemUI 侧 BridgeReceiver（RECEIVER_EXPORTED 动态注册）永远收不到，
            // 开关切换只能靠 remote prefs 跨进程监听（App 的 Xposed 连接断就丢）。
            // 去掉 setPackage → 隐式广播 → SystemUI 动态注册接收器可收到。
            putExtra(BridgeConstants.EXTRA_CONFIG_JSON, configJson)
        }
        ctx.sendBroadcast(intent)
        YLog.debug(TAG, "config changed broadcast sent")
    }

    /**
     * 发送配置响应（响应 SystemUI 的配置请求）
     */
    fun sendConfigResponse() {
        val ctx = context ?: return
        val configJson = ConfigCenter.configJson()
        val intent = Intent(BridgeConstants.ACTION_CONFIG_RESPONSE).apply {
            putExtra(BridgeConstants.EXTRA_CONFIG_JSON, configJson)
        }
        ctx.sendBroadcast(intent)
        YLog.debug(TAG, "config response broadcast sent")
    }
}
