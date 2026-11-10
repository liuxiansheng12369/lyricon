/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


@file:Suppress("unused")

package com.liuxiansheng.lyricon.common.config

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.liuxiansheng.lyricon.common.log.YLog
import kotlinx.serialization.json.Json

/**
 * 配置中心
 *
 * 核心设计：
 * 1. Volatile 缓存：currentConfig 用 @Volatile 修饰，热路径直接读引用，0 反射 / 0 SP 读
 * 2. 不可变对象：LyriconConfig 是 data class（全 val），配置变更 = 替换整个引用
 * 3. onConfigChanged 实时生效：update() 后立即替换引用并通知所有监听器
 * 4. SP 持久化：配置变更异步写入 SP，不阻塞热路径
 *
 * 性能铁律：
 * - 热路径（帧渲染）只读 currentConfig 引用，绝不调用 load() / SP
 * - 配置变更用 update()，内部替换引用 + 主线程通知
 * - 监听器回调在主线程，避免线程安全问题
 */
object ConfigCenter {
    private const val TAG = "ConfigCenter"
    private const val PREFS_NAME = "lyricon_config"
    private const val KEY_CONFIG_JSON = "config_json"

    private val listeners = java.util.concurrent.CopyOnWriteArraySet<ConfigChangeListener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 当前配置（@Volatile 保证多线程可见性，热路径只读这个引用） */
    @Volatile
    var currentConfig: LyriconConfig = LyriconConfig()
        private set

    private var prefs: SharedPreferences? = null
    private var initialized = false

    /**
     * 初始化配置中心（从 SP 加载配置）
     * @param context Context（用 applicationContext 即可）
     */
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
        YLog.info(TAG, "initialized, island.enabled=${currentConfig.island.enabled}")
    }

    /**
     * 从 SP 加载配置（启动时调用一次）
     */
    private fun load() {
        val sp = prefs ?: return
        val jsonText = sp.getString(KEY_CONFIG_JSON, null)
        if (jsonText.isNullOrBlank()) {
            currentConfig = LyriconConfig()
            return
        }
        currentConfig = runCatching {
            json.decodeFromString(LyriconConfig.serializer(), jsonText)
        }.getOrElse {
            YLog.warning(TAG, "load config failed, use default: ${it.message}")
            LyriconConfig()
        }
    }

    /**
     * 更新配置（替换整个引用 + 通知监听器 + 异步持久化）
     *
     * @param updater 配置更新函数，传入当前配置，返回新配置
     */
    fun update(updater: (LyriconConfig) -> LyriconConfig) {
        val oldConfig = currentConfig
        val newConfig = updater(oldConfig)
        if (newConfig == oldConfig) return

        currentConfig = newConfig
        version.incrementAndGet()

        // 异步持久化（不阻塞热路径）
        persistAsync(newConfig)

        // 主线程通知
        post {
            listeners.forEach { it.onConfigChanged(newConfig, oldConfig) }
        }

        YLog.debug(TAG, "config updated, version=${version.get()}")
    }

    /**
     * 直接设置配置（用于跨进程下发等场景）
     */
    fun setConfig(newConfig: LyriconConfig) {
        val oldConfig = currentConfig
        if (newConfig == oldConfig) return

        currentConfig = newConfig
        version.incrementAndGet()
        persistAsync(newConfig)
        post { listeners.forEach { it.onConfigChanged(newConfig, oldConfig) } }
    }

    /**
     * 导出配置为 JSON（跨进程传递用）
     */
    fun configJson(): String = json.encodeToString(LyriconConfig.serializer(), currentConfig)

    /**
     * 从 JSON 恢复配置（跨进程下发后调用）
     */
    fun restoreConfig(jsonText: String?) {
        if (jsonText.isNullOrBlank()) return
        runCatching {
            val config = json.decodeFromString(LyriconConfig.serializer(), jsonText)
            setConfig(config)
            YLog.info(TAG, "config restored from json")
        }.onFailure {
            YLog.warning(TAG, "restoreConfig failed: ${it.message}")
        }
    }

    // --- 订阅管理 ---

    fun addListener(listener: ConfigChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ConfigChangeListener) {
        listeners.remove(listener)
    }

    // --- 版本号（用于配置变更检测） ---

    private val version = java.util.concurrent.atomic.AtomicInteger(0)
    val configVersion: Int get() = version.get()

    // --- 内部方法 ---

    private fun persistAsync(config: LyriconConfig) {
        val sp = prefs ?: return
        Thread {
            runCatching {
                sp.edit().putString(KEY_CONFIG_JSON, json.encodeToString(LyriconConfig.serializer(), config)).apply()
            }.onFailure {
                YLog.warning(TAG, "persist config failed: ${it.message}")
            }
        }.start()
    }

    private fun post(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}

/**
 * 配置变更监听器
 */
interface ConfigChangeListener {
    /**
     * 配置变更回调（主线程执行）
     * @param newConfig 新配置
     * @param oldConfig 旧配置
     */
    fun onConfigChanged(newConfig: LyriconConfig, oldConfig: LyriconConfig)
}
