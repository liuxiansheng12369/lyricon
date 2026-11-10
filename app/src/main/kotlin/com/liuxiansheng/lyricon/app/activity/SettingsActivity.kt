/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.app.activity

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import com.liuxiansheng.lyricon.app.compose.effect.AppBackground
import com.liuxiansheng.lyricon.app.compose.effect.BgEffectBackground
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import com.liuxiansheng.lyricon.app.util.MasterPrefs
import com.liuxiansheng.lyricon.app.util.PrefsBridge
import com.liuxiansheng.lyricon.app.util.SectionOrder
import com.liuxiansheng.lyricon.app.util.SettingsBackup
import com.liuxiansheng.lyricon.bridge.BridgeSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.foundation.layout.Row
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.TextStyle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置界面（Miuix / Compose 重写版）。
 *
 * 结构按老词幕超级岛分组（基础设置/歌名歌曲动效/歌词效果/显示设置/超级岛特效/岛背景/歌词内容/字体设置），
 * 并补回 HyperLyric 移植的缺失设置项（布局样式/卡片主题/动画跑马灯/翻译/媒体卡片等）。
 * 所有设置写入 MasterPrefs（本地 + Xposed 远程 prefs），SystemUI 侧 HookEntry 监听生效。
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BridgeSender.initialize(applicationContext)
        setContent { SkinHost { SettingsScreen() } }
    }
}

@Composable
internal fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SkinTheme {
        // 状态栏图标（时钟/信号/WiFi/电量）按页面背景反色：深背景→浅图标，浅背景→深图标
        val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
        SideEffect {
            val window = (context as Activity).window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !isDark
        }
        // 应用全局背景（受"流光背景"开关控制）
        AppBackground(
            isDarkTheme = isDark,
        ) {
        // Miuix Scaffold 提供 Overlay 弹窗宿主（OverlayDialog/OverlayBottomSheet 才能弹出）
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            // 启动同步：把"分离歌词模式"开关当前值落实到 HyperLyric 渲染键。
            // 旧版本（≤3.0.96）只写 island_split_lyric_mode，从未写 key_hook_lyric_mode，
            // 导致开关显示开但实际未生效（重启 SystemUI 后恢复默认不分段）。
            // 进入设置页即按开关真相补写一次渲染键，无需用户重拨开关。
            LaunchedEffect(Unit) {
                // 默认值初始化（对齐老词幕：App 是唯一写入者，SystemUI 只读 remote prefs）：
                // 首次启动（用户从未设置过）才写默认 true，绝不覆盖用户已设的开/关。
                if (!MasterPrefs.contains(context, MasterPrefs.KEY_ENABLE_SUPER_ISLAND)) {
                    MasterPrefs.setBoolean(context, MasterPrefs.KEY_ENABLE_SUPER_ISLAND, true)
                }
                if (!MasterPrefs.contains(context, MasterPrefs.KEY_ISLAND_DISABLE_WIDTH_LIMIT)) {
                    MasterPrefs.setBoolean(context, MasterPrefs.KEY_ISLAND_DISABLE_WIDTH_LIMIT, true)
                }
                val split = MasterPrefs.getBoolean(
                    context,
                    MasterPrefs.KEY_ISLAND_SPLIT_LYRIC_MODE,
                    true
                )
                MasterPrefs.setInt(
                    context,
                    MasterPrefs.KEY_LYRIC_MODE,
                    if (split) 1 else 0
                )
                MasterPrefs.setInt(
                    context,
                    MasterPrefs.KEY_ISLAND_CONTENT_LEFT,
                    if (split) 7 else 8
                ) // 7=lyric, 8=custom_music_info
                MasterPrefs.setInt(context, MasterPrefs.KEY_ISLAND_CONTENT_RIGHT, 7)
            }

            // 右上角重启按钮：与大卡片同款磨砂圆角卡片（半透明白底 + 白边 + ↻），避开状态栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
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

            // 内容（3.0.777：分组顺序可长按标题排序，持久化到 pref_settings_section_order）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                val localContext = LocalContext.current
                val settingsSectionIds = listOf(
                    "super", "basic", "effect", "display", "special",
                    "background", "content", "font", "extras",
                )
                var settingsOrder by remember {
                    mutableStateOf(
                        SectionOrder.read(
                            localContext,
                            "pref_settings_section_order",
                            settingsSectionIds,
                        )
                    )
                }
                val moveSettings: (Int, Int) -> Unit = { from, to ->
                    val list = settingsOrder.toMutableList()
                    val id = list.removeAt(from)
                    list.add(to.coerceIn(0, list.size), id)
                    settingsOrder = list
                    SectionOrder.write(localContext, "pref_settings_section_order", list)
                }
                settingsOrder.forEachIndexed { index, id ->
                    val canUp = index > 0
                    val canDown = index < settingsOrder.size - 1
                    val reorder = SectionReorder(
                        canReorder = true,
                        onMoveUp = if (canUp) { { moveSettings(index, index - 1) } } else null,
                        onMoveDown = if (canDown) { { moveSettings(index, index + 1) } } else null,
                        onMoveTop = if (canUp) { { moveSettings(index, 0) } } else null,
                        onMoveBottom = if (canDown) {
                            { moveSettings(index, settingsOrder.size - 1) }
                        } else null,
                    )
                    when (id) {
                        "super" -> SuperIslandSection(context, reorder)
                        "basic" -> BasicSettingsSection(context, reorder)
                        "effect" -> LyricEffectSection(context, reorder)
                        "display" -> DisplaySection(context, reorder)
                        "special" -> SpecialEffectsSection(context, reorder)
                        "background" -> IslandBackgroundSection(context, reorder)
                        "content" -> LyricContentSection(context, reorder)
                        "font" -> FontSection(context, reorder)
                        "extras" -> HyperlyricExtrasSection(context, reorder)
                    }
                }
            }
        }
        }
        }
    }
}

