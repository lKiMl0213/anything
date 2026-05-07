package rpg.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

data class GameSelectOption(
    val key: String,
    val label: String
)

@Composable
fun GameDropdownSelect(
    label: String,
    options: List<GameSelectOption>,
    onSelect: (GameSelectOption) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val resolvedWidth = width ?: GameUiTokens.compactSelectWidth

    Box(
        modifier = modifier.width(resolvedWidth),
        contentAlignment = Alignment.TopCenter
    ) {
        GamePrimaryButton(
            label = "$label \u25be",
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(resolvedWidth)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
