import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

val versionCode = rootProject.extra["appVersionCode"] as Int
val versionName = rootProject.extra["appVersionName"] as String
val buildTime = System.currentTimeMillis()

configure<LibraryExtension> {
    namespace = "com.liuxiansheng.lyricon.app"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int)
    }

    defaultConfig {
        minSdk = rootProject.extra.get("minSdkVersion") as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("int", "VERSION_CODE", versionCode.toString())
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        buildConfigField("long", "BUILD_TIME", "${buildTime}L")
    }

    flavorDimensions += "locale"
    productFlavors {
        create("standard") {
            dimension = "locale"
            buildConfigField("boolean", "ENABLE_CHINESE_CONVERSION", "false")
        }
        create("zh") {
            dimension = "locale"
            buildConfigField("boolean", "ENABLE_CHINESE_CONVERSION", "true")
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
        compose = true
    }
}

dependencies {
    implementation(project(":bridge"))
    implementation(project(":common"))
    implementation(project(":lyric:view"))
    implementation(project(":lyric:model"))

    implementation(libs.libxposed.service)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    // Compose + Miuix（MIUI 风格设置页）
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.material:material")
    implementation(libs.androidx.activity.compose)
    implementation(libs.miuix.android)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
