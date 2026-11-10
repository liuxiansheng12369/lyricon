/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * 内置字体库：扫描系统与小米预置的中文字体目录，逐个确认字体文件里真的含汉字，
 * 列成可直接点选的样式，选中后写入与「自定义字体文件路径」同一个键，
 * 岛与状态栏歌词都会跟着换，不需要用户自己去文件管理器里找 .ttf 抄路径。
 */

package com.liuxiansheng.lyricon.app.activity

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuxiansheng.lyricon.app.util.MasterPrefs
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.RandomAccessFile

/** 一个可选字体：[path] 为空表示"跟随系统" */
data class BuiltinFont(val name: String, val note: String, val path: String)

object BuiltinFonts {

    /** 常见 HyperOS / MIUI 中文字体，取第一个真实存在且可读的路径（这些名字本身就是中文，不再做字形检测） */
    private val curated: List<Triple<String, String, List<String>>> = listOf(
        Triple(
            "MiSans", "HyperOS 默认中文字体",
            listOf("/system/fonts/MiSansVF.ttf", "/system/fonts/MiSans-Regular.ttf", "/system/fonts/MiSansVF.otf")
        ),
        Triple(
            "小米兰亭 Pro", "MIUI 经典中文字体",
            listOf("/system/fonts/MiLanProVF.ttf", "/system/fonts/MiLanPro-Regular.ttf", "/system/fonts/MiLanProVFTF.ttf")
        ),
        Triple(
            "思源黑体", "Noto Sans CJK，偏方正",
            listOf(
                "/system/fonts/NotoSansCJK-Regular.ttc",
                "/system/fonts/NotoSansCJKsc-Regular.otf",
                "/system/fonts/NotoSansCJKzh-Regular.ttc"
            )
        ),
        Triple(
            "思源宋体", "Noto Serif CJK，笔画带衬线",
            listOf("/system/fonts/NotoSerifCJK-Regular.ttc", "/system/fonts/NotoSerifCJKsc-Regular.otf")
        ),
        Triple(
            "MiSans 圆润", "小米圆体",
            listOf("/product/fonts/MiSansRoundedSC.ttf")
        ),
        Triple(
            "MiSerif", "小米衬线体",
            listOf("/product/fonts/MiSerifSCVF.ttf")
        )
    )

    /** 这些文件名里带的是别的语种/别的用途，直接跳过，别混进中文列表 */
    private val denyNames = listOf(
        "bengali", "devanagari", "gujarati", "gurmukhi", "tamil", "telugu", "thai",
        "arabic", "hebrew", "kannada", "malayalam", "sinhala", "myanmar", "ethiopic",
        "khmer", "lao", "odia", "oriya", "tibetan", "mongolian", "japanese", "korean",
        "latin", "emoji", "mono", "clock", "webview", "_overlay", "ttc.bak"
    )

    fun list(): List<BuiltinFont> {
        val out = mutableListOf(BuiltinFont("系统默认", "跟随系统字体，不指定文件", ""))
        val usedPaths = HashSet<String>()

        fun add(name: String, note: String, candidatePaths: List<String>) {
            val hit = candidatePaths.firstOrNull { p ->
                runCatching { File(p).let { it.exists() && it.canRead() } }.getOrDefault(false)
            } ?: return
            val canonical = runCatching { File(hit).canonicalPath }.getOrDefault(hit)
            if (!usedPaths.add(canonical)) return
            out += BuiltinFont(name, note, hit)
        }
        curated.forEach { add(it.first, it.second, it.third) }

        // 兜底扫描：把机器上能找到中文字体的地方都扫一遍。
        // 每个文件都读一遍字体里的 cmap 表，确认真的映射了汉字才收，避免把印度语/拉丁字体列进来。
        runCatching {
            var extra = 0
            // 目录 -> 显示备注；recursive 表示往下两级（下载目录里常是套文件夹）
            val dirs = listOf(
                Triple("/product/fonts", "小米预置中文字体", false),
                Triple("/system/fonts", "系统中文字体", false),
                Triple("/data/fonts/files", "系统字体缓存", false),
                Triple("/data/system/fonts", "主题商店已应用字体", false),
                Triple("/data/system/theme/fonts", "主题商店字体", false),
                Triple("/sdcard/Fonts", "存储卡 Fonts 目录", true),
                Triple("/sdcard/fonts", "存储卡 fonts 目录", true),
                Triple("/sdcard/Download", "下载目录", true)
            )
            dirs.forEach { (dirPath, note, recursive) ->
                val dir = File(dirPath)
                if (!dir.isDirectory) return@forEach
                runCatching {
                    val files: List<File> = if (recursive) {
                        dir.walkTopDown().maxDepth(2).filter { it.isFile }.toList().sortedBy { it.name }
                    } else {
                        dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
                    }
                    files.forEach { f ->
                        if (extra >= 20) return@forEach
                        if (!f.canRead()) return@forEach
                        val lower = f.name.lowercase()
                        if (!(lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc"))) return@forEach
                        if (denyNames.any { lower.contains(it) }) return@forEach
                        val canonical = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)
                        if (!usedPaths.add(canonical)) return@forEach
                        if (!hasCjkGlyph(f)) {
                            usedPaths.remove(canonical)
                            return@forEach
                        }
                        out += BuiltinFont(
                            prettifyName(f.name.substringBeforeLast('.')),
                            note,
                            f.absolutePath
                        )
                        extra++
                    }
                }
            }
        }
        return out
    }

    // ---------- 字体字形检测 ----------

    /** 把文件名收拾成人能念的名字：去掉字重/可变字体的技术后缀 */
    private fun prettifyName(raw: String): String = raw
        .replace(Regex("(?i)[-_]?(regular|medium|bold|light|semibold|extralight|extraBold)$"), "")
        .replace(Regex("(?i)[-_]?(vf)$"), "")
        .replace(Regex("(?i)[-_]?(tw|tf)$"), "")
        .trimEnd('-', '_')
        .ifBlank { raw }

    /** 字体文件的 cmap 表里是否有汉字（U+4E2D「中」/ U+4E00「一」） */
    private fun hasCjkGlyph(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val cmapOffset = findCmapOffset(raf) ?: return@use false
            glyphIdFor(raf, cmapOffset, 0x4E2D) > 0 || glyphIdFor(raf, cmapOffset, 0x4E00) > 0
        }
    }.getOrDefault(false)

