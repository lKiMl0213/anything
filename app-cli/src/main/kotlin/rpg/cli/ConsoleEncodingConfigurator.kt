package rpg.cli

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object ConsoleEncodingConfigurator {
    fun apply() {
        if (!isWindows()) return
        val originalCodePage = detectWindowsCodePage()
        val switchedToUtf8 = switchCodePage(65001)
        val activeCodePage = detectWindowsCodePage() ?: originalCodePage ?: return
        val charset = resolveCharset(activeCodePage) ?: return
        runCatching {
            System.setProperty("stdin.encoding", charset.name())
            System.setProperty("stdout.encoding", charset.name())
            System.setProperty("sun.stdout.encoding", charset.name())
            System.setProperty("stderr.encoding", charset.name())
            System.setProperty("sun.stderr.encoding", charset.name())
            System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, charset))
            System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, charset))
        }
        if (switchedToUtf8 && originalCodePage != null && originalCodePage != 65001) {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    switchCodePage(originalCodePage)
                }
            )
        }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name")?.lowercase()?.contains("win") == true
    }

    private fun detectWindowsCodePage(): Int? {
        return runCatching {
            val process = ProcessBuilder("cmd", "/c", "chcp")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(1, TimeUnit.SECONDS)
            Regex("""(\d{3,5})""").find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.getOrNull()
    }

    private fun switchCodePage(codePage: Int): Boolean {
        return runCatching {
            val process = ProcessBuilder("cmd", "/c", "chcp $codePage > nul")
                .redirectErrorStream(true)
                .start()
            process.waitFor(1, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrElse { false }
    }

    private fun resolveCharset(codePage: Int): Charset? {
        if (codePage == 65001) return Charsets.UTF_8
        return runCatching { Charset.forName("CP$codePage") }.getOrNull()
    }
}
