import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
}

configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.central"
    compileSdk = rootProject.extra.get("compileSdkVersion") as Int

    defaultConfig {
        minSdk = 27
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":lyric:bridge:provider"))
    api(project(":lyric:bridge:subscriber"))

    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
