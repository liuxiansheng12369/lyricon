/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


@file:Suppress("unused")

package com.liuxiansheng.lyricon.common.render

import android.os.Handler
import android.os.Looper
import com.liuxiansheng.lyricon.common.log.YLog
import java.util.concurrent.atomic.AtomicInteger

/**
 * 渲染协调器（治卡顿核心）
 *
 * 核心设计：
 * 1. 防抖（默认 32ms）：32ms 内的多次渲染请求合并为一次，避免频繁 invalidate
 * 2. 版本号校验：每次请求版本+1，只有最新版本的请求才执行渲染，旧请求自动丢弃
 * 3. 主线程 post：渲染动作始终在主线程执行
 * 4. 立即渲染模式：紧急场景可跳过防抖直接渲染（仍走版本号校验）
 *
 * 性能铁律：
 * - 帧路径只调用 requestRender()，不做任何计算
 * - 实际渲染逻辑在 renderAction 中执行，由协调器调度
 * - 不用 postDelayed 清除视图（会闪烁），去抖统一用本协调器
 * - 无变化不渲染（调用方负责判断数据是否变化，协调器只负责调度）
 *
 * @param debounceMs 防抖延迟（毫秒），默认 32ms（约 2 帧）
 * @param tag 日志标签
 */
class RenderCoordinator(
    private val debounceMs: Long = 32L,
    private val tag: String = "RenderCoordinator"
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val versionCounter = AtomicInteger(0)

    @Volatile
    private var pendingRunnable: Runnable? = null

    @Volatile
    private var renderAction: (() -> Unit)? = null

    /**
     * 设置渲染动作（实际的 invalidate / 视图更新逻辑）
     * 只需设置一次，后续 requestRender 会复用
     */
    fun setRenderAction(action: () -> Unit) {
        this.renderAction = action
    }

    /**
     * 请求渲染（防抖模式）
     *
     * 32ms 内的多次请求合并为一次，只有最后一次请求的版本号会执行渲染。
     * 适用于高频触发场景（进度更新、歌词滚动、特效动画参数变化等）。
     */
    fun requestRender() {
        val action = renderAction ?: return
        val currentVersion = versionCounter.incrementAndGet()

        // 取消上一次待执行的渲染
        pendingRunnable?.let { mainHandler.removeCallbacks(it) }

        val runnable = Runnable {
            // 版本号校验：只有最新版本才执行
            if (currentVersion != versionCounter.get()) {
                YLog.debug(tag, "skip stale render, version=$currentVersion, latest=${versionCounter.get()}")
                return@Runnable
            }
            pendingRunnable = null
            doRender(action)
        }
        pendingRunnable = runnable
        mainHandler.postDelayed(runnable, debounceMs)
    }

    /**
     * 立即渲染（跳过防抖）
     *
     * 仍走版本号校验，确保只有最新请求执行。
     * 适用于紧急场景（歌曲切换、播放状态变化等需要立即响应的场景）。
     */
    fun requestRenderImmediate() {
        val action = renderAction ?: return
        val currentVersion = versionCounter.incrementAndGet()

        // 取消待执行的防抖渲染
        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRunnable = null

        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (currentVersion == versionCounter.get()) {
                doRender(action)
            }
        } else {
            mainHandler.post {
                if (currentVersion == versionCounter.get()) {
                    doRender(action)
                }
            }
        }
    }

    /**
     * 取消所有待执行的渲染请求
     */
    fun cancelPending() {
        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRunnable = null
        // 版本号 +1，使任何在途的旧请求失效
        versionCounter.incrementAndGet()
    }

    /**
     * 当前是否有待执行的渲染请求
     */
    fun hasPending(): Boolean = pendingRunnable != null

    /**
     * 当前版本号（用于外部校验）
     */
    val version: Int get() = versionCounter.get()

    /**
     * 销毁协调器（取消所有待执行请求，清除渲染动作）
     */
    fun destroy() {
        cancelPending()
        renderAction = null
    }

    private fun doRender(action: () -> Unit) {
        runCatching {
            action()
        }.onFailure { e ->
            YLog.error(tag, "render action failed", e)
        }
    }
}
