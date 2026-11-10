/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * StatusBarLyricSettingsScreen：状态栏歌词设置页（SettingsActivity 内部 Compose Screen）。
 * 设置项全套对齐 HyperLyric StatusBarLyricSettingsPage（布局/显示行为/交互/图标），
 * 风格沿用 V3 蓝紫渐变 + 磨砂卡片 + 右上角重启按钮。
 *
 * 说明：本页是 SettingsActivity 内的导航页，不再是独立 Activity——
 * 独立 Activity 启动会让 SystemUI 感知到"新窗口"并触发大岛重渲染，
 * 导致大岛/通知栏透明；并入主设置页后不再触发。
 */

package com.liuxiansheng.lyricon.app.activity

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.liuxiansheng.lyricon.app.util.MasterPrefs
import com.liuxiansheng.lyricon.app.util.PrefsBridge
import com.liuxiansheng.lyricon.app.util.StatusBarPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ============ 状态栏歌词设置键（与 xposed 侧 RootConstants 键名一致，跨进程共享同一 prefs） ============
private const val SB_INSERTION_ORDER = "key_hook_status_bar_lyric_insertion_order"
private const val SB_ICON_ENABLED = "key_hook_status_bar_lyric_icon_enabled"
private const val SB_ICON_STYLE = "key_hook_status_bar_lyric_icon_style"
private const val SB_ICON_ORDER = "key_hook_status_bar_lyric_icon_order"
private const val SB_ICON_SIZE_DP = "key_hook_status_bar_lyric_icon_size_dp"
private const val SB_PORTRAIT_MAX_WIDTH = "key_hook_status_bar_lyric_portrait_dynamic_max_width"
private const val SB_LANDSCAPE_MAX_WIDTH = "key_hook_status_bar_lyric_landscape_dynamic_max_width"
private const val SB_PADDING_LEFT_DP = "key_hook_status_bar_lyric_padding_left_dp"
private const val SB_PADDING_RIGHT_DP = "key_hook_status_bar_lyric_padding_right_dp"
private const val SB_CLOCK_HIDE = "key_hook_status_bar_lyric_clock_hide_behavior"
private const val SB_NOTIFICATION_HIDE = "key_hook_status_bar_lyric_notification_icon_hide_behavior"
private const val SB_ISLAND_HIDE = "key_hook_status_bar_lyric_island_hide_behavior"
private const val SB_ADJUST_WIDTH = "key_hook_status_bar_lyric_adjust_width_for_super_island"
private const val SB_HIDE_LOCKSCREEN = "key_hook_status_bar_lyric_hide_on_lock_screen"
private const val SB_DOUBLE_TAP = "key_hook_status_bar_lyric_double_tap_action"
private const val SB_LONG_PRESS = "key_hook_status_bar_lyric_long_press_action"
private const val SB_SWIPE_LEFT = "key_hook_status_bar_lyric_swipe_left_action"
private const val SB_SWIPE_RIGHT = "key_hook_status_bar_lyric_swipe_right_action"
private const val SB_HAPTIC = "key_hook_status_bar_lyric_gesture_haptic_feedback"
private const val SB_TEXT_SIZE = "key_hook_status_bar_lyric_text_size"
private const val SB_TEXT_COLOR = "key_hook_status_bar_lyric_text_color"
private const val SB_RAINBOW = "key_hook_status_bar_lyric_rainbow_enabled"
private const val SB_COVER_COLOR = "key_hook_status_bar_lyric_cover_color_enabled"
private const val SB_INVERT = "key_hook_status_bar_lyric_invert_enabled"
private const val SB_FONT_FAMILY = "key_hook_status_bar_lyric_font_family"
private const val SB_BOLD = "key_hook_status_bar_lyric_bold"
private const val SB_FONT_WEIGHT = "key_hook_status_bar_lyric_font_weight"
// 状态栏自己的换句动画（与超级岛的 key_hook_line_switch_anim 分开，互不影响）
private const val SB_SWITCH_ANIM = "key_hook_status_bar_lyric_switch_anim"
// 状态栏自己的持续动画效果（编号与超级岛共用，配置键独立）
private const val SB_LYRIC_EFFECT = "key_hook_status_bar_lyric_effect"

