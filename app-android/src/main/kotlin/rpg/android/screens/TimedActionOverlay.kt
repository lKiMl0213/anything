package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.state.TimedActionUiState
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GamePopup

@Composable
fun TimedActionOverlay(
    state: TimedActionUiState,
    onCancel: () -> Unit
) {
    GamePopup(
        title = state.title,
        onDismiss = onCancel,
        showCloseButton = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(state.detail, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("Tempo restante: ${state.remainingSeconds}s", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            GamePrimaryButton(
                label = "Cancelar ação",
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}