// ================= 通用组件 =================

/** 分组排序回调（3.0.777：主页区块 / 设置页分组 长按标题弹出排序菜单） */
data class SectionReorder(
    val canReorder: Boolean = false,
    val onMoveUp: (() -> Unit)? = null,
    val onMoveDown: (() -> Unit)? = null,
    val onMoveTop: (() -> Unit)? = null,
    val onMoveBottom: (() -> Unit)? = null,
)

/** 分组标题（3.0.777：canReorder=true 时长按标题弹 上移/下移/置顶/置底 排序菜单） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionTitle(
    text: String,
    canReorder: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onMoveTop: (() -> Unit)? = null,
    onMoveBottom: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val skin = LocalHomeSkin.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, top = 16.dp, end = 26.dp, bottom = 8.dp)
            .then(
                if (canReorder) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { menuOpen = true },
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = skin?.textPrimary ?: MiuixTheme.colorScheme.onSurface,
            )
            if (canReorder) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "⇅",
                    fontSize = 12.sp,
                    color = skin?.textSecondary ?: MiuixTheme.colorScheme.outline,
                )
            }
        }
        if (canReorder) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    onClick = {
                        menuOpen = false
                        onMoveUp?.invoke()
                    },
                    enabled = onMoveUp != null,
                ) {
                    Text("上移")
                }
                DropdownMenuItem(
                    onClick = {
                        menuOpen = false
                        onMoveDown?.invoke()
                    },
                    enabled = onMoveDown != null,
                ) {
                    Text("下移")
                }
                DropdownMenuItem(
                    onClick = {
                        menuOpen = false
                        onMoveTop?.invoke()
                    },
                    enabled = onMoveTop != null,
                ) {
                    Text("置顶")
                }
                DropdownMenuItem(
                    onClick = {
                        menuOpen = false
                        onMoveBottom?.invoke()
                    },
                    enabled = onMoveBottom != null,
                ) {
                    Text("置底")
                }
            }
        }
    }
}

/** 分组卡片容器 */
@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        content()
    }
}

