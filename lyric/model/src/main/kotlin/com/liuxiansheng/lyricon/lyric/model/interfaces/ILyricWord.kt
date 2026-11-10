package com.liuxiansheng.lyricon.lyric.model.interfaces

import com.liuxiansheng.lyricon.lyric.model.LyricMetadata

interface ILyricWord : ILyricTiming {
    var text: String?
    var metadata: LyricMetadata?
}