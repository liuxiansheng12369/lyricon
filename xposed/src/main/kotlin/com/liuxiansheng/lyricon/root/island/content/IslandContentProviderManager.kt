/*
 * Lyricon V3 · 超级岛内容模式框架（移植自老词幕 IslandContentBridge）
 * IslandContentProviderManager：Provider 视图生命周期管理。
 * - 按（容器视图 → 侧）持有 Provider 绑定，视图被系统回收时绑定自动失效
 * - 模式切换时销毁旧 Provider、创建新 Provider
 * - 与 IslandSlotStructureInjector / IslandSlotContentFacade 集成
 */

package com.liuxiansheng.lyricon.hyper.root.island.content

import android.content.Context
import android.view.View
import android.view.ViewParent
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.ContentProviderConfig
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentMode
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProvider
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.IslandContentProviderFactory
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import java.util.WeakHashMap

/**
 * 内容 Provider 管理器（V3 接入层）。
 * 歌词（LYRIC）与音乐信息（SONG_INFO）仍走 V3 现有渲染链路；
 * 其余 53 种内容模式经 [IslandContentProviderFactory] 渲染。
 */
internal object IslandContentProviderManager {

    private const val TAG = "IslandContentProviderManager"

    private class Binding(
        val mode: IslandContentMode,
        val provider: IslandContentProvider,
        @Volatile var config: ContentProviderConfig
    )

    /** view → binding 弱引用表：view 被系统回收后绑定自动消失，防泄漏。 */
    private val bindings = WeakHashMap<View, Binding>()

    /** 是否为 Provider 渲染模式（非歌词/非音乐信息）。 */
    fun isProviderMode(mode: Int): Boolean = mode >= 100

    /** 由渲染配置构造 Provider 渲染配置。 */
    fun buildConfig(config: IslandSlotRuntimeConfig): ContentProviderConfig {
        return ContentProviderConfig(
            textSize = config.textSizeSp * config.textSizeRatio,
            maxWidthPx = 0,
            displayTranslation = !config.disableTranslation,
            displayRoma = config.syllableLineDisplay,
            enableRelativeProgress = config.syllableRelative,
            enableRelativeProgressHighlight = config.syllableHighlight,
            fontWeight = config.fontWeight,
            fontItalic = config.fontItalic,
            customFontPath = config.customFontPath
        )
    }

    /** 创建 Provider 视图（挂载到 wrapper 前调用）。 */
    fun createProviderView(
        context: Context,
        mode: IslandContentMode,
        providerConfig: ContentProviderConfig,
        side: String,
        tag: String? = null
    ): View {
        val provider = IslandContentProviderFactory.getProvider(mode)
        val view = provider.createView(context, providerConfig, side)
        if (tag != null) view.tag = tag
        bindings[view] = Binding(mode, provider, providerConfig)
        runCatching { provider.update(view, providerConfig, side) }
        return view
    }

    /** 更新已有 Provider 视图（内容刷新路径）。 */
    fun updateProviderView(
        view: View?,
        providerConfig: ContentProviderConfig,
        side: String
    ): Boolean {
        if (view == null) return false
        val binding = bindings[view] ?: return false
        binding.config = providerConfig
        runCatching { binding.provider.update(view, providerConfig, side) }
        return true
    }

    /** 视图已绑定且模式匹配。 */
    fun isBoundWithMode(view: View, mode: IslandContentMode): Boolean {
        return bindings[view]?.mode == mode
    }

    /** 释放绑定（视图移除时调用，停内部 Handler/Receiver）。 */
    fun destroyProviderView(view: View?) {
        if (view == null) return
        val binding = bindings.remove(view) ?: return
        runCatching { binding.provider.destroy(view) }
        HookLogger.d(TAG, "Provider 释放: mode=${binding.mode}")
    }

    /** 清理某容器（wrapper）下的全部 Provider 绑定（移除/重建时调用）。 */
    fun destroyAllIn(rootView: View?) {
        if (rootView == null) return
        val stale = bindings.keys.filter { isChildOf(rootView, it) }
        stale.forEach { destroyProviderView(it) }
    }

    private fun isChildOf(root: View, target: View): Boolean {
        var p: ViewParent? = target.parent
        while (p is View) {
            if (p === root) return true
            p = p.parent
        }
        return false
    }
}
