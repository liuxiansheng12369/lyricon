/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * IslandOuterGlowHooker — 超级岛外发光/超级光效
 * 按老词幕 2.9.78 稳定版（IslandOuterGlowHooker）逐行对照重新移植，仅融合两项适配：
 * 1. 外圈/内圈分开控制（outer→uLightColors 渐变环，inner→uBaseColor 底色）
 * 2. 新版 hyper_widget 插件光效视图 LightBgView（方法名 start/stop，直接持有 mShader）
 *
 * 核心机制（与老词幕一致，用户确认"光效由系统自动生成"）：
 * 1. hook DynamicFeatureConfig.getFEATURE_DYNAMIC_ISLAND_SHADER → 开启时返回 true
 * 2. hook AbsShader.readRawString → 修改 shader 源码，注入 uBaseColor / uUseBaseColor uniform
 * 3. hook AnimationController.onStateChange → 媒体岛出现时主动 startGlowEffect（调系统方法让系统生成光效）
 *    + applyGlowColor + applyGlowRange，350ms 后重试一次兜底
 * 4. hook 光效视图 start/stop（DynamicGlowEffectView.startGlowEffect + LightBgView.start）→ 系统启动时拦截改色
 * 5. 获取 RuntimeShader → setFloatUniform 设置 uLightColors / uBaseColor / uUseBaseColor
 *
 * 关闭时三重控制（解决关不死）：
 * - 拦截 start 调用（关闭时 return null）
 * - setSizeOfGlowArea(0f)
 * - 全黑（uLightColors 全 0 + uBaseColor 黑 + uUseBaseColor=1）
 *
 * 修改：刘先生（酷安）
 */

package com.liuxiansheng.lyricon.hyper.root.island.effects.glow

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.root.HookEntry
import com.liuxiansheng.lyricon.hyper.root.utils.CoverColorHelper
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * 超级岛外发光 Hooker（老词幕 2.9.78 逐行对照移植 + LightBgView 适配 + 外圈/内圈分开控制）。
 */
internal object IslandOuterGlowHooker {
    private const val TAG = "IslandOuterGlow"
    private const val ISLAND_CONTENT_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"

    // HyperIsland 冲突检测：HyperIsland 接管的岛会在 extras 中写入这些标记
    private const val HYPERISLAND_OWNER_KEY = "hyperisland.owner"
    private const val HYPERISLAND_CHANNEL_KEY = "hyperisland_channel_id"
    private const val HYPERISLAND_SOURCE_CHANNEL_KEY = "hyperisland_source_channel"

    private const val FEATURE_CONFIG_CLASS = "miui.systemui.dynamicisland.DynamicFeatureConfig"
    private const val ANIMATION_CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationController"
    private const val ANIMATION_CONTROLLER_CLASS2 =
        "com.android.systemui.statusbar.phone.dynamicisland.animation.DynamicIslandAnimationController"
    private const val GLOW_VIEW_CLASS =
        "miui.systemui.dynamicisland.view.DynamicGlowEffectView"
    /** 新版本（hyper_widget 插件）光效视图：直接持有 mShader，方法名 start/stop */
    private const val LIGHT_BG_VIEW_CLASS = "com.mi.widget.view.LightBgView"
    private const val ABS_SHADER_CLASS = "com.mi.widget.core.AbsShader"
    private const val LIGHT_BG_SHADER_FIELD = "U_LIGHT_COLORS"
    private const val DEFAULT_TEXTURE_BASE_COLOR = "vec3 currentColor = vec3(0.0, 0.5884, 1.0);"
    private const val LIGHT_COLOR_ARRAY_SIZE = 33

