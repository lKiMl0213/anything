package rpg.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.ceil

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
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        if (trailingInfo.isNullOrBlank()) {
            Text(
                text = "$label ${current.roundToInt()}/${clampedMax.roundToInt()}",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = GameUiTokens.bodyTextSize),
                fontWeight = FontWeight.SemiBold
            )
        } else {
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
                Text(
                    text = trailingInfo,
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
    premiumStatusLabel: String,
    raceClassLabel: String,
    levelXpLabel: String,
    playerLevel: Int,
    playerXp: Int,
    playerXpMax: Int,
    currencyLabel: String,
    inventoryLabel: String,
    debuffLabel: String?,
    hpCurrent: Double,
    hpMax: Double,
    mpCurrent: Double,
    mpMax: Double,
    activeEffectName: String?,
    activeEffectRemainingSeconds: Int,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = name,
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
                Text(
                    text = raceClassLabel,
                    style = bodyStyle,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (compact) {
                    GameXpBarRow(
                        level = playerLevel,
                        currentXp = playerXp,
                        maxXp = playerXpMax,
                        compact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = currencyLabel,
                        style = bodyStyle,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Inventário $inventoryLabel | $premiumStatusLabel",
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
                        GameXpBarRow(
                            level = playerLevel,
                            currentXp = playerXp,
                            maxXp = playerXpMax,
                            compact = false,
                            modifier = Modifier.weight(1f)
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
                            text = "Inventário $inventoryLabel",
                            style = bodyStyle,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = premiumStatusLabel,
                            style = bodyStyle,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!debuffLabel.isNullOrBlank()) {
                        Text(
                            text = debuffLabel,
                            style = bodyStyle,
                            modifier = Modifier.fillMaxWidth(),
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
        GameActiveEffectLine(
            effectName = activeEffectName,
            initialRemainingSeconds = activeEffectRemainingSeconds
        )
    }
}

@Composable
private fun GameXpBarRow(
    level: Int,
    currentXp: Int,
    maxXp: Int,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Nível $level | XP",
            modifier = Modifier.weight(if (compact) 0.80f else 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = GameUiTokens.bodyTextSize),
            fontWeight = FontWeight.SemiBold
        )
        GameXpBar(
            current = currentXp,
            max = maxXp,
            modifier = Modifier
                .weight(if (compact) 1.20f else 1.28f)
                .widthIn(min = 104.dp)
        )
    }
}

@Composable
private fun GameXpBar(
    current: Int,
    max: Int,
    modifier: Modifier = Modifier
) {
    val clampedMax = max.coerceAtLeast(1)
    val clampedCurrent = current.coerceIn(0, clampedMax)
    val targetProgress = clampedCurrent.toFloat() / clampedMax.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        label = "xpProgress"
    )
    val shape = RoundedCornerShape(999.dp)

    BoxWithConstraints(
        modifier = modifier
            .height(14.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                .height(14.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.88f))
        )
        Text(
            text = "XP ($clampedCurrent/$clampedMax)",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = GameUiTokens.labelTextSize),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
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

@Composable
fun GameActiveEffectLine(
    effectName: String?,
    initialRemainingSeconds: Int
) {
    val normalizedName = effectName?.trim().orEmpty()
    if (normalizedName.isBlank()) {
        return
    }
    if (initialRemainingSeconds <= 0) {
        Text(
            text = "Efeito ativo: $normalizedName",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = GameUiTokens.labelTextSize),
            textAlign = TextAlign.Center
        )
        return
    }
    val startMillis = remember(normalizedName, initialRemainingSeconds) { System.currentTimeMillis() }
    val nowMillis by produceState(
        initialValue = System.currentTimeMillis(),
        normalizedName,
        initialRemainingSeconds
    ) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    val elapsedSeconds = ((nowMillis - startMillis) / 1000L).toInt().coerceAtLeast(0)
    val remainingSeconds = (initialRemainingSeconds - elapsedSeconds).coerceAtLeast(0)
    if (remainingSeconds <= 0) {
        return
    }
    val remainingTurns = ceil(remainingSeconds / 5.0).toInt().coerceAtLeast(1)
    Text(
        text = "Efeito ativo: $normalizedName ($remainingTurns turnos)",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = GameUiTokens.labelTextSize),
        textAlign = TextAlign.Center
    )
}

