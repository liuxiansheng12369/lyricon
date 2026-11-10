/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * HomeThemes：首页四套主题的独立布局。
 *   黑胶唱片（原 vinyl）  → 唱片机：大黑胶 + 唱臂 + 播放控制
 *   晨雾留白（原 mist）   → 晨雾概览：白纸留白 + 今日听歌数据
 *   赛博霓虹（原 cyber）  → 深空仪表：16 色频谱 + 今日时长环 + 近 7 天 + 最近播放
 *   歌词杂志（新 lyric）  → 整页歌词 + 玻璃面板
 * 这四套是"布局级"改动；默认（pref_home_skin=0）仍是原来的经典区块，不受影响。
 */

package com.liuxiansheng.lyricon.app.activity

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuxiansheng.lyricon.app.live.ListenRecord
import com.liuxiansheng.lyricon.app.live.ListenRecordsStore
import com.liuxiansheng.lyricon.app.live.LiveStateProvider
import com.liuxiansheng.lyricon.app.live.NowPlaying
import com.liuxiansheng.lyricon.app.live.NowPlayingReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ===== 数据 =====

internal data class HomeLive(
    val now: NowPlaying,
    val records: List<ListenRecord>,
)

/** 首页实时数据：播放状态 600ms 一刷，听歌记录 2.4s 一刷（够用且不费电）。 */
@Composable
internal fun rememberHomeLive(): HomeLive {
    val context = LocalContext.current
    var now by remember { mutableStateOf(NowPlayingReader.read(context)) }
    var records by remember { mutableStateOf<List<ListenRecord>>(emptyList()) }
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            now = NowPlayingReader.read(context)
            if (tick % 4 == 0) {
                records = withContext(Dispatchers.IO) { ListenRecordsStore.load(context) }
            }
            tick++
            delay(600)
        }
    }
    return HomeLive(now, records)
}

private fun dayStartMs(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun clockLabel(ms: Long, pattern: String = "HH:mm"): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))

private fun minuteLabel(ms: Long): String {
    if (ms <= 0) return "0"
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h${(m % 60).toString().padStart(2, '0')}" else "$m"
}

private fun durationShort(ms: Long): String {
    if (ms <= 0) return "—"
    val m = ms / 60_000
    return if (m >= 60) "${m / 60} 小时 ${m % 60} 分" else "$m 分钟"
}

// ===== 动作 =====

private fun restartSystemUi(scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        withContext(Dispatchers.IO) {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "killall com.android.systemui")).waitFor()
            }
        }
    }
}

/** 播放控制走 su + input keyevent（和"重启状态栏"同一条通道，不需要额外权限）。 */
private fun sendMediaKey(scope: kotlinx.coroutines.CoroutineScope, keyCode: Int) {
    scope.launch {
        withContext(Dispatchers.IO) {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent $keyCode")).waitFor()
            }
        }
    }
}

private const val KEY_PREV = 88
private const val KEY_PLAY_PAUSE = 85
private const val KEY_NEXT = 87

// ===== 入口 =====

@Composable
internal fun ThemedHome(
    skin: HomeSkin,
    skinId: Int,
    moduleActive: Boolean,
    onOpenSuperIsland: () -> Unit,
    onOpenStatusBarLyric: () -> Unit,
    onOpenLyricSource: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val context = LocalContext.current
    val live = rememberHomeLive()
    when (skin.layout) {
        HomeSkins.LAYOUT_DECK -> DeckHome(skin, skinId, live, moduleActive, context, onOpenSuperIsland, onOpenStatusBarLyric, onOpenLyricSource, onOpenAdvanced, onOpenRecords)
        HomeSkins.LAYOUT_MIST -> MistHome(skin, skinId, live, moduleActive, context, onOpenSuperIsland, onOpenStatusBarLyric, onOpenLyricSource, onOpenAdvanced, onOpenRecords)
        HomeSkins.LAYOUT_DASH -> DashHome(skin, skinId, live, moduleActive, context, onOpenSuperIsland, onOpenStatusBarLyric, onOpenLyricSource, onOpenAdvanced, onOpenRecords)
        else -> LyricHome(skin, skinId, live, moduleActive, context, onOpenSuperIsland, onOpenStatusBarLyric, onOpenLyricSource, onOpenAdvanced, onOpenRecords)
    }
}

