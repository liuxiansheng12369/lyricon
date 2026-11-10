/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig

class WallpaperNameProvider : IslandContentProvider {
    override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
        return TextView(context).apply {
            text = getWallpaperName(context)
            textSize = config.textSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun update(view: View, config: ContentProviderConfig, side: String) {
        if (view is TextView) view.text = getWallpaperName(view.context)
    }

    private fun getWallpaperName(ctx: Context): String {
        return try {
            val wm = WallpaperManager.getInstance(ctx)
            val info = wm.wallpaperInfo
            val name = info?.loadLabel(ctx.packageManager)?.toString() ?: "系统壁纸"
            "壁纸 ${name.take(8)}"
        } catch (_: Exception) {
            "壁纸 --"
        }
    }
}
