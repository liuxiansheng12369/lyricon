/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.presentation

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandProbeUtils
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandTextHookerSupport
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandViewRegistry
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * 原生刷新协调器（V3 自研）。
 */
internal object IslandNativeRefreshCoordinator {
    private const val TAG = "IslandNativeRefreshCoordinator"
    private const val REQUEST_DEBOUNCE_MS = 16L
    private const val NATIVE_SETTLE_TIMEOUT_MS = 160L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRequest: RefreshRequest? = null
    private val activeRefreshes = WeakHashMap<ViewGroup, ActiveRefresh>()

    private val requestRunnable = Runnable {
        performPendingRequest()
    }

    fun request(
        onComplete: (ViewGroup) -> Unit
    ) {
        runOnMain {
            pendingRequest = RefreshRequest(onComplete)
            mainHandler.removeCallbacks(requestRunnable)
            mainHandler.postDelayed(requestRunnable, REQUEST_DEBOUNCE_MS)
        }
    }

    /**
     * 由真实岛 hook 在小米完成一次更新后调用。
     * 延迟完成保留为兜底：较新的 suspend 签名不总是通过被 hook 的方法暴露 Boolean 结果。
     */
    fun onSystemUpdateComplete(root: ViewGroup) {
        runOnMain {
            activeRefreshes[root]?.let { complete(root, it, "system_callback") }
        }
    }

    fun clear() {
        runOnMain {
            mainHandler.removeCallbacks(requestRunnable)
            pendingRequest = null
            activeRefreshes.values.forEach { it.settleRunnable?.let(mainHandler::removeCallbacks) }
            activeRefreshes.clear()
        }
    }

    private fun performPendingRequest() {
        val request = pendingRequest ?: return
        pendingRequest = null

        val packageName = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: return

        var accepted = false
        IslandPresentationCoordinator.snapshotAttachedHosts(packageName).forEach { token ->
            if (!isEligibleHost(token)) return@forEach

            val target = resolveTarget(token) ?: return@forEach
            val existing = activeRefreshes[token.root]
            if (existing != null) {
                existing.request = request
                accepted = true
                return@forEach
            }

            val active = ActiveRefresh(token, request)
            activeRefreshes[token.root] = active
            if (invokeNativeUpdate(target, active)) {
                accepted = true
            } else if (activeRefreshes[token.root] === active) {
                activeRefreshes.remove(token.root)
            }
        }

        if (!accepted && activeRefreshes.isEmpty()) {
            HookLogger.d(TAG, "未找到可执行小米原生超级岛刷新的当前媒体岛")
        }
    }

    private fun isEligibleHost(token: IslandViewRegistry.HostToken): Boolean {
        if (!IslandPresentationCoordinator.isCurrentHost(token) ||
            IslandPresentationCoordinator.isHostFrozenForFakeTransition(token)
        ) {
            return false
        }
        val mediaInfo = IslandProbeUtils.extractMediaIslandInfo(
            IslandProbeUtils.getCurrentIslandData(token.root)
        ) ?: return false
        return mediaInfo.packageName == token.packageName &&
                IslandPresentationCoordinator.isCurrentLyricOwner(mediaInfo)
    }

    private fun resolveTarget(token: IslandViewRegistry.HostToken): NativeTarget? {
        val data = IslandProbeUtils.getCurrentIslandData(token.root) ?: return null
        val key = IslandTextHookerSupport.callNoArgMethodResult(data, "getKey")
            as? String
            ?: return null
        if (key.isEmpty()) return null

        val eventCoordinator = IslandTextHookerSupport.callNoArgMethodResult(
            token.root,
            "getDynamicIslandEventCoordinator"
        ) ?: return null
        val windowView = IslandTextHookerSupport.callNoArgMethodResult(
            eventCoordinator,
            "getWindowView"
        ) ?: return null
        val controller = IslandTextHookerSupport.callNoArgMethodResult(
            windowView,
            "getWindowViewController"
        ) ?: return null
        val maxWidth = (controller.let {
            IslandTextHookerSupport.callNoArgMethodResult(it, "getIslandMaxWidth")
        } as? Number)?.toFloat() ?: return null
        val updateMethod = findNativeUpdateMethod(windowView.javaClass)
            ?: run {
                HookLogger.w(TAG, "小米超级岛原生刷新接口不可用: target=updateDynamicIslandView")
                return null
            }

        return NativeTarget(
            root = token.root,
            data = data,
            key = key,
            windowView = windowView,
            updateMethod = updateMethod,
            maxWidth = maxWidth
        )
    }

