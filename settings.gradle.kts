@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/repository/jitpack") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

include(
    ":lyricon",
    ":app",
    ":bridge",
    ":xposed",
    ":common",
    ":common-legacy",
)

include(":lyric:view")
include(":lyric:model")
include(":lyric:model-legacy")
include(":lyric:statusbarlyric")

include(":lyric:bridge:provider")
include(":lyric:bridge:central")
include(":lyric:bridge:subscriber")

include(":opencc-lite")

rootProject.name = "lyricon"
