/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.common.UIConstants
import com.liuxiansheng.lyricon.hyper.lyric.source.SourceManager
import com.liuxiansheng.lyricon.hyper.root.island.effects.album.IslandAlbumCoverStyleHooker
import com.liuxiansheng.lyricon.hyper.root.island.effects.color.IslandMusicWaveColorHooker
import com.liuxiansheng.lyricon.hyper.root.island.effects.color.StatusBarTextColorHooker
import com.liuxiansheng.lyricon.hyper.root.island.effects.glow.IslandProgressGlowController
import com.liuxiansheng.lyricon.hyper.root.island.hooks.IslandModuleRestoreHooker
import com.liuxiansheng.lyricon.hyper.root.island.hooks.IslandLyricShareHooker
import com.liuxiansheng.lyricon.hyper.root.island.hooks.MediaIslandCreationGate
import com.liuxiansheng.lyricon.hyper.root.island.hooks.RealIslandHooker
import com.liuxiansheng.lyricon.hyper.root.island.hooks.SystemUIHookRegistry
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandNativeRefreshCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandBackgroundHook
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandBlurHook
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandOutlineHook
import com.liuxiansheng.lyricon.hyper.root.island.presentation.IslandPresentationCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.renderer.BaseIslandRenderer
import com.liuxiansheng.lyricon.hyper.root.island.renderer.IslandSettingsRefreshCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.renderer.IslandContentUpdateCoordinator
import com.liuxiansheng.lyricon.hyper.root.island.sizing.IslandCompactModeController
import com.liuxiansheng.lyricon.hyper.root.statusbar.StatusBarLyricHooker
import com.liuxiansheng.lyricon.hyper.root.plugin.PluginRuntime
import com.liuxiansheng.lyricon.hyper.root.source.LyricInfoSource
import com.liuxiansheng.lyricon.hyper.root.source.LyriconSource
import com.liuxiansheng.lyricon.hyper.root.source.RootLyricSink
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import com.liuxiansheng.lyricon.hyper.root.utils.ShellUtils
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

private const val TAG = "HookEntry"

open class HookEntry : XposedModule() {

    companion object {
        private const val STATE_RUNTIME_READY = "runtimeReady"
        private const val STATE_STATUS_BAR_TEXT_COLOR = "statusBarTextColor"

        @Volatile
        var activeMode = 0
        val lyriconSource = LyriconSource()
        // ColorOS/网易云等把歌词 JSON 塞进 MediaSession metadata["lyricInfo"] 的歌词源
        val lyricInfoSource = LyricInfoSource()
        // 超级歌词源（SuperLyricApi 第三方依赖不可用，V3 按用户要求走上游 provider 歌词源）
        // val superLyricSource = SuperLyricSource()
        var sourceManager: SourceManager? = null
            private set

        /** 当前数据接收器：加强连接断连检测生命周期管理用 */
        @Volatile
        var activeSink: RootLyricSink? = null
            private set

        @JvmStatic
        var instance: HookEntry? = null
            private set

        private val SUPER_ISLAND_RUNTIME_REFRESH_KEYS = setOf(
            RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT,
            RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE,
            RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE,
            RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT,
            RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_WIDTH_MODE,
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MIN_WIDTH,
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MAX_WIDTH,
            RootConstants.KEY_HOOK_ISLAND_DISABLE_WIDTH_LIMIT,
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS,
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.KEY_HOOK_ISLAND_LONG_PRESS_BEHAVIOR,
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_GLOW,
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_GRADIENT,
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_TRACK,
            RootConstants.KEY_HOOK_TEXT_SIZE,
            RootConstants.KEY_HOOK_TEXT_SIZE_RATIO,
            RootConstants.KEY_HOOK_FONT_WEIGHT,
            RootConstants.KEY_HOOK_FONT_ITALIC,
            RootConstants.KEY_HOOK_GRADIENT_PROGRESS,
            RootConstants.KEY_HOOK_CENTER_LYRIC,
            RootConstants.KEY_HOOK_CENTER_MUSIC_INFO,
            RootConstants.KEY_HOOK_RIGHT_LYRIC,
            RootConstants.KEY_HOOK_ANIM_ENABLE,
            RootConstants.KEY_HOOK_ANIM_ID,
            RootConstants.KEY_HOOK_SYLLABLE_RELATIVE,
            RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT,
            RootConstants.KEY_HOOK_SYLLABLE_LINE_DISPLAY,
            RootConstants.KEY_HOOK_DISABLE_TRANSLATION,
            RootConstants.KEY_HOOK_TRANSLATION_ONLY,
            RootConstants.KEY_HOOK_SWAP_TRANSLATION,
            RootConstants.KEY_HOOK_AUTO_SWITCH_TRANSLATION,
            RootConstants.KEY_SHOW_TRANSLATION,
            RootConstants.KEY_HOOK_NEXT_LYRIC_LINE,
            RootConstants.KEY_HOOK_LINE_SWITCH_ANIM,
            RootConstants.KEY_HOOK_CUSTOM_FONT_PATH,
            RootConstants.KEY_HOOK_NARROW_LATIN_FONT,
            RootConstants.KEY_HOOK_WORD_MOTION_ENABLED,
            // V3 修复：彩虹开关与自定义文字颜色键纳入运行时刷新，
            // 否则修改这两项后不触发样式重建，必须重启状态栏才生效。
            RootConstants.KEY_HOOK_RAINBOW_ENABLED,
            RootConstants.KEY_HOOK_TEXT_COLOR,
            RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND
        )
    }

