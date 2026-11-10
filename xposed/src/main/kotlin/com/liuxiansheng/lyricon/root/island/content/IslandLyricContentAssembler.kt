/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.root.island.content

import android.content.SharedPreferences
import android.text.TextPaint
import android.view.View
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.common.lyric.RichLyricLineSplitter
import com.liuxiansheng.lyricon.hyper.lyric.model.RichLyricLine
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.IRichLyricLine
import com.liuxiansheng.lyricon.hyper.lyric.model.lyricMetadataOf
import com.liuxiansheng.lyricon.lyric.render.view.METADATA_NEXT_LINE_PREVIEW
import com.liuxiansheng.lyricon.lyric.render.view.RichLyricLineView
import com.liuxiansheng.lyricon.lyric.render.view.SpaceGateRichLyricLineView
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.hyper.root.island.config.IslandSlotRuntimeConfig
import com.liuxiansheng.lyricon.hyper.root.island.host.IslandProbeUtils
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import com.liuxiansheng.lyricon.hyper.root.utils.TranslationHelper

/**
 * 歌词内容组装器（V3 自研）。
 *
 * 职责：
 * 1. 从 [LyriconDataBridge] 取当前歌词行；
 * 2. 应用翻译策略（翻译独占 / 交换）与下一句预览；
 * 3. 分离模式下按容器策略拆分左右栏；
 * 4. 提交到歌词视图并维护内容签名（避免重复刷新）。
 */
internal object IslandLyricContentAssembler {

    /**
     * 提交歌词内容到指定视图。
     * @return 是否发生了内容更新（签名变化）
     */
    fun apply(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        lineOverride: IRichLyricLine?,
        force: Boolean,
        playbackActive: Boolean,
        suppressAnimation: Boolean,
        onLineWillApply: ((Float) -> Boolean)?,
        onLineApplied: (() -> Unit)?,
        onLineCancelled: (() -> Unit)?
    ): Boolean {
        val isLeftView = view.tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG
        // 1.0.14：偏移必须在这里算，不能只依赖调用方传进来的行。
        // `IslandContentUpdateCoordinator.updateLyricSlot` 走的是 lineOverride 路径
        // （它自己先拆好行再传进来），那条路径没有偏移，会把右段偏移冲回 0
        // → 左右又各自从第 1 个色开始。这里按同一份拆分结果补算。
        val built = if (lineOverride != null) {
            val offset = if (config.isSplitMode) {
                buildSlotLyricLineWithOffset(view, prefs, config, isLeftView).colorOffset
            } else {
                0
            }
            SlotLine(lineOverride, offset)
        } else {
            buildSlotLyricLineWithOffset(
                view = view,
                prefs = prefs,
                config = config,
                isLeft = isLeftView
            )
        }
        val targetLine = built.line
        // 整句连续配色：把右段该接的色序偏移交给视图（状态栏整行渲染 offset 恒为 0）
        when (view) {
            is RichLyricLineView -> view.paletteOffset = built.colorOffset
            is SpaceGateRichLyricLineView -> view.paletteOffset = built.colorOffset
        }
        val nextLinePreviewEnabledForView = isNextLinePreviewEnabled(prefs, config)
        val disableAll = TranslationHelper.isTranslationDisabled(prefs) ||
                nextLinePreviewEnabledForView
        val translationOnly = TranslationHelper.isTranslationOnly(prefs)
        val signature = "lyric|${lineContentSignature(targetLine)}|${config.styleSignature}"
        if (!force && IslandSlotContentSignatureCache.get(view) == signature) {
            applyPlaybackActive(view, playbackActive)
            return false
        }
        // 3.0.611：force（设置刷新全量路径）但内容+样式签名都没变 → 跳过 setLine，
        // 保留逐字进度——否则切换"频谱封面隐藏"等开关会强制重设同一行歌词，
        // 逐字状态清零、全高亮行从 0 快速重扫 = "字唱完切换开关抖动"。
        // 内容或样式任一变化时签名不同，仍照常 setLine（设置即生效）。
        if (force && IslandSlotContentSignatureCache.get(view) == signature) {
            applyPlaybackActive(view, playbackActive)
            return false
        }

        val applyLine: (View) -> Unit = { target ->
            when (target) {
                is RichLyricLineView -> {
                    target.setLineWithCallbacks(
                        targetLine,
                        onMainLineWillApply = onLineWillApply,
                        onMainLineApplied = onLineApplied,
                        onMainLineCancelled = onLineCancelled
                    )
                    target.setPlaybackActive(playbackActive)
                }

                is SpaceGateRichLyricLineView -> {
                    target.setLineWithCallbacks(
                        targetLine,
                        onMainLineWillApply = onLineWillApply,
                        onMainLineApplied = onLineApplied,
                        onMainLineCancelled = onLineCancelled
                    )
                    target.setPlaybackActive(playbackActive)
                }
            }
        }

        val suppressContentAnimation = suppressAnimation || nextLinePreviewEnabledForView ||
                view.parent == null || !view.isAttachedToWindow
        // 3.0.619 翻译诊断：实证翻译数据层/开关层断在哪
        // （translation 有值但没显示 = 开关层；translation 空 = 数据层）
        HookLogger.i(
            "IslandLyricAssembler",
            "applyDebug tag=${view.tag} trans=${targetLine?.translation?.take(40)} " +
                "sec=${targetLine?.secondary?.take(40)} disableAll=$disableAll " +
                "transOnly=$translationOnly nextPreview=$nextLinePreviewEnabledForView force=$force"
        )
        // 行切换动画由渲染视图内部统一处理；这里只提交内容。
        applyLine(view)
        IslandSlotContentSignatureCache.set(view, signature)
        val viewKey = view.tag?.toString() ?: view.javaClass.simpleName
        val animated = config.lyricAnimationEnabled && !suppressContentAnimation
        val linePresent = targetLine != null
        val secondaryPresent = !targetLine?.secondary.isNullOrBlank()
        val translationPresent = !targetLine?.translation.isNullOrBlank()
        val debugState = listOf(
            linePresent,
            secondaryPresent,
            translationPresent,
            disableAll,
            translationOnly,
            nextLinePreviewEnabledForView,
            animated,
            view.isAttachedToWindow
        ).joinToString("|")
        HookLogger.dState(
            stateId = "IslandLyricContentAssembler:$viewKey",
            tag = "IslandLyricContentAssembler",
            state = debugState
        ) {
            "歌词内容状态已提交: tag=$viewKey, linePresent=$linePresent, " +
                    "secondaryPresent=$secondaryPresent, translationPresent=$translationPresent, " +
                    "translationOnly=$translationOnly, disableAll=$disableAll, " +
                    "nextLinePreview=$nextLinePreviewEnabledForView, " +
                    "animationEnabled=${config.lyricAnimationEnabled}, animated=$animated, " +
                    "attached=${view.isAttachedToWindow}"
        }
        return true
    }

