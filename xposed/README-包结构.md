# :xposed 包结构说明（改代码前先看这个）

> 最后核对：2026-11-08，按当时工程实际扫描结果写成（脚本 `arch_scan*.py` / `pkg_tree2.py`，见文末）。
> 这份文件只解决一个具体问题：**同一个功能在工程里存在两份同名实现时，哪一份改了才生效。**

---

## 一、先看一级包：哪个是"生效实现"，哪个是"历史副本"

`:xposed` 共 270 个 `.kt`、54,127 行，一级包如下：

| 一级包 | 文件 | 行数 | 性质 | 能不能改 |
|---|---:|---:|---|---|
| `...lyricon.root.**` | 105 | 28,697 | **生效实现主体**（岛 hook、渲染、材质、歌词源、状态栏都在这） | ✅ 改这里 |
| `...lyricon.hyper.**` | 80 | 11,900 | 混合：**一部分是活功能，一部分是历史副本** | ⚠️ 先查下面第二节 |
| `...lyricon.lyric.**` | 51 | 8,028 | 歌词渲染/视图（**注意：在 `:xposed` 里，不在 `:lyric:view` 模块里**） | ✅ |
| `...lyricon.common.**` | 16 | 2,098 | `:xposed` 内部自己的一份 common | ⚠️ 与 `:common` 模块同名，见第四节 |
| `...lyricon.plugin.**` | 10 | 2,011 | 第三方插件运行时（`PluginRuntime` 681 行） | ✅ |
| `...lyricon.xposed.systemui.**` | 6 | 1,040 | SystemUI 侧工具：`LyricInfoParser`(337)、反射工具等 | ✅ |
| `...lyricon.utils.**` | 2 | 358 | 通用小工具 | ✅ |

### `...lyricon.hyper.**` 拆开看

| 子包 | 文件 | 行数 | 性质 |
|---|---:|---:|---|
| `hyper/root/island/upstream/` | 22 | **8,010** | **混合：6 个文件在用（3,752 行）、16 个是死副本（4,258 行）**，详见第二节 |
| `hyper/root/island/content/provider/providers/` | 51 | 3,459 | 活：51 个设备/环境信息 provider（电量、CPU 温度、运营商、蓝牙电量…） |
| `hyper/root/island/content/provider/` | 7 | 431 | 活：provider 注册与调度 |

> 记忆口诀：**`hyper` 里只有 `island/upstream` 是副本，`content/provider` 是活功能。**

---

## 二、`hyper/root/island/upstream/`：谁在生效、谁是死副本

这个子包里 22 个文件中有两类，**必须分开对待**：

> 判定依据不是"文件名带不带 Upstream"，而是**有没有外部文件 `import` 它这个类**（全模块扫描，`upstream_detail.py` / `upstream_imports.py`）。
> 注意：很多文件名带 `Upstream` 后缀，但里面的**类名是干净的**（如 `LiquidGlassDrawableUpstream.kt` 里定义的是 `LiquidGlassDrawable`），所以按类名 grep 会被 `root/**` 的同名类污染，必须按 import 判定。

### 2.1 仍被装载的：6 个文件、3,752 行（改了会生效）

| 文件 | 行数 | 里面被外部 import 的类 | 装载位置 |
|---|---:|---|---|
| `IslandBackgroundHook.kt` | 1,879 | `IslandBackgroundHook` | `root/HookEntry.kt`、`root/island/hooks/SystemUIHookRegistry.kt` |
| `IslandBlurHook.kt` | 1,274 | `IslandBlurHook` | `HookEntry.kt`、`SystemUIHookRegistry.kt`、`root/statusbar/StatusBarLyricHooker.kt` |
| `IslandOutlineHookUpstream.kt` | 341 | `IslandOutlineHook` | `HookEntry.kt`、`SystemUIHookRegistry.kt` |
| `IslandBlurModelsUpstream.kt` | 168 | `IslandType`（`MaterialType`/`BlurConfig`/`MaterialConfig(s)` 等只被 upstream 内部用） | `root/island/hooks/RealIslandHooker.kt` |
| `IslandStateResolver.kt` | 69 | `IslandStateResolver` | `RealIslandHooker.kt` |
| `LambdaHookers.kt` | 21 | `LambdaHooker` | `root/island/effects/blur/softglass/SoftGlassController.kt` |

> 其中 `IslandBackgroundHook.kt` / `IslandBlurHook.kt` / `IslandOutlineHookUpstream.kt` 是**岛背景、模糊、轮廓的实际装载点**，改它们会生效——但它们是"上游移植版"，风格与 `root/**` 不同，改前先看清调用链。

