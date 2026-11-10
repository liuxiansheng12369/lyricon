import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.provider"
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
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
