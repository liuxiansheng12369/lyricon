/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.hyper.root.island.hooks

import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandTextHookerSupport
import io.github.libxposed.api.XposedModule

/**
 * Super Island hook installer.
 *
 * Behavior lives in small hooker groups so the verified real-island, fake-view,
 * adapter/module, and width paths can be reviewed independently.
 */
internal object IslandTextHooker {

    private const val TAG = IslandTextHookerSupport.TAG
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val FAKE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
    private const val BASE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val EXPANDED_VIEW_CLASS =
        "miui.systemui.dynamicisland.view.DynamicIslandExpandedView"
    private const val ANIMATION_DELEGATE_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate"
    private const val EVENT_COORDINATOR_CLASS =
        "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
    private const val TEMPLATE_BUILDER_CLASS =
        "miui.systemui.dynamicisland.template.IslandTemplateBuilder"
    private const val ADAPTER_CLASS =
        "miui.systemui.dynamicisland.module.IslandModuleViewHolderAdapter"

    /**
     * 基线实验总开关：把这 5 个"替系统保歌词 / 防透明"的拦截整批关掉，
     * 让 18.3 系统按自己的 hide/show 与自愈流程跑，用来拿干净基线。
     * 代码保留，改回 true 即整批恢复。
     */
    private const val LEGACY_ISLAND_GUARDS = false

    /**
     * 1.2.36 租约模式：关屏交还 / 亮屏重挂。
     *
     * 旧做法（1.1.14 的"锁屏不清岛内容"）是硬拦系统的 hideView / hideIslandLayout，
     * 布局留住了，但系统改用 alpha 归零来隐藏 → 岛在、字也在、整层透明 = 空壳岛。
     * 租约模式不再抢布局：锁屏期间让系统按自己的流程收发，亮屏解锁后由
     * HookEntry 的亮屏/解锁监听延迟重挂歌词。代码保留，改回 false 即恢复旧拦截。
     */
    private const val LOCK_LEASE_MODE = true

