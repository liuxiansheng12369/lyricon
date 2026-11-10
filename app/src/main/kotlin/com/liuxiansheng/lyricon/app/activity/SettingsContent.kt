/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * 超级岛设置页各分组（V3 自研设置项）。
 * 全部组件为状态化：写入 prefs 的同时更新 UI 状态（开关/下拉/数值立即反馈）。
 * 键直读直写（开=开、关=关），不取反、不双写；改动即广播让 SystemUI 实时生效。
 */

package com.liuxiansheng.lyricon.app.activity

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuxiansheng.lyricon.app.util.HubPresetClient
import com.liuxiansheng.lyricon.app.util.MasterPrefs
import com.liuxiansheng.lyricon.app.util.StatusBarPrefs
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.widget.Toast
import org.json.JSONObject
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 超级岛设置各组（V3 自研设置项）。
 * 只保留 V3 自己的功能项；上游/暂不支持项已全部移除。
 */

// ================= 总开关 =================

@Composable
fun SuperIslandSection(context: Context, reorder: SectionReorder? = null) {
    SectionTitle("超级岛歌词", canReorder = reorder?.canReorder == true, onMoveUp = reorder?.onMoveUp, onMoveDown = reorder?.onMoveDown, onMoveTop = reorder?.onMoveTop, onMoveBottom = reorder?.onMoveBottom)
    SectionCard {
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ENABLE_SUPER_ISLAND,
            default = true,
            title = "启用超级岛歌词",
            summary = "在小米灵动岛内显示歌词，切换后需重启系统界面生效",
            extraWrite = {
                // 互拆（双向）：超级岛开关与状态栏歌词开关永远相反
                StatusBarPrefs.setBoolean(
                    context,
                    StatusBarPrefs.KEY_HOOK_ENABLE_STATUS_BAR_LYRIC,
                    !it
                )
            }
        )
    }
}

// ================= 内容模式数据（左右共用） =================

private val contentCategories = listOf(
    ContentCategory("基础功能", listOf(
        "lyric" to "歌词",
        "song_info" to "歌名+艺术家",
        "time" to "当前时间",
        "network_speed" to "实时网速",
        "status_bar_icons" to "状态栏图标(蓝牙+电池+网速)",
    )),
    ContentCategory("系统状态", listOf(
        "battery_level" to "电池电量",
        "battery_temp" to "电池温度",
        "charging_status" to "充电状态",
        "charging_wattage" to "充电功率",
        "battery_health" to "电池健康度",
        "signal_strength" to "信号强度",
        "mobile_network_type" to "移动网络",
        "carrier_name" to "运营商",
        "wifi_ssid" to "WiFi名称",
        "volume_level" to "音量等级",
        "screen_brightness" to "屏幕亮度",
        "screen_refresh_rate" to "刷新率",
    )),
    ContentCategory("快捷信息", listOf(
        "date_weekday" to "日期+星期",
        "lunar_date" to "农历日期",
        "moon_phase" to "月相",
        "seconds_time" to "秒级时间",
        "system_uptime" to "运行时长",
        "alarm_time" to "闹钟时间",
        "dnd_status" to "勿扰模式",
        "airplane_mode" to "飞行模式",
        "vpn_status" to "VPN状态",
        "current_app" to "当前应用",
        "local_ip" to "IP地址",
    )),
    ContentCategory("性能监控", listOf(
        "cpu_usage" to "CPU使用率",
        "cpu_temp" to "CPU温度",
        "memory_usage" to "内存使用率",
        "storage_space" to "存储空间",
        "fps_counter" to "实时帧率",
        "thermal_summary" to "设备温度",
        "network_speed_split" to "网速分上下行",
        "bluetooth_battery" to "蓝牙电量",
        "unread_notifications" to "未读通知数",
    )),
    ContentCategory("在线服务", listOf(
        "weather_temp" to "天气温度",
        "air_quality" to "空气质量",
        "uv_index" to "紫外线指数",
        "today_steps" to "今日步数",
        "countdown_days" to "倒数日",
        "custom_api" to "自定义API",
    )),
)

private data class ContentCategory(val title: String, val items: List<Pair<String, String>>)

// ================= 一、基础设置 =================

