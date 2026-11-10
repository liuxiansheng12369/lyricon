/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 状态栏背景亮度反色检测器：
 * 系统（DarkIconDispatcher）会根据状态栏背景明暗自动决定状态栏图标反色（背景亮→深色图标，暗→浅色图标）。
 * 歌词"黑白背景反色"直接复用这一判断：Hook DarkIconDispatcher 的反色方法，拿到"状态栏背景是否亮"，
 * 亮背景→歌词用深色字，暗背景→用浅色字（白字）。
 * 魅族等魔改 SystemUI 若无该类，回退系统深色/浅色模式（UI_MODE_NIGHT）近似判断，跨系统兜底。
 */

package com.liuxiansheng.lyricon.hyper.root.statusbar

import android.content.Context
import android.content.res.Configuration
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger

/** 状态栏背景亮度反色检测器 */
object StatusBarInvertTracker : XposedInterface.Hooker {

    private const val TAG = "StatusBarInvert"

    /** true=状态栏背景亮（图标深色）→ 歌词用深色字；false=背景暗 → 用浅色字 */
    @Volatile
    var isBarLight = false
        private set

    /** 是否成功 hook 到系统反色判断（成功才用真实背景亮度） */
    @Volatile
    private var hooked = false

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        if (hooked) return
        // 候选实现类（HyperOS/澎湃 保留 AOSP statusbar.phone 包结构）
        val implNames = arrayOf(
            "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl",
            "com.android.systemui.statusbar.DarkIconDispatcherImpl",
            "com.android.systemui.statusbar.DarkIconDispatcher"
        )
        var clazz: Class<*>? = null
        for (name in implNames) {
            clazz = runCatching { classLoader.loadClass(name) }.getOrNull()
            if (clazz != null) break
        }
        if (clazz == null) {
            HookLogger.i(TAG, "DarkIconDispatcher 类未找到，回退深色模式判断")
            return
        }
        // 候选反色判断方法（返回 Boolean），命中即 hook
        val candidates = arrayOf(
            "shouldIconsBeDark", "isIconsDark", "getIconsDark", "areIconsDark", "isIconDark"
        )
        for (name in candidates) {
            val m = runCatching { clazz.getDeclaredMethod(name) }.getOrNull()
            if (m != null && m.returnType == Boolean::class.java) {
                module.hook(m).intercept(this)
                hooked = true
                HookLogger.i(TAG, "hook $name OK → 跟随状态栏背景亮度反色")
                return
            }
        }
        // HyperOS 关键信号：applyDarkIntensity(float darkIntensity)。
        // darkIntensity 高=背景暗→图标浅色；低=背景亮→图标深色。直接驱动歌词反色。
        val applyIntensity = runCatching {
            clazz.declaredMethods.firstOrNull {
                it.name == "applyDarkIntensity" && it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].name == "float"
            }
        }.getOrNull()
        if (applyIntensity != null) {
            module.hook(applyIntensity).intercept(IntensityHooker())
            hooked = true
            HookLogger.i(TAG, "hook applyDarkIntensity OK → 跟随状态栏背景亮度反色")
            return
        }
        HookLogger.i(TAG, "未找到反色判断方法，回退深色模式判断")
    }

    /** 拦 applyDarkIntensity(float)：暗强度 ≥0.5 → 背景暗 → 歌词浅字；<0.5 → 背景亮 → 深字 */
    private class IntensityHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val arg = chain.args?.firstOrNull()
            (arg as? Float)?.let { StatusBarInvertTracker.markBarLight(it) }
            return chain.proceed()
        }
    }

    /** 由 applyDarkIntensity 回调：HyperOS 语义为"暗色强度高=背景亮(图标需深色)"→ 歌词深字；
     * 强度低=背景暗(图标浅色)→ 歌词浅字。已实测校正（首次按常规语义设反了） */
    internal fun markBarLight(intensity: Float) {
        isBarLight = intensity > 0.5f
    }

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        (result as? Boolean)?.let { isBarLight = it }
        return result
    }

    /** 是否"亮背景"：优先真实背景亮度（hook 成功），否则回退系统深色/浅色模式 */
    fun isLight(context: Context?): Boolean {
        if (hooked) return isBarLight
        if (context == null) return true
        val night = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return !night
    }
}
