/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 模糊/材质模型：岛形态、材质类型、模糊配置与材质配置的数据结构。
 */

package com.liuxiansheng.lyricon.hyper.root.island.effects.blur

import android.graphics.Color

/** 岛形态。 */
internal enum class IslandType { SMALL, BIG, EXPAND }

/** 材质类型：default / 高斯模糊 / 高光玻璃 / 液态玻璃 / 柔光玻璃。 */
internal enum class MaterialType {
    DEFAULT,
    GAUSSIAN,
    HIGHLIGHT,
    LIQUID,
    SOFT;

    companion object {
        fun fromValue(value: String): MaterialType = when (value) {
            "gaussian" -> GAUSSIAN
            "highlight_glass" -> HIGHLIGHT
            "liquid_glass" -> LIQUID
            "soft_glass" -> SOFT
            else -> DEFAULT
        }
    }
}

/** 模糊配置（isActive=true 表示启用模糊）。 */
internal class BlurConfig(
    enabled: Boolean,
    val radius: Int,
    val blendColor: Int,
    val materialType: MaterialType = MaterialType.DEFAULT,
    val reflectionStrength: Int = 0,
    val highlight: Boolean = true,
) {
    val isActive = enabled
}

/**
 * 材质配置（字段对齐 pref_island_material_*_config JSON 结构，
 * 这样从 Hub 下载的预设能直接解析应用；softGlass 参数当前暂并入 darker/refraction）。
 */
internal data class MaterialConfig(
    val type: MaterialType,
    val blur: Int,
    val darker: Int,
    val refraction: Int,
    val edgeThickness: Int,
    val reflectionStrength: Int,
    val lightDirection: Int,
    val dispersion: Int,
    val blendColor: Int,
    val highlight: Boolean,
) {
    val isCustom get() = type != MaterialType.DEFAULT

    fun toBlurConfig() = BlurConfig(
        type == MaterialType.GAUSSIAN ||
            type == MaterialType.HIGHLIGHT ||
            type == MaterialType.LIQUID ||
            type == MaterialType.SOFT,
        blur,
        blendColor,
        type,
        reflectionStrength,
        highlight,
    )

    companion object {
        fun default() = MaterialConfig(
            MaterialType.DEFAULT,
            35,
            14,
            16,
            16,
            42,
            243,
            18,
            Color.TRANSPARENT,
            true,
        )
    }
}

internal data class MaterialConfigs(
    val small: MaterialConfig,
    val big: MaterialConfig,
    val expand: MaterialConfig,
) {
    val anyCustom get() = small.isCustom || big.isCustom || expand.isCustom

    fun forType(type: IslandType): MaterialConfig = when (type) {
        IslandType.SMALL -> small
        IslandType.BIG -> big
        IslandType.EXPAND -> expand
    }

    companion object {
        fun defaults(): MaterialConfigs {
            val value = MaterialConfig.default()
            return MaterialConfigs(value, value, value)
        }
    }
}

internal data class BlurConfigs(
    val small: BlurConfig,
    val big: BlurConfig,
    val expand: BlurConfig,
) {
    fun forType(type: IslandType): BlurConfig = when (type) {
        IslandType.SMALL -> small
        IslandType.BIG -> big
        IslandType.EXPAND -> expand
    }

    companion object {
        fun disabled(): BlurConfigs {
            val disabled = BlurConfig(false, 80, 0x20FFFFFF)
            return BlurConfigs(disabled, disabled, disabled)
        }
    }
}