    fun hook(module: XposedModule, cl: ClassLoader) {
        // 3.0.763+：自动重建 watchdog 独立启动（不依赖任何 installFeature 成功，
        // 之前挂在 attach installFeature 里，attach hook 未挂载时 watchdog 不启动
        // → 歌词被清后无检测 → 不再重新注入）。
        RealIslandHooker.startAutoRebuildWatchdog()

        installFeature("超级岛长按行为") {
            IslandLyricShareHooker.hook(module, cl)
        }

        installFeature("解除超级岛长度限制") {
            IslandWidthLimitHooker.hook(module, cl)
        }

        installFeature("真实岛") {
            val contentViewClass = cl.loadClass(CONTENT_VIEW_CLASS)

            contentViewClass.methods.filter { it.name == "updateBigIslandView" }.forEach { method ->
                module.deoptimize(method)
                module.hook(method).intercept(RealIslandHooker.UpdateBigIslandViewHook())
            }

            // 系统重算大岛宽度入口：每次 updateBigIslandViewWidth（触发 UpdateDynamicIslandWidth
            // 事件链）后做背景纠偏——媒体在播 + 窗口可见 + 背景被系统置 GONE（checkError 异常态）
            // 时恢复背景，消除"背景GONE+窗口VISIBLE"的大岛透明。与 gate 无关，无条件注册。
            // 方法可能声明在父类 DynamicIslandBaseContentView 且非 public，须递归 declaredMethods 查找。
            var widthCls: Class<*>? = contentViewClass
            var widthHooked = 0
            if (LEGACY_ISLAND_GUARDS) {
                while (widthCls != null) {
                    widthCls.declaredMethods
                        .filter {
                            it.name == "updateBigIslandViewWidth" && it.parameterTypes.isEmpty()
                        }
                        .forEach { method ->
                            method.isAccessible = true
                            module.deoptimize(method)
                            module.hook(method).intercept(
                                RealIslandHooker.WidthUpdateVisibilityGuardHook()
                            )
                            widthHooked++
                        }
                    widthCls = widthCls.superclass
                }
            }
            if (LEGACY_ISLAND_GUARDS) {
                if (widthHooked > 0) {
                    HookLogger.i(TAG, "宽度重算背景守卫 Hook 已初始化: hooks=$widthHooked")
                } else {
                    HookLogger.w(TAG, "未找到 updateBigIslandViewWidth() 无参方法")
                }
            } else {
                HookLogger.i(TAG, "基线实验：宽度重算背景守卫已停用")
            }

            // 背景状态监听：系统把岛背景层置 GONE 时，若窗口可见+媒体在播则自动恢复，
            // 覆盖非宽度重算来源的透明（与 gate 无关）。
            val bgClass = runCatching {
                cl.loadClass("miui.systemui.dynamicisland.DynamicIslandBackgroundView")
            }.getOrNull()
            if (LEGACY_ISLAND_GUARDS && bgClass != null) {
                bgClass.declaredMethods
                    .filter {
                        it.name == "setVisibility" &&
                                it.parameterTypes.size == 1 &&
                                it.parameterTypes[0] == Int::class.javaPrimitiveType
                    }
                    .forEach { method ->
                        method.isAccessible = true
                        module.deoptimize(method)
                        module.hook(method).intercept(
                            RealIslandHooker.BackgroundVisibilityGuardHook()
                        )
                        HookLogger.i(TAG, "背景状态监听 Hook 已初始化")
                    }
            } else if (!LEGACY_ISLAND_GUARDS) {
                HookLogger.i(TAG, "基线实验：背景状态监听已停用")
            } else {
                HookLogger.w(TAG, "未找到 DynamicIslandBackgroundView")
            }

            if (LEGACY_ISLAND_GUARDS) {
                contentViewClass.methods
                    .filter { it.name == "hideIslandLayout" || it.name == "showIslandLayout" }
                    .filter { it.parameterTypes.isEmpty() }
                    .forEach { method ->
                        module.deoptimize(method)
                        module.hook(method)
                            .intercept(RealIslandHooker.LayoutVisibilityHook(method.name))
                    }
            } else {
                HookLogger.i(TAG, "基线实验：hide/showIslandLayout 拦截已停用")
            }

        }

        // 1.1.14：锁屏别清内容——系统反复 hideView / hideIslandLayout 把媒体岛模块藏掉，
        // 亮屏后没人再 show。这里直接不让它清，内容一直挂着（锁屏期间窗口自己不可见，用户看不到）。
        installFeature("锁屏不清岛内容") {
            if (LOCK_LEASE_MODE) {
                HookLogger.i(TAG, "租约模式：锁屏保内容拦截已停用（改由亮屏重挂）")
                return@installFeature
            }
            val factoryCls = runCatching {
                cl.loadClass("miui.systemui.dynamicisland.template.IslandTemplateFactory")
            }.getOrNull()
            if (factoryCls != null) {
                var hideHooked = 0
                factoryCls.declaredMethods
                    .filter { it.name == "hideView" }
                    .forEach { method ->
                        method.isAccessible = true
                        module.deoptimize(method)
                        module.hook(method).intercept(RealIslandHooker.KeepContentHideViewHook())
                        hideHooked++
                    }
                HookLogger.i(TAG, "hideView 保内容 Hook 已初始化: hooks=$hideHooked")
            } else {
                HookLogger.w(TAG, "未找到 IslandTemplateFactory，hideView 无法挂")
            }

            val contentViewClass = runCatching { cl.loadClass(CONTENT_VIEW_CLASS) }.getOrNull()
            if (contentViewClass != null) {
                var layoutHooked = 0
                contentViewClass.methods
                    .filter { it.name == "hideIslandLayout" && it.parameterTypes.isEmpty() }
                    .forEach { method ->
                        module.deoptimize(method)
                        module.hook(method)
                            .intercept(RealIslandHooker.KeepContentHideLayoutHook())
                        layoutHooked++
                    }
                HookLogger.i(TAG, "hideIslandLayout 保内容 Hook 已初始化: hooks=$layoutHooked")
            }
        }

        // 3.0.754+ 根治"歌词回来瞬间被清走"：hook 系统 DynamicIslandEventCoordinator.checkError
        // 直接跳过（void），从源头截断"报错→恢复背景→hide/清内容→守卫恢复→又报错"死循环。
        installFeature("拦截系统 checkError 恢复循环") {
            if (!LEGACY_ISLAND_GUARDS) {
                HookLogger.i(TAG, "基线实验：checkError 拦截已停用")
                return@installFeature
            }
            val coordinatorClass = runCatching {
                cl.loadClass(EVENT_COORDINATOR_CLASS)
            }.getOrNull()
            val eventClass = runCatching {
                cl.loadClass("miui.systemui.dynamicisland.event.DynamicIslandEvent")
            }.getOrNull()
            if (coordinatorClass == null || eventClass == null) {
                HookLogger.w(
                    TAG,
                    "checkError 拦截跳过: coordinator=${coordinatorClass != null} event=${eventClass != null}"
                )
                return@installFeature
            }
            var hooked = 0
            coordinatorClass.declaredMethods
                .filter {
                    it.name == "checkError" &&
                            it.parameterTypes.size == 1 &&
                            it.parameterTypes[0] == eventClass
                }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(RealIslandHooker.CheckErrorBlockHook())
                    hooked++
                }
            if (hooked > 0) {
                HookLogger.i(TAG, "checkError 恢复循环拦截已挂载: hooks=$hooked")
            } else {
                HookLogger.w(TAG, "未找到 checkError(DynamicIslandEvent) 方法")
            }
        }

