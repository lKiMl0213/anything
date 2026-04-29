plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("core/src/main/kotlin", "app-cli/src/main/kotlin"))
        resources.setSrcDirs(listOf("core/src/main/resources", "app-cli/src/main/resources"))
    }
    named("test") {
        java.setSrcDirs(listOf("core/src/test/kotlin", "app-cli/src/test/kotlin"))
        resources.setSrcDirs(listOf("core/src/test/resources", "app-cli/src/test/resources"))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

val kotlinLineLimit = 300
val kotlinLineLimitBaselineFile = file("tools/kotlin-line-limit-baseline.txt")

tasks.register("checkKotlinFileLineLimit") {
    group = "verification"
    description = "Falha quando arquivos Kotlin novos passam de 300 linhas (baseline em tools/kotlin-line-limit-baseline.txt)."
    notCompatibleWithConfigurationCache("Analisa a arvore de arquivos em runtime e depende de estado dinamico do workspace.")

    doLast {
        val baseline: Set<String> = if (kotlinLineLimitBaselineFile.exists()) {
            kotlinLineLimitBaselineFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        } else {
            emptySet()
        }

        val roots = listOf(
            file("core/src/main/kotlin"),
            file("app-cli/src/main/kotlin"),
            file("app-android/src/main/kotlin")
        ).filter { it.exists() }

        val oversized = roots
            .asSequence()
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .map { file ->
                val rel = projectDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                Triple(rel, file.readLines().size, baseline.contains(rel))
            }
            .filter { (_, lines, _) -> lines > kotlinLineLimit }
            .toList()

        val violations = oversized.filter { (_, _, inBaseline) -> !inBaseline }
        if (violations.isNotEmpty()) {
            val report = violations.joinToString(separator = "\n") { (path, lines, _) ->
                "- $path ($lines linhas)"
            }
            throw GradleException(
                "Arquivos Kotlin acima de $kotlinLineLimit linhas sem baseline:\n$report\n" +
                    "Divida o arquivo por responsabilidade ou registre justificativa temporaria no baseline."
            )
        }
    }
}

application {
    mainClass.set("rpg.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.named("check") {
    dependsOn("checkKotlinFileLineLimit")
}

val appName = project.name
val portableRoot = layout.buildDirectory.dir("portable/$appName")
val launcher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.register<Sync>("prepareWindowsPortable") {
    group = "distribution"
    description = "Prepara uma build portavel para Windows com runtime Java embutido."
    dependsOn("installDist")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    into(portableRoot)
    from(layout.buildDirectory.dir("install/$appName"))
    from("data") {
        into("data")
    }
    from("tools/portable")
    from("README.md")
    from("data/saves") {
        into("data/saves")
    }
    from(launcher.map { it.metadata.installationPath.asFile }) {
        into("runtime")
    }
}

tasks.register<Zip>("packageWindowsPortable") {
    group = "distribution"
    description = "Gera zip portavel para Windows (nao precisa instalar Java no destino)."
    dependsOn("prepareWindowsPortable")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    archiveBaseName.set(appName)
    archiveClassifier.set("windows-portable")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(portableRoot)
}