@Composable
fun BasicSettingsSection(context: Context, reorder: SectionReorder? = null) {
    SectionTitle("基础设置", canReorder = reorder?.canReorder == true, onMoveUp = reorder?.onMoveUp, onMoveDown = reorder?.onMoveDown, onMoveTop = reorder?.onMoveTop, onMoveBottom = reorder?.onMoveBottom)
    SectionCard {
        // 3.0.773：删除"灵动岛"开关（xposed 端未读取该键，纯摆设，用户确认无用）

        // 暂停后行为（0=暂停时隐藏 1=暂停时保留 2=暂停时不生成超级岛（播放时自动弹出））
        PrefSpinner(
            context = context,
            key = MasterPrefs.KEY_ISLAND_BEHAVIOR_AFTER_PAUSE,
            default = 0,
            options = listOf(
                ("暂停时隐藏" to "暂停后歌词自动隐藏"),
                ("暂停时保留" to "暂停后歌词岛继续显示"),
                ("暂停时不生成超级岛" to "暂停时岛直接不存在，恢复播放后自动弹出")
            ),
            title = "暂停后行为",
            summary = "音乐暂停时超级岛歌词的显示策略"
        )

        // 动态宽度基准（0=全部内容 1=仅歌词，IslandSlotRuntimeConfigReader 读取）
        PrefSpinner(
            context = context,
            key = MasterPrefs.KEY_ISLAND_DYNAMIC_WIDTH_BASIS,
            default = 0,
            options = listOf(
                ("全部内容" to "按岛内全部内容计算动态宽度"),
                ("仅歌词" to "只按歌词文本计算动态宽度")
            ),
            title = "动态宽度基准",
            summary = "动态宽度收缩时以什么为基准"
        )

        // 分离歌词模式：长歌词左右分段显示，中间避让挖孔区域
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_SPLIT_LYRIC_MODE,
            default = true,
            title = "分离歌词模式",
            summary = "长歌词左右分段显示，中间避让挖孔区域",
            extraWrite = { value ->
                MasterPrefs.setInt(context, MasterPrefs.KEY_LYRIC_MODE, if (value) 1 else 0)
                // 3.0.881：关闭分离时不再强制左右写歌词——
                // 左右各显示用户在选择器里选的内容（渲染端优先读字符串模式键）
            }
        )

        // 动态宽度收缩：开启后岛宽随歌词长度实时变化，关闭后固定宽度
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_ENABLE_DYNAMIC_SHRINK,
            default = true,
            title = "动态宽度收缩",
            summary = "开启后岛宽随歌词长度实时变化，关闭后固定宽度",
            extraWrite = { value ->
                MasterPrefs.setInt(context, MasterPrefs.KEY_ISLAND_WIDTH_MODE, if (value) 1 else 0)
            }
        )

        // 解除宽度限制（V3 补回：默认系统限制单侧 114dp，开启后 170dp）
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_DISABLE_WIDTH_LIMIT,
            default = false,
            title = "解除宽度限制",
            summary = "开启后单侧歌词容器最大 148dp；关闭时系统限制 92dp"
        )

        // 动态最大宽度
        NumberInputPreference(
            context = context,
            key = MasterPrefs.KEY_ISLAND_DYNAMIC_MAX_WIDTH,
            default = 72,
            range = 22..150,
            unit = "dp",
            title = "动态最大宽度",
            extraWrite = {}
        )

        NumberInputPreference(
            context = context,
            key = MasterPrefs.KEY_ISLAND_DYNAMIC_MIN_WIDTH,
            default = 22,
            range = 10..100,
            unit = "dp",
            title = "动态最小宽度"
        )

        // 频谱封面隐藏（总开关：开=始终隐藏封面与频谱，关=正常显示）
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_SPECTRUM_LINK_WIDTH,
            default = true,
            title = "频谱封面隐藏",
            summary = "开启后始终隐藏封面和频谱，只显示歌词；关闭后正常显示"
        )

        // 文字大小（默认 12sp）
        NumberInputPreference(
            context = context,
            key = MasterPrefs.KEY_TEXT_SIZE,
            default = 12,
            range = 8..32,
            unit = "sp",
            title = "文字大小",
            extraWrite = {}
        )

        // 歌词未到时显示：占位格式（存储值：0=关闭 1=歌名-歌手 2=歌名；倒计时已删除）
        PrefSpinner(
            context = context,
            key = MasterPrefs.KEY_PLACEHOLDER_FORMAT,
            default = 2,
            options = listOf(
                "关闭" to "歌词出现前不显示任何占位",
                "歌名-歌手" to "显示 歌名 - 歌手",
                "歌名" to "只显示歌名"
            ),
            values = listOf(0, 1, 2),
            title = "歌词未到时显示",
            summary = "歌词出现前，岛上显示歌名，让人知道功能已生效"
        )

        // 歌词/容器偏移（合并左/右歌词偏移 + 左/右容器间距为一个弹窗项，交互同自定义颜色）
        var showOffsetDialog by remember { mutableStateOf(false) }
        ArrowPreference(
            title = "歌词/容器偏移",
            summary = "统一调整左/右歌词偏移与容器间距（弹窗内直接改）",
            onClick = { showOffsetDialog = true }
        )
        if (showOffsetDialog) {
            WindowDialog(
                show = true,
                title = "歌词/容器偏移",
                onDismissRequest = { showOffsetDialog = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    NumberInputPreference(
                        context = context,
                        key = MasterPrefs.KEY_ISLAND_LEFT_PADDING_LEFT,
                        default = 0,
                        range = -100..100,
                        unit = "dp",
                        title = "左歌词偏移",
                        inline = true
                    )
                    NumberInputPreference(
                        context = context,
                        key = MasterPrefs.KEY_ISLAND_RIGHT_PADDING_RIGHT,
                        default = 0,
                        range = -100..100,
                        unit = "dp",
                        title = "右歌词偏移",
                        inline = true
                    )
                    NumberInputPreference(
                        context = context,
                        key = MasterPrefs.KEY_ISLAND_LEFT_PADDING_RIGHT,
                        default = 2,
                        range = 0..100,
                        unit = "dp",
                        title = "左容器右间距（贴摄像头侧避让）",
                        inline = true
                    )
                    NumberInputPreference(
                        context = context,
                        key = MasterPrefs.KEY_ISLAND_RIGHT_PADDING_LEFT,
                        default = 10,
                        range = 0..100,
                        unit = "dp",
                        title = "右容器左间距（贴摄像头侧避让）",
                        inline = true
                    )
                }
            }
        }

        // 左右显示歌词/内容（合并 4 项为一个弹窗，交互同歌词/容器偏移）
        var showSideDialog by remember { mutableStateOf(false) }
        ArrowPreference(
            title = "左右显示歌词/内容",
            summary = "统一调整左侧/右侧的歌词开关与显示内容",
            onClick = { showSideDialog = true }
        )
        if (showSideDialog) {
            WindowDialog(
                show = true,
                title = "左右显示歌词/内容",
                onDismissRequest = { showSideDialog = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    // 左侧显示歌词
                    var leftLyricEnabled by remember {
                        mutableStateOf(MasterPrefs.getBoolean(context, MasterPrefs.KEY_ISLAND_LEFT_ENABLED, true))
                    }
                    SwitchPreference(
                        checked = leftLyricEnabled,
                        onCheckedChange = { v ->
                            leftLyricEnabled = v
                            MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_LEFT_ENABLED, v)
                            MasterPrefs.setInt(context, MasterPrefs.KEY_ISLAND_CONTENT_LEFT, if (v) 7 else 0)
                        },
                        title = "左侧显示歌词",
                        summary = "在岛的左侧区域显示歌词"
                    )
                    // 左侧显示内容（二级弹窗为系统级 Dialog，盖在一级之上）
                    val leftModeNow = MasterPrefs.getString(
                        context, MasterPrefs.KEY_ISLAND_LEFT_CONTENT_MODE, "lyric"
                    ) ?: "lyric"
                    val leftModeName = contentCategories.flatMap { it.items }
                        .firstOrNull { it.first == leftModeNow }?.second ?: "歌词"
                    var showLeftContentDialog by remember { mutableStateOf(false) }
                    ArrowPreference(
                        title = "左侧显示内容",
                        summary = leftModeName,
                        onClick = { showLeftContentDialog = true }
                    )
                    if (showLeftContentDialog) {
                        ContentModeDialog(
                            current = leftModeNow,
                            onDismiss = { showLeftContentDialog = false },
                            onSelected = { mode ->
                                showLeftContentDialog = false
                                MasterPrefs.setString(context, MasterPrefs.KEY_ISLAND_LEFT_CONTENT_MODE, mode)
                                MasterPrefs.setInt(
                                    context,
                                    MasterPrefs.KEY_ISLAND_CONTENT_LEFT,
                                    when (mode) {
                                        "lyric" -> 7
                                        "song_info" -> 8
                                        else -> 0 // 扩展模式：渲染端读字符串键
                                    }
                                )
                            }
                        )
                    }
                    // 右侧显示歌词
                    var rightLyricEnabled by remember {
                        mutableStateOf(MasterPrefs.getBoolean(context, MasterPrefs.KEY_ISLAND_RIGHT_ENABLED, true))
                    }
                    SwitchPreference(
                        checked = rightLyricEnabled,
                        onCheckedChange = { v ->
                            rightLyricEnabled = v
                            MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_RIGHT_ENABLED, v)
                            MasterPrefs.setInt(context, MasterPrefs.KEY_ISLAND_CONTENT_RIGHT, if (v) 7 else 0)
                        },
                        title = "右侧显示歌词",
                        summary = "在岛的右侧区域显示歌词"
                    )
                    // 右侧显示内容（二级弹窗为系统级 Dialog，盖在一级之上）
                    val rightModeNow = MasterPrefs.getString(
                        context, MasterPrefs.KEY_ISLAND_RIGHT_CONTENT_MODE, "lyric"
                    ) ?: "lyric"
                    val rightModeName = contentCategories.flatMap { it.items }
                        .firstOrNull { it.first == rightModeNow }?.second ?: "歌词"
                    var showRightContentDialog by remember { mutableStateOf(false) }
                    ArrowPreference(
                        title = "右侧显示内容",
                        summary = rightModeName,
                        onClick = { showRightContentDialog = true }
                    )
                    if (showRightContentDialog) {
                        ContentModeDialog(
                            current = rightModeNow,
                            onDismiss = { showRightContentDialog = false },
                            onSelected = { mode ->
                                showRightContentDialog = false
                                MasterPrefs.setString(context, MasterPrefs.KEY_ISLAND_RIGHT_CONTENT_MODE, mode)
                                MasterPrefs.setInt(
                                    context,
                                    MasterPrefs.KEY_ISLAND_CONTENT_RIGHT,
                                    when (mode) {
                                        "lyric" -> 7
                                        "song_info" -> 8
                                        else -> 0 // 扩展模式：渲染端读字符串键
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ================= 二、歌词效果 =================

@Composable
fun LyricEffectSection(context: Context, reorder: SectionReorder? = null) {
    // 动画总开关关掉时，下面这些子项全部收起，只留总开关本身
    var animEnabled by remember {
        mutableStateOf(MasterPrefs.getBoolean(context, MasterPrefs.KEY_ANIM_ENABLE, true))
    }
    SectionTitle("歌词效果", canReorder = reorder?.canReorder == true, onMoveUp = reorder?.onMoveUp, onMoveDown = reorder?.onMoveDown, onMoveTop = reorder?.onMoveTop, onMoveBottom = reorder?.onMoveBottom)
    SectionCard {
        // 动画总开关（渲染端 key_hook_anim_enable 读取）
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ANIM_ENABLE,
            default = true,
            title = "启用动画",
            summary = "歌词与媒体卡动画总开关",
            extraWrite = { animEnabled = it }
        )

        if (animEnabled) {
        // 以下子项仅在动画总开关打开时显示
        // 歌词动画效果（0=静态，其余见 LyricEffectRenderer 的 EFFECT_*；三点倒计时/歌名+三点已移除）
        PrefSpinner(
            context = context,
            key = MasterPrefs.KEY_ISLAND_LYRIC_EFFECT,
            default = 0,
            options = listOf(
                ("静态" to "不带动画"),
                ("波浪起伏" to "每字波浪上下移动"),
                ("打字机" to "逐字出现加闪烁光标"),
                ("霓虹灯" to "发光脉冲闪烁"),
                ("模糊入场" to "从模糊到清晰"),
                ("渐入淡出" to "透明度循环淡入淡出"),
                ("缩放呼吸" to "整体缩放呼吸"),
                ("左右滑动" to "左滑入停留右滑出"),
                ("颜色流动" to "彩虹色渐变流动"),
                ("逐字放大" to "每个字依次放大"),
                ("上下浮动" to "整行上下浮动"),
                ("3D翻转" to "Y轴旋转翻转"),
                ("聚光灯" to "聚光灯移动照亮文字"),
                ("镜像倒影" to "下方镜像倒影"),
                ("激光扫描" to "激光线扫描揭示文字"),
                ("粒子聚合" to "粒子飞向文字聚合再散开"),
                ("故障艺术" to "RGB色差错位+扫描线"),
                ("手写效果" to "从左到右逐渐显露+抖动"),
                ("波形背景" to "音频波形竖条背景"),
                ("星星散落" to "周围星星粒子闪烁"),
                ("旋转出现" to "旋转缩放出现停留消失"),
                ("印度运镜" to "夸张旋转缩放魔性效果"),
            ),
            // 存储值仍对齐 LyricEffectRenderer.EFFECT_*（删掉的 2/3/16/21 号不再出现，老设置不会错位）
            values = listOf(
                0, 1, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                17, 18, 19, 20, 22, 23, 24, 25
            ),
            title = "歌词动画效果",
            summary = "歌词显示时的持续动画效果（选静态/无则正常逐字渲染）"
        )

        // 歌词镜像倒影
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_LYRIC_MIRROR,
            default = false,
            title = "歌词镜像倒影",
            summary = "歌词下方显示半透明倒影（有翻译或预显示下一句时自动不显示）"
        )

        // 换句动画（原在「歌词内容 → 下一句歌词」，3.0.1108 移到动画分组一起管）
        PrefSpinner(
            context = context,
            key = MasterPrefs.KEY_LINE_SWITCH_ANIM,
            default = 1,
            options = listOf(
                ("无动画" to "原始切换，无过渡效果"),
                ("副行放大" to "副行放大并移到主行位置"),
                ("淡入淡出" to "主行淡出、副行原地淡入"),
                ("上滑" to "主行上移淡出、副行自下滑入"),
                ("下滑" to "主行下移淡出、副行自上滑入"),
                ("缩放淡入" to "主行缩小淡出、副行放大淡入"),
                ("3D 翻转" to "沿纵轴翻出翻入"),
                ("左滑" to "主行左滑淡出、副行右侧滑入"),
                ("右滑" to "主行右滑淡出、副行左侧滑入"),
                ("弹性放大" to "副行带过冲弹性放大入场"),
                ("竖向翻转" to "沿横轴上下翻出翻入"),
                ("模糊淡入" to "主行糊掉、副行由糊变清晰（Android 12+）"),
            ),
            title = "换句动画",
            summary = "换句时副行预览/翻译提升为主行的过渡动画（12 种，与状态栏各自独立）"
        )

        // 逐字往上跳（逐字动效：每个字随播放进度依次向上跳动）
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_WORD_MOTION_ENABLED,
            default = false,
            title = "逐字往上跳",
            summary = "每个字随播放进度依次向上跳动"
        )
        }
    }
}

// ================= 三、显示设置 =================

@Composable
fun DisplaySection(context: Context, reorder: SectionReorder? = null) {
    SectionTitle("显示设置", canReorder = reorder?.canReorder == true, onMoveUp = reorder?.onMoveUp, onMoveDown = reorder?.onMoveDown, onMoveTop = reorder?.onMoveTop, onMoveBottom = reorder?.onMoveBottom)
    SectionCard {
        // 歌词居中
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_CENTER_LYRIC,
            default = false,
            title = "歌词居中",
            summary = "歌词在岛内居中显示"
        )

        // 歌词右对齐
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_RIGHT_LYRIC,
            default = false,
            title = "歌词右对齐",
            summary = "歌词在岛内右对齐显示"
        )

        // 音乐信息居中（控制歌名/歌手区域对齐；歌词右对齐不影响歌名）
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_CENTER_MUSIC_INFO,
            default = false,
            title = "音乐信息居中",
            summary = "歌名/歌手信息在岛内居中显示"
        )

        // 歌词颜色样式（0=默认 1=封面取色 2=封面渐变 3=跟随状态栏 4=彩虹 5=自定义颜色）
        var currentTextStyle by remember {
            mutableStateOf(MasterPrefs.getInt(context, MasterPrefs.KEY_TEXT_COLOR_STYLE, 4))
        }
        var previousTextStyle by remember { mutableStateOf(currentTextStyle) }
        var showCustomColorDialog by remember { mutableStateOf(false) }
        // 选中"自定义颜色"（样式 5）时自动弹出颜色选择框
        LaunchedEffect(currentTextStyle) {
            if (currentTextStyle == 5 && previousTextStyle != 5) {
                showCustomColorDialog = true
            }
            previousTextStyle = currentTextStyle
        }
        PrefSpinner(
            context = context,
            key = MasterPrefs.KEY_TEXT_COLOR_STYLE,
            default = 4,
            options = listOf(
                ("默认" to "使用默认文字颜色"),
                ("封面取色" to "从封面提取主色"),
                ("封面渐变" to "封面渐变色彩"),
                ("跟随状态栏" to "随状态栏图标颜色"),
                ("彩虹" to "歌词呈 16 色逐字渐变"),
                ("自定义颜色" to "使用自定义歌词颜色")
            ),
            title = "歌词颜色样式",
            summary = "歌词颜色来源",
            extraWrite = { i ->
                currentTextStyle = i
            }
        )

        // 选中"自定义颜色"后自动弹出的颜色选择框
        if (showCustomColorDialog) {
            MiuixColorDialog(
                title = "自定义颜色",
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
                onSelected = { color ->
                    MasterPrefs.setInt(context, MasterPrefs.KEY_HOOK_TEXT_COLOR, color)
                    MasterPrefs.setInt(context, MasterPrefs.KEY_TEXT_COLOR_STYLE, 5)
                    showCustomColorDialog = false
                },
                onDismiss = { showCustomColorDialog = false }
            )
        }

        // 专辑封面旋转（映射封面样式 3=旋转圆形）
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_ALBUM_COVER_ROTATION,
            default = false,
            title = "专辑封面旋转",
            summary = "播放时专辑封面匀速旋转，暂停时停转",
            extraWrite = { value ->
                MasterPrefs.setInt(
                    context,
                    MasterPrefs.KEY_ISLAND_ALBUM_COVER_STYLE,
                    if (value) 3 else 1
                )
            }
        )

        // 音乐律动样式（0=默认 1=封面色 2=封面渐变 3=隐藏）
        PrefSpinner(
            context = context,
            key = MasterPrefs.KEY_ISLAND_MUSIC_WAVE_STYLE,
            default = 0,
            options = listOf(
                ("默认律动" to "显示标准音乐律动波形"),
                ("跟随封面色" to "律动颜色跟随专辑封面主色"),
                ("封面色渐变" to "律动使用封面颜色的渐变"),
                ("隐藏律动" to "不显示音乐律动")
            ),
            title = "音乐律动样式",
            summary = "超级岛音乐律动波形的显示方式"
        )

        // 系统超级岛设置（跳转）
        ArrowPreference(
            title = "系统超级岛设置",
            summary = "跳转小米系统设置，管理哪些应用能上岛",
            onClick = {
                runCatching {
                    val intent = android.content.Intent(
                        "miui.settings.FOCUS_NOTIFICATION_SETTINGS"
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        )
    }
}

// ================= 四、超级岛特效 =================

@Composable
fun SpecialEffectsSection(context: Context, reorder: SectionReorder? = null) {
    SectionTitle("超级岛特效", canReorder = reorder?.canReorder == true, onMoveUp = reorder?.onMoveUp, onMoveDown = reorder?.onMoveDown, onMoveTop = reorder?.onMoveTop, onMoveBottom = reorder?.onMoveBottom)
    SectionCard {
        // 超级岛进度条（映射进度光效）
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_PROGRESS_BAR_ENABLED,
            default = false,
            title = "超级岛进度条",
            summary = "在超级岛四周显示歌曲播放进度条",
            extraWrite = { value ->
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_PROGRESS_GLOW, value)
                // 仅首次启用时初始化起点（0=起点·上），之后保留用户设置的位置
                if (value && !MasterPrefs.contains(context, MasterPrefs.KEY_ISLAND_PROGRESS_STYLE)) {
                    MasterPrefs.setInt(context, MasterPrefs.KEY_ISLAND_PROGRESS_STYLE, 0)
                }
            }
        )

        // 底圈：环绕岛的完整轨道（独立开关，与进度弧分开控制）
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_PROGRESS_BAR_TRACK_ENABLED,
            default = true,
            title = "底圈",
            summary = "显示进度条底部的完整轨道圈（关闭后只画进度弧）",
            extraWrite = { value ->
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_HOOK_ISLAND_PROGRESS_TRACK, value)
            }
        )

        // 颜色模式：彩虹渐变 / 跟随封面
        var colorMode by remember {
            mutableStateOf(
                MasterPrefs.getInt(context, MasterPrefs.KEY_ISLAND_PROGRESS_BAR_COLOR_MODE, 0)
            )
        }
        SwitchPreference(
            checked = colorMode == 0,
            onCheckedChange = { value ->
                if (value) {
                    colorMode = 0
                    MasterPrefs.setInt(context, MasterPrefs.KEY_ISLAND_PROGRESS_BAR_COLOR_MODE, 0)
                    MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_PROGRESS_GRADIENT, true)
                }
            },
            title = "彩虹渐变",
            summary = "进度条使用彩虹渐变色"
        )
        SwitchPreference(
            checked = colorMode == 1,
            onCheckedChange = { value ->
                if (value) {
                    colorMode = 1
                    MasterPrefs.setInt(context, MasterPrefs.KEY_ISLAND_PROGRESS_BAR_COLOR_MODE, 1)
                    MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_PROGRESS_GRADIENT, false)
                }
            },
            title = "跟随封面色",
            summary = "进度条颜色跟随专辑封面主色"
        )

        // 进度条起点
        PrefSpinner(
            context = context,
            key = MasterPrefs.KEY_ISLAND_PROGRESS_BAR_STYLE,
            default = 0,
            options = listOf(
                ("起点·上" to "进度条从上侧起点顺时针推进"),
                ("起点·右" to "进度条从右侧起点顺时针推进"),
                ("起点·下" to "进度条从下侧起点顺时针推进"),
                ("起点·左" to "进度条从左侧起点顺时针推进")
            ),
            title = "进度条起点",
            summary = "选择进度条环绕的起始位置",
            extraWrite = { value ->
                MasterPrefs.setInt(context, MasterPrefs.KEY_ISLAND_PROGRESS_STYLE, value)
            }
        )

        // ===== 超级光效（超级岛外发光，老词幕 IslandOuterGlowHooker 移植） =====
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_OUTER_GLOW_ENABLED,
            default = false,
            title = "超级光效",
            summary = "岛外围光晕发光效果，颜色跟随封面或使用固定色",
            extraWrite = { value ->
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_HOOK_ISLAND_OUTER_GLOW_ENABLED, value)
            }
        )

        // 光效颜色模式（0=跟随封面 1=固定色），选中"固定色"自动弹颜色选择框
        var currentGlowColorMode by remember {
            mutableStateOf(MasterPrefs.getInt(context, MasterPrefs.KEY_ISLAND_OUTER_GLOW_OUTER_MODE, 0))
        }
        var previousGlowColorMode by remember { mutableStateOf(currentGlowColorMode) }
        var showGlowColorDialog by remember { mutableStateOf(false) }
        LaunchedEffect(currentGlowColorMode) {
            if (currentGlowColorMode == 1 && previousGlowColorMode != 1) {
                showGlowColorDialog = true
            }
            previousGlowColorMode = currentGlowColorMode
        }
        PrefSpinner(
            context = context,
            key = MasterPrefs.KEY_ISLAND_OUTER_GLOW_OUTER_MODE,
            default = 0,
            options = listOf(
                ("跟随封面" to "光效颜色跟随专辑封面主色"),
                ("固定色" to "光效使用下方选择的固定颜色")
            ),
            title = "光效颜色模式",
            summary = "光效颜色来源：跟随封面主色或使用固定色",
            extraWrite = { value ->
                currentGlowColorMode = value
                MasterPrefs.setInt(context, MasterPrefs.KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_MODE, value)
            }
        )

        // 选中"固定色"后自动弹出的光效颜色选择框
        if (showGlowColorDialog) {
            MiuixColorDialog(
                title = "光效固定色",
                presetColors = remember {
                    listOf(
                        0xFF4285F4.toInt() to "Google 蓝",
                        0xFFFFFFFF.toInt() to "纯白",
                        0xFFFFEB3B.toInt() to "明黄",
                        0xFFFF9800.toInt() to "橙色",
                        0xFF4CAF50.toInt() to "绿色",
                        0xFF2196F3.toInt() to "蓝色",
                        0xFFE91E63.toInt() to "粉色",
                        0xFF9C27B0.toInt() to "紫色"
                    )
                },
                onSelected = { color ->
                    MasterPrefs.setInt(context, MasterPrefs.KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_COLOR, color)
                    MasterPrefs.setInt(context, MasterPrefs.KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_MODE, 1)
                    showGlowColorDialog = false
                },
                onDismiss = { showGlowColorDialog = false }
            )
        }

    }
}