/** 内嵌分组：给一组相关设置项加组标题+内容左缩进，视觉上归成一个选项组
 * （如"下一句歌词"三件套：预显示下一句 / 下一句动画 / 文字大小比例）。
 * 3.0.638：去掉边框（用户反馈加框不好看），改用标题分隔+缩进。 */
@Composable
fun SubGroupBox(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp)
        )
        Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp)) {
            content()
        }
    }
}

/** 数字输入项（对话框输入，状态化：写 prefs + 更新 UI；inline=true 时行内直接输入，不弹第二层弹窗，避免嵌套 Dialog 点击失效） */
@Composable
fun NumberInputPreference(
    context: android.content.Context,
    key: String,
    default: Int,
    range: IntRange,
    unit: String = "",
    title: String,
    extraWrite: ((Int) -> Unit)? = null,
    bridge: PrefsBridge = MasterPrefs,
    inline: Boolean = false
) {
    var value by remember { mutableStateOf(bridge.getInt(context, key, default)) }
    var showDialog by remember { mutableStateOf(false) }

    if (inline) {
        // 行内数字输入：不弹第二层弹窗（嵌套 Dialog 点击失效），点文本框直接改（数字键盘），
        // 用字符串状态驱动显示：允许删空（删空不写 prefs、不回显旧值），非空数字即时写 prefs
        var text by remember { mutableStateOf(bridge.getInt(context, key, default).toString()) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) { }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
                Text("${text.ifEmpty { "0" }}$unit", fontSize = 12.sp, color = Color.Gray)
            }
            BasicTextField(
                value = text,
                onValueChange = { input ->
                    val cleaned = input.filter { it.isDigit() }.take(3)
                    text = cleaned
                    val parsed = cleaned.toIntOrNull()
                    if (parsed != null) {
                        val clamped = parsed.coerceIn(range.first, range.last)
                        if (clamped != value) {
                            value = clamped
                            bridge.setInt(context, key, clamped)
                            extraWrite?.invoke(clamped)
                        }
                    }
                    // 删空时 parsed == null：保留空显示，不写 prefs（避免 0 被 clamp 成 1）
                },
                textStyle = TextStyle(fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .width(76.dp)
                    .background(Color(0xFFF2F3F5), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        return
    }

    ArrowPreference(
        title = title,
        summary = "$value$unit",
        onClick = { showDialog = true }
    )

    if (showDialog) {
        MiuixInputDialog(
            title = title,
            initial = value.toString(),
            rangeLabel = "输入数值（${range.first}-${range.last}$unit）",
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null) {
                    val clamped = parsed.coerceIn(range.first, range.last)
                    value = clamped
                    bridge.setInt(context, key, clamped)
                    extraWrite?.invoke(clamped)
                }
            },
            onDismiss = { showDialog = false }
        )
    }
}

/** 文本输入项（对话框输入，状态化） */
@Composable
fun StringInputPreference(
    context: android.content.Context,
    key: String,
    default: String,
    title: String,
    summary: String,
    extraWrite: ((String) -> Unit)? = null,
    bridge: PrefsBridge = MasterPrefs
) {
    var value by remember { mutableStateOf(bridge.getString(context, key, default) ?: default) }
    var showDialog by remember { mutableStateOf(false) }

    ArrowPreference(
        title = title,
        summary = summary,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        MiuixInputDialog(
            title = title,
            initial = value,
            rangeLabel = "输入内容",
            onConfirm = { input ->
                val newValue = input.trim()
                value = newValue
                bridge.setString(context, key, newValue)
                extraWrite?.invoke(newValue)
            },
            onDismiss = { showDialog = false }
        )
    }
}

