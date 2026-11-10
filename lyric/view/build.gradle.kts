import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
}

configure<LibraryExtension> {
    namespace = "com.liuxiansheng.lyricon.lyric.view"
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
    //noinspection UseTomlInstead
    implementation("com.daimajia.androidanimations:library:2.4@aar")
    implementation("com.daimajia.easing:library:2.4@aar")

    implementation(project(":lyric:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.interpolator)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