    private var _prefs: android.content.SharedPreferences? = null
    private var _statusBarPrefs: android.content.SharedPreferences? = null
    private var prefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var statusBarPrefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var runtimeApp: Application? = null
    private var systemUiClassLoader: ClassLoader? = null
    private var pluginRuntime: PluginRuntime? = null
    // 3.0.613：亮屏/解锁监听——锁屏听歌后媒体岛重建，歌词容器边距可能失配
    // （歌曲暂停时播放更新不触发 maybeUpdate），亮屏后主动强制一次同步自愈。
    private var screenStateReceiver: BroadcastReceiver? = null

    val prefs: android.content.SharedPreferences
        get() {
            if (_prefs == null) {
                val remote = getRemotePreferences(UIConstants.PREF_NAME)
                // 3.0.934：remote prefs 即 App 主副本文件（libxposed 跨进程代理，
                // 落盘由系统保证，SystemUI 重启不丢）。MergedPrefs 保留仅作
                // SystemUI 本地残留的兜底，正常为空。
                val local = runtimeApp?.getSharedPreferences(
                    UIConstants.PREF_NAME, android.content.Context.MODE_PRIVATE
                )
                _prefs = if (local != null) MergedPrefs(remote, local) else remote
            }
            return _prefs!!
        }

    // 3.0.934：persistKeyToLocal 已移除——remote prefs 即 App 主副本文件，
    // 落盘由系统保证，SystemUI 无需本地持久副本（老慈木式读写）。

    /** 状态栏歌词独立 prefs（与超级岛各是各的文本、各是各的控制） */
    val statusBarPrefs: android.content.SharedPreferences
        get() {
            if (_statusBarPrefs == null) {
                val remote = getRemotePreferences(RootConstants.PREF_NAME_STATUS_BAR_LYRIC)
                val local = runtimeApp?.getSharedPreferences(
                    RootConstants.PREF_NAME_STATUS_BAR_LYRIC, android.content.Context.MODE_PRIVATE
                )
                _statusBarPrefs = if (local != null) MergedPrefs(remote, local) else remote
            }
            return _statusBarPrefs!!
        }

    /**
     * V3 专用启动入口：V3 无 HyperLyric 配置 UI，
     * 直接开启超级岛开关并启动歌词源管理器（LyriconSource 订阅本地 provider）。
     */
    fun enableSuperIslandForV3() {
        // 对齐老词幕：SystemUI 进程只读 remote prefs，绝不 edit()（只读实现，
        // 写入抛 Read only implementation）。桥接键、默认值均由 App 侧
        // MasterPrefs / SettingsActivity 写入（App 是唯一写入者），
        // SystemUI 每次初始化只按当前值落实启动/停止。

        // ColorOS Provider 歌词源：移植自老词幕 CurrentMediaInfo
        android.util.Log.d("CurrentMediaInfo", "=== enableSuperIslandForV3 开始 ===")
        runtimeApp?.let { ctx ->
            runCatching {
                com.liuxiansheng.lyricon.xposed.systemui.util.CurrentMediaInfo.initialize(ctx)
                android.util.Log.d("CurrentMediaInfo", "CurrentMediaInfo 初始化成功")
                // 实时状态推送：把正在播的歌名/歌词/播放状态推给 App（首页实时预览 + 听歌记录）
                com.liuxiansheng.lyricon.hyper.root.live.LiveStatePusher.start(ctx)
            }.onFailure { e ->
                android.util.Log.e("CurrentMediaInfo", "CurrentMediaInfo 初始化失败", e)
            }
        } ?: run {
            android.util.Log.e("CurrentMediaInfo", "runtimeApp 为 null")
        }

        runCatching {
            val split = prefs.getBoolean(
                "island_split_lyric_mode",
                RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT != RootConstants.ISLAND_CONTENT_MODE_LYRIC
            )
            HookLogger.i(
                TAG,
                "超级岛初始化: split=$split, enabled=${SystemUiEnhancementGate.isEnabled()}"
            )
        }.onFailure { e ->
            HookLogger.e(TAG, "读取超级岛配置失败", e)
        }
        // 总开关决定是否自启：开 → 启动歌词源；关 → 彻底关闭（停止源 + 清除视图），
        // 不再无条件启动（"关了还自动启动/自动全开"的根因）。
        val statusBarLyricOn = statusBarPrefs.getBoolean(
            RootConstants.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC,
            true
        )
        if (SystemUiEnhancementGate.isEnabled() || statusBarLyricOn) {
            startLyricSourcesWithRetry(0)
        }
        // 状态栏歌词独立于超级岛总开关：状态栏开关开即挂载（总开关开/关都各自独立）
        if (statusBarLyricOn) {
            runtimeApp?.let {
                StatusBarLyricHooker.setPrefs(statusBarPrefs)
                StatusBarLyricHooker.remount(it)
            }
        } else {
            StatusBarLyricHooker.disable()
        }
        if (!SystemUiEnhancementGate.isEnabled()) {
            updateSystemUiEnhancements(false)
        }
    }

