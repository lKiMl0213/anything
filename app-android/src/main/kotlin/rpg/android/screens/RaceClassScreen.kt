package rpg.android.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.state.RaceClassUiModel
import rpg.android.ui.components.GameFooterActions
import rpg.android.ui.components.GameInfoPanel
import rpg.android.ui.components.GameScreenRoot

@Composable
fun RaceClassScreen(
    state: RaceClassUiModel,
    onSelectRace: (String) -> Unit,
    onSelectClass: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var raceExpanded by remember { mutableStateOf(false) }
    var classExpanded by remember { mutableStateOf(false) }
    val selectedRaceLabel = state.raceOptions.firstOrNull { it.id == state.selectedRaceId }?.label ?: "-"
    val selectedClassLabel = state.classOptions.firstOrNull { it.id == state.selectedClassId }?.label ?: "-"

    GameScreenRoot(
        backgroundRes = R.drawable.bg_new_game,
        footer = {
            GameFooterActions(
                leftLabel = "Cancelar",
                rightLabel = "Confirmar",
                onLeftClick = onCancel,
                onRightClick = onConfirm,
                rightEnabled = state.selectedRaceId != null && state.selectedClassId != null
            )
        }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GameInfoPanel(title = selectedRaceLabel) {
                state.raceSummaryLines.forEach { Text(it) }
            }
            GameInfoPanel(title = selectedClassLabel) {
                state.classSummaryLines.forEach { Text(it) }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedRaceLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Raca") },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { raceExpanded = true }
                )
                DropdownMenu(
                    expanded = raceExpanded,
                    onDismissRequest = { raceExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    state.raceOptions.forEach { race ->
                        DropdownMenuItem(
                            text = { Text(race.label) },
                            onClick = {
                                onSelectRace(race.id)
                                raceExpanded = false
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedClassLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Classe") },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { classExpanded = true }
                )
                DropdownMenu(
                    expanded = classExpanded,
                    onDismissRequest = { classExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    state.classOptions.forEach { clazz ->
                        DropdownMenuItem(
                            text = { Text(clazz.label) },
                            onClick = {
                                onSelectClass(clazz.id)
                                classExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
