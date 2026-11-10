/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.app.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import com.liuxiansheng.lyricon.app.live.ListenPoster
import com.liuxiansheng.lyricon.app.live.ListenRecord
import com.liuxiansheng.lyricon.app.live.ListenRecordsStore
import com.liuxiansheng.lyricon.app.live.ListenStats
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 听歌记录页。
 *
 * 记录口径参考主流音乐 App / Last.fm：
 *  - 只记"有效播放"：听够一半（长曲封顶 4 分钟），30 秒以内的片段不算
 *  - 同一首 30 分钟内重复播放合并成一条，累加收听时长
 *  - 按天分组、时间倒序，最多保留 200 条
 *  - 可单条长按删除，也可一键清空；可关掉自动记录
 */
@Composable
fun ListenRecordsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var records by remember { mutableStateOf<List<ListenRecord>>(emptyList()) }
    var enabled by remember { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(ListenStats.RANGE_WEEK) }
    var poster by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var posterUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var toast by remember { mutableStateOf("") }

    val stats = remember(records, range) { ListenStats.build(records, range) }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        records = ListenRecordsStore.load(context)
        enabled = ListenRecordsStore.isEnabled(context)
    }

    SkinTheme {
        val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
        androidx.compose.runtime.SideEffect {
            val window = (context as android.app.Activity).window
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !isDark
        }
        com.liuxiansheng.lyricon.app.compose.effect.AppBackground(isDarkTheme = isDark) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "听歌记录",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (records.isEmpty()) {
                                "只统计音乐类 App · 听够一半才算一次"
                            } else {
                                "共 ${records.size} 条 · 长按单条可删除"
                            },
                            fontSize = 12.sp,
                            color = subColorOf(isDark),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (records.isNotEmpty()) {
                        SmallPill(
                            text = if (confirmClear) "再点一次清空" else "清空",
                            isDark = isDark,
                            onClick = {
                                if (confirmClear) {
                                    ListenRecordsStore.clear(context)
                                    records = emptyList()
                                    confirmClear = false
                                } else {
                                    confirmClear = true
                                }
                            },
                        )
                    }
                }

                // 自动记录开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(cardBgOf(isDark), RoundedCornerShape(14.dp))
                        .border(1.dp, borderOf(isDark), RoundedCornerShape(14.dp))
                        .clickable {
                            enabled = !enabled
                            ListenRecordsStore.setEnabled(context, enabled)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动记录听歌",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "由词岛模块在系统里抓取，不需要保持 App 打开",
                            fontSize = 11.sp,
                            color = subColorOf(isDark),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (enabled) "已开启" else "已关闭",
                        fontSize = 12.sp,
                        color = if (enabled) accentOf() else subColorOf(isDark),
                    )
                }

                // 统计卡：累计时长 / 有效播放 / 打卡天数 + 时间范围切换 + 生成海报
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                        .background(cardBgOf(isDark), RoundedCornerShape(14.dp))
                        .border(1.dp, borderOf(isDark), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "听歌小结",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                            listOf(
                                ListenStats.RANGE_WEEK to "7 天",
                                ListenStats.RANGE_MONTH to "30 天",
                                ListenStats.RANGE_ALL to "全部",
                            ).forEach { (value, label) ->
                                RangeButton(label, range == value, isDark) { range = value }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        StatCell(stats.totalLabel, "累计收听", isDark, Modifier.weight(1.3f))
                        StatCell("${stats.playCount}", "有效播放", isDark, Modifier.weight(1f))
                        StatCell("${stats.activeDays}", "打卡天数", isDark, Modifier.weight(1f))
                    }
                    if (stats.topArtists.isNotEmpty()) {
                        Text(
                            text = "最爱歌手：" + stats.topArtists.take(2).joinToString(" · ") { it.first },
                            fontSize = 11.sp,
                            color = subColorOf(isDark),
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    if (stats.topSongs.isNotEmpty()) {
                        Text(
                            text = "单曲循环：" + stats.topSongs.first().first,
                            fontSize = 11.sp,
                            color = subColorOf(isDark),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(accentOf().copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .border(1.dp, accentOf().copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .clickable {
                                if (stats.playCount == 0) {
                                    toast = "先听几首歌，再生成报告"
                                } else {
                                    poster = ListenPoster.build(stats)
                                    posterUri = null
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "生成听歌海报 ›",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentOf(),
                        )
                    }
                    if (toast.isNotBlank()) {
                        Text(
                            text = toast,
                            fontSize = 11.sp,
                            color = subColorOf(isDark),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                if (records.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "还没有记录。\n播放一首歌、听够一半以上，这里就会自动记下歌名、歌手和收听时长。",
                            fontSize = 13.sp,
                            color = subColorOf(isDark),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    ) {
                        var lastDay = ""
                        records.forEach { record ->
                            val day = dayLabel(record.time)
                            if (day != lastDay) {
                                lastDay = day
                                Text(
                                    text = day,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = subColorOf(isDark),
                                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
                                )
                            }
                            RecordRow(
                                record = record,
                                isDark = isDark,
                                onDelete = {
                                    ListenRecordsStore.remove(context, record)
                                    records = records.filterNot {
                                        it.title == record.title &&
                                            it.artist == record.artist &&
                                            it.time == record.time
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    poster?.let { bmp ->
        Dialog(onDismissRequest = { poster = null }) {
            Column(
                modifier = Modifier
                    .background(Color(0xFF101014).copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "听歌报告海报",
                    modifier = Modifier
                        .width(260.dp)
                        .aspectRatio(1080f / 1620f),
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    PosterAction("关闭") { poster = null }
                    PosterAction("保存到相册") {
                        val uri = ListenPoster.saveToGallery(context, bmp, stats)
                        if (uri != null) {
                            posterUri = uri
                            toast = "已保存到相册 Pictures/词岛"
                            poster = null
                        } else {
                            toast = "保存失败，请重试"
                        }
                    }
                    PosterAction("分享") {
                        val uri = posterUri ?: ListenPoster.saveToGallery(context, bmp, stats)
                        if (uri != null) {
                            posterUri = uri
                            runCatching { ListenPoster.share(context, uri) }
                        } else {
                            toast = "分享失败，请重试"
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeButton(text: String, selected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) accentOf() else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .border(
                1.dp,
                if (selected) Color.Transparent else borderOf(isDark),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (selected) Color.White else subColorOf(isDark),
        )
    }
}

@Composable
private fun StatCell(number: String, caption: String, isDark: Boolean, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = number,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = caption,
            fontSize = 10.sp,
            color = subColorOf(isDark),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PosterAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 12.sp, color = Color(0xFFEDF1F7))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(record: ListenRecord, isDark: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(cardBgOf(isDark), RoundedCornerShape(14.dp))
            .border(1.dp, borderOf(isDark), RoundedCornerShape(14.dp))
            .combinedClickable(onClick = {}, onLongClick = onDelete)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            val sub = buildString {
                if (record.artist.isNotBlank()) append(record.artist)
                val app = ListenRecordsStore.appLabel(record.packageName)
                if (record.packageName.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(app)
                }
                val heard = ListenRecordsStore.listenedLabel(record.listenedMs)
                if (heard.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(heard)
                }
            }
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    fontSize = 11.sp,
                    color = subColorOf(isDark),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = timeLabel(record.time),
            fontSize = 11.sp,
            color = subColorOf(isDark),
        )
    }
}

@Composable
private fun SmallPill(text: String, isDark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(cardBgOf(isDark), RoundedCornerShape(10.dp))
            .border(1.dp, borderOf(isDark), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@androidx.compose.runtime.Composable
private fun subColorOf(isDark: Boolean): Color {
    val skin = LocalHomeSkin.current
    if (skin != null) return skin.textSecondary
    return if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF334155).copy(alpha = 0.75f)
}

@androidx.compose.runtime.Composable
private fun cardBgOf(isDark: Boolean): Color {
    val skin = LocalHomeSkin.current
    if (skin != null) return skin.cardBg
    return if (isDark) Color(0xFF202024).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.32f)
}

@androidx.compose.runtime.Composable
private fun borderOf(isDark: Boolean): Color {
    val skin = LocalHomeSkin.current
    if (skin != null) return skin.cardBorder
    return if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.70f)
}

/** 强调色：跟随皮肤（黑胶金 / 晨雾蓝灰 / 赛博青），默认走原蓝 */
@androidx.compose.runtime.Composable
private fun accentOf(): Color = LocalHomeSkin.current?.accent ?: Color(0xFF4F7CFF)

private fun dayLabel(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = fmt.format(Date(now))
    val that = fmt.format(Date(epochMs))
    if (that == today) return "今天"
    val yesterday = fmt.format(Date(now - 24 * 60 * 60 * 1000L))
    if (that == yesterday) return "昨天"
    return that
}

private fun timeLabel(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
