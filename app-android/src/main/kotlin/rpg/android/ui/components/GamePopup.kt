package rpg.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

@Composable
fun GamePopup(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showCloseButton: Boolean = true,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
        ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = GameUiTokens.panelColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(GameUiTokens.panelCorner),
            border = androidx.compose.foundation.BorderStroke(1.dp, GameUiTokens.panelBorderColor())
        ) {
            Column(
                modifier = Modifier.padding(GameUiTokens.panelPadding),
                verticalArrangement = Arrangement.spacedBy(GameUiTokens.panelSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    if (showCloseButton) {
                        GamePrimaryButton(
                            label = "X",
                            onClick = onDismiss,
                            modifier = Modifier.width(42.dp)
                        )
                    }
                }
                content()
            }
        }
    }
}

@Composable
fun GamePopupMenu(
    title: String,
    onDismiss: () -> Unit,
    showCloseButton: Boolean = true,
    content: @Composable () -> Unit
) {
    GamePopup(
        title = title,
        onDismiss = onDismiss,
        showCloseButton = showCloseButton,
        content = content
    )
}