/** 颜色输入项（预设色板对话框，状态化） */
@Composable
fun ColorInputPreference(
    context: android.content.Context,
    key: String,
    default: Int,
    title: String,
    summary: String,
    presetColors: List<Pair<Int, String>>,
    extraWrite: ((Int) -> Unit)? = null,
    bridge: PrefsBridge = MasterPrefs
) {
    var value by remember { mutableStateOf(bridge.getInt(context, key, default)) }
    var showDialog by remember { mutableStateOf(false) }

    ArrowPreference(
        title = title,
        summary = summary,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        MiuixColorDialog(
            title = title,
            presetColors = presetColors,
            onSelected = { c ->
                value = c
                bridge.setInt(context, key, c)
                extraWrite?.invoke(c)
            },
            onDismiss = { showDialog = false }
        )
    }
}

/** 开启前的二次确认文案（试验性功能专用）。传了就表示"打开前必须先点同意"。
 *  [onceKey] 非空时表示"只第一次弹"：同意后把该键置 true，之后开启不再弹框。 */
data class SwitchConfirmSpec(
    val title: String,
    val message: String,
    val confirmText: String = "同意并开启",
    val dismissText: String = "取消",
    val onceKey: String? = null
)

/** 开关项（状态化：写 prefs + 更新 UI + 可选双写） */
@Composable
fun PrefSwitch(
    context: android.content.Context,
    key: String,
    default: Boolean,
    title: String,
    summary: String,
    extraWrite: ((Boolean) -> Unit)? = null,
    bridge: PrefsBridge = MasterPrefs,
    confirmOnEnable: SwitchConfirmSpec? = null
) {
    var checked by remember { mutableStateOf(bridge.getBoolean(context, key, default)) }
    var showConfirm by remember { mutableStateOf(false) }

    fun applyValue(value: Boolean) {
        checked = value
        bridge.setBoolean(context, key, value)
        extraWrite?.invoke(value)
    }

    top.yukonga.miuix.kmp.preference.SwitchPreference(
        checked = checked,
        onCheckedChange = { value ->
            // 试验性功能：只拦"关→开"这一步；[onceKey] 有值且已同意过就不再弹（只第一次弹）。
            val spec = confirmOnEnable
            val alreadyAgreed = spec?.onceKey?.let { bridge.getBoolean(context, it, false) } ?: false
            if (value && !checked && spec != null && !alreadyAgreed) {
                showConfirm = true
            } else {
                applyValue(value)
            }
        },
        title = title,
        summary = summary
    )

    if (showConfirm) {
        val spec = confirmOnEnable!!
        MiuixConfirmDialog(
            title = spec.title,
            message = spec.message,
            confirmText = spec.confirmText,
            dismissText = spec.dismissText,
            onConfirm = {
                showConfirm = false
                // 记住"已同意"，之后每次开启都不再弹框（只第一次提示）
                spec.onceKey?.let { bridge.setBoolean(context, it, true) }
                applyValue(true)
            },
            onDismiss = { showConfirm = false }
        )
    }
}

