package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.ui.components.GameBackIconButton
import rpg.android.state.NewGameUiModel
import rpg.android.ui.components.GameFooterActions
import rpg.android.ui.components.GameInfoPanel
import rpg.android.ui.components.GameOutlinedTextField
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameScreenRoot

@Composable
fun NewGameScreen(
    state: NewGameUiModel,
    onNameChange: (String) -> Unit,
    onOpenRaceClass: () -> Unit,
    onOpenAttributes: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    GameScreenRoot(
        backgroundRes = R.drawable.bg_new_game,
        footer = {
            GameFooterActions(
                rightLabel = "Confirmar Criacao",
                onRightClick = onConfirm,
                rightEnabled = state.canConfirm
            )
        }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameBackIconButton(onClick = onCancel)
            }

            GameInfoPanel(title = "=== Criacao de Personagem ===") {
                Text("Nome: ${state.name.ifBlank { "-" }}")
                Text("Raca: ${state.selectedRaceName}")
                Text("Classe: ${state.selectedClassName}")
                Text("Pontos de atributo restantes: ${state.pointsRemaining}")
            }

            GameInfoPanel(title = "Atributos atuais") {
                state.attributes.forEach { row ->
                    Text("${row.code} (${row.label}) = ${row.finalValue}")
                }
            }

            GameOutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            GamePrimaryButton(
                label = "Racas e Classes",
                onClick = onOpenRaceClass,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .align(Alignment.CenterHorizontally)
            )
            GamePrimaryButton(
                label = "Distribuir atributos",
                onClick = onOpenAttributes,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .align(Alignment.CenterHorizontally)
            )

            state.message?.takeIf { it.isNotBlank() }?.let { message ->
                GameInfoPanel {
                    Text(message, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}
