package rpg.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.clickable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    val clampedMax = max.coerceAtLeast(1.0)
    val progress = (current / clampedMax).coerceIn(0.0, 1.0).toFloat()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("$label ${current.roundToInt()}/${clampedMax.roundToInt()}")
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
    onRaceClassInfoClick: (() -> Unit)? = null
) {
    GamePanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameUiTokens.panelSpacing)
        ) {
            Text(name, modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(raceClassLabel, modifier = Modifier.weight(1f))
                if (onRaceClassInfoClick != null) {
                    Text(
                        text = "(i)",
                        modifier = Modifier
                            .wrapContentWidth()
                            .clickable(onClick = onRaceClassInfoClick)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameUiTokens.panelSpacing)
        ) {
            Text(levelXpLabel, modifier = Modifier.weight(1f))
            Text(currencyLabel, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameUiTokens.panelSpacing)
        ) {
            Text("Inventario $inventoryLabel", modifier = Modifier.weight(1f))
            Text(debuffLabel ?: "-", modifier = Modifier.weight(1f))
        }
        GameStatBar(label = "HP", current = hpCurrent, max = hpMax)
        GameStatBar(label = "MP", current = mpCurrent, max = mpMax)
    }
}