    /** 先查本类 declaredMethods（含非 public），再沿父类链递归补 protected/private 定义。 */
    private fun findNativeUpdateMethod(clazz: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val found = current.declaredMethods.firstOrNull(::isNativeUpdateMethod)
            if (found != null) {
                found.isAccessible = true
                return found
            }
            current = current.superclass
        }
        return null
    }

    private fun invokeNativeUpdate(target: NativeTarget, active: ActiveRefresh): Boolean {
        return runCatching {
            // 按方法签名组装宽度参数：同一方法在 HyperOS 不同版本上第 3 参可能是
            // float / int / double，反射 invoke 对 primitive 参数要求类型精确，不能一律传 Float。
            val widthArg: Any = when (target.updateMethod.parameterTypes[2]) {
                Integer::class.javaPrimitiveType -> target.maxWidth.toInt()
                java.lang.Double::class.javaPrimitiveType -> target.maxWidth.toDouble()
                else -> target.maxWidth.toFloat()
            }
            target.updateMethod.invoke(
                target.windowView,
                target.data,
                false,
                widthArg,
                false
            )
            active.settleRunnable = Runnable {
                activeRefreshes[target.root]?.let { current ->
                    if (current === active) complete(target.root, current, "settle_timeout")
                }
            }
            if (activeRefreshes[target.root] === active) {
                active.settleRunnable?.let {
                    mainHandler.postDelayed(it, NATIVE_SETTLE_TIMEOUT_MS)
                }
            }
            HookLogger.d(
                TAG,
                "已请求小米原生超级岛刷新: root=${System.identityHashCode(target.root)}, " +
                        "keyHash=${target.key.hashCode()}, maxWidth=${target.maxWidth}"
            )
            true
        }.getOrElse { error ->
            HookLogger.w(
                TAG,
                "调用小米原生超级岛刷新失败: root=${System.identityHashCode(target.root)}",
                error
            )
            false
        }
    }

    private fun complete(root: ViewGroup, active: ActiveRefresh, reason: String) {
        if (activeRefreshes[root] !== active) return
        activeRefreshes.remove(root)
        active.settleRunnable?.let(mainHandler::removeCallbacks)

        if (!IslandPresentationCoordinator.isCurrentHost(active.token) ||
            IslandPresentationCoordinator.isHostFrozenForFakeTransition(active.token) ||
            LyriconDataBridge.currentLyricPackageName != active.token.packageName
        ) {
            HookLogger.d(
                TAG,
                "忽略过期的原生超级岛刷新完成: root=${System.identityHashCode(root)}, reason=$reason"
            )
            return
        }

        runCatching { active.request.onComplete(root) }
            .onFailure { error ->
                HookLogger.e(TAG, "原生超级岛刷新后的配置对账失败", error)
            }
    }

    private fun isNativeUpdateMethod(method: Method): Boolean {
        val types = method.parameterTypes
        return method.name == "updateDynamicIslandView" &&
                types.size == 4 &&
                types[1] == Boolean::class.javaPrimitiveType &&
                (types[2] == Float::class.javaPrimitiveType ||
                    types[2] == Integer::class.javaPrimitiveType ||
                    types[2] == java.lang.Double::class.javaPrimitiveType) &&
                types[3] == Boolean::class.javaPrimitiveType
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private data class RefreshRequest(
        val onComplete: (ViewGroup) -> Unit
    )

    private class ActiveRefresh(
        val token: IslandViewRegistry.HostToken,
        var request: RefreshRequest,
        var settleRunnable: Runnable? = null
    )

    private data class NativeTarget(
        val root: ViewGroup,
        val data: Any,
        val key: String,
        val windowView: Any,
        val updateMethod: Method,
        val maxWidth: Float
    )
}
