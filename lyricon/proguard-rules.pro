# Lyricon application ProGuard 规则
# libxposed API 由设备端 SystemUI/Xposed 框架在运行时提供，编译期 compileOnly：
# R8 minify 时 classpath 上不存在这些类，缺失类会导致 minifyZhReleaseWithR8 失败。
# 这些类不会被打进 APK，仅需压制警告，不需要 keep。
-dontwarn io.github.libxposed.api.**

# ====================================================================
# 业务 keep 已规范化到各依赖模块的 consumer-rules.pro（见 xposed/consumer-rules.pro），
# app 打包时自动合并进 R8，无需在此重复维护。
#
# 未来新增独立模块（不在 com.liuxiansheng.lyricon.** 包内）时，
# 请在该模块自己的 consumer-rules.pro 中声明其运行时机制的 keep。
# ====================================================================

# ====================================================================
# R8 release fix (2026-10-22): libxposed is compileOnly at build time,
# so R8 cannot see XposedInterface.Hooker. Inline intercept{} lambdas
# produce synthetic classes that R8 strips the interface method from,
# causing AbstractMethodError at runtime (blur/materials silently dead).
# The named LambdaHooker impl keeps its interface method.
# Keep the whole runtime hook package set (same approach as the upstream
# HyperIsland project: -keep class io.github.hyperisland.xposed.** { *; }).
# The libxposed runtime reaches these classes reflectively; keeping them
# (names + members) makes release behave exactly like debug. The app UI
# layer is NOT kept and is still fully R8-optimized, so the package stays
# small (~2.5MB).
# ====================================================================
-keep class com.liuxiansheng.lyricon.hyper.root.island.upstream.LambdaHooker { *; }
-keep class com.liuxiansheng.lyricon.hyper.** { *; }
-keep class com.liuxiansheng.lyricon.root.** { *; }
-keep class com.liuxiansheng.lyricon.xposed.** { *; }
-keep class com.liuxiansheng.lyricon.lyric.** { *; }
-keep class com.liuxiansheng.lyricon.common.** { *; }
-keep class com.liuxiansheng.lyricon.bridge.** { *; }
-keep class com.liuxiansheng.lyricon.plugin.** { *; }