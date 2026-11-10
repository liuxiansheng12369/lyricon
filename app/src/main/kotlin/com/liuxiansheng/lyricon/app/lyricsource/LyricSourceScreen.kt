/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * LyricSourceScreen：歌词源管理页（MainActivity 内部二级页）。
 * - 扫描展示已安装歌词 provider 插件（图标 / 名称 / 版本 / 作者 / 描述 / 标签）
 * - 顶部切换列表视图模式（精简 / 完整）
 * - 底部"歌词服务"设置区：暂停防冻结开关（暂停时保持播放会话活跃防冻结，
 *   避免被系统冻结导致重启状态栏后歌词数据不通）+ 保持时长。
 */

package com.liuxiansheng.lyricon.app.lyricsource

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import com.liuxiansheng.lyricon.app.activity.NumberInputPreference
import com.liuxiansheng.lyricon.app.activity.PrefSwitch
import com.liuxiansheng.lyricon.app.activity.SectionCard
import com.liuxiansheng.lyricon.app.activity.SectionTitle
import com.liuxiansheng.lyricon.app.util.MasterPrefs
import com.liuxiansheng.lyricon.app.util.ProviderPrefsBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** ColorOS 实时歌词桥接系列（上游项目 ColorOS Live Lyrics Providers，GitHub: Andrea-lyz/ColorOS-Live-Lyrics）。 */
private const val COLOROS_PROJECT_PREFIX = "io.github.andrealtb.coloroslyrics.provider."
private const val COLOROS_PROJECT_NAME = "ColorOS Live Lyrics"

/**
 * 歌词源管理页。
 */
@Composable
fun LyricSourceScreen(onBack: () -> Unit) {    val context = LocalContext.current
    var listStyle by remember {
        mutableStateOf(
            MasterPrefs.getInt(context, MasterPrefs.KEY_LYRIC_SOURCE_LIST_STYLE, LyricSourceViewMode.SHORT)
        )
    }
    var categories by remember { mutableStateOf<List<LyricSourceCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // 扫描 + 认证 + 分组（后台线程）
    LaunchedEffect(Unit) {
        categories = withContext(Dispatchers.IO) { LyricSourceScanner.scan(context) }
        loading = false
    }

    BackHandler { onBack() }

    com.liuxiansheng.lyricon.app.activity.SkinTheme {
        val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
        // 状态栏图标按页面背景反色
        SideEffect {
            val window = (context as android.app.Activity).window
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDark
        }
        // 应用全局背景（受"流光背景"开关控制）
        com.liuxiansheng.lyricon.app.compose.effect.AppBackground(
            isDarkTheme = isDark,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏：标题 + 列表视图切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "歌词源",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    ViewModeSwitch(listStyle) {
                        listStyle = it
                        MasterPrefs.setInt(context, MasterPrefs.KEY_LYRIC_SOURCE_LIST_STYLE, it)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    SectionTitle("已安装歌词源")
                    if (!loading && categories.isNotEmpty()) {
                        // 提示行显示"支持的歌词源"：ColorOS 桥接系列用上游项目名（ColorOS Live Lyrics），其余用 APK 真实 label
                        val supported = categories
                            .flatMap { it.items }
                            .map { module ->
                                if (module.packageName.startsWith(COLOROS_PROJECT_PREFIX)) {
                                    COLOROS_PROJECT_NAME
                                } else {
                                    module.label
                                }
                            }
                            .distinct()
                            .joinToString(" · ")
                        Text(
                            text = "支持：$supported",
                            fontSize = 12.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF334155).copy(alpha = 0.75f),
                            modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 4.dp),
                        )
                    }
                    when {
                        loading -> StatusHint("正在扫描歌词源…")
                        categories.isEmpty() -> StatusHint("未检测到歌词源 provider（需安装并启用歌词模块）")
                        else -> categories.forEach { cat -> CategoryBlock(cat, listStyle == LyricSourceViewMode.FULL, context) }
                    }
                }
            }
        }
    }
}

/** 顶部列表视图切换（精简 / 完整）。 */
@Composable
private fun ViewModeSwitch(current: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ViewModeButton("精简", current == LyricSourceViewMode.SHORT) {
            onChange(LyricSourceViewMode.SHORT)
        }
        ViewModeButton("完整", current == LyricSourceViewMode.FULL) {
            onChange(LyricSourceViewMode.FULL)
        }
    }
}

