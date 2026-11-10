package io.github.proify.lyricon.lyric.model.interfaces

import io.github.proify.lyricon.lyric.model.LyricMetadata

interface ILyricWord : ILyricTiming {
    var text: String?
    var metadata: LyricMetadata?
}