package rpg.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AttributeRow(
    code: String,
    valueLabel: String,
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
        Text(
            text = code,
            modifier = Modifier
                .weight(0.8f)
                .clickable(onClick = onInfoClick)
        )
        Text(
            text = valueLabel,
            modifier = Modifier.weight(2.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
