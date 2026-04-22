plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
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
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

application {
    mainClass.set("rpg.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

val appName = project.name
val portableRoot = layout.buildDirectory.dir("portable/$appName")
val launcher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.register<Sync>("prepareWindowsPortable") {
    group = "distribution"
    description = "Prepara uma build portavel para Windows com runtime Java embutido."
    dependsOn("installDist")

    into(portableRoot)
    from(layout.buildDirectory.dir("install/$appName"))
    from("data") {
        into("data")
    }
    from("README.md")
    if (file("data/saves").exists()) {
        from("data/saves") {
            into("data/saves")
        }
    }
    from(launcher.map { it.metadata.installationPath.asFile }) {
        into("runtime")
    }

    doLast {
        val output = portableRoot.get().asFile
        val ps1 = output.resolve("run-anything.ps1")
        ps1.writeText(
            """
            |${'$'}ErrorActionPreference = "Stop"
            |${'$'}scriptDir = Split-Path -Parent ${'$'}MyInvocation.MyCommand.Path
            |Set-Location ${'$'}scriptDir
            |
            |${'$'}javaExe = Join-Path ${'$'}scriptDir "runtime\bin\java.exe"
            |if (-not (Test-Path ${'$'}javaExe)) {
            |    Write-Host "Runtime Java nao encontrado: ${'$'}javaExe"
            |    exit 1
            |}
            |
            |${'$'}classPath = Join-Path ${'$'}scriptDir "lib\*"
            |& ${'$'}javaExe "-Dfile.encoding=UTF-8" "-cp" ${'$'}classPath "rpg.MainKt"
            |exit ${'$'}LASTEXITCODE
            |
            """.trimMargin(),
            Charsets.UTF_8
        )

        val cmd = output.resolve("run-anything.cmd")
        cmd.writeText(
            (
                """
                |@echo off
                |setlocal
                |set "SCRIPT_DIR=%~dp0"
                |cd /d "%SCRIPT_DIR%"
                |
                |set "JAVA_EXE=%SCRIPT_DIR%runtime\bin\java.exe"
                |if not exist "%JAVA_EXE%" (
                |  echo Runtime Java nao encontrado: "%JAVA_EXE%"
                |  exit /b 1
                |)
                |
                |"%JAVA_EXE%" -Dfile.encoding=UTF-8 -cp "%SCRIPT_DIR%lib\*" rpg.MainKt
                |exit /b %ERRORLEVEL%
                |
                """.trimMargin()
            ).replace("\n", "\r\n"),
            Charsets.UTF_8
        )
    }
}

tasks.register<Zip>("packageWindowsPortable") {
    group = "distribution"
    description = "Gera zip portavel para Windows (nao precisa instalar Java no destino)."
    dependsOn("prepareWindowsPortable")

    archiveBaseName.set(appName)
    archiveClassifier.set("windows-portable")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(portableRoot)
}
