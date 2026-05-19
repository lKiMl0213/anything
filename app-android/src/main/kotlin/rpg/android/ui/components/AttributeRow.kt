package rpg.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AttributeRow(
    code: String,
    valueLabel: String,
    allocatedLabel: String,
    onInfoClick: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GameUiTokens.panelSpacing)
    ) {
        AttributeInfoButton(
            label = code,
            onClick = onInfoClick,
            modifier = Modifier
                .weight(0.82f)
        )
        Column(
            modifier = Modifier.weight(1.05f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = valueLabel,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = allocatedLabel,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        GamePrimaryButton(
            label = "-",
            onClick = onDecrease,
            enabled = canDecrease,
            modifier = Modifier.widthIn(min = 42.dp, max = 56.dp)
        )
        GamePrimaryButton(
            label = "+",
            onClick = onIncrease,
            enabled = canIncrease,
            modifier = Modifier.widthIn(min = 42.dp, max = 56.dp)
        )
    }
}

@Composable
fun AttributeInfoButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        color = GameUiTokens.panelColor().copy(alpha = 0.58f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(GameUiTokens.buttonCorner),
        border = BorderStroke(1.dp, GameUiTokens.panelBorderColor())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AttributeInfoPopup(
    title: String,
    lines: List<String>,
    onDismiss: () -> Unit
) {
    GamePopup(
        title = title,
        onDismiss = onDismiss
    ) {
        if (lines.isEmpty()) {
            Text(
                text = "Sem detalhes adicionais.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
