/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */


@file:Suppress("unused")

package com.liuxiansheng.lyricon.common.hook

import com.liuxiansheng.lyricon.common.log.YLog
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookHandle
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Hook 捕获基座
 *
 * 职责：
 * 1. 去重：synchronizedSet + WeakHashMap，避免同一方法重复 hook
 * 2. 动态 ClassLoader 捕获：SystemUI 插件 ClassLoader 可能动态加载，支持按 ClassLoader 追踪
 * 3. 失败回退：hook 失败不崩溃，打 warning 日志
 * 4. 反射不静默失败：所有反射异常必须打 YLog.warning
 *
 * 性能铁律：hook 动作只在启动/插件加载时发生，热路径 0 反射
 */
object HookCapture {
    private const val TAG = "HookCapture"

    /** 已 hook 的方法标识（类名#方法名#参数签名），去重用 */
    private val hookedMethods = Collections.synchronizedSet(HashSet<String>())

    /** 已捕获的 ClassLoader（WeakReference，防止内存泄漏），key 为 ClassLoader 身份 */
    private val capturedClassLoaders = Collections.synchronizedMap(WeakHashMap<ClassLoader, WeakReference<ClassLoader>>())

    /** 已注册的 HookHandle，用于统一 unhook */
    private val hookHandles = Collections.synchronizedList(ArrayList<HookHandle>())

    /**
     * 尝试 hook 方法，自动去重
     *
     * @param module XposedModule 实例
     * @param clazz 目标类
     * @param methodName 方法名
     * @param parameterTypes 方法参数类型
     * @param hooker hook 回调
     * @return HookHandle? 成功返回 handle，已 hook 过或失败返回 null
     */
    fun hookMethod(
        module: XposedInterface,
        clazz: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        hooker: XposedInterface.Hooker
    ): HookHandle? {
        val key = buildMethodKey(clazz.name, methodName, parameterTypes)

        // 去重：同一方法只 hook 一次
        if (!hookedMethods.add(key)) {
            YLog.debug(TAG, "skip duplicate hook: $key")
            return null
        }

        return try {
            val method = clazz.getDeclaredMethod(methodName, *parameterTypes)
            method.isAccessible = true
            val handle = module.hook(method).intercept(hooker)
            hookHandles.add(handle)
            YLog.info(TAG, "hooked: $key")
            handle
        } catch (e: Throwable) {
            // 反射失败不静默，打 warning
            hookedMethods.remove(key)
            YLog.warning(TAG, "hook failed: $key — ${e.message}")
            null
        }
    }

    /**
     * 尝试 hook 构造方法，自动去重
     */
    fun hookConstructor(
        module: XposedInterface,
        clazz: Class<*>,
        parameterTypes: Array<Class<*>>,
        hooker: XposedInterface.Hooker
    ): HookHandle? {
        val key = buildConstructorKey(clazz.name, parameterTypes)

        if (!hookedMethods.add(key)) {
            YLog.debug(TAG, "skip duplicate constructor hook: $key")
            return null
        }

        return try {
            val constructor = clazz.getDeclaredConstructor(*parameterTypes)
            constructor.isAccessible = true
            val handle = module.hook(constructor).intercept(hooker)
            hookHandles.add(handle)
            YLog.info(TAG, "hooked constructor: $key")
            handle
        } catch (e: Throwable) {
            hookedMethods.remove(key)
            YLog.warning(TAG, "hook constructor failed: $key — ${e.message}")
            null
        }
    }

    /**
     * 捕获动态 ClassLoader（SystemUI 插件等场景）
     * 重复捕获自动去重
     *
     * @param classLoader 待捕获的 ClassLoader
     * @return true 表示新捕获，false 表示已捕获过
     */
    fun captureClassLoader(classLoader: ClassLoader?): Boolean {
        if (classLoader == null) return false
        if (capturedClassLoaders.containsKey(classLoader)) return false

        capturedClassLoaders[classLoader] = WeakReference(classLoader)
        YLog.info(TAG, "captured ClassLoader: ${classLoader.javaClass.name}")
        return true
    }

    /**
     * 从指定 ClassLoader 加载类，失败打 warning
     *
     * @param classLoader 类加载器
     * @param className 全限定类名
     * @return Class<*>? 成功返回类，失败返回 null
     */
    fun loadClass(classLoader: ClassLoader?, className: String): Class<*>? {
        if (classLoader == null) {
            YLog.warning(TAG, "loadClass failed: classLoader is null, className=$className")
            return null
        }
        return try {
            classLoader.loadClass(className)
        } catch (e: Throwable) {
            YLog.warning(TAG, "loadClass failed: $className — ${e.message}")
            null
        }
    }

    /**
     * 安全获取字段，失败打 warning
     */
    fun getField(clazz: Class<*>, fieldName: String): java.lang.reflect.Field? {
        return try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field
        } catch (e: Throwable) {
            YLog.warning(TAG, "getField failed: ${clazz.name}#$fieldName — ${e.message}")
            null
        }
    }

    /**
     * 安全获取方法，失败打 warning
     */
    fun getMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method? {
        return try {
            val method = clazz.getDeclaredMethod(methodName, *parameterTypes)
            method.isAccessible = true
            method
        } catch (e: Throwable) {
            YLog.warning(TAG, "getMethod failed: ${clazz.name}#$methodName — ${e.message}")
            null
        }
    }

    /**
     * 取消所有已注册的 hook
     */
    fun unhookAll() {
        synchronized(hookHandles) {
            hookHandles.forEach { handle ->
                try {
                    handle.unhook()
                } catch (e: Throwable) {
                    YLog.warning(TAG, "unhook failed: ${e.message}")
                }
            }
            hookHandles.clear()
        }
        hookedMethods.clear()
        YLog.info(TAG, "all hooks unhooked")
    }

    /**
     * 检查方法是否已 hook
     */
    fun isHooked(clazz: Class<*>, methodName: String, parameterTypes: Array<Class<*>>): Boolean {
        return hookedMethods.contains(buildMethodKey(clazz.name, methodName, parameterTypes))
    }

    private fun buildMethodKey(className: String, methodName: String, parameterTypes: Array<Class<*>>): String {
        val params = parameterTypes.joinToString(",") { it.name }
        return "$className#$methodName($params)"
    }

    private fun buildConstructorKey(className: String, parameterTypes: Array<Class<*>>): String {
        val params = parameterTypes.joinToString(",") { it.name }
        return "$className#<init>($params)"
    }
}