### 2.2 外部零 import 的：16 个文件、4,258 行（**改了不会生效**）

| 副本文件 | 行数 | root 侧对应的生效版本 |
|---|---:|---|
| `LiquidGlassDrawableUpstream.kt` | 1,163 | `root/island/effects/blur/liquidglass/LiquidGlassDrawable.kt`（两者相似度 95.8%） |
| `SoftGlassControllerUpstream.kt` | 778 | `root/island/effects/blur/softglass/SoftGlassController.kt`（相似度 77.4%） |
| `HdrHighlightSurfaceUpstream.kt` | 521 | `root/.../liquidglass/HdrHighlightSurface.kt` |
| `OuterBlurRegistry.kt` | 475 | 无对应（root 侧由 `blur/IslandBackgroundBlurHooker.kt` 承担） |
| `IslandMaterialConfigStore.kt` | 271 | 无对应（root 侧是 `blur/IslandMaterialConfigReader.kt`） |
| `NativeBlurRendererUpstream.kt` | 170 | `root/island/effects/blur/NativeBlurRenderer.kt` |
| `GlassStateBlurController.kt` | 157 | 无对应 |
| `LiquidGlassEdgeHighlightUpstream.kt` | 155 | `root/.../liquidglass/LiquidGlassEdgeHighlight.kt` |
| `TransitionBlurController.kt` | 153 | 无对应 |
| `IslandCoverBackgroundController.kt` | 128 | 无对应（root 侧是 `island/effects/album/`） |
| `ClippedBlurDrawableUpstream.kt` | 90 | `root/island/effects/blur/ClippedBlurDrawable.kt` |
| `SoftGlassConfigUpstream.kt` | 70 | `root/.../softglass/SoftGlassConfig.kt` |
| `IslandBackgroundFileUpstream.kt` | 39 | 无对应 |
| `SystemUiReflectionUpstream.kt` | 36 | `root/island/effects/blur/SystemUiReflection.kt` |
| `UpstreamConfig.kt` | 33 | 无对应 |
| `IslandBlurRuntime.kt` | 19 | 无对应 |

**结论：柔光玻璃、液态玻璃、模糊、折射、HDR 高光这些参数，一律改 `root/island/effects/**`，不要改 `upstream` 里那 16 个副本。**
（只有 `IslandBackgroundHook.kt` / `IslandBlurHook.kt` / `IslandOutlineHookUpstream.kt` 这三个是活的上游实现。）

---

## 三、四条主要链路的文件级路径

### 3.1 歌词链路（"岛停一句"那条）

```
播放器 App
  └─ provider(外部模块) 写 MediaSession.MediaMetadata["lyricInfo"]
      └─ :lyric:bridge:subscriber
          └─ root/source/LyriconSource.kt        (222)  订阅通道
          └─ root/source/LyricInfoSource.kt      (325)  直读 MediaSession 兜底
              └─ root/source/RootLyricSink.kt    (782)  清洗/节流/本地时间轴外推/看门狗
                  └─ root/LyriconDataBridge.kt   (366)  行导航（timingNavigator）
                      └─ lyric/render/**         (8,028) LyricLineView/LyricEffectRenderer
                          └─ root/island/renderer/BaseIslandRenderer.kt
```

辅助：`root/source/KeepSessionAlive.kt`(125) 防进程冻结；`xposed/systemui/media/LyricInfoParser.kt`(337) 解析 `lyricInfo` 三通道。

### 3.2 岛材质/玻璃链路

```
root/island/effects/blur/IslandBackgroundBlurHooker.kt (400)   背景模糊装载
root/island/effects/blur/IslandMaterialConfigReader.kt (243)   材质档 → 参数
root/island/effects/blur/liquidglass/LiquidGlassDrawable.kt (1163)  液态玻璃
root/island/effects/blur/softglass/SoftGlassController.kt  (818)   柔光玻璃
root/island/effects/glow/IslandOuterGlowHooker.kt          (935)   外光晕
root/island/effects/color/**                              (760)   取色/配色
（hook 装载入口：hyper/.../IslandBackgroundHook.kt 1879 + IslandBlurHook.kt 1274）
```

### 3.3 岛的存在与显隐