/** 下拉选择项（状态化：写 prefs + 更新 UI + 可选双写） */
@Composable
fun PrefSpinner(
    context: android.content.Context,
    key: String,
    default: Int,
    options: List<Pair<String, String>>,
    title: String,
    summary: String,
    extraWrite: ((Int) -> Unit)? = null,
    bridge: PrefsBridge = MasterPrefs,
    /** 选项对应的实际存储值（不传则存位置下标）。
     *  用于"从列表里删掉几个选项但老值不能错位"的场景（如歌词动画效果删了三点倒计时/歌名+三点）。 */
    values: List<Int>? = null,
    /** 写入前置守卫：返回 false 时本次选择不写 prefs、不改 UI（用于「总开关没开 → 写不进去」的场景）。 */
    guard: (() -> Boolean)? = null,
    /** 被守卫拦下时的回调（给用户一个可见提示）。 */
    onGuarded: (() -> Unit)? = null
) {
    val valueOf = { i: Int -> values?.getOrNull(i) ?: i }
    var index by remember {
        mutableStateOf(
            run {
                val cur = bridge.getInt(context, key, default)
                if (values == null) {
                    cur
                } else {
                    val pos = values.indexOf(cur)
                    if (pos >= 0) {
                        pos
                    } else {
                        // 老值已被删掉（如 2/3）：回落到第一项，并把存储值一并纠正
                        val fallback = values.indexOf(default).let { if (it >= 0) it else 0 }
                        bridge.setInt(context, key, valueOf(fallback))
                        fallback
                    }
                }
            }
        )
    }
    // 老词幕同款：Miuix 原生 OverlaySpinnerPreference（Popup 下拉模式——从设置项下方弹出细长条小列表，
    // 不占大面积居中弹窗，白底黑字、选中整行高亮）
    OverlaySpinnerPreference(
        items = options.mapIndexed { i, (label, desc) ->
            DropdownItem(
                text = label,
                summary = desc.ifBlank { null },
                selected = i == index
            )
        },
        selectedIndex = index,
        title = title,
        summary = summary,
        onSelectedIndexChange = { i ->
            if (guard != null && !guard.invoke()) {
                onGuarded?.invoke()
                return@OverlaySpinnerPreference
            }
            index = i
            bridge.setInt(context, key, valueOf(i))
            extraWrite?.invoke(valueOf(i))
        }
    )
}

// ================= 自绘弹窗组件（不依赖 Miuix WindowDialog/OverlayDialog） =================

/** 二次确认弹窗（白色卡片 + 说明文字 + 取消/确认两键）。
 *  用于"试验性功能默认关闭，开启前必须点同意"的场景。 */
@Composable
fun MiuixConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "同意并开启",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 480f),
        label = "confirmScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(200),
        label = "confirmAlpha"
    )
    val bg = androidx.compose.ui.graphics.Color.White
    val fg = androidx.compose.ui.graphics.Color(0xFF000000)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material.Surface(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .padding(vertical = 24.dp)
                .graphicsLayer { this.scaleX = scale; this.scaleY = scale; this.alpha = alpha },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = bg,
            elevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
                Text(
                    text = title,
                    color = fg,
                    style = MiuixTheme.textStyles.title2
                )
                Text(
                    text = message,
                    color = fg.copy(alpha = 0.72f),
                    style = TextStyle(fontSize = 14.sp),
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material.TextButton(onClick = onDismiss) {
                        Text(text = dismissText, color = fg.copy(alpha = 0.6f))
                    }
                    androidx.compose.material.TextButton(onClick = onConfirm) {
                        Text(text = confirmText, color = androidx.compose.ui.graphics.Color(0xFF2196F3))
                    }
                }
            }
        }
    }
}

