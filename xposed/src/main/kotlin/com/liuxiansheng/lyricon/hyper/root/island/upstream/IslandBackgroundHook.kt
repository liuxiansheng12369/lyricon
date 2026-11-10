/*
 * This file is based on HyperIsland by 1812z.
 * Original: https://github.com/1812z/HyperIsland
 * Ported to Lyricon V3. Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root.island.upstream

import android.graphics.*
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandBlurRuntime
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandType as BlurIslandType
import com.liuxiansheng.lyricon.hyper.root.island.upstream.MaterialType
import com.liuxiansheng.lyricon.hyper.root.island.upstream.SoftGlassController
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import io.github.libxposed.api.XposedModule
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Hook 超级岛背景视图，替换默认背景 Drawable 为自定义图片或 GIF。
 *
 * 核心原则：**只对配置了自定义背景的岛类型替换 drawable，绝不影响其他类型的岛外形。**
 * 但遮罩处理采用"全有或全无"策略——只要有一个类型配了自定义背景（anyCustomBgConfigured），
 * 所有类型的遮罩都由本 Hook 管理：
 *   - 有自定义背景的类型 → 清除遮罩（透明，让自定义背景透出）
 *   - 无自定义背景的类型 → loadCustomDrawable 返回纯黑图片（替代系统遮罩）
 *   - 无任何自定义背景时 → 完全跟随系统，不做任何修改
 *
 * 原因：container 是共享视图，清除 container 遮罩后，非自定义类型无法依赖系统遮罩，
 * 必须通过 loadCustomDrawable 返回纯黑图片，走相同渲染管线。
 *
 * 架构分析（来自 JADX 反编译）：
 *   View 层级（来自 DynamicIslandViewBinding）：
 *     DynamicIslandBackgroundView (rootView, 绘制岛外形)
 *       └── DynamicIslandContentView (id=island_content)
 *            ├── smallIslandView (FrameLayout, id=small_island_view)
 *            ├── bigIslandView (DynamicIslandBigIslandView, id=big_island_view)
 *            ├── expandedView (DynamicIslandExpandedView, from ViewStub)
 *            └── container (FrameLayout, id=container)
 *
 *   遮罩来源（来自 JADX updateBackgroundBg）：
 *     - blur 开启时：setMiViewBlurModeCompat(view, 1) + setMiBackgroundBlendColors → 暗色叠加
 *     - blur 关闭时：view.setBackgroundDrawable(dynamic_island_background) → 黑色遮罩
 *     - tablet 路径：view.setBackground(null) + clearMiBlurBlendEffect
 *
 * Hook 策略（anyCustomBgConfigured=true 时）：
 *   1. hookUpdateDarkLightMode → 识别岛类型，存入 ThreadLocal + lastIslandType
 *   2. hookSetDrawable → 替换 drawable（自定义背景 or 纯黑图片）
 *   3. hookAlphaAnimation → 设 alpha=1.0
 *   4. hookUpdateBackgroundBg → 拦截所有类型的遮罩设置（清除遮罩）
 *   5. OS3: hook containerScheduleUpdate → 清除 container 遮罩
 *   6. OS4: hook IslandPropertyUpdater → 清除新版 container/island_mask 层
 *
 * ★ 关键：当任意类型有自定义背景时，所有遮罩都由本 Hook 控制。
 *   非自定义类型用真正的纯黑图片替代系统遮罩，避免 container 清空后变透明。
 *   当没有任何自定义背景时，完全跟随系统，不做任何修改。
 */
object IslandBackgroundHook {

    private const val TAG = "HyperIsland[IslandBg]"

    /** disableBlurAndClearBlend 首次失败告警防刷屏（同一生命周期只记一次） */
    @Volatile
    private var loggedBlurClearFailure = false

    /** 配置 Key */
    private const val KEY_SMALL_BG = "pref_island_bg_small_path"
    private const val KEY_BIG_BG = "pref_island_bg_big_path"
    private const val KEY_EXPAND_BG = "pref_island_bg_expand_path"
    private const val KEY_SMALL_BLUR_ENABLED = "pref_island_blur_small_enabled"
    private const val KEY_BIG_BLUR_ENABLED = "pref_island_blur_big_enabled"
    private const val KEY_EXPAND_BLUR_ENABLED = "pref_island_blur_expand_enabled"
    /** 封面背景开关（app 端 MasterPrefs.KEY_ISLAND_COVER_BG_ENABLED，对齐老词幕 BG_COVER） */
    private const val KEY_COVER_BG_ENABLED = "island_cover_bg_enabled"
    /** 岛背景透明开关（app 端"透明"开关同步写入 island_transparent_enabled，对齐老词幕透明模式） */
    private const val KEY_TRANSPARENT_ENABLED = "island_transparent_enabled"

    /** island 类型枚举 */
    private enum class IslandType { SMALL, BIG, EXPAND }

    private data class DrawableCacheKey(
        val type: IslandType,
        val stokeWidth: Int,
    )

    /** Pre-resolved accessors used by animation hot paths. */
    private data class ManagedLayerAccess(
        val stateMethod: Method?,
        val stateField: Field?,
        val containerMethod: Method,
        val maskMethod: Method?,
    )

    /** 真实外层背景和过渡假视图使用不同 bounds，不能共享同一个 drawable。 */
    private val cachedDrawables = ConcurrentHashMap<DrawableCacheKey, Drawable>()

    /** 按类型和描边宽度记录上次文件修改时间 */
    private val lastFileModified = ConcurrentHashMap<DrawableCacheKey, Long>()

    /** 按类型和描边宽度记录上次配置的路径字符串 */
    private val lastConfigPath = ConcurrentHashMap<DrawableCacheKey, String>()

