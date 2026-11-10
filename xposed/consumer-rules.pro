# ====================================================================
# Lyricon xposed 模块 consumer rules（规范化：业务 keep 由各模块自带）
#
# 为什么必须 keep：
#   Xposed 模块的调用链是"运行时"建立的（hook 注入 SystemUI + 反射调 intercept、
#   反射式序列化、反射渲染），R8 编译期静态分析看不到这些调用，
#   会误判为死代码而删掉/改名 → AbstractMethodError / hook 静默失效 / 无歌词。
#
# 作用范围：
#   本规则被 app（lyricon）打包时通过 consumerProguardFiles 自动合并进 R8。
#   以后在 com.liuxiansheng.lyricon.** 包下新增的任何类/新模块，
#   都会被下方通配 keep 自动保护，无需再写规则。
# ====================================================================

# 1) 业务包通配保护（hook/反射/渲染全在此包，运行时机制 R8 不可见）
-keep class com.liuxiansheng.lyricon.** { *; }

# 2) Xposed hooker 接口及其实现类（libxposed 运行时注入调用 intercept）
-keep interface io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker { *; }

# 3) kotlinx.serialization（歌词/媒体数据 JSON 解析）
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }

# 4) 动画/布局第三方库（YoYo 动画、palette 取色、interpolator 插值）
-keep class com.daimajia.** { *; }
-keep class androidx.palette.** { *; }
-keep class androidx.interpolator.** { *; }
