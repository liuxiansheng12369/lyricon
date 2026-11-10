/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.presentation

import android.view.View
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandViewRegistry
import java.util.WeakHashMap

/**
 * 宿主附着观察器（V3 自研）。
 */
internal class IslandHostAttachmentObserver(
    private val currentPresentationRevision: () -> Long,
    private val onHostAttached: (IslandViewRegistry.HostToken, Long) -> Unit
) {
    private val lock = Any()
    private val listeners = WeakHashMap<ViewGroup, View.OnAttachStateChangeListener>()

    fun observe(root: ViewGroup) {
        synchronized(lock) {
            if (listeners.containsKey(root)) return
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    val attachedRoot = view as? ViewGroup ?: return
                    val token = IslandViewRegistry.markAttached(attachedRoot) ?: return
                    val expectedRevision = currentPresentationRevision()
                    attachedRoot.post {
                        onHostAttached(token, expectedRevision)
                    }
                }

                override fun onViewDetachedFromWindow(view: View) {
                    (view as? ViewGroup)?.let(IslandViewRegistry::markDetached)
                }
            }
            listeners[root] = listener
            root.addOnAttachStateChangeListener(listener)
        }
    }

    fun stop(root: ViewGroup) {
        val listener = synchronized(lock) {
            listeners.remove(root)
        } ?: return
        root.removeOnAttachStateChangeListener(listener)
    }
}
