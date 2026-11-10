/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.hyper.root.live

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.liuxiansheng.lyricon.hyper.root.LyriconDataBridge
import com.liuxiansheng.lyricon.xposed.systemui.util.CurrentMediaInfo

/**
 * 把"状态栏/岛上正在播的真实内容"推给 App，并顺手结算听歌记录。
 *
 * 两条推送：
 *  1. 实时帧：歌名/歌手/当前歌词/下一句/播放状态变化时推（首页实时预览用）
 *  2. 播放结算：换歌或停播时，把上一首"开始时间 + 实际听了多久 + 总时长"推过去，
 *     App 按 Last.fm 口径决定这条算不算一次有效播放（听够一半或 4 分钟才算）。
 *
 * 方向：SystemUI（本进程）→ App 的 ContentProvider（App 侧 LiveStateProvider）。
 */
object LiveStatePusher {
    private const val TAG = "LiveStatePusher"
    private const val AUTHORITY = "com.liuxiansheng.lyricon.v3.live-state"
    private const val METHOD_PUBLISH = "publish_live"

    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_PLAYING = "playing"
    private const val KEY_PKG = "package"
    private const val KEY_LYRIC = "lyric"
    private const val KEY_NEXT = "next"
    private const val KEY_SONG = "song"

    private const val KEY_FINALIZE = "finalize"
    private const val KEY_F_STARTED_AT = "f_started_at"
    private const val KEY_F_LISTENED_MS = "f_listened_ms"
    private const val KEY_F_DURATION_MS = "f_duration_ms"

    private const val NOT_PLAYING = "未在播放"

    /** 只对音乐类 App 做听歌记录（视频/短视频/听书这些不记）。与 App 侧 ListenRecordsStore.MUSIC_PACKAGES 保持一致。 */
    private val MUSIC_PACKAGES = setOf(
        "com.luna.music",
        "com.netease.cloudmusic",
        "com.tencent.qqmusic",
        "com.kugou.android",
        "com.kuwo.music",
        "com.migu.music",
        "com.spotify.music",
        "com.apple.android.music",
        "com.google.android.apps.youtube.music",
        "com.ximalaya.ting.android",
        "com.tencent.karaoke",
    )

    private val uri: Uri = Uri.parse("content://$AUTHORITY")

    private var started = false
    private var lastContext: Context? = null
    private var lastSignature = ""

    // 当前这一首的结算信息
    private var curTitle = ""
    private var curArtist = ""
    private var curPkg = ""
    private var curStartedAt = 0L
    private var curMaxPosition = 0L
    private var curDuration = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            runCatching { poll() }
            handler.postDelayed(this, 1000)
        }
    }

    fun start(context: Context) {
        if (started) return
        started = true
        lastContext = context.applicationContext ?: context
        handler.post(tick)
        android.util.Log.i(TAG, "实时状态推送已启动")
    }

    private fun poll() {
        val context = lastContext ?: return
        val title = CurrentMediaInfo.title
        val artist = CurrentMediaInfo.artist
        val pkg = CurrentMediaInfo.packageName
        val playing = CurrentMediaInfo.isPlaying
        val position = CurrentMediaInfo.position
        val duration = CurrentMediaInfo.duration

        // 只有音乐类 App 才参与听歌记录；视频/短视频/听书不进结算，也不打乱当前结算状态
        val musicSource = pkg.isBlank() || pkg in MUSIC_PACKAGES

        if (musicSource) {
            // 位置累积：只取最大值，避免拖动进度条把"听了多久"算成一次长播放
            if (position > curMaxPosition) curMaxPosition = position
            if (duration > 0) curDuration = duration

            val trackChanged = title != curTitle && title != NOT_PLAYING
            if (trackChanged) {
                // 上一首结算（第一首之前 curTitle 为空，不结算）
                if (curTitle.isNotBlank()) {
                    android.util.Log.i(
                        TAG,
                        "换歌结算上一首: title=$curTitle listened=${curMaxPosition}ms dur=${curDuration}ms",
                    )
                    pushFinalize(context, curTitle, curArtist, curPkg, curStartedAt, curMaxPosition, curDuration)
                }
                curTitle = title
                curArtist = artist
                curPkg = pkg
                curStartedAt = System.currentTimeMillis()
                curMaxPosition = 0L
                curDuration = if (duration > 0) duration else 0L
                lastSignature = ""
            }

            // 停播：结算当前这首，然后把会话清空（下次重新开始算）
            if (!playing && curTitle.isNotBlank() && position <= 0L) {
                android.util.Log.i(
                    TAG,
                    "停播结算: title=$curTitle listened=${curMaxPosition}ms dur=${curDuration}ms",
                )
                pushFinalize(context, curTitle, curArtist, curPkg, curStartedAt, curMaxPosition, curDuration)
                curTitle = ""
                curArtist = ""
                curPkg = ""
                curStartedAt = 0L
                curMaxPosition = 0L
                curDuration = 0L
            }
        }

        val lyric = LyriconDataBridge.currentLyricLine?.text.orEmpty()
        val next = LyriconDataBridge.currentNextLyricLine?.text.orEmpty()
        val song = LyriconDataBridge.currentSongName.orEmpty()

        val signature = "$title|$artist|$playing|$pkg|$lyric|$next|$song"
        if (signature == lastSignature) return
        lastSignature = signature

        val extras = Bundle().apply {
            putString(KEY_TITLE, title)
            putString(KEY_ARTIST, artist)
            putBoolean(KEY_PLAYING, playing)
            putString(KEY_PKG, pkg)
            putString(KEY_LYRIC, lyric)
            putString(KEY_NEXT, next)
            putString(KEY_SONG, song)
        }
        call(context, extras)
    }

    private fun pushFinalize(
        context: Context,
        title: String,
        artist: String,
        pkg: String,
        startedAt: Long,
        listenedMs: Long,
        durationMs: Long,
    ) {
        val extras = Bundle().apply {
            putBoolean(KEY_FINALIZE, true)
            putString(KEY_TITLE, title)
            putString(KEY_ARTIST, artist)
            putString(KEY_PKG, pkg)
            putLong(KEY_F_STARTED_AT, startedAt)
            putLong(KEY_F_LISTENED_MS, listenedMs)
            putLong(KEY_F_DURATION_MS, durationMs)
        }
        call(context, extras)
    }

    private fun call(context: Context, extras: Bundle) {
        runCatching {
            context.contentResolver.call(uri, METHOD_PUBLISH, null, extras)
        }
    }
}
