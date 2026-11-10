/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common

/** 日志接口（V3 自研）。 */
interface HyperLogger {
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String, e: Throwable? = null)
    fun e(tag: String, msg: String, e: Throwable? = null)
}
