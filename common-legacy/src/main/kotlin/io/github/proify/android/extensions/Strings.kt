/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.android.extensions

import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * 将字符串转换为 MD5 哈希值
 * 采用 32 位小写十六进制格式输出
 */
fun String.md5(): String {
    return MessageDigest.getInstance("MD5")
        .digest(this.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/**
 * 将字符串转换为 CRC32 校验值
 */
fun String.crc32(): Long {
    val crc = CRC32()
    crc.update(this.toByteArray(Charsets.UTF_8))
    return crc.value
}