package io.github.proify.lyricon.lyric.model.interfaces

/**
 * 歌词时间
 *
 * @property begin 开始时间
 * @property end 结束时间
 * @property duration 持续时间
 */
interface ILyricTiming {
    var begin: Long
    var end: Long
    var duration: Long
}