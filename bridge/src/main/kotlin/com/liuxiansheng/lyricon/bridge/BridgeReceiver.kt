/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


@file:Suppress("unused")

package com.liuxiansheng.lyricon.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.liuxiansheng.lyricon.common.config.ConfigCenter
import com.liuxiansheng.lyricon.common.log.YLog

/**
 * Bridge 接收端（SystemUI 侧使用）
 *
 * 职责：接收 APP 侧发送的配置变更广播，调用 ConfigCenter.restoreConfig() 更新配置。
 * 同时支持响应配置请求（SystemUI 启动时向 APP 请求当前配置）。
 *
 * 使用方式：
 * ```
 * BridgeReceiver.initialize(context)
 * // 不需要时调用
 * BridgeReceiver.destroy(context)
 * ```
 *
 * V3 修复（连接层）：SystemUI 侧初始化后，APP 写配置广播到达时除 restoreConfig 外，
 * 还通过 onConfigRestored 回调通知 HookEntry 重新读取渲染配置并刷新——
 * 保证"设置即生效"不依赖 remote prefs 跨进程监听（该监听依赖 App 进程存活，
 * App 被杀后台时设置变更会丢）。
 */
object BridgeReceiver {
    private const val TAG = "BridgeReceiver"

    /** 配置恢复后的回调（SystemUI 侧注册，用于触发渲染层刷新） */
    @Volatile
    var onConfigRestored: (() -> Unit)? = null

    private var receiver: InternalReceiver? = null
    private var context: Context? = null

    fun initialize(ctx: Context) {
        if (receiver != null) return
        this.context = ctx.applicationContext

        val filter = IntentFilter().apply {
            addAction(BridgeConstants.ACTION_CONFIG_CHANGED)
            addAction(BridgeConstants.ACTION_CONFIG_RESPONSE)
            addAction(BridgeConstants.ACTION_REQUEST_CONFIG)
        }
        receiver = InternalReceiver()
        // Android 13+（targetSdk 33+）动态注册必须显式指定 exported 标志。
        // 本接收器接收的是 APP 进程（不同包）发来的配置广播，必须 RECEIVER_EXPORTED，
        // 否则抛 SecurityException 导致整个 SystemUI 侧初始化中断。
        runCatching {
            ctx.applicationContext.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        }.onFailure { e ->
            YLog.error(TAG, "registerReceiver failed: ${e.message}")
            receiver = null
            return
        }
        YLog.info(TAG, "initialized and registered")

        // 启动时请求 APP 下发当前配置
        requestConfig()
    }

    fun destroy(ctx: Context) {
        receiver?.let {
            runCatching { ctx.applicationContext.unregisterReceiver(it) }
            receiver = null
            YLog.info(TAG, "destroyed and unregistered")
        }
    }

    /**
     * 向 APP 侧请求当前配置（SystemUI 启动时调用）
     */
    private fun requestConfig() {
        val ctx = context ?: return
        val intent = Intent(BridgeConstants.ACTION_REQUEST_CONFIG).apply {
            setPackage(ctx.packageName)
        }
        ctx.sendBroadcast(intent)
        YLog.debug(TAG, "config request broadcast sent")
    }

    private class InternalReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BridgeConstants.ACTION_CONFIG_CHANGED,
                BridgeConstants.ACTION_CONFIG_RESPONSE -> {
                    val configJson = intent.getStringExtra(BridgeConstants.EXTRA_CONFIG_JSON)
                    if (!configJson.isNullOrBlank()) {
                        ConfigCenter.restoreConfig(configJson)
                        YLog.info(TAG, "config restored from broadcast, version=${ConfigCenter.configVersion}")
                        // 通知渲染层重新读取配置并刷新（不依赖 remote prefs 跨进程监听）
                        runCatching { onConfigRestored?.invoke() }
                    } else {
                        YLog.warning(TAG, "config broadcast received but json is empty")
                    }
                }

                BridgeConstants.ACTION_REQUEST_CONFIG -> {
                    // APP 侧收到 SystemUI 的配置请求：回发当前配置
                    runCatching { BridgeSender.sendConfigResponse() }
                        .onFailure { e -> YLog.warning(TAG, "sendConfigResponse failed: ${e.message}") }
                }
            }
        }
    }
}
