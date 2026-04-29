import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.named("main") {
        manifest.srcFile("src/main/AndroidManifest.xml")
        java.srcDirs("src/main/kotlin")
        assets.srcDir("../data")
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
