/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.app.live

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 听歌海报：把统计结果画成一张竖版 PNG（1080×1620），
 * 可保存到相册（Pictures/词岛）或直接分享。
 *
 * 结构照主流「年度听歌报告」：总时长 / 曲目数 / 打卡天数 → 最爱歌手榜 → 最爱单曲 → 来源分布。
 */
object ListenPoster {
    private const val W = 1080
    private const val H = 1620

    fun build(stats: ListenStats): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val accent = Color.parseColor("#7BD8FF")
        val accent2 = Color.parseColor("#C6A6FF")
        val textMain = Color.parseColor("#EEF2F7")
        val textSub = Color.parseColor("#9AA6B2")

        // 背景：深色渐变 + 两团光
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(Color.parseColor("#0B0D12"), Color.parseColor("#161C2A"), Color.parseColor("#0B0D12")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bgPaint)
        val glow = Paint().apply {
            shader = android.graphics.RadialGradient(
                W * 0.85f, H * 0.18f, 520f,
                intArrayOf(Color.parseColor("#4A7BD8FF"), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glow)
        val glow2 = Paint().apply {
            shader = android.graphics.RadialGradient(
                W * 0.10f, H * 0.92f, 560f,
                intArrayOf(Color.parseColor("#3AC6A6FF"), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glow2)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        // 顶部品牌行
        paint.textSize = 34f
        paint.color = accent
        canvas.drawText("词岛 · 听歌记录", 80f, 120f, paint)
        paint.textSize = 30f
        paint.color = textSub
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        canvas.drawText(stats.label + " 的音乐小结", 80f, 166f, paint)

        // 主标题
        paint.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        paint.textSize = 96f
        paint.color = textMain
        canvas.drawText("我的听歌", 80f, 300f, paint)
        canvas.drawText("报告", 80f, 410f, paint)

        // 三块统计
        var x = 80f
        val y = 480f
        val boxH = 200f
        val gap = 30f
        val boxW = (W - 160f - gap * 2) / 3f
        drawStatBox(canvas, paint, x, y, boxW, boxH, stats.totalLabel.replace(" ", ""), "累计收听", accent)
        x += boxW + gap
        drawStatBox(canvas, paint, x, y, boxW, boxH, "${stats.playCount}", "有效播放", accent2)
        x += boxW + gap
        drawStatBox(canvas, paint, x, y, boxW, boxH, "${stats.uniqueSongs}", "听过曲目", accent)

        // 最爱歌手榜
        var cursorY = 760f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = 40f
        paint.color = textMain
        canvas.drawText("最爱歌手", 80f, cursorY, paint)
        cursorY += 54f
        if (stats.topArtists.isEmpty()) {
            paint.textSize = 30f
            paint.color = textSub
            canvas.drawText("还没有足够数据", 80f, cursorY + 20f, paint)
            cursorY += 80f
        } else {
            val max = stats.topArtists.first().second.toFloat()
            stats.topArtists.take(3).forEachIndexed { index, (name, count) ->
                paint.textSize = 32f
                paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                paint.color = textSub
                canvas.drawText("${index + 1}", 80f, cursorY + 30f, paint)
                paint.color = textMain
                paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                canvas.drawText(name, 128f, cursorY + 30f, paint)
                paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                paint.textSize = 26f
                paint.color = textSub
                canvas.drawText("${count} 次", W - 80f - paint.measureText("${count} 次"), cursorY + 30f, paint)
                // 进度条
                val barLeft = 128f
                val barRight = W - 80f
                val barTop = cursorY + 46f
                val barPaint = Paint().apply { color = Color.parseColor("#26FFFFFF") }
                canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barTop + 10f), 5f, 5f, barPaint)
                val filled = (barRight - barLeft) * (count.toFloat() / max)
                barPaint.color = if (index == 0) accent else accent2
                canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + filled, barTop + 10f), 5f, 5f, barPaint)
                cursorY += 96f
            }
        }

        // 最爱单曲（前 2 首）
        cursorY += 20f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = 40f
        paint.color = textMain
        canvas.drawText("单曲循环", 80f, cursorY, paint)
        cursorY += 54f
        if (stats.topSongs.isEmpty()) {
            paint.textSize = 30f
            paint.color = textSub
            canvas.drawText("还没有足够数据", 80f, cursorY + 10f, paint)
            cursorY += 70f
        } else {
            stats.topSongs.take(2).forEachIndexed { index, (title, count) ->
                paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                paint.textSize = 32f
                paint.color = textMain
                val clipped = clip(title, 22)
                canvas.drawText("「$clipped」", 80f, cursorY + 30f, paint)
                paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                paint.textSize = 26f
                paint.color = textSub
                canvas.drawText("${count} 次", 150f, cursorY + 66f, paint)
                cursorY += if (index == 0) 96f else 70f
            }
        }

        // 来源分布
        cursorY += 6f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = 34f
        paint.color = textMain
        canvas.drawText("都在哪听", 80f, cursorY, paint)
        cursorY += 44f
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = 28f
        paint.color = textSub
        val sourceText = if (stats.topSources.isEmpty()) "—" else
            stats.topSources.joinToString("    ") { "${it.first} ${it.second}" }
        canvas.drawText(sourceText, 80f, cursorY, paint)

        // 底部
        paint.textSize = 26f
        paint.color = textSub
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("词岛 · 超级岛歌词被系统抓到的每一次认真听歌    $stamp", 80f, H - 80f, paint)

        return bmp
    }

    private fun drawStatBox(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        number: String,
        caption: String,
        accent: Int,
    ) {
        val bg = Paint().apply { color = Color.parseColor("#14FFFFFF") }
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 28f, 28f, bg)
        val border = Paint().apply {
            color = Color.parseColor("#33FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 28f, 28f, border)
        paint.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        // 数字过长时自动缩字号，避免出框
        var size = 46f
        paint.textSize = size
        while (paint.measureText(number) > w - 40f && size > 24f) {
            size -= 2f
            paint.textSize = size
        }
        paint.color = accent
        canvas.drawText(number, x + 28f, y + 92f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = 24f
        paint.color = Color.parseColor("#9AA6B2")
        canvas.drawText(caption, x + 28f, y + 150f, paint)
    }

    private fun clip(text: String, max: Int): String =
        if (text.length <= max) text else text.substring(0, max) + "…"

    /** 保存到相册，返回 content uri（失败返回 null）。 */
    fun saveToGallery(context: Context, bitmap: Bitmap, stats: ListenStats): Uri? {
        val name = "词岛听歌报告_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date()) + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/词岛"
            )
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.onFailure { return null }
        return uri
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享听歌报告"))
    }
}
