/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


@file:Suppress("unused")

package com.liuxiansheng.lyricon.xposed.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val TAG = "CoverExt"

/** 将 [Bitmap] 保存到文件（与上游 io.github.proify.android.extensions.BitmapExtensions 原样一致） */
fun Bitmap.saveTo(
    file: File,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    quality: Int = 100
): Boolean {
    if (isRecycled) return false

    return try {
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()
        file.outputStream().buffered().use { out ->
            this.compress(format, quality, out)
        }
        true
    } catch (e: IOException) {
        Log.e(TAG, "saveTo: Failed to write file", e)
        false
    }
}

/** 将 [Bitmap] 转换为字节数组（与上游原样一致） */
fun Bitmap.toByteArray(
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    quality: Int = 100
): ByteArray {
    if (isRecycled) return byteArrayOf()
    return ByteArrayOutputStream().use {
        compress(format, quality, it)
        it.toByteArray()
    }
}

/** 从文件安全解码 [Bitmap]，支持按目标尺寸采样（与上游原样一致） */
fun File.toBitmap(reqWidth: Int = 0, reqHeight: Int = 0): Bitmap? {
    if (!exists() || !canRead()) return null

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(this)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                if (reqWidth > 0 && reqHeight > 0) {
                    decoder.setTargetSize(reqWidth, reqHeight)
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_DEFAULT
                decoder.isMutableRequired = true
            }
        } else {
            decodeLegacy(this, reqWidth, reqHeight)
        }
    } catch (e: Exception) {
        Log.e(TAG, "toBitmap: Failed", e)
        null
    }
}

private fun decodeLegacy(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
    val options = BitmapFactory.Options()
    if (reqWidth > 0 && reqHeight > 0) {
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
    }
    options.inPreferredConfig = Bitmap.Config.ARGB_8888
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/** 字符串 MD5（与上游 io.github.proify.android.extensions.Strings 原样一致） */
fun String.md5(): String {
    return MessageDigest.getInstance("MD5")
        .digest(this.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