`root/island/hooks/`(10 文件 2,825) = `RealIslandHooker` / `SystemUIHookRegistry` / `IslandTextHooker` 等；
`root/island/presentation/`(11 文件 1,892) = 显隐协调；`root/island/host/`(5 文件 1,295) = 视图宿主；`root/island/sizing/`(4 文件 1,123) = 尺寸。

### 3.4 状态栏歌词

`root/statusbar/`(8 文件 2,870)，主体 `StatusBarLyricHooker.kt` **2,225 行**（全工程最大文件）。
**注意：`:lyric:statusbarlyric` 模块整个只有 14 行占位文件，实现不在这里。**

---

## 四、容易找错类的三处"同名陷阱"

1. **`:xposed` 内 `...lyricon.common.**`（2,098 行）与 `:common` 模块**：`LyricDataHub`、`LyricDataListener` 两边各有一个。
2. **模型类三份**：`lyric:model`、`lyric:model-legacy`、`:xposed` 内部 `...lyricon.lyric.model.**` 都定义了 `Song` / `LyricLine` / `IRichLyricLine` / `TimingNavigator`。
   - 新代码优先用 `:lyric:model`（`com.liuxiansheng.lyricon.*`）；
   - `io.github.proify.*`（`*-legacy`）只应由 `:lyric:bridge:*` 使用。
3. **渲染包在 `:xposed` 而非 `:lyric:view`**：`...lyricon.lyric.render.**` 8,028 行住在 `:xposed`；`:lyric:view` 模块只有 238 行。

**改之前先确认：**
```powershell
# 1) 这个文件在工程里是不是唯一一份
Get-ChildItem -LiteralPath "D:\Android\lyricon-3.0.86" -Recurse -Filter "LiquidGlassDrawable.kt" -File |
  Where-Object { $_.FullName -notmatch "\\build\\" } | ForEach-Object { $_.FullName }

# 2) 这个类除了自己那一份，还有谁引用（0 = 你改的是死副本）
Select-String -LiteralPath (Get-ChildItem -LiteralPath "D:\Android\lyricon-3.0.86" -Recurse -Include *.kt -File |
  Where-Object { $_.FullName -notmatch "\\build\\" }).FullName -Pattern "LiquidGlassDrawableUpstream"
```

---

## 五、模块与包的对照（为什么总在 `:xposed` 里找到东西）

| 模块 | 行数 | 里面实际有什么 |
|---|---:|---|
| `:xposed` | 54,127 | 几乎全部实现（上面所有链路） |
| `:app` | 7,246 | 设置页 UI |
| `:lyric:bridge:*` | 4,221 | provider / central / subscriber 三方通信（只吃 `model-legacy`） |
| `:common-legacy` | 2,786 | 旧 `io.github.proify.*` 工具与模型 |
| `:common` | 1,014 | 新 `com.liuxiansheng.*` 工具 |
| `:lyric:model-legacy` | 994 | 旧模型（bridge 协议依赖） |
| `:lyric:model` | 709 | 新模型 |
| `:bridge` | 231 | App 与 Xposed 侧通信 |
| `:lyric:statusbarlyric` | **14** | 占位文件 `StatusBarPlaceholder.kt`，**无实现** |
| `:opencc-lite` | **7** | 占位文件 `OpenCCPlaceholder.kt`，**无实现** |

---

## 六、约定（新代码遵守这三条，就不会再出现"改了没反应"）

1. **新功能只进 `root/**`（或明确归属的子包），不进 `hyper/root/island/upstream/`。**
2. **新代码只用 `com.liuxiansheng.lyricon.*` 命名空间的模型**，不新引用 `io.github.proify.*`。
3. **改渲染/材质前先跑上面第四节的两条命令**，确认文件唯一且非死副本。

> 待办（下一步会做，不在本文件范围内）：
> - 把 `hyper/root/island/upstream/` 里第二节列出的 **16 个死副本（4,258 行）** 移出编译（`java_ref/`）或删除；
> - 把三个仍在装载的上游 Hook（`IslandBackgroundHook` / `IslandBlurHook` / `IslandOutlineHook`）从 `hyper/root/island/upstream` 迁到 `root/island/hooks/material`，让"位置即语义"。

---

## 七、本文件的数据来源

`D:\Android\plugin_cmp\` 下的只读脚本：`arch_scan.py`（模块体量与跨模块同名类）、`arch_scan2.py`（两棵包树对比 + upstream 清单）、`arch_scan3.py`（同 FQN 冲突 0 个 + 文件相似度）、`arch_reach.py`（可达性）、`pkg_tree2.py`（本文件第一节与第三、五节的包体量）。