    /** 在 sfnt（含 ttc 集合）里找到 cmap 表的文件偏移 */
    private fun findCmapOffset(raf: RandomAccessFile): Long? {
        raf.seek(0)
        val tag = raf.readInt()
        val tableDirBase: Long = if (tag == 0x74746366) { // 'ttcf' 字体集合
            raf.seek(12)
            raf.readInt().toLong()
        } else {
            0L
        }
        raf.seek(tableDirBase)
        raf.readInt() // sfntVersion
        val numTables = raf.readUnsignedShort()
        for (i in 0 until numTables) {
            raf.seek(tableDirBase + 12L + i * 16L)
            val name = String(ByteArray(4).also { raf.readFully(it) }, Charsets.US_ASCII)
            raf.readInt() // checksum
            val offset = raf.readInt().toLong()
            raf.readInt() // length
            if (name == "cmap") return offset
        }
        return null
    }

    /** 依次尝试 cmap 内的各子表，返回字符码位对应的 glyph id（0 表示没有该字形） */
    private fun glyphIdFor(raf: RandomAccessFile, cmapOffset: Long, codePoint: Int): Int {
        raf.seek(cmapOffset)
        raf.readUnsignedShort() // version
        val numTables = raf.readUnsignedShort()
        for (i in 0 until numTables) {
            raf.seek(cmapOffset + 4L + i * 8L)
            raf.readUnsignedShort() // platformID
            raf.readUnsignedShort() // encodingID
            val subOffset = raf.readInt().toLong()
            raf.seek(cmapOffset + subOffset)
            val format = raf.readUnsignedShort()
            val gid = when (format) {
                4 -> glyphIdFormat4(raf, cmapOffset + subOffset, codePoint)
                12 -> glyphIdFormat12(raf, cmapOffset + subOffset, codePoint)
                6 -> glyphIdFormat6(raf, cmapOffset + subOffset, codePoint)
                else -> 0
            }
            if (gid > 0) return gid
        }
        return 0
    }

    private fun glyphIdFormat4(raf: RandomAccessFile, base: Long, cp: Int): Int {
        if (cp > 0xFFFF) return 0
        raf.seek(base + 6)
        val segCount = raf.readUnsignedShort() / 2
        val endCodesBase = base + 14L
        val startCodesBase = endCodesBase + segCount * 2L + 2L
        val idDeltaBase = startCodesBase + segCount * 2L
        val idRangeOffsetBase = idDeltaBase + segCount * 2L
        // 找到第一个 endCode >= cp 的段
        for (i in 0 until segCount) {
            raf.seek(endCodesBase + i * 2L)
            val end = raf.readUnsignedShort()
            if (end < cp) continue
            raf.seek(startCodesBase + i * 2L)
            val start = raf.readUnsignedShort()
            if (start > cp) return 0
            raf.seek(idDeltaBase + i * 2L)
            val idDelta = raf.readShort().toInt()
            raf.seek(idRangeOffsetBase + i * 2L)
            val idRangeOffset = raf.readUnsignedShort()
            if (idRangeOffset == 0) {
                return (cp + idDelta) and 0xFFFF
            }
            val glyphIndexAddress = idRangeOffsetBase + i * 2L + idRangeOffset + (cp - start) * 2L
            raf.seek(glyphIndexAddress)
            val glyphIndex = raf.readUnsignedShort()
            if (glyphIndex == 0) return 0
            return (glyphIndex + idDelta) and 0xFFFF
        }
        return 0
    }

