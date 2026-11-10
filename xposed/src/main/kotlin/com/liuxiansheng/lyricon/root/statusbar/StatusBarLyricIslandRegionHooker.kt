/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.statusbar

import android.graphics.Rect
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field

/** 观察 SystemUI 原生超级岛的提交区域（与小米状态栏图标容器同一数据源）。 */
internal object StatusBarLyricIslandRegionHooker {

    private const val TAG = "StatusBarLyricIslandRegionHooker"
    private const val CONTROLLER_CLASS =
        "com.android.systemui.statusbar.StatusBarIslandControllerImpl"
    private const val ISLAND_STATE_HANDLER_CLASS =
        "com.android.systemui.statusbar.StatusBarIslandControllerImpl\$IslandStateHandler"
    private var hooked = false

    /** 幂等安装；类/方法不存在时仅记日志，不影响状态栏歌词主体功能。 */
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        if (hooked) return
        runCatching {
            val stateHandlerClass = classLoader.loadClass(ISLAND_STATE_HANDLER_CLASS)
            val islandRectField = stateHandlerClass.getDeclaredField("islandRect").apply {
                isAccessible = true
            }
            val stateUpdateMethod = stateHandlerClass.declaredMethods.firstOrNull { m ->
                m.name == "islandUpdate" &&
                    m.parameterTypes.contentEquals(
                        arrayOf(Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    ) &&
                    m.returnType == Void.TYPE
            } ?: throw NoSuchMethodException("$ISLAND_STATE_HANDLER_CLASS.islandUpdate(boolean, boolean) 未找到")
            module.deoptimize(stateUpdateMethod)
            module.hook(stateUpdateMethod).intercept(IslandRegionUpdatedHook(islandRectField))
            hooked = true
            HookLogger.i(TAG, "原生超级岛区域 hook 已安装")
        }.onFailure { e ->
            HookLogger.w(TAG, "原生超级岛区域 hook 不可用（岛避让/时钟隐藏的 WHEN_ISLAND_PRESENT 降级为播放中策略）: ${e.message}")
        }
    }

    private fun publishRegion(owner: Any, rectField: Field) {
        val rect = runCatching { rectField.get(owner) as? Rect }.getOrNull()
        StatusBarLyricIslandRegionDispatcher.onNativeIslandRegionChanged(rect)
    }

    private class IslandRegionUpdatedHook(
        private val islandRectField: Field,
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            StatusBarLyricIslandRegionHooker.publishRegion(chain.thisObject, islandRectField)
            return result
        }
    }
}
