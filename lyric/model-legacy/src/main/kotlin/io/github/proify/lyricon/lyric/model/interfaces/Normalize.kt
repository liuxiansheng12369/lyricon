package io.github.proify.lyricon.lyric.model.interfaces

interface Normalize<T : Normalize<T>> {
    /**
     * 规范化对象
     */
    fun normalize(): T
}