/*
 * This file is based on HyperIsland by 1812z.
 * Original: https://github.com/1812z/HyperIsland
 * Ported to Lyricon V3. Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root.island.upstream

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import java.util.Collections
import java.util.WeakHashMap

/**
 * 高斯模糊 = 系统清透磨砂（对齐老词幕 IslandBlurController 做法）。
 *
 * 关键坑（老词幕实测）：把系统 BackgroundBlurDrawable 写入外形画布
 * DynamicIslandBackgroundView 的私有 drawable 字段【不生效，只透明不模糊】；
 * 必须 setBackground 到真实状态视图（big_island_view / expanded_view），
 * 由系统 RenderThread 渲染。
 *
 * 磨砂质感：系统实时模糊 + 40% 白/黑底色 tint（浅色叠白、深色叠黑），
 * 避免"纯透明无底色"的假透明效果。
 */
internal object GlassStateBlurController {
    private const val TAG = "HyperIsland[GlassBlur]"

    private class Methods(
        val setRadius: java.lang.reflect.Method,
        val setCorner: java.lang.reflect.Method,
        val setColor: java.lang.reflect.Method,
        val cornerArity: Int,
    )

    private val attached = Collections.synchronizedMap(WeakHashMap<View, Drawable>())
    private val methodsMap = Collections.synchronizedMap(WeakHashMap<Drawable, Methods>())
    private val logged = Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    /** 把系统模糊设为该状态视图的 background；已挂载则只更新参数。 */
    fun applyToStateView(
        view: View,
        radiusPx: Int,
        cornerDp: Float,
        color: Int,
        fallbackTint: Boolean = true,
    ): Boolean {
        // 毛玻璃总开关关闭：高斯类系统模糊也不再挂（否则关掉后仍有一层模糊）。
        if (!UpstreamConfig.getBoolean("pref_island_material_enabled", false)) return false
        var blur = attached[view]
        if (blur == null) {
            blur = createBlur(view) ?: return false
            attached[view] = blur
            view.background = blur
        }
        val m = methodsMap[blur] ?: return false
        return runCatching {
            val density = view.resources.displayMetrics.density
            val corner = (cornerDp * density).coerceAtMost(view.height / 2f)
            val tint = if (fallbackTint && (color == 0 || Color.alpha(color) == 0)) {
                tintFor(view)
            } else {
                color
            }
            m.setColor.invoke(blur, tint)
            if (m.cornerArity == 4) {
                m.setCorner.invoke(blur, corner, corner, corner, corner)
            } else {
                m.setCorner.invoke(blur, corner)
            }
            m.setRadius.invoke(blur, radiusPx) // 最后激活
            if (logged.put(view, true) == null) {
                HookLogger.i(TAG, "glass blur setBackground on ${id(view)} " +
                    "corner=$corner radius=$radiusPx tint=${Integer.toHexString(tint)}")
            }
            true
        }.onFailure {
            HookLogger.w(TAG, "apply failed on ${id(view)}: ${it.message}")
        }.getOrDefault(false)
    }

    fun removeStateView(view: View) {
        attached.remove(view)
        logged.remove(view)
        runCatching { view.background = null }
    }

    /** 配置变更/关闭时释放所有已挂载的磨砂，恢复默认背景 */
    fun removeAll(): List<View> {
        val views = synchronized(attached) { attached.keys.toList() }
        views.forEach { removeStateView(it) }
        return views
    }

    /** 磨砂玻璃底色：浅色叠 40% 白，深色叠 40% 黑。 */
    private fun tintFor(view: View): Int {
        val night = (view.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val a = (255 * 0.40f).toInt()
        return if (night) Color.argb(a, 0, 0, 0)
        else Color.argb(a, 255, 255, 255)
    }

    private fun id(v: View): String =
        runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()
            ?: v.javaClass.simpleName

    private fun cfail(view: View, why: String): Drawable? {
        HookLogger.w(TAG, "[CREATE_FAIL] ${id(view)} attached=${view.isAttachedToWindow}" +
            " w=${view.width} h=${view.height} : $why")
        return null
    }

    private fun createBlur(view: View): Drawable? {
        val getVR = view.javaClass.methods.firstOrNull { it.name == "getViewRootImpl" }
            ?: return cfail(view, "no getViewRootImpl")
        val vr = runCatching { getVR.invoke(view) }.getOrNull()
            ?: return cfail(view, "viewRoot null")
        val createBlur = vr.javaClass.methods
            .firstOrNull { it.name == "createBackgroundBlurDrawable" }
            ?: return cfail(view, "no createBackgroundBlurDrawable")
        val blur = runCatching { createBlur.invoke(vr) as? Drawable }.getOrNull()
            ?: return cfail(view, "createBlur returned null")
        val c = blur.javaClass
        val setRadius = c.methods.firstOrNull { it.name == "setBlurRadius" }
        val s4 = c.methods.firstOrNull {
            it.name == "setCornerRadius" && it.parameterTypes.size == 4
        }
        val s1 = c.methods.firstOrNull {
            it.name == "setCornerRadius" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Float::class.javaPrimitiveType
        }
        val setCorner = s4 ?: s1
        val setColor = c.methods.firstOrNull { it.name == "setColor" }
        if (setRadius == null || setCorner == null || setColor == null) {
            return cfail(view, "missing radius/corner/color methods")
        }
        methodsMap[blur] = Methods(
            setRadius, setCorner, setColor, if (s4 != null) 4 else 1
        )
        HookLogger.i(TAG, "create OK on ${id(view)} arity=${if (s4 != null) 4 else 1}")
        return blur
    }

    /** 圆角兜底：island_radius 资源，找不到用 32dp。 */
    fun resolveCornerRadius(view: View): Float = runCatching {
        val id = view.resources.getIdentifier("island_radius", "dimen", "com.android.systemui")
        if (id == 0) null else view.resources.getDimension(id)
    }.getOrNull() ?: TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        32f,
        view.resources.displayMetrics,
    )
}