    private fun glyphIdFormat12(raf: RandomAccessFile, base: Long, cp: Int): Int {
        raf.seek(base + 12)
        val nGroups = raf.readInt()
        for (i in 0 until nGroups) {
            raf.seek(base + 16L + i * 12L)
            val startChar = raf.readInt()
            val endChar = raf.readInt()
            val startGlyph = raf.readInt()
            if (cp in startChar..endChar) {
                return startGlyph + (cp - startChar)
            }
        }
        return 0
    }

    private fun glyphIdFormat6(raf: RandomAccessFile, base: Long, cp: Int): Int {
        raf.seek(base + 6)
        val firstCode = raf.readUnsignedShort()
        val entryCount = raf.readUnsignedShort()
        if (cp < firstCode || cp >= firstCode + entryCount) return 0
        raf.seek(base + 10L + (cp - firstCode) * 2L)
        return raf.readUnsignedShort()
    }
}

@Composable
private fun rememberFontFamily(path: String): FontFamily = remember(path) {
    if (path.isBlank()) {
        FontFamily.Default
    } else {
        runCatching { FontFamily(Typeface.createFromFile(File(path))) }.getOrDefault(FontFamily.Default)
    }
}

/** 字体设置里的「内置字体」入口：点开就是一排字体，每项用该字体渲染一句歌词预览 */
@Composable
fun BuiltinFontPreference(context: Context) {
    val fonts = remember { BuiltinFonts.list() }
    var current by remember {
        mutableStateOf(MasterPrefs.getString(context, MasterPrefs.KEY_ISLAND_TYPEFACE_PATH, "") ?: "")
    }
    var showDialog by remember { mutableStateOf(false) }

    val currentLabel = when {
        current.isBlank() -> "系统默认"
        else -> fonts.firstOrNull { it.path == current }?.name ?: "自定义字体文件"
    }

    ArrowPreference(
        title = "内置字体",
        summary = "$currentLabel（点选即换，不用填路径）",
        onClick = { showDialog = true }
    )

    if (showDialog) {
        BuiltinFontDialog(
            fonts = fonts,
            currentPath = current,
            onSelected = { font ->
                current = font.path
                MasterPrefs.setString(context, MasterPrefs.KEY_ISLAND_TYPEFACE_PATH, font.path)
                MasterPrefs.setString(context, MasterPrefs.KEY_CUSTOM_FONT_PATH, font.path)
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun BuiltinFontDialog(
    fonts: List<BuiltinFont>,
    currentPath: String,
    onSelected: (BuiltinFont) -> Unit,
    onDismiss: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (shown) 1f else 0.9f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.68f, stiffness = 480f),
        label = "fontDialogScale"
    )
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "fontDialogAlpha"
    )
    val fg = Color(0xFF000000)
    val accent = Color(0xFF2196F3)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .heightIn(max = 520.dp)
                .padding(vertical = 24.dp)
                .graphicsLayer { this.scaleX = scale; this.scaleY = scale; this.alpha = alpha },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            elevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = "内置字体",
                    color = fg,
                    style = MiuixTheme.textStyles.title2,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                )
                Text(
                    text = "已扫系统字体、小米预置字体、系统字体缓存、主题字体目录、存储卡下载目录；" +
                        "主题商店的字体文件受系统权限限制，没出现的话可以用下面「字体文件路径」填绝对路径",
                    color = fg.copy(alpha = 0.55f),
                    style = TextStyle(fontSize = 12.sp),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                )
                Divider(modifier = Modifier.padding(horizontal = 16.dp), color = fg.copy(alpha = 0.14f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 不用 rememberScrollState：它会记住上次的滚动位置，再打开时列表停在中间，看不见「系统默认」
                        .verticalScroll(remember { androidx.compose.foundation.ScrollState(0) })
                        .padding(top = 4.dp, bottom = 4.dp)
                ) {
                    fonts.forEach { font ->
                        val selected = font.path == currentPath
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (selected) {
                                        Modifier.background(Color(0xFFE3F2FD))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    onSelected(font)
                                    onDismiss()
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = font.name,
                                    color = if (selected) accent else fg,
                                    style = MiuixTheme.textStyles.body1
                                )
                                Text(
                                    text = "   ${font.note}",
                                    color = fg.copy(alpha = 0.45f),
                                    style = TextStyle(fontSize = 11.sp)
                                )
                                if (selected) {
                                    Text(text = "  ✓", color = accent, style = TextStyle(fontSize = 13.sp))
                                }
                            }
                            Text(
                                text = "对你有多爱就有多恨",
                                color = fg.copy(alpha = if (selected) 0.95f else 0.80f),
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontFamily = rememberFontFamily(font.path)
                                ),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = fg.copy(alpha = 0.08f))
                    }
                }

                Divider(modifier = Modifier.padding(horizontal = 16.dp), color = fg.copy(alpha = 0.14f))
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
