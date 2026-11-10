/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 系统实时毛玻璃渲染器：反射调用 ViewRootImpl.createBackgroundBlurDrawable()
 * 创建系统模糊 drawable，设置模糊半径/圆角/混合色，并用 ClippedBlurDrawable
 * 裁剪成岛圆角。
 *
 * 高光玻璃（HIGHLIGHT）/ 液态玻璃（LIQUID）→ 自绘引擎（LiquidGlassDrawable）：
 *   RuntimeShader 折射/边缘高光 + 屏幕采样 + RenderNode 硬件模糊，
 *   全程不依赖小米 Bionics（setMiGlass），Android 17 上不会返黑。
 * 高斯模糊（GAUSSIAN）→ 原系统 BackgroundBlurDrawable 路径。
 */

package com.liuxiansheng.lyricon.hyper.root.island.effects.blur

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import com.liuxiansheng.lyricon.hyper.root.island.effects.blur.liquidglass.LiquidGlassConfig
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * 系统实时毛玻璃渲染器（V3 自研）。
 */
internal class NativeBlurRenderer {

    fun create(
        view: View,
        type: IslandType,
        materialType: MaterialType,
        bgBitmap: Bitmap? = null,
        glassConfig: LiquidGlassConfig = LiquidGlassConfig.disabled(),
    ): OwnedBlur? {
        val viewRoot = runCatching {
            SystemUiReflection.findMethod(view.javaClass, "getViewRootImpl")?.invoke(view)
        }.getOrNull() ?: return null
        val drawable = runCatching {
            val method = SystemUiReflection.findMethod(viewRoot.javaClass, "createBackgroundBlurDrawable")
                ?: return@runCatching null
            method.invoke(viewRoot) as? Drawable
        }.getOrNull() ?: return null

        return runCatching {
            val drawableClass = drawable.javaClass
            val methods = BlurDrawableMethods(
                setRadius = SystemUiReflection.findMethod(
                    drawableClass,
                    "setBlurRadius",
                    Int::class.javaPrimitiveType!!,
                ) ?: return@runCatching null,
                setCornerRadius = SystemUiReflection.findMethod(
                    drawableClass,
                    "setCornerRadius",
                    Float::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                ) ?: return@runCatching null,
                setColor = SystemUiReflection.findMethod(
                    drawableClass,
                    "setColor",
                    Int::class.javaPrimitiveType!!,
                ) ?: return@runCatching null,
            )
            val strokeWidth = strokeWidth(view)
            // 对齐上游 HyperIsland：所有材质统一走系统 BackgroundBlurDrawable 通道
            // （高光/液态/柔光只在 overlay 上做轻量渐变表达，不启用自绘 LiquidGlass 引擎）。
            val bgDrawable = if (bgBitmap != null) {
                BitmapDrawable(view.resources, bgBitmap).apply {
                    gravity = Gravity.FILL
                    setBounds(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
                }
            } else {
                GradientDrawable().apply { setColor(0x00000000) }
            }
            val overlay = GradientDrawable().apply { setColor(0x00000000) }
            val layer = LayerDrawable(arrayOf(bgDrawable, drawable, overlay))
            layer.setBounds(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
            for (i in 0 until layer.numberOfLayers) layer.setLayerGravity(i, Gravity.FILL)
            val clippedDrawable = ClippedBlurDrawable(layer, strokeWidth)
            OwnedBlur(
                drawable = clippedDrawable,
                systemDrawable = drawable,
                clippedDrawable = clippedDrawable,
                type = type,
                methods = methods,
                materialType = materialType,
                overlayDrawable = overlay,
                bgBitmap = bgBitmap,
            )
        }.getOrNull()
    }

    /** 高光/液态/柔光叠加层：玻璃反光/柔和光晕，高斯时全透明。 */
    private fun updateOverlay(d: GradientDrawable, config: BlurConfig) {
        val strength = config.reflectionStrength.coerceIn(0, 100)
        d.setGradientType(GradientDrawable.LINEAR_GRADIENT)
        when (config.materialType) {
            MaterialType.HIGHLIGHT -> {
                val a = (strength * 255 / 100).coerceIn(0, 255)
                d.setColors(intArrayOf(Color.argb(a, 255, 255, 255), Color.TRANSPARENT))
            }
            MaterialType.LIQUID -> {
                // 液态：更强高光 + 整体微白
                val a = ((strength * 2 / 100 + 60) * 255 / 100).coerceIn(0, 255)
                d.setColors(intArrayOf(Color.argb(a, 255, 255, 255), Color.argb(a / 3, 255, 255, 255)))
            }
            MaterialType.SOFT -> {
                // 柔光：均匀柔和半透明白
                val a = (strength * 255 / 100).coerceIn(30, 120)
                d.setColor(Color.argb(a, 255, 255, 255))
            }
            else -> d.setColor(0x00000000) // GAUSSIAN / DEFAULT：无叠加
        }
    }

    fun update(view: View, owned: OwnedBlur, config: BlurConfig, glassConfig: LiquidGlassConfig? = null) {
        // 对齐上游：统一系统 BackgroundBlurDrawable 更新路径（radius/corner/blend + overlay 轻量渐变）
        if (owned.materialType != config.materialType) {
            owned.materialType = config.materialType
            owned.overlayDrawable?.let { updateOverlay(it, config) }
        }
        val radius = resolveCornerRadius(view)
        if (owned.cornerRadius != radius) {
            owned.cornerRadius = radius
            owned.clippedDrawable.setCornerRadius(radius)
            owned.methods.setCornerRadius.invoke(
                owned.systemDrawable,
                radius,
                radius,
                radius,
                radius,
            )
        }
        if (owned.blendColor != config.blendColor) {
            owned.blendColor = config.blendColor
            owned.methods.setColor.invoke(owned.systemDrawable, config.blendColor)
        }
        if (owned.blurRadius != config.radius) {
            owned.blurRadius = config.radius
            // Radius 激活 RenderThread，先初始化其他属性再设半径。
            owned.methods.setRadius.invoke(owned.systemDrawable, config.radius)
        }
    }

    private fun resolveCornerRadius(view: View): Float = runCatching {
        val id = view.resources.getIdentifier("island_radius", "dimen", "com.android.systemui")
        if (id == 0) null else view.resources.getDimension(id)
    }.getOrNull() ?: TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        32f,
        view.resources.displayMetrics,
    )

    fun strokeWidth(view: View): Int = runCatching {
        (SystemUiReflection.findMethod(view.javaClass, "getStokeWidth")?.invoke(view) as? Int) ?: 0
    }.getOrDefault(0).coerceAtLeast(0)
}

internal class OwnedBlur(
    val drawable: Drawable,
    val systemDrawable: Drawable,
    val clippedDrawable: ClippedBlurDrawable,
    var type: IslandType,
    internal val methods: BlurDrawableMethods,
    var active: Boolean = false,
    var materialType: MaterialType = MaterialType.DEFAULT,
    val overlayDrawable: GradientDrawable? = null,
    val bgBitmap: Bitmap? = null,
) {
    var cornerRadius = Float.NaN
    var blurRadius = Int.MIN_VALUE
    var blendColor = Int.MIN_VALUE

    fun updateEffectRadius(radius: Int) {
        methods.setRadius.invoke(systemDrawable, radius)
        blurRadius = radius
    }

    fun release() {
        clippedDrawable.release()
    }
}

internal class WeakViewDrawableCallback(view: View) : Drawable.Callback {
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

internal data class BlurDrawableMethods(
    val setRadius: Method,
    val setCornerRadius: Method,
    val setColor: Method,
)