    /**
     * 设置变更广播到达后的统一刷新入口（V3 连接层修复）。
     *
     * APP 设置页每次写配置后通过 BridgeSender 广播通知，SystemUI 侧
     * BridgeReceiver 收到后回调本方法：重新读取总开关并刷新超级岛，
     * 不依赖 remote prefs 跨进程监听（App 进程被杀后台时监听会失效）。
     */
    fun onSettingsBroadcastReceived() {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                // 先清缓存，确保总开关强制重读（否则广播到达时读到旧缓存）
                SystemUiEnhancementGate.invalidateCache()
                val enabled = SystemUiEnhancementGate.isEnabled()
                updateSystemUiEnhancements(enabled)
                IslandSettingsRefreshCoordinator.request()
                // 上游岛外观（材质类型/模糊/描边）刷新：广播是 V3 可靠的配置通道，
                // prefs 跨进程监听不可靠，必须在此显式刷新，否则切材质不生效。
                runCatching {
                    IslandBackgroundHook.onConfigChanged()
                    IslandBlurHook.onConfigChanged()
                    IslandOutlineHook.onConfigChanged()
                }.onFailure { e ->
                    HookLogger.e(TAG, "上游岛外观刷新失败", e)
                }
                HookLogger.i(TAG, "设置广播刷新完成: enabled=$enabled")
            }.onFailure { e ->
                HookLogger.e(TAG, "设置广播刷新失败", e)
            }
        }
    }

    private fun startLyricSourcesWithRetry(attempt: Int) {
        val mgr = sourceManager
        if (mgr != null) {
            runCatching {
                mgr.start()
                IslandSettingsRefreshCoordinator.request()
                HookLogger.i(TAG, "V3 歌词源管理器已启动")
            }.onFailure { e ->
                HookLogger.e(TAG, "启动歌词源管理器失败", e)
            }
            return
        }
        if (attempt >= 10) {
            HookLogger.w(TAG, "sourceManager 未就绪，放弃启动")
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            startLyricSourcesWithRetry(attempt + 1)
        }, 500L)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        instance = this
        HookLogger.module = this
        HookLogger.i(TAG, "模块加载完成（V3 词岛显示管线）")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        param.setSavedInstanceState(
            Bundle().apply {
                putBoolean(STATE_RUNTIME_READY, runtimeApp != null)
                putInt(
                    STATE_STATUS_BAR_TEXT_COLOR,
                    StatusBarTextColorHooker.currentTextColor()
                )
            }
        )
        // The media-card hookers intentionally stay alive in the old generation. Their
        // configuration is restart-only, so replacing them here is both unnecessary and
        // unsafe for active SystemUI card/fake-view animations.
        cleanupRuntime(preserveMediaHooks = true)
        HookLogger.d(TAG, "超级岛歌词热重载准备完成")
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        instance = this
        HookLogger.module = this

        var replacedCount = 0
        var retainedNoReloadCount = 0
        param.oldHookHandles.forEach { handle ->
            val executable = handle.executable
            val replacement = createLyricReplacementHooker(executable)
            if (replacement != null) {
                runCatching {
                    handle.replaceHook(replacement)
                    replacedCount++
                }.onFailure {
                    // A failed replacement leaves the old hook in place. This is safer than
                    // losing an active island feature while SystemUI continues to run.
                    retainedNoReloadCount++
                }
            } else {
                // Media cards deliberately fall into this branch: no replacement, no runtime
                // config read, no mutation of an in-flight card/fake-view animation.
                retainedNoReloadCount++
            }
        }

        val state = param.savedInstanceState as? Bundle
        if (state?.containsKey(STATE_STATUS_BAR_TEXT_COLOR) == true) {
            StatusBarTextColorHooker.restoreTextColor(
                state.getInt(STATE_STATUS_BAR_TEXT_COLOR)
            )
        }
        if (state?.getBoolean(STATE_RUNTIME_READY) == true) {
            findCurrentApplication()?.let { app ->
                Handler(Looper.getMainLooper()).post { initializeSystemEnvironment(app) }
            } ?: HookLogger.w(
                TAG,
                "热重载后未取得当前 Application，等待 Application.onCreate"
            )
        }
        HookLogger.i(
            TAG,
            "超级岛歌词热重载完成: replaced=$replacedCount, " +
                    "retainedNoReload=$retainedNoReloadCount"
        )
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val processName = runCatching { Application.getProcessName() }.getOrNull() ?: ""

        // 仅在主进程注入
        if (processName.contains(":")) return

        val packageName = param.packageName

        if (packageName == "com.android.systemui") {
            systemUiClassLoader = param.defaultClassLoader
            StatusBarTextColorHooker.setTextColorChangedListener {
                BaseIslandRenderer.updateTextColors()
            }
            StatusBarTextColorHooker.hook(this, param.defaultClassLoader)
            try {
                UnlockIslandWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                HookLogger.w(TAG, "此系统版本不支持超级岛下拉小窗白名单")
                } else {
                    HookLogger.e(TAG, "超级岛下拉小窗白名单注入失败", e)
                }
            }
            try {
                UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                HookLogger.w(TAG, "此系统版本不支持解锁焦点通知白名单")
                } else {
                    HookLogger.e(TAG, "焦点通知白名单注入失败", e)
                }
            }

            activeMode = prefs.getInt(
                RootConstants.KEY_HOOK_LYRIC_MODE,
                RootConstants.DEFAULT_HOOK_LYRIC_MODE
            )

            // 3.0.934：移除 SystemUI 启动对账——remote prefs 即 App 主副本
            // 文件，落盘由系统保证，重启不丢，无需 su 对账（老慈木式读写）。

            // 劫持 Application.onCreate 以初始化 Lyricon Receiver 所需的环境
            try {
                val appClass = param.defaultClassLoader.loadClass("android.app.Application")
                val onCreateMethod = appClass.getDeclaredMethod("onCreate")
                deoptimize(onCreateMethod)
                hook(onCreateMethod).intercept(AppCreateHooker())
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    HookLogger.w(TAG, "跳过生命周期 Hook: target=Application.onCreate")
                } else {
                    HookLogger.e(
                        TAG,
                        "安装生命周期 Hook 失败: target=Application.onCreate",
                        e
                    )
                }
            }

            // 核心：拦截 ClassLoader 构造，以捕捉 miui.systemui.plugin 等动态加载的插件
            try {
                val clClass = Class.forName("dalvik.system.BaseDexClassLoader")
                for (constructor in clClass.declaredConstructors) {
                    deoptimize(constructor)
                    hook(constructor).intercept(ClassLoaderHooker())
                }
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    HookLogger.w(TAG, "跳过插件加载 Hook: target=BaseDexClassLoader")
                } else {
                    HookLogger.e(
                        TAG,
                        "安装插件加载 Hook 失败: target=BaseDexClassLoader",
                        e
                    )
                }
            }

        } else if (packageName == "miui.systemui.plugin") {
            SystemUIHookRegistry.hook(this, param.defaultClassLoader)
        }
    }

    // 3.0.934：reconcilePrefsFromApp 已移除（见启动路径注释）。
    // remote prefs 即 App 主副本文件，无需 su 对账恢复。

    private fun initializeSystemEnvironment(app: Application) {
        try {
            // ColorOS Provider 歌词源：这里只注入 Application，
            // 真正的 start() 放在下方正式 sink 就绪后执行（见 517 附近），
            // 否则此处的临时 sink 会把 start 去重挡住，正式渲染链路接不上。
            runCatching {
                lyricInfoSource.initialize(app)
            }.onFailure { e ->
                HookLogger.e(TAG, "LyricInfoSource 初始化失败", e)
            }

            cleanupRuntime()
            runtimeApp = app

            // 3.0.1094：音乐岛创建拦截（移植上游 HyperIsland v3.2.3 的 hookMediaIslandVisibility，
            // 改为跟随本模块模式：状态栏歌词模式拦截建岛、超级岛模式放行）。
            // 必须装在 Application classLoader 上：状态栏模式时超级岛插件可能根本没加载，
            // 挂到 IslandTemplateBuilder 的插件 cl 上会漏装。
            runCatching {
                val ok = MediaIslandCreationGate.hook(this, app.classLoader, app)
                if (!ok) {
                    HookLogger.w(TAG, "音乐岛拦截 Hook 未安装（类/签名不匹配，播放不受影响）")
                }
            }.onFailure { e ->
                HookLogger.e(TAG, "音乐岛拦截 Hook 安装异常", e)
            }

            val renderer = BaseIslandRenderer
            pluginRuntime = runCatching {
                PluginRuntime(this, app).also { it.loadEnabledPlugins() }
            }.onFailure { error ->
                HookLogger.w(TAG, "插件 Runtime 初始化失败，继续使用原有歌词链路", error)
            }.getOrNull()
            val sink = RootLyricSink(
                renderer = renderer,
                context = app,
                prefs = prefs,
                pluginRuntime = pluginRuntime,
                // 加强连接：检测到数据停滞（播放中无位置推进）→ 重新注册订阅端恢复数据流
                onConnectionLost = { lyriconSource.restart() }
            )
            activeSink = sink
            sink.startReconnectCheck()

            // 状态栏歌词：注册布局 hook（拿状态栏根容器）
            systemUiClassLoader?.let { cl ->
                // 状态栏颜色/彩虹/封面/自定义色跟随主设置（与超级岛同一份），一次注入即可
                runCatching { StatusBarLyricHooker.setMasterPrefs(prefs) }
                runCatching { StatusBarLyricHooker.hook(this, cl, app) }
                    .onFailure { e -> HookLogger.e(TAG, "状态栏歌词 hook 注册失败", e) }
            }

            lyriconSource.initialize(app, prefs)
            lyricInfoSource.initialize(app)
            // superLyricSource.initialize(app)

            sourceManager = SourceManager(
                sources = listOf(lyriconSource, lyricInfoSource),
                prefs = prefs,
                sink = sink,
                prefKey = RootConstants.KEY_HOOK_LYRIC_SOURCE,
                defaultSourceId = RootConstants.DEFAULT_HOOK_LYRIC_SOURCE,
                logger = HookLogger
            )
            activeMode = prefs.getInt(
                RootConstants.KEY_HOOK_LYRIC_MODE,
                RootConstants.DEFAULT_HOOK_LYRIC_MODE
            )
            val statusBarLyricOnInit = statusBarPrefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC,
                true
            )
            if (SystemUiEnhancementGate.isEnabled() || statusBarLyricOnInit) {
                sourceManager?.start()
            }

            // ColorOS Provider 歌词源：直接启动，不通过 SourceManager
            // 两个歌词源都工作：V3 自己的 lyriconSource + ColorOS 的 LyricInfoSource
            runCatching {
                val infoSink = object : com.liuxiansheng.lyricon.hyper.lyric.source.LyricSink {
                    override fun onSongChanged(song: com.liuxiansheng.lyricon.hyper.lyric.model.Song?) {
                        sink.onSongChanged(song)
                    }
                    override fun onLyricLine(line: com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.IRichLyricLine) {
                        sink.onLyricLine(line)
                    }
                    override fun onPlainText(text: String?) { sink.onPlainText(text) }
                    override fun onStop() {}
                    override fun onMetadata(metadata: com.liuxiansheng.lyricon.hyper.lyric.model.LyricMediaMetadata?) {
                        sink.onMetadata(metadata)
                    }
                    override fun onPlaybackStateChanged(isPlaying: Boolean, playbackSpeed: Float) {
                        sink.onPlaybackStateChanged(isPlaying, playbackSpeed)
                    }
                    override fun onPositionChanged(position: Long, playbackSpeed: Float) {
                        sink.onPositionChanged(position, playbackSpeed)
                    }
                }
                lyricInfoSource.start(infoSink)
            }.onFailure { e ->
                HookLogger.e(TAG, "LyricInfoSource 启动失败", e)
            }

            // 3.0.613：亮屏/解锁监听——锁屏听歌后媒体岛重建可能使歌词容器边距失配，
            // 亮屏后延迟强制同步（歌曲暂停时播放更新不触发 maybeUpdate，需主动兜底）。
            registerScreenStateReceiver(app)

            prefListener =
                android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    // 3.0.934：不再落盘 SystemUI 本地——remote prefs 即 App
                    // 主副本文件，落盘由系统保证，SystemUI 无需本地副本。
                    if (key == UIConstants.KEY_LOG_LEVEL) {
                        HookLogger.refreshLogLevel()
                        HookLogger.d(TAG, "日志配置已刷新: level=${prefs.getInt(key, UIConstants.DEFAULT_LOG_LEVEL)}")
                        return@OnSharedPreferenceChangeListener
                    }
                    if (key?.startsWith(RootConstants.KEY_HOOK_LYRICON_PROVIDER_DELAY_PREFIX) == true) {
                        lyriconSource.onPreferenceChanged(key)
                    }
                    // ★ 3.0.1092：毛玻璃/材质/玻璃参数变更必须走「上游外观引擎」的刷新与卸载。
                    //   此前这些键没有特判，全部落到 when 的 else → 只调 IslandSettingsRefreshCoordinator
                    //   （它只重建歌词内容，完全不碰材质），已挂上去的材质没有任何人卸载
                    //   → 现场表现就是「毛玻璃关了却还是开着，只能重启 SystemUI 才恢复」。
                    if (key != null && (
                            key.startsWith("pref_island_material_") ||
                                    key.startsWith("pref_island_blur_") ||
                                    key.startsWith("pref_island_glass_") ||
                                    key.startsWith("pref_island_refraction_") ||
                                    key.startsWith("pref_island_bg_")
                            )
                    ) {
                        Handler(Looper.getMainLooper()).post {
                            runCatching {
                                IslandBackgroundHook.onConfigChanged()
                                IslandBlurHook.onConfigChanged()
                                IslandOutlineHook.onConfigChanged()
                                val masterOn = prefs.getBoolean(
                                    RootConstants.KEY_ISLAND_MATERIAL_ENABLED,
                                    RootConstants.DEFAULT_ISLAND_MATERIAL_ENABLED
                                )
                                if (!masterOn) {
                                    // 总开关关闭：无条件卸载残留材质（柔光 Bionics / 液态·高斯·高光
                                    // drawable），并恢复被替换的系统背景——是「卸载」而不只是「不再新挂」。
                                    IslandBlurHook.unloadAll("master-off-pref")
                                    HookLogger.i(TAG, "毛玻璃总开关关闭 → 已卸载全部材质: key=$key")
                                } else {
                                    HookLogger.i(TAG, "毛玻璃参数变更 → 已刷新材质: key=$key")
                                }
                            }.onFailure { e ->
                                HookLogger.e(TAG, "毛玻璃参数变更刷新失败: key=$key", e)
                            }
                            // 顺带重建一次歌词内容，避免材质层变化后歌词被旧图层遮住。
                            IslandSettingsRefreshCoordinator.request()
                        }
                        return@OnSharedPreferenceChangeListener
                    }

                    when (key) {
                        RootConstants.KEY_HOOK_LYRIC_SOURCE -> {
                            val newSourceId =
                                prefs.getString(key, RootConstants.DEFAULT_HOOK_LYRIC_SOURCE)
                                    ?: RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
                            if (!SystemUiEnhancementGate.isEnabled()) {
                                HookLogger.d(
                                    TAG,
                                    "配置未生效: key=$key, reason=system_ui_enhancement_disabled"
                                )
                                return@OnSharedPreferenceChangeListener
                            }
                            Handler(Looper.getMainLooper()).post {
                                val manager = sourceManager
                                manager?.switchSource(newSourceId)
                                val activeSourceId = manager?.getActiveSource()?.id
                                if (activeSourceId == newSourceId) {
                                    HookLogger.i(TAG, "歌词源切换完成: source=$newSourceId")
                                }
                            }
                        }

                        RootConstants.KEY_HOOK_LYRIC_MODE -> {
                            val newMode = prefs.getInt(key, RootConstants.DEFAULT_HOOK_LYRIC_MODE)
                            if (newMode == activeMode) {
                                HookLogger.d(
                                    TAG,
                                    "配置未生效: key=$key, value=$newMode, reason=value_unchanged"
                                )
                                return@OnSharedPreferenceChangeListener
                            }
                            Handler(Looper.getMainLooper()).post {
                                activeMode = newMode
                                IslandSettingsRefreshCoordinator.request()
                                HookLogger.i(TAG, "歌词模式切换完成: mode=$newMode")
                            }
                        }

                        RootConstants.KEY_HOOK_PLACEHOLDER_FORMAT -> {
                            val format = prefs.getInt(
                                key,
                                RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
                            )
                            Handler(Looper.getMainLooper()).post {
                                val changed = LyriconDataBridge.updatePlaceholderFormat(format)
                                if (changed) {
                                    BaseIslandRenderer.updateLyricLine()
                                    BaseIslandRenderer.updatePosition(
                                        LyriconDataBridge.currentPosition,
                                        1f
                                    )
                                } else {
                                    HookLogger.d(
                                        TAG,
                                        "配置未生效: key=$key, value=$format, reason=value_unchanged_or_no_active_lyric"
                                    )
                                }
                            }
                        }

                        RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND -> {
                            Handler(Looper.getMainLooper()).post {
                                // 与广播桥（onSettingsBroadcastReceived）同一流程：
                                // 先清 Gate 缓存，强制实时重读，避免 IPC 偶发失败回退旧值
                                // 导致总开关"时好时坏"。
                                SystemUiEnhancementGate.invalidateCache()
                                updateSystemUiEnhancements(SystemUiEnhancementGate.isEnabled())
                            }
                        }

                        RootConstants.KEY_HOOK_ISLAND_ALBUM_COVER_STYLE -> {
                            Handler(Looper.getMainLooper()).post {
                                IslandSettingsRefreshCoordinator.request()
                            }
                        }

                        RootConstants.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC -> {
                            // 状态栏歌词独立开关：不受超级岛总开关 gate，实时挂载/卸载
                            Handler(Looper.getMainLooper()).post {
                                val on = statusBarPrefs.getBoolean(
                                    RootConstants.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC,
                                    true
                                )
                                if (on) {
                                    // 互拆/关超导后源可能被停：启用状态栏歌词必须确保歌词源在跑
                                    sourceManager?.start()
                                    runtimeApp?.let {
                                        StatusBarLyricHooker.setPrefs(statusBarPrefs)
                                        StatusBarLyricHooker.remount(it)
                                    }
                                } else {
                                    StatusBarLyricHooker.disable()
                                }
                            }
                        }

                        RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_STYLE -> {
                            Handler(Looper.getMainLooper()).post {
                                IslandSettingsRefreshCoordinator.request()
                            }
                        }

                        RootConstants.KEY_HOOK_TEXT_COLOR_STYLE -> {
                            Handler(Looper.getMainLooper()).post {
                                BaseIslandRenderer.updateTextColors()
                            }
                        }

                        RootConstants.KEY_ISLAND_SPECTRUM_LINK_WIDTH -> {
                            // 频谱封面隐藏：切开关实时刷新，无需重启 SystemUI
                            Handler(Looper.getMainLooper()).post {
                                IslandSettingsRefreshCoordinator.request()
                                // V3 修复：立即对已挂载的岛应用隐藏状态，不依赖系统岛
                                // 原生刷新回调（回调只在播放中/系统岛刷新时触发，
                                // 改开关时经常不回调导致无效/等下一首才生效）。
                                IslandContentUpdateCoordinator.forEachActiveHost { root, _, prefs, config ->
                                    IslandCompactModeController.maybeUpdate(
                                        root, prefs, config, immediate = true
                                    )
                                }
                            }
                        }

                        in SUPER_ISLAND_RUNTIME_REFRESH_KEYS -> {
                            Handler(Looper.getMainLooper()).post {
                                IslandSettingsRefreshCoordinator.request()
                            }
                        }

                        else -> {
                            // 兜底（对齐老词幕 reloadConfig）：未特判的任意设置变更
                            // → 全量刷新超级岛。保证"设置即生效"，不再依赖重启
                            // SystemUI / 等下一句。刷新本身有 16ms 防抖 + 无有效宿主
                            // 自动跳过，不会闪。
                            Handler(Looper.getMainLooper()).post {
                                IslandSettingsRefreshCoordinator.request()
                            }
                        }
                    }
                }
            prefListener?.let {
                prefs.registerOnSharedPreferenceChangeListener(it)
                // 状态栏歌词独立 prefs 监听：任何状态栏设置键变化 → 重挂载（实时生效，不受超级岛总开关 gate）
                statusBarPrefListener =
                    android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        val isStatusBarKey = key == RootConstants.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC ||
                                key?.startsWith("key_hook_status_bar_lyric_") == true ||
                                // 3.0.796：翻译 4 项 + 下一句预览键也纳入状态栏监听——
                                // 否则改显示翻译/独占/交换/自动切换后状态栏歌词 view 不重建，
                                // displayTranslation/displayRoma 永远停在 attach 时的值 → 翻译开关无效
                                key == RootConstants.KEY_SHOW_TRANSLATION ||
                                key == RootConstants.KEY_HOOK_TRANSLATION_ONLY ||
                                key == RootConstants.KEY_HOOK_SWAP_TRANSLATION ||
                                key == RootConstants.KEY_HOOK_AUTO_SWITCH_TRANSLATION ||
                                key == RootConstants.KEY_HOOK_NEXT_LYRIC_LINE
                        if (!isStatusBarKey) return@OnSharedPreferenceChangeListener
                        HookLogger.i(TAG, "状态栏设置变更触发刷新: key=$key isStatusBarKey=$isStatusBarKey")
                        Handler(Looper.getMainLooper()).post {
                            val on = statusBarPrefs.getBoolean(
                                RootConstants.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC,
                                true
                            )
                            if (on) {
                                // 互拆/关超导后源可能被停：启用状态栏歌词必须确保歌词源在跑
                                sourceManager?.start()
                                runtimeApp?.let {
                                    StatusBarLyricHooker.setPrefs(statusBarPrefs)
                                    StatusBarLyricHooker.remount(it)
                                }
                            } else {
                                StatusBarLyricHooker.disable()
                            }
                        }
                    }
                statusBarPrefs.registerOnSharedPreferenceChangeListener(statusBarPrefListener)
            }

            HookLogger.i(
                TAG,
                "系统环境初始化完成: enabled=${SystemUiEnhancementGate.isEnabled()}, " +
                        "source=${sourceManager?.getActiveSource()?.displayName ?: "inactive"}, " +
                        "mode=$activeMode"
            )
        } catch (e: Exception) {
            HookLogger.e(TAG, "系统环境初始化失败", e)
        }
    }

    /**
     * 3.0.613：注册亮屏/解锁监听——锁屏听歌后系统重建媒体岛，歌词容器边距可能
     * 仍是隐藏时的 0（封面频谱已显示 → 歌词占满遮挡）；歌曲暂停时播放更新不触发
     * maybeUpdate，亮屏后延迟强制对账，让紧凑模式按当前开关自愈（无需切歌）。
     */
    private fun registerScreenStateReceiver(app: Application) {
        runCatching {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action
                    // 1.2.36 租约：关屏 → 交还系统（停止自动重建，不再抢布局/透明度）
                    if (action == Intent.ACTION_SCREEN_OFF) {
                        // 1.2.55：用户要求先取消"锁屏切状态栏歌词 / 解锁重建岛"这套租约方案，
                        // 让系统按原样管屏幕开关，好单独验证"更新岛视图"这条路。
                        // 方案代码保留，开关 SCREEN_LEASE_MODE 打开即可恢复。
                        if (RealIslandHooker.SCREEN_LEASE_MODE) {
                            runCatching { RealIslandHooker.suspendForScreenOff() }
                            runCatching { enterStatusBarLyricOnLock(app) }
                        }
                        return
                    }
                    if (action != Intent.ACTION_SCREEN_ON &&
                        action != Intent.ACTION_USER_PRESENT
                    ) {
                        return
                    }
                    // 1.2.46：亮屏 ≠ 解锁。SCREEN_ON 时锁屏还在，系统判"不许显示岛"，
                    // 这时塞数据白塞（日志里 islandOn 一直 false）。真正的解锁信号是 USER_PRESENT，
                    // 只在它到来后才做"主动生成"，并再多等 800ms 让系统解锁流程先跑完。
                    val unlocked = action == Intent.ACTION_USER_PRESENT
                    // 等媒体岛重建/布局稳定后再同步，避免在重建中途抢跑。
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching {
                            // 租约重挂：先放行自动重建并重挂歌词，再做紧凑模式/材质同步
                            if (RealIslandHooker.SCREEN_LEASE_MODE) {
                                RealIslandHooker.resumeForScreenOn(allowRelease = unlocked)
                                // 1.2.41：解锁后把"锁屏临时状态栏歌词"收回，回到用户自己的设置 + 超级岛
                                runCatching { leaveStatusBarLyricOnUnlock(app) }
                            }
                            IslandContentUpdateCoordinator.forEachActiveHost { root, _, p, config ->
                                IslandCompactModeController.maybeUpdate(root, p, config)
                            }
                            // 锁屏时系统会关 pass-blur / 玻璃 view detach 时 release 材质，
                            // 解锁后必须重新 apply，否则透明玻璃一直空着（pass-blur=false）。
                            IslandBackgroundHook.onConfigChanged()
                            IslandBlurHook.onConfigChanged()
                            IslandOutlineHook.onConfigChanged()
                            HookLogger.i(TAG, "亮屏后紧凑模式 + 玻璃材质同步完成")
                        }.onFailure { e ->
                            HookLogger.e(TAG, "亮屏后同步失败", e)
                        }
                    }, 600)
                    // 1.2.57：解锁后固定走两次系统"更新岛视图"，与租约开关无关。
                    // 起因（反编译实证）：系统 lockScreen(true) 会对所有岛内容调
                    // hideAllElementSurface() → contentView.resetStatus()，岛体表面被清掉；
                    // 解锁时 lockScreen(false) 只清标志、不恢复表面，要等下一次媒体更新
                    // 才会重建 → 所以"下一首能出岛、干等不行"。这里就是替系统补那次重建。
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching {
                            RealIslandHooker.requestSystemUpdateIslandView("解锁 1.2s 更新岛视图")
                        }.onFailure { e -> HookLogger.e(TAG, "解锁更新岛视图失败", e) }
                    }, 1200)
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching {
                            RealIslandHooker.requestSystemUpdateIslandView("解锁 3.5s 补更新岛视图")
                        }.onFailure { e -> HookLogger.e(TAG, "解锁补更新岛视图失败", e) }
                    }, 3500)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            screenStateReceiver = receiver
            HookLogger.i(TAG, "屏幕开关/解锁监听已注册（租约：关屏交还、亮屏重挂）")
        }.onFailure { e ->
            HookLogger.e(TAG, "注册亮屏监听失败", e)
        }
    }

    // ===== 1.2.41：锁屏临时切状态栏歌词 / 亮屏收回（不改用户设置）=====
    private var sbLyricLockTemporary = false

    internal fun enterStatusBarLyricOnLock(anchor: android.content.Context) {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                sbLyricLockTemporary = true
                sourceManager?.start()
                StatusBarLyricHooker.setPrefs(statusBarPrefs)
                StatusBarLyricHooker.remount(anchor)
                HookLogger.i(TAG, "锁屏：临时切到状态栏歌词（不改用户设置）")
            }.onFailure { e ->
                HookLogger.e(TAG, "锁屏切状态栏歌词失败", e)
            }
        }
    }

    internal fun leaveStatusBarLyricOnUnlock(anchor: android.content.Context) {
        if (!sbLyricLockTemporary) return
        sbLyricLockTemporary = false
        Handler(Looper.getMainLooper()).post {
            runCatching {
                val userOn = statusBarPrefs.getBoolean(
                    RootConstants.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC, true
                )
                if (userOn) {
                    sourceManager?.start()
                    StatusBarLyricHooker.setPrefs(statusBarPrefs)
                    StatusBarLyricHooker.remount(anchor)
                } else {
                    StatusBarLyricHooker.disable()
                }
                HookLogger.i(TAG, "解锁：状态栏歌词回到用户设置 userOn=$userOn")
            }.onFailure { e ->
                HookLogger.e(TAG, "解锁恢复状态栏歌词设置失败", e)
            }
        }
    }

    private fun updateSystemUiEnhancements(enabled: Boolean) {
        val statusBarLyricEnabled = statusBarPrefs.getBoolean(
            RootConstants.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC,
            true
        )
        // 歌词源共享常驻：模块激活期间一直运行，超导/状态栏模式互拆切换不停源，
        // 保证歌词实时写入、切换零延迟（只停源发生在模块整体停用 cleanupRuntime）
        sourceManager?.start()
        if (enabled) {
            IslandSettingsRefreshCoordinator.request()
            // 状态栏歌词独立于超级岛总开关：由自身开关控制
            if (statusBarLyricEnabled) {
                runtimeApp?.let {
                    StatusBarLyricHooker.setPrefs(statusBarPrefs)
                    StatusBarLyricHooker.remount(it)
                }
            } else {
                StatusBarLyricHooker.disable()
            }
            // 切到超导：disable 释放超导岛回原样后，再强制重排一次，让歌词立即注入显示。
            // （此前 refresh 放在 disable 前调用，会被 disable 的 clearAllViews 清掉，
            //   等于没刷 → "切到灵动岛要过一会才反应"）
            // 先主动重新注册最近媒体岛宿主（切回超导不触发系统 update → 宿主没进 registry），
            // 否则 refreshActiveIsland 空跑、歌词不过（必须换歌/重启才恢复）。
            runCatching { IslandPresentationCoordinator.reRegisterLastMediaHost() }
            runCatching { BaseIslandRenderer.refreshActiveIsland() }
            // 3.0.1097 修正：状态栏模式期间音乐岛被源头拦截/摘掉后，系统不会自己再 add
            // （只有播放/暂停/切歌等媒体事件才 add），所以切回超导必须主动让系统把岛重建回来，
            // 否则表现是"切回超级岛后岛不回来，要等下一首歌"。
            // 延迟 600ms：等上面 remount / 刷新落定后再建，避免刚建就被模式切换逻辑清掉。
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { MediaIslandCreationGate.forceRecreate("切回超级岛模式") }
            }, 600L)
        } else {
            // 总开关只关超级岛；不停止歌词源、不清 LyriconDataBridge（状态栏歌词继续用）
            BaseIslandRenderer.clearAllViews()
            // 恢复被模块隐藏的封面/频谱视图（关总开关=超级岛恢复出厂）
            IslandProgressGlowController.clearAll()
            IslandContentUpdateCoordinator.forEachActiveHost { root, _, _, _ ->
                IslandCompactModeController.applyNow(root, false)
            }
            IslandAlbumCoverStyleHooker.refresh()
            IslandMusicWaveColorHooker.refresh()
            if (statusBarLyricEnabled) {
                runtimeApp?.let {
                    StatusBarLyricHooker.setPrefs(statusBarPrefs)
                    StatusBarLyricHooker.remount(it)
                }
            } else {
                StatusBarLyricHooker.disable()
            }
        }
        HookLogger.i(TAG, "更新系统界面增强状态: enabled=$enabled")
    }

    private fun cleanupRuntime(preserveMediaHooks: Boolean = false) {
        IslandNativeRefreshCoordinator.clear()
        StatusBarLyricHooker.disable()
        if (!preserveMediaHooks) {
            IslandAlbumCoverStyleHooker.cleanup()
            IslandMusicWaveColorHooker.cleanup()
        }
        prefListener?.let {
            runCatching { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        }
        prefListener = null
        statusBarPrefListener?.let {
            runCatching { statusBarPrefs.unregisterOnSharedPreferenceChangeListener(it) }
        }
        statusBarPrefListener = null
        screenStateReceiver?.let {
            runCatching { runtimeApp?.unregisterReceiver(it) }
        }
        screenStateReceiver = null
        runCatching { pluginRuntime?.close() }
        pluginRuntime = null
        runCatching { activeSink?.stopReconnectCheck() }
        activeSink = null
        runCatching { sourceManager?.stop() }
        sourceManager = null
        runtimeApp = null
    }

    private fun findCurrentApplication(): Application? {
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThreadClass.getDeclaredMethod("currentApplication")
            currentApplication.invoke(null) as? Application
        }.getOrNull()
    }

    private fun createLyricReplacementHooker(executable: Executable): Hooker? {
        val owner = executable.declaringClass.name
        if (executable is Constructor<*> && owner == "dalvik.system.BaseDexClassLoader") {
            return ClassLoaderHooker(lyricsOnly = true)
        }
        if (executable !is Method) return null

        return when (executable.name) {
            "onCreate" -> AppCreateHooker().takeIf { owner == "android.app.Application" }
            "updateBigIslandView" -> RealIslandHooker.UpdateBigIslandViewHook()
            // 1.2.54：记账系统自己的 updateDynamicIslandView（岛窗口 + 媒体数据 + 真实宽度），
            // 解锁后按系统原话重放，走完整"更新岛视图"路径。
            "updateDynamicIslandView" -> RealIslandHooker.UpdateDynamicIslandViewHook()
                .takeIf { owner.endsWith("DynamicIslandWindowView") }
            // 基线实验：hideIslandLayout/showIslandLayout 的"保歌词"拦截与 IslandTextHooker
            // 里的 LEGACY_ISLAND_GUARDS 同步停用，热重载时不再重新挂上。
            "hideIslandLayout", "showIslandLayout" -> null

            "updateModuleView" -> IslandModuleRestoreHooker.UpdateModuleViewHook()
                .takeIf { owner.endsWith("IslandTemplateBuilder") }

            "bindData" -> IslandModuleRestoreHooker.AdapterBindDataHook()
                .takeIf { owner.endsWith("IslandModuleViewHolderAdapter") }

            "updateView" -> IslandModuleRestoreHooker.AdapterUpdateViewHook()
                .takeIf { owner.endsWith("IslandModuleViewHolderAdapter") }

            "onLongPressed" -> IslandLyricShareHooker.LongPressedHook()
                .takeIf { owner.endsWith("DynamicIslandBaseContentViewController") }

            else -> StatusBarTextColorHooker.createReplacement(executable)
        }
    }

    /**
     * 动态类加载器劫持
     */
    inner class ClassLoaderHooker(
        private val lyricsOnly: Boolean = false
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (PluginRuntime.isCreatingPluginClassLoader()) return result
            val cl = chain.thisObject as? ClassLoader ?: return result
            try {
                SystemUIHookRegistry.hook(this@HookEntry, cl, lyricsOnly = lyricsOnly)
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                } else {
                    HookLogger.e(TAG, "注入超级岛插件失败", e)
                }
            }
            return result
        }
    }

    /**
     * Application 生命周期劫持
     */
    class AppCreateHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val app = chain.thisObject as? Application
            app?.let { instance?.initializeSystemEnvironment(it) }
            return chain.proceed()
        }
    }
}
