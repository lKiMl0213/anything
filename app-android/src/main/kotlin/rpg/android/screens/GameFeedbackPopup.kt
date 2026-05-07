package rpg.android.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rpg.android.config.AppBuildInfo
import rpg.android.config.BetaConfig
import rpg.android.feedback.CrashLogStore
import rpg.android.feedback.FeedbackReportResult
import rpg.android.feedback.FeedbackReportService
import rpg.android.ui.components.GamePopup
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.scale.GameUiScale

@Composable
internal fun FeedbackPopup(
    buildInfo: AppBuildInfo,
    uiScale: GameUiScale,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val reportService = remember(context) { FeedbackReportService(context) }

    var generating by remember { mutableStateOf(false) }
    var latestReport by remember { mutableStateOf<FeedbackReportResult?>(null) }

    val baseInfo = remember(buildInfo, uiScale) { feedbackInfoText(context, buildInfo, uiScale) }

    GamePopup(
        title = "Feedback / Reportar bug",
        onDismiss = onDismiss,
        showCloseButton = false,
        modifier = Modifier.fillMaxWidth(0.96f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 470.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Este jogo esta em beta. Sistemas, interface, balanceamento, imagens e textos ainda podem mudar.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Ao enviar feedback inclua: versao, modelo, Android, o que voce estava fazendo e o que aconteceu.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = buildInfo.betaLabel,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            GamePrimaryButton(
                label = "Copiar infos tecnicas",
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipData
                                .newPlainText("anything_rpg_feedback_info", baseInfo)
                                .toClipEntry()
                        )
                        Toast.makeText(context, "Informacoes copiadas.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(0.88f)
            )
            GamePrimaryButton(
                label = if (generating) "Gerando pacote..." else "Gerar ZIP de logs",
                onClick = {
                    if (generating) return@GamePrimaryButton
                    generating = true
                    scope.launch {
                        val result = runCatching {
                            reportService.createReport(buildInfo = buildInfo, uiScale = uiScale)
                        }
                        generating = false
                        latestReport = result.getOrNull()
                        if (result.isSuccess) {
                            val message = latestReport?.savedUri?.toString()
                                ?: latestReport?.localPath
                                ?: "Downloads"
                            Toast.makeText(context, "ZIP salvo: $message", Toast.LENGTH_LONG).show()
                            CrashLogStore.writeEvent(context, "Pacote de feedback gerado: ${latestReport?.fileName}")
                        } else {
                            Toast.makeText(context, "Falha ao gerar ZIP de logs.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(0.88f)
            )
            if (generating) {
                CircularProgressIndicator()
            }
            Text(
                text = "Clique aqui para enviar",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (generating) return@clickable
                        generating = true
                        scope.launch {
                            val result = runCatching {
                                reportService.createReport(buildInfo = buildInfo, uiScale = uiScale)
                            }
                            generating = false
                            latestReport = result.getOrNull() ?: latestReport
                            if (result.isSuccess) {
                                val message = latestReport?.savedUri?.toString()
                                    ?: latestReport?.localPath
                                    ?: "Downloads"
                                Toast.makeText(context, "ZIP salvo: $message", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Nao foi possivel gerar o ZIP automaticamente.", Toast.LENGTH_LONG).show()
                            }
                            openFeedbackEmail(context, buildInfo, baseInfo, latestReport)
                        }
                    },
                textAlign = TextAlign.Center
            )
            latestReport?.let { report ->
                Text(
                    text = "Ultimo pacote: ${report.fileName}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                GamePrimaryButton(
                    label = "Fechar",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(0.58f)
                )
            }
        }
    }
}

private fun feedbackInfoText(
    context: android.content.Context,
    buildInfo: AppBuildInfo,
    uiScale: GameUiScale
): String {
    val metrics = context.resources.displayMetrics
    val config = context.resources.configuration
    return buildString {
        appendLine("versao=${buildInfo.versionName}")
        appendLine("build=${buildInfo.versionCode}")
        appendLine("android=${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
        appendLine("modelo=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("tela_dp=${config.screenWidthDp}x${config.screenHeightDp}")
        appendLine("tela_px=${metrics.widthPixels}x${metrics.heightPixels}")
        appendLine("ui_scale=${uiScale.name}")
    }
}

private fun openFeedbackEmail(
    context: android.content.Context,
    buildInfo: AppBuildInfo,
    baseInfo: String,
    latestReport: FeedbackReportResult?
) {
    val reportHint = latestReport?.let {
        "\nZIP gerado: ${it.fileName}\nLocal: ${it.savedUri ?: it.localPath ?: "-"}\n"
    } ?: ""
    val body = buildString {
        appendLine("Este jogo esta em beta. Segue meu feedback:")
        appendLine()
        appendLine("- O que eu estava fazendo:")
        appendLine("- O que aconteceu:")
        appendLine("- O que eu esperava:")
        append(reportHint)
        appendLine()
        appendLine("Informacoes tecnicas:")
        append(baseInfo)
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:${BetaConfig.feedbackEmail}".toUri()
        putExtra(Intent.EXTRA_SUBJECT, "[Feedback Beta] Anything RPG ${buildInfo.shortLabel}")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum app de e-mail encontrado.", Toast.LENGTH_LONG).show()
    }
}
