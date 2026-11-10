package com.liuxiansheng.lyricon.lyric.model.interfaces

import com.liuxiansheng.lyricon.lyric.model.LyricMetadata
import com.liuxiansheng.lyricon.lyric.model.LyricWord

interface ILyricLine : ILyricTiming {
    var isAlignedRight: Boolean
    var metadata: LyricMetadata?
    var text: String?
    var words: List<LyricWord>?
}