    /** 便捷入口：默认非强制提交。 */
    fun applyLine(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        lineOverride: IRichLyricLine?,
        playbackActive: Boolean = true,
        onLineWillApply: ((Float) -> Boolean)? = null,
        onLineApplied: (() -> Unit)? = null,
        onLineCancelled: (() -> Unit)? = null
    ): Boolean = apply(
        view = view,
        prefs = prefs,
        config = config,
        lineOverride = lineOverride,
        force = false,
        playbackActive = playbackActive,
        suppressAnimation = false,
        onLineWillApply = onLineWillApply,
        onLineApplied = onLineApplied,
        onLineCancelled = onLineCancelled
    )

    /**
     * 构建当前槽位应显示的歌词行：
     * - 非分离模式：返回处理后的整行；
     * - 分离模式：按容器策略（固定 / 动态）拆分成左右栏对应的一侧；
     *   若该侧为空则回退整行，避免空侧渲染。
     */
    fun buildSlotLyricLine(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        isLeft: Boolean
    ): IRichLyricLine? = buildSlotLyricLineWithOffset(view, prefs, config, isLeft).line

    /** 槽位歌词行 + 整句连续配色偏移（右侧 = 左侧字数，色序从左贯到右） */
    internal class SlotLine(val line: IRichLyricLine?, val colorOffset: Int = 0)

