/*
 * This file is based on HyperIsland by 1812z.
 * Original: https://github.com/1812z/HyperIsland
 * Ported to Lyricon V3. Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root.island.upstream

import java.lang.reflect.Field
import java.lang.reflect.Method

internal object SystemUiReflection {
    fun findMethod(clazz: Class<*>, name: String, vararg types: Class<*>): Method? {
        runCatching {
            return clazz.getMethod(name, *types).apply { isAccessible = true }
        }
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(name, *types).apply { isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }

    fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredField(name).apply { isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }
}