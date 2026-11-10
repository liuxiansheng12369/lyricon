/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 毛玻璃材质配置读取：优先读 pref_island_material_*_config（JSON 预设格式），
 * 不存在则回退 pref_island_blur_* 旧键；玻璃参数支持散落独立键覆盖。
 */

package com.liuxiansheng.lyricon.hyper.root.island.effects.blur

import android.content.SharedPreferences
import android.graphics.Color
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import org.json.JSONObject

/**
 * 从词岛 prefs 读取毛玻璃材质配置（V3 自研）。
 * 优先读 pref_island_material_*_config（JSON，对齐 Hub 预设格式），
 * 不存在则回退到 pref_island_blur_* 旧键。
 */
internal class IslandMaterialConfigReader {

    @Volatile
    private var blurConfigs = BlurConfigs.disabled()

    @Volatile
    private var materialConfigs = MaterialConfigs.defaults()

    @Volatile
    var revision: Int = 0
        private set

    @Volatile
    var anyEnabled: Boolean = false
        private set

    fun reload(prefs: SharedPreferences) {
        val masterEnabled = prefs.getBoolean(RootConstants.KEY_ISLAND_MATERIAL_ENABLED, false)
        val hasMaterialSchema = prefs.contains(RootConstants.KEY_ISLAND_MATERIAL_BIG)
        if (hasMaterialSchema) {
            val big = readMaterialConfig(prefs, RootConstants.KEY_ISLAND_MATERIAL_BIG)
            val small = if (prefs.getBoolean(
                    RootConstants.KEY_ISLAND_MATERIAL_SMALL_FOLLOW_BIG,
                    true
                )
            ) {
                big
            } else {
                readMaterialConfig(prefs, RootConstants.KEY_ISLAND_MATERIAL_SMALL)
            }
            val expand = if (prefs.getBoolean(
                    RootConstants.KEY_ISLAND_MATERIAL_EXPAND_FOLLOW_BIG,
                    true
                )
            ) {
                big
            } else {
                readMaterialConfig(prefs, RootConstants.KEY_ISLAND_MATERIAL_EXPAND)
            }
            materialConfigs = MaterialConfigs(small, big, expand)
            blurConfigs = BlurConfigs(small.toBlurConfig(), big.toBlurConfig(), expand.toBlurConfig())
        } else {
            blurConfigs = BlurConfigs(
                small = readBlurConfig(
                    prefs,
                    RootConstants.KEY_ISLAND_BLUR_SMALL_ENABLED,
                    RootConstants.KEY_ISLAND_BLUR_SMALL_RADIUS,
                    RootConstants.KEY_ISLAND_BLUR_SMALL_COLOR,
                ),
                big = readBlurConfig(
                    prefs,
                    RootConstants.KEY_ISLAND_BLUR_BIG_ENABLED,
                    RootConstants.KEY_ISLAND_BLUR_BIG_RADIUS,
                    RootConstants.KEY_ISLAND_BLUR_BIG_COLOR,
                ),
                expand = readBlurConfig(
                    prefs,
                    RootConstants.KEY_ISLAND_BLUR_EXPAND_ENABLED,
                    RootConstants.KEY_ISLAND_BLUR_EXPAND_RADIUS,
                    RootConstants.KEY_ISLAND_BLUR_EXPAND_COLOR,
                ),
            )
            // 兼容单一"材质类型"开关：pref_island_material_type 存在时以其为准（0=高斯 1=高光 2=液态 3=柔光）
            if (prefs.contains(RootConstants.KEY_ISLAND_MATERIAL_TYPE)) {
                val type = when (prefs.getInt(RootConstants.KEY_ISLAND_MATERIAL_TYPE, 0)) {
                    1 -> MaterialType.HIGHLIGHT
                    2 -> MaterialType.LIQUID
                    3 -> MaterialType.SOFT
                    else -> MaterialType.GAUSSIAN
                }
                if (type != MaterialType.DEFAULT) {
                    val big = applyScatteredOverrides(
                        MaterialConfig.default().copy(type = type, blur = blurConfigs.big.radius),
                        prefs,
                    )
                    materialConfigs = MaterialConfigs(big, big, big)
                    blurConfigs = BlurConfigs(big.toBlurConfig(), big.toBlurConfig(), big.toBlurConfig())
                }
            }
        }
        if (!masterEnabled) {
            blurConfigs = BlurConfigs.disabled()
            materialConfigs = MaterialConfigs.defaults()
        }
        // 玻璃独立总开关 pref_island_glass_enabled=false → 禁用材质（柔光/毛玻璃不挂，回退默认）
        if (prefs.contains(RootConstants.KEY_ISLAND_GLASS_ENABLED) &&
            !runCatching {
                prefs.getBoolean(RootConstants.KEY_ISLAND_GLASS_ENABLED, true)
            }.getOrDefault(true)
        ) {
            val plain = MaterialConfig.default()
            materialConfigs = MaterialConfigs(plain, plain, plain)
            blurConfigs = BlurConfigs(plain.toBlurConfig(), plain.toBlurConfig(), plain.toBlurConfig())
        }
        // BIG 形态玻璃开关 pref_island_glass_big_enabled=false → BIG 不挂玻璃（词岛当前实际渲染 BIG）
        if (prefs.contains(RootConstants.KEY_ISLAND_GLASS_BIG_ENABLED) &&
            !runCatching {
                prefs.getBoolean(RootConstants.KEY_ISLAND_GLASS_BIG_ENABLED, true)
            }.getOrDefault(true)
        ) {
            val plain = MaterialConfig.default()
            materialConfigs = MaterialConfigs(plain, plain, plain)
            blurConfigs = BlurConfigs(plain.toBlurConfig(), plain.toBlurConfig(), plain.toBlurConfig())
        }
        anyEnabled = IslandType.entries.any { blurConfigs.forType(it).isActive }
        revision++
    }

