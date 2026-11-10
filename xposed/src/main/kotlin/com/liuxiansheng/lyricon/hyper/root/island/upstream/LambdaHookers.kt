/*
 * This file is based on HyperIsland by 1812z.
 * Original: https://github.com/1812z/HyperIsland
 * Ported to Lyricon V3. Licensed under GPL-3.0-or-later.
 */
package com.liuxiansheng.lyricon.hyper.root.island.upstream

import io.github.libxposed.api.XposedInterface

/**
 * R8 混淆修复：libxposed 的 XposedInterface.Hooker 是 compileOnly 依赖，
 * `.intercept { }` 生成的合成 lambda 类在 release(R8) 下被改名/精简，
 * 运行时报 AbstractMethodError（intercept 方法消失）。
 * 具名类显式实现 Hooker，R8 无法省略接口方法，release 行为与 debug 一致。
 */
class LambdaHooker(
    private val block: (XposedInterface.Chain) -> Any?,
) : XposedInterface.Hooker {
    override fun intercept(chain: XposedInterface.Chain): Any? = block(chain)
}