// ================= 五、岛背景 =================

/** 把材质类型 index 写成上游 HyperIsland 的 material JSON（type+blur+softSchema），大/小/展开跟随大岛。 */
private fun writeUpstreamMaterialJson(context: Context, typeIndex: Int) {
    val typeName = when (typeIndex) {
        0 -> "gaussian"
        1 -> "highlight_glass"
        2 -> "liquid_glass"
        3 -> "soft_glass"
        else -> return
    }
    val radius = MasterPrefs.getInt(context, MasterPrefs.KEY_ISLAND_BLUR_BIG_RADIUS, 35)
    val json = org.json.JSONObject().apply {
        put("type", typeName)
        put("blur", radius)
        put("blendColor", "#0F0F0F")
        if (typeName == "soft_glass") put("softSchema", 2)
    }.toString()
    MasterPrefs.setString(context, MasterPrefs.KEY_ISLAND_MATERIAL_BIG, json)
    MasterPrefs.setString(context, MasterPrefs.KEY_ISLAND_MATERIAL_SMALL, json)
    MasterPrefs.setString(context, MasterPrefs.KEY_ISLAND_MATERIAL_EXPAND, json)
    MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_MATERIAL_SMALL_FOLLOW_BIG, true)
    MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_MATERIAL_EXPAND_FOLLOW_BIG, true)
    // ★ 不再强制打开毛玻璃总开关：否则用户关掉总开关后，只要切过一次材质/调过模糊
    //   就会被强制重新打开（"关不掉"）。blur_big_enabled 完全跟随总开关：
    //   总开关开 → 切材质/调模糊保持开启（改完立即生效）；总开关关 → 保持关闭。
    if (MasterPrefs.getBoolean(context, MasterPrefs.KEY_ISLAND_MATERIAL_ENABLED, false)) {
        MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_BLUR_BIG_ENABLED, true)
    }
}

