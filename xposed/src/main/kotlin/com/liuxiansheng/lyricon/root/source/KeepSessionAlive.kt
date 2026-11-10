/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * KeepSessionAlive：B1 暂停防冻结（补回消费端）。
 *
 * 背景：暂停防冻结开关/保持时长（歌词源页·认证源弹窗）通过 ProviderPrefsBridge
 * 写入 Pro 端 b1config ContentProvider（com.liuxiansheng.lyricon.provider.b1config）。
 * 消费端此前从未实现——本类补上：
 *  - 音乐 App 播放暂停且开关开启时，启动周期心跳（每 30s 一次轻量 ContentResolver
 *    query，让进程持续有 binder 活动），对抗系统 freeze 缓存进程导致歌词/播放状态断链；
 *  - 心跳期间每次重读配置：时长变更 30s 内生效；达到 keepMinutes 上限自动停止，
 *    放行系统正常冻结（不无限保活耗电）；
 *  - 恢复播放立即停止心跳。
 * 心跳本身极轻（一次空 query + 30s 间隔），用户已确认微乎其微的耗电可接受。
 */

package com.liuxiansheng.lyricon.hyper.root.source

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger

object KeepSessionAlive {

    private const val TAG = "KeepSessionAlive"

    private const val AUTHORITY = "com.liuxiansheng.lyricon.provider.b1config"
    private const val COL_ENABLE = "enable"
    private const val COL_MINUTES = "minutes"
    private val uri = Uri.parse("content://$AUTHORITY")

    /** 心跳间隔（ms）。 */
    private const val HEARTBEAT_INTERVAL_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var heartbeat: Runnable? = null

    @Volatile
    private var startedAt = 0L

    @Volatile
    private var keepMinutes = 15

    /** 播放状态回调挂载点：恢复播放停、暂停且开启则启动心跳保活。 */
    fun onPlaybackStateChanged(context: Context, isPlaying: Boolean) {
        if (isPlaying) {
            stopHeartbeat()
            return
        }
        val cfg = readConfig(context)
        if (!cfg.first) {
            stopHeartbeat()
            return
        }
        startHeartbeat(context, cfg.second)
    }

    /** 强制停止（歌词源 stop/模块销毁时调用，防泄漏）。 */
    fun stop() = stopHeartbeat()

    private fun startHeartbeat(context: Context, minutes: Int) {
        if (heartbeat != null) return
        keepMinutes = minutes.coerceAtLeast(1)
        startedAt = SystemClock.elapsedRealtime()
        val run = object : Runnable {
            override fun run() {
                if (heartbeat !== this) return
                // 心跳动作：一次轻量 ContentResolver query（binder 活动保持进程活跃防冻结）
                runCatching {
                    context.contentResolver.query(uri, null, null, null, null)?.use { }
                }
                val elapsedMin = (SystemClock.elapsedRealtime() - startedAt) / 60_000L
                if (elapsedMin >= keepMinutes) {
                    heartbeat = null
                    HookLogger.i(TAG, "暂停保活到期停止: keep=${keepMinutes}min elapsed=${elapsedMin}min")
                    return
                }
                // 心跳期间重读配置：时长变更 30s 内生效（切换 30→60 等无需重启）
                keepMinutes = readConfig(context).second.coerceAtLeast(1)
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
        heartbeat = run
        handler.postDelayed(run, HEARTBEAT_INTERVAL_MS)
        HookLogger.i(TAG, "暂停保活启动: keep=${keepMinutes}min 每30s心跳")
    }

    private fun stopHeartbeat() {
        val h = heartbeat ?: return
        heartbeat = null
        handler.removeCallbacks(h)
        if (startedAt != 0L) {
            HookLogger.i(TAG, "暂停保活停止")
        }
        startedAt = 0L
    }

    /** 读 Pro 端 b1config：enable / minutes。Provider 未装或异常回退 关/15。 */
    private fun readConfig(context: Context): Pair<Boolean, Int> {
        return try {
            var enable = false
            var minutes = 15
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ei = c.getColumnIndex(COL_ENABLE)
                    val mi = c.getColumnIndex(COL_MINUTES)
                    if (ei >= 0) enable = c.getInt(ei) != 0
                    if (mi >= 0) minutes = c.getInt(mi)
                }
            }
            enable to minutes
        } catch (e: Exception) {
            false to 15
        }
    }
}
