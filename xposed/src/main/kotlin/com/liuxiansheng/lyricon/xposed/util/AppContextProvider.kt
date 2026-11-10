/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


@file:Suppress("unused")

package com.liuxiansheng.lyricon.xposed.util

import android.app.Application
import com.liuxiansheng.lyricon.common.log.YLog
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Application Context 提供者
 *
 * 通过 hook Application.onCreate() 获取 Application 实例（Context）。
 * libxposed API 的 PackageLoadedParam 不直接提供 appContext，需要主动 hook。
 *
 * 使用方式：
 * ```
 * AppContextProvider.initialize(module, classLoader)
 * AppContextProvider.addOnContextReady { context ->
 *     // context 就绪后初始化需要 context 的组件
 * }
 * ```
 */
object AppContextProvider {
    private const val TAG = "AppContextProvider"

    @Volatile
    var appContext: Application? = null
        private set

    private val onContextReadyListeners = CopyOnWriteArrayList<(Application) -> Unit>()

    @Volatile
    private var initialized = false

    /**
     * 初始化，hook Application.onCreate
     */
    fun initialize(module: XposedModule, classLoader: ClassLoader) {
        if (initialized) return
        initialized = true

        // 优先 hook 目标应用的 Application 类，失败则兜底 hook 全局 Application
        val targetClassName = runCatching {
            classLoader.loadClass("android.app.Application")
        }.getOrNull()

        if (targetClassName != null) {
            hookApplicationOnCreate(module, targetClassName)
        } else {
            YLog.warning(TAG, "Application class not found, context will not be available")
        }
    }

    /**
     * 注册 context 就绪回调。如果 context 已就绪，立即执行。
     */
    fun addOnContextReady(listener: (Application) -> Unit) {
        val current = appContext
        if (current != null) {
            runCatching { listener(current) }.onFailure {
                YLog.error(TAG, "onContextReady listener failed", it)
            }
            return
        }
        onContextReadyListeners.add(listener)
    }

    fun removeOnContextReady(listener: (Application) -> Unit) {
        onContextReadyListeners.remove(listener)
    }

    private fun hookApplicationOnCreate(module: XposedModule, appClass: Class<*>) {
        runCatching {
            val onCreateMethod = appClass.getDeclaredMethod("onCreate")
            module.hook(onCreateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val instance = chain.thisObject as? Application
                    if (instance != null) {
                        onApplicationCreated(instance)
                    }
                    return result
                }
            })
            YLog.info(TAG, "hooked Application.onCreate: ${appClass.name}")
        }.onFailure { e ->
            YLog.error(TAG, "failed to hook Application.onCreate", e)
        }
    }

    private fun onApplicationCreated(instance: Application) {
        if (appContext != null) return
        appContext = instance
        YLog.info(TAG, "Application context ready: ${instance.javaClass.name}")

        // 通知所有监听器
        val listeners = onContextReadyListeners.toList()
        onContextReadyListeners.clear()
        listeners.forEach { listener ->
            runCatching { listener(instance) }.onFailure {
                YLog.error(TAG, "onContextReady listener failed", it)
            }
        }
    }
}