// 默认值（与 xposed 侧一致）
private const val SB_DEF_INSERTION = 1          // AFTER_CLOCK
private const val SB_DEF_PORTRAIT_WIDTH = 150
private const val SB_DEF_LANDSCAPE_WIDTH = 200
private const val SB_PORTRAIT_LIMIT = 200
private const val SB_LANDSCAPE_LIMIT = 400
private const val SB_PADDING_MIN = -32
private const val SB_PADDING_MAX = 32
private const val SB_DEF_CLOCK_HIDE = 1         // WHILE_PLAYING
private const val SB_DEF_NOTIFICATION_HIDE = 1  // WHILE_PLAYING
private const val SB_DEF_ISLAND_HIDE = 1        // WHILE_PLAYING（3.0.935 用户指定默认隐藏）
private const val SB_DEF_LONG_PRESS = 1         // TOGGLE_PLAYBACK
private const val SB_DEF_SWIPE_LEFT = 2         // NEXT
private const val SB_DEF_SWIPE_RIGHT = 1        // PREVIOUS
private const val SB_ICON_MIN_SIZE = 10
private const val SB_ICON_MAX_SIZE = 24
private const val SB_DEF_ICON_SIZE = 16
private const val SB_DEF_SWITCH_ANIM = 1        // SCALE_UP（副行放大）
private const val SB_ICON_SPACING_DP = "key_hook_status_bar_lyric_icon_spacing_dp"
private const val SB_ICON_MIN_SPACING = 0
private const val SB_ICON_MAX_SPACING = 16
private const val SB_DEF_ICON_SPACING = 3
private const val SB_CONTENT_MODE = "key_hook_status_bar_lyric_content_mode"
private const val SB_DEF_CONTENT_MODE = 2     // 歌词（0=无 / 1=音乐信息 / 2=歌词，index 语义）
private const val SB_DEF_TEXT_SIZE = 13
private const val SB_TEXT_SIZE_MIN = 8
private const val SB_TEXT_SIZE_MAX = 20
private const val SB_DEF_RAINBOW = true   // 默认彩虹（与超级岛不同：超级岛默认关，状态栏默认开）
private const val SB_DEF_COVER_COLOR = false
private const val SB_DEF_FONT_FAMILY = 0     // 系统默认 sans-serif
private const val SB_DEF_BOLD = true         // 默认加粗（保持原硬编码行为）
private const val SB_DEF_FONT_WEIGHT = 5     // 粗体 700

/**
 * 状态栏歌词设置页（MainActivity 内部 Screen，二级目录）。
 * 返回用系统返回键，页面内不加返回按钮。
 */
