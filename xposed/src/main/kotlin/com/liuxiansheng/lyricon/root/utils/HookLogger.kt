/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.utils

import android.content.SharedPreferences
import android.util.Log
import com.liuxiansheng.lyricon.hyper.common.HyperLogger
import com.liuxiansheng.lyricon.hyper.common.UIConstants
import com.liuxiansheng.lyricon.xposed.BuildConfig
import io.github.libxposed.api.XposedModule

private const val TAG = "Lyricon"

/**
 * Hook 日志器（V3 自研）。
 *
 * 日志门禁（1.0.6）：
 * - debug 版（APK 可调试）：始终打印；
 * - 正式版：默认完全静默，但设置页「更多设置 → 打印日志」打开后照样打印，
 *   方便发版后临时收集日志（收集完关掉即可）。
 * 开关每 1 秒最多重读一次 remote prefs，保证改完设置不重启也能生效。
 *
 * 注意：debug 判定走包管理器 FLAG_DEBUGGABLE，不依赖 BuildConfig.DEBUG
 * （库模块的 BuildConfig.DEBUG 在 release 里可能被内联成 true，实测会误开日志）。
 */
object HookLogger : HyperLogger {
    /** debug 版：无条件下日志（包管理器读取，避免 BuildConfig 被错误内联） */
    @Volatile
    private var forceLogging = false

    /** 设置页「打印日志」开关的缓存值（release 下才需要） */
    @Volatile
    private var debugLoggingEnabled = false

    @Volatile
    private var lastFlagCheckMs = 0L

    @Volatile
    private var logPrefs: SharedPreferences? = null

    @Volatile
    private var logPrefsResolved = false

    @Volatile
    private var logLevel = UIConstants.DEFAULT_LOG_LEVEL

    private val debugStates = HashMap<String, String>()

    var module: XposedModule? = null
        set(value) {
            field = value
            logPrefs = null
            logPrefsResolved = false
            forceLogging = detectDebuggableBuild()
            synchronized(debugStates) {
                debugStates.clear()
            }
            refreshLogLevel()
        }

    /** 用包管理器判定自己是不是 debug 版（release 版不可调试 → false） */
    private fun detectDebuggableBuild(): Boolean = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val app = activityThread.getDeclaredMethod("currentApplication")
            .invoke(null) as? android.app.Application ?: return@runCatching false
        val ai = app.packageManager.getApplicationInfo(BuildConfig.APP_PACKAGE_NAME, 0)
        val raw = ai.javaClass.getField("flags").get(ai)
        val flags = (raw as? Number)?.toInt() ?: 0
        (flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }.getOrDefault(false)

    /** 当前是否允许打印日志：debug 版恒真；正式版看设置页开关 */
    val isLoggingEnabled: Boolean
        get() {
            if (forceLogging) return true
            if (!debugLoggingEnabled) {
                // 关着的时候也要低频重读，否则打开开关后不重启不生效
                refreshDebugFlagIfDue()
            }
            return debugLoggingEnabled
        }

    private fun refreshDebugFlagIfDue() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastFlagCheckMs < 1000L) return
        lastFlagCheckMs = now
        debugLoggingEnabled = readDebugFlag()
    }

    private fun readDebugFlag(): Boolean = runCatching {
        resolveLogPrefs()?.getBoolean(UIConstants.KEY_DEBUG_LOGGING, false) ?: false
    }.getOrDefault(false)

    override fun d(tag: String, msg: String) {
        if (!isLoggingEnabled) return
        if (logLevel < 1) return
        val finalMsg = format(tag, msg)
        Log.d(TAG, finalMsg)
        module?.log(Log.DEBUG, TAG, finalMsg)
    }

    /**
     * 仅在低频状态变化时输出诊断日志。调用方保持消息惰性求值，
     * 调试日志关闭时不会产生格式化开销。
     */
    fun dState(
        stateId: String,
        tag: String,
        state: String,
        message: () -> String
    ) {
        if (!isDebugEnabled) return
        val changed = synchronized(debugStates) {
            if (debugStates[stateId] == state) {
                false
            } else {
                debugStates[stateId] = state
                true
            }
        }
        if (changed) d(tag, message())
    }

    val isDebugEnabled: Boolean
        get() = isLoggingEnabled && logLevel >= 1

    override fun i(tag: String, msg: String) {
        if (!isLoggingEnabled) return
        val finalMsg = format(tag, msg)
        Log.i(TAG, finalMsg)
        module?.log(Log.INFO, TAG, finalMsg)
    }

    override fun w(tag: String, msg: String, e: Throwable?) {
        if (!isLoggingEnabled) return
        val finalMsg = format(tag, msg)
        Log.w(TAG, finalMsg, e)
        module?.log(Log.WARN, TAG, finalMsg, e)
    }

    override fun e(tag: String, msg: String, e: Throwable?) {
        if (!isLoggingEnabled) return
        val finalMsg = format(tag, msg)
        Log.e(TAG, finalMsg, e)
        module?.log(Log.ERROR, TAG, finalMsg, e)
    }

    fun refreshLogLevel() {
        logLevel = runCatching {
            resolveLogPrefs()?.getInt(UIConstants.KEY_LOG_LEVEL, UIConstants.DEFAULT_LOG_LEVEL)
                ?: UIConstants.DEFAULT_LOG_LEVEL
        }.getOrDefault(UIConstants.DEFAULT_LOG_LEVEL)
        debugLoggingEnabled = readDebugFlag()
        lastFlagCheckMs = android.os.SystemClock.uptimeMillis()
    }

    private fun resolveLogPrefs(): SharedPreferences? {
        if (logPrefsResolved) return logPrefs
        synchronized(this) {
            if (!logPrefsResolved) {
                logPrefs = runCatching {
                    module?.getRemotePreferences(UIConstants.PREF_NAME)
                }.getOrNull()
                logPrefsResolved = true
            }
        }
        return logPrefs
    }

    private fun format(tag: String, msg: String): String {
        return "[${tag.trim()}] ${msg.trim()}"
    }
}
