/*
 * This file is based on HyperIsland by 1812z.
 * Original: https://github.com/1812z/HyperIsland
 * Ported to Lyricon V3. Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root.island.upstream

import java.io.File

internal object IslandBackgroundFile {
    private const val MAX_FILE_SIZE = 128L * 1024L * 1024L
    private val allowedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val allowedRoots = listOf(
        File("/sdcard/Pictures/HyperIsland"),
        File("/storage/emulated/0/Pictures/HyperIsland"),
        File("/sdcard/Android/data/io.github.hyperisland/files"),
        File("/storage/emulated/0/Android/data/io.github.hyperisland/files"),
        File("/sdcard/Pictures/Lyricon"),
        File("/storage/emulated/0/Pictures/Lyricon"),
        File("/sdcard/Android/data/com.liuxiansheng.lyricon.v3/files"),
        File("/storage/emulated/0/Android/data/com.liuxiansheng.lyricon.v3/files"),
    )

    fun resolve(path: String): File? {
        if (path.isBlank()) return null
        return runCatching {
            val file = File(path).canonicalFile
            if (!file.isFile || !file.canRead() || file.length() !in 1..MAX_FILE_SIZE) {
                return@runCatching null
            }
            if (file.extension.lowercase() !in allowedExtensions) return@runCatching null
            val insideAllowedRoot = allowedRoots.any { root ->
                val canonicalRoot = root.canonicalFile
                file.parentFile == canonicalRoot
            }
            file.takeIf { insideAllowedRoot }
        }.getOrNull()
    }
}