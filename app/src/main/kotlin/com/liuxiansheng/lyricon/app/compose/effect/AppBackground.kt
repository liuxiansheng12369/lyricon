package com.liuxiansheng.lyricon.app.compose.effect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用全局背景（受"流光背景"开关控制）
 * - 开关开启：Shader 流光背景（老词幕同款）
 * - 开关关闭：静态蓝紫渐变背景
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean? = null,
    content: @Composable (BoxScope.() -> Unit),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = isDarkTheme ?: (MiuixTheme.colorScheme.background.luminance() < 0.5f)

    // 首页主题皮肤（1=黑胶 / 2=晨雾 / 3=赛博）：皮肤接管背景，默认(id=0)继续走下面的原有分支
    val homeSkin = com.liuxiansheng.lyricon.app.activity.LocalHomeSkin.current
    if (homeSkin != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(homeSkin.bgTop, homeSkin.bgMid, homeSkin.bgBottom)
                        )
                    )
                    homeSkin.glows.forEach { g ->
                        val center = Offset(size.width * g.cx, size.height * g.cy)
                        val r = size.width * g.radiusRatio
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(g.color, Color.Transparent),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                            center = center,
                        )
                    }
                }
        ) {
            content()
        }
        return
    }

    // 用 mutableStateOf 存储开关状态，注册 SharedPreferences 监听
    // PrefSwitch 写的是 MasterPrefs.PREF_NAME = "com.liuxiansheng.lyricon_preferences"
    val pref = context.getSharedPreferences("com.liuxiansheng.lyricon_preferences", android.content.Context.MODE_PRIVATE)
    var enableShader by remember { mutableStateOf(pref.getBoolean("pref_advanced_流光背景", true)) }

    // 注册监听，开关变化时更新 state，触发重组
    LaunchedEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "pref_advanced_流光背景") {
                enableShader = pref.getBoolean(key, true)
            }
        }
        pref.registerOnSharedPreferenceChangeListener(listener)
    }

    if (enableShader) {
        BgEffectBackground(
            dynamicBackground = true,
            effectBackground = true,
            isDarkTheme = isDark,
            modifier = modifier,
            content = content,
        )
    } else {
        // 静态蓝紫渐变背景
        val bgBrush = if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1E3A8A),
                    Color(0xFF16225C),
                    MiuixTheme.colorScheme.background,
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF4F7CFF),
                    Color(0xFF8FB2FF),
                    Color(0xFFDCE6FF),
                    MiuixTheme.colorScheme.background,
                )
            )
        }
        Box(
            modifier = modifier
                .background(bgBrush)
        ) {
            content()
        }
    }
}
