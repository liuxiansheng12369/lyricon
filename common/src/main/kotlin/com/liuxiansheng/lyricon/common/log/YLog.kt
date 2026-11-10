/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


@file:Suppress("unused")

package com.liuxiansheng.lyricon.common.log

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * 日志工具类
 * 优先使用 LSPosed 框架日志，回退到 Android Logcat
 *
 * 性能铁律：release 版本 debug/info 默认关闭，帧路径 0 日志
 */
object YLog {
    const val TAG = "Lyricon"

    private var xposedInterface: XposedInterface? = null

    /** 是否启用调试日志（debug/verbose 级别），release 默认关闭 */
    @Volatile
    var debugEnabled: Boolean = false

    /** 是否启用信息日志（info 级别），release 默认关闭 */
    @Volatile
    var infoEnabled: Boolean = false

    /**
     * 初始化日志系统
     * @param ctx XposedInterface 实例（通常是 XposedModule）
     */
    fun init(ctx: XposedInterface) {
        xposedInterface = ctx
    }

    fun info(tag: String, msg: String) {
        if (!infoEnabled) return
        log(Log.INFO, tag, msg)
    }

    fun debug(tag: String, msg: String) {
        if (!debugEnabled) return
        log(Log.DEBUG, tag, msg)
    }

    fun error(tag: String, msg: String) {
        log(Log.ERROR, tag, msg)
    }

    fun verbose(tag: String, msg: String) {
        if (!debugEnabled) return
        log(Log.VERBOSE, tag, msg)
    }

    fun warning(tag: String, msg: String) {
        log(Log.WARN, tag, msg)
    }

    fun error(tag: String, msg: String?, e: Throwable?) {
        val xi = xposedInterface
        if (xi != null) {
            xi.log(Log.ERROR, TAG, buildMessage(tag, msg), e)
        } else {
            Log.e(TAG, buildMessage(tag, msg), e)
        }
    }

    private fun log(priority: Int, tag: String, msg: String) {
        val xi = xposedInterface
        if (xi != null) {
            xi.log(priority, TAG, buildMessage(tag, msg))
        } else {
            when (priority) {
                Log.VERBOSE -> Log.v(TAG, buildMessage(tag, msg))
                Log.DEBUG -> Log.d(TAG, buildMessage(tag, msg))
                Log.INFO -> Log.i(TAG, buildMessage(tag, msg))
                Log.WARN -> Log.w(TAG, buildMessage(tag, msg))
                Log.ERROR -> Log.e(TAG, buildMessage(tag, msg))
            }
        }
    }

    private fun buildMessage(tag: String, msg: String?): String {
        return "[$tag] ${msg ?: ""}"
    }
}
