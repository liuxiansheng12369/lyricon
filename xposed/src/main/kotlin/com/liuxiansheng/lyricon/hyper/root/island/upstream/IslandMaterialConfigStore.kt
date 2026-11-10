/*
 * This file is based on HyperIsland by 1812z.
 * Original: https://github.com/1812z/HyperIsland
 * Ported to Lyricon V3. Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root.island.upstream

import android.graphics.Color
import com.liuxiansheng.lyricon.hyper.root.island.upstream.BlurConfig
import com.liuxiansheng.lyricon.hyper.root.island.upstream.BlurConfigs
import com.liuxiansheng.lyricon.hyper.root.island.upstream.GlassState
import com.liuxiansheng.lyricon.hyper.root.island.upstream.GlassStates
import com.liuxiansheng.lyricon.hyper.root.island.upstream.IslandType
import com.liuxiansheng.lyricon.hyper.root.island.upstream.MaterialConfig
import com.liuxiansheng.lyricon.hyper.root.island.upstream.MaterialConfigs
import com.liuxiansheng.lyricon.hyper.root.island.upstream.MaterialType
import com.liuxiansheng.lyricon.hyper.root.island.upstream.SoftGlassConfig
import org.json.JSONObject

/** Reads preferences and exposes one immutable material snapshot to renderers. */
internal class IslandMaterialConfigStore {
    @Volatile
    private var blurConfigs = BlurConfigs.disabled()

    @Volatile
    private var materialConfigs = MaterialConfigs.defaults()

    @Volatile
    private var glassConfig = LiquidGlassConfig.disabled()

    @Volatile
    private var glassStates = GlassStates.disabled()

    @Volatile
    var revision: Int = 0
        private set

    @Volatile
    var anyBlurEnabled: Boolean = false
        private set

    @Volatile
    var anyMaterialEnabled: Boolean = false
        private set

    @Volatile
    private var materialSchemaEnabled: Boolean = false

    fun reload() {
        blurConfigs = BlurConfigs(
            small = readBlurConfig(KEY_SMALL_ENABLED, KEY_SMALL_RADIUS, KEY_SMALL_COLOR),
            big = readBlurConfig(KEY_BIG_ENABLED, KEY_BIG_RADIUS, KEY_BIG_COLOR),
            expand = readBlurConfig(KEY_EXPAND_ENABLED, KEY_EXPAND_RADIUS, KEY_EXPAND_COLOR),
        )
        anyBlurEnabled = IslandType.entries.any { blurConfigs.forType(it).isActive }
        val legacyGlassEnabled = UpstreamConfig.getBoolean(KEY_GLASS_ENABLED, false)
        val legacyRefractionEnabled = UpstreamConfig.getBoolean(KEY_GLASS_TRUE_REFRACTION, false)
        glassStates = GlassStates(
            small = readGlassState(
                KEY_GLASS_SMALL_ENABLED,
                KEY_REFRACTION_SMALL_ENABLED,
                legacyGlassEnabled,
                legacyRefractionEnabled,
            ),
            big = readGlassState(
                KEY_GLASS_BIG_ENABLED,
                KEY_REFRACTION_BIG_ENABLED,
                legacyGlassEnabled,
                legacyRefractionEnabled,
            ),
            expand = readGlassState(
                KEY_GLASS_EXPAND_ENABLED,
                KEY_REFRACTION_EXPAND_ENABLED,
                legacyGlassEnabled,
                legacyRefractionEnabled,
            ),
        )
        glassConfig = LiquidGlassConfig(
            enabled = glassStates.anyEnabled && anyBlurEnabled,
            edgeWidth = UpstreamConfig.getInt(KEY_GLASS_EDGE_WIDTH, 16).coerceIn(4, 40) / 100f,
            refraction = UpstreamConfig.getInt(KEY_GLASS_REFRACTION, 16).coerceIn(0, 40) / 100f,
            highlight = UpstreamConfig.getInt(KEY_GLASS_HIGHLIGHT, 42).coerceIn(0, 100) / 100f,
            shadow = UpstreamConfig.getInt(KEY_GLASS_SHADOW, 14).coerceIn(0, 100) / 100f,
            lightDirection = UpstreamConfig.getInt(KEY_GLASS_LIGHT_DIRECTION, 243)
                .coerceIn(0, 359),
            dispersion = UpstreamConfig.getInt(KEY_GLASS_DISPERSION, 18)
                .coerceIn(0, 100) / 100f,
            gyroscope = UpstreamConfig.getBoolean(KEY_GLASS_GYROSCOPE, true),
            hdrHighlight = UpstreamConfig.getBoolean(KEY_GLASS_HDR_HIGHLIGHT, false),
            trueRefraction = glassStates.anyRefractionEnabled,
            captureFps = UpstreamConfig.getInt(KEY_GLASS_CAPTURE_FPS, 20).coerceIn(1, 90),
            captureScale = UpstreamConfig.getInt(KEY_GLASS_CAPTURE_QUALITY, 30)
                .coerceIn(10, 100) / 100f,
        )
        materialSchemaEnabled = UpstreamConfig.contains(KEY_MATERIAL_BIG)
        if (materialSchemaEnabled) {
            val big = readMaterialConfig(KEY_MATERIAL_BIG)
            val small = if (UpstreamConfig.getBoolean(KEY_MATERIAL_SMALL_FOLLOW_BIG, true)) {
                big
            } else {
                readMaterialConfig(KEY_MATERIAL_SMALL)
            }
            val expand = if (UpstreamConfig.getBoolean(KEY_MATERIAL_EXPAND_FOLLOW_BIG, true)) {
                big
            } else {
                readMaterialConfig(KEY_MATERIAL_EXPAND)
            }
            materialConfigs = MaterialConfigs(small, big, expand)
            blurConfigs = BlurConfigs(
                small.toBlurConfig(),
                big.toBlurConfig(),
                expand.toBlurConfig(),
            )
            anyBlurEnabled = IslandType.entries.any { blurConfigs.forType(it).isActive }
            anyMaterialEnabled = materialConfigs.anyCustom
        } else {
            materialConfigs = MaterialConfigs.fromLegacy(blurConfigs, glassStates)
            anyMaterialEnabled = anyBlurEnabled
        }
        revision++
    }

