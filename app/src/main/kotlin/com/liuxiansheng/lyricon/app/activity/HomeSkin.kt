/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 *
 * HomeSkin：首页主题皮肤（1=黑胶唱片 / 2=晨雾留白 / 3=赛博霓虹）。
 * id=0 表示"默认"——完全走原有逻辑（流光背景 + 现有玻璃卡片配色），本文件不参与。
 * 皮肤只影响首页（MainActivity HomeScreen）与其中卡片；设置页仍用 Miuix 主题。
 */

package com.liuxiansheng.lyricon.app.activity

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 光晕：位置用画布宽高的比例，半径按宽度比例 */
data class HomeGlow(
    val color: Color,
    val cx: Float,
    val cy: Float,
    val radiusRatio: Float,
)

data class HomeSkin(
    val id: Int,
    val name: String,
    val desc: String,
    val dark: Boolean,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val glows: List<HomeGlow>,
    val cardBg: Color,
    val cardBorder: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val chips: List<Color>,
    val islandBg: Color,
    val islandLyric: Color,
    /** 首页整体布局：0=经典区块（原有四套皮肤），1=唱片机，2=晨雾概览，3=仪表盘，4=歌词杂志 */
    val layout: Int = HomeSkins.LAYOUT_CLASSIC,
) {
    fun chipFor(key: String): Color = chips[Math.floorMod(key.hashCode(), chips.size)]
}

object HomeSkins {

    const val KEY = "pref_home_skin"
    const val DEFAULT = 0

    const val LAYOUT_CLASSIC = 0
    const val LAYOUT_DECK = 1
    const val LAYOUT_MIST = 2
    const val LAYOUT_DASH = 3
    const val LAYOUT_LYRIC = 4

    /** ① 唱片机（原「黑胶唱片」换新布局）：深棕 + 铜金，首页主体是黑胶大唱片 */
    val vinyl = HomeSkin(
        id = 1,
        name = "唱片机",
        desc = "深棕 + 铜金，首页即唱机",
        dark = true,
        bgTop = Color(0xFF14100C),
        bgMid = Color(0xFF1B1510),
        bgBottom = Color(0xFF241C14),
        glows = listOf(
            HomeGlow(Color(0x99C8934E), 0.86f, 0.12f, 0.72f),
            HomeGlow(Color(0x553A2A18), 0.12f, 0.74f, 0.60f),
        ),
        cardBg = Color(0xFF221B14).copy(alpha = 0.92f),
        cardBorder = Color(0xFFE9B872).copy(alpha = 0.32f),
        accent = Color(0xFFE9B872),
        textPrimary = Color(0xFFF6F0E4),
        textSecondary = Color(0xFFA79A88),
        chips = listOf(
            Color(0xFFE9B872), Color(0xFF8AB2A4), Color(0xFF9A92CE), Color(0xFFD79A82),
        ),
        islandBg = Color(0xFF2A2320).copy(alpha = 0.96f),
        islandLyric = Color(0xFFF7E1BC),
        layout = LAYOUT_DECK,
    )

    /** ② 晨雾概览（原「晨雾留白」换新布局）：白纸大留白 + 今日听歌数据 */
    val mist = HomeSkin(
        id = 2,
        name = "晨雾概览",
        desc = "白纸留白 + 今日数据",
        dark = false,
        bgTop = Color(0xFFFBFBFA),
        bgMid = Color(0xFFF4F5F6),
        bgBottom = Color(0xFFE7EBEE),
        glows = listOf(
            HomeGlow(Color(0xCCFFFFFF), 0.80f, 0.08f, 0.70f),
            HomeGlow(Color(0x55C0D2DE), 0.16f, 0.72f, 0.60f),
        ),
        cardBg = Color(0xFFFFFFFF).copy(alpha = 0.95f),
        cardBorder = Color(0xFF5A7E94).copy(alpha = 0.26f),
        accent = Color(0xFF5A7E94),
        textPrimary = Color(0xFF20242A),
        textSecondary = Color(0xFF7A8086),
        chips = listOf(
            Color(0xFF5A7E94), Color(0xFF87A6B5), Color(0xFF6E8CA0), Color(0xFFA8BAC4),
        ),
        islandBg = Color(0xFFEFF3F6).copy(alpha = 0.97f),
        islandLyric = Color(0xFF243340),
        layout = LAYOUT_MIST,
    )

    /** ③ 深空仪表（原「赛博霓虹」换新布局）：深色玻璃 + 16 色频谱 + 听歌数据 */
    val cyber = HomeSkin(
        id = 3,
        name = "深空仪表",
        desc = "16 色频谱 + 听歌数据",
        dark = true,
        bgTop = Color(0xFF070C10),
        bgMid = Color(0xFF0A1318),
        bgBottom = Color(0xFF0E1A1E),
        glows = listOf(
            HomeGlow(Color(0x6646E6C8), 0.88f, 0.10f, 0.66f),
            HomeGlow(Color(0x55FF3E8E), 0.10f, 0.70f, 0.58f),
        ),
        cardBg = Color(0xFF131C22).copy(alpha = 0.94f),
        cardBorder = Color(0xFF46E6C8).copy(alpha = 0.34f),
        accent = Color(0xFF46E6C8),
        textPrimary = Color(0xFFE9F5F3),
        textSecondary = Color(0xFF8CA2A6),
        chips = listOf(
            Color(0xFF46E6C8), Color(0xFFFF3E8E), Color(0xFF7C8CFF), Color(0xFFFFB454),
        ),
        islandBg = Color(0xFF060D10).copy(alpha = 0.96f),
        islandLyric = Color(0xFF9BFFF0),
        layout = LAYOUT_DASH,
    )

    /** ④ 歌词杂志：整页歌词 + 玻璃面板 */
    val lyric = HomeSkin(
        id = 4,
        name = "歌词杂志",
        desc = "整页歌词，大字高亮",
        dark = true,
        bgTop = Color(0xFF05161A),
        bgMid = Color(0xFF072025),
        bgBottom = Color(0xFF0A2A2D),
        glows = listOf(
            HomeGlow(Color(0x6646C8BC), 0.82f, 0.14f, 0.72f),
            HomeGlow(Color(0x55306E78), 0.18f, 0.72f, 0.60f),
        ),
        cardBg = Color(0xFF0B2429).copy(alpha = 0.72f),
        cardBorder = Color(0xFF7CF0E2).copy(alpha = 0.30f),
        accent = Color(0xFF7CF0E2),
        textPrimary = Color(0xFFE6F6F4),
        textSecondary = Color(0xFF8FA6A8),
        chips = listOf(
            Color(0xFF7CF0E2), Color(0xFF69B8C8), Color(0xFF9AA8D0), Color(0xFFD6A98C),
        ),
        islandBg = Color(0xFF041012).copy(alpha = 0.98f),
        islandLyric = Color(0xFFD9FFFA),
        layout = LAYOUT_LYRIC,
    )

    val all = listOf(vinyl, mist, cyber, lyric)

    fun of(id: Int): HomeSkin? = all.firstOrNull { it.id == id }

    fun read(context: Context): Int {
        val pref = context.getSharedPreferences(
            "com.liuxiansheng.lyricon_preferences", Context.MODE_PRIVATE
        )
        return pref.getInt(KEY, DEFAULT)
    }

    fun write(context: Context, id: Int) {
        context.getSharedPreferences(
            "com.liuxiansheng.lyricon_preferences", Context.MODE_PRIVATE
        ).edit().putInt(KEY, id).apply()
    }
}

/** 首页当前皮肤；null = 默认（走原有配色分支） */
val LocalHomeSkin = staticCompositionLocalOf<HomeSkin?> { null }
