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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.ui.components.AttributeInfoButton
import rpg.android.ui.components.AttributeInfoPopup
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
    var popupCode by remember { mutableStateOf<String?>(null) }

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
                Text("Pontos de atributos disponíveis: ${state.pointsRemaining}")
            }

            GameInfoPanel(title = "Atributos atuais") {
                state.attributes.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AttributeInfoButton(
                            label = row.label,
                            onClick = { popupCode = row.code },
                            modifier = Modifier.weight(0.82f)
                        )
                        Text(
                            text = row.finalValue.toString(),
                            modifier = Modifier.weight(0.55f),
                            textAlign = TextAlign.Center
                        )
                    }
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

    popupCode?.let { selectedCode ->
        val selected = state.attributes.firstOrNull { it.code == selectedCode }
        AttributeInfoPopup(
            title = selected?.label ?: selectedCode,
            lines = state.attributeDetailByCode[selectedCode].orEmpty(),
            onDismiss = { popupCode = null }
        )
    }
}