        // 3.0.769+ 新插件(18.3.2.57.0+)黑底拦截：dispatchUpdateEvent 的 densityChanged 分支
        // （岛缩回=密度变化→setUpdateOrder→重建→垫深色背景）。before 清掉该标记，
        // 跳过重建分支，缩回不再垫黑底；老插件无此 key，零副作用。
        installFeature("拦截新插件 densityChanged 重建") {
            if (!LEGACY_ISLAND_GUARDS) {
                HookLogger.i(TAG, "基线实验：densityChanged 拦截已停用")
                return@installFeature
            }
            val windowViewClass = runCatching {
                cl.loadClass("miui.systemui.dynamicisland.window.DynamicIslandWindowView")
            }.getOrNull()
            if (windowViewClass == null) {
                HookLogger.w(TAG, "densityChanged 拦截跳过: DynamicIslandWindowView 不存在")
                return@installFeature
            }
            var hooked = 0
            windowViewClass.declaredMethods
                .filter {
                    it.name == "dispatchUpdateEvent" && it.parameterTypes.size == 1
                }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(RealIslandHooker.DensityChangedBlockHook())
                    hooked++
                }
            if (hooked > 0) {
                HookLogger.i(TAG, "新插件 densityChanged 重建拦截已挂载: hooks=$hooked")
            } else {
                HookLogger.w(TAG, "未找到 dispatchUpdateEvent 方法")
            }
        }

        // 3.0.755+ 第三重保险：hook 全局 View.onAttachedToWindow——
        // 岛窗口根（island_content，全屏窗口根）重建 attach（锁屏/窗口重建/岛恢复）时
        // 媒体在播则立即重注入歌词（老词幕同款机制，补事件守卫覆盖不到的窗口重建场景）。
        installFeature("岛窗口根重建 attach 重注入") {
            val onAttached = android.view.View::class.java.getDeclaredMethod("onAttachedToWindow")
            onAttached.isAccessible = true
            module.deoptimize(onAttached)
            module.hook(onAttached).intercept(RealIslandHooker.ReattachLyricInjectorHook())
            HookLogger.i(TAG, "岛窗口根重建 attach 重注入已挂载")
            // 3.0.756+：启动自动重建 watchdog——检测歌词 View 被系统清掉时自动重注入（等效切换）
            RealIslandHooker.startAutoRebuildWatchdog()
        }

        installFeature("fake view 过渡") {
            val fakeViewClass = cl.loadClass(FAKE_CONTENT_VIEW_CLASS)
            fakeViewClass.methods
                .filter {
                    it.name == "setVisibility" &&
                            it.parameterTypes.size == 1 &&
                            it.declaringClass.name == FAKE_CONTENT_VIEW_CLASS
                }
                .forEach { method ->
                    module.deoptimize(method)
                    module.hook(method).intercept(
                        FakeIslandTransitionHooker.VisibilityHook()
                    )
                }

        }

        installFeature("应用返回 fake view") {
            val animationDelegateClass = cl.loadClass(ANIMATION_DELEGATE_CLASS)
            animationDelegateClass.declaredMethods
                .filter {
                    it.name == "fakeViewToBigIsland" &&
                            it.parameterTypes.size == 2 &&
                            it.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(
                        FakeIslandTransitionHooker.AppReturnToBigIslandHook()
                    )
                }

            animationDelegateClass.declaredMethods
                .filter {
                    it.name == "fakeViewToExpanded" &&
                            it.parameterTypes.size == 2 &&
                            it.parameterTypes[0].name == CONTENT_VIEW_CLASS &&
                            it.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(
                        FakeIslandTransitionHooker.ExpandedViewTransitionHook()
                    )
                }

            cl.loadClass(EVENT_COORDINATOR_CLASS).declaredMethods
                .filter {
                    it.name == "updateFreeformFakeView" &&
                            it.parameterTypes.size == 3 &&
                            it.parameterTypes[0].name == FAKE_CONTENT_VIEW_CLASS &&
                            it.parameterTypes[1].name == CONTENT_VIEW_CLASS
                }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(
                        FakeIslandTransitionHooker.FreeformFakeViewCallbackHook()
                    )
                }

        }

        installFeature("模块首次绑定") {
            val adapterClass = cl.loadClass(ADAPTER_CLASS)

            adapterClass.declaredMethods
                .filter {
                    it.name == "bindData" &&
                            it.parameterTypes.size == 2 &&
                            it.parameterTypes[0] == String::class.java
                }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(IslandModuleRestoreHooker.AdapterBindDataHook())
                }
        }

        installFeature("模块恢复") {
            cl.loadClass(TEMPLATE_BUILDER_CLASS).declaredMethods
                .filter { it.name == "updateModuleView" && it.parameterTypes.size == 3 }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(IslandModuleRestoreHooker.UpdateModuleViewHook())
                }

            cl.loadClass(ADAPTER_CLASS).declaredMethods
                .filter { it.name == "updateView" && it.parameterTypes.size == 3 }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(IslandModuleRestoreHooker.AdapterUpdateViewHook())
                }
        }
    }


    private inline fun installFeature(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: ClassNotFoundException) {
            HookLogger.w(TAG, "跳过不支持的 $name Hook: reason=${e.message}")
        } catch (e: Exception) {
            HookLogger.e(TAG, "安装 $name Hook 失败", e)
        }
    }
}
