@file:Suppress("unused")

package io.github.proify.lyricon.lyric.model.extensions

import io.github.proify.lyricon.lyric.model.interfaces.DeepCopyable
import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming
import io.github.proify.lyricon.lyric.model.interfaces.Normalize

/**
 * 规范化排序
 */
fun <T : ILyricTiming> List<T>.normalizeSortByTime(): List<T> = sortedBy { it.begin }

/**
 * 深拷贝对象
 */
fun <T : DeepCopyable<T>> List<T>.deepCopy(): List<T> = map { it.deepCopy() }

/**
 * 规范化对象
 */
fun <T : Normalize<T>> List<T>.normalize(): List<T> = map { it.normalize() }