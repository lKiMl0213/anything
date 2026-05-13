import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.2.10"
        id("org.jetbrains.kotlin.android") version "2.2.10"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
        id("com.android.application") version "9.0.1"
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "anything-rpg"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include("core")
include("app-android")








