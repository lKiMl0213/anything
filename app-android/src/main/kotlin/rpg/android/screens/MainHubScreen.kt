package rpg.android.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.state.HubSkillUi
import rpg.android.state.MainHubUiModel
import rpg.android.ui.components.BottomNavItem
import rpg.android.ui.components.GameBottomNav
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GamePopup
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameScreenRoot
import rpg.android.ui.components.GameTopHud

@Composable
fun MainHubScreen(
    state: MainHubUiModel,
    hasProgressAlert: Boolean,
    isDarkTheme: Boolean,
    onExplore: () -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenProduction: () -> Unit,
    onOpenCity: () -> Unit,
    onOpenProgression: () -> Unit,
    onClearInfo: () -> Unit,
    onToggleTheme: () -> Unit,
    onExitApp: () -> Unit
) {
    var selectedSkill by remember { mutableStateOf<HubSkillUi?>(null) }
    var infoPopupOpen by remember { mutableStateOf(false) }
    var settingsPopupOpen by remember { mutableStateOf(false) }

    GameScreenRoot(
        backgroundRes = R.drawable.bg_main_hub,
        bottomNav = {
            GameBottomNav(
                items = listOf(
                    BottomNavItem(
                        key = "character",
                        label = "Personagem",
                        selected = false,
                        onClick = onOpenCharacter
                    ),
                    BottomNavItem(
                        key = "production",
                        label = "Producao",
                        selected = false,
                        onClick = onOpenProduction
                    ),
                    BottomNavItem(
                        key = "explore",
                        label = "Explorar",
                        selected = true,
                        onClick = {}
                    ),
                    BottomNavItem(
                        key = "city",
                        label = "Cidade",
                        selected = false,
                        onClick = onOpenCity
                    ),
                    BottomNavItem(
                        key = "progress",
                        label = "Progresso",
                        selected = false,
                        hasAlert = hasProgressAlert,
                        onClick = onOpenProgression
                    )
                )
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
                horizontalArrangement = Arrangement.End
            ) {
                GamePrimaryButton(
                    label = "⚙️",
                    onClick = { settingsPopupOpen = true }
                )
            }

            GameTopHud(
                name = state.name,
                raceClassLabel = state.raceClassLabel,
                levelXpLabel = state.levelXpLabel,
                currencyLabel = state.currencyLabel,
                inventoryLabel = state.inventoryCapacityLabel,
                debuffLabel = if (state.deathDebuffStacks > 0) {
                    "☠ x${state.deathDebuffStacks} (${formatMinutes(state.deathDebuffMinutes)}m)"
                } else {
                    null
                },
                hpCurrent = state.hpCurrent,
                hpMax = state.hpMax,
                mpCurrent = state.mpCurrent,
                mpMax = state.mpMax,
                onRaceClassInfoClick = { infoPopupOpen = true }
            )

            GamePanel(title = "Regeneracao") {
                Text("HP +${formatRegen(state.hpRegenPerMinute)}/min | cheio em ${formatEta(state.hpEtaSeconds)}")
                Text("MP +${formatRegen(state.mpRegenPerMinute)}/min | cheio em ${formatEta(state.mpEtaSeconds)}")
            }

            GamePanel(title = "Habilidades de producao") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.skills.chunked(3).forEach { rowSkills ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSkills.forEach { skill ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                        .clickable { selectedSkill = skill }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${skill.symbol} nv${skill.level}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            repeat((3 - rowSkills.size).coerceAtLeast(0)) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .height(220.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Placeholder personagem idle")
                }
            }

            GamePrimaryButton(
                label = "EXPLORAR",
                onClick = onExplore,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    selectedSkill?.let { skill ->
        GamePopup(
            title = skill.label,
            onDismiss = { selectedSkill = null }
        ) {
            Text(
                text = "Nivel atual: ${skill.level}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "XP: ${skill.currentXp.toInt()} / ${skill.requiredXp.toInt()}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }

    if (infoPopupOpen) {
        GamePopup(
            title = "Avisos e Logs",
            onDismiss = { infoPopupOpen = false }
        ) {
            if (state.infoLines.isEmpty()) {
                Text(
                    text = "Sem avisos no momento.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.infoLines.forEach { line ->
                        Text(
                            text = line,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            GamePrimaryButton(
                label = "Limpar",
                onClick = {
                    onClearInfo()
                    infoPopupOpen = false
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (settingsPopupOpen) {
        GamePopup(
            title = "Configuracoes",
            onDismiss = { settingsPopupOpen = false }
        ) {
            Text(
                text = "Tema atual: ${if (isDarkTheme) "Escuro" else "Claro"}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            GamePrimaryButton(
                label = "Alternar tema",
                onClick = onToggleTheme,
                modifier = Modifier.fillMaxWidth()
            )
            GamePrimaryButton(
                label = "Sair",
                onClick = {
                    settingsPopupOpen = false
                    onExitApp()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatRegen(value: Double): String = "%.1f".format(value.coerceAtLeast(0.0))

private fun formatEta(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "agora"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatMinutes(value: Double): String = "%.1f".format(value.coerceAtLeast(0.0))
