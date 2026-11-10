import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
}

configure<ApplicationExtension> {
    namespace = rootProject.extra["appPackageName"] as String

    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int)
    }

    packaging {
        resources {
            excludes.addAll(
                listOf(
                    "META-INF/**/LICENSE*",
                    "META-INF/**/NOTICE*",
                    "META-INF/*.version",
                    "DebugProbesKt.bin"
                )
            )
        }
        dex {
            // 强制压缩 Dex
            useLegacyPackaging = true
        }
    }

    defaultConfig {
        applicationId = rootProject.extra["appPackageName"] as String
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = rootProject.extra["appVersionCode"] as Int
        versionName = rootProject.extra["appVersionName"] as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        androidResources {
            @Suppress("UnstableApiUsage")
            localeFilters += listOf(
                "en",
                "zh-rCN",
                "zh-rTW",
                "zh-rHK"
            )
        }
    }

    flavorDimensions += "locale"
    productFlavors {
        create("standard") {
            dimension = "locale"
        }
    }

    signingConfigs {
        create("release") {
            // V3 独立签名（与原版 lyricon123 不同，可与原版共存安装）
            // 仓库版不保存签名口令，构建正式版前先设置环境变量：
            // RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD
            storeFile = rootProject.file(System.getenv("RELEASE_STORE_FILE") ?: "release-v3.jks")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "liuxiansheng"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug")
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            // R8 混淆开启以控制体积；hooker 接口由 proguard-rules.pro 的 keep 规则保护，
            // 避免 AbstractMethodError（hook 静默失效）导致岛/歌词不渲染。
            isMinifyEnabled = true
            isShrinkResources = true
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

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
        }
    }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":xposed"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