    @Volatile
    private var module: XposedModule? = null
    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val hookedShaderClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val defaultShaderColors = WeakHashMap<Class<*>, FloatArray>()
    private val defaultGlowRanges = WeakHashMap<Any, Float>()
    private val activeGlowViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )

    @Volatile
    private var glowEnabled = RootConstants.DEFAULT_HOOK_ISLAND_OUTER_GLOW_ENABLED

    /** 0=跟随封面 1=固定色（与老词幕一致） */
    @Volatile
    private var glowColorMode = RootConstants.ISLAND_OUTER_GLOW_MODE_FOLLOW_COVER

    @Volatile
    private var glowFixedColor = RootConstants.DEFAULT_HOOK_ISLAND_OUTER_GLOW_OUTER_COLOR

    /** 当前生效的光效颜色（根据颜色模式，老词幕原逻辑） */
    private fun getEffectiveColor(): Int {
        return if (glowColorMode == RootConstants.ISLAND_OUTER_GLOW_MODE_FIXED) {
            glowFixedColor
        } else {
            currentCoverColor() ?: glowFixedColor
        }
    }

    /** 从共享调色板取当前封面占优色（与进度条/字体取色同源） */
    private fun currentCoverColor(): Int? {
        return runCatching {
            CoverColorHelper.currentSession()
                ?.let { CoverColorHelper.getCachedColors(useGradient = false, session = it) }
                ?.first?.firstOrNull()
        }.getOrNull()
    }

    // ========== 入口 ==========

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        if (classLoader.javaClass.name.contains("BootClassLoader")) return
        module = xposedModule
        HookLogger.i(TAG, "=== initialize called ===")
        try {
            probeClassLoader(classLoader)
            hookAll(classLoader)
        } catch (e: Throwable) {
            HookLogger.e(TAG, "hookAll failed", e)
        }

        // 动态 ClassLoader 检测：新岛视图挂载时自动补 hook（与老词幕一致，只 hookAll 不直接接管）
        try {
            val onAttachedMethod = View::class.java.getDeclaredMethod("onAttachedToWindow")
            onAttachedMethod.isAccessible = true
            try { xposedModule.deoptimize(onAttachedMethod) } catch (_: Throwable) {}
            xposedModule.hook(onAttachedMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    try {
                        val view = chain.thisObject as? View ?: return result
                        val name = view.javaClass.name
                        if (name.contains("DynamicIsland", ignoreCase = true) ||
                            name.contains("dynamicisland", ignoreCase = true) ||
                            name.contains("LightBg", ignoreCase = true) ||
                            name.contains("GlowEffect", ignoreCase = true) ||
                            name.contains("AbsShader", ignoreCase = true) ||
                            name.contains("BigIsland", ignoreCase = true) ||
                            name.contains("ExpandedView", ignoreCase = true)
                        ) {
                            val cl = view.javaClass.classLoader
                            HookLogger.i(TAG, "onAttachedToWindow detected: $name")
                            if (cl != null) hookAll(cl)
                        }
                    } catch (_: Throwable) {}
                    return result
                }
            })
        } catch (e: Throwable) {
            HookLogger.w(TAG, "Hook onAttachedToWindow failed: ${e.message}")
        }

        // 启动时主动读配置，防止默认开启导致关闭状态重启后死灰复燃
        onConfigChanged()
        HookLogger.i(TAG, "=== initialize done ===")
    }

    /** 配置变更（总开关关闭时整体失效） */
    fun onConfigChanged() {
        val prefs = runCatching { HookEntry.instance?.prefs }.getOrNull() ?: return
        glowEnabled = prefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_OUTER_GLOW_ENABLED,
            RootConstants.DEFAULT_HOOK_ISLAND_OUTER_GLOW_ENABLED
        )
        glowColorMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_MODE, glowColorMode)
        glowFixedColor = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_COLOR,
            RootConstants.DEFAULT_HOOK_ISLAND_OUTER_GLOW_OUTER_COLOR
        )
        HookLogger.i(
            TAG,
            "Config changed: glowEnabled=$glowEnabled, colorMode=$glowColorMode, fixedColor=#${Integer.toHexString(glowFixedColor)}"
        )
        // 与老词幕一致：开启时主动 startGlowEffect（调系统方法让系统生成光效）+ 改色 + 范围
        synchronized(activeGlowViews) {
            for (view in activeGlowViews) {
                if (glowEnabled) {
                    startGlowEffect(view)
                    applyGlowColor(view)
                    applyGlowRange(view)
                } else {
                    stopGlowEffect(view)
                    forceGlowAreaZero(view)
                }
            }
        }
    }

    private fun probeClassLoader(cl: ClassLoader) {
        val classes = listOf(
            FEATURE_CONFIG_CLASS,
            ANIMATION_CONTROLLER_CLASS,
            ANIMATION_CONTROLLER_CLASS2,
            GLOW_VIEW_CLASS,
            ABS_SHADER_CLASS,
            LIGHT_BG_VIEW_CLASS
        )
        for (cls in classes) {
            try {
                cl.loadClass(cls)
                HookLogger.i(TAG, "  CL can load: $cls")
            } catch (e: Throwable) {
                HookLogger.d(TAG, "  CL CANNOT load: $cls (${e.javaClass.simpleName})")
            }
        }
    }

    // ========== Hook 安装（与老词幕一致：分开 if，绝不短路） ==========

    private fun hookAll(classLoader: ClassLoader) {
        val mod = module ?: return
        var hookedAny = false
        synchronized(hookedClassLoaders) {
            if (hookedClassLoaders.add(classLoader)) {
                if (hookFeatureConfig(mod, classLoader)) hookedAny = true
                if (hookAnimationController(mod, classLoader)) hookedAny = true
                if (hookGlowView(mod, classLoader)) hookedAny = true
            }
        }
        synchronized(hookedShaderClassLoaders) {
            if (hookedShaderClassLoaders.add(classLoader)) {
                if (hookShaderSource(mod, classLoader)) hookedAny = true
            }
        }
        if (hookedAny) HookLogger.i(TAG, "hookAll: installed main+shader hooks")
    }

    private fun hookFeatureConfig(module: XposedModule, classLoader: ClassLoader): Boolean {
        return try {
            val clazz = classLoader.loadClass(FEATURE_CONFIG_CLASS)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "getFEATURE_DYNAMIC_ISLAND_SHADER" && it.parameterCount == 0
            }
            if (method == null) {
                HookLogger.i(TAG, "  FeatureConfig: method not found")
                return false
            }
            method.isAccessible = true
            try { module.deoptimize(method) } catch (_: Throwable) {}
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    return if (glowEnabled) true else chain.proceed()
                }
            })
            HookLogger.i(TAG, "  [OK] Hooked DynamicFeatureConfig.getFEATURE_DYNAMIC_ISLAND_SHADER")
            true
        } catch (e: ClassNotFoundException) {
            HookLogger.i(TAG, "  FeatureConfig class not found: ${e.message}")
            false
        } catch (e: Throwable) {
            HookLogger.i(TAG, "  Hook feature config failed: ${e.message}")
            false
        }
    }

    private fun hookShaderSource(module: XposedModule, classLoader: ClassLoader): Boolean {
        return try {
            val clazz = classLoader.loadClass(ABS_SHADER_CLASS)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "readRawString" && it.parameterCount == 1
            } ?: run {
                HookLogger.i(TAG, "  AbsShader: readRawString not found")
                return false
            }
            method.isAccessible = true
            try { module.deoptimize(method) } catch (_: Throwable) {}
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val source = result as? String ?: return result
                    val hasLightColors = source.contains("uniform vec3 uLightColors[11]")
                    val hasBaseAnchor = source.contains(DEFAULT_TEXTURE_BASE_COLOR)
                    if (!hasLightColors || !hasBaseAnchor) {
                        HookLogger.i(
                            TAG,
                            "readRawString NOT matched: len=${source.length}, hasLightColors=$hasLightColors, hasBaseAnchor=$hasBaseAnchor"
                        )
                        return source
                    }
                    val modified = source
                        .replaceFirst(
                            "uniform vec2 uResolution;",
                            "uniform vec2 uResolution;\nuniform vec3 uBaseColor;\nuniform float uUseBaseColor;"
                        )
                        .replaceFirst(
                            DEFAULT_TEXTURE_BASE_COLOR,
                            "vec3 currentColor = mix(vec3(0.0, 0.5884, 1.0), uBaseColor, uUseBaseColor);"
                        )
                    HookLogger.i(
                        TAG,
                        "readRawString INJECTED: len=${modified.length}, hasBaseDecl=${modified.contains("uBaseColor")}"
                    )
                    return modified
                }
            })
            HookLogger.i(TAG, "  [OK] Hooked AbsShader.readRawString for shader source modification")
            true
        } catch (e: ClassNotFoundException) {
            HookLogger.i(TAG, "  AbsShader class not found: ${e.message}")
            false
        } catch (e: Throwable) {
            HookLogger.i(TAG, "  Hook shader source failed: ${e.message}")
            false
        }
    }

    private fun hookAnimationController(module: XposedModule, classLoader: ClassLoader): Boolean {
        var success = false
        for (className in listOf(ANIMATION_CONTROLLER_CLASS, ANIMATION_CONTROLLER_CLASS2)) {
            try {
                val clazz = classLoader.loadClass(className)
                val methods = clazz.declaredMethods.filter {
                    it.name == "onStateChange" && it.parameterCount >= 1
                }
                if (methods.isEmpty()) {
                    HookLogger.i(TAG, "  AnimationController[$className]: onStateChange not found")
                    continue
                }
                methods.forEach { method ->
                    method.isAccessible = true
                    try { module.deoptimize(method) } catch (_: Throwable) {}
                    module.hook(method).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val stateObj = chain.args.getOrNull(0)
                            val stateName = stateObj?.javaClass?.simpleName ?: "null"
                            val result = chain.proceed()
                            try {
                                // 同时获取大岛和小岛的 glowView，两个都应用光效（老词幕原逻辑）
                                val bigView = invokeNoArg(stateObj ?: Any(), "getBigIslandView")
                                val expandView = invokeNoArg(stateObj ?: Any(), "getExpandedView")
                                val views = listOfNotNull(bigView, expandView).distinct()
                                views.forEach { glowView ->
                                    val hyperExtras = extractExtrasFromView(glowView)
                                    if (hasHyperIslandMark(hyperExtras)) {
                                        synchronized(activeGlowViews) { activeGlowViews.remove(glowView) }
                                        return@forEach
                                    }
                                    val isMedia = isMediaIslandView(glowView)
                                    if (isMedia) {
                                        synchronized(activeGlowViews) { activeGlowViews.add(glowView) }
                                        HookLogger.i(
                                            TAG,
                                            "onStateChange: state=$stateName, view=${glowView.javaClass.simpleName}, MEDIA, glowEnabled=$glowEnabled"
                                        )
                                        if (glowEnabled) {
                                            // 关键：主动调系统 startGlowEffect 让系统生成光效（老词幕核心）
                                            startGlowEffect(glowView)
                                            applyGlowColor(glowView)
                                            applyGlowRange(glowView)
                                            (glowView as? View)?.postDelayed({
                                                val still = synchronized(activeGlowViews) { activeGlowViews.contains(glowView) }
                                                if (glowEnabled && still) {
                                                    startGlowEffect(glowView)
                                                    applyGlowColor(glowView)
                                                    applyGlowRange(glowView)
                                                }
                                            }, 350L)
                                        } else {
                                            stopGlowEffect(glowView)
                                            forceGlowAreaZero(glowView)
                                        }
                                    } else {
                                        synchronized(activeGlowViews) { activeGlowViews.remove(glowView) }
                                        restoreDefaultGlowColor(glowView)
                                    }
                                }
                            } catch (e: Throwable) {
                                HookLogger.i(TAG, "onStateChange post-process failed: ${e.message}")
                            }
                            return result
                        }
                    })
                }
                HookLogger.i(TAG, "  [OK] Hooked AnimationController.onStateChange on $className (${methods.size} overloads)")
                success = true
            } catch (e: ClassNotFoundException) {
                HookLogger.i(TAG, "  AnimationController class not found: $className")
            } catch (e: Throwable) {
                HookLogger.i(TAG, "  Hook animation controller failed for $className: ${e.message}")
            }
        }
        return success
    }

    private fun hookGlowView(module: XposedModule, classLoader: ClassLoader): Boolean {
        var hookedAny = false
        // 老词幕原版：DynamicGlowEffectView（startGlowEffect/stopGlowEffect）
        // 新版适配：LightBgView（start/stop）——新插件光效视图直接持有 mShader
        for (className in listOf(GLOW_VIEW_CLASS, LIGHT_BG_VIEW_CLASS)) {
            try {
                val clazz = classLoader.loadClass(className)
                val methods = clazz.declaredMethods.filter {
                    it.parameterCount == 0 && (
                        it.name == "startGlowEffect" ||
                        it.name == "startGlowEffect\$miui_dynamicisland_release" ||
                        it.name == "stopGlowEffect" ||
                        it.name == "stopGlowEffect\$miui_dynamicisland_release" ||
                        it.name == "start" ||
                        it.name == "stop"
                    )
                }
                if (methods.isEmpty()) {
                    HookLogger.i(TAG, "  GlowView[$className]: no start/stop methods found")
                    continue
                }
                methods.forEach { method ->
                    method.isAccessible = true
                    try { module.deoptimize(method) } catch (_: Throwable) {}
                    module.hook(method).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val glowView = chain.thisObject
                            // 冲突避让：HyperIsland 已接管的岛，直接 passthrough 不干预
                            val hyperExtras = extractExtrasFromView(glowView)
                            if (hasHyperIslandMark(hyperExtras)) {
                                return chain.proceed()
                            }
                            val isStart = method.name.startsWith("startGlowEffect") || method.name == "start"
                            val isStop = method.name.startsWith("stopGlowEffect") || method.name == "stop"
                            if (isStart) {
                                // LightBgView 是光效专用视图（新插件），不做 media 过滤；老视图严格 media
                                val isLightBg = glowView.javaClass.name.contains("LightBg", ignoreCase = true)
                                val isMedia = isLightBg || isMediaIslandView(glowView)
                                if (!isMedia) {
                                    return chain.proceed()
                                }
                                if (glowEnabled) {
                                    synchronized(activeGlowViews) { activeGlowViews.add(glowView) }
                                    HookLogger.i(TAG, "${method.name}: media=true, applying color+range")
                                    applyGlowColor(glowView)
                                    applyGlowRange(glowView)
                                } else {
                                    HookLogger.i(TAG, "${method.name}: BLOCKED (glow disabled)")
                                    return null
                                }
                            } else if (isStop) {
                                synchronized(activeGlowViews) { activeGlowViews.remove(glowView) }
                            }
                            return chain.proceed()
                        }
                    })
                }
                HookLogger.i(TAG, "  [OK] Hooked $className glow methods (${methods.size})")
                hookedAny = true
            } catch (e: ClassNotFoundException) {
                HookLogger.i(TAG, "  GlowView class not found: $className")
            } catch (e: Throwable) {
                HookLogger.i(TAG, "  Hook glow view failed for $className: ${e.message}")
            }
        }
        return hookedAny
    }

    // ========== 光效控制（老词幕原样） ==========

    private fun startGlowEffect(glowView: Any) {
        invokeGlowEffectMethod(glowView, "startGlowEffect")
        // LightBgView 适配：新版光效视图方法名是 start（老方法没找到时补调）
        if (glowView.javaClass.name.contains("LightBg", ignoreCase = true)) {
            invokeGlowEffectMethod(glowView, "start")
        }
    }

    private fun stopGlowEffect(glowView: Any) {
        invokeGlowEffectMethod(glowView, "stopGlowEffect")
        if (glowView.javaClass.name.contains("LightBg", ignoreCase = true)) {
            invokeGlowEffectMethod(glowView, "stop")
        }
    }

    private fun forceGlowAreaZero(glowView: Any) {
        val container = invokeNoArg(glowView, "getMContainer")
        if (container == null) {
            // LightBgView 自身有 setSizeOfGlowArea，直接用 view 本身
            val appliedSelf = invokeFloatSetter(glowView, "setSizeOfGlowArea", 0f)
            setGlowColorBlack(glowView)
            return
        }
        invokeFloatSetter(container, "setSizeOfGlowArea", 0f)
        setGlowColorBlack(glowView)
    }

    /** 设置光晕颜色为全黑，彻底关闭岛内光晕 */
    private fun setGlowColorBlack(glowView: Any) {
        val shader = resolveLightBgShader(glowView) ?: return
        val runtimeShader = resolveRuntimeShader(shader) ?: return
        val shaderClass = shader.javaClass
        val base = obtainDefaultLightColors(shaderClass) ?: readInstanceLightColors(shader)
        val size = base?.size ?: LIGHT_COLOR_ARRAY_SIZE
        val blackColors = FloatArray(size) { 0f }
        val colorsApplied = setRuntimeShaderLightColors(runtimeShader, blackColors)
        val baseApplied = setRuntimeShaderColor(runtimeShader, "uBaseColor", Color.BLACK)
        val useApplied = setRuntimeShaderFloat(runtimeShader, "uUseBaseColor", 1f)
        HookLogger.i(TAG, "setGlowColorBlack: colors=$colorsApplied, base=$baseApplied, use=$useApplied")
    }

    private fun applyGlowRange(glowView: Any) {
        val container = invokeNoArg(glowView, "getMContainer") ?: return
        val defaultRange = synchronized(defaultGlowRanges) {
            defaultGlowRanges.getOrPut(container) {
                (invokeNoArg(container, "getSizeOfGlowArea") as? Number)?.toFloat() ?: Float.NaN
            }
        }
        if (!defaultRange.isNaN()) {
            val applied = invokeFloatSetter(container, "setSizeOfGlowArea", defaultRange * 0.8f)
            HookLogger.i(TAG, "applyGlowRange: default=$defaultRange, applied=${defaultRange * 0.8f}, ok=$applied")
        } else {
            HookLogger.i(TAG, "applyGlowRange: defaultRange is NaN")
        }
    }

    // ========== 颜色注入（外圈→uLightColors，内圈→uBaseColor） ==========

    private fun applyGlowColor(glowView: Any) {
        val shader = resolveLightBgShader(glowView)
        if (shader == null) {
            HookLogger.i(TAG, "applyGlowColor: resolveLightBgShader returned null")
            return
        }
        val runtimeShader = resolveRuntimeShader(shader)
        if (runtimeShader == null) {
            HookLogger.i(TAG, "applyGlowColor: resolveRuntimeShader returned null")
            return
        }
        val shaderClass = shader.javaClass
        val base = obtainDefaultLightColors(shaderClass) ?: readInstanceLightColors(shader)
        if (base == null) {
            HookLogger.i(TAG, "applyGlowColor: cannot obtain default light colors")
            return
        }
        cacheDefaultLightColors(shaderClass, base)

        // 老词幕单色注入：渐变环 + 底色用同一颜色
        val color = getEffectiveColor()
        val targetColors = rebuildLightShaderArray(base, color)
        val colorsApplied = setRuntimeShaderLightColors(runtimeShader, targetColors)
        val baseApplied = setRuntimeShaderColor(runtimeShader, "uBaseColor", color)
        val useApplied = setRuntimeShaderFloat(runtimeShader, "uUseBaseColor", 1f)
        HookLogger.i(
            TAG,
            "applyGlowColor: color=#${Integer.toHexString(color)}, mode=$glowColorMode, " +
                "shader=${shader.javaClass.simpleName}, setLight=$colorsApplied, setBase=$baseApplied, setUse=$useApplied"
        )
    }

    private fun restoreDefaultGlowColor(glowView: Any) {
        val shader = resolveLightBgShader(glowView) ?: return
        val runtimeShader = resolveRuntimeShader(shader) ?: return
        val shaderClass = shader.javaClass
        val colors = obtainDefaultLightColors(shaderClass) ?: readInstanceLightColors(shader) ?: return
        cacheDefaultLightColors(shaderClass, colors)
        setRuntimeShaderLightColors(runtimeShader, colors)
        setRuntimeShaderFloat(runtimeShader, "uUseBaseColor", 0f)
        HookLogger.i(TAG, "restoreDefaultGlowColor: done")
    }

    // ========== Shader 反射（保留 LightBgView 直读增强，链路 getMContainer→getMShader + mShader 字段） ==========

    private fun resolveLightBgShader(glowView: Any): Any? {
        // LightBgView 直接持有 mShader 字段
        readFieldValue(glowView, "mShader")?.let { return it }
        invokeNoArg(glowView, "getMShader")?.let { return it }
        // 老链路：getMContainer → getMShader
        val container = invokeNoArg(glowView, "getMContainer") ?: return null
        invokeNoArg(container, "getMShader\$hyper_widget_1_0_8_pluginRelease")?.let { return it }
        invokeNoArg(container, "getMShader\$hyper_widget_1_0_11_pluginRelease")?.let { return it }
        container.javaClass.declaredMethods.forEach { method ->
            if (method.parameterCount == 0 && method.name.contains("getMShader")) {
                runCatching {
                    method.isAccessible = true
                    method.invoke(container)
                }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun resolveRuntimeShader(shader: Any): Any? {
        invokeNoArg(shader, "getMTextureShader")?.let { return it }
        shader.javaClass.declaredMethods.forEach { method ->
            if (method.parameterCount == 0 && method.name.contains("getMTextureShader")) {
                runCatching {
                    method.isAccessible = true
                    method.invoke(shader)
                }.getOrNull()?.let { return it }
            }
        }
        invokeNoArg(shader, "getRuntimeShader")?.let { return it }
        invokeNoArg(shader, "getMRuntimeShader")?.let { return it }
        readFieldValue(shader, "mRuntimeShader")?.let { return it }
        readFieldValue(shader, "mShader")?.let { return it }
        return null
    }

    private fun obtainDefaultLightColors(shaderClass: Class<*>): FloatArray? {
        synchronized(defaultShaderColors) {
            defaultShaderColors[shaderClass]?.let { return it.copyOf() }
        }
        return readStaticLightColors(findLightColorField(shaderClass, preferStatic = true))
            ?.also { cacheDefaultLightColors(shaderClass, it) }
    }

    private fun cacheDefaultLightColors(shaderClass: Class<*>, colors: FloatArray) {
        synchronized(defaultShaderColors) {
            if (!defaultShaderColors.containsKey(shaderClass)) {
                defaultShaderColors[shaderClass] = colors.copyOf()
            }
        }
    }

    private fun readStaticLightColors(field: Field?): FloatArray? {
        return runCatching { (field?.get(null) as? FloatArray)?.copyOf() }.getOrNull()
    }

    private fun readInstanceLightColors(shader: Any): FloatArray? {
        val field = findLightColorField(shader.javaClass, preferStatic = false) ?: return null
        return runCatching { (field.get(shader) as? FloatArray)?.copyOf() }.getOrNull()
    }

    private fun findLightColorField(clazz: Class<*>, preferStatic: Boolean): Field? {
        runCatching {
            val exact = clazz.getDeclaredField(LIGHT_BG_SHADER_FIELD)
            exact.isAccessible = true
            return exact
        }
        val fields = clazz.declaredFields.filter {
            it.type == FloatArray::class.java && it.name.contains("LIGHT", ignoreCase = true)
        }
        val preferred = fields.firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers) == preferStatic }
        return (preferred ?: fields.firstOrNull())?.apply { isAccessible = true }
    }

    private fun setRuntimeShaderLightColors(runtimeShader: Any, colors: FloatArray): Boolean {
        val method = findSetFloatUniformArrayMethod(runtimeShader) ?: return false
        return runCatching {
            method.isAccessible = true
            method.invoke(runtimeShader, "uLightColors", colors)
            true
        }.getOrDefault(false)
    }

    private fun setRuntimeShaderColor(runtimeShader: Any, uniform: String, color: Int): Boolean {
        val method = findSetFloatUniformArrayMethod(runtimeShader) ?: return false
        return runCatching {
            method.isAccessible = true
            method.invoke(
                runtimeShader,
                uniform,
                floatArrayOf(
                    Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f
                )
            )
            true
        }.getOrDefault(false)
    }

    private fun setRuntimeShaderFloat(runtimeShader: Any, uniform: String, value: Float): Boolean {
        val method = findSetFloatUniformArrayMethod(runtimeShader) ?: return false
        return runCatching {
            method.isAccessible = true
            method.invoke(runtimeShader, uniform, floatArrayOf(value))
            true
        }.getOrDefault(false)
    }

    private fun findSetFloatUniformArrayMethod(runtimeShader: Any): Method? {
        return (runtimeShader.javaClass.methods + runtimeShader.javaClass.declaredMethods).firstOrNull {
            it.name == "setFloatUniform" &&
                it.parameterCount == 2 &&
                it.parameterTypes.getOrNull(0) == String::class.java &&
                it.parameterTypes.getOrNull(1)?.isArray == true
        }
    }

    // ========== 颜色数组生成（老词幕原样） ==========

    private fun rebuildLightShaderArray(base: FloatArray, argb: Int): FloatArray {
        val template = normalizeTemplatePalette(base)
        val output = FloatArray(template.size)
        val seedHsv = FloatArray(3)
        Color.colorToHSV(argb, seedHsv)
        val anchorHsv = extractStopHsv(template, 2)
        val ranges = calcTemplateSvMinMax(template)
        val stopCount = template.size / 3
        for (i in 0 until stopCount) {
            val tplHsv = extractStopHsv(template, i)
            val hue = wrapHue(seedHsv[0] + shortestHueDelta(anchorHsv[0], tplHsv[0]) * 0.45f)
            val sat = clamp01(
                seedHsv[1] *
                    (0.72f + 0.38f * normalize01(tplHsv[1], ranges[0], ranges[1])) *
                    (tplHsv[1] / maxOf(anchorHsv[1], 0.01f))
            )
            val value = clamp01(
                seedHsv[2] *
                    (0.62f + 0.48f * normalize01(tplHsv[2], ranges[2], ranges[3])) *
                    (tplHsv[2] / maxOf(anchorHsv[2], 0.01f))
            )
            val color = Color.HSVToColor(Color.alpha(argb), floatArrayOf(hue, sat, value))
            val baseIndex = i * 3
            output[baseIndex] = Color.red(color) / 255f
            output[baseIndex + 1] = Color.green(color) / 255f
            output[baseIndex + 2] = Color.blue(color) / 255f
        }
        return output
    }

    private fun normalizeTemplatePalette(base: FloatArray): FloatArray {
        if (base.size >= LIGHT_COLOR_ARRAY_SIZE) return base.copyOf(LIGHT_COLOR_ARRAY_SIZE)
        return floatArrayOf(
            0.502f, 0.525f, 1.0f,
            1.0f, 0.827f, 0.702f,
            1.0f, 0.525f, 0.208f,
            0.518f, 0.494f, 1.0f,
            0.071f, 0.412f, 0.949f,
            0.502f, 0.525f, 1.0f,
            1.0f, 0.827f, 0.702f,
            1.0f, 0.525f, 0.208f,
            0.518f, 0.494f, 1.0f,
            0.071f, 0.412f, 0.949f,
            1.0f, 0.525f, 0.208f
        )
    }

    private fun extractStopHsv(rgb33: FloatArray, stopIndex: Int): FloatArray {
        val idx = stopIndex * 3
        val hsv = FloatArray(3)
        Color.RGBToHSV(
            (clamp01(rgb33[idx]) * 255f).toInt(),
            (clamp01(rgb33[idx + 1]) * 255f).toInt(),
            (clamp01(rgb33[idx + 2]) * 255f).toInt(),
            hsv
        )
        return hsv
    }

    private fun calcTemplateSvMinMax(rgb33: FloatArray): FloatArray {
        var minS = 1f
        var maxS = 0f
        var minV = 1f
        var maxV = 0f
        val stopCount = rgb33.size / 3
        for (i in 0 until stopCount) {
            val hsv = extractStopHsv(rgb33, i)
            minS = minOf(minS, hsv[1])
            maxS = maxOf(maxS, hsv[1])
            minV = minOf(minV, hsv[2])
            maxV = maxOf(maxV, hsv[2])
        }
        return floatArrayOf(minS, maxS, minV, maxV)
    }

    private fun normalize01(x: Float, min: Float, max: Float): Float {
        val diff = max - min
        if (diff <= 1e-6f) return 0.5f
        return clamp01((x - min) / diff)
    }

    private fun shortestHueDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    private fun wrapHue(value: Float): Float {
        var hue = value % 360f
        if (hue < 0f) hue += 360f
        return hue
    }

    private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

    // ========== 媒体岛判断（老词幕原样） ==========

    /**
     * 判断给定视图是否属于媒体通知岛。
     * 通过向上/向下查找 DynamicIslandContentView，调用 getCurrentIslandData() 检查 extras。
     * 判定标准与 SuperIslandLyricHooker.extractMediaIslandInfo 一致：
     *   1. extras 含非空 miui.pkg.name
     *   2. extras 含至少一个媒体相关字段
     */
    private fun isMediaIslandView(view: Any): Boolean {
        val contentView = findIslandContentView(view) ?: return false
        return isMediaIslandContentView(contentView)
    }

    private fun findIslandContentView(view: Any): ViewGroup? {
        val v = view as? View ?: return null
        if (isContentViewClass(v)) return v as? ViewGroup
        var parent = v.parent as? View
        while (parent != null) {
            if (isContentViewClass(parent)) return parent as? ViewGroup
            parent = parent.parent as? View
        }
        val queue = java.util.LinkedList<View>()
        queue.add(v)
        while (queue.isNotEmpty()) {
            val child = queue.poll() ?: continue
            if (isContentViewClass(child)) return child as? ViewGroup
            if (child is ViewGroup) {
                for (i in 0 until child.childCount) {
                    queue.add(child.getChildAt(i))
                }
            }
        }
        return null
    }

    private fun isContentViewClass(view: View): Boolean {
        if (view.javaClass.name == ISLAND_CONTENT_CLASS) return true
        return view.javaClass.methods.any { it.name == "getCurrentIslandData" && it.parameterTypes.isEmpty() }
    }

    private fun isMediaIslandContentView(contentView: ViewGroup): Boolean {
        val data = runCatching {
            contentView.javaClass.methods.find { it.name == "getCurrentIslandData" && it.parameterTypes.isEmpty() }
                ?.invoke(contentView)
        }.getOrNull() ?: return false

        val extras = runCatching {
            data.javaClass.methods.find { it.name == "getExtras" && it.parameterTypes.isEmpty() }
                ?.invoke(data) as? Bundle
        }.getOrNull() ?: return false

        val pkgName = extras.getString("miui.pkg.name").orEmpty()
        if (pkgName.isEmpty()) return false

        val hasMediaExtra = extras.containsKey("miui.media.title") ||
            extras.containsKey("miui.media.artist") ||
            extras.containsKey("android.media.metadata.TITLE") ||
            extras.containsKey("miui.media.playstate") ||
            extras.containsKey("miui.pending.intent")
        return hasMediaExtra
    }

    // ========== HyperIsland 冲突避让 ==========

    private fun hasHyperIslandMark(extras: Bundle?): Boolean {
        if (extras == null) return false
        return extras.containsKey(HYPERISLAND_OWNER_KEY) ||
            extras.containsKey(HYPERISLAND_CHANNEL_KEY) ||
            extras.containsKey(HYPERISLAND_SOURCE_CHANNEL_KEY)
    }

    /** 从任意岛视图提取当前岛数据的 extras */
    private fun extractExtrasFromView(view: Any): Bundle? {
        val data = invokeNoArg(view, "getCurrentIslandData") ?: return null
        return runCatching {
            data.javaClass.methods.find { it.name == "getExtras" && it.parameterTypes.isEmpty() }
                ?.invoke(data) as? Bundle
        }.getOrNull()
    }

    // ========== 通用反射工具（老词幕原样 + LightBg 兜底） ==========

    private fun invokeGlowEffectMethod(view: Any?, baseMethodName: String) {
        if (view == null) return
        findNoArgMethod(view.javaClass, baseMethodName)?.let {
            runCatching {
                it.isAccessible = true
                it.invoke(view)
            }
            return
        }
        findNoArgMethod(view.javaClass, "$baseMethodName\$miui_dynamicisland_release")?.let {
            runCatching {
                it.isAccessible = true
                it.invoke(view)
            }
            return
        }
        invokeNoArg(view, "getGlowEffectView")?.let {
            invokeGlowEffectMethod(it, baseMethodName)
            return
        }
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? {
        return runCatching {
            val method = findNoArgMethod(target.javaClass, methodName) ?: return null
            method.isAccessible = true
            method.invoke(target)
        }.getOrNull()
    }

    private fun invokeFloatSetter(target: Any, methodName: String, value: Float): Boolean {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                it.name == methodName &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == Float::class.javaPrimitiveType
            }
            if (method != null) {
                return runCatching {
                    method.isAccessible = true
                    method.invoke(target, value)
                    true
                }.getOrDefault(false)
            }
            current = current.superclass
        }
        return false
    }

    private fun findNoArgMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun readFieldValue(instance: Any, fieldName: String): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(instance)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
