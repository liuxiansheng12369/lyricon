/*
 * Lyricon V3 - Copyright (C) 2026 liuxiansheng
 * Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.xposed

import android.util.Log
import androidx.annotation.Keep
import com.liuxiansheng.lyricon.bridge.BridgeReceiver
import com.liuxiansheng.lyricon.bridge.BridgeSender
import com.liuxiansheng.lyricon.common.config.ConfigCenter
import com.liuxiansheng.lyricon.common.log.YLog
import com.liuxiansheng.lyricon.xposed.systemui.datasource.V3DataSource
import com.liuxiansheng.lyricon.xposed.util.AppContextProvider
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import io.github.libxposed.api.XposedModuleInterface

/**
 * Lyricon V3 模块入口。
 *
 * SystemUI 进程：全部走 HyperLyric 管线（HookEntry 的 hook 注册 + SourceManager 歌词源
 * + BaseIslandRenderer 渲染）——超级岛显示（歌名/歌词/元数据）与 HyperLyric 一字不差。
 * APP 进程：保留 V3 的配置发送（BridgeSender/ConfigCenter）。
 */
@Keep
class ModuleEntry : HookEntry() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        // V3 日志系统初始化（HyperLyric 的 HookLogger 由父类初始化）
        YLog.init(this)
        if (BuildConfig.DEBUG) {
            YLog.debugEnabled = true
            YLog.infoEnabled = true
            Log.i(TAG, "Lyricon v3 ModuleEntry loaded: process=${param.processName}, debug=true")
        }
        super.onModuleLoaded(param)
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)

        val packageName = param.packageName
        // SystemUI 进程：HyperLyric 显示管线（父类）+ V3 本地数据源（central + MediaSession lyricInfo provider）
        if (packageName == "com.android.systemui") {
            YLog.info(TAG, "initializing SystemUI data source...")
            AppContextProvider.initialize(this, param.defaultClassLoader)
            AppContextProvider.addOnContextReady { context ->
                runCatching { V3DataSource.initialize(context) }
                    .onFailure { e -> YLog.error(TAG, "V3DataSource init failed: ${e.message}") }
                // V3 无 HyperLyric 配置 UI：强制开启超级岛 + 启动歌词源管理器（订阅本地 provider）
                runCatching {
                    val entry = HookEntry.instance
                    if (entry != null) {
                        entry.enableSuperIslandForV3()
                    } else {
                        YLog.warning(TAG, "HookEntry instance not ready, super island start deferred")
                    }
                }.onFailure { e ->
                    YLog.error(TAG, "enableSuperIslandForV3 failed: ${e.message}")
                }
                // V3 修复（连接层）：SystemUI 侧注册 BridgeReceiver——
                // APP 设置页每次写配置后发广播，这里收到后刷新渲染配置。
                // 此前接收器从未注册，广播通道全废，设置变更只能依赖
                // remote prefs 跨进程监听（App 进程被杀就丢）。
                runCatching {
                    BridgeReceiver.initialize(context)
                    BridgeReceiver.onConfigRestored = {
                        runCatching {
                            HookEntry.instance?.onSettingsBroadcastReceived()
                        }.onFailure { e ->
                            YLog.error(TAG, "onSettingsBroadcastReceived failed: ${e.message}")
                        }
                    }
                }.onFailure { e ->
                    YLog.error(TAG, "BridgeReceiver init failed: ${e.message}")
                }
                YLog.info(TAG, "SystemUI data source initialized")
            }
        }
        // 模块自身 APP 进程：初始化配置发送（V3 配置 UI -> SystemUI 生效）
        if (packageName == BuildConfig.APP_PACKAGE_NAME) {
            YLog.info(TAG, "initializing APP domain...")
            AppContextProvider.initialize(this, param.defaultClassLoader)
            AppContextProvider.addOnContextReady { context ->
                runCatching { ConfigCenter.initialize(context) }
                    .onFailure { e -> YLog.error(TAG, "ConfigCenter init failed: ${e.message}") }
                runCatching { BridgeSender.initialize(context) }
                    .onFailure { e -> YLog.error(TAG, "BridgeSender init failed: ${e.message}") }
                // V3 修复（连接层）：APP 侧注册接收器，响应 SystemUI 的配置请求
                runCatching { BridgeReceiver.initialize(context) }
                    .onFailure { e -> YLog.error(TAG, "BridgeReceiver init failed: ${e.message}") }
                YLog.info(TAG, "APP domain initialized")
            }
        }
    }

    companion object {
        private const val TAG = "ModuleEntry"
    }
}