/** 通用选择弹窗（窗口级 Dialog + Miuix 卡片外观，替代 OverlayDialog/WindowDialog） */
@Composable
fun MiuixOptionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedIndex: Int = -1,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 弹出动画：scale 由小放大 + 弹性回弹 + 渐显（对齐老词幕的弹出效果）
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 480f),
        label = "popScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(200),
        label = "popAlpha"
    )
    // 老词幕配色：白底、黑字、选中项蓝色
    val bg = androidx.compose.ui.graphics.Color.White
    val fg = androidx.compose.ui.graphics.Color(0xFF000000)
    val selectedColor = androidx.compose.ui.graphics.Color(0xFF2196F3)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material.Surface(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .heightIn(max = 460.dp)
                .padding(vertical = 24.dp)
                .graphicsLayer { this.scaleX = scale; this.scaleY = scale; this.alpha = alpha },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = bg,
            elevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                // 标题：黑色
                Text(
                    text = title,
                    color = fg,
                    style = MiuixTheme.textStyles.title2,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                )
                androidx.compose.material.Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = fg.copy(alpha = 0.14f)
                )
                // 选项区：限高 + 可滚动（选项多如动画效果 26 项也能往下滑选）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(top = 4.dp, bottom = 4.dp)
                ) {
                    options.forEachIndexed { i, (label, summary) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 选中项：整行浅蓝高亮背景（对齐 Miuix/老词幕选中态），未选中透明
                            val isSelected = i == selectedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(androidx.compose.ui.graphics.Color(0xFFE3F2FD))
                                        } else Modifier
                                    )
                                    .clickable { onSelected(i); onDismiss() }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "✓ $label" else label,
                                    color = if (isSelected) selectedColor else fg,
                                    style = MiuixTheme.textStyles.body1
                                )
                            }
                            if (summary.isNotEmpty()) {
                                Text(
                                    text = summary,
                                    style = MiuixTheme.textStyles.body2,
                                    color = fg.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                                )
                            }
                            // 每项分隔线，选项独立分开
                            androidx.compose.material.Divider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = fg.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
                androidx.compose.material.Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = fg.copy(alpha = 0.14f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "取消",
                        color = fg.copy(alpha = 0.75f),
                        style = MiuixTheme.textStyles.body1
                    )
                }
            }
        }
    }
}

/** 数值输入弹窗（替代 Miuix WindowDialog） */
@Composable
fun MiuixInputDialog(
    title: String,
    initial: String,
    rangeLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    // 老词幕同款：Miuix OverlayDialog 居中卡片 + 数字键盘输入 + 取消/确定（确定蓝色高亮、SpaceBetween 布局）
    OverlayDialog(
        show = true,
        title = title,
        summary = rangeLabel,
        backgroundColor = MiuixTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { new ->
                    text = new.filter { it.isDigit() || it == '-' || it == '.' }
                },
                label = rangeLabel,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    autoCorrectEnabled = false
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = "确定",
                    onClick = { onConfirm(text); onDismiss() },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 颜色选择弹窗（替代 Miuix WindowDialog 色板） */
@Composable
fun MiuixColorDialog(
    title: String,
    presetColors: List<Pair<Int, String>>,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 老词幕同款：Miuix 原生 OverlayDialog（白底黑字、Miuix 材质与弹出动画），内容为色块网格
    OverlayDialog(
        show = true,
        title = title,
        summary = "点击色块选择歌词文字颜色",
        backgroundColor = MiuixTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            presetColors.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { (c, name) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(c))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                                    .clickable { onSelected(c); onDismiss() }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss
                )
            }
        }
    }
}

