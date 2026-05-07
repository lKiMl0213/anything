package rpg.android.feedback

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rpg.android.config.AppBuildInfo
import rpg.android.ui.scale.GameUiScale

data class FeedbackReportResult(
    val fileName: String,
    val savedUri: Uri?,
    val localPath: String?
)

class FeedbackReportService(
    private val context: Context
) {
    suspend fun createReport(
        buildInfo: AppBuildInfo,
        uiScale: GameUiScale
    ): FeedbackReportResult = withContext(Dispatchers.IO) {
        val now = Date()
        val suffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val fileName = "anything_rpg_feedback_$suffix.zip"

        val entries = linkedMapOf<String, String>()
        entries["app_info.txt"] = buildAppInfo(buildInfo, uiScale)
        entries["runtime_events.txt"] = readRuntimeEvents()
        entries["latest_crash.txt"] = readLatestCrash()
        entries["logcat_recent.txt"] = readRecentLogcat()

        val zipBytes = buildZip(entries)
        saveToDownloads(fileName, zipBytes)
    }

    private fun buildAppInfo(buildInfo: AppBuildInfo, uiScale: GameUiScale): String {
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration
        val screenDp = "${config.screenWidthDp}x${config.screenHeightDp}dp"
        val screenPx = "${metrics.widthPixels}x${metrics.heightPixels}px"
        val densityLabel = "density=${"%.2f".format(metrics.density)} (${metrics.densityDpi}dpi)"
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("timestamp=$timestamp")
            appendLine("version=${buildInfo.versionName}")
            appendLine("build=${buildInfo.versionCode}")
            appendLine("ui_scale=${uiScale.name}")
            appendLine("android=${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("screen_dp=$screenDp")
            appendLine("screen_px=$screenPx")
            appendLine(densityLabel)
        }
    }

    private fun readRuntimeEvents(): String {
        val file = File(context.filesDir, "feedback_logs/runtime_events.txt")
        return if (file.exists()) file.readText() else "Sem eventos registrados nesta sessao.\n"
    }

    private fun readLatestCrash(): String {
        val file = CrashLogStore.latestCrashFile(context)
        return file?.readText() ?: "Sem crash registrado localmente.\n"
    }

    private fun readRecentLogcat(): String {
        return runCatching {
            val process = ProcessBuilder("logcat", "-d", "-t", "500").start()
            process.inputStream.bufferedReader().use { it.readText() }
        }.getOrElse { error ->
            "Falha ao coletar logcat: ${error.message ?: "erro desconhecido"}"
        }
    }

    private fun buildZip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun saveToDownloads(fileName: String, data: ByteArray): FeedbackReportResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(data)
                    stream.flush()
                }
                return FeedbackReportResult(fileName = fileName, savedUri = uri, localPath = null)
            }
        }

        val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!fallbackDir.exists()) fallbackDir.mkdirs()
        val file = File(fallbackDir, fileName)
        file.writeBytes(data)
        return FeedbackReportResult(
            fileName = fileName,
            savedUri = Uri.fromFile(file),
            localPath = file.absolutePath
        )
    }
}