// ===== 公共小件 =====

@Composable
private fun RebootChip(skin: HomeSkin, onRestart: () -> Unit) {
    Box(
        modifier = Modifier
            .background(skin.cardBg, RoundedCornerShape(14.dp))
            .border(1.dp, skin.cardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onRestart)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("↻", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = skin.textPrimary)
    }
}

/** 胶囊岛（真实播放状态）：歌名/歌词 + 频谱律动 */
@Composable
private fun IslandCapsule(
    skin: HomeSkin,
    now: NowPlaying,
    barCount: Int = 6,
    onClick: (() -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "capsule")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val borderMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(skin.islandBg, RoundedCornerShape(28.dp))
            .border(1.dp, skin.accent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
            .then(borderMod)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    Brush.linearGradient(
                        listOf(skin.accent.copy(alpha = 0.82f), skin.accent.copy(alpha = 0.28f))
                    ),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("岛", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = skin.textPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                NowPlayingReader.mainLine(now),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = skin.islandLyric,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                NowPlayingReader.subLine(now),
                fontSize = 11.sp,
                color = skin.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // 右侧小律动条：固定尺寸自绘，避免每帧改布局导致整卡高度跳动
        Canvas(
            modifier = Modifier
                .width(((barCount * 3) + ((barCount - 1) * 2)).dp)
                .height(24.dp)
        ) {
            val bw = 3.dp.toPx()
            val gp = 2.dp.toPx()
            repeat(barCount) { i ->
                val factor = when (i) {
                    0 -> 0.55f
                    2 -> 0.95f
                    4 -> 0.7f
                    else -> 0.85f
                }
                val h = if (now.playing) (7f + 16f * factor * pulse).dp.toPx() else 7.dp.toPx()
                val y = size.height - h
                drawRoundRect(
                    color = skin.accent.copy(alpha = if (now.playing) 1f else 0.4f),
                    topLeft = Offset(i * (bw + gp), y),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }
    }
}

/** 细进度条：有进度数据前用一条流动光带表示"在播"。 */
@Composable
private fun ShimmerLine(accent: Color, track: Color, playing: Boolean, height: Int = 3) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier = Modifier.fillMaxWidth().height(height.dp)) {
        drawRect(color = track, size = size)
        if (playing) {
            val w = size.width * 0.34f
            val x = -w + (size.width + w) * t
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, accent.copy(alpha = 0.85f), Color.Transparent)
                ),
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
            )
        } else {
            drawRect(color = accent.copy(alpha = 0.55f), size = size)
        }
    }
}

/** 16 色频谱条（深空仪表的视觉主角）。
 *  注意：必须在固定尺寸的 Canvas 里画，不能靠改 Box 的 height 做律动——
 *  那样每帧都要重新 measure/layout，整页内容会跟着上下抖。 */
