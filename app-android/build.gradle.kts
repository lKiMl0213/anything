import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<ApplicationExtension>("android") {
    namespace = "rpg.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "rpg.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.named("main") {
        manifest.srcFile("src/main/AndroidManifest.xml")
        kotlin.directories.add("src/main/kotlin")
        assets.directories.add("../data")
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    val composeVersion = "1.8.2"
    val composeMaterial3Version = "1.3.2"
    val lifecycleVersion = "2.8.7"

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.foundation:foundation:$composeVersion")
    implementation("androidx.compose.material3:material3:$composeMaterial3Version")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")

    implementation(project(":"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

tasks.matching { task ->
    task.name.startsWith("lint", ignoreCase = true) ||
        task.name.contains("Lint", ignoreCase = true)
}.configureEach {
    enabled = false
}

val validateNoBundledSaves by tasks.registering {
    group = "verification"
    description = "Impede que saves/autosaves sejam empacotados como assets do APK."
    val savesDir = file("../data/saves")
    val rootDir = projectDir
    doLast {
        if (!savesDir.exists()) return@doLast
        val bundledSaveFiles = savesDir
            .walkTopDown()
            .filter { it.isFile }
            .filter { it.extension.equals("json", ignoreCase = true) }
            .toList()
        if (bundledSaveFiles.isNotEmpty()) {
            val details = bundledSaveFiles.joinToString("\n") { "- ${it.relativeTo(rootDir).path}" }
            throw GradleException(
                "Remova saves de data/saves antes do build Android.\nArquivos encontrados:\n$details"
            )
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(validateNoBundledSaves)
}