    internal fun buildSlotLyricLineWithOffset(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        isLeft: Boolean
    ): SlotLine {
        val rawLine = processedRawLine(prefs, config)
        if (!config.isSplitMode || rawLine == null) return SlotLine(rawLine)
        if (rawLine.text.isNullOrEmpty()) return SlotLine(rawLine)

        val density = view.resources.displayMetrics.density
        val fallbackPaint = TextPaint().apply {
            textSize = config.textSizeSp.toFloat() * density
        }
        val textPaint = when (view) {
            is RichLyricLineView -> TextPaint(view.main.textPaint)
            is SpaceGateRichLyricLineView -> TextPaint(view.main.textPaint)
            else -> fallbackPaint
        }.takeIf { it.textSize > 0f } ?: fallbackPaint
        val secondaryPaint = when (view) {
            is RichLyricLineView -> TextPaint(view.secondary.textPaint)
            is SpaceGateRichLyricLineView -> TextPaint(view.secondary.textPaint)
            else -> TextPaint(textPaint).apply {
                textSize *= config.textSizeRatio
            }
        }.takeIf { it.textSize > 0f } ?: TextPaint(textPaint).apply {
            textSize *= config.textSizeRatio
        }

        fun contentWidthPx(widthDp: Int, parentName: String): Float {
            val wrapperWidthPx = widthDp * density
            val paddingPx = config.geometry.paddingLeftPx(view, parentName) +
                    config.geometry.paddingRightPx(view, parentName)
            return (wrapperWidthPx - paddingPx).coerceAtLeast(1f)
        }

        val leftMinContentPx = contentWidthPx(
            config.geometry.leftMinWidthDp,
            IslandProbeUtils.LEFT_PARENT_NAME
        )
        val leftMaxContentPx = contentWidthPx(
            config.geometry.leftMaxWidthDp,
            IslandProbeUtils.LEFT_PARENT_NAME
        )
        val rightMinContentPx = contentWidthPx(
            config.geometry.rightMinWidthDp,
            IslandProbeUtils.RIGHT_PARENT_NAME
        )
        val rightMaxContentPx = contentWidthPx(
            config.geometry.rightMaxWidthDp,
            IslandProbeUtils.RIGHT_PARENT_NAME
        )
        val containerWidthSpec = if (config.geometry.isDynamicWidth) {
            RichLyricLineSplitter.ContainerWidthSpec.Dynamic(
                leftMinWidthPx = leftMinContentPx,
                leftMaxWidthPx = leftMaxContentPx,
                rightMinWidthPx = rightMinContentPx,
                rightMaxWidthPx = rightMaxContentPx
            )
        } else {
            RichLyricLineSplitter.ContainerWidthSpec.Fixed(
                leftWidthPx = leftMaxContentPx,
                rightWidthPx = rightMaxContentPx
            )
        }
        val splitResult = RichLyricLineSplitter.split(
            line = rawLine,
            primaryPaint = textPaint,
            // 下一句预览暂时渲染在副行：它会在下一次歌词变更时提升到主行，
            // 因此即使当前画笔较小，也要与主行保持同一拆分边界。
            secondaryPaint = if (rawLine.metadata?.getBoolean(METADATA_NEXT_LINE_PREVIEW) == true) {
                textPaint
            } else {
                secondaryPaint
            },
            containerWidthSpec = containerWidthSpec
        )
        val side = if (isLeft) splitResult.left else splitResult.right
        // 1.0.9：整句连续配色。右侧色序从左侧字数处接着走，避免左右各从第 1 个色重新开始。
        val leftCharCount = splitResult.left.text?.length ?: 0
        val rightCharCount = splitResult.right.text?.length ?: 0
        HookLogger.i(
            "IslandColorOffset",
            "split tag=${view.tag} isLeft=$isLeft raw=${rawLine.text?.length} " +
                "left=[${splitResult.left.text}]($leftCharCount) right=[${splitResult.right.text}]" +
                "($rightCharCount) off=${if (isLeft) 0 else leftCharCount}"
        )
        val leftEmpty = splitResult.left.text.isNullOrBlank() &&
                splitResult.left.words.isNullOrEmpty()
        val rightEmpty = splitResult.right.text.isNullOrBlank() &&
                splitResult.right.words.isNullOrEmpty()
        // 1.0.11：短句不再两端各塞一份整行。
        // 之前这里对该侧为空的情况"回退整行"，字少（1 字 / 短英文）时拆不出右段，
        // 结果左右两个都渲染整行，看着就是"左右一模一样、颜色也从第 1 色重来"。
        // 改为：字只够放一侧时，有字的一侧显示整行，另一侧留空。
        if (leftEmpty || rightEmpty) {
            HookLogger.i(
                "IslandLyricAssembler",
                "splitShort isLeft=$isLeft leftEmpty=$leftEmpty rightEmpty=$rightEmpty " +
                    "text=${rawLine.text?.take(20)}"
            )
            val chosen = if (isLeft) splitResult.left else splitResult.right
            val chosenEmpty = if (isLeft) leftEmpty else rightEmpty
            return SlotLine(if (chosenEmpty) null else chosen, 0)
        }
        return SlotLine(side, if (isLeft) 0 else leftCharCount)
    }

    /** 取处理后的原始歌词行：应用翻译策略或下一句预览。
     *  [nextLinePreviewOverride]：null=按 config 判定（超级岛）；true/false=强制（状态栏自行判定）。 */
    fun processedRawLine(
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig? = null,
        nextLinePreviewOverride: Boolean? = null
    ): IRichLyricLine? {
        var rawLine = LyriconDataBridge.currentLyricLine
            ?: return null
        // 3.0.619 翻译诊断：数据层原始行
        HookLogger.i(
            "IslandLyricAssembler",
            "rawLine text=${rawLine.text?.take(30)} trans=${rawLine.translation?.take(30)} " +
                "sec=${rawLine.secondary?.take(30)}"
        )

        val previewEnabled = nextLinePreviewOverride
            ?: (config != null && isNextLinePreviewEnabled(prefs, config, rawLine))
        if (previewEnabled) {
            val previewed = rawLine.withNextLinePreview(LyriconDataBridge.currentNextLyricLine)
            // 3.0.619 翻译诊断：预览接管（会清空翻译）
            HookLogger.i(
                "IslandLyricAssembler",
                "NEXT_PREVIEW enabled: sec=${previewed.secondary?.take(30)} trans=${previewed.translation}"
            )
            return previewed
        }

        // 翻译策略（对齐上游 HyperLyric 语义 + 样式层 effectiveTranslationOnly）：
        // 自动切换翻译开启时，若整首歌无翻译 → 翻译独占/交换全部回退（回到原文 + 副行罗马音/下一句降级）
        val autoSwitch = TranslationHelper.isAutoSwitchTranslation(prefs)
        val songHasTranslation = TranslationHelper.songHasTranslation()
        val transStrategyActive = !autoSwitch || songHasTranslation
        val effectiveTransOnly = TranslationHelper.isTranslationOnly(prefs) && transStrategyActive
        val effectiveSwap = TranslationHelper.isSwapTranslation(prefs) && transStrategyActive
        if (effectiveTransOnly) {
            rawLine = TranslationHelper.applyTranslationOnly(rawLine)
            // 3.0.619 翻译诊断：翻译独占后主行
            HookLogger.i(
                "IslandLyricAssembler",
                "TRANSLATION_ONLY applied: text=${rawLine.text?.take(30)} trans=${rawLine.translation}"
            )
        } else if (effectiveSwap) {
            rawLine = TranslationHelper.swapTranslation(rawLine)
            // 3.0.619 翻译诊断：交换后主/副
            HookLogger.i(
                "IslandLyricAssembler",
                "SWAP applied: text=${rawLine.text?.take(30)} trans=${rawLine.translation?.take(30)}"
            )
        }
        return rawLine
    }

    /** 下一句预览是否启用：仅歌词模式 + 指定歌词源 + 无翻译时才预览。 */
    internal fun isNextLinePreviewEnabled(
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        currentLine: IRichLyricLine? = LyriconDataBridge.currentLyricLine
    ): Boolean {
        val cfgNext = config.nextLyricLine
        val textMode = LyriconDataBridge.isTextMode
        val source = prefs.getString(
            RootConstants.KEY_HOOK_LYRIC_SOURCE,
            RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
        )
        val sourceOk = source == "lyricon" || source == "lyricinfo"

        // 翻译优先于预览：只要当前行或整首歌存在翻译，就不启用下一句预览，
        // 否则预览会把 translation 清空，翻译永远显示不出来。
        val hasSongTranslation =
            LyriconDataBridge.currentSong?.lyrics?.any { !it.translation.isNullOrBlank() } == true
        val hasLineTranslation = !currentLine?.translation.isNullOrBlank()
        val result = cfgNext && !textMode && sourceOk && !hasSongTranslation && !hasLineTranslation
        // 3.0.619 翻译诊断：预览判定各条件
        HookLogger.i(
            "IslandLyricAssembler",
            "nextPreview cfgNext=$cfgNext textMode=$textMode source=$source sourceOk=$sourceOk " +
                "hasSongTrans=$hasSongTranslation hasLineTrans=$hasLineTranslation -> $result"
        )
        return result
    }

    private fun applyPlaybackActive(view: View, playbackActive: Boolean) {
        when (view) {
            is RichLyricLineView -> view.setPlaybackActive(playbackActive)
            is SpaceGateRichLyricLineView -> view.setPlaybackActive(playbackActive)
        }
    }

    /** 内容签名：歌词行内容 + 样式配置，变化才触发刷新。 */
    private fun lineContentSignature(line: IRichLyricLine?): Int {
        if (line == null) return 0
        return listOf(
            line.begin,
            line.end,
            line.duration,
            line.text,
            line.words,
            line.secondary,
            line.secondaryWords,
            line.translation,
            line.translationWords,
            line.roma,
            line.isAlignedRight,
            line.metadata
        ).hashCode()
    }

    /** 构造下一句预览行：把下一句文本放进副行（临时），下次歌词变更提升为主行。 */
    private fun IRichLyricLine.withNextLinePreview(nextLine: IRichLyricLine?): IRichLyricLine {
        val nextText = nextLine?.text?.takeIf { it.isNotBlank() }
        return RichLyricLine(
            begin = begin,
            end = end,
            duration = duration,
            isAlignedRight = isAlignedRight,
            metadata = lyricMetadataOf(
                *(metadata?.entries?.map { it.key to it.value } ?: emptyList()).toTypedArray(),
                METADATA_NEXT_LINE_PREVIEW to "true"
            ),
            text = text,
            words = words,
            secondary = nextText,
            secondaryWords = emptyList(),
            translation = null,
            translationWords = null,
            roma = null
        )
    }
}
