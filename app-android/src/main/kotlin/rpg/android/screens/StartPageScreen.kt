package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.state.StartPageUiModel
import rpg.android.ui.components.GameButtonTone
import rpg.android.ui.components.GameInfoPanel
import rpg.android.ui.components.GamePopup
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameScreenRoot

@Composable
fun StartPageScreen(
    state: StartPageUiModel,
    onNewGame: () -> Unit,
    onLoad: () -> Unit,
    onLoadSelected: (String) -> Unit = {},
    onDeleteSelected: (String) -> Unit = {}
) {
    var showLoadPopup by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    GameScreenRoot(backgroundRes = R.drawable.bg_menu) {
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "/\\",
                textAlign = TextAlign.Center
            )
            GamePrimaryButton(
                label = "Novo jogo",
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
            GamePrimaryButton(
                label = "Carregar",
                onClick = {
                    onLoad()
                    showLoadPopup = true
                },
                enabled = state.canLoad,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
            state.message?.takeIf { it.isNotBlank() }?.let { message ->
                GameInfoPanel(modifier = Modifier.fillMaxWidth(0.85f)) {
                    Text(message)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Spacer(modifier = Modifier.weight(1f))
    }

    if (showLoadPopup) {
        GamePopup(
            title = "Selecionar Save",
            onDismiss = { showLoadPopup = false }
        ) {
            if (state.saves.isEmpty()) {
                Text(
                    text = "Nenhum save encontrado.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = state.saves,
                        key = { it.fileName }
                    ) { save ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GamePrimaryButton(
                                label = "${save.characterName} (${save.fileName})",
                                onClick = {
                                    showLoadPopup = false
                                    onLoadSelected(save.fileName)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            GamePrimaryButton(
                                label = "\uD83D\uDDD1",
                                onClick = { pendingDelete = save.fileName },
                                tone = GameButtonTone.ALERT
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { fileName ->
        GamePopup(
            title = "Excluir Save",
            onDismiss = { pendingDelete = null }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tem certeza que deseja excluir esse save?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GamePrimaryButton(
                        label = "Cancelar",
                        onClick = { pendingDelete = null },
                        modifier = Modifier.weight(1f)
                    )
                    GamePrimaryButton(
                        label = "Excluir",
                        onClick = {
                            onDeleteSelected(fileName)
                            pendingDelete = null
                        },
                        modifier = Modifier.weight(1f),
                        tone = GameButtonTone.ALERT
                    )
                }
            }
        }
    }
}