    /** 按实际目标 Class 去重，避免委托 ClassLoader 对同一方法重复安装 Hook。 */
    private val hookedBackgroundClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )

    /** OS3 and OS4 use different animation property writers; deduplicate them independently. */
    private val hookedOs3AnimationClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedOs4PropertyUpdaterClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedOs4ContentClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    /** 在 updateDarkLightMode → setDrawable 调用链中传递岛类型 */
    private val islandTypeHolder = ThreadLocal<IslandType>()

    /** 上一次确定的岛类型（在 islandTypeHolder 被清除后供其他 hook 使用） */
    @Volatile
    private var lastIslandType: IslandType? = null

    /** 缓存的纯黑 Bitmap（512x512），供无自定义背景的岛类型使用 */
    @Volatile
    private var cachedBlackBitmap: Bitmap? = null

    /** 缓存 anyCustomBgConfigured 结果，避免热路径每帧做 3 次 I/O */
    @Volatile
    private var cachedAnyCustomBg: Boolean? = null

    /** 按类型缓存背景文件可用状态，避免动画热路径访问文件系统。 */
    private val cachedBgAvailability = ConcurrentHashMap<IslandType, Boolean>()
    private val cachedBlurEnabled = ConcurrentHashMap<IslandType, Boolean>()
    /** Retains the last drawable-owning state while OS4 animates through Hidden/Deleted. */
    private val managedContentTypes = Collections.synchronizedMap(
        WeakHashMap<Any, IslandType>()
    )
    private val suppressedSystemDrawables = Collections.synchronizedMap(
        WeakHashMap<View, Drawable>()
    )
    /**
     * 被 clearMaskForView 清掉的原背景存档。
     *
     * 3.0.1093：此前 `clearMaskForView` 直接 `view.background = null` 且不存档，
     * 总开关关闭后没有任何东西能把它还回去 → 现场表现「毛玻璃关了，岛变成一块纯黑底板」
     * （用户反馈的"还原不彻底"）。这里在清之前存档，卸载时原样还回去。
     * 用 WeakHashMap 存，View 销毁后自动回收。
     */
    private val clearedBackgrounds = Collections.synchronizedMap(
        WeakHashMap<View, Drawable?>()
    )

    /** 缓存圆角半径，运行时不会变 */
    @Volatile
    private var cachedCornerRadius: Float? = null

    /** 缓存 MiBlurCompat 反射对象，避免每次调用 clearMaskForView 都做类加载+方法查找 */
    @Volatile
    private var miBlurCompatClass: Class<*>? = null
    @Volatile
    private var setBlurModeMethod: Method? = null
    @Volatile
    private var clearBlendMethod: Method? = null
    @Volatile
    private var setBackgroundBlurModeMethod: Method? = null
    @Volatile
    private var clearBionicsMaterialMethod: Method? = null
    @Volatile
    private var clearBlurBlendEffectMethod: Method? = null
    @Volatile
    private var commonUtilsInstance: Any? = null

    /** 缓存 bgViewClass 的 Field/Method，避免热路径反复反射查找 */
    private val stokeWidthFieldCache = ConcurrentHashMap<Class<*>, Field?>()
    private val drawableFieldCache = ConcurrentHashMap<Class<*>, Field?>()
    private val backgroundAlphaFieldCache = ConcurrentHashMap<Class<*>, Field?>()
    private val scheduleUpdateMethodCache = ConcurrentHashMap<Class<*>, Method?>()

    @Volatile
    private var hookModule: XposedModule? = null

    /** V3 注册入口：由 SystemUIHookRegistry.hook(module, cl) 按 classloader 调用。 */
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        hookModule = module
        onClassLoaderLoaded(module, classLoader)
    }

    /**
     * 当新的 ClassLoader 加载时，尝试识别并 Hook DynamicIsland 相关类。
     */
    private fun onClassLoaderLoaded(module: XposedModule, classLoader: ClassLoader) {
        try {
            val bgViewClass = try {
                classLoader.loadClass("miui.systemui.dynamicisland.DynamicIslandBackgroundView")
            } catch (_: ClassNotFoundException) {
                return
            }
            val contentViewClass = runCatching {
                classLoader.loadClass("miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView")
            }.getOrNull() ?: return
            val stateClass = runCatching {
                classLoader.loadClass("miui.systemui.dynamicisland.event.DynamicIslandState")
            }.getOrNull() ?: return
            if (!hookedBackgroundClasses.add(bgViewClass)) return

            hookSetDrawable(module, bgViewClass)
            hookAlphaAnimation(module, bgViewClass)

            try {
                // HyperOS 4: the new property updater restores container/mask layers.
                // Detect the ABI instead of reading ro.* properties, which change between betas.
                val os4Updater = findOs4PropertyUpdater(classLoader)
                hookUpdateDarkLightMode(
                    module,
                    contentViewClass,
                    stateClass,
                    clearOs4InitialLayers = os4Updater != null,
                )
                hookUpdateBackgroundBg(module, contentViewClass)

                if (os4Updater != null) {
                    runCatching {
                        classLoader.loadClass(
                            "miui.systemui.dynamicisland.window.content.DynamicIslandContentView",
                        )
                    }.getOrNull()?.let { realContentClass ->
                        hookOs4InitialContentLayers(module, realContentClass)
                    }
                    hookOs4PropertyUpdater(module, os4Updater, contentViewClass)
                } else {
                    // HyperOS 3: containerScheduleUpdate() itself is the final mask writer.
                    runCatching {
                        classLoader.loadClass(
                            "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate",
                        )
                    }.getOrNull()?.let { animationClass ->
                        hookOs3ContainerUpdate(module, animationClass, contentViewClass)
                    }
                }
            } catch (e: Throwable) {
                HookLogger.e(TAG, "Failed to hook updateDarkLightMode/updateBackgroundBg: ${e.message}")
            }

        } catch (e: Throwable) {
            HookLogger.e(TAG, "Hook setup failed for CL: ${e.message}")
        }
    }

    /**
     * 获取当前岛类型（优先 ThreadLocal，回退到 lastIslandType）。
     */
    private fun getCurrentIslandType(): IslandType? {
        return islandTypeHolder.get() ?: lastIslandType
    }

    /**
     * Hook DynamicIslandBackgroundView.setDrawable(Drawable)。
     *
     * ★ 仅当当前岛类型有自定义背景时替换 drawable，不影响其他类型。
     * 替换后，精准清除当前类型主视图的遮罩（不遍历所有子 View，避免跨类型干扰）。
     * 额外的遮罩清除由 hookUpdateBackgroundBg 和 hookContainerScheduleUpdate 处理。
     */
    private fun hookSetDrawable(module: XposedModule, bgViewClass: Class<*>) {
        try {
            val setDrawableMethod = bgViewClass.getDeclaredMethod("setDrawable", Drawable::class.java)

            module.hook(setDrawableMethod).intercept(LambdaHooker { chain ->    // ★ 毛玻璃总开关关闭 → 全部透传，恢复系统默认背景
                // 3.0.911 封面背景是独立功能：材质总开关关闭时，只要小岛封面背景开启仍要接管
                val bgViewNow = chain.thisObject as? View
                val typeNow = getCurrentIslandType() ?: bgViewNow?.let(::resolveTypeForBackgroundView)
                android.util.Log.e(
                    "CoverBgDebug",
                    "setDrawable type=$typeNow cover=${isCoverBgEnabled(typeNow)} materialsOff=${materialsOff()}"
                )
                if (materialsOff() && !isCoverBgEnabled(typeNow) && !isTransparentForType(typeNow)) {
                    return@LambdaHooker chain.proceed()
                }
                val bgView = chain.thisObject as? View
                // updateDarkLightMode(state) receives the destination before the shared
                // ContentView.state field is updated. Its ThreadLocal is authoritative here;
                // preferring the old field misclassifies DEFAULT EXPAND as the previous BIG/SOFT.
                val type = getCurrentIslandType()
                    ?: bgView?.let(::resolveTypeForBackgroundView)

                // updateDarkLightMode() creates dynamic_island_background_big_island_dark
                // when another island disappears and BIG is re-asserted. Reject that
                // outer drawable before it reaches DynamicIslandBackgroundView; clearing
                // only container/fake layers cannot affect this independent draw slot.
                // 3.0.911 封面背景修复：系统默认软玻璃时不再一律拦截，封面背景开启要接管。
                if (chain.args.getOrNull(0) is Drawable && type != null &&
                    isSystemSoftGlass(type) && !isCoverBgEnabled(type)
                ) {
                    if (bgView != null) {
                        suppressedSystemDrawables[bgView] = chain.args[0] as Drawable
                    }
                    return@LambdaHooker null
                }

                val result = chain.proceed()
                if (bgView != null) suppressedSystemDrawables.remove(bgView)

                if (type != null && shouldOwnOuterDrawable(type)) {
                    // 封面背景：封面放到状态视图 background（最上层，老词幕 applyToStateView）。
                    // 黑色层（container/island_mask/系统默认背景）在封面之下被盖住，
                    // 歌词是状态视图的子视图，仍在封面上方可见。
                    if (isCoverBgEnabled(type)) {
                        applyCoverOnTop(bgView, type)
                        return@LambdaHooker result
                    }

                    val context = try { bgView?.context } catch (_: Exception) { null }

                    val stokeWidth = if (bgView != null) getStokeWidth(bgView, bgViewClass) else 0

                    val customDrawable = loadCustomDrawable(type, context, module, stokeWidth)
                        ?.let(::newDrawableInstance)

                    if (customDrawable != null) {
                        try {
                            val drawableField = getCachedField(drawableFieldCache, bgViewClass, "drawable")
                                ?: return@LambdaHooker null
                            val renderDrawable = IslandOutlineHook.withOutline(
                                customDrawable,
                                chain.args.getOrNull(0) as? Drawable,
                                type == IslandType.EXPAND,
                                type.name,
                            )
                            if (bgView != null) setWeakCallback(renderDrawable, bgView)
                            drawableField.set(chain.thisObject, renderDrawable)
                        } catch (e: Exception) {
                            HookLogger.e(TAG, "Reflection set drawable failed: ${e.message}")
                        }

                        // Each state owns either its image or an independent black
                        // fallback, so clearing a shared stale mask cannot expose alpha.
                        if (bgView is ViewGroup) {
                            clearMaskForCurrentType(bgView, type)
                            // 封面背景：按老词幕 BG_COVER 机制清黑色层（island_mask 隐藏、
                            // container 底色清空、下层自绘黑层隐藏），露出封面且不碰歌词
                            if (isCoverBgEnabled(type)) {
                                findRealContentView(bgView)?.let(::clearCoverBlackLayers)
                            }
                        }
                    }
                }

                result
            })

        } catch (e: Throwable) {
            HookLogger.e(TAG, "Failed to hook setDrawable: ${e.message}")
        }
    }

    private fun resolveTypeForBackgroundView(backgroundView: View): IslandType? {
        val root = backgroundView as? ViewGroup ?: return null
        val contentView = findRealContentView(root) ?: return null
        val state = runCatching {
            contentView.javaClass.getMethod("getState").invoke(contentView)
        }.getOrNull()
        val resolvedState = resolveIslandType(
            state?.javaClass?.simpleName.orEmpty(),
            state?.javaClass?.name.orEmpty(),
        )
        val visibleType = if (resolvedState == null) {
            sequenceOf(
                IslandType.EXPAND to "getExpandedView",
                IslandType.BIG to "getBigIslandView",
                IslandType.SMALL to "getSmallIslandView",
            ).firstOrNull { (_, getterName) ->
                val child = runCatching {
                    contentView.javaClass.getMethod(getterName).invoke(contentView) as? View
                }.getOrNull()
                child?.visibility == View.VISIBLE && child.alpha > 0f
            }?.first
        } else {
            null
        }
        val currentType = resolvedState ?: visibleType
        if (currentType != null) managedContentTypes[contentView] = currentType
        return currentType ?: managedContentTypes[contentView]
    }

    private fun findRealContentView(root: ViewGroup): Any? {
        val stack = ArrayDeque<ViewGroup>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val group = stack.removeFirst()
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child.javaClass.name ==
                    "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
                ) return child
                if (child is ViewGroup) stack.add(child)
            }
        }
        return null
    }

    /**
     * 精准清除当前岛类型主视图的暗色遮罩。
     *
     * ★ 与旧版 clearChildBackgrounds 不同：只清除匹配当前类型的那一个主视图，
     *   绝不触碰其他类型的视图。container 由 hookContainerScheduleUpdate 单独处理。
     *
     * View 层级：
     *   DynamicIslandBackgroundView (bgView)
     *     └── DynamicIslandContentView (contentView)
     *          ├── smallIslandView  → 只在 type=SMALL 时清除
     *          ├── bigIslandView    → 只在 type=BIG 时清除
     *          ├── expandedView     → 只在 type=EXPAND 时清除
     *          └── container        → 不在此处处理，由 hookContainerScheduleUpdate 处理
     */
    private fun clearMaskForCurrentType(bgView: ViewGroup, currentType: IslandType) {
        // 遍历到 DynamicIslandContentView
        for (i in 0 until bgView.childCount) {
            val contentView = bgView.getChildAt(i)
            if (contentView !is ViewGroup) continue

            // 遍历 contentView 的子 View，找匹配当前类型的那个
            for (j in 0 until contentView.childCount) {
                val child = contentView.getChildAt(j)
                val childType = getIslandTypeForView(child)

                // ★ 只清除类型完全匹配的主视图，跳过 container 和不匹配的类型
                if (childType == currentType) {
                    clearMaskForView(child)
                }
            }
        }
    }

    /**
     * 封面背景清黑层（对齐老词幕 SuperIslandLyricHooker BG_COVER 机制）：
     * 1. island_mask 全屏自绘黑描边 → INVISIBLE（自绘，清 background 停不掉，直接隐藏）
     * 2. container 底色 → background = null
     * 3. container 下层自绘黑色胶囊层 → 仅隐藏满足全部条件的纯绘制层：
     *    id 为空（NO_ID）、非 ViewGroup（跳过容器，歌词/内容安全）、非 island_mask、
     *    非 BackgroundBlur（不碰模糊材质）、willNotDraw=false 且当前可见。
     */
    private fun clearCoverBlackLayers(contentView: Any) {
        runCatching {
            val mask = contentView.javaClass.getMethod("getMask").invoke(contentView) as? View
            if (mask != null && mask.visibility != View.INVISIBLE) {
                mask.visibility = View.INVISIBLE
            }
        }
        runCatching {
            val container = contentView.javaClass.getMethod("getContainer").invoke(contentView) as? View
            if (container != null && container.background != null) {
                container.background = null
            }
        }
        runCatching {
            val cg = contentView.javaClass.getMethod("getContainer").invoke(contentView) as? ViewGroup
                ?: return@runCatching
            for (i in 0 until cg.childCount) {
                val inner = cg.getChildAt(i) as? ViewGroup ?: continue
                for (j in 0 until inner.childCount) {
                    val g = inner.getChildAt(j)
                    if (g.id != View.NO_ID) continue
                    if (g is ViewGroup) continue
                    if (g.visibility == View.INVISIBLE) continue
                    val resName = runCatching {
                        g.resources.getResourceEntryName(g.id)
                    }.getOrNull().orEmpty()
                    if (resName == "island_mask") continue
                    if (g.background != null &&
                        g.background.javaClass.name.contains("BackgroundBlur")
                    ) continue
                    if (!g.willNotDraw()) {
                        g.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    /**
     * 封面背景：封面铺到状态视图 background（最上层，对齐老词幕 applyToStateView）。
     * 黑色层（container/island_mask/系统默认背景）在封面之下被盖住；
     * 歌词是状态视图的子视图，仍在封面上方可见。
     * island_mask 若绘制在状态视图之上，仍按老词幕 BG_COVER 清掉。
     */
    private fun applyCoverOnTop(bgView: View?, type: IslandType) {
        val contentView = (bgView as? ViewGroup)?.let(::findRealContentView) ?: return
        reapplyCoverFor(contentView)
    }

    /** 每帧重挂封面（对齐老词幕 BG_COVER 的 updateContainer 分支） */
    private fun reapplyCoverFor(contentView: Any) {
        val cv = contentView as? ViewGroup ?: return
        val type = resolveTypeOfContentView(contentView) ?: return
        val stateView = findStateViewFrom(cv, type)
        val cover = IslandCoverBackgroundController.resolveCurrentCover(
            stateView?.context ?: cv.context
        )
        android.util.Log.e(
            "CoverBgDebug",
            "reapplyCover: type=$type sv=${stateView?.javaClass?.simpleName} cover=${cover != null}"
        )
        if (stateView != null && cover != null && !cover.isRecycled) {
            stateView.background = IslandCoverBackgroundController.CoverDrawable(cover)
            stateView.invalidate()
        }
        clearCoverBlackLayers(contentView)
    }

    /** 解析 contentView 当前岛类型（state 名优先，回退到历史记录） */
    private fun resolveTypeOfContentView(contentView: Any?): IslandType? {
        if (contentView == null) return null
        val state = runCatching {
            contentView.javaClass.getMethod("getState").invoke(contentView)
        }.getOrNull()
        val resolved = resolveIslandType(
            state?.javaClass?.simpleName.orEmpty(),
            state?.javaClass?.name.orEmpty(),
        )
        return resolved ?: managedContentTypes[contentView]
    }

    /** 从 contentView 子树 BFS 找当前类型的状态视图（对齐老词幕 findStateView） */
    private fun findStateViewFrom(cv: ViewGroup, type: IslandType): View? {
        val stack = ArrayDeque<ViewGroup>()
        stack.add(cv)
        var guard = 0
        while (stack.isNotEmpty() && guard < 400) {
            guard++
            val group = stack.removeFirst()
            for (i in 0 until group.childCount) {
                val ch = group.getChildAt(i)
                if (getIslandTypeForView(ch) == type) return ch
                if (ch is ViewGroup) stack.add(ch)
            }
        }
        return null
    }

    /**
     * Hook DynamicIslandBaseContentView.updateDarkLightMode。
     *
     * 通过 DynamicIslandState 子类名判断岛类型，存入 ThreadLocal + lastIslandType。
     */
    private fun hookUpdateDarkLightMode(
        module: XposedModule,
        contentViewClass: Class<*>,
        stateClass: Class<*>,
        clearOs4InitialLayers: Boolean,
    ) {
        try {
            val method = contentViewClass.getDeclaredMethod(
                "updateDarkLightMode",
                stateClass,
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            val containerMethod = if (clearOs4InitialLayers) {
                contentViewClass.getMethod("getContainer").apply { isAccessible = true }
            } else {
                null
            }
            val maskMethod = if (clearOs4InitialLayers) {
                runCatching {
                    contentViewClass.getMethod("getMask").apply { isAccessible = true }
                }.getOrNull()
            } else {
                null
            }

            module.hook(method).intercept(LambdaHooker { chain ->    val state = chain.args[0]
                val stateName = state?.javaClass?.simpleName ?: ""
                val stateFullName = state?.javaClass?.name ?: ""

                val type = resolveIslandType(stateName, stateFullName)

                // ★ 毛玻璃总开关关闭 → 全部透传（不挂材质、不清遮罩），恢复系统默认岛。
                // 封面背景是独立功能：封面开启时不透传，保持类型识别与清黑层链路。
                // 透明模式同理：透明开启时不透传。
                if (materialsOff() && !isCoverBgEnabled(type) && !isTransparentForType(type)) {
                    return@LambdaHooker chain.proceed()
                }

                if (type != null) {
                    islandTypeHolder.set(type)
                    lastIslandType = type
                }

                try {
                    val result = chain.proceed()
                    // OS4's real container starts with dynamic_island_background from XML.
                    // Restored islands can become visible without an IslandPropertyUpdater
                    // frame, so remove that initial host in the first state transaction.
                    // 说明：不按总开关关闭去跳过（清黑层同时负责让歌词不被系统黑罩盖住）。
                    if (type != null && clearOs4InitialLayers && shouldClearSharedMask(type)
                    ) {
                        val contentView = chain.thisObject
                        runCatching { containerMethod?.invoke(contentView) as? View }
                            .getOrNull()
                            ?.let { clearSharedContainer(it, type) }
                        runCatching { maskMethod?.invoke(contentView) as? View }
                            .getOrNull()
                            ?.let(::clearMaskForView)
                    }
                    result
                } finally {
                    islandTypeHolder.remove()
                }
            })

        } catch (e: Throwable) {
            HookLogger.e(TAG, "Failed to hook updateDarkLightMode: ${e.message}")
        }
    }

    private fun resolveIslandType(simpleName: String, fullName: String): IslandType? {
        return when (simpleName) {
            "SmallIsland" -> IslandType.SMALL
            "BigIsland", "ShowOnceBigIsland" -> IslandType.BIG
            "Expanded", "AppExpanded", "MiniWindowExpanded",
            "SubAppExpanded", "SubMiniWindowExpanded" -> IslandType.EXPAND
            else -> when {
                fullName.contains("SmallIsland") -> IslandType.SMALL
                fullName.contains("BigIsland") -> IslandType.BIG
                fullName.contains("Expanded") -> IslandType.EXPAND
                else -> null
            }
        }
    }

    /**
     * Hook DynamicIslandBackgroundView.alphaAnimation(float)。
     *
     * ★ 仅当当前岛类型有自定义背景时设 alpha=1.0 并跳过 Folme 动画。
     */
    private fun hookAlphaAnimation(module: XposedModule, bgViewClass: Class<*>) {
        try {
            val alphaMethod = bgViewClass.getDeclaredMethod("alphaAnimation", Float::class.javaPrimitiveType)

            module.hook(alphaMethod).intercept(LambdaHooker { chain ->    // ★ 毛玻璃总开关关闭 → 透传
                if (materialsOff()) return@LambdaHooker chain.proceed()
                val type = getCurrentIslandType()

                if (type != null && shouldClearMaskForType(type)) {
                    val bgView = chain.thisObject
                    try {
                        val alphaField = getCachedField(backgroundAlphaFieldCache, bgViewClass, "backgroundAlpha")
                            ?: return@LambdaHooker chain.proceed()
                        alphaField.setFloat(bgView, 1.0f)

                        val scheduleMethod = getCachedMethod(scheduleUpdateMethodCache, bgViewClass, "scheduleUpdate")
                            ?: return@LambdaHooker chain.proceed()
                        scheduleMethod.invoke(bgView)
                    } catch (e: Exception) {
                        HookLogger.e(TAG, "alphaAnimation override failed: ${e.message}, falling back")
                        chain.proceed()
                    }
                    return@LambdaHooker null
                }

                chain.proceed()
                null
            })

        } catch (e: Throwable) {
            HookLogger.e(TAG, "Failed to hook alphaAnimation: ${e.message}")
        }
    }

    /**
     * 通过 View 的类名/资源名判断它属于哪个岛类型。
     *
     * 精确映射（来自 JADX DynamicIslandViewBinding）：
     *   - DynamicIslandBigIslandView → BIG
     *   - DynamicIslandExpandedView → EXPAND
     *   - small_island_view (FrameLayout) → SMALL
     *   - container → 跟随当前岛类型（lastIslandType）
     *   - island_content → 无法确定，不处理
     *   - 其他 → 无法确定，不处理
     */
    private fun getIslandTypeForView(view: View): IslandType? {
        val className = view.javaClass.name
        return when {
            className.contains("BigIslandView") -> IslandType.BIG
            className.contains("ExpandedView") -> IslandType.EXPAND
            else -> {
                val resName = try {
                    view.resources?.getResourceEntryName(view.id) ?: ""
                } catch (_: Exception) { "" }
                when {
                    resName.contains("small_island") -> IslandType.SMALL
                    resName.contains("big_island") -> IslandType.BIG
                    resName.contains("expanded") -> IslandType.EXPAND
                    resName.contains("container") -> lastIslandType
                    else -> null
                }
            }
        }
    }

    /**
     * 清除 View 的暗色遮罩：设置 background=null + blur mode=0 + 清除 blend colors。
     *
     * ★ 必须同时设 blur mode=0，否则残留的 blur mode 会导致系统继续施加模糊效果，
     *   在某些设备上表现为半透明暗色叠加覆盖自定义背景。
     */
    private fun clearMaskForView(view: View) {
        // ★ 注意：这一步不能按总开关关闭去停掉——它同时是"清掉盖在歌词上的系统黑层"的唯一手段，
        //   停了岛会退回系统默认外观（外观看似正常，但歌词被黑罩盖住看不见）。
        // 3.0.1093：清背景前先存档原背景（同一 View 只存第一次的原始值），
        // 否则总开关关闭后无从还原 → 岛被留成一块纯黑底板（"还原不彻底"）。
        synchronized(clearedBackgrounds) {
            if (!clearedBackgrounds.containsKey(view)) {
                clearedBackgrounds[view] = view.background
            }
        }
        view.background = null
        // 高斯/柔光依赖系统材质（背景模糊 + Bionics）：清黑罩时禁用 blur/blend 会把它们
        // 永久关掉显示透明。仅当启用这类系统材质时跳过禁用，由系统玻璃自然覆盖背景（无黑罩）。
        if (!IslandBlurRuntime.configStore.hasSystemMaterialEnabled()) {
            disableBlurAndClearBlend(view)
        }
    }

    /**
     * 是否走「完全透传」：毛玻璃总开关关闭，或当前材质是高斯/柔光（系统原生玻璃）。
     * 高斯/柔光依赖系统窗口渲染：切到它们时必须关掉自绘/遮罩干预，完全交给系统渲染原生玻璃，
     * 否则系统采样不到或清黑罩把系统材质清掉 → 透明异常。
     */
    private fun materialsOff(): Boolean =
        !UpstreamConfig.getBoolean("pref_island_material_enabled", false) ||
            BlurIslandType.entries.any {
                IslandBlurRuntime.configStore.materialFor(it).type == MaterialType.SOFT
            }

    internal fun clearManagedVisualMask(view: View) {
        clearMaskForView(view)
    }

    /** Commits shared-layer transparency only after the real SOFT target owns its renderer. */
    internal fun clearCommittedSoftLayers(contentView: Any, typeName: String) {
        val type = when (typeName) {
            "SMALL" -> IslandType.SMALL
            "BIG" -> IslandType.BIG
            "EXPAND" -> IslandType.EXPAND
            else -> return
        }
        if (!hasCommittedVisual(contentView, type)) return
        runCatching {
            contentView.javaClass.getMethod("getContainer").invoke(contentView) as? View
        }.getOrNull()?.let { clearSharedContainer(it, type) }
        runCatching {
            contentView.javaClass.getMethod("getMask").invoke(contentView) as? View
        }.getOrNull()?.let(::clearMaskForView)
    }

    /** Restores the exact stock drawable rejected while this shared instance belonged to SOFT. */
    internal fun restoreSuppressedSystemDrawable(backgroundView: View) {
        val drawable = suppressedSystemDrawables.remove(backgroundView) ?: return
        val field = runCatching {
            backgroundView.javaClass.getDeclaredField("drawable").apply { isAccessible = true }
        }.getOrNull() ?: return
        runCatching { field.set(backgroundView, drawable) }
        runCatching {
            backgroundView.javaClass.getDeclaredMethod("scheduleUpdate").apply {
                isAccessible = true
            }.invoke(backgroundView)
        }
        backgroundView.invalidate()
    }

    /** 总开关/材质关闭时恢复所有被替换的系统背景 drawable（否则毛玻璃效果残留=“关不掉”）。 */
    internal fun restoreAllSuppressedDrawables() {
        val views = synchronized(suppressedSystemDrawables) {
            suppressedSystemDrawables.keys.toList()
        }
        views.forEach { view ->
            // 关闭时把系统 blur mode / Bionics 一并清掉（高斯/柔光材质残留），再恢复原背景
            disableBlurAndClearBlend(view)
            restoreSuppressedSystemDrawable(view)
        }
    }

    /**
     * 3.0.1093：把 [clearMaskForView] 清掉的原背景全部还回去。
     *
     * 只还原「当前背景仍为空」的 View——若系统在卸载后又自己铺了新背景，则以系统为准，
     * 避免把过期 Drawable 盖回去。
     */
    internal fun restoreAllClearedBackgrounds() {
        val snapshot = synchronized(clearedBackgrounds) {
            clearedBackgrounds.entries.map { it.key to it.value }
        }
        clearedBackgrounds.clear()
        var restored = 0
        snapshot.forEach { (view, drawable) ->
            runCatching {
                if (drawable != null && view.background == null) {
                    view.background = drawable
                    view.invalidate()
                    restored++
                }
            }.onFailure { e ->
                HookLogger.e(TAG, "还原被清掉的岛背景失败: ${e.message}", e)
            }
        }
        HookLogger.i(TAG, "岛背景还原完成: 共 ${snapshot.size} 个，实际还原 $restored 个")
    }


    /**
     * 禁用 blur 并清除 blend colors。
     * blur mode 设为 0 + 清除 blend colors，确保无暗色叠加残留。
     * ★ 反射对象缓存，避免每次调用都做类加载+方法查找。
     */
    private fun disableBlurAndClearBlend(view: View) {
        try {
            val cl = view.javaClass.classLoader ?: return

            // 确保 MiBlurCompat 反射对象已缓存
            if (miBlurCompatClass == null || miBlurCompatClass?.classLoader != cl) {
                val blurClass = sequenceOf(
                    "miui.systemui.util.MiBlurCompat",
                    "miui.util.MiBlurCompat",
                ).mapNotNull { name ->
                    runCatching { cl.loadClass(name) }.getOrNull()
                }.firstOrNull() ?: return
                miBlurCompatClass = blurClass
                setBlurModeMethod = blurClass.getDeclaredMethod(
                    "setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType
                )
                clearBlendMethod = blurClass.getDeclaredMethod(
                    "clearMiBackgroundBlendColorCompat", View::class.java
                )
                setBackgroundBlurModeMethod = runCatching {
                    blurClass.getDeclaredMethod(
                        "setMiBackgroundBlurModeCompat",
                        View::class.java,
                        Int::class.javaPrimitiveType,
                    ).apply { isAccessible = true }
                }.getOrNull()

                val backgroundStyleClass = runCatching {
                    cl.loadClass("miui.systemui.util.MiBackgroundStyle")
                }.getOrNull()
                clearBionicsMaterialMethod = backgroundStyleClass?.let { clazz ->
                    runCatching {
                        clazz.getDeclaredMethod("clearBionicsMaterial", View::class.java).apply {
                            isAccessible = true
                        }
                    }.getOrNull()
                }

                val commonUtilsClass = runCatching {
                    cl.loadClass("miui.systemui.util.CommonUtils")
                }.getOrNull()
                commonUtilsInstance = commonUtilsClass?.let { clazz ->
                    runCatching { clazz.getField("INSTANCE").get(null) }.getOrNull()
                }
                clearBlurBlendEffectMethod = commonUtilsClass?.let { clazz ->
                    runCatching {
                        clazz.getDeclaredMethod("clearMiBlurBlendEffect", View::class.java).apply {
                            isAccessible = true
                        }
                    }.getOrNull()
                }
            }

            // OS4's bionics material is independent from View.background and blend colors.
            try {
                clearBionicsMaterialMethod?.invoke(null, view)
            } catch (_: Exception) {}

            try {
                clearBlurBlendEffectMethod?.invoke(commonUtilsInstance, view)
            } catch (_: Exception) {}

            try {
                setBackgroundBlurModeMethod?.invoke(null, view, 0)
            } catch (_: Exception) {}

            // 1. 设 blur mode = 0（禁用模糊）
            try {
                setBlurModeMethod?.invoke(null, view, 0)
            } catch (_: Exception) {}

            // 2. 清除 blend colors
            try {
                clearBlendMethod?.invoke(null, view)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            // 诊断：清除系统毛玻璃/Bionics/blend 失败会残留暗色叠加（毛玻璃"关不掉"）。
            // 反射探测失败（系统版本无对应 API）属正常，一次记录即可，不刷屏。
            if (!loggedBlurClearFailure) {
                loggedBlurClearFailure = true
                HookLogger.w(
                    TAG,
                    "disableBlurAndClearBlend 失败（毛玻璃可能残留）: " +
                            "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    private fun anyBlurEnabled(): Boolean {
        return isBlurEnabledForType(IslandType.SMALL) ||
            isBlurEnabledForType(IslandType.BIG) ||
            isBlurEnabledForType(IslandType.EXPAND)
    }

    /**
     * 统一辅助方法：将自定义 drawable 应用到 backgroundView 并安排重绘。
     *
     * 1. 通过反射设置 drawable 字段
     * 2. 设置 backgroundAlpha = 1.0
     * 3. 取消 Folme 动画（设 backgroundAlpha 直接覆盖）
     * 4. 调用 scheduleUpdate() 触发重绘
     * 5. 精准清除当前类型主视图的遮罩
     */
    private fun applyDrawableToBgView(
        bgView: View,
        bgViewClass: Class<*>,
        type: IslandType,
        module: XposedModule,
        customDrawable: Drawable
    ) {
        try {
            val drawable = newDrawableInstance(customDrawable)
            val drawableField = getCachedField(drawableFieldCache, bgViewClass, "drawable") ?: return
            val stockDrawable = drawableField.get(bgView) as? Drawable
            val renderDrawable = IslandOutlineHook.withOutline(
                drawable,
                stockDrawable,
                type == IslandType.EXPAND,
                type.name,
            )
            setWeakCallback(renderDrawable, bgView)
            drawableField.set(bgView, renderDrawable)
        } catch (e: Exception) {
            HookLogger.e(TAG, "applyDrawable failed: ${e.message}")
        }

        try {
            val alphaField = getCachedField(backgroundAlphaFieldCache, bgViewClass, "backgroundAlpha")
            alphaField?.setFloat(bgView, 1.0f)
        } catch (_: Exception) {}

        try {
            val scheduleMethod = getCachedMethod(scheduleUpdateMethodCache, bgViewClass, "scheduleUpdate")
            scheduleMethod?.invoke(bgView)
        } catch (_: Exception) {}

        if (bgView is ViewGroup) {
            clearMaskForCurrentType(bgView, type)
        }
    }

    /** Restores this state's image or black fallback after another state releases the shared slot. */
    internal fun restoreCustomBackground(backgroundView: View, typeName: String) {
        val type = when (typeName) {
            "SMALL" -> IslandType.SMALL
            "BIG" -> IslandType.BIG
            "EXPAND" -> IslandType.EXPAND
            else -> return
        }
        val module = hookModule ?: return
        val drawable = loadCustomDrawable(
            type,
            backgroundView.context,
            module,
            getStokeWidth(backgroundView, backgroundView.javaClass),
        ) ?: return
        applyDrawableToBgView(
            backgroundView,
            backgroundView.javaClass,
            type,
            module,
            drawable,
        )
    }

    /** Creates an independent image drawable for a fake transition island. */
    internal fun createTransitionBackground(view: View, typeName: String): Drawable? {
        val type = when (typeName) {
            "SMALL" -> IslandType.SMALL
            "BIG" -> IslandType.BIG
            "EXPAND" -> IslandType.EXPAND
            else -> return null
        }
        val module = hookModule ?: return null
        return loadCustomDrawable(type, view.context, module, allowBlurFallback = true)
            ?.let(::newDrawableInstance)
            ?.also { setWeakCallback(it, view) }
    }

    internal fun updateStockOutline(
        backgroundView: Any?,
        stockDrawable: Drawable?,
        typeName: String?,
    ) {
        val view = backgroundView as? View ?: return
        val stock = stockDrawable ?: return
        val type = getCurrentIslandType() ?: return
        if (type.name != typeName) return
        val drawableField = getCachedField(
            drawableFieldCache,
            view.javaClass,
            "drawable",
        ) ?: return
        val current = runCatching { drawableField.get(view) as? Drawable }.getOrNull()
        val updated = IslandOutlineHook.refreshOutline(
            current,
            stock,
            type == IslandType.EXPAND,
            type.name,
        ) ?: return
        setWeakCallback(updated, view)
        runCatching { drawableField.set(view, updated) }
        view.invalidate()
    }

    /**
     * Hook DynamicIslandBaseContentView.updateBackgroundBg(View, boolean)。
     *
     * ★ 根据 View 自身类型判断是否需要跳过原方法：
     *   - View 属于有自定义背景的类型 → 跳过原方法，清除遮罩（background + blur + blend）
     *   - View 属于无自定义背景的类型 → 执行原方法，完全跟随系统
     *
     * 这样只配 BIG 背景时，updateBackgroundBg(smallIslandView) 仍走原方法，
     * smallIslandView 的遮罩正常设置，不会变透明。
     */
    private fun hookUpdateBackgroundBg(module: XposedModule, contentViewClass: Class<*>) {
        try {
            val method = contentViewClass.getDeclaredMethod(
                "updateBackgroundBg",
                View::class.java,
                Boolean::class.javaPrimitiveType
            )

            module.hook(method).intercept(LambdaHooker { chain ->    val view = chain.args[0] as? View ?: return@LambdaHooker chain.proceed()
                val viewType = getIslandTypeForView(view)

                // ★ 毛玻璃总开关关闭 → 全部透传，恢复系统默认背景。
                // 封面背景/透明模式独立功能：开启时不透传，保持清黑层链路。
                if (materialsOff() && !isCoverBgEnabled(viewType) && !isTransparentForType(viewType)) {
                    return@LambdaHooker chain.proceed()
                }

                if (viewType != null && hasBgFileForType(viewType)) {
                    clearMaskForView(view)
                    return@LambdaHooker null
                }

                if (viewType != null && isBlurEnabledForType(viewType)) {
                    // The native BlurDrawable lives on DynamicIslandBackgroundView.
                    // Any stock background on this content view is above it and would
                    // make the blur appear opaque until another feature clears it.
                    clearMaskForView(view)
                    return@LambdaHooker null
                }

                // 无自定义背景 → 执行原方法
                chain.proceed()
                null
            })

        } catch (e: Throwable) {
            HookLogger.e(TAG, "Failed to hook updateBackgroundBg: ${e.message}")
        }
    }

    /**
     * HyperOS 3 path.
     *
     * OS3 restores the shared container background from
     * DynamicIslandAnimationDelegate.containerScheduleUpdate(). Keep this path intact for OS3;
     * OS4 is handled at IslandPropertyUpdater below.
     */
    private fun hookOs3ContainerUpdate(
        module: XposedModule,
        animDelegateClass: Class<*>,
        contentViewClass: Class<*>,
    ) {
        if (!hookedOs3AnimationClasses.add(animDelegateClass)) return
        try {
            val viewField = animDelegateClass.getDeclaredField("view").apply { isAccessible = true }
            val access = createManagedLayerAccess(contentViewClass, includeOs4Mask = false)
            val method = animDelegateClass.declaredMethods.firstOrNull {
                it.name == "containerScheduleUpdate" && it.parameterCount == 0
            } ?: return
            method.isAccessible = true
            module.hook(method).intercept(LambdaHooker { chain ->    // ★ 毛玻璃总开关关闭 → 透传
                if (materialsOff()) return@LambdaHooker chain.proceed()
                val result = chain.proceed()
                runCatching { viewField.get(chain.thisObject) }
                    .getOrNull()
                    ?.let { clearManagedLayers(it, access) }
                result
            })
        } catch (e: Throwable) {
            hookedOs3AnimationClasses.remove(animDelegateClass)
            HookLogger.e(TAG, "Failed to hook OS3 containerScheduleUpdate: ${e.message}")
        }
    }

    /** The class moved packages in some OS4 beta builds, so probe both binary names. */
    private fun findOs4PropertyUpdater(classLoader: ClassLoader): Class<*>? {
        return sequenceOf(
            "miui.systemui.dynamicisland.anim.ui.animator.IslandPropertyUpdater",
            "miui.systemui.dynamicisland.anim.p110ui.animator.IslandPropertyUpdater",
        ).mapNotNull { name -> runCatching { classLoader.loadClass(name) }.getOrNull() }
            .firstOrNull()
    }

    /** Removes OS4's XML-provided black container before a restored island can be shown. */
    private fun hookOs4InitialContentLayers(module: XposedModule, contentClass: Class<*>) {
        if (!hookedOs4ContentClasses.add(contentClass)) return
        val method = runCatching { contentClass.getDeclaredMethod("onFinishInflate") }
            .getOrNull() ?: return
        val containerMethod = runCatching { contentClass.getMethod("getContainer") }
            .getOrNull() ?: return
        val maskMethod = runCatching { contentClass.getMethod("getMask") }.getOrNull()
        method.isAccessible = true
        module.hook(method).intercept(LambdaHooker { chain ->// ★ 毛玻璃总开关关闭 → 透传
            if (materialsOff()) return@LambdaHooker chain.proceed()
            val result = chain.proceed()
            // With mixed stock/custom states, wait for updateDarkLightMode where the state is
            // known. Clearing at inflation is safe only when every state owns the shared host.
            if (IslandType.entries.all(::shouldClearSharedMask) &&
                IslandType.entries.none(::isSystemSoftGlass)
            ) {
                val contentView = chain.thisObject
                runCatching { containerMethod.invoke(contentView) as? View }
                    .getOrNull()
                    ?.let(::clearMaskForView)
                runCatching { maskMethod?.invoke(contentView) as? View }
                    .getOrNull()
                    ?.let(::clearMaskForView)
            }
            result
        })
    }

    /**
     * HyperOS 4 path.
     *
     * New OS4 builds moved all per-frame writes into IslandPropertyUpdater. Hooking the delegate
     * is too early/indirect now: updateContainer() can restore the stock Drawable, MiBlur blend,
     * bionics material and island_mask after it. Clear them directly after the final writer.
     */
    private fun hookOs4PropertyUpdater(
        module: XposedModule,
        updaterClass: Class<*>,
        contentViewClass: Class<*>,
    ) {
        if (!hookedOs4PropertyUpdaterClasses.add(updaterClass)) return
        try {
            val viewField = updaterClass.getDeclaredField("view").apply { isAccessible = true }
            val access = createManagedLayerAccess(contentViewClass, includeOs4Mask = true)
            val methods = updaterClass.declaredMethods.filter {
                it.name == "updateContainer" && it.parameterCount == 1
            }
            if (methods.isEmpty()) error("updateContainer(IslandAnimProperties) not found")
            methods.forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept(LambdaHooker { chain ->        val contentView = runCatching { viewField.get(chain.thisObject) }.getOrNull()
                    val type = contentView?.let(::resolveTypeOfContentView)
                    // ★ 毛玻璃总开关关闭 → 透传。封面背景/透明模式独立功能：
                    // 开启时不透传，每帧保持清黑层（封面还每帧重挂，对齐老词幕 updateContainer）。
                    if (materialsOff() && !isCoverBgEnabled(type) && !isTransparentForType(type)) {
                        return@LambdaHooker chain.proceed()
                    }
                    val result = chain.proceed()
                    if (contentView != null) {
                        clearManagedLayers(contentView, access)
                        if (isCoverBgEnabled(type)) {
                            reapplyCoverFor(contentView)
                        }
                    }
                    result
                })
            }
        } catch (e: Throwable) {
            hookedOs4PropertyUpdaterClasses.remove(updaterClass)
            HookLogger.e(TAG, "Failed to hook OS4 IslandPropertyUpdater: ${e.message}")
        }
    }

    /** Clears only layers owned by the currently rendered state. */
    private fun clearManagedLayers(contentView: Any, access: ManagedLayerAccess) {
        val state = runCatching { access.stateMethod?.invoke(contentView) }.getOrNull()
            ?: runCatching { access.stateField?.get(contentView) }.getOrNull()
        val resolvedType = resolveIslandType(
            state?.javaClass?.simpleName.orEmpty(),
            state?.javaClass?.name.orEmpty(),
        )
        if (resolvedType != null) {
            managedContentTypes[contentView] = resolvedType
        }
        // Hidden/Deleted are animation states, not proof that drawing has stopped. OS4 keeps
        // shrinking the previous island after entering them, so continue clearing the masks for
        // that previous SMALL/BIG/EXPAND type until the View instance is discarded.
        val type = resolvedType ?: managedContentTypes[contentView] ?: return
        if (!anyManagedOuterVisual()) return

        val container = runCatching { access.containerMethod.invoke(contentView) as? View }
            .getOrNull()
        if (container == null || !shouldClearSharedMask(type)) return
        clearSharedContainer(container, type)

        // OS3 did not use island_mask as the final per-frame visual writer. OS4 does, and its
        // gradient/bionics state is independent from View.background, so clear all three states.
        runCatching { access.maskMethod?.invoke(contentView) as? View }
            .getOrNull()
            ?.let(::clearMaskForView)
    }


    private fun createManagedLayerAccess(
        contentViewClass: Class<*>,
        includeOs4Mask: Boolean,
    ): ManagedLayerAccess {
        val stateMethod = runCatching {
            contentViewClass.getMethod("getState").apply { isAccessible = true }
        }.getOrNull()
        val stateField = if (stateMethod == null) {
            contentViewClass.getDeclaredField("state").apply { isAccessible = true }
        } else {
            null
        }
        val containerMethod = contentViewClass.getMethod("getContainer").apply {
            isAccessible = true
        }
        val maskMethod = if (includeOs4Mask) {
            runCatching {
                contentViewClass.getMethod("getMask").apply { isAccessible = true }
            }.getOrNull()
        } else {
            null
        }
        return ManagedLayerAccess(stateMethod, stateField, containerMethod, maskMethod)
    }

    /**
     * 检查指定类型是否有配置路径且文件存在。
     */
    private fun hasBgFileForType(type: IslandType): Boolean {
        cachedBgAvailability[type]?.let { return it }
        // 3.0.795 透明背景：island_transparent_enabled 开启 → 该类型透明（不依赖文件）
        if (isTransparentForType(type)) {
            return true.also { cachedBgAvailability[type] = it }
        }
        // 3.0.795 封面背景：island_cover_bg_enabled → 小岛/大岛/展开全部铺封面（对齐老词幕 BG_COVER）。
        // 实测 OS4 媒体常驻收起胶囊=DynamicIslandBigIslandView（V3 归 BIG，用户语义“小岛”），
        // 展开大卡片=Expanded（V3 归 EXPAND，用户语义“大岛”），都铺满封面。
        if (UpstreamConfig.getBoolean(KEY_COVER_BG_ENABLED, false)) {
            return true.also { cachedBgAvailability[type] = it }
        }
        val configPath = when (type) {
            IslandType.SMALL -> UpstreamConfig.getString(KEY_SMALL_BG)
            IslandType.BIG -> UpstreamConfig.getString(KEY_BIG_BG)
            IslandType.EXPAND -> UpstreamConfig.getString(KEY_EXPAND_BG)
        }
        if (configPath.isNullOrBlank()) {
            cachedBgAvailability[type] = false
            return false
        }
        return (IslandBackgroundFile.resolve(configPath) != null).also {
            cachedBgAvailability[type] = it
        }
    }

    private fun shouldClearMaskForType(type: IslandType): Boolean {
        return hasBgFileForType(type) ||
            (!isSystemSoftGlass(type) && isBlurEnabledForType(type))
    }

    private fun shouldOwnOuterDrawable(type: IslandType): Boolean {
        // 封面背景/透明模式独立功能：开启时即使系统默认软玻璃也强制接管
        if (isSystemSoftGlass(type) && !isCoverBgEnabled(type) && !isTransparentForType(type)) {
            return false
        }
        return hasBgFileForType(type) ||
            (!isBlurEnabledForType(type) && anyCustomBgConfigured())
    }

    /** 封面背景开启（island_cover_bg_enabled）：小岛/大岛/展开全部铺封面，对齐老词幕 BG_COVER */
    private fun isCoverBgEnabled(type: IslandType?): Boolean =
        // 封面背景功能已下线，始终返回 false
        false

    private fun anyManagedOuterVisual(): Boolean {
        return anyCustomBgConfigured() || anyBlurEnabled() || anySystemSoftGlass()
    }

    private fun clearSharedContainer(container: View, type: IslandType) {
        // 同 clearMaskForView：歌词可见性依赖它，不能按总开关关闭去停。
        if (!shouldClearSharedMask(type)) return
        // Both generations may restore blend/bionics state without assigning a Drawable, so
        // background == null alone is not a reliable indication that the mask is gone.
        clearMaskForView(container)
    }

    private fun shouldClearSharedMask(type: IslandType): Boolean {
        // SOFT owns the concrete state View, while container/island_mask are independent
        // shared black layers written by IslandPropertyUpdater. Clear those hosts exactly
        // like the Gaussian path; this never touches the Bionics material on the child View.
        if (isSystemSoftGlass(type)) {
            return true
        }
        // LiquidGlassDrawable is installed only on an active blur state, so the same blur key
        // deliberately covers both the plain glass/blur pipeline and custom image backgrounds.
        return isBlurEnabledForType(type) || anyCustomBgConfigured()
    }

    /** Shared masks may be removed only after this ContentView owns a drawable/material. */
    private fun hasCommittedVisual(contentView: Any?, type: IslandType): Boolean {
        if (!isSystemSoftGlass(type)) return true
        val root = contentView as? View ?: return false
        if (SoftGlassController.hasManagedDescendant(root)) return true
        val backgroundView = findBackgroundAncestor(root) ?: return false
        return IslandBlurRuntime.outerBlurRegistry.hasActiveVisual(backgroundView)
    }

    private fun findBackgroundAncestor(start: View): View? {
        var current: View? = start
        while (current != null) {
            if (current.javaClass.name ==
                "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
            ) return current
            current = current.parent as? View
        }
        return null
    }

    private fun isBlurEnabledForType(type: IslandType): Boolean {
        cachedBlurEnabled[type]?.let { return it }
        val blurKey = when (type) {
            IslandType.SMALL -> KEY_SMALL_BLUR_ENABLED
            IslandType.BIG -> KEY_BIG_BLUR_ENABLED
            IslandType.EXPAND -> KEY_EXPAND_BLUR_ENABLED
        }
        val runtimeType = when (type) {
            IslandType.SMALL -> BlurIslandType.SMALL
            IslandType.BIG -> BlurIslandType.BIG
            IslandType.EXPAND -> BlurIslandType.EXPAND
        }
        val runtimeStore = IslandBlurRuntime.configStore
        val enabled = runtimeStore.materialFor(runtimeType).type != MaterialType.SOFT &&
            (UpstreamConfig.getBoolean(blurKey, false) ||
                runtimeStore.blurFor(runtimeType).isActive)
        return enabled.also { cachedBlurEnabled[type] = it }
    }

    /**
     * 岛背景透明：对齐老词幕——透明只作用于小岛（收起胶囊），大岛/展开保持黑底。
     * 老词幕判定：type == ISLAND_TYPE_SMALL && isTransparentBigEnabled -> BG_TRANSPARENT
     */
    private fun isTransparentForType(type: IslandType?): Boolean {
        // 收起态 BIG（DynamicIslandBigIslandView，用户语义“小岛”）+ SMALL 都透明；
        // 与封面背景同理：OS4 媒体常驻收起胶囊归 V3 BIG，只认 SMALL 会无效。
        return (type == IslandType.BIG || type == IslandType.SMALL) &&
            UpstreamConfig.getBoolean(KEY_TRANSPARENT_ENABLED, false)
    }

    private fun isSystemSoftGlass(type: IslandType): Boolean {
        // 说明：这里只判"当前材质是不是柔光"，不掺总开关——因为清黑罩（歌词可见性）依赖它。
        // "关总开关不生效"的门禁放在真正挂载材质的入口（SoftGlassController.apply / 各 registry）。
        val runtimeType = when (type) {
            IslandType.SMALL -> BlurIslandType.SMALL
            IslandType.BIG -> BlurIslandType.BIG
            IslandType.EXPAND -> BlurIslandType.EXPAND
        }
        return IslandBlurRuntime.configStore.materialFor(runtimeType).type == MaterialType.SOFT
    }

    private fun anySystemSoftGlass(): Boolean {
        return IslandType.entries.any(::isSystemSoftGlass)
    }

    /**
     * 从缓存获取或创建 Field 对象，避免热路径反复反射查找。
     * 反射失败返回 null，由调用方安全处理。
     */
    private fun getCachedField(
        cache: ConcurrentHashMap<Class<*>, Field?>,
        clazz: Class<*>,
        fieldName: String
    ): Field? {
        cache[clazz]?.let { return it }
        return try {
            val field = clazz.getDeclaredField(fieldName).apply { isAccessible = true }
            cache[clazz] = field
            field
        } catch (_: Exception) { null }
    }

    /**
     * 从缓存获取或创建 Method 对象，避免热路径反复反射查找。
     * 反射失败返回 null，由调用方安全处理。
     */
    private fun getCachedMethod(
        cache: ConcurrentHashMap<Class<*>, Method?>,
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Method? {
        cache[clazz]?.let { return it }
        return try {
            val method = clazz.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
            cache[clazz] = method
            method
        } catch (_: Exception) { null }
    }

    /**
     * 读取 bgView 的 stokeWidth 字段值，Field 对象缓存复用。
     */
    private fun getStokeWidth(bgView: View, bgViewClass: Class<*>): Int {
        return try {
            val field = stokeWidthFieldCache.getOrPut(bgViewClass) {
                try {
                    bgViewClass.getDeclaredField("stokeWidth").apply { isAccessible = true }
                } catch (_: Exception) { null }
            }
            field?.getInt(bgView) ?: 0
        } catch (_: Exception) { 0 }
    }

    /**
     * 加载指定类型的自定义背景 BitmapDrawable。
     * ★ 只加载该类型的背景，不回退到其他类型。
     * ★ 当该类型没有自定义背景但其他类型有时，返回纯黑图片（避免 container 清空后变透明）。
     */
    private fun loadCustomDrawable(
        type: IslandType,
        context: android.content.Context?,
        module: XposedModule,
        stokeWidth: Int = 0,
        allowBlurFallback: Boolean = false,
    ): Drawable? {
        val cacheKey = DrawableCacheKey(type, stokeWidth.coerceAtLeast(0))
        // 3.0.795 透明背景：island_transparent_enabled 开启 → 该类型背景透明（去掉系统黑遮罩透出桌面）。
        // 优先级高于封面/文件背景。
        if (isTransparentForType(type)) {
            return ColorDrawable(Color.TRANSPARENT)
        }
        // 封面背景功能已下线，不再走 cover 分支
        val configPath = when (type) {
            IslandType.SMALL -> UpstreamConfig.getString(KEY_SMALL_BG)
            IslandType.BIG -> UpstreamConfig.getString(KEY_BIG_BG)
            IslandType.EXPAND -> UpstreamConfig.getString(KEY_EXPAND_BG)
        }

        if (configPath.isNullOrBlank()) {
            if (anyCustomBgConfigured() || allowBlurFallback && anyBlurEnabled()) {
                return loadBlackDrawable(context, module, stokeWidth)
            }
            return null
        }

        val file = IslandBackgroundFile.resolve(configPath)
        if (file == null) {
            if (anyCustomBgConfigured() || allowBlurFallback && anyBlurEnabled()) {
                return loadBlackDrawable(context, module, stokeWidth)
            }
            return null
        }

        val currentModified = file.lastModified()
        val cachedModified = lastFileModified[cacheKey] ?: 0L
        val cachedPath = lastConfigPath[cacheKey] ?: ""

        val cachedDrawable = cachedDrawables[cacheKey]
        if (cachedDrawable is RoundedClippingAnimatedDrawable) {
            cachedDrawable.start()
        }

        if (cachedDrawable == null || currentModified != cachedModified || cachedPath != configPath) {
            synchronized(this) {
                if (cachedDrawables[cacheKey] == null || currentModified != (lastFileModified[cacheKey] ?: 0L) || lastConfigPath[cacheKey] != configPath) {
                    val drawable = decodeFile(file, context, module, cacheKey.stokeWidth)
                    if (drawable != null) {
                        val previous = cachedDrawables.put(cacheKey, drawable)
                        if (previous is RoundedClippingAnimatedDrawable && previous !== drawable) {
                            previous.release()
                        }
                        lastFileModified[cacheKey] = currentModified
                        lastConfigPath[cacheKey] = configPath
                    }
                }
            }
        }
        return cachedDrawables[cacheKey]
    }

    /**
     * 检查是否至少有一个岛类型配置了自定义背景。
     * ★ 结果缓存，避免热路径每帧做 3 次跨进程读 + 磁盘 I/O。
     *   配置变更时由 onConfigChanged() 清除缓存。
     */
    private fun anyCustomBgConfigured(): Boolean {
        cachedAnyCustomBg?.let { return it }
        val result = hasBgFileForType(IslandType.SMALL)
                || hasBgFileForType(IslandType.BIG)
                || hasBgFileForType(IslandType.EXPAND)
        cachedAnyCustomBg = result
        return result
    }

    /**
     * 加载纯黑背景 Drawable（512x512 Bitmap + RoundedClippingDrawable）。
     * ★ 用于没有自定义背景的岛类型，避免 container 清空后变透明。
     * ★ Bitmap 缓存复用，每次创建新的 Drawable 实例（Drawable 不可跨 View 共享）。
     */
    private fun loadBlackDrawable(
        context: android.content.Context?,
        module: XposedModule,
        stokeWidth: Int = 0
    ): Drawable? {
        val bitmap = getOrCreateBlackBitmap(module) ?: return null
        val cornerRadius = getCornerRadius(context)
        return RoundedClippingDrawable(bitmap, cornerRadius, stokeWidth)
    }

    /**
     * 获取或创建缓存的纯黑 Bitmap（512x512 ARGB_8888）。
     */
    private fun getOrCreateBlackBitmap(module: XposedModule): Bitmap? {
        cachedBlackBitmap?.let { if (!it.isRecycled) return it }
        synchronized(this) {
            cachedBlackBitmap?.let { if (!it.isRecycled) return it }
            return try {
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.BLACK)
                cachedBlackBitmap = bitmap
                bitmap
            } catch (e: Exception) {
                HookLogger.e(TAG, "Failed to create black bitmap: ${e.message}")
                null
            }
        }
    }


    /**
     * 解码背景文件为圆角裁剪 Drawable。
     */
    private fun decodeFile(
        file: File,
        context: android.content.Context?,
        module: XposedModule,
        stokeWidth: Int = 0
    ): Drawable? {
        if (file.extension.equals("gif", ignoreCase = true)) {
            return decodeAnimatedGif(file, context, module, stokeWidth)
        }

        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val srcW = options.outWidth
            val srcH = options.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
            val maxTargetSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 200f, displayMetrics
            ).toInt().coerceAtLeast(512)

            val sampleSize = calculateInSampleSize(srcW, srcH, maxTargetSize, maxTargetSize)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

            val cornerRadius = getCornerRadius(context)
            RoundedClippingDrawable(bitmap, cornerRadius, stokeWidth)
        } catch (e: Exception) {
            HookLogger.e(TAG, "Failed to decode background: ${e.message}")
            null
        }
    }

    private fun decodeAnimatedGif(
        file: File,
        context: android.content.Context?,
        module: XposedModule,
        stokeWidth: Int = 0
    ): Drawable? {
        return try {
            val source = ImageDecoder.createSource(file)
            var gifWidth = 0
            var gifHeight = 0
            val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                val size = info.size
                gifWidth = size.width
                gifHeight = size.height
                val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
                val maxTargetSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 200f, displayMetrics
                ).toInt().coerceAtLeast(512)
                val largest = maxOf(gifWidth, gifHeight)
                if (largest > maxTargetSize) {
                    val scale = maxTargetSize.toFloat() / largest
                    gifWidth = (gifWidth * scale).toInt().coerceAtLeast(1)
                    gifHeight = (gifHeight * scale).toInt().coerceAtLeast(1)
                    decoder.setTargetSize(gifWidth, gifHeight)
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            if (drawable is AnimatedImageDrawable) {
                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                drawable.start()
            }
            RoundedClippingAnimatedDrawable(drawable, gifWidth, gifHeight, getCornerRadius(context), stokeWidth)
        } catch (e: Exception) {
            HookLogger.e(TAG, "Failed to decode GIF background: ${e.message}")
            null
        }
    }

    /**
     * 获取圆角半径。
     * ★ 结果缓存，运行时圆角不会变，避免每次都做资源查找。
     */
    private fun getCornerRadius(context: android.content.Context?): Float {
        cachedCornerRadius?.let { return it }
        val radius = computeCornerRadius(context)
        cachedCornerRadius = radius
        return radius
    }

    private fun computeCornerRadius(context: android.content.Context?): Float {
        if (context != null) {
            try {
                val res = context.resources
                val dimenId = res.getIdentifier("island_radius", "dimen", "com.android.systemui")
                if (dimenId > 0) {
                    val radius = res.getDimension(dimenId)
                    if (radius > 0f) return radius
                }
            } catch (_: Exception) {}
        }
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 30f,
            android.content.res.Resources.getSystem().displayMetrics
        )
    }

    private fun calculateInSampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        var inSampleSize = 1
        while (maxOf(srcW / inSampleSize, srcH / inSampleSize) > maxOf(dstW, dstH)) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun setWeakCallback(drawable: Drawable, view: View) {
        drawable.callback = WeakDrawableCallback(view)
    }

    private fun newDrawableInstance(drawable: Drawable): Drawable {
        return if (drawable is RoundedClippingDrawable) drawable.newDrawable() else drawable
    }

    private class WeakDrawableCallback(view: View) : Drawable.Callback {
        private val view = WeakReference(view)

        override fun invalidateDrawable(who: Drawable) {
            view.get()?.invalidateDrawable(who)
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            view.get()?.scheduleDrawable(who, what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            view.get()?.unscheduleDrawable(who, what)
        }
    }

    fun onConfigChanged() {
        synchronized(this) {
            cachedDrawables.values.forEach { drawable ->
                if (drawable is RoundedClippingAnimatedDrawable) drawable.stop()
                drawable.callback = null
            }
            cachedDrawables.clear()
            lastFileModified.clear()
            lastConfigPath.clear()
            cachedBlackBitmap = null
        }
        // ★ 清除性能缓存，下次调用时重新计算
        cachedAnyCustomBg = null
        cachedBgAvailability.clear()
        cachedBlurEnabled.clear()
        managedContentTypes.clear()
        cachedCornerRadius = null
        stokeWidthFieldCache.clear()
        drawableFieldCache.clear()
        backgroundAlphaFieldCache.clear()
        scheduleUpdateMethodCache.clear()
        miBlurCompatClass = null
        setBlurModeMethod = null
        clearBlendMethod = null
        setBackgroundBlurModeMethod = null
        clearBionicsMaterialMethod = null
        clearBlurBlendEffectMethod = null
        commonUtilsInstance = null
    }

    /**
     * 圆角裁剪 Drawable 包装器。
     */
    private class RoundedClippingDrawable(
        val bitmap: Bitmap,
        private val cornerRadius: Float,
        private val stokeWidth: Int = 0
    ) : Drawable() {

        fun newDrawable(): RoundedClippingDrawable {
            return RoundedClippingDrawable(bitmap, cornerRadius, stokeWidth)
        }

        private val clipPath = Path()
        private val rect = RectF()
        private val srcRect = Rect()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }

        override fun draw(canvas: Canvas) {
            val bounds = getBounds()
            if (bounds.isEmpty || bitmap.isRecycled) return

            // SystemUI expands DynamicIslandBackgroundView's drawable bounds by stokeWidth,
            // while the content outline and glow aperture keep using the unexpanded island rect.
            val inset = stokeWidth.coerceAtLeast(0).toFloat()
            rect.set(
                bounds.left + inset,
                bounds.top + inset,
                bounds.right - inset,
                bounds.bottom - inset,
            )
            if (rect.isEmpty) return

            clipPath.reset()
            clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

            val scale = kotlin.math.max(
                rect.width() / bitmap.width.toFloat(),
                rect.height() / bitmap.height.toFloat()
            )
            val srcWidth = (rect.width() / scale).coerceAtMost(bitmap.width.toFloat())
            val srcHeight = (rect.height() / scale).coerceAtMost(bitmap.height.toFloat())
            val srcLeft = ((bitmap.width - srcWidth) / 2f).toInt().coerceAtLeast(0)
            val srcTop = ((bitmap.height - srcHeight) / 2f).toInt().coerceAtLeast(0)
            val srcRight = (srcLeft + srcWidth.toInt()).coerceAtMost(bitmap.width)
            val srcBottom = (srcTop + srcHeight.toInt()).coerceAtMost(bitmap.height)
            srcRect.set(srcLeft, srcTop, srcRight, srcBottom)

            val save = canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(bitmap, srcRect, rect, paint)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getIntrinsicWidth(): Int = bitmap.width
        override fun getIntrinsicHeight(): Int = bitmap.height
    }

    /**
     * 圆角裁剪 AnimatedImageDrawable 包装器，用于 GIF 背景。
     */
    private class RoundedClippingAnimatedDrawable(
        private val child: Drawable,
        private val sourceWidth: Int,
        private val sourceHeight: Int,
        private val cornerRadius: Float,
        private val stokeWidth: Int = 0
    ) : Drawable(), Drawable.Callback {

        private val clipPath = Path()
        private val rect = RectF()
        private val childRect = Rect()

        init {
            child.callback = this
            start()
        }

        fun start() {
            if (child is AnimatedImageDrawable) {
                child.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                child.start()
            }
        }

        fun stop() {
            if (child is AnimatedImageDrawable) child.stop()
        }

        fun release() {
            stop()
            child.callback = null
            callback = null
        }

        override fun draw(canvas: Canvas) {
            val bounds = getBounds()
            if (bounds.isEmpty) return

            val inset = stokeWidth.coerceAtLeast(0).toFloat()
            rect.set(
                bounds.left + inset,
                bounds.top + inset,
                bounds.right - inset,
                bounds.bottom - inset,
            )
            if (rect.isEmpty) return

            clipPath.reset()
            clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

            val save = canvas.save()
            canvas.clipPath(clipPath)

            val imageW = if (sourceWidth > 0) sourceWidth else child.intrinsicWidth
            val imageH = if (sourceHeight > 0) sourceHeight else child.intrinsicHeight
            if (imageW > 0 && imageH > 0) {
                val scale = kotlin.math.max(
                    rect.width() / imageW.toFloat(),
                    rect.height() / imageH.toFloat()
                )
                val drawW = imageW * scale
                val drawH = imageH * scale
                val left = rect.left + (rect.width() - drawW) / 2f
                val top = rect.top + (rect.height() - drawH) / 2f

                canvas.translate(left, top)
                canvas.scale(scale, scale)
                childRect.set(0, 0, imageW, imageH)
            } else {
                childRect.set(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            }
            child.bounds = childRect
            child.draw(canvas)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) { child.alpha = alpha }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        override fun setColorFilter(colorFilter: ColorFilter?) { child.colorFilter = colorFilter }
        override fun getIntrinsicWidth(): Int = if (sourceWidth > 0) sourceWidth else child.intrinsicWidth
        override fun getIntrinsicHeight(): Int = if (sourceHeight > 0) sourceHeight else child.intrinsicHeight
        override fun invalidateDrawable(who: Drawable) { invalidateSelf() }
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            scheduleSelf(what, `when`)
        }
        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            unscheduleSelf(what)
        }
    }
}