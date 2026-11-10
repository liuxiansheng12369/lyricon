# 词岛（Lyricon v3）

小米澎湃 OS / HyperOS 超级岛歌词 + 状态栏歌词 Xposed 模块。包名 `com.liuxiansheng.lyricon.v3`，namespace `com.liuxiansheng.lyricon.app`，minSdk 33，targetSdk 37。

## 模块结构

| 模块 | 职责 |
|---|---|
| `lyricon` | 工程壳与签名配置 |
| `app` | 设置界面（Compose / Miuix），首页皮肤、听歌记录、字体与参数 |
| `xposed` | 模块本体：注入 SystemUI 超级岛、状态栏歌词、歌词渲染与特效 |
| `lyric:view` / `lyric:model` / `lyric:model-legacy` / `lyric:statusbarlyric` | 歌词视图、模型、旧版模型与状态栏歌词 |
| `lyric:bridge:*` | 模块与 App 之间的数据桥（provider / central / subscriber） |
| `common` / `common-legacy` | 公共工具与历史兼容代码 |
| `bridge` | App 侧桥接 |
| `opencc-lite` | 简繁转换 |

## 构建

```bat
gradlew.bat :lyricon:assembleStandardDebug
```

输出在 `build\all-apks\debug\`。

正式版：

```bat
gradlew.bat :lyricon:assembleStandardRelease
```

构建正式版前必须设置环境变量（仓库内不含任何签名文件与口令）：

```
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

未设置 `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD` 时签名配置为空，`assembleStandardRelease` 会失败——这是有意为之，避免口令进入版本库。

## 版本号规则

在根 `build.gradle.kts` 内维护：

- Debug：patch + 1，versionCode + 1
- Release：次版本 + 1，patch 归零，versionCode + 1
- `-PforceVersionName=<x.y.z>`：按指定值记账，patch 归零

当前日志见 `更新日志.md`，功能候选见 `功能候选清单-34项.md`。

## 说明

- 本仓库只有源码与资源，不含 `build/` 产物、APK、签名文件、真机截图与本机临时脚本。
- 字体功能只读取系统已有字体，不随 APK 打包 ttf，安装包体积不受影响。
