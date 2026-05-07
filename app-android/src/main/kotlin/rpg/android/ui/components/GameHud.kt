package rpg.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun StatMiniPanel(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    GamePanel(modifier = modifier) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(value)
    }
}

@Composable
fun GameStatBar(
    label: String,
    current: Double,
    max: Double,
    trailingInfo: String? = null,
    modifier: Modifier = Modifier
) {
    val clampedMax = max.coerceAtLeast(1.0)
    val progress = (current / clampedMax).coerceIn(0.0, 1.0).toFloat()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$label ${current.roundToInt()}/${clampedMax.roundToInt()}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = GameUiTokens.bodyTextSize),
                fontWeight = FontWeight.SemiBold
            )
            trailingInfo?.takeIf { it.isNotBlank() }?.let { info ->
                Text(
                    text = info,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = GameUiTokens.labelTextSize),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun GameTopHud(
    name: String,
    raceClassLabel: String,
    levelXpLabel: String,
    currencyLabel: String,
    inventoryLabel: String,
    debuffLabel: String?,
    hpCurrent: Double,
    hpMax: Double,
    mpCurrent: Double,
    mpMax: Double,
    hpRegenPerMinute: Double,
    mpRegenPerMinute: Double,
    hpEtaSeconds: Int,
    mpEtaSeconds: Int,
    onRaceClassInfoClick: (() -> Unit)? = null
) {
    GamePanel {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 410.dp
            val lineSpacing = if (compact) 3.dp else 5.dp
            val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = GameUiTokens.bodyTextSize,
                lineHeight = (GameUiTokens.bodyTextSize.value + 4f).sp
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(lineSpacing)
            ) {
                Text(
                    text = name,
                    style = bodyStyle,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = raceClassLabel,
                        style = bodyStyle,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (onRaceClassInfoClick != null) {
                        GameIconActionButton(
                            icon = "\u2699\ufe0f",
                            onClick = onRaceClassInfoClick,
                            modifier = Modifier
                                .wrapContentWidth()
                                .width(if (compact) 44.dp else 50.dp)
                                .height(if (compact) 44.dp else 50.dp),
                            size = if (compact) 44.dp else 50.dp
                        )
                    }
                }
                if (compact) {
                    Text(
                        text = levelXpLabel,
                        style = bodyStyle,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currencyLabel,
                        style = bodyStyle,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Inventario $inventoryLabel",
                        style = bodyStyle,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = debuffLabel ?: "-",
                        style = bodyStyle,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = levelXpLabel,
                            style = bodyStyle,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currencyLabel,
                            style = bodyStyle,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Inventario $inventoryLabel",
                            style = bodyStyle,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = debuffLabel ?: "-",
                            style = bodyStyle,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        GameStatBar(
            label = "HP",
            current = hpCurrent,
            max = hpMax,
            trailingInfo = regenLabel(
                current = hpCurrent,
                max = hpMax,
                regenPerMinute = hpRegenPerMinute,
                etaSeconds = hpEtaSeconds
            )
        )
        GameStatBar(
            label = "MP",
            current = mpCurrent,
            max = mpMax,
            trailingInfo = regenLabel(
                current = mpCurrent,
                max = mpMax,
                regenPerMinute = mpRegenPerMinute,
                etaSeconds = mpEtaSeconds
            )
        )
    }
}

private fun regenLabel(
    current: Double,
    max: Double,
    regenPerMinute: Double,
    etaSeconds: Int
): String {
    val regen = "+${"%.1f".format(regenPerMinute.coerceAtLeast(0.0))}/min"
    val isFull = current >= max - 0.0001
    if (isFull) return regen
    val etaSuffix = if (etaSeconds > 0) " | cheio em ${formatEtaMinutes(etaSeconds)} min" else ""
    return regen + etaSuffix
}

private fun formatEtaMinutes(totalSeconds: Int): String {
    val minutes = totalSeconds.coerceAtLeast(0).toDouble() / 60.0
    return "%.1f".format(minutes)
}
