import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

// ============ 版本号（正式版进位，测试版跟在正式版后面加一位小数） ============
// 正式版：1.1.0 →（下一次打正式版）1.2.0 → 1.3.0 …
// 测试版：跟在当前正式版号后面加一位小数 —— 正式版 1.1.0 之后是 1.1.1、1.1.2 …
//         正式版升到 1.2.0 后，测试版从 1.2.1 重新开始
// 两条规则共用一个小数计数：只在真正发起打包时 +1，打完就停，不自动加。
// 构建失败 / 被打断：下次启动按 .version_state.txt 把号退回去，不白涨。
val selfVersionFile = rootProject.file("build.gradle.kts")
val versionStateFile = rootProject.file(".version_state.txt")
val versionCodeRe = """extra\["appVersionCode"\] = (\d+)""".toRegex()
val releaseNameRe = """extra\["appReleaseVersionName"\] = "(\d+)\.(\d+)\.(\d+)"""".toRegex()
val testPatchRe = """extra\["appTestPatch"\] = (\d+)""".toRegex()

// ↓ 下面是版本号存档（打包脚本自己改写它们），不是本次构建生效值，别手改
extra["appVersionCode"] = 2254
extra["appReleaseVersionName"] = "1.3.1"
extra["appTestPatch"] = 8

fun writeVersionToFile(code: Int, release: String, testPatch: Int) {
    var t = selfVersionFile.readText()
    t = versionCodeRe.replace(t, """extra["appVersionCode"] = $code""")
    t = releaseNameRe.replace(t, """extra["appReleaseVersionName"] = "$release"""")
    t = testPatchRe.replace(t, """extra["appTestPatch"] = $testPatch""")
    selfVersionFile.writeText(t)
}

run {
    var text = selfVersionFile.readText()
    var code = versionCodeRe.find(text)!!.groupValues[1].toInt()
    var rel = releaseNameRe.find(text)!!.groupValues.let { "${it[1]}.${it[2]}.${it[3]}" }
    var patch = testPatchRe.find(text)!!.groupValues[1].toInt()

    if (versionStateFile.exists()) {
        val prev = versionStateFile.readLines()
        if (prev.size >= 3) {
            code = prev[0].toInt()
            rel = prev[1]
            patch = prev[2].toInt()
            writeVersionToFile(code, rel, patch)
            println("上次构建未成功，版本号已回退到 正式=${rel} / 测试补位=${patch} (code ${code})")
        }
        versionStateFile.delete()
    }

    val names = gradle.startParameter.taskNames
    val isRelease = names.any { it.endsWith("Release") }
    val isTest = !isRelease && names.any { it.endsWith("Debug") }

    var buildName = rel
    if (isTest) {
        // 测试版：正式版号 + 加一位小数，例如 1.1.0 → 1.1.1
        patch += 1
        val p = rel.split(".")
        buildName = "${p[0]}.${p[1]}.$patch"
    } else if (isRelease) {
        // 正式版：进到下一个小版本，测试小数位归零，例如 1.1.0 → 1.2.0
        // 例外：打补丁正式版时用 -PforceVersionName=1.3.1 指定，号段按指定值记账
        val forced = (project.findProperty("forceVersionName") as String?)?.takeIf { it.isNotBlank() }
        if (forced != null) {
            buildName = forced
            rel = forced
            patch = 0
        } else {
            val p = rel.split(".")
            buildName = "${p[0]}.${p[1].toInt() + 1}.0"
            rel = buildName
            patch = 0
        }
    }

    if (isRelease || isTest) {
        val oldCode = code
        val oldRel = releaseNameRe.find(selfVersionFile.readText())!!.groupValues.let { "${it[1]}.${it[2]}.${it[3]}" }
        val oldPatch = testPatchRe.find(selfVersionFile.readText())!!.groupValues[1]
        code += 1
        writeVersionToFile(code, rel, patch)
        versionStateFile.writeText("$oldCode\n$oldRel\n$oldPatch\n")
        println("本次打包版本: $buildName (code $code)  [${if (isTest) "测试版" else "正式版"}]")
    }

    extra["appPackageName"] = "com.liuxiansheng.lyricon.v3"
    extra["appVersionCode"] = code
    extra["appReleaseVersionName"] = rel
    extra["appTestPatch"] = patch
    extra["appVersionName"] = buildName
}
extra["compileSdkVersion"] = 37
extra["targetSdkVersion"] = 37
extra["minSdkVersion"] = 33

extra["providerSdkVersion"] = "0.1.71"
extra["subscriberSdkVersion"] = "0.1.71"
extra["lyricModelVersion"] = "0.1.71"

// ============ 构建产物输出（跟随工程所在目录） ============
// 之前因工程根目录含中文（lyricon-v3-全新架构），AGP 测试 worker 的类路径经 @argfile 传递时
// 无法解析非 ASCII 路径，导致"测试类 ClassNotFoundException"，故曾把各模块 build 产物
// 重定向到纯 ASCII 路径 D:\Android\lyricon-build-3086。
// 现工程目录为纯 ASCII（D:\Android\lyricon-3.0.86），取消重定向，恢复 Gradle 默认：
// APK 生成在工程所在目录的 build/all-apks/debug 下，工程移到哪，APK 就在哪生成。
// 注意：若将来把工程放到含中文的目录，需恢复上面的重定向。

// ============ APK 自动导出 ============
val cleanApks: TaskProvider<Task> = tasks.register("cleanAllApks") {
    group = "build"
    doFirst {
        val outputDir = project.layout.buildDirectory.dir("all-apks").get().asFile
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
            println("--- [Clean] 已清理旧的 APK 导出目录 ---")
        }
    }
}

