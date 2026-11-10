/*
 * Lyricon V3 - Copyright (C) 2026 liuxiansheng
 * Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root.island.effects.blur.softglass

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import com.liuxiansheng.lyricon.hyper.root.island.upstream.LambdaHooker
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Per-View renderer for Xiaomi's native Bionics soft-glass material.
 *
 * Material ownership and rendering ownership are intentionally separate. A View can be prepared
 * before its first frame, but only a rendering lease is allowed to keep the shared window/pass
 * sampler alive. Xiaomi's close requests are rewritten at their source while a lease exists.
 */
internal object SoftGlassController {
    private const val TAG = "HyperIsland[SoftGlass]"

    private inline fun log(msg: () -> String) { HookLogger.i(TAG, msg()) }
    private fun logWarn(msg: String) { HookLogger.w(TAG, msg) }

    private const val WINDOW_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private const val BACKGROUND_VIEW_CLASS =
        "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
    private const val SYSTEM_SMALL_BLUR_RADIUS = 50
    private const val SYSTEM_BIG_BLUR_RADIUS = 500
    private val managedViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )
    private val renderingViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )
    private val stockStates = Collections.synchronizedMap(WeakHashMap<View, StockViewState>())
    private val detachListeners = Collections.synchronizedMap(
        WeakHashMap<View, View.OnAttachStateChangeListener>(),
    )
    private val appliedConfigs = Collections.synchronizedMap(WeakHashMap<View, SoftGlassConfig>())
    private val appliedWindowRadii = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val retainedWindowBlurRoots = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )
    private val retainedPassBlurRoots = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )
    private val hookedStyleClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private val hookedCompatClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private val hookedWindowClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    // 3.0.713（收缩后玻璃消失修复）：每 500ms 重新拿系统 42 参数注入所有渲染中的玻璃视图，
    // 并保持窗口/传递模糊采样器。收缩动画结束时系统放弃 passBlur sampler、重置材质
    // （日志实锤 [passBlur] detachFromView: abandoned LegacySurfaceTexture），周期刷新兜住重置。
    @Volatile
    private var refreshRunning = false
    @Volatile
    private var glassToken: Any? = null
    private val refreshHandler = Handler(Looper.getMainLooper())

    private data class RuntimeBinding(
        val contentClass: Class<*>,
        val compatClass: Class<*>,
        val params: FloatArray,
        val setViewMode: Method,
        val clearBlend: Method,
        val isBionicsActive: Method,
    )

    // Keep plugin generations isolated. Weak values are essential: Methods retain their Class.
    private val runtimes = WeakHashMap<Class<*>, java.lang.ref.WeakReference<RuntimeBinding>>()
    private val viewRuntimes = Collections.synchronizedMap(WeakHashMap<View, RuntimeBinding>())
    private val bionicsRuntimeAvailable get() = synchronized(runtimes) {
        runtimes.values.any { it.get() != null }
    }

    private fun runtimeFor(view: View): RuntimeBinding? {
        viewRuntimes[view]?.let { return it }
        var owner: View? = view
        repeat(12) {
            val current = owner ?: return null
            var type: Class<*>? = current.javaClass
            while (type != null) {
                val candidate = type
                synchronized(runtimes) { runtimes[candidate]?.get() }?.let { return it }
                type = candidate.superclass
            }
            owner = current.parent as? View
        }
        return null
    }
    private var diagnosticEvents = 0

    private fun loaderId(loader: ClassLoader?): String =
        "${loader?.javaClass?.simpleName ?: "bootstrap"}@${System.identityHashCode(loader)}"

    private fun diagnose(event: String, view: View) {
        synchronized(this) {
            if (diagnosticEvents >= 160) return
            diagnosticEvents++
        }
        val root = findWindowView(view)
        log { "$TAG trace $event view=${view.javaClass.simpleName}@${System.identityHashCode(view)} " +
                "root=${root?.let(System::identityHashCode)} attached=${view.isAttachedToWindow} " +
                "managed=${managedViews.contains(view)} rendering=${renderingViews.contains(view)} " +
                "window=${root != null && retainedWindowBlurRoots.contains(root)} " +
                "pass=${root != null && retainedPassBlurRoots.contains(root)}" }
    }

    @Synchronized
    fun bindRuntime(module: XposedModule, contentClass: Class<*>, compatClass: Class<*>): Boolean {
        synchronized(runtimes) {
            runtimes.entries.removeAll { it.value.get() == null }
            runtimes[contentClass]?.get()?.let { return it.compatClass === compatClass }
            if (runtimes.size >= 32) return false
        }
        val params = runCatching {
            val field = contentClass.getDeclaredField("EXPANDED_GLASS_TOKEN").apply {
                isAccessible = true
            }
            val token = field.get(null)
            glassToken = token
            val method = findMethod(token.javaClass, "getToBionicsParams")
                ?: return@runCatching null
            (method.invoke(token) as? FloatArray)?.clone()
        }.getOrNull()
        val styleClass = runCatching {
            Class.forName("miui.systemui.util.MiBackgroundStyle", false, contentClass.classLoader)
        }.getOrNull()
        val activeMethod = styleClass?.let {
            findMethod(it, "isBionicsActive", Context::class.java)
        }
        val viewModeMethod = findMethod(
            compatClass,
            "setMiViewBlurModeCompat",
            View::class.java,
            Int::class.javaPrimitiveType!!,
        )
        val clearBlendMethod = findMethod(
            compatClass,
            "clearMiBackgroundBlendColorCompat",
            View::class.java,
        )
        // HyperOS versions cannot be distinguished reliably by Android SDK. These two APIs are
        // the actual OS4 Bionics capability boundary; OS3 must remain on the Gaussian path and
        // must not receive any of the Bionics source-writer hooks below.
        if (params == null || activeMethod == null) {
            log { "$TAG runtime unavailable candidate=${loaderId(contentClass.classLoader)}" }
            return true // OS3 remains on its existing Gaussian path.
        }
        check(params.size >= 42 && viewModeMethod != null && clearBlendMethod != null) {
            "Incomplete Bionics runtime candidate=${loaderId(contentClass.classLoader)} params=${params.size}"
        }
        val candidate = RuntimeBinding(
            contentClass, compatClass, params, viewModeMethod, clearBlendMethod, activeMethod,
        )
        hookSystemWriters(module, checkNotNull(styleClass))
        hookCompatWriters(module, compatClass)
        synchronized(runtimes) {
            runtimes[contentClass] = java.lang.ref.WeakReference(candidate)
        }
        // Source hooks retain only their own validated runtime; foreign copies cannot replace it.
        hookRuntimeLifetime(module, contentClass, candidate)
        log { "$TAG runtime bound bionics=$bionicsRuntimeAvailable " +
                "params=${params.size} owner=${loaderId(contentClass.classLoader)} " +
                "style=${loaderId(activeMethod.declaringClass.classLoader)} " +
                "compat=${loaderId(compatClass.classLoader)}" }
        // 3.0.712（Android 17 系统参数比对）：打印系统原始 42 参数全量，
        // 用于确认 Android 17 上 EXPANDED_GLASS_TOKEN 的默认值是否变化（用户怀疑系统参数变了）。
        log { "$TAG sysGlassParams=${params.joinToString(",") { "%.3f".format(it) }}" }
        startPeriodicRefresh()
        return true
    }

    /** 3.0.713：周期刷新（对齐老词幕 2.9.78 startPeriodicRefresh）。 */
    private fun startPeriodicRefresh() {
        if (refreshRunning) return
        refreshRunning = true
        refreshHandler.post(refreshRunnable)
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            runCatching {
                val token = glassToken ?: return@runCatching
                val getParams = findMethod(token.javaClass, "getToBionicsParams")
                    ?: return@runCatching
                val fresh = (getParams.invoke(token) as? FloatArray)?.clone()
                    ?: return@runCatching
                synchronized(managedViews) {
                    managedViews.toList().forEach { v ->
                        if (!v.isAttachedToWindow) return@forEach
                        val binding = runtimeFor(v) ?: return@forEach
                        val cfg = appliedConfigs[v] ?: return@forEach
                        val setGlass = findMethod(
                            v.javaClass, "setMiGlass", FloatArray::class.java,
                        ) ?: return@forEach
                        // 带当前材质配置重新 customize，保持用户设置 + 跟随系统参数
                        runCatching { setGlass.invoke(v, customizeParams(fresh, cfg)) }
                        runCatching { binding.setViewMode.invoke(null, v, 1) }
                        runCatching { binding.clearBlend.invoke(null, v) }
                        // 3.0.772（柔光挂住修复）：不再要求 renderingViews（租约）——
                        // 系统 setMiViewMaterialType(0) 写回默认后，即使租约被清也能把
                        // 用户材质写回去（500ms 内拉回柔光，兜底"变默认"）。
                        if (!renderingViews.contains(v)) {
                            renderingViews.add(v)
                            ensureWindowBlur(v)
                            ensurePassWindowBlur(v)
                            appliedConfigs[v]?.let { updateWindowRadius(v, it.blurRadius) }
                        } else {
                            // 收缩动画结束时系统放弃采样器，这里重新保持
                            ensureWindowBlur(v)
                            ensurePassWindowBlur(v)
                        }
                        v.invalidate()
                    }
                }
            }
            refreshHandler.postDelayed(this, 500)
        }
    }

    private fun hookRuntimeLifetime(module: XposedModule, contentClass: Class<*>, binding: RuntimeBinding) {
        val method = findMethod(contentClass, "updateBackgroundBg", View::class.java,
            Boolean::class.javaPrimitiveType!!) ?: return
        module.hook(method).intercept(LambdaHooker { chain ->val target = chain.args.getOrNull(0) as? View
            runCatching {
                if (target != null) synchronized(viewRuntimes) {
                    if (viewRuntimes.containsKey(target) || viewRuntimes.size < 256) {
                        viewRuntimes[target] = binding
                    }
                }
            }
            chain.proceed()
        })
    }

    /**
     * Rewrites Xiaomi's shutdown transaction before it reaches the native sampler. Calling the
     * original method with true keeps DynamicIslandWindowView's cache, FPS and native flag in one
     * consistent state; no corrective post-write or private-field mutation is needed.
     */
    fun observeWindowLifecycle(module: XposedModule, windowClass: Class<*>) {
        if (!bionicsRuntimeAvailable) return
        if (!hookedWindowClasses.add(windowClass)) return
        findMethod(
            windowClass,
            "updateWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept(LambdaHooker { chain ->    val root = chain.thisObject as? View ?: return@LambdaHooker chain.proceed()
                val requested = chain.args.getOrNull(0) as? Boolean ?: false
                val keepForSoft = !requested && hasRenderingView(root)
                val result = if (keepForSoft) chain.proceed(arrayOf(true)) else chain.proceed()
                appliedWindowRadii.remove(root)
                if (requested || keepForSoft) {
                    retainedWindowBlurRoots.add(root)
                    currentRenderingConfig(root)?.let { updateWindowRadius(root, it.blurRadius) }
                } else {
                    retainedWindowBlurRoots.remove(root)
                }
                diagnose("window-blur requested=$requested kept=$keepForSoft", root)
                result
            })
        }
        findMethod(
            windowClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept(LambdaHooker { chain ->    val root = chain.thisObject as? View ?: return@LambdaHooker chain.proceed()
                val requested = chain.args.getOrNull(0) as? Boolean ?: false
                val keepForSoft = !requested && hasRenderingView(root)
                val result = if (keepForSoft) chain.proceed(arrayOf(true)) else chain.proceed()
                if (requested || keepForSoft) {
                    retainedPassBlurRoots.add(root)
                } else {
                    retainedPassBlurRoots.remove(root)
                }
                diagnose("pass-blur requested=$requested kept=$keepForSoft", root)
                result
            })
        }
    }

    /** Observe stock cleanup and parameter rewrites without changing its window lifecycle. */
    private fun hookSystemWriters(module: XposedModule, styleClass: Class<*>) {
        if (!hookedStyleClasses.add(styleClass)) return
        findMethod(
            styleClass,
            "setMiViewMaterialType",
            View::class.java,
            Int::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept(LambdaHooker { chain ->    val view = chain.args.getOrNull(0) as? View
                val type = chain.args.getOrNull(1) as? Int
                if (view != null && type != 1 && renderingViews.contains(view)) {
                    // The active slot owns Bionics until its source lifecycle releases the lease.
                    // Do not let a late stock cleanup remove the material from a submitted frame.
                    return@LambdaHooker null
                }
                val result = chain.proceed()
                if (view != null && type != 1) forget(view)
                result
            })
        }
        findMethod(
            styleClass,
            "setMiGlassCompat",
            View::class.java,
            FloatArray::class.java,
        )?.let { method ->
            module.hook(method).intercept(LambdaHooker { chain ->    val view = chain.args.getOrNull(0) as? View
                val config = view?.let(appliedConfigs::get)
                if (view != null && config != null && managedViews.contains(view)) {
                    val source = chain.args.getOrNull(1) as? FloatArray
                    return@LambdaHooker chain.proceed(
                        arrayOf(view, customizeParams(source ?: baseParams(runtimeFor(view)), config)),
                    )
                }
                chain.proceed()
            })
        }
    }

    fun apply(view: View, config: SoftGlassConfig): Boolean = runCatching {
        val binding = runtimeFor(view) ?: return@runCatching false
        if (!isSystemBionicsActive(view)) {
            diagnose("apply inactive", view)
            release(view)
            return@runCatching false
        }
        val setMaterial = findMethod(
            view.javaClass,
            "setMiViewMaterialType",
            Int::class.javaPrimitiveType!!,
        ) ?: return@runCatching false
        val setGlass = findMethod(view.javaClass, "setMiGlass", FloatArray::class.java)
            ?: return@runCatching false
        if (managedViews.contains(view) && appliedConfigs[view] == config) {
            // Same config, but the material must still be re-committed. Replacing the expanded
            // notification child (new message) can reset the View's RenderNode material, which
            // drops the edge-refraction/reflection channels until the next full apply. Re-apply
            // the shader params without touching outline/stock state so the customized channels
            // survive every content update.
            diagnose("apply cached", view)
        }
        if (!managedViews.contains(view)) {
            stockStates[view] = StockViewState(
                background = view.background,
                outlineProvider = view.outlineProvider,
                clipToOutline = view.clipToOutline,
            )
            managedViews.add(view)
            installOutline(view)
            ensureDetachCleanup(view)
        }
        viewRuntimes[view] = binding
        binding.setViewMode.invoke(null, view, 1)
        binding.clearBlend.invoke(null, view)
        setMaterial.invoke(view, 1)
        val glassParams = customizeParams(baseParams(binding), config)
        // 3.0.712（Android 17 系统参数比对）：打印注入后的 42 参数全量
        log { "$TAG injectGlassParams=${glassParams.joinToString(",") { "%.3f".format(it) }}" }
        setGlass.invoke(view, glassParams)
        // 3.0.710（Android 17 黑罩根治）：官方 setMiGlass 通道**不清任何背景**。
        // 曾用 view.background=null + clearParentBackgrounds + clearOuterDrawable(drawable=null)
        // 清系统背景 → 系统 LayerDrawable 子 drawable 变 null（日志实锤
        // "Failed to obtain smooth corners because the drawable of the children is null"），
        // 玻璃画布空 → 露出底层黑色 Surface = 黑罩。
        // setMiGlass 是系统材质 API，图层由系统自己管理，无需也绝不能清背景。
        appliedConfigs[view] = config
        diagnose("apply submitted", view)
        view.invalidate()
        true
    }.getOrElse { error ->
        release(view)
        logWarn("$TAG apply failed: ${error.message}")
        false
    }

    /** 递归清父容器背景（中间 FrameLayout 黑块等），到岛窗口层为止，防盖住柔光材质。 */
    private fun clearParentBackgrounds(view: View) {
        var parent: View? = view.parent as? View
        while (parent != null) {
            if (parent.background != null) parent.background = null
            if (parent.javaClass.name == WINDOW_VIEW_CLASS) break
            parent = parent.parent as? View
        }
    }

    /** 清外形画布（DynamicIslandBackgroundView）的 drawable（白色底），防柔光显示不完整。
     *  3.0.709（Android 17 黑罩来源修复）：**不再置 null**——背景视图绘制靠 drawable 字段，
     *  置 null = 画布空 → 露出底层黑色 Surface = 黑罩（视图树全透明但黑罩在，实锤）。
     *  柔光材质已通过 setMiGlass 挂在状态视图上，不依赖背景画布；保留系统 drawable。 */
    private fun clearOuterDrawable(view: View) {
        val bg = findBackgroundView(view) ?: return
        runCatching {
            // 3.0.709：禁掉 drawable=null（黑罩来源）；只 invalidate 保持画布跟随系统
            bg.invalidate()
        }
    }

    /** 向上查找岛外形画布视图。 */
    private fun findBackgroundView(view: View): View? {
        var current: View? = view
        while (current != null) {
            if (current.javaClass.name == BACKGROUND_VIEW_CLASS) return current
            current = current.parent as? View
        }
        return null
    }

    fun isManaged(view: View): Boolean = managedViews.contains(view) && appliedConfigs.containsKey(view)
    fun isActive(view: View): Boolean = isManaged(view)
    fun hasManagedDescendant(root: View): Boolean {
        val views = synchronized(managedViews) { managedViews.toList() }
        return views.any { candidate ->
            var current: View? = candidate
            while (current != null) {
                if (current === root) return@any true
                current = current.parent as? View
            }
            false
        }
    }
    fun isBionicsRuntimeAvailable(): Boolean = bionicsRuntimeAvailable
    fun isSystemBionicsActive(view: View): Boolean = runCatching {
        runtimeFor(view)?.isBionicsActive?.invoke(null, view.context) as? Boolean
    }.getOrNull() == true

    /** Hidden state owns neither a material RenderNode nor a sampler lease. */
    fun suspend(view: View) = release(view, restoreBackground = false)

    /** Acquires the shared sampler before the real/fake slot submits its first visible frame. */
    fun beginRendering(view: View) {
        if (!managedViews.contains(view) || !view.isAttachedToWindow) return
        renderingViews.add(view)
        ensureWindowBlur(view)
        ensurePassWindowBlur(view)
        appliedConfigs[view]?.let { updateWindowRadius(view, it.blurRadius) }
        diagnose("begin-rendering", view)
    }

    /** Temporarily removes only the sampler lease, retaining the prepared View material. */
    fun pauseRendering(view: View) {
        if (!renderingViews.remove(view)) return
        val root = findWindowView(view) ?: return
        if (!hasRenderingView(root)) {
            restoreWindowRadius(root)
            closeRetainedPassBlur(root)
            closeRetainedWindowBlur(root)
        }
    }

    /** Prevents Xiaomi's per-View blur-mode cleanup from racing an active material lease. */
    private fun hookCompatWriters(module: XposedModule, compatClass: Class<*>) {
        if (!hookedCompatClasses.add(compatClass)) return
        findMethod(
            compatClass,
            "setMiViewBlurModeCompat",
            View::class.java,
            Int::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept(LambdaHooker { chain ->    val view = chain.args.getOrNull(0) as? View
                val mode = chain.args.getOrNull(1) as? Int
                if (view != null && mode != 1 && renderingViews.contains(view)) {
                    return@LambdaHooker chain.proceed(arrayOf(view, 1))
                }
                chain.proceed()
            })
        }
    }

    /** Temporary-hide is a window event; drop every lease but keep the sampler alive. */
    fun pauseWindow(root: View) {
        val views = synchronized(renderingViews) { renderingViews.toList() }
            .filter { findWindowView(it) === root }
        views.forEach { renderingViews.remove(it) }
        // Do not close the shared pass/window blur samplers here. The island can still be on
        // screen during its hide animation (notably STATUS_BAR_DISAPPEARANCE, where the island is
        // only logically hidden). Closing the sampler — or updateWindowBlur(false) zeroing the
        // Bionics radius — makes a still-visible material transparent. With the lease gone,
        // Xiaomi's own finalizeAnimFinished() closes the sampler once the animation truly ends,
        // and that close is no longer intercepted.
    }

    fun release(
        view: View,
        restoreBackground: Boolean = true,
        releaseSampling: Boolean = true,
    ) {
        if (!managedViews.remove(view)) return
        val root = findWindowView(view)
        renderingViews.remove(view)
        val stock = stockStates.remove(view)
        appliedConfigs.remove(view)
        removeDetachCleanup(view)
        clearMaterial(view)
        viewRuntimes.remove(view)
        restoreOutline(view, stock)
        if (restoreBackground && view.background == null) view.background = stock?.background
        if (root != null && !hasRenderingView(root)) {
            restoreWindowRadius(root)
            if (releaseSampling) {
                closeRetainedPassBlur(root)
                closeRetainedWindowBlur(root)
            } else {
                // A DEFAULT/Gaussian/Liquid destination now owns the same SystemUI window.
                // Relinquish bookkeeping without sending false over its freshly opened sampler.
                retainedPassBlurRoots.remove(root)
                retainedWindowBlurRoots.remove(root)
            }
        }
    }

    private fun forget(view: View) {
        viewRuntimes.remove(view)
        val root = findWindowView(view)
        managedViews.remove(view)
        renderingViews.remove(view)
        val stock = stockStates.remove(view)
        appliedConfigs.remove(view)
        removeDetachCleanup(view)
        restoreOutline(view, stock)
        if (root != null && !hasRenderingView(root)) {
            restoreWindowRadius(root)
            closeRetainedPassBlur(root)
            closeRetainedWindowBlur(root)
        }
    }

    private fun clearMaterial(view: View) {
        runCatching {
            findMethod(
                view.javaClass,
                "setMiViewMaterialType",
                Int::class.javaPrimitiveType!!,
            )?.invoke(view, 0)
        }
        val binding = runtimeFor(view)
        runCatching { binding?.clearBlend?.invoke(null, view) }
        runCatching { binding?.setViewMode?.invoke(null, view, 0) }
        view.invalidate()
    }

    /** Bionics blur strength is a window property, not one of the 42 shader parameters. */
    private fun updateWindowRadius(source: View, radius: Int) {
        val root = findWindowView(source) ?: return
        val small = radius.coerceIn(0, 100)
        if (appliedWindowRadii[root] == small) return
        val big = (small * 10).coerceIn(0, 1000)
        runCatching {
            findMethod(
                root.javaClass,
                "setMiGlassBlurRadius",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )?.invoke(root, small, big)
            appliedWindowRadii[root] = small
        }.onFailure { error ->
            logWarn("$TAG blur radius unavailable: ${error.message}")
        }
    }

    private fun restoreWindowRadius(root: View) {
        if (appliedWindowRadii.remove(root) == null) return
        runCatching {
            findMethod(
                root.javaClass,
                "setMiGlassBlurRadius",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )?.invoke(root, SYSTEM_SMALL_BLUR_RADIUS, SYSTEM_BIG_BLUR_RADIUS)
        }
    }

    private fun hasRenderingView(root: View): Boolean {
        val views = synchronized(renderingViews) { renderingViews.toList() }
        return views.any { findWindowView(it) === root }
    }

    private fun currentRenderingConfig(root: View): SoftGlassConfig? {
        val views = synchronized(renderingViews) { renderingViews.toList() }
        return views.asSequence()
            .filter { findWindowView(it) === root }
            .maxByOrNull(View::getAlpha)
            ?.let(appliedConfigs::get)
    }

    private fun ensureWindowBlur(source: View) {
        val root = findWindowView(source) ?: return
        if (retainedWindowBlurRoots.contains(root)) return
        val method = findMethod(
            root.javaClass,
            "updateWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        ) ?: return
        runCatching { method.invoke(root, true) }
        retainedWindowBlurRoots.add(root)
    }

    private fun closeRetainedWindowBlur(root: View) {
        if (!retainedWindowBlurRoots.remove(root)) return
        val method = findMethod(
            root.javaClass,
            "updateWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        ) ?: return
        runCatching { method.invoke(root, false) }
    }

    private fun ensurePassWindowBlur(source: View) {
        val root = findWindowView(source) ?: return
        if (retainedPassBlurRoots.contains(root)) return
        val method = findMethod(
            root.javaClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        ) ?: return
        runCatching { method.invoke(root, true) }
        retainedPassBlurRoots.add(root)
    }

    private fun closeRetainedPassBlur(root: View) {
        if (!retainedPassBlurRoots.remove(root)) return
        val method = findMethod(
            root.javaClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        ) ?: return
        runCatching { method.invoke(root, false) }
    }

    private fun findWindowView(view: View): View? {
        var current = view
        while (true) {
            if (current.javaClass.name == WINDOW_VIEW_CLASS) return current
            current = current.parent as? View ?: return null
        }
    }

    private fun installOutline(view: View) {
        val maxRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            32f,
            view.resources.displayMetrics,
        )
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(target: View, outline: Outline) {
                outline.setRoundRect(
                    0,
                    0,
                    target.width,
                    target.height,
                    minOf(target.height / 2f, maxRadius),
                )
            }
        }
        view.clipToOutline = true
    }

    private fun restoreOutline(view: View, stock: StockViewState?) {
        stock ?: return
        view.outlineProvider = stock.outlineProvider
        view.clipToOutline = stock.clipToOutline
    }

    private fun ensureDetachCleanup(view: View) {
        if (detachListeners.containsKey(view)) return
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                // 3.0.772（柔光挂住修复）：attach 回来自动重新提交材质 + 补渲染租约。
                // 岛重建/收缩动画/锁屏恢复后新视图实例立即恢复柔光，不再等下一次 maybeAttach。
                runCatching {
                    val cfg = appliedConfigs[view] ?: return@runCatching
                    val binding = runtimeFor(view) ?: return@runCatching
                    if (!managedViews.contains(view)) {
                        stockStates[view] = StockViewState(
                            background = view.background,
                            outlineProvider = view.outlineProvider,
                            clipToOutline = view.clipToOutline,
                        )
                        managedViews.add(view)
                        installOutline(view)
                    }
                    val setMaterial = findMethod(
                        view.javaClass,
                        "setMiViewMaterialType",
                        Int::class.javaPrimitiveType!!,
                    )
                    val setGlass = findMethod(view.javaClass, "setMiGlass", FloatArray::class.java)
                    binding.setViewMode.invoke(null, view, 1)
                    binding.clearBlend.invoke(null, view)
                    runCatching { setMaterial?.invoke(view, 1) }
                    runCatching { setGlass?.invoke(view, customizeParams(baseParams(binding), cfg)) }
                    renderingViews.add(view)
                    ensureWindowBlur(view)
                    ensurePassWindowBlur(view)
                    appliedConfigs[view]?.let { updateWindowRadius(view, it.blurRadius) }
                    view.invalidate()
                    log { "$TAG attach 重新提交柔光材质（挂住）" }
                }.onFailure { e ->
                    logWarn("$TAG attach 重新提交失败: ${e.message}")
                }
            }

            override fun onViewDetachedFromWindow(view: View) {
                // 3.0.772（柔光挂住修复）：detach 只暂停采样租约、保留材质与配置，
                // 不再 release 清掉 Bionics 材质 —— 否则岛动画/锁屏/重建一次 detach
                // 材质就没了，系统用默认材质渲染（"挂不住、变默认"根因）。
                pauseRendering(view)
            }
        }
        detachListeners[view] = listener
        view.addOnAttachStateChangeListener(listener)
    }

    private fun removeDetachCleanup(view: View) {
        val listener = detachListeners.remove(view) ?: return
        view.removeOnAttachStateChangeListener(listener)
    }

    private data class StockViewState(
        val background: Drawable?,
        val outlineProvider: ViewOutlineProvider?,
        val clipToOutline: Boolean,
    )

    private fun baseParams(binding: RuntimeBinding?): FloatArray = binding?.params?.clone() ?: floatArrayOf(
        0f, 2f, .5f, .8f, .15f, 2.4f, .3f, .2f, 0f, 0f, 0f,
        .06f, .06f, .06f, .6f, .15f, .4f, 1.36f, 1f, 72f, 3.8f,
        80f, 1000f, 1.2f, .6f, -.4f, .6f, -.8f, 1.8f, 1.2f, 1f,
        1.1764706f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
    )

    private fun customizeParams(source: FloatArray, config: SoftGlassConfig): FloatArray {
        val params = source.clone()
        fun scale(index: Int, configured: Double) {
            val original = params[index]
            params[index] = if (original == 0f) configured.toFloat()
            else original * (1f + configured.toFloat() / 100f)
        }
        scale(4, config.softLight)
        params[5] = (1f + config.saturation.toFloat() / 100f).coerceIn(.5f, 1.5f)
        scale(6, config.brightness)
        scale(7, config.darker)
        scale(21, config.edgeThickness)
        scale(24, config.reflection)
        scale(28, config.directionalLightIntensity)
        scale(32, config.refraction)
        scale(33, config.backgroundSaturation)
        scale(34, config.backgroundBrightness)
        scale(35, config.burn)
        if (!config.highlight) {
            params[24] = 0f
            params[28] = 0f
        }
        // Xiaomi's expanded token also mixes a fixed white inner layer through channels 15/16.
        // Keeping it after clearing the RGB tint is what makes the island look opaque gray.
        params[15] = 0f
        params[16] = 0f
        val tintAlpha = Color.alpha(config.tintColor) / 255f
        if (tintAlpha > 0f) {
            params[11] = Color.red(config.tintColor) / 255f
            params[12] = Color.green(config.tintColor) / 255f
            params[13] = Color.blue(config.tintColor) / 255f
            // 白雾保底：采样器黑底必须用高白雾盖住。tint 透明度再低也不允许把
            // 白雾压到 0.6 以下（对齐老词幕固定 0.8 白雾的盖黑逻辑）。
            params[14] = (tintAlpha * (1f + config.transparency.toFloat() / 100f))
                .coerceAtLeast(0.6f)
                .coerceIn(0f, 1f)
        } else {
            // 3.0.712（Android 17 黑罩根治，对齐老词幕 2.9.78 稳定版）：
            // Android 17 系统 42 参数 RGB≈0.06（黑）+ 白雾 0.6，原生玻璃就是深色/黑罩。
            // 必须用亮灰玻璃盖住黑底：RGB 0.6 + 白雾 0.8 + 半径 40 + 颗粒 6 + 亮度 0.8。
            // （RGB=0+白雾0.8=灰黑纱，或 RGB=0+白雾0=透明露系统黑 Surface，都是黑罩）
            params[11] = 0.6f
            params[12] = 0.6f
            params[13] = 0.6f
            params[14] = 0.8f
            params[19] = 40.0f
            params[20] = 6.0f
            params[24] = 0.8f
        }
        return params
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg types: Class<*>): Method? {
        runCatching { return clazz.getMethod(name, *types).apply { isAccessible = true } }
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(name, *types).apply { isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }
}
