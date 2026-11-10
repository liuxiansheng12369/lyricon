package com.liuxiansheng.lyricon.lyric.model.interfaces

import com.liuxiansheng.lyricon.lyric.model.LyricWord

interface IRichLyricLine : ILyricLine {
    var secondary: String?
    var secondaryWords: List<LyricWord>?
    var translation: String?
    var translationWords: List<LyricWord>?
    var roma: String?
}