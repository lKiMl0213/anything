package rpg.android.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import rpg.android.state.PatchNotesUiModel
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GameUiTokens

@Composable
fun PatchNotesPopupContent(
    notes: PatchNotesUiModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "v${notes.versionLabel}",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = GameUiTokens.titleTextSize
            )
        )

        notes.dateLabel?.takeIf { it.isNotBlank() }?.let { date ->
            Text(
                text = date,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = GameUiTokens.bodyTextSize
                )
            )
        }

        Text(
            text = "Resumo das mudancas",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic,
                fontSize = GameUiTokens.bodyTextSize
            )
        )

        PatchNotesSection(
            title = "Novidades",
            lines = notes.novidades,
            headerColor = MaterialTheme.colorScheme.primary
        )
        PatchNotesSection(
            title = "Melhorias",
            lines = notes.melhorias,
            headerColor = MaterialTheme.colorScheme.tertiary
        )
        PatchNotesSection(
            title = "Correcoes",
            lines = notes.correcoes,
            headerColor = MaterialTheme.colorScheme.error
        )
        PatchNotesSection(
            title = "Sistemas",
            lines = notes.sistemas,
            headerColor = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun PatchNotesSection(
    title: String,
    lines: List<String>,
    headerColor: androidx.compose.ui.graphics.Color
) {
    if (lines.isEmpty()) return

    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = headerColor.copy(alpha = 0.20f),
            border = BorderStroke(1.dp, headerColor.copy(alpha = 0.55f))
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
                color = headerColor,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = GameUiTokens.labelTextSize
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lines.forEach { rawLine ->
                Text(
                    text = toPatchNotesAnnotatedText(rawLine),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = GameUiTokens.bodyTextSize
                    )
                )
            }
        }
    }
}

private fun toPatchNotesAnnotatedText(rawLine: String): AnnotatedString {
    val normalized = rawLine.trim().removePrefix("-").trim()
    val text = if (normalized.isBlank()) "-" else normalized
    val builder = AnnotatedString.Builder()
    builder.append("• ")
    builder.appendInlineMarkdown(text)
    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.appendInlineMarkdown(source: String) {
    var index = 0
    var bold = false
    var italic = false
    while (index < source.length) {
        if (index + 1 < source.length && source[index] == '*' && source[index + 1] == '*') {
            bold = !bold
            index += 2
            continue
        }
        if (source[index] == '*') {
            italic = !italic
            index += 1
            continue
        }

        val start = index
        while (index < source.length && source[index] != '*') {
            index += 1
        }
        val chunk = source.substring(start, index)
        if (chunk.isEmpty()) continue

        if (bold || italic) {
            withStyle(
                SpanStyle(
                    fontWeight = if (bold) FontWeight.Bold else null,
                    fontStyle = if (italic) FontStyle.Italic else null
                )
            ) {
                append(chunk)
            }
        } else {
            append(chunk)
        }
    }
}

