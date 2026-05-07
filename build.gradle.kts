import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Exec
import java.nio.file.Paths

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Mantem classes AGP visiveis para o Kotlin Gradle plugin em subprojetos Android.
        classpath("com.android.tools.build:gradle:9.0.1")
    }
}

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
    testImplementation(kotlin("test"))
}

abstract class CheckKotlinFileLineLimitTask : DefaultTask() {
    @get:Input
    abstract val lineLimit: Property<Int>

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @TaskAction
    fun validateLineLimit() {
        val baseline: Set<String> = baselineFile.orNull
            ?.asFile
            ?.takeIf { it.exists() }
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?.toSet()
            ?: emptySet()

        val projectRoot = Paths.get(projectRootPath.get())
        val oversized = sourceRoots.files
            .asSequence()
            .filter { it.exists() }
            .flatMap { root ->
                root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            }
            .map { file ->
                val rel = projectRoot.relativize(file.toPath()).toString().replace('\\', '/')
                val lines = file.useLines { seq -> seq.count() }
                Triple(rel, lines, baseline.contains(rel))
            }
            .filter { (_, lines, _) -> lines > lineLimit.get() }
            .toList()

        val violations = oversized.filter { (_, _, inBaseline) -> !inBaseline }
        if (violations.isNotEmpty()) {
            val report = violations.joinToString(separator = "\n") { (path, lines, _) ->
                "- $path ($lines linhas)"
            }
            throw GradleException(
                "Arquivos Kotlin acima de ${lineLimit.get()} linhas sem baseline:\n$report\n" +
                    "Divida o arquivo por responsabilidade ou registre justificativa temporaria no baseline."
            )
        }
    }
}

val kotlinLineLimit = 300
tasks.register<CheckKotlinFileLineLimitTask>("checkKotlinFileLineLimit") {
    group = "verification"
    description = "Falha quando arquivos Kotlin novos passam de 300 linhas (baseline em tools/kotlin-line-limit-baseline.txt)."
    lineLimit.set(kotlinLineLimit)
    projectRootPath.set(layout.projectDirectory.asFile.absolutePath)
    baselineFile.set(layout.projectDirectory.file("tools/kotlin-line-limit-baseline.txt"))
    sourceRoots.from(
        layout.projectDirectory.dir("core/src/main/kotlin"),
        layout.projectDirectory.dir("app-cli/src/main/kotlin"),
        layout.projectDirectory.dir("app-android/src/main/kotlin")
    )
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

tasks.register<Exec>("updatePatchNotes") {
    group = "release"
    description = "Atualiza data/patchnotes/changelog.json com um resumo amigavel das mudancas recentes."
    workingDir = projectDir
    val script = layout.projectDirectory.file("tools/patchnotes/update_patchnotes.ps1").asFile.absolutePath
    val versionArg = (findProperty("patchVersion") as String?)?.trim().orEmpty()
    val sinceRefArg = (findProperty("patchSinceRef") as String?)?.trim().orEmpty()
    val includeWorkingTree = (findProperty("patchIncludeWorkingTree") as String?)?.toBoolean() == true

    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script)
    } else {
        commandLine("pwsh", "-NoProfile", "-File", script)
    }

    if (versionArg.isNotBlank()) {
        args("-Version", versionArg)
    }
    if (sinceRefArg.isNotBlank()) {
        args("-SinceRef", sinceRefArg)
    }
    if (includeWorkingTree) {
        args("-IncludeWorkingTree")
    }
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