@Composable
fun IslandBackgroundSection(context: Context, reorder: SectionReorder? = null) {
    SectionTitle("岛背景（试验性功能）", canReorder = reorder?.canReorder == true, onMoveUp = reorder?.onMoveUp, onMoveDown = reorder?.onMoveDown, onMoveTop = reorder?.onMoveTop, onMoveBottom = reorder?.onMoveBottom)
    SectionCard {
        // 毛玻璃（HyperOS 系统实时模糊挂到岛背景）
        // ★ 试验性功能：默认关闭；开启前必须点"同意"（说明可能不稳定），关掉不需要确认。
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_MATERIAL_ENABLED,
            default = false,
            title = "毛玻璃",
            summary = "试验性功能，默认关闭、可能不稳定。用 HyperOS 系统实时模糊（高斯/玻璃材质）替换岛背景，开时关闭透明/磨砂/柔光/封面",
            confirmOnEnable = SwitchConfirmSpec(
                title = "开启试验性功能？",
                message = "岛背景 / 毛玻璃属于试验性功能，可能不正常：岛内容可能突然消失、玻璃可能不显示、" +
                    "切换状态栏歌词模式与超级岛模式后可能不恢复等。确定同意并开启吗？",
                confirmText = "同意并开启",
                dismissText = "取消",
                // 只第一次开启时提示，同意后不再弹
                onceKey = MasterPrefs.KEY_ISLAND_EXPERIMENT_CONSENT
            ),
            extraWrite = { value ->
                // 毛玻璃开关必须同步 blur_big_enabled，否则 maybeAttach 判 isActive=false、毛玻璃永不挂载
                // ★ 且要同步 small/expand 三档：上游拦截看 anyBlurEnabled（三档任一开就算开），
                //   只关 big 档则关总开关后切材质仍生效（材质不受开关控制）。
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_BLUR_BIG_ENABLED, value)
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_BLUR_SMALL_ENABLED, value)
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_BLUR_EXPAND_ENABLED, value)
            }
        )

        // 材质类型
        PrefSpinner(
            context = context,
            key = MasterPrefs.KEY_ISLAND_MATERIAL_TYPE,
            default = 0,
            options = listOf(
                "高斯模糊" to "经典系统磨砂玻璃（实时模糊+圆角裁剪）",
                "高光玻璃" to "玻璃高光反射",
                "液态玻璃" to "液态折射效果",
                "柔光玻璃" to "柔和雾面透光"
            ),
            title = "材质类型",
            summary = "毛玻璃的材质样式（四种实时生效；总开关未开启时选择不生效）",
            guard = { MasterPrefs.isMaterialMasterOn(context) },
            onGuarded = {
                Toast.makeText(context, "「毛玻璃」总开关未开启，材质设置不生效", Toast.LENGTH_SHORT).show()
            },
            extraWrite = { index ->
                // 上游 HyperIsland 只认 material_big_config JSON 的 type 字段；
                // 这里把设置的材质 index 同步写成上游 JSON（type+blur+softSchema），
                // 否则上游永远按 fromLegacy 解析成高斯，四种材质看起来一样。
                writeUpstreamMaterialJson(context, index)
            }
        )

        // 模糊强度
        NumberInputPreference(
            context = context,
            key = MasterPrefs.KEY_ISLAND_BLUR_BIG_RADIUS,
            default = 35,
            range = 0..100,
            unit = "",
            title = "模糊强度",
            extraWrite = {
                // 同步刷新上游材质 JSON 的 blur（保持当前材质 type）
                writeUpstreamMaterialJson(
                    context,
                    MasterPrefs.getInt(context, MasterPrefs.KEY_ISLAND_MATERIAL_TYPE, 0)
                )
            }
        )

        // 3.0.783：删除"大岛背景图"设置项（用户确认无用，留空纯毛玻璃）

        // 下载岛背景预设（预设配置页：卡片列表 → 勾选要应用的配置项 → 应用）
        val hubScope = rememberCoroutineScope()
        var hubPresets by remember { mutableStateOf<List<HubPresetClient.PresetSummary>>(emptyList()) }
        var hubLoading by remember { mutableStateOf(false) }
        var hubDetailLoading by remember { mutableStateOf(false) }
        var hubShowDialog by remember { mutableStateOf(false) }
        // 当前展开的预设（null = 列表层，非 null = 详情层）
        var hubActive by remember { mutableStateOf<HubPresetClient.PresetSummary?>(null) }
        var hubSections by remember { mutableStateOf<JSONObject?>(null) }
        var selectedSections by remember { mutableStateOf(setOf<String>()) }
        ArrowPreference(
            title = "下载岛背景预设",
            summary = "预设卡片列表，勾选要应用的配置项后应用（词岛不支持的自动跳过；毛玻璃总开关未开启时写入无效）",
            onClick = {
                if (hubLoading || hubDetailLoading) return@ArrowPreference
                if (!MasterPrefs.isMaterialMasterOn(context)) {
                    Toast.makeText(context, "「毛玻璃」总开关未开启，岛背景预设写不进去", Toast.LENGTH_SHORT).show()
                    return@ArrowPreference
                }
                hubLoading = true
                hubScope.launch {
                    val presets = withContext(Dispatchers.IO) { HubPresetClient.listAll() }
                    hubLoading = false
                    if (presets.isEmpty()) {
                        Toast.makeText(context, "无可用预设或网络失败", Toast.LENGTH_SHORT).show()
                    } else {
                        hubPresets = presets
                        hubActive = null
                        hubSections = null
                        hubShowDialog = true
                    }
                }
            }
        )
        if (hubShowDialog) {
            WindowDialog(
                show = true,
                title = if (hubActive == null) "选择岛背景预设" else hubActive!!.title,
                onDismissRequest = {
                    if (hubActive == null) {
                        hubShowDialog = false
                    } else {
                        hubActive = null
                        hubSections = null
                    }
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 内容区：方格网格（预设列表 / 详情勾选），限高滚动
                    if (hubActive == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            hubPresets.chunked(2).forEach { chunk ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    chunk.forEach { preset ->
                                        TextButton(
                                            text = preset.title +
                                                (if (preset.author.isNotBlank()) "\n${preset.author}" else "") +
                                                (if (preset.version.isNotBlank()) " v${preset.version}" else ""),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                hubActive = preset
                                                hubDetailLoading = true
                                                hubScope.launch {
                                                    val sections = withContext(Dispatchers.IO) {
                                                        runCatching { HubPresetClient.detailSections(preset.hubId) }.getOrNull()
                                                    }
                                                    hubDetailLoading = false
                                                    if (sections == null) {
                                                        Toast.makeText(context, "拉取预设详情失败", Toast.LENGTH_SHORT).show()
                                                        hubActive = null
                                                    } else {
                                                        hubSections = sections
                                                        selectedSections = buildSet {
                                                            sections.keys().forEach { key ->
                                                                val sec = sections.optJSONObject(key)
                                                                if (sec != null && sec.length() > 0) add(key)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val sections = hubSections
                        if (hubDetailLoading || sections == null) {
                            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                Text(text = "正在加载预设配置…")
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 460.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                buildList { sections.keys().forEach { add(it) } }.chunked(2).forEach { chunk ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        chunk.forEach { id ->
                                            val sec = sections.optJSONObject(id)
                                            val count = sec?.length() ?: 0
                                            val checked = id in selectedSections
                                            TextButton(
                                                text = (if (checked) "☑ " else "☐ ") +
                                                    HubPresetClient.sectionTitleZh(id) + "\n（$count 项）",
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    selectedSections =
                                                        if (checked) selectedSections - id else selectedSections + id
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 底部固定：永远可见，不随列表滚动
                    Spacer(Modifier.height(8.dp))
                    if (hubActive == null) {
                        TextButton(
                            text = "关闭",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { hubShowDialog = false }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                text = "取消",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hubActive = null
                                    hubSections = null
                                }
                            )
                            TextButton(
                                text = "确定应用（${selectedSections.size}）",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val sections = hubSections ?: return@TextButton
                                    if (selectedSections.isEmpty()) {
                                        Toast.makeText(context, "请先勾选要应用的配置项", Toast.LENGTH_SHORT).show()
                                        return@TextButton
                                    }
                                    hubShowDialog = false
                                    hubScope.launch {
                                        val msg = withContext(Dispatchers.IO) {
                                            HubPresetClient.applySections(context, sections, selectedSections)
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 透明
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_TRANSPARENT_BIG_ENABLED,
            default = false,
            title = "透明",
            summary = "展开大卡片和收起小胶囊背景都透明（去掉黑色背景透出桌面）",
            extraWrite = { value ->
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_TRANSPARENT_ENABLED, value)
            }
        )
    }
}

// ================= 六、歌词内容 =================

@Composable
fun LyricContentSection(context: Context, reorder: SectionReorder? = null) {
    SectionTitle("歌词内容", canReorder = reorder?.canReorder == true, onMoveUp = reorder?.onMoveUp, onMoveDown = reorder?.onMoveDown, onMoveTop = reorder?.onMoveTop, onMoveBottom = reorder?.onMoveBottom)
    SectionCard {
        // ===== 翻译（双行/副行翻译相关全部归组）=====
        SubGroupBox("翻译") {
            // 显示翻译（V3 直写键：开=开，不再写 disable 取反）
            PrefSwitch(
                context = context,
                key = MasterPrefs.KEY_SHOW_TRANSLATION,
                default = true,
                title = "显示翻译",
                summary = "在主歌词下方显示翻译歌词"
            )

            // 翻译独占
            PrefSwitch(
                context = context,
                key = MasterPrefs.KEY_TRANSLATION_ONLY,
                default = false,
                title = "翻译独占",
                summary = "只显示翻译歌词，不显示原文"
            )

            // 交换翻译
            PrefSwitch(
                context = context,
                key = MasterPrefs.KEY_SWAP_TRANSLATION,
                default = false,
                title = "交换翻译",
                summary = "翻译歌词显示在原文上方"
            )

            // 自动切换翻译
            PrefSwitch(
                context = context,
                key = MasterPrefs.KEY_AUTO_SWITCH_TRANSLATION,
                default = false,
                title = "自动切换翻译",
                summary = "无翻译内容时自动切换翻译显示策略"
            )
        }

        // ===== 下一句歌词（三件套：预览 / 动画 / 字号）归组 =====
        SubGroupBox("下一句歌词") {
            // 预显示下一句
            PrefSwitch(
                context = context,
                key = MasterPrefs.KEY_NEXT_LYRIC_LINE,
                default = false,
                title = "预显示下一句",
                summary = "在当前句下方预显示下一句歌词"
            )

            // 下一句动画已移到「歌词效果」分组（与歌词动画效果放一起，便于集中调动画）

            // 文字大小比例（渲染键 Float 0.7=70%）：副行（下一句预览/翻译）相对主行的字号
            NumberInputPreference(
                context = context,
                key = MasterPrefs.KEY_ISLAND_TEXT_SIZE_RATIO_PCT,
                default = 70,
                range = 50..150,
                unit = "%",
                title = "文字大小比例",
                extraWrite = {
                    MasterPrefs.setFloat(context, MasterPrefs.KEY_TEXT_SIZE_RATIO, it / 100f)
                }
            )
        }

        // ===== 逐字与高亮 =====
        SubGroupBox("逐字与高亮") {
            // 逐字进度（映射音节相对进度）
            PrefSwitch(
                context = context,
                key = MasterPrefs.KEY_ISLAND_RELATIVE_PROGRESS,
                default = true,
                title = "逐字进度",
                summary = "根据播放进度逐字高亮歌词",
                extraWrite = { value ->
                    MasterPrefs.setBoolean(context, MasterPrefs.KEY_SYLLABLE_RELATIVE, value)
                }
            )

            // 拉长音发光、逐字行显示 已于 3.0.907 移除（只保留逐字进度：开=逐字，关=整段高亮）
        }
    }
}

// ================= 七、字体设置 =================

@Composable
fun FontSection(context: Context, reorder: SectionReorder? = null) {
    SectionTitle("字体设置", canReorder = reorder?.canReorder == true, onMoveUp = reorder?.onMoveUp, onMoveDown = reorder?.onMoveDown, onMoveTop = reorder?.onMoveTop, onMoveBottom = reorder?.onMoveBottom)
    SectionCard {
        // 内置字体库：从系统里现成可用的中文字体里点选，不用再手填路径
        BuiltinFontPreference(context)

        // 字体文件路径
        StringInputPreference(
            context = context,
            key = MasterPrefs.KEY_ISLAND_TYPEFACE_PATH,
            default = "",
            title = "字体文件路径",
            summary = "自定义字体文件的绝对路径（留空使用系统默认字体）",
            extraWrite = {
                MasterPrefs.setString(context, MasterPrefs.KEY_CUSTOM_FONT_PATH, it)
            }
        )

        // 字重
        NumberInputPreference(
            context = context,
            key = MasterPrefs.KEY_ISLAND_FONT_WEIGHT,
            default = 600,
            range = 0..1000,
            unit = "",
            title = "字重",
            extraWrite = {
                MasterPrefs.setInt(context, MasterPrefs.KEY_FONT_WEIGHT, it)
            }
        )

        // 粗体
        var bold by remember {
            mutableStateOf(
                MasterPrefs.getBoolean(context, MasterPrefs.KEY_ISLAND_TYPEFACE_BOLD, false)
            )
        }
        SwitchPreference(
            checked = bold,
            onCheckedChange = { value ->
                bold = value
                val weight = MasterPrefs.getInt(context, MasterPrefs.KEY_ISLAND_FONT_WEIGHT, 600)
                MasterPrefs.setInt(context, MasterPrefs.KEY_FONT_WEIGHT, if (value) 700 else weight)
            },
            title = "粗体",
            summary = "使用粗体字重（700）"
        )

        // 斜体
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_ISLAND_TYPEFACE_ITALIC,
            default = false,
            title = "斜体",
            summary = "使用斜体字型",
            extraWrite = { value ->
                MasterPrefs.setBoolean(context, MasterPrefs.KEY_FONT_ITALIC, value)
            }
        )

        // 窄拉丁字体
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_NARROW_LATIN_FONT,
            default = false,
            title = "窄拉丁字体",
            summary = "拉丁字符使用窄字体，节省岛内空间"
        )
    }
}

// ================= 八、权限与白名单 =================

@Composable
fun HyperlyricExtrasSection(context: Context, reorder: SectionReorder? = null) {
    SectionTitle("权限与白名单", canReorder = reorder?.canReorder == true, onMoveUp = reorder?.onMoveUp, onMoveDown = reorder?.onMoveDown, onMoveTop = reorder?.onMoveTop, onMoveBottom = reorder?.onMoveBottom)
    SectionCard {
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_REMOVE_FOCUS_WHITELIST,
            default = true,
            title = "解除焦点通知白名单",
            summary = "移除系统对哪些应用能进通知中心的白名单限制"
        )
        PrefSwitch(
            context = context,
            key = MasterPrefs.KEY_REMOVE_ISLAND_WHITELIST,
            default = true,
            title = "解除超级岛白名单",
            summary = "移除系统对哪些应用能上超级岛的白名单限制"
        )
    }
}

// ================= 内容模式选择对话框 =================

@Composable
private fun ContentModeDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    // 系统级 Dialog（androidx.compose.ui.window.Dialog）：独立窗口层，
    // 作为二级弹窗时盖在一级 WindowDialog 之上，不会被遮挡、点击正常。
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material.Surface(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .heightIn(max = 480.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            color = MiuixTheme.colorScheme.surface,
            elevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "选择显示内容",
                    style = MiuixTheme.textStyles.title2,
                    modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp)
                )
                contentCategories.forEach { category ->
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = category.title,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                        )
                        category.items.forEach { (value, name) ->
                            TextButton(
                                text = if (value == current) "✓ $name" else name,
                                onClick = {
                                    onSelected(value)
                                    onDismiss()
                                },
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}
