/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */

package com.liuxiansheng.lyricon.hyper.lyric.processor

import com.liuxiansheng.lyricon.hyper.lyric.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 歌词数据加工引擎
 * 负责执行前置清洗、后置增强与显示层变换。
 */
object LyricDataProcessor {

    private const val TAG = "LyricDataProcessor"

    /** 注册后置加工插件列表（V3 暂为空，后续可扩展） */
    private val postProcessors: List<PostProcessor> = emptyList()

    /**
     * 执行前置加工。
     * 职责：处理繁简转换、屏蔽词等基础数据清洗。
     */
    fun executePreProcessing(song: Song): Song {
        return song
            .let(::filterBlockedWords)
    }

    /**
     * 执行后置加工流水线 (异步)
     * 职责：顺序等待所有已启用的耗时插件处理完毕，合并结果后统一返回。
     */
    suspend fun executePostProcessingPipeline(song: Song): Song =
        withContext(Dispatchers.Default) {
            var currentSong = song
            postProcessors.forEach { processor ->
                if (processor.isEnabled()) {
                    try {
                        currentSong = processor.process(currentSong)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 处理失败跳过，不影响主流程
                    }
                }
            }
            currentSong
        }

    /**
     * 执行显示层加工。
     * 职责：应用只影响展示、不应参与 AI 等数据增强步骤的变换。
     */
    fun executeDisplayProcessing(song: Song): Song {
        return song
    }

    // --- 内部基础处理方法 ---

    private fun filterBlockedWords(song: Song): Song {
        // V3 暂未实现屏蔽词过滤，后续可扩展
        return song
    }
}
