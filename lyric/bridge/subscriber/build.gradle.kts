plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

android {
    namespace = "io.github.proify.lyricon.subscriber"
    compileSdk = rootProject.extra.get("compileSdkVersion") as Int

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
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
    api(project(":lyric:model-legacy"))
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
