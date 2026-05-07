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
import rpg.android.ui.components.GameIconActionButton
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GamePopup
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameScreenRoot
import rpg.android.ui.components.GameTopHud
import rpg.android.ui.components.GameUiTokens
@Composable
fun MainHubScreen(
    state: MainHubUiModel,
    hasProgressAlert: Boolean,
    versionLabel: String,
    onExplore: () -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenProduction: () -> Unit,
    onOpenCity: () -> Unit,
    onOpenProgression: () -> Unit,
    onOpenGlobalBoss: () -> Unit
) {
    var selectedSkill by remember { mutableStateOf<HubSkillUi?>(null) }
    GameScreenRoot(
        backgroundRes = R.drawable.bg_main_hub,
        bottomNav = {
            GameBottomNav(
                items = listOf(
                    BottomNavItem(
                        key = "character",
                        label = "Personagem",
                        icon = "\uD83E\uDDD9",
                        selected = false,
                        onClick = onOpenCharacter
                    ),
                    BottomNavItem(
                        key = "production",
                        label = "Producao",
                        icon = "\u2692",
                        selected = false,
                        onClick = onOpenProduction
                    ),
                    BottomNavItem(
                        key = "explore",
                        label = "Explorar",
                        icon = "\uD83E\uDDED",
                        selected = true,
                        onClick = {}
                    ),
                    BottomNavItem(
                        key = "city",
                        label = "Cidade",
                        icon = "\uD83C\uDFD9",
                        selected = false,
                        onClick = onOpenCity
                    ),
                    BottomNavItem(
                        key = "progress",
                        label = "Progresso",
                        icon = "\uD83D\uDCC8",
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
                hpRegenPerMinute = state.hpRegenPerMinute,
                mpRegenPerMinute = state.mpRegenPerMinute,
                hpEtaSeconds = state.hpEtaSeconds,
                mpEtaSeconds = state.mpEtaSeconds,
                onRaceClassInfoClick = null
            )
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
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .height(190.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Placeholder personagem idle")
                    }
                }
                GameIconActionButton(
                    icon = "\uD83D\uDC79",
                    onClick = onOpenGlobalBoss,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp),
                    size = 54.dp
                )
            }
            GamePrimaryButton(
                label = "EXPLORAR",
                onClick = onExplore,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = versionLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = GameUiTokens.screenPadding),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = GameUiTokens.labelTextSize)
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
}
private fun formatMinutes(value: Double): String = "%.1f".format(value.coerceAtLeast(0.0))
