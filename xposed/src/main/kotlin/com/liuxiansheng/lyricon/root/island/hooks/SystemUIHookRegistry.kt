/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.hooks

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.island.effects.album.IslandAlbumCoverStyleHooker
import com.liuxiansheng.lyricon.hyper.root.island.effects.blur.IslandBackgroundBlurHooker
import com.liuxiansheng.lyricon.hyper.root.island.effects.color.IslandMusicWaveColorHooker
import com.liuxiansheng.lyricon.hyper.root.island.effects.glow.IslandProgressGlowHooker
import com.liuxiansheng.lyricon.hyper.root.island.effects.glow.IslandOuterGlowHooker
import com.liuxiansheng.lyricon.hyper.root.utils.DynamicFinder
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandBackgroundHook
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandBlurHook
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandOutlineHook
import io.github.libxposed.api.XposedModule

/**
 * SystemUI hook registry.
 */
object SystemUIHookRegistry {
    private const val TAG = "SystemUIHookRegistry"

    lateinit var module: XposedModule

    private val hookedClassLoaders = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())
    )

    @Volatile
    var isHookedSuccess = false
        private set

    /** 配置变更 → 刷新上游岛外观（材质/模糊/背景/描边），否则四种材质永远用首载配置。 */
    @Volatile
    private var upstreamPrefRegistered = false

    private val upstreamPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null) return@OnSharedPreferenceChangeListener
        val isUpstreamKey = key.startsWith("pref_island_material") ||
            key.startsWith("pref_island_blur") ||
            key.startsWith("pref_island_bg") ||
            key.startsWith("pref_island_glass") ||
            key == "pref_always_show_island_outline" ||
            key == "pref_always_show_focus_outline" ||
            key.startsWith("key_hook_island_outer_glow")
        if (!isUpstreamKey) return@OnSharedPreferenceChangeListener
        Handler(Looper.getMainLooper()).post {
            runCatching {
                IslandBackgroundHook.onConfigChanged()
                IslandBlurHook.onConfigChanged()
                IslandOutlineHook.onConfigChanged()
                IslandOuterGlowHooker.onConfigChanged()
                HookLogger.i(TAG, "上游岛外观配置已刷新: key=$key")
            }.onFailure { e ->
                HookLogger.e(TAG, "上游岛外观配置刷新失败", e)
            }
        }
    }

    fun hook(xposedModule: XposedModule, cl: ClassLoader, lyricsOnly: Boolean = false) {
        if (cl.javaClass.name.contains("BootClassLoader")) return

        val islandPkg = "miui.systemui.dynamicisland"

        runCatching {
            cl.loadClass("$islandPkg.template.IslandTemplateBuilder")
        }.getOrNull() ?: DynamicFinder.findClassByConstantString(
            cl,
            "$islandPkg.template",
            "IslandTemplateBuilder"
        ) ?: return

        if (!hookedClassLoaders.add(cl)) return
        module = xposedModule

        if (!upstreamPrefRegistered) {
            upstreamPrefRegistered = true
            runCatching {
                HookEntry.instance?.prefs
                    ?.registerOnSharedPreferenceChangeListener(upstreamPrefListener)
            }
        }

        try {
            IslandTextHooker.hook(module, cl)
            // 毛玻璃与超级岛歌词同路径注册（测试机走 lyricsOnly 初始化，毛玻璃必须在此生效）
            // V3 自研简化清除 → 上游 HyperIsland 完整遮罩对抗链（六层防护，根治 Android 17 黑罩）
            IslandBackgroundHook.hook(module, cl)
            IslandBlurHook.hook(module, cl)
            IslandOutlineHook.hook(module, cl)
            // 上游接管毛玻璃：退役 V3 旧 IslandBackgroundBlurHooker（否则两套同挂互相覆盖）
            IslandBackgroundBlurHooker.supersededByUpstream = true
            if (!lyricsOnly) {
                IslandProgressGlowHooker.hook(module, cl)
                IslandOuterGlowHooker.hook(module, cl)
                IslandMusicWaveColorHooker.hook(module, cl)
                IslandAlbumCoverStyleHooker.hook(module, cl)
            }

            isHookedSuccess = true
            HookLogger.i(
                TAG,
                if (lyricsOnly) "超级岛歌词 Hook 已初始化" else "超级岛 Hook 已初始化"
            )
        } catch (e: ClassNotFoundException) {
            HookLogger.w(TAG, "跳过不支持的超级岛插件: reason=${e.message}")
        } catch (e: Exception) {
            HookLogger.e(TAG, "注入超级岛插件失败", e)
        }
    }
}