@Composable
fun StatusBarLyricSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SkinTheme {
        val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
        SideEffect {
            val window = (context as Activity).window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !isDark
        }
        // 应用全局背景（受"流光背景"开关控制）
        com.liuxiansheng.lyricon.app.compose.effect.AppBackground(
            isDarkTheme = isDark,
        ) {
            // Miuix Scaffold 提供 Overlay 弹窗宿主
            top.yukonga.miuix.kmp.basic.Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent
            ) { _ ->
            Column(modifier = Modifier.fillMaxSize()) {
                // 右上角重启按钮：与大卡片同款磨砂圆角卡片，避开状态栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    val btnBg = if (isDark) {
                        Color(0xFF202024).copy(alpha = 0.80f)
                    } else {
                        Color.White.copy(alpha = 0.68f)
                    }
                    val btnBorder = if (isDark) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        Color.White.copy(alpha = 0.80f)
                    }
                    // 重启状态栏按钮
                    Box(
                        modifier = Modifier
                            .background(btnBg, RoundedCornerShape(14.dp))
                            .border(1.dp, btnBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val proc = Runtime.getRuntime().exec(
                                                arrayOf("su", "-c", "killall com.android.systemui")
                                            )
                                            proc.waitFor()
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "↻ 重启状态栏",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    SectionTitle("状态栏歌词")
                    SectionCard {
                        PrefSwitch(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = StatusBarPrefs.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC,
                            default = true,
                            title = "启用状态栏歌词",
                            summary = "在系统状态栏显示歌词，切换实时生效，独立于超级岛总开关",
                            extraWrite = { value ->
                                // 互拆（双向）：状态栏歌词开关与超级岛开关永远相反——
                                // 开状态栏→关超级岛，关状态栏→开超级岛
                                MasterPrefs.setBoolean(
                                    context,
                                    MasterPrefs.KEY_ENABLE_SUPER_ISLAND,
                                    !value
                                )
                            }
                        )
                    }

                    // ============ 布局 ============
                    SectionTitle("布局")
                    SectionCard {
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_INSERTION_ORDER,
                            default = SB_DEF_INSERTION,
                            options = listOf(
                                "时钟之前" to "",
                                "时钟之后" to "",
                            ),
                            title = "插入位置",
                            summary = "歌词相对时钟的位置",
                        )
                        NumberInputPreference(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_PORTRAIT_MAX_WIDTH,
                            default = SB_DEF_PORTRAIT_WIDTH,
                            range = 0..SB_PORTRAIT_LIMIT,
                            unit = "dp",
                            title = "竖屏最大宽度",
                        )
                        NumberInputPreference(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_LANDSCAPE_MAX_WIDTH,
                            default = SB_DEF_LANDSCAPE_WIDTH,
                            range = 0..SB_LANDSCAPE_LIMIT,
                            unit = "dp",
                            title = "横屏最大宽度",
                        )
                        NumberInputPreference(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_PADDING_LEFT_DP,
                            default = 20.toInt(),
                            range = SB_PADDING_MIN.toInt()..
                                    SB_PADDING_MAX.toInt(),
                            unit = "dp",
                            title = "左边距",
                        )
                        NumberInputPreference(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_PADDING_RIGHT_DP,
                            default = 0.toInt(),
                            range = SB_PADDING_MIN.toInt()..
                                    SB_PADDING_MAX.toInt(),
                            unit = "dp",
                            title = "右边距",
                        )
                        PrefSwitch(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_ADJUST_WIDTH,
                            default = true,
                            title = "避让超级岛宽度",
                            summary = "歌词宽度自动避让超级岛区域",
                        )
                    }

                    // ============ 样式 ============
                    SectionTitle("样式")
                    SectionCard {
                        PrefSwitch(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_RAINBOW,
                            default = SB_DEF_RAINBOW,
                            title = "彩虹歌词",
                            summary = "歌词呈 16 色逐字渐变，优先于封面取色与自定义颜色（默认开启）",
                            extraWrite = { value ->
                                if (value) StatusBarPrefs.setBoolean(context, SB_COVER_COLOR, false)
                            },
                        )
                        PrefSwitch(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_COVER_COLOR,
                            default = SB_DEF_COVER_COLOR,
                            title = "封面取色",
                            summary = "从专辑封面提取颜色作为歌词颜色（开启时自动关闭彩虹）",
                            extraWrite = { value ->
                                if (value) StatusBarPrefs.setBoolean(context, SB_RAINBOW, false)
                            },
                        )
                        PrefSwitch(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_INVERT,
                            default = false,
                            title = "黑白背景反色",
                            summary = "歌词字色跟随状态栏背景亮度：亮背景→深色字，暗背景→浅色字（开启时优先于彩虹/封面取色/自定义颜色）",
                            extraWrite = { value ->
                                if (value) {
                                    StatusBarPrefs.setBoolean(context, SB_RAINBOW, false)
                                    StatusBarPrefs.setBoolean(context, SB_COVER_COLOR, false)
                                }
                            },
                        )
                        NumberInputPreference(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_TEXT_SIZE,
                            default = SB_DEF_TEXT_SIZE,
                            range = SB_TEXT_SIZE_MIN..SB_TEXT_SIZE_MAX,
                            unit = "sp",
                            title = "字体大小",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_FONT_FAMILY,
                            default = SB_DEF_FONT_FAMILY,
                            title = "字体",
                            summary = "选择歌词字体（默认系统 sans-serif）",
                            options = listOf(
                                "系统默认（sans-serif）" to "",
                                "中等（sans-serif-medium）" to "",
                                "细体（sans-serif-light）" to "",
                                "衬线（serif）" to "",
                                "等宽（monospace）" to "",
                                "窄体（sans-serif-condensed）" to "",
                                "黑体（sans-serif-black）" to "",
                                "手写体（cursive）" to "",
                            ),
                        )
                        PrefSwitch(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_BOLD,
                            default = SB_DEF_BOLD,
                            title = "粗体",
                            summary = "歌词文字加粗（默认开启）",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_FONT_WEIGHT,
                            default = SB_DEF_FONT_WEIGHT,
                            title = "字重",
                            summary = "调整歌词文字粗细（细 100 ~ 特粗 900，默认粗体 700）",
                            options = listOf(
                                "极细（100）" to "",
                                "细体（300）" to "",
                                "正常（400）" to "",
                                "中等（500）" to "",
                                "半粗（600）" to "",
                                "粗体（700）" to "",
                                "特粗（900）" to "",
                            ),
                        )
                        ColorInputPreference(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_TEXT_COLOR,
                            default = 0xFFFFFFFF.toInt(),
                            title = "歌词颜色",
                            summary = "自定义歌词文字颜色（主色/高亮同色，对齐超级岛）",
                            presetColors = remember {
                                listOf(
                                    0xFFFFFFFF.toInt() to "纯白",
                                    0xFFFFEB3B.toInt() to "明黄",
                                    0xFFFF9800.toInt() to "橙色",
                                    0xFF4CAF50.toInt() to "绿色",
                                    0xFF2196F3.toInt() to "蓝色",
                                    0xFFE91E63.toInt() to "粉色",
                                    0xFF9C27B0.toInt() to "紫色",
                                    0xFF000000.toInt() to "黑色",
                                )
                            },
                        )
                    }

                    // ============ 显示行为 ============
                    SectionTitle("显示行为")
                    SectionCard {
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_CONTENT_MODE,
                            default = SB_DEF_CONTENT_MODE,
                            options = listOf(
                                "无" to "",
                                "音乐信息" to "",
                                "歌词" to "",
                            ),
                            title = "显示内容",
                            summary = "状态栏歌词区域显示的内容",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_CLOCK_HIDE,
                            default = SB_DEF_CLOCK_HIDE,
                            options = listOf(
                                "从不隐藏" to "",
                                "播放时隐藏" to "",
                                "岛出现时隐藏" to "",
                            ),
                            title = "时钟隐藏行为",
                            summary = "歌词显示时对时钟的处理",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_NOTIFICATION_HIDE,
                            default = SB_DEF_NOTIFICATION_HIDE,
                            options = listOf(
                                "从不隐藏" to "",
                                "播放时隐藏" to "",
                                "播放时右移" to "",
                            ),
                            title = "通知图标隐藏",
                            summary = "歌词显示时对左侧通知图标的处理",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_ISLAND_HIDE,
                            default = SB_DEF_ISLAND_HIDE,
                            options = listOf(
                                "从不隐藏" to "",
                                "播放时隐藏" to "",
                                "始终隐藏" to "",
                            ),
                            title = "超级岛隐藏",
                            summary = "状态栏歌词显示时对超级岛的处理",
                        )
                        PrefSwitch(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_HIDE_LOCKSCREEN,
                            default = true,
                            title = "锁屏时隐藏",
                            summary = "锁屏后不显示状态栏歌词",
                        )
                    }

                    // ============ 动画（状态栏独立配置） ============
                    SectionTitle("动画")
                    SectionCard {
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_LYRIC_EFFECT,
                            default = 0,
                            options = listOf(
                                "静态" to "不带动画",
                                "波浪起伏" to "每字波浪上下移动",
                                "打字机" to "逐字出现加闪烁光标",
                                "霓虹灯" to "发光脉冲闪烁",
                                "模糊入场" to "从模糊到清晰",
                                "渐入淡出" to "透明度循环淡入淡出",
                                "缩放呼吸" to "整体缩放呼吸",
                                "左右滑动" to "左滑入停留右滑出",
                                "颜色流动" to "彩虹色渐变流动",
                                "逐字放大" to "每个字依次放大",
                                "上下浮动" to "整行上下浮动",
                                "3D翻转" to "Y轴旋转翻转",
                                "聚光灯" to "聚光灯移动照亮文字",
                                "镜像倒影" to "下方镜像倒影",
                                "激光扫描" to "激光线扫描揭示文字",
                                "粒子聚合" to "粒子飞向文字聚合再散开",
                                "故障艺术" to "RGB色差错位+扫描线",
                                "手写效果" to "从左到右逐渐显露+抖动",
                                "波形背景" to "音频波形竖条背景",
                                "星星散落" to "周围星星粒子闪烁",
                                "旋转出现" to "旋转缩放出现停留消失",
                                "印度运镜" to "夸张旋转缩放魔性效果",
                            ),
                            // 存储值对齐 LyricEffectRenderer.EFFECT_*（已删 2/3/16/21 号，老设置不错位）
                            values = listOf(
                                0, 1, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                                17, 18, 19, 20, 22, 23, 24, 25
                            ),
                            title = "歌词动画效果",
                            summary = "状态栏歌词的持续动画（与超级岛同一套效果库，各自独立设置）",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_SWITCH_ANIM,
                            default = SB_DEF_SWITCH_ANIM,
                            options = listOf(
                                "无动画" to "原始切换，无过渡效果",
                                "副行放大" to "副行放大并移到主行位置",
                                "淡入淡出" to "主行淡出、副行原地淡入",
                                "上滑" to "主行上移淡出、副行自下滑入",
                                "下滑" to "主行下移淡出、副行自上滑入",
                                "缩放淡入" to "主行缩小淡出、副行放大淡入",
                                "3D 翻转" to "沿纵轴翻出翻入",
                                "左滑" to "主行左滑淡出、副行右侧滑入",
                                "右滑" to "主行右滑淡出、副行左侧滑入",
                                "弹性放大" to "副行带过冲弹性放大入场",
                                "竖向翻转" to "沿横轴上下翻出翻入",
                                "模糊淡入" to "主行糊掉、副行由糊变清晰（Android 12+）",
                            ),
                            title = "换句动画",
                            summary = "状态栏自己的换句过渡（与超级岛分开设置，互不影响）",
                        )
                    }

                    // ============ 交互 ============
                    SectionTitle("交互")
                    SectionCard {
                        val pressOptions = listOf(
                            "无操作" to "",
                            "播放/暂停" to "",
                            "临时显示时钟" to "",
                            "打开音乐App" to "",
                            "临时隐藏歌词" to "",
                            "小窗打开音乐App" to "",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_DOUBLE_TAP,
                            default = 0,
                            options = pressOptions,
                            title = "双击",
                            summary = "双击歌词的动作",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_LONG_PRESS,
                            default = SB_DEF_LONG_PRESS,
                            options = pressOptions,
                            title = "长按",
                            summary = "长按歌词的动作",
                        )
                        val swipeOptions = listOf(
                            "无操作" to "",
                            "上一首" to "",
                            "下一首" to "",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_SWIPE_LEFT,
                            default = SB_DEF_SWIPE_LEFT,
                            options = swipeOptions,
                            title = "左滑",
                            summary = "向左滑动歌词的动作",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_SWIPE_RIGHT,
                            default = SB_DEF_SWIPE_RIGHT,
                            options = swipeOptions,
                            title = "右滑",
                            summary = "向右滑动歌词的动作",
                        )
                        PrefSwitch(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_HAPTIC,
                            default = true,
                            title = "手势触觉反馈",
                            summary = "手势操作时震动反馈",
                        )
                    }

                    // ============ 图标 ============
                    SectionTitle("图标")
                    SectionCard {
                        PrefSwitch(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_ICON_ENABLED,
                            default = false,
                            title = "显示图标",
                            summary = "歌词前显示封面/应用图标",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_ICON_STYLE,
                            default = 0,
                            options = listOf(
                                "专辑封面" to "",
                                "圆形封面" to "",
                                "旋转封面" to "",
                                "应用图标" to "",
                                "单色图标" to "",
                            ),
                            title = "图标样式",
                            summary = "图标显示样式",
                        )
                        PrefSpinner(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_ICON_ORDER,
                            default = 0,
                            options = listOf(
                                "歌词之前" to "",
                                "歌词之后" to "",
                            ),
                            title = "图标位置",
                            summary = "图标相对歌词的位置",
                        )
                        NumberInputPreference(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_ICON_SIZE_DP,
                            default = SB_DEF_ICON_SIZE,
                            range = SB_ICON_MIN_SIZE..
                                    SB_ICON_MAX_SIZE,
                            unit = "dp",
                            title = "图标大小",
                        )
                        NumberInputPreference(
                        context = context,
                        bridge = StatusBarPrefs,
                        key = SB_ICON_SPACING_DP,
                            default = SB_DEF_ICON_SPACING,
                            range = SB_ICON_MIN_SPACING..
                                    SB_ICON_MAX_SPACING,
                            unit = "dp",
                            title = "图标间距",
                        )
                    }
                }
            }
            }
        }
    }
}
