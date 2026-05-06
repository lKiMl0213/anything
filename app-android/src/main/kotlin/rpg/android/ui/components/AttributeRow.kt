package rpg.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
                .weight(1f)
                .clickable(onClick = onInfoClick)
        )
        Text(valueLabel, modifier = Modifier.weight(1f))
        GamePrimaryButton(
            label = "-",
            onClick = onDecrease,
            enabled = canDecrease
        )
        GamePrimaryButton(
            label = "+",
            onClick = onIncrease,
            enabled = canIncrease
        )
    }
}