/** 关于页（独立页面，带流光背景动画） */
@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"
    val packageName = context.packageName
    // 0=未打开 1=微信 2=支付宝
    var sponsorTarget by remember { mutableStateOf(0) }

    SkinTheme {
        val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
        SideEffect {
            val window = (context as Activity).window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !isDark
        }

        // 应用全局背景（受"流光背景"开关控制）
        AppBackground(
            isDarkTheme = isDark,
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent
            ) { _ ->
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部返回按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isDark) Color(0xFF202024).copy(alpha = 0.80f)
                                    else Color.White.copy(alpha = 0.68f),
                                    RoundedCornerShape(14.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.14f)
                                    else Color.White.copy(alpha = 0.80f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onBack() }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("← 返回", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                        }
                    }

                    // 内容
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 应用名 + 副标题
                        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Text("词岛", style = MiuixTheme.textStyles.title1, color = MiuixTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text("超级岛歌词 · Xposed 模块", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.outline)
                        }

                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material.Divider(color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(Modifier.height(12.dp))

                        // 版本信息列表
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            AboutItem("版本", versionName)
                            AboutItem("包名", packageName)
                            AboutItem("作者", "liuxiansheng（刘先生）")
                            AboutItem("适配", "小米澎湃 OS / HyperOS")
                            AboutItem("功能", "超级岛歌词 · 卡拉OK逐字 · 歌词源管理")
                        }

                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material.Divider(color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(Modifier.height(12.dp))

                        // 赞助
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            Text("赞助", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "词岛完全免费、无广告、无内购。如果它帮到了你，可以扫码请我喝杯水，纯属自愿。",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SponsorButton(
                                    label = "微信赞助",
                                    accent = Color(0xFF07C160),
                                    onClick = { sponsorTarget = 1 },
                                )
                                SponsorButton(
                                    label = "支付宝赞助",
                                    accent = Color(0xFF1677FF),
                                    onClick = { sponsorTarget = 2 },
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material.Divider(color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(Modifier.height(12.dp))

                        // 致谢（完整列表）
                        if (sponsorTarget != 0) {
                            SponsorQrDialog(
                                target = sponsorTarget,
                                onDismiss = { sponsorTarget = 0 },
                            )
                        }
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            Text("致谢", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(6.dp))
                            AboutCredit("ColorOS Live Lyrics Providers", "上游歌词源推送", "")
                            AboutCredit("词幕 / Lyricon Master", "逻辑移植参考", "")
                            AboutCredit("HyperLyric", "部分设置项移植参考", "https://github.com/zwkk/hyperlyric")
                            AboutCredit("Miuix KMP", "UI 框架", "https://github.com/kyantuto/miuix")
                            AboutCredit("LSPosed / Xposed Framework", "Hook 框架", "https://github.com/LSPosed/LSPosed")
                            AboutCredit("岛背景 / 毛玻璃效果", "HyperOS 系统实时模糊实现", "")
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

/** 赞助按钮：主题色描边小胶囊 */
@Composable
private fun SponsorButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = accent)
    }
}

/** 赞助收款码弹窗：显示二维码 + 保存到相册 */
@Composable
private fun SponsorQrDialog(target: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isWechat = target == 1
    val accent = if (isWechat) Color(0xFF07C160) else Color(0xFF1677FF)
    val title = if (isWechat) "微信赞助" else "支付宝赞助"
    val painter = painterResource(
        if (isWechat) com.liuxiansheng.lyricon.app.R.drawable.sponsor_wechat
        else com.liuxiansheng.lyricon.app.R.drawable.sponsor_alipay
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.background, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Image(
                painter = painter,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(8.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (isWechat) "微信扫一扫，或用另一台手机扫这张图"
                else "支付宝扫一扫，或用另一台手机扫这张图",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SponsorButton("保存到相册", accent) { saveQrToGallery(context, target) }
                SponsorButton("关闭", MiuixTheme.colorScheme.onSurface.copy(alpha = 0.80f), onDismiss)
            }
        }
    }
}

/** 把收款码写进相册 Pictures/词岛（Q 以上无需存储权限） */
private fun saveQrToGallery(context: Context, target: Int) {
    val isWechat = target == 1
    val resId = if (isWechat) com.liuxiansheng.lyricon.app.R.drawable.sponsor_wechat
    else com.liuxiansheng.lyricon.app.R.drawable.sponsor_alipay
    val fileName = if (isWechat) "cid_ao_wechat.png" else "cid_ao_alipay.png"
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/词岛")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册文件")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            context.resources.openRawResource(resId).use { input -> input.copyTo(out) }
        } ?: error("无法写入相册文件")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
        }
        Toast.makeText(context, "已保存到相册（Pictures/词岛）", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
    }
}

