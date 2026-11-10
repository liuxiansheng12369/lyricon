/*
 * This file is based on HyperIsland by 1812z.
 * Original: https://github.com/1812z/HyperIsland
 * Ported to Lyricon V3. Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root.island.upstream

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
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
    private const val WINDOW_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private const val SYSTEM_SMALL_BLUR_RADIUS = 50
    private const val SYSTEM_BIG_BLUR_RADIUS = 500
    private val managedViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )
    /**
     * 曾经提交过柔光材质的全部 view（即使后来 managedViews 里被清掉也记得）。
     * 关闭材质时按这张表兜底复位，避免"登记没命中 → 整段 release 直接 return → 关不死"。
     */
    private val everAppliedViews = Collections.synchronizedSet(
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

    // 18.2 exposes updatePassWindowBlur(boolean); 18.3.2.57.0 appends the owning package
    // (updatePassWindowBlur(boolean, String)). Resolve whichever shape the plugin provides.
    // 3.0.935 移植 HyperIsland 最新修复：柔光玻璃不透明——新版签名只 hook 旧签名时
    // Bionics 遮罩不被穿透，柔光显示为不透明色块。
    private val passBlurMethods = Collections.synchronizedMap(
        WeakHashMap<Class<*>, java.lang.ref.WeakReference<Method>>(),
    )

    private fun passBlurMethod(clazz: Class<*>): Method? {
        synchronized(passBlurMethods) { passBlurMethods[clazz]?.get() }?.let { return it }
        val booleanType = Boolean::class.javaPrimitiveType!!
        val resolved = findMethod(clazz, "updatePassWindowBlur", booleanType)
            ?: findMethod(clazz, "updatePassWindowBlur", booleanType, String::class.java)
            ?: return null
        synchronized(passBlurMethods) {
            passBlurMethods[clazz] = java.lang.ref.WeakReference(resolved)
        }
        return resolved
    }

    private fun passBlurArgs(method: Method, enabled: Boolean, pkg: String?): Array<Any?> =
        if (method.parameterTypes.size >= 2) arrayOf(enabled, pkg) else arrayOf(enabled)

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
        HookLogger.i(TAG, "$TAG trace $event view=${view.javaClass.simpleName}@${System.identityHashCode(view)} " +
                "root=${root?.let(System::identityHashCode)} attached=${view.isAttachedToWindow} " +
                "managed=${managedViews.contains(view)} rendering=${renderingViews.contains(view)} " +
                "window=${root != null && retainedWindowBlurRoots.contains(root)} " +
                "pass=${root != null && retainedPassBlurRoots.contains(root)}")
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
            HookLogger.i(TAG, "$TAG runtime unavailable candidate=${loaderId(contentClass.classLoader)}")
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
        HookLogger.i(TAG, "$TAG runtime bound bionics=$bionicsRuntimeAvailable " +
                "params=${params.size} owner=${loaderId(contentClass.classLoader)} " +
                "style=${loaderId(activeMethod.declaringClass.classLoader)} " +
                "compat=${loaderId(compatClass.classLoader)}")
        return true
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
                val keepForSoft = !requested && hasRenderingView(root) &&
                    UpstreamConfig.getBoolean("pref_island_material_enabled", false)
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
        passBlurMethod(windowClass)?.let { method ->
            module.hook(method).intercept(LambdaHooker { chain ->    val root = chain.thisObject as? View ?: return@LambdaHooker chain.proceed()
                val requested = chain.args.getOrNull(0) as? Boolean ?: false
                val keepForSoft = !requested && hasRenderingView(root) &&
                    UpstreamConfig.getBoolean("pref_island_material_enabled", false)
                val result = if (keepForSoft) {
                    // finalizeAnimFinished() closes the sampler as soon as the open/close
                    // animation settles. Rewrite the request instead of patching afterwards:
                    // the original body then keeps its cache, FPS and native flag consistent.
                    // 3.0.935：按方法签名透传所属包名（新版 updatePassWindowBlur(boolean, String)），
                    // 修复柔光玻璃不透明。
                    chain.proceed(passBlurArgs(method, true, chain.args.getOrNull(1) as? String))
                } else {
                    chain.proceed()
                }
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
        // 毛玻璃总开关关闭时一律拒绝挂载：材质 JSON 里可能还留着 soft_glass，
        // 而 preDraw 提交链路只看 JSON type、不看开关，会在这里把 Bionics 重新装回去，
        // 之后 window/pass blur 的 keepForSoft 就永远“保住”系统柔光 → 总开关看起来无效。
        if (!UpstreamConfig.getBoolean("pref_island_material_enabled", false)) {
            diagnose("apply rejected: master off", view)
            if (managedViews.remove(view)) release(view, restoreBackground = false)
            return@runCatching false
        }
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
            everAppliedViews.add(view)
            installOutline(view)
            ensureDetachCleanup(view)
        }
        viewRuntimes[view] = binding
        binding.setViewMode.invoke(null, view, 1)
        binding.clearBlend.invoke(null, view)
        setMaterial.invoke(view, 1)
        setGlass.invoke(view, customizeParams(baseParams(binding), config))
        view.background = null
        appliedConfigs[view] = config
        diagnose("apply submitted", view)
        view.invalidate()
        true
    }.getOrElse { error ->
        release(view)
        HookLogger.w(TAG, "$TAG apply failed: ${error.message}")
        false
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
        // 总开关关闭：不领采样租约（否则窗口/pass blur 会被 keepForSoft 永久保活）。
        if (!UpstreamConfig.getBoolean("pref_island_material_enabled", false)) return
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
        // 旧实现在 managedViews 里找不到 view 就直接 return，导致柔光残留"关不死"；
        // 这里改成：只要曾经 apply 过（everAppliedViews 有记录），照样把材质和窗口玻璃复位。
        val wasManaged = managedViews.remove(view)
        val wasApplied = everAppliedViews.remove(view)
        if (!wasManaged && !wasApplied) return
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

    /** 总开关/材质关闭时释放所有受管视图的柔光材质（否则 Bionics 残留=“关不掉”）。 */
    fun releaseAllManaged() {
        val views = synchronized(managedViews) { managedViews.toList() } +
            synchronized(everAppliedViews) { everAppliedViews.toList() }
        if (views.isEmpty()) {
            resetAllKnownWindows("no-view")
            return
        }
        val roots = Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
        )
        views.distinct().forEach { view ->
            findWindowView(view)?.let { roots.add(it) }
            release(view, restoreBackground = true, releaseSampling = true)
        }
        synchronized(retainedWindowBlurRoots) { roots.addAll(retainedWindowBlurRoots) }
        synchronized(retainedPassBlurRoots) { roots.addAll(retainedPassBlurRoots) }
        roots.forEach { forceResetWindow(it) }
        HookLogger.i(TAG, "$TAG releaseAllManaged: views=${views.size}, roots=${roots.size}")
    }

    /**
     * 无条件把窗口级 Bionics 玻璃复位回系统默认：
     * 恢复 blur 半径 + 关掉 pass/window blur 采样 + 清记账。
     * 不再依赖 hasRenderingView(root) 判断——旧逻辑只要窗口上还剩一个"正在渲染"的 view
     * 就整段跳过，结果柔光看起来永远关不掉。
     */
    fun forceResetWindow(root: View?) {
        root ?: return
        appliedWindowRadii.remove(root)
        runCatching {
            findMethod(
                root.javaClass,
                "setMiGlassBlurRadius",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )?.invoke(root, SYSTEM_SMALL_BLUR_RADIUS, SYSTEM_BIG_BLUR_RADIUS)
        }
        closeRetainedPassBlur(root)
        closeRetainedWindowBlur(root)
        retainedPassBlurRoots.remove(root)
        retainedWindowBlurRoots.remove(root)
        runCatching { root.invalidate() }
    }

    /**
     * 兜底：连 view 都拿不到时（模板已被系统重建/移除），按 SystemUI 里当前的岛窗口找一遍。
     */
    private fun resetAllKnownWindows(reason: String) {
        val roots = synchronized(retainedWindowBlurRoots) { retainedWindowBlurRoots.toList() } +
            synchronized(retainedPassBlurRoots) { retainedPassBlurRoots.toList() }
        if (roots.isEmpty()) {
            HookLogger.d(TAG, "$TAG resetAllKnownWindows($reason): 没有记录的窗口，跳过")
            return
        }
        roots.forEach { forceResetWindow(it) }
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
        // 老版池木的做法：材质类型归零还不够，必须把 setMiGlass 的 42 个参数也清空，
        // 否则 RenderNode 上残留玻璃参数 → 关掉后仍渲染成柔光，表现为"关不死"。
        runCatching {
            findMethod(view.javaClass, "setMiGlass", FloatArray::class.java)
                ?.invoke(view, FloatArray(0))
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
            HookLogger.w(TAG, "$TAG blur radius unavailable: ${error.message}")
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
        val method = passBlurMethod(root.javaClass) ?: return
        runCatching { method.invoke(root, *passBlurArgs(method, true, null)) }
        retainedPassBlurRoots.add(root)
    }

    private fun closeRetainedPassBlur(root: View) {
        if (!retainedPassBlurRoots.remove(root)) return
        val method = passBlurMethod(root.javaClass) ?: return
        runCatching { method.invoke(root, *passBlurArgs(method, false, null)) }
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
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                release(view, restoreBackground = false)
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
        // 1.1.34：要"既透光、白底又立得住"。1.1.32 是白雾把背景糊掉了，1.1.33 又透到看不见。
        // 这一版把内层白雾压到最低，改用一层很淡的中性灰（14=0.14）当"底色"——
        // 背景的颜色与明暗仍然透得进来，白底上则比背景低一档，看得出玻璃轮廓。
        params[15] = 0.10f
        params[16] = 0.12f
        val tintAlpha = Color.alpha(config.tintColor) / 255f
        if (tintAlpha > 0f) {
            params[11] = Color.red(config.tintColor) / 255f
            params[12] = Color.green(config.tintColor) / 255f
            params[13] = Color.blue(config.tintColor) / 255f
            params[14] = (tintAlpha * (1f + config.transparency.toFloat() / 100f))
                .coerceIn(0f, 1f)
        } else {
            // 1.1.38：朝"更像玻璃"推一档——刀口更窄(19)、玻璃更薄(21)、顶边亮线更亮(24)、
            // 方向光更白(29)。14 维持 0.20 不变，透光仍靠它保底。
            params[7] = 0.18f
            params[11] = 0.06f
            params[12] = 0.06f
            params[13] = 0.06f
            params[14] = 0.20f
            params[19] = 45f
            params[21] = 90f
            params[24] = 1.3f
            params[28] = 2.6f
            params[29] = 1.6f
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