/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.content

import android.view.View
import java.util.WeakHashMap

/** 内容签名缓存（V3 自研）：按视图弱引用保存最近一次提交的内容签名。 */
internal object IslandSlotContentSignatureCache {
    private val signatures = WeakHashMap<View, String>()

    fun get(view: View): String? = signatures[view]

    fun set(view: View, signature: String) {
        signatures[view] = signature
    }

    fun invalidate(view: View? = null) {
        if (view == null) {
            synchronized(signatures) { signatures.clear() }
        } else {
            synchronized(signatures) { signatures.remove(view) }
        }
    }
}