@Composable
private fun Spectrum16(accent: Color, playing: Boolean, height: Int = 74) {
    val transition = rememberInfiniteTransition(label = "spec16")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "t",
    )
    val count = 16
    val barW = 4.dp
    val gap = 2.dp
    Canvas(
        modifier = Modifier
            .width(((count * 4) + ((count - 1) * 2)).dp)
            .height(height.dp)
    ) {
        val bw = barW.toPx()
        val gp = gap.toPx()
        repeat(count) { i ->
            val hue = (i * 360f / count)
            val base = Color.hsv(hue, 0.62f, 1f)
            val phase = i / count.toFloat()
            val k = if (playing) {
                val v = kotlin.math.abs(kotlin.math.sin((t + phase) * Math.PI * 2.0)).toFloat()
                0.22f + 0.78f * v
            } else {
                0.22f + 0.10f * (i % 3) / 2f
            }
            val h = (height.dp.toPx() * k).coerceIn(3.dp.toPx(), size.height)
            val x = i * (bw + gp)
            val y = size.height - h
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(base, base.copy(alpha = 0.55f)),
                    startY = y,
                    endY = size.height,
                ),
                topLeft = Offset(x, y),
                size = Size(bw, h),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color, trailing: String? = null, trailingColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, fontSize = 11.sp, color = trailingColor ?: color.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun Footer(skin: HomeSkin, moduleActive: Boolean) {
    Text(
        text = "模块" + (if (moduleActive) "已激活" else "未激活") +
            " · 柔光玻璃 · " + com.liuxiansheng.lyricon.app.BuildConfig.VERSION_NAME,
        fontSize = 11.sp,
        color = skin.textSecondary.copy(alpha = 0.85f),
        modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
    )
}

/** 最近播放 3 条（深空仪表 / 歌词杂志共用）。 */
@Composable
private fun RecentList(skin: HomeSkin, records: List<ListenRecord>, limit: Int = 3, onOpenRecords: () -> Unit) {
    if (records.isEmpty()) {
        Text(
            "还没有听歌记录（听够一半才会记一条）",
            fontSize = 12.sp,
            color = skin.textSecondary,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        return
    }
    records.take(limit).forEachIndexed { index, record ->
        if (index > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(skin.cardBorder.copy(alpha = 0.35f))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenRecords)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = skin.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    record.artist.ifBlank { ListenRecordsStore.appLabel(record.packageName) },
                    fontSize = 11.sp,
                    color = skin.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(clockLabel(record.time), fontSize = 11.sp, color = skin.textSecondary)
        }
    }
}

// ===== ① 唱片机 =====

@Composable
private fun DeckHome(
    skin: HomeSkin,
    skinId: Int,
    live: HomeLive,
    moduleActive: Boolean,
    context: Context,
    onOpenSuperIsland: () -> Unit,
    onOpenStatusBarLyric: () -> Unit,
    onOpenLyricSource: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val now = live.now
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // 头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("词岛", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = skin.textPrimary)
                Text(
                    "VINYL PLAYER · 唱片机",
                    fontSize = 10.sp,
                    color = skin.accent,
                )
            }
            RebootChip(skin) { restartSystemUi(scope) }
        }

        Spacer(Modifier.height(18.dp))

        // 黑胶唱机
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(288.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height * 0.54f
                val r = size.height * 0.42f
                // 唱片本体
                drawCircle(Color(0xFF15100B), radius = r, center = Offset(cx, cy))
                drawCircle(
                    skin.accent.copy(alpha = 0.29f),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.6f),
                )
                // 纹路
                repeat(9) { i ->
                    val rr = r * (0.44f + i * 0.062f)
                    drawCircle(
                        Color.White.copy(alpha = 0.055f),
                        radius = rr,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.4f),
                    )
                }
                // 铜金弧线
                drawArc(
                    color = skin.accent.copy(alpha = 0.9f),
                    startAngle = 198f,
                    sweepAngle = 62f,
                    useCenter = false,
                    topLeft = Offset(cx - r - 1.5f, cy - r - 1.5f),
                    size = Size(r * 2 + 3f, r * 2 + 3f),
                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                )
                // 唱臂
                val ax = cx + r * 0.74f
                val ay = cy - r * 0.96f
                drawLine(
                    color = skin.accent.copy(alpha = 0.75f),
                    start = Offset(ax, ay),
                    end = Offset(cx + r * 0.30f, cy - r * 0.42f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
                drawCircle(skin.accent.copy(alpha = 0.85f), radius = 9f, center = Offset(ax, ay))
                drawCircle(Color(0xFFF1DDC0), radius = 4.5f, center = Offset(cx + r * 0.30f, cy - r * 0.42f))
            }
            // 唱片中心封面
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(skin.accent.copy(alpha = 0.30f), Color(0xFF0E1A1F))
                        ),
                        RoundedCornerShape(10.dp),
                    )
                    .border(1.dp, skin.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = now.title.take(4).ifBlank { "词岛" },
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = skin.textPrimary,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (now.hasTrack) now.title else "未在播放",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = skin.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (now.hasTrack) {
                listOf(now.artist, ListenRecordsStore.appLabel(now.packageName)).filter { it.isNotBlank() }.joinToString(" · ")
            } else {
                "打开音乐 App 播放即可实时显示"
            },
            fontSize = 12.sp,
            color = skin.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(Modifier.height(16.dp))
        ShimmerLine(skin.accent, skin.cardBorder.copy(alpha = 0.5f), now.playing)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            Text(if (now.playing) "正在播放" else "已暂停", fontSize = 11.sp, color = skin.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(clockLabel(System.currentTimeMillis()), fontSize = 11.sp, color = skin.textSecondary)
        }

        Spacer(Modifier.height(14.dp))
        // 播放控制
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(skin, 44, onClick = { sendMediaKey(scope, KEY_PREV) }) {
                Text("⏮", fontSize = 15.sp, color = skin.textPrimary)
            }
            Spacer(Modifier.width(24.dp))
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .background(skin.accent, CircleShape)
                    .clickable { sendMediaKey(scope, KEY_PLAY_PAUSE) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(22.dp)) {
                    val ink = Color(0xFF1A130C)
                    if (now.playing) {
                        val bw = size.width * 0.30f
                        drawRect(ink, topLeft = Offset(0f, 0f), size = Size(bw, size.height))
                        drawRect(ink, topLeft = Offset(size.width - bw, 0f), size = Size(bw, size.height))
                    } else {
                        val tri = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(tri, ink)
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            RoundIconButton(skin, 44, onClick = { sendMediaKey(scope, KEY_NEXT) }) {
                Text("⏭", fontSize = 15.sp, color = skin.textPrimary)
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("岛内实时", skin.textSecondary, "听歌记录 ›", skin.accent)
        Spacer(Modifier.height(8.dp))
        IslandCapsule(skin, now, onClick = onOpenRecords)

        Spacer(Modifier.height(18.dp))
        ThemedQuickGrid(
            skin = skin,
            onOpenSuperIsland = onOpenSuperIsland,
            onOpenStatusBarLyric = onOpenStatusBarLyric,
            onOpenLyricSource = onOpenLyricSource,
            onOpenAdvanced = onOpenAdvanced,
            onOpenRecords = onOpenRecords,
        )

        Spacer(Modifier.height(14.dp))
        MasterSwitchCard(context)

        Spacer(Modifier.height(16.dp))
        SectionLabel("首页主题", skin.textSecondary, skin.name, skin.accent)
        Spacer(Modifier.height(8.dp))
        HomeSkinPicker(currentId = skinId, onPick = { id -> HomeSkins.write(context, id) })

        Footer(skin, moduleActive)
    }
}

@Composable
private fun RoundIconButton(skin: HomeSkin, size: Int, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(skin.cardBg, CircleShape)
            .border(1.dp, skin.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ===== ② 晨雾概览 =====

@Composable
private fun MistHome(
    skin: HomeSkin,
    skinId: Int,
    live: HomeLive,
    moduleActive: Boolean,
    context: Context,
    onOpenSuperIsland: () -> Unit,
    onOpenStatusBarLyric: () -> Unit,
    onOpenLyricSource: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val now = live.now
    val todayStart = remember { dayStartMs() }
    val today = live.records.filter { it.time >= todayStart }
    val todayMs = today.sumOf { it.listenedMs }
    val topArtist = today.filter { it.artist.isNotBlank() }
        .groupingBy { it.artist }.eachCount().entries.maxByOrNull { it.value }?.key ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("词岛", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = skin.textPrimary)
                Text("超级岛歌词 · 晨雾概览", fontSize = 13.sp, color = skin.textSecondary)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .border(1.dp, skin.assertAccent(0.45f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text("晨雾 · 概览", fontSize = 11.sp, color = skin.accent)
                }
            }
            RebootChip(skin) { restartSystemUi(scope) }
        }

        Spacer(Modifier.height(20.dp))

        // 主卡：正在播放
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(skin.cardBg, RoundedCornerShape(24.dp))
                .border(1.dp, skin.cardBorder, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (now.hasTrack) "正在播放 · " + ListenRecordsStore.appLabel(now.packageName) else "未在播放",
                        fontSize = 12.sp,
                        color = skin.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (now.playing) Color(0xFF4CAF50) else skin.textSecondary,
                                CircleShape,
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (now.playing) "在线" else "暂停",
                        fontSize = 11.sp,
                        color = if (now.playing) Color(0xFF4CAF50) else skin.textSecondary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(skin.accent.copy(alpha = 0.75f), skin.accent.copy(alpha = 0.22f))
                                ),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "♪",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (now.hasTrack) now.title else "未在播放",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = skin.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            NowPlayingReader.mainLine(now).let { if (it == now.title) now.artist else it },
                            fontSize = 12.sp,
                            color = skin.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Spectrum16(skin.accent, now.playing, height = 34)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (now.hasTrack) now.artist.ifBlank { "—" } else "打开音乐 App 播放",
                        fontSize = 11.sp,
                        color = skin.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(clockLabel(System.currentTimeMillis()), fontSize = 11.sp, color = skin.textSecondary)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("今日", skin.textPrimary, "听歌概览", skin.textSecondary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TodayCell("${today.size}", "首 曲目", skin, Modifier.weight(1f))
            TodayCell(
                minuteLabel(todayMs),
                if (todayMs >= 60L * 60 * 1000) "小时 时长" else "分钟 时长",
                skin, Modifier.weight(1f),
            )
            TodayCell(topArtist.take(5), "最爱歌手", skin, Modifier.weight(1f))
        }

        Spacer(Modifier.height(22.dp))
        ThemedQuickGrid(
            skin = skin,
            onOpenSuperIsland = onOpenSuperIsland,
            onOpenStatusBarLyric = onOpenStatusBarLyric,
            onOpenLyricSource = onOpenLyricSource,
            onOpenAdvanced = onOpenAdvanced,
            onOpenRecords = onOpenRecords,
        )

        Spacer(Modifier.height(14.dp))
        MasterSwitchCard(context)

        Spacer(Modifier.height(16.dp))
        SectionLabel("首页主题", skin.textPrimary, skin.name, skin.accent)
        Spacer(Modifier.height(8.dp))
        HomeSkinPicker(currentId = skinId, onPick = { id -> HomeSkins.write(context, id) })

        Footer(skin, moduleActive)
    }
}

@Composable
private fun TodayCell(value: String, label: String, skin: HomeSkin, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(skin.cardBg, RoundedCornerShape(18.dp))
            .border(1.dp, skin.cardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = if (value.length > 3) 17.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = skin.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(label, fontSize = 11.sp, color = skin.textSecondary)
        }
    }
}

// ===== ③ 深空仪表 =====

@Composable
private fun DashHome(
    skin: HomeSkin,
    skinId: Int,
    live: HomeLive,
    moduleActive: Boolean,
    context: Context,
    onOpenSuperIsland: () -> Unit,
    onOpenStatusBarLyric: () -> Unit,
    onOpenLyricSource: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val now = live.now
    val todayStart = remember { dayStartMs() }
    val today = live.records.filter { it.time >= todayStart }
    val todayMs = today.sumOf { it.listenedMs }
    val week = remember(live.records) { weekBuckets(live.records) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("词岛", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = skin.textPrimary)
                Text("LIVE DASHBOARD · 深空仪表", fontSize = 10.sp, color = skin.accent)
            }
            Box(
                modifier = Modifier
                    .border(1.dp, skin.cardBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (moduleActive) Color(0xFF46E6C8) else Color(0xFFE25B5B),
                                CircleShape,
                            )
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(if (moduleActive) "已激活" else "未激活", fontSize = 12.sp, color = skin.textPrimary)
                }
            }
            Spacer(Modifier.width(8.dp))
            RebootChip(skin) { restartSystemUi(scope) }
        }

        Spacer(Modifier.height(16.dp))

        // 实时歌词 + 16 色频谱
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(skin.cardBg, RoundedCornerShape(24.dp))
                .border(1.dp, skin.cardBorder, RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (now.hasTrack) "实时歌词 · " + ListenRecordsStore.appLabel(now.packageName) else "实时歌词 · 未在播放",
                        fontSize = 12.sp,
                        color = skin.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text("16 色频谱", fontSize = 11.sp, color = skin.accent)
                }
                Spacer(Modifier.height(12.dp))
                IslandCapsule(skin, now, barCount = 0, onClick = onOpenRecords)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (now.playing) "播放中" else "已暂停", fontSize = 12.sp, color = skin.accent)
                        Text(
                            "今日已听 " + durationShort(todayMs) + " · 记录 " + today.size + " 首",
                            fontSize = 11.sp,
                            color = skin.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spectrum16(skin.accent, now.playing, height = 64)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 今日时长环
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(skin.cardBg, RoundedCornerShape(20.dp))
                    .border(1.dp, skin.cardBorder, RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text("今日时长", fontSize = 12.sp, color = skin.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            TodayRing(skin, todayMs)
                            Text(
                                minuteLabel(todayMs) + "'",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = skin.textPrimary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("${today.size} 首曲目", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = skin.textPrimary)
                            Text(
                                if (today.isNotEmpty() && live.records.isNotEmpty()) "共 ${live.records.size} 首记录" else "今天还没记",
                                fontSize = 10.sp,
                                color = skin.textSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            // 近 7 天
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(skin.cardBg, RoundedCornerShape(20.dp))
                    .border(1.dp, skin.cardBorder, RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row {
                        Text("近 7 天", fontSize = 12.sp, color = skin.textSecondary)
                        Spacer(Modifier.weight(1f))
                        Text(durationShort(week.sumOf { it.second }), fontSize = 10.sp, color = skin.accent)
                    }
                    Spacer(Modifier.height(12.dp))
                    WeekBars(skin, week, height = 54)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("最近播放", skin.textPrimary, "${live.records.size} 首", skin.textSecondary)
        Spacer(Modifier.height(4.dp))
        RecentList(skin, live.records, 3, onOpenRecords)

        Spacer(Modifier.height(14.dp))
        ThemedQuickRows(
            skin = skin,
            onOpenSuperIsland = onOpenSuperIsland,
            onOpenStatusBarLyric = onOpenStatusBarLyric,
            onOpenLyricSource = onOpenLyricSource,
            onOpenAdvanced = onOpenAdvanced,
            onOpenRecords = onOpenRecords,
        )

        Spacer(Modifier.height(14.dp))
        MasterSwitchCard(context)

        Spacer(Modifier.height(16.dp))
        SectionLabel("首页主题", skin.textPrimary, skin.name, skin.accent)
        Spacer(Modifier.height(8.dp))
        HomeSkinPicker(currentId = skinId, onPick = { id -> HomeSkins.write(context, id) })

        Footer(skin, moduleActive)
    }
}

private fun weekBuckets(records: List<ListenRecord>): List<Pair<String, Long>> {
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val today = cal.timeInMillis
    val out = ArrayList<Pair<String, Long>>(7)
    for (i in 6 downTo 0) {
        val dayMs = today - i * 24L * 60 * 60 * 1000
        val key = dayFmt.format(Date(dayMs))
        val sum = records.filter { dayFmt.format(Date(it.time)) == key }.sumOf { it.listenedMs }
        val c = Calendar.getInstance()
        c.timeInMillis = dayMs
        val idx = when (c.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 6
        }
        out.add(labels[idx] to sum)
    }
    return out
}

@Composable
private fun TodayRing(skin: HomeSkin, todayMs: Long) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = size.minDimension * 0.12f
        val target = 2L * 60 * 60 * 1000  // 2 小时算一整圈
        val fraction = (todayMs.toFloat() / target).coerceIn(0f, 1f)
        drawCircle(skin.cardBorder.copy(alpha = 0.55f), radius = (size.minDimension - stroke) / 2f, style = Stroke(width = stroke))
        drawArc(
            brush = Brush.sweepGradient(listOf(skin.accent, skin.accent.copy(alpha = 0.45f), skin.accent)),
            startAngle = -90f,
            sweepAngle = 360f * fraction,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun WeekBars(skin: HomeSkin, data: List<Pair<String, Long>>, height: Int) {
    val max = (data.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier.fillMaxWidth().height((height + 16).dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEachIndexed { i, (label, ms) ->
            val k = (ms.toFloat() / max).coerceIn(0.12f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height((height * k).dp)
                        .background(
                            if (i == data.size - 1) skin.accent else skin.accent.copy(alpha = 0.38f),
                            RoundedCornerShape(5.dp),
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 9.sp, color = skin.textSecondary)
            }
        }
    }
}

// ===== ④ 歌词杂志 =====

@Composable
private fun LyricHome(
    skin: HomeSkin,
    skinId: Int,
    live: HomeLive,
    moduleActive: Boolean,
    context: Context,
    onOpenSuperIsland: () -> Unit,
    onOpenStatusBarLyric: () -> Unit,
    onOpenLyricSource: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val now = live.now
    val todayStart = remember { dayStartMs() }
    val today = live.records.filter { it.time >= todayStart }
    val todayMs = today.sumOf { it.listenedMs }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("词岛", fontSize = 14.sp, color = skin.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                "更多 ›",
                fontSize = 12.sp,
                color = skin.textSecondary,
                modifier = Modifier
                    .clickable(onClick = onOpenAdvanced)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            RebootChip(skin) { restartSystemUi(scope) }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            if (now.hasTrack) "正在播放 · " + ListenRecordsStore.appLabel(now.packageName) else "未在播放",
            fontSize = 11.sp,
            color = skin.accent,
        )
        Text(
            if (now.hasTrack) listOf(now.title, now.artist).filter { it.isNotBlank() }.joinToString(" / ") else "打开音乐 App 播放",
            fontSize = 12.sp,
            color = skin.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(22.dp))
        // 当前歌词（大字）
        Text(
            text = if (now.hasTrack) NowPlayingReader.mainLine(now) else "还没有在播放的歌",
            fontSize = 30.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
            color = if (now.hasTrack) skin.accent else skin.textSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (now.next.isNotBlank()) now.next else if (now.hasTrack) now.title else "",
            fontSize = 24.sp,
            lineHeight = 34.sp,
            color = skin.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(20.dp))
        ShimmerLine(skin.accent, skin.cardBorder.copy(alpha = 0.55f), now.playing, height = 2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            Text(if (now.playing) "正在播放" else "已暂停", fontSize = 11.sp, color = skin.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(clockLabel(System.currentTimeMillis()), fontSize = 11.sp, color = skin.textSecondary)
        }

        Spacer(Modifier.height(14.dp))
        IslandCapsule(skin, now, onClick = onOpenRecords)

        Spacer(Modifier.height(22.dp))
        // 玻璃面板
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(skin.cardBg, RoundedCornerShape(28.dp))
                .border(1.dp, skin.cardBorder, RoundedCornerShape(28.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(skin.accent.copy(alpha = 0.55f), skin.accent.copy(alpha = 0.18f))
                                ),
                                RoundedCornerShape(16.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(now.title.take(1).ifBlank { "岛" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = skin.textPrimary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (now.hasTrack) now.title else "未在播放",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = skin.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (now.hasTrack) listOf(now.artist, ListenRecordsStore.appLabel(now.packageName)).filter { it.isNotBlank() }.joinToString(" · ") else "—",
                            fontSize = 11.sp,
                            color = skin.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "今日已听 " + durationShort(todayMs) + " · " + today.size + " 首",
                    fontSize = 11.sp,
                    color = skin.accent,
                )

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelAction("岛设置", "歌词 / 材质", skin, Modifier.weight(1f), onOpenSuperIsland)
                    PanelAction("听歌记录", "历史 / 海报", skin, Modifier.weight(1f), onOpenRecords)
                    PanelAction("更多", "歌词源 / 关于", skin, Modifier.weight(1f), onOpenAdvanced)
                }

                Spacer(Modifier.height(16.dp))
                SectionLabel("最近播放", skin.textPrimary, "${live.records.size} 首", skin.textSecondary)
                RecentList(skin, live.records, 3, onOpenRecords)
            }
        }

        Spacer(Modifier.height(14.dp))
        MasterSwitchCard(context)

        Spacer(Modifier.height(16.dp))
        SectionLabel("首页主题", skin.textPrimary, skin.name, skin.accent)
        Spacer(Modifier.height(8.dp))
        HomeSkinPicker(currentId = skinId, onPick = { id -> HomeSkins.write(context, id) })

        Footer(skin, moduleActive)
    }
}

@Composable
private fun PanelAction(
    title: String,
    sub: String,
    skin: HomeSkin,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(skin.cardBg.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
            .border(1.dp, skin.cardBorder.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = skin.textPrimary)
            Text(sub, fontSize = 10.sp, color = skin.textSecondary, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// ===== 快捷入口两种排布 =====

@Composable
private fun ThemedQuickGrid(
    skin: HomeSkin,
    onOpenSuperIsland: () -> Unit,
    onOpenStatusBarLyric: () -> Unit,
    onOpenLyricSource: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    SectionLabel("快捷入口", skin.textPrimary)
    Spacer(Modifier.height(10.dp))
    val items = listOf(
        Triple("超级岛设置", "歌词 / 布局 / 材质", onOpenSuperIsland),
        Triple("状态栏歌词", "独立样式与动画", onOpenStatusBarLyric),
        Triple("歌词源", "扫描 / 白名单", onOpenLyricSource),
        Triple("听歌记录", "历史 / 统计 / 海报", onOpenRecords),
    )
    items.chunked(2).forEachIndexed { rowIndex, chunk ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (rowIndex == 0) 10.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            chunk.forEach { (title, sub, action) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(skin.cardBg, RoundedCornerShape(18.dp))
                        .border(1.dp, skin.cardBorder, RoundedCornerShape(18.dp))
                        .clickable(onClick = action)
                        .padding(14.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(skin.chipFor(title).copy(alpha = 0.22f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("›", fontSize = 13.sp, color = skin.chipFor(title))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = skin.textPrimary)
                        Text(sub, fontSize = 10.sp, color = skin.textSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "更多设置 ›",
        fontSize = 11.sp,
        color = skin.textSecondary,
        modifier = Modifier
            .clickable(onClick = onOpenAdvanced)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun ThemedQuickRows(
    skin: HomeSkin,
    onOpenSuperIsland: () -> Unit,
    onOpenStatusBarLyric: () -> Unit,
    onOpenLyricSource: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    SectionLabel("快捷入口", skin.textPrimary)
    Spacer(Modifier.height(8.dp))
    val items = listOf(
        Triple("超级岛设置", "歌词 / 布局 / 材质 / 避让", onOpenSuperIsland),
        Triple("状态栏歌词", "独立样式 / 动画 / 图标", onOpenStatusBarLyric),
        Triple("歌词源", "扫描 / 白名单 / 优先级", onOpenLyricSource),
        Triple("听歌记录", "历史 / 统计 / 海报", onOpenRecords),
    )
    items.forEachIndexed { index, (title, sub, action) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .background(skin.cardBg, RoundedCornerShape(18.dp))
                .border(1.dp, skin.cardBorder, RoundedCornerShape(18.dp))
                .clickable(onClick = action)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(skin.chipFor(title).copy(alpha = 0.22f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("●", fontSize = 10.sp, color = skin.chipFor(title))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = skin.textPrimary)
                Text(sub, fontSize = 10.sp, color = skin.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
            Text("›", fontSize = 15.sp, color = skin.textSecondary)
        }
    }
    Text(
        "更多设置 ›",
        fontSize = 11.sp,
        color = skin.textSecondary,
        modifier = Modifier
            .clickable(onClick = onOpenAdvanced)
            .padding(vertical = 4.dp),
    )
}

// 便于在 composable 里做透明度派生
private fun HomeSkin.assertAccent(alpha: Float): Color = accent.copy(alpha = alpha)
