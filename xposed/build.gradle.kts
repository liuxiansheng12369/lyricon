import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    kotlin("plugin.parcelize")
}

configure<LibraryExtension> {
    namespace = "com.liuxiansheng.lyricon.xposed"
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

    flavorDimensions += "locale"
    productFlavors {
        create("standard") {
            dimension = "locale"
        }
        create("zh") {
            dimension = "locale"
        }
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
        aidl = true
    }
}

dependencies {
    implementation(project(":bridge"))
    implementation(project(":common"))
    implementation(project(":lyric:view"))
    implementation(project(":lyric:model"))
    implementation(project(":lyric:statusbarlyric"))
    implementation(project(":common-legacy"))

    // Provider AIDL 歌词源通道（SystemUI 侧 central + subscriber）
    implementation(project(":lyric:bridge:central"))
    implementation(project(":lyric:bridge:subscriber"))

    // 仅 zh 风味打包 OpenCC 简繁转换库
    "zhImplementation"(project(":opencc-lite"))

    // libxposed API 仅编译期引用，运行期由 SystemUI / Xposed 框架提供
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    // HyperLyric 显示管线依赖（超级岛歌词：YoYo 动画 / 调色板 / 隐藏API绕过）
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("com.daimajia.androidanimations:library:2.4@aar")
    implementation("com.daimajia.easing:library:2.4@aar")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    implementation("androidx.interpolator:interpolator:1.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