    fun materialFor(type: IslandType): MaterialConfig = materialConfigs.forType(type)

    fun blurFor(type: IslandType): BlurConfig = blurConfigs.forType(type)

    private fun readMaterialConfig(prefs: SharedPreferences, key: String): MaterialConfig {
        val json = runCatching { JSONObject(prefs.getString(key, "").orEmpty()) }.getOrNull()
            ?: return MaterialConfig.default()
        fun int(name: String, fallback: Int, min: Int, max: Int) =
            json.optInt(name, fallback).coerceIn(min, max)
        val type = MaterialType.fromValue(json.optString("type", "default"))
        val blur = int("blur", RootConstants.DEFAULT_ISLAND_BLUR_RADIUS, 0, 100)
        val color = parseColor(json.optString("blendColor", "#0F0F0F"))
        val opacity = int("blendOpacity", 0, 0, 100)
        val blendColor = Color.argb(
            opacity * 255 / 100,
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
        var config = MaterialConfig(
            type = type,
            blur = blur,
            darker = int("darker", RootConstants.DEFAULT_ISLAND_GLASS_SHADOW, 0, 100),
            refraction = int("refraction", RootConstants.DEFAULT_ISLAND_GLASS_REFRACTION, 0, 40),
            edgeThickness = int("edgeThickness", RootConstants.DEFAULT_ISLAND_GLASS_EDGE_WIDTH, 4, 40),
            reflectionStrength = int("reflectionStrength", RootConstants.DEFAULT_ISLAND_GLASS_HIGHLIGHT, 0, 100),
            lightDirection = int("lightDirection", RootConstants.DEFAULT_ISLAND_GLASS_LIGHT_DIRECTION, 0, 359),
            dispersion = int("dispersion", RootConstants.DEFAULT_ISLAND_GLASS_DISPERSION, 0, 100),
            blendColor = blendColor,
            highlight = json.optBoolean("highlight", true),
        )
        return applyScatteredOverrides(config, prefs)
    }

    /**
     * Hub 预设可能以「散落独立键」下发玻璃参数（而非 material_big_config JSON）。
     * 只要这些键存在就以独立键为准覆盖，让预设里写了就真正生效。
     * 关键：预设键类型可能与期望不符（布尔被存成 int、int 被存成字符串等），
     * 一律 runCatching 安全读取，避免在 SystemUI 进程抛 ClassCastException 导致状态栏崩溃。
     * 无论走 material JSON 分支还是材质类型开关分支都应用，保证每项设置都能生效。
     */
    private fun applyScatteredOverrides(config: MaterialConfig, prefs: SharedPreferences): MaterialConfig {
        fun safeInt(key: String, fallback: Int) =
            runCatching { prefs.getInt(key, fallback) }.getOrDefault(fallback)
        fun safeBool(key: String, fallback: Boolean) =
            runCatching { prefs.getBoolean(key, fallback) }.getOrDefault(fallback)
        var result = config
        if (prefs.contains(RootConstants.KEY_ISLAND_GLASS_EDGE_WIDTH)) {
            result = result.copy(
                edgeThickness = safeInt(RootConstants.KEY_ISLAND_GLASS_EDGE_WIDTH, result.edgeThickness)
                    .coerceIn(4, 40),
            )
        }
        if (prefs.contains(RootConstants.KEY_ISLAND_GLASS_REFRACTION)) {
            result = result.copy(
                refraction = safeInt(RootConstants.KEY_ISLAND_GLASS_REFRACTION, result.refraction)
                    .coerceIn(0, 40),
            )
        }
        if (prefs.contains(RootConstants.KEY_ISLAND_GLASS_SHADOW)) {
            result = result.copy(
                darker = safeInt(RootConstants.KEY_ISLAND_GLASS_SHADOW, result.darker)
                    .coerceIn(0, 100),
            )
        }
        if (prefs.contains(RootConstants.KEY_ISLAND_GLASS_HIGHLIGHT)) {
            result = result.copy(
                highlight = safeBool(RootConstants.KEY_ISLAND_GLASS_HIGHLIGHT, result.highlight),
            )
        }
        if (prefs.contains(RootConstants.KEY_ISLAND_GLASS_LIGHT_DIRECTION)) {
            result = result.copy(
                lightDirection = safeInt(
                    RootConstants.KEY_ISLAND_GLASS_LIGHT_DIRECTION,
                    result.lightDirection,
                ).coerceIn(0, 359),
            )
        }
        if (prefs.contains(RootConstants.KEY_ISLAND_GLASS_DISPERSION)) {
            result = result.copy(
                dispersion = safeInt(RootConstants.KEY_ISLAND_GLASS_DISPERSION, result.dispersion)
                    .coerceIn(0, 100),
            )
        }
        // 折射独立开关 pref_island_refraction_*_enabled：false 时折射归零（无折射），true 时用折射参数
        if (prefs.contains(RootConstants.KEY_ISLAND_REFRACTION_BIG_ENABLED) &&
            !safeBool(RootConstants.KEY_ISLAND_REFRACTION_BIG_ENABLED, true)
        ) {
            result = result.copy(refraction = 0)
        }
        return result
    }

    private fun readBlurConfig(
        prefs: SharedPreferences,
        enabledKey: String,
        radiusKey: String,
        colorKey: String,
    ) = BlurConfig(
        enabled = runCatching { prefs.getBoolean(enabledKey, false) }.getOrDefault(false),
        radius = runCatching { prefs.getInt(radiusKey, RootConstants.DEFAULT_ISLAND_BLUR_RADIUS) }
            .getOrDefault(RootConstants.DEFAULT_ISLAND_BLUR_RADIUS).coerceIn(0, 100),
        blendColor = parseColor(runCatching { prefs.getString(colorKey, "").orEmpty() }.getOrDefault("")),
    )

    private fun parseColor(value: String): Int {
        if (value.isBlank()) return RootConstants.DEFAULT_ISLAND_BLUR_COLOR
        return runCatching { Color.parseColor(value.trim()) }.getOrDefault(
            RootConstants.DEFAULT_ISLAND_BLUR_COLOR
        )
    }
}