/** 视图切换小按钮（选中高亮）。 */
@Composable
private fun ViewModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val bg = if (selected) {
        if (isDark) Color(0xFF3D5AFE) else Color(0xFF4F7CFF)
    } else {
        if (isDark) Color(0xFF202024).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.30f)
    }
    val border = if (selected) Color.Transparent else {
        if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.70f)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 一个分类分组。 */
@Composable
private fun CategoryBlock(category: LyricSourceCategory, showDetails: Boolean, context: Context) {
    if (category.name.isNotBlank()) {
        SectionTitle(category.name)
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        category.items.forEachIndexed { index, module ->
            ModuleCard(
                module = module,
                showDetails = showDetails,
                context = context,
                modifier = Modifier.padding(bottom = if (index == category.items.lastIndex) 12.dp else 12.dp),
            )
        }
    }
}

/** 单个歌词源卡片（磨砂半透明底 + 图标 + 名称/版本/作者 + 描述 + 标签；认证的底部内嵌暂停防冻结设置）。 */
@Composable
private fun ModuleCard(module: LyricSourceModule, showDetails: Boolean, context: Context, modifier: Modifier = Modifier) {
    var showCertSettings by remember { mutableStateOf(false) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    // LSPosed 未激活的模块：卡片整体调暗 + 红色调提示（亮色主题深红 / 暗色主题浅红，保证可读）
    val notActivated = module.activation == false
    val warnRed = if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
    val glassBg = if (notActivated) {
        if (isDark) Color(0xFF202024).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f)
    } else if (isDark) {
        Color(0xFF202024).copy(alpha = 0.45f)
    } else {
        Color.White.copy(alpha = 0.30f)
    }
    val glassBorder = if (notActivated) {
        warnRed.copy(alpha = 0.50f)
    } else if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.70f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(glassBg, RoundedCornerShape(16.dp))
            .border(1.dp, glassBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncAppIcon(packageName = module.packageName, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = module.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    if (module.isCertified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "✓ 认证",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50),
                        )
                    }
                }
                val secondary = buildString {
                    append("v${module.versionName ?: "未知"}")
                    module.author?.let { append(" · $it") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = secondary,
                        fontSize = 13.sp,
                        color = Color.Gray,
                    )
                    when {
                        notActivated -> {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "未激活",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = warnRed,
                            )
                        }
                        module.activation == true -> {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "已激活",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF4CAF50),
                            )
                        }
                    }
                }
                if (showDetails && !module.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = module.description,
                        fontSize = 13.sp,
                        color = Color.Gray,
                    )
                }
                if (showDetails && module.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        module.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                }

                // B1 暂停防冻结：认证歌词源的设置弹窗（⚙ 图标触发）
                if (showCertSettings) {
                    Dialog(onDismissRequest = { showCertSettings = false }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MiuixTheme.colorScheme.background, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                        ) {
                            Column {
                                Text(
                                    text = "${module.label} · 暂停防冻结",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                PrefSwitch(
                                    context = context,
                                    key = MasterPrefs.KEY_LYRIC_SOURCE_KEEP_SESSION_ON_PAUSE,
                                    default = false,
                                    title = "暂停防冻结",
                                    summary = "音乐暂停时保持播放会话活跃，避免被系统冻结导致重启状态栏后歌词数据不通",
                                    bridge = ProviderPrefsBridge,
                                )
                                NumberInputPreference(
                                    context = context,
                                    key = MasterPrefs.KEY_LYRIC_SOURCE_KEEP_SESSION_MINUTES,
                                    default = 15,
                                    range = 1..60,
                                    unit = "分钟",
                                    title = "保持时长",
                                    bridge = ProviderPrefsBridge,
                                    inline = true,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Text(
                                        text = "完成",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF4F7CFF),
                                        modifier = Modifier
                                            .clickable { showCertSettings = false }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 设置按钮：认证歌词源，卡片中间高度靠右
            if (module.isCertified) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "设置",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            if (isDark) Color(0xFF3D5AFE) else Color(0xFF4F7CFF),
                            RoundedCornerShape(9.dp),
                        )
                        .clickable { showCertSettings = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 异步加载应用图标（Drawable → Bitmap，后台线程 + 状态缓存）。 */
@Composable
private fun AsyncAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(packageName) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
                }.getOrNull()
            }
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            painter = BitmapPainter(bmp.asImageBitmap()),
            contentDescription = packageName,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(
                color = MiuixTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            )
        )
    }
}

/** 简单提示文本（加载中 / 空态）。 */
@Composable
private fun StatusHint(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
    )
}