    fun materialFor(type: IslandType): MaterialConfig = materialConfigs.forType(type)

    /**
     * 是否有任一档材质是「系统材质路径」（高斯=系统背景模糊 / 柔光=系统 Bionics）。
     * 清黑罩时若启用这类材质必须保留系统模糊 + Bionics（不可 disableBlurAndClearBlend），
     * 否则高斯/柔光被永久关闭显示透明（自绘的高光/液态不受影响）。
     */
    fun hasSystemMaterialEnabled(): Boolean =
        IslandType.entries.any {
            val m = materialConfigs.forType(it)
            m.type == MaterialType.GAUSSIAN || m.type == MaterialType.SOFT
        }

    fun blurFor(type: IslandType): BlurConfig = blurConfigs.forType(type)

    fun liquidGlassFor(type: IslandType): LiquidGlassConfig {
        if (materialSchemaEnabled) {
            val material = materialFor(type)
            // 高斯/高光/液态都走自绘液态外壳（自己截屏渲染，不依赖系统采样，稳定出玻璃）。
            // 柔光（SOFT）走系统 Bionics 原生玻璃，不进自绘引擎。
            val glassEnabled = material.type == MaterialType.HIGHLIGHT ||
                material.type == MaterialType.LIQUID ||
                material.type == MaterialType.GAUSSIAN
            return glassConfig.copy(
                enabled = glassEnabled && blurFor(type).isActive,
                edgeWidth = material.edgeThickness.coerceIn(4, 40) / 100f,
                refraction = material.refraction / 100f,
                highlight = if (material.highlight) {
                    material.reflectionStrength.coerceIn(0, 200) / 100f
                } else {
                    0f
                },
                shadow = material.darker / 100f,
                lightDirection = material.lightDirection,
                dispersion = material.dispersion / 100f,
                trueRefraction = material.type == MaterialType.LIQUID,
            )
        }
        val state = glassStates.forType(type)
        return glassConfig.copy(
            enabled = state.enabled && blurFor(type).isActive,
            trueRefraction = state.refractionEnabled && blurFor(type).isActive,
        )
    }

