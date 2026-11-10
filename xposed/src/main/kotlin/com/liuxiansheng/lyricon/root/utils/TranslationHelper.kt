/*
 * Lyricon V3 · 翻译显示策略
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.hyper.root.utils

import android.content.SharedPreferences
import com.liuxiansheng.lyricon.hyper.common.RootConstants
import com.liuxiansheng.lyricon.hyper.lyric.model.RichLyricLine
import com.liuxiansheng.lyricon.hyper.lyric.model.interfaces.IRichLyricLine
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge

/**
 * 翻译显示策略（V3 自研）。
 *
 * 根据设置页翻译开关，决定翻译歌词如何参与渲染：
 * - 关闭翻译：调用方（渲染组装）直接不取翻译字段
 * - 翻译独占：翻译提升为主文本，原文不再显示
 * - 交换翻译：主文本与翻译对调（翻译在上、原文在下）
 */
object TranslationHelper {

    /** 翻译是否被关闭。优先读 V3 直写键 v3_show_translation（开=开），
     *  旧配置（老词幕写 disable 取反）回退兼容。 */
    fun isTranslationDisabled(prefs: SharedPreferences): Boolean {
        val result: Boolean
        val source: String
        if (prefs.contains(RootConstants.KEY_SHOW_TRANSLATION)) {
            result = !prefs.getBoolean(RootConstants.KEY_SHOW_TRANSLATION, true)
            source = "KEY_SHOW_TRANSLATION(v3_show_translation)"
        } else {
            result = prefs.getBoolean(
                RootConstants.KEY_HOOK_DISABLE_TRANSLATION,
                RootConstants.DEFAULT_HOOK_DISABLE_TRANSLATION
            )
            source = "KEY_HOOK_DISABLE_TRANSLATION"
        }
        // 3.0.619 翻译诊断：禁用判定来源与结果
        android.util.Log.i("LyricTrans", "isTranslationDisabled=$result source=$source")
        return result
    }

    /** 翻译独占：只显示翻译，不显示原文 */
    fun isTranslationOnly(prefs: SharedPreferences): Boolean {
        val result = prefs.getBoolean(
            RootConstants.KEY_HOOK_TRANSLATION_ONLY,
            RootConstants.DEFAULT_HOOK_TRANSLATION_ONLY
        )
        // 3.0.619 翻译诊断
        android.util.Log.i("LyricTrans", "isTranslationOnly=$result")
        return result
    }

    /** 交换翻译：翻译显示在原文上方 */
    fun isSwapTranslation(prefs: SharedPreferences): Boolean {
        val result = prefs.getBoolean(
            RootConstants.KEY_HOOK_SWAP_TRANSLATION,
            RootConstants.DEFAULT_HOOK_SWAP_TRANSLATION
        )
        // 3.0.619 翻译诊断
        android.util.Log.i("LyricTrans", "isSwapTranslation=$result")
        return result
    }

    /** 自动切换翻译：无翻译内容时自动切换翻译显示策略（上游 HyperLyric 语义） */
    fun isAutoSwitchTranslation(prefs: SharedPreferences): Boolean {
        val result = prefs.getBoolean(
            RootConstants.KEY_HOOK_AUTO_SWITCH_TRANSLATION,
            RootConstants.DEFAULT_HOOK_AUTO_SWITCH_TRANSLATION
        )
        android.util.Log.i("LyricTrans", "isAutoSwitchTranslation=$result")
        return result
    }

    /** 当前整首歌是否存在翻译（供自动切换判定：无翻译时回退原文+罗马音策略） */
    fun songHasTranslation(): Boolean =
        LyriconDataBridge.currentSong?.lyrics?.any { !it.translation.isNullOrBlank() } == true

    /**
     * 翻译独占：把翻译提升为主文本。
     * 无翻译时原样返回，保证渲染层拿到的始终是"当前该显示的那一行"。
     * 注意：不能 `line as? RichLyricLine`——LyriconDataBridge 的行可能是
     * SongPreprocessor 的 TimedLine（IRichLyricLine 委托包装），转型失败会静默原样返回，
     * 导致翻译独占/交换"设置页有代码但不生效"。统一按接口字段重建 RichLyricLine。
     */
    fun applyTranslationOnly(line: IRichLyricLine): IRichLyricLine {
        val translation = line.translation
        if (translation.isNullOrBlank()) return line
        return RichLyricLine(
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            isAlignedRight = line.isAlignedRight,
            metadata = line.metadata,
            text = translation,
            words = line.translationWords ?: emptyList(),
            secondary = line.secondary,
            secondaryWords = line.secondaryWords,
            translation = null,
            translationWords = null,
            roma = line.roma
        )
    }

    /**
     * 交换翻译：主文本与翻译互换。
     * 无翻译时原样返回。
     * 同上：按接口字段重建，不依赖具体实现类型。
     */
    fun swapTranslation(line: IRichLyricLine): IRichLyricLine {
        val translation = line.translation
        if (translation.isNullOrBlank()) return line
        return RichLyricLine(
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            isAlignedRight = line.isAlignedRight,
            metadata = line.metadata,
            text = translation,
            words = line.translationWords ?: emptyList(),
            secondary = line.secondary,
            secondaryWords = line.secondaryWords,
            translation = line.text,
            translationWords = line.words,
            roma = line.roma
        )
    }
}
