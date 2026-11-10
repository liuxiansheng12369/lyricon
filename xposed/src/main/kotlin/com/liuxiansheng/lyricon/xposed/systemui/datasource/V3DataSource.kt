/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


package com.liuxiansheng.lyricon.xposed.systemui.datasource

import android.content.Context
import com.liuxiansheng.lyricon.common.log.YLog
import io.github.proify.lyricon.central.BridgeCentral

/**
 * V3 数据源总入口（SystemUI 进程）。
 *
 * 职责：启动 central 中心服务，供 HyperLyric 显示管线的 LyriconSource 订阅端接收歌词。
 * 歌词由外部 provider（liuxiansheng.lyricon.provider，hook 音乐 App 抓取）经 central 分发。
 */
object V3DataSource {

    private const val TAG = "V3DataSource"

    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        val appContext = context.applicationContext

        // 1. 启动 central 中心服务（provider 注册 / subscriber 订阅都经由它）
        runCatching {
            BridgeCentral.initialize(appContext)
            // 关键：向外部 provider（liuxiansheng.lyricon.provider）广播"central 已启动"。
            // 音乐 App 进程的 Provider 收到 ACTION_CENTRAL_BOOT_COMPLETED 后会触发
            // onServiceBootCompleted -> 重新注册 -> CachedRemotePlayer.syncs() 重推当前歌曲。
            // 缺少此广播时，SystemUI 重启后 Provider 不知道 central 重启，不重连重推，
            // central 内 activeRecorder 为空，订阅端重连也拿不到当前歌快照 -> 表现为"重启断联"。
            // （老词幕 SystemUIHooker.initLyriconService 同样调用 initialize + sendBootCompleted）
            BridgeCentral.sendBootCompleted()
            YLog.info(TAG, "central initialized + boot completed broadcast sent")
        }.onFailure { e ->
            YLog.error(TAG, "central init failed: ${e.message}")
        }

        // 2. 本地媒体 provider 保持停用（不主动 hook MediaSession，符合"只解析、不主动 hook 音乐"架构）。
        //    歌词由外部 provider（liuxiansheng.lyricon.provider）经 central 分发。
    }

    fun destroy() {
        if (!initialized) return
        initialized = false
        // 本地 provider 已停用，无需清理；central 由 BridgeCentral 生命周期管理。
    }
}
