package io.github.proify.lyricon.lyric.model.interfaces

import io.github.proify.lyricon.lyric.model.LyricMetadata
import io.github.proify.lyricon.lyric.model.LyricWord

interface ILyricLine : ILyricTiming {
    var isAlignedRight: Boolean
    var metadata: LyricMetadata?
    var text: String?
    var words: List<LyricWord>?
}