    private fun readMaterialConfig(key: String): MaterialConfig {
        val json = runCatching { JSONObject(UpstreamConfig.getString(key)) }.getOrNull()
            ?: return MaterialConfig.default()
        fun int(name: String, fallback: Int, min: Int, max: Int) =
            json.optInt(name, fallback).coerceIn(min, max)
        val hasSoftSchema = json.optInt("softSchema", 0) >= 2
        val color = parseColor(json.optString("blendColor", "#0F0F0F"))
        val type = MaterialType.fromValue(json.optString("type", "default"))
        val opacity = if (!hasSoftSchema && type == MaterialType.SOFT) {
            0
        } else {
            int("blendOpacity", 0, 0, 100)
        }
        val blur = int("blur", 35, 0, 100)
        val blendColor = Color.argb(
            opacity * 255 / 100,
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
        val highlight = json.optBoolean("highlight", true)
        return MaterialConfig(
            type = type,
            blur = blur,
            softGlass = SoftGlassConfig.fromJson(json, blur, blendColor, highlight),
            darker = int("darker", 14, 0, 100),
            refraction = int("refraction", 16, 0, 40),
            edgeThickness = int("edgeThickness", 16, 4, 40),
            reflectionStrength = int("reflectionStrength", 42, 0, 100),
            lightDirection = int("lightDirection", 243, 0, 359),
            dispersion = int("dispersion", 18, 0, 100),
            blendColor = blendColor,
            highlight = highlight,
        )
    }

    private fun readGlassState(
        glassKey: String,
        refractionKey: String,
        legacyGlassEnabled: Boolean,
        legacyRefractionEnabled: Boolean,
    ): GlassState {
        val enabled = if (UpstreamConfig.contains(glassKey)) {
            UpstreamConfig.getBoolean(glassKey, false)
        } else {
            legacyGlassEnabled
        }
        val refractionEnabled = if (UpstreamConfig.contains(refractionKey)) {
            UpstreamConfig.getBoolean(refractionKey, false)
        } else {
            legacyRefractionEnabled
        }
        return GlassState(enabled, enabled && refractionEnabled)
    }

    private fun readBlurConfig(enabledKey: String, radiusKey: String, colorKey: String) =
        BlurConfig(
            enabled = UpstreamConfig.getBoolean(enabledKey, false),
            radius = UpstreamConfig.getInt(radiusKey, DEFAULT_RADIUS).coerceIn(0, 100),
            blendColor = parseColor(UpstreamConfig.getString(colorKey)),
        )

    private fun parseColor(value: String): Int {
        if (value.isBlank()) return DEFAULT_BLEND_COLOR
        return runCatching { Color.parseColor(value.trim()) }.getOrDefault(DEFAULT_BLEND_COLOR)
    }

    private companion object {
        const val KEY_SMALL_ENABLED = "pref_island_blur_small_enabled"
        const val KEY_SMALL_RADIUS = "pref_island_blur_small_radius"
        const val KEY_SMALL_COLOR = "pref_island_blur_small_color"
        const val KEY_BIG_ENABLED = "pref_island_blur_big_enabled"
        const val KEY_BIG_RADIUS = "pref_island_blur_big_radius"
        const val KEY_BIG_COLOR = "pref_island_blur_big_color"
        const val KEY_EXPAND_ENABLED = "pref_island_blur_expand_enabled"
        const val KEY_EXPAND_RADIUS = "pref_island_blur_expand_radius"
        const val KEY_EXPAND_COLOR = "pref_island_blur_expand_color"
        const val KEY_GLASS_ENABLED = "pref_island_glass_enabled"
        const val KEY_GLASS_SMALL_ENABLED = "pref_island_glass_small_enabled"
        const val KEY_GLASS_BIG_ENABLED = "pref_island_glass_big_enabled"
        const val KEY_GLASS_EXPAND_ENABLED = "pref_island_glass_expand_enabled"
        const val KEY_GLASS_EDGE_WIDTH = "pref_island_glass_edge_width"
        const val KEY_GLASS_REFRACTION = "pref_island_glass_refraction"
        const val KEY_GLASS_HIGHLIGHT = "pref_island_glass_highlight"
        const val KEY_GLASS_SHADOW = "pref_island_glass_shadow"
        const val KEY_GLASS_LIGHT_DIRECTION = "pref_island_glass_light_direction"
        const val KEY_GLASS_DISPERSION = "pref_island_glass_dispersion"
        const val KEY_GLASS_GYROSCOPE = "pref_island_glass_gyroscope"
        const val KEY_GLASS_HDR_HIGHLIGHT = "pref_island_glass_hdr_highlight"
        const val KEY_GLASS_TRUE_REFRACTION = "pref_island_glass_true_refraction"
        const val KEY_REFRACTION_SMALL_ENABLED = "pref_island_refraction_small_enabled"
        const val KEY_REFRACTION_BIG_ENABLED = "pref_island_refraction_big_enabled"
        const val KEY_REFRACTION_EXPAND_ENABLED = "pref_island_refraction_expand_enabled"
        const val KEY_GLASS_CAPTURE_FPS = "pref_island_glass_capture_fps"
        const val KEY_GLASS_CAPTURE_QUALITY = "pref_island_glass_capture_quality"
        const val KEY_MATERIAL_BIG = "pref_island_material_big_config"
        const val KEY_MATERIAL_SMALL = "pref_island_material_small_config"
        const val KEY_MATERIAL_EXPAND = "pref_island_material_expand_config"
        const val KEY_MATERIAL_SMALL_FOLLOW_BIG = "pref_island_material_small_follow_big"
        const val KEY_MATERIAL_EXPAND_FOLLOW_BIG = "pref_island_material_expand_follow_big"
        const val DEFAULT_RADIUS = 80
        const val DEFAULT_BLEND_COLOR = 0x20FFFFFF
    }
}