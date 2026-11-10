
package io.github.proify.lyricon.lyric.model

import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming
import kotlinx.serialization.Serializable

/**
 * 歌词时间信息
 *
 * @property begin 开始时间
 * @property end 结束时间
 * @property duration 持续时间
 */
@Serializable
data class LyricTiming(
    override var begin: Long,
    override var end: Long,
    override var duration: Long
) : ILyricTiming