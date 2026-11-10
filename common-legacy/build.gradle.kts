import com.android.build.api.dsl.LibraryExtension

/*
 * Lyricon V3 · 老词幕支持面（legacy common）
 *
 * 来源：老词幕 `lyricon-master/common`（只读借鉴，原包名 `io.github.proify.*` 原样保留，
 * 与 `:lyric:model-legacy` 同一策略）。作用：为状态栏域（M5）移植提供
 * `io.github.proify.android.extensions.*`、`io.github.proify.lyricon.common.util.*`、
 * `io.github.proify.lyricon.colorextractor.palette.*` 三套依赖面。
 *
 * 注意：与 V3 自研 `:common`（namespace com.liuxiansheng.lyricon.common）不是一套，勿混用。
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    kotlin("plugin.parcelize")
}

configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.common"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int)
    }

    defaultConfig {
        minSdk = rootProject.extra.get("minSdkVersion") as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "APP_PACKAGE_NAME",
            "\"${rootProject.extra["appPackageName"] as String}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidsvg)

    api(libs.kotlinx.serialization.json)
    api(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
