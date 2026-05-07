package rpg.android.feedback

import android.content.Context
import android.os.Build
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

object CrashLogStore {
    private const val LOG_DIR = "feedback_logs"
    private const val CRASH_FILE = "last_crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                writeCrash(context, thread.name, throwable)
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun latestCrashFile(context: Context): File? {
        val file = File(logDirectory(context), CRASH_FILE)
        return if (file.exists()) file else null
    }

    fun writeEvent(context: Context, message: String) {
        val file = File(logDirectory(context), "runtime_events.txt")
        file.parentFile?.mkdirs()
        file.appendText("${timestamp()} | $message\n")
    }

    private fun writeCrash(context: Context, threadName: String, throwable: Throwable) {
        val file = File(logDirectory(context), CRASH_FILE)
        file.parentFile?.mkdirs()
        val header = buildString {
            appendLine("timestamp=${timestamp()}")
            appendLine("thread=$threadName")
            appendLine("android=${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
        }
        file.writeText(header + throwable.stackTraceToString())
    }

    private fun logDirectory(context: Context): File {
        return File(context.filesDir, LOG_DIR)
    }

    private fun timestamp(): String {
        return if (Build.VERSION.SDK_INT >= 26) {
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(java.util.Date())
        }
    }
}

