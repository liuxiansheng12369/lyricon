import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
}

configure<LibraryExtension> {
    // 移植自老词幕同名模块，保留原包名/namespace（与 :lyric:bridge:* 一致策略）
    namespace = "io.github.proify.lyricon.statusbarlyric"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int)
    }

    defaultConfig {
        minSdk = rootProject.extra.get("minSdkVersion") as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    implementation(project(":common"))
    implementation(project(":common-legacy"))
    implementation(project(":lyric:view"))
    implementation(project(":lyric:model"))
    implementation(project(":lyric:model-legacy"))
    implementation(project(":lyric:bridge:subscriber"))
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
