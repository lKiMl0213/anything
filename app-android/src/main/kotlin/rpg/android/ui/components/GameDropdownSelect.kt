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
import rpg.android.audio.LocalGameAudioController
import rpg.android.audio.SoundEffect

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
    width: Dp? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val audioController = LocalGameAudioController.current
    val resolvedWidth = width ?: GameUiTokens.compactSelectWidth

    Box(
        modifier = modifier.width(resolvedWidth),
        contentAlignment = Alignment.TopCenter
    ) {
        GamePrimaryButton(
            label = "$label \u25be",
            onClick = { if (enabled) expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        DropdownMenu(
            expanded = expanded && enabled,
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
                        audioController.play(SoundEffect.CONFIRM)
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