val copyApksAll: TaskProvider<Task> = tasks.register("copyApks") {
    group = "build"
    description = "收集所有 APK 并按 BuildType 分开打包 ZIP"
}

val buildTypeZipTasks = mutableSetOf<String>()

subprojects {
    plugins.withId("com.android.application") {
        val androidComponents = extensions.getByType<ApplicationAndroidComponentsExtension>()

        androidComponents.onVariants { variant ->
            val variantName = variant.name
            val moduleName = project.name
            val buildType = variant.buildType ?: "others"
            val versionName =
                variant.outputs.firstOrNull()?.versionName?.getOrElse(project.version.toString())
                    ?: "1.0"

            val zipTaskName = "zip${buildType.replaceFirstChar { it.uppercase() }}Apks"
            val typeZipTask = rootProject.tasks.maybeCreate(zipTaskName, Zip::class.java).apply {
                group = "build"
                archiveFileName.set("${rootProject.name}-all-$buildType.zip")
                destinationDirectory.set(rootProject.layout.buildDirectory.dir("distributions"))
                from(rootProject.layout.buildDirectory.dir("all-apks/$buildType"))
            }

            val copyTask =
                tasks.register<Copy>("copy${variantName.replaceFirstChar { it.uppercase() }}Apk") {
                    dependsOn(cleanApks)
                    from(variant.artifacts.get(SingleArtifact.APK))
                    into(rootProject.layout.buildDirectory.dir("all-apks/$buildType"))
                    include("*.apk")

                    eachFile { relativePath = RelativePath(true, name) }

                    rename { fileName ->
                        val abiSuffix = when {
                            fileName.contains("arm64-v8a") -> "-arm64-v8a"
                            fileName.contains("armeabi-v7a") -> "-armeabi-v7a"
                            fileName.contains("x86_64") -> "-x86_64"
                            else -> ""
                        }
                        "词岛-${versionName}-${buildType}${abiSuffix}.apk"
                    }
                    duplicatesStrategy = DuplicatesStrategy.INCLUDE
                    finalizedBy(typeZipTask)
                }

            copyApksAll.configure { dependsOn(copyTask) }

            tasks.matching { it.name == "assemble${variantName.replaceFirstChar { c -> c.uppercase() }}" }
                .configureEach { finalizedBy(copyTask) }
        }
    }
}

// ============ 版本号策略：逻辑在文件顶部（只在打包时 +1，打完不再自动加） ============
// 构建成功 → 删掉 .version_state.txt（号段值就是本次打出去的版本）

/** 构建结束：成功才提交版本号（删除回滚点），失败保留给下次回滚。 */
fun commitVersionIfSuccess(assembleSuffix: String, label: String) {
    val assembles = rootProject.allprojects.mapNotNull { p ->
        p.tasks.toList().firstOrNull { it.name.startsWith("assemble") && it.name.endsWith(assembleSuffix) }
    }
    val anyFailure = assembles.any { it.state.failure != null }
    val anyExecuted = assembles.any { it.state.executed }
    if (!anyFailure && anyExecuted) {
        if (versionStateFile.exists()) versionStateFile.delete()
        println("$label 构建成功，版本号已提交")
    } else {
        println("$label 构建未成功，版本号将在下次构建前自动回滚")
    }
}

tasks.register("commitVersionTest") {
    doLast { commitVersionIfSuccess("Debug", "测试版") }
}

tasks.register("commitVersionRelease") {
    doLast { commitVersionIfSuccess("Release", "正式版") }
}

allprojects {
    tasks.matching { it.name.startsWith("assemble") && it.name.endsWith("Debug") }
        .configureEach {
            finalizedBy(":commitVersionTest")
        }
    tasks.matching { it.name.startsWith("assemble") && it.name.endsWith("Release") }
        .configureEach {
            finalizedBy(":commitVersionRelease")
        }
}
