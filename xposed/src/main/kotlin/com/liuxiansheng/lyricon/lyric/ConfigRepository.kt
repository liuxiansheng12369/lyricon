/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.lyric

import android.content.Context
import androidx.core.content.edit
import com.liuxiansheng.lyricon.hyper.common.PrefsBridge
import com.liuxiansheng.lyricon.hyper.common.ServiceConstants
import com.liuxiansheng.lyricon.hyper.common.UIConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConfigRepository {

    private val _whitelistState = MutableStateFlow<Set<String>>(emptySet())
    val whitelistState = _whitelistState.asStateFlow()

    fun initWhitelist(context: Context) {
        val prefs = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(ServiceConstants.KEY_NOTIFICATION_WHITELIST, emptySet())?.toSet() ?: emptySet()
        _whitelistState.value = savedSet
    }

    fun addPackageToWhitelist(context: Context, packageName: String): Boolean {
        val currentSet = _whitelistState.value.toMutableSet()
        if (!currentSet.add(packageName)) return false
        saveWhitelist(context, currentSet)
        return true
    }

    fun removePackageFromWhitelist(context: Context, packageName: String) {
        val currentSet = _whitelistState.value.toMutableSet()
        if (currentSet.remove(packageName)) {
            saveWhitelist(context, currentSet)
        }
    }

    private fun saveWhitelist(context: Context, set: Set<String>) {
        _whitelistState.value = set
        context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE).edit {
            putStringSet(ServiceConstants.KEY_NOTIFICATION_WHITELIST, set)
        }
        PrefsBridge.putStringSet(ServiceConstants.KEY_NOTIFICATION_WHITELIST, set)
    }
}