/** 致谢项：项目名 + 用途说明，可点击打开对应链接 */
@Composable
private fun AboutCredit(name: String, desc: String, url: String = "") {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable(enabled = url.isNotEmpty()) {
                if (url.isNotEmpty()) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                }
            }
    ) {
        Text(name, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        Text(desc, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

/** 关于信息行：左标签 + 右值 */
@Composable
private fun AboutItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.outline,
            modifier = Modifier.width(56.dp),
        )
        Text(
            value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

// 各组实现见 SettingsContent.kt

/** 更多设置页（背景设置 / 主题设置 / 备份） */
@Composable
internal fun AdvancedSettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit = {}) {
    val context = LocalContext.current

    // —— 备份：导出到用户选择的位置（SAF，无需存储权限） ——
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = SettingsBackup.exportJson(context)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入所选文件")
            }.onSuccess {
                android.widget.Toast.makeText(
                    context, "设置已导出，可拷到其它设备导入", android.widget.Toast.LENGTH_SHORT
                ).show()
            }.onFailure { e ->
                android.widget.Toast.makeText(
                    context, "导出失败：${e.message}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // —— 备份：从用户选择的文件恢复 ——
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("无法读取所选文件")
                SettingsBackup.importJson(context, text)
            }.onSuccess { count ->
                android.widget.Toast.makeText(
                    context,
                    "已恢复 $count 项设置，点右上角「重启状态栏」后全部生效",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }.onFailure { e ->
                android.widget.Toast.makeText(
                    context, "导入失败：${e.message}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    SkinTheme {
        val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
        SideEffect {
            val window = (context as Activity).window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !isDark
        }

        // 应用全局背景（受"流光背景"开关控制）
        AppBackground(
            isDarkTheme = isDark,
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent
            ) { _ ->
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部返回按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isDark) Color(0xFF202024).copy(alpha = 0.80f)
                                    else Color.White.copy(alpha = 0.68f),
                                    RoundedCornerShape(14.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.14f)
                                    else Color.White.copy(alpha = 0.80f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onBack() }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("← 返回", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                        }
                    }

                    // 内容
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 背景设置
                        SectionTitle("背景设置")
                        SectionCard {
                            PrefSwitch(
                                context = context,
                                key = "pref_advanced_流光背景",
                                default = true,
                                title = "流光背景",
                                summary = "开启后所有页面背景显示流光动画效果",
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // 主题设置（占位）
                        SectionTitle("主题设置")
                        SectionCard {
                            PrefSpinner(
                                context = context,
                                key = "pref_advanced_theme_mode",
                                default = 0,
                                options = listOf(
                                    "跟随主题" to "跟随系统主题",
                                    "浅色" to "强制浅色主题",
                                    "深色" to "强制深色主题",
                                ),
                                title = "主题模式",
                                summary = "选择应用主题模式",
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // 备份（3.0.1117 起真正可用：JSON 导出/导入，覆盖主设置 + 状态栏设置）
                        SectionTitle("备份")
                        SectionCard {
                            ArrowPreference(
                                title = "导出设置",
                                summary = "将当前所有设置导出为 JSON 备份文件",
                                onClick = {
                                    runCatching { exportLauncher.launch(SettingsBackup.suggestedFileName()) }
                                        .onFailure { e ->
                                            android.widget.Toast.makeText(
                                                context, "无法打开文件选择器：${e.message}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                },
                            )
                            ArrowPreference(
                                title = "导入设置",
                                summary = "从备份文件恢复设置（会覆盖当前全部设置）",
                                onClick = {
                                    runCatching { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
                                        .onFailure { e ->
                                            android.widget.Toast.makeText(
                                                context, "无法打开文件选择器：${e.message}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                },
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // 调试（日志开关：正式版默认不打印，排障时临时打开）
                        SectionTitle("调试")
                        SectionCard {
                            PrefSwitch(
                                context = context,
                                key = "pref_debug_logging",
                                default = false,
                                title = "打印日志",
                                summary = "默认关闭。排障时打开，日志打到 logcat（标签 Lyricon），" +
                                    "电脑上执行 adb logcat -s Lyricon 抓取；收集完记得关掉",
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // 关于（入口）
                        SectionTitle("关于")
                        SectionCard {
                            ArrowPreference(
                                title = "关于词岛",
                                summary = "版本信息、致谢、开源许可",
                                onClick = onOpenAbout,
                            )
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
