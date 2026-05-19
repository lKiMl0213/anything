package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.state.RaceClassUiModel
import rpg.android.ui.components.CharacterSpriteImage
import rpg.android.ui.components.GameBackIconButton
import rpg.android.ui.components.GameDropdownSelect
import rpg.android.ui.components.GameFooterActions
import rpg.android.ui.components.GameInfoPanel
import rpg.android.ui.components.GameSelectOption
import rpg.android.ui.components.GameScreenRoot
import rpg.android.ui.components.GameUiTokens

@Composable
fun RaceClassScreen(
    state: RaceClassUiModel,
    onSelectRace: (String) -> Unit,
    onSelectClass: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val selectedRaceLabel = state.raceOptions.firstOrNull { it.id == state.selectedRaceId }?.label ?: "-"
    val selectedClassLabel = state.classOptions.firstOrNull { it.id == state.selectedClassId }?.label ?: "-"
    val canSelectClass = state.selectedRaceId != null

    GameScreenRoot(
        backgroundRes = R.drawable.bg_new_game,
        footer = {
            GameFooterActions(
                rightLabel = "Confirmar",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameBackIconButton(onClick = onCancel)
            }

            GameInfoPanel(title = selectedRaceLabel) {
                state.raceSummaryLines.forEach { line ->
                    Text(
                        text = line,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = GameUiTokens.titleTextSize
                        )
                    )
                }
            }
            GameInfoPanel(title = selectedClassLabel) {
                state.classSummaryLines.forEach { line ->
                    Text(
                        text = line,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = GameUiTokens.titleTextSize
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameDropdownSelect(
                    label = "Raças",
                    options = state.raceOptions.map { GameSelectOption(it.id, it.label) },
                    onSelect = { option -> onSelectRace(option.key) }
                )
                GameDropdownSelect(
                    label = "Classes",
                    options = state.classOptions.map { GameSelectOption(it.id, it.label) },
                    onSelect = { option -> onSelectClass(option.key) },
                    enabled = canSelectClass
                )
            }

            if (state.spriteAssetPath.isNotBlank()) {
                CharacterSpriteImage(
                    assetPath = state.spriteAssetPath,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            GameInfoPanel(title = "Seleção atual") {
                Text(
                    text = "Selecionado: $selectedRaceLabel | $selectedClassLabel",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

