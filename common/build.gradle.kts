import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

configure<LibraryExtension> {
    namespace = "com.liuxiansheng.lyricon.common"
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
    // Hook 捕获基座（M2）需要 libxposed API，仅在编译期引用，运行期由 SystemUI 提供
    compileOnly(libs.libxposed.api)

    api(libs.kotlinx.serialization.json)
    api(libs.androidx.core.ktx)

    // 歌词数据模型（Song/LyricLine 等），LyricDataHub 依赖
    api(project(":lyric:model"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
