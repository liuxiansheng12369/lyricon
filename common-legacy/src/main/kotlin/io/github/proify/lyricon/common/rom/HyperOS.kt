/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现（工具/扩展层，保留自 V3 移植以来的稳定逻辑）。
 */

package io.github.proify.lyricon.common.rom

import android.annotation.SuppressLint
import android.os.Build

object HyperOS {

    val isXiaomiFamilyDevice: Boolean by lazy {
        val brand = Build.BRAND.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()

        brand.contains("xiaomi")
                || brand.contains("redmi")
                || brand.contains("poco")
                || manufacturer.contains("xiaomi")
                || manufacturer.contains("redmi")
                || manufacturer.contains("poco")
                || product.contains("xiaomi")
                || product.contains("redmi")
                || product.contains("poco")

    }

    fun isXiaomiHyperOs3OrAbove(): Boolean {
        return isXiaomiFamilyDevice && detectHyperOsMajor >= 3
    }

    val detectHyperOsMajor: Int by lazy {
        val sources = listOfNotNull(
            getSystemProperty("ro.system.build.version.incremental"),
            getSystemProperty("ro.build.version.incremental"),
            getSystemProperty("ro.vendor.build.version.incremental"),
            getSystemProperty("ro.system.build.fingerprint"),
            getSystemProperty("ro.vendor.build.fingerprint"),
            Build.DISPLAY,
            Build.FINGERPRINT
        )
        val regex = Regex("""(?i)\bOS(\d+)(?:\.\d+)*""")

        sources
            .mapNotNull { source ->
                regex.find(source)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            .maxOrNull() ?: 0
    }

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String): String? {
        return runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, key, "") as? String)?.trim().orEmpty()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}