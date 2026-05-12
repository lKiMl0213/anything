package rpg.android.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import rpg.android.state.MainSection
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.BottomNavItem
import rpg.android.ui.components.GameDropdownSelect
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameSelectOption
import rpg.android.ui.components.GameUiTokens
import rpg.application.actions.GameAction
import rpg.application.progression.QuestSection
import rpg.presentation.model.PlayerSummaryViewModel
import rpg.presentation.model.ScreenOptionViewModel

@Composable
internal fun GenericMenuHeaderPanel(
    title: String,
    subtitle: String?,
    isGlobalBossContext: Boolean,
    showPlayerSummary: Boolean,
    summary: PlayerSummaryViewModel?,
    bodyLines: List<String>,
    isExplorationAreasContext: Boolean,
    section: MainSection,
    sectionMessages: List<String>,
    goldErrorPulse: Int = 0
) {
    var goldErrorActive by remember { mutableStateOf(false) }
    val goldShakeOffsetPx = remember { Animatable(0f) }
    val goldTextColor by animateColorAsState(
        targetValue = if (goldErrorActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        label = "goldTextColor"
    )
    LaunchedEffect(goldErrorPulse) {
        if (goldErrorPulse <= 0) return@LaunchedEffect
        goldErrorActive = true
        val sequence = listOf(14f, -14f, 11f, -11f, 7f, -7f, 3f, -3f, 0f)
        sequence.forEach { value ->
            goldShakeOffsetPx.animateTo(value, animationSpec = tween(durationMillis = 45))
        }
        delay(650)
        goldErrorActive = false
    }

    GamePanel(title = if (isGlobalBossContext) null else title) {
        if (isGlobalBossContext) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        subtitle?.takeIf { it.isNotBlank() }?.let { line ->
            Text(
                text = line,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        if (showPlayerSummary) {
            summary?.let { header ->
                Text(
                    text = "${header.name} | Nv ${header.level}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = header.classLabel,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "HP ${header.hp.current.toInt()}/${header.hp.max.toInt()} | MP ${header.mp.current.toInt()}/${header.mp.max.toInt()}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Ouro ${header.gold}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = goldShakeOffsetPx.value },
                    color = goldTextColor,
                    textAlign = TextAlign.Center
                )
            }
        }
        bodyLines.forEach { line ->
            Text(
                text = line,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (isExplorationAreasContext || section == MainSection.PRODUCTION) {
                    TextAlign.Center
                } else {
                    TextAlign.Start
                }
            )
        }
        if (section != MainSection.PRODUCTION) {
            sectionMessages.forEach { line ->
                Text(
                    text = line,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
internal fun ShopFilterPanel(
    filterOptions: List<ScreenOptionViewModel>,
    onAction: (GameAction) -> Unit
) {
    if (filterOptions.isEmpty()) return
    val selected = filterOptions.firstOrNull { it.label.contains("(ativo)", ignoreCase = true) } ?: filterOptions.first()

    GamePanel(title = "Filtrar") {
        GameDropdownSelect(
            label = "Filtrar: ${shopFilterLabel(selected.label)}",
            options = filterOptions.map { option ->
                GameSelectOption(
                    key = option.key,
                    label = shopFilterLabel(option.label)
                )
            },
            onSelect = { selectedOption ->
                filterOptions.firstOrNull { it.key == selectedOption.key }?.let { option ->
                    onAction(option.action)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            width = GameUiTokens.buttonMaxWidth
        )
    }
}

internal fun optionShouldAlert(option: ScreenOptionViewModel, isQuestContext: Boolean): Boolean {
    if (!isQuestContext) {
        return optionHasAlert(option.label)
    }
    return when (val action = option.action) {
        is GameAction.OpenQuestSection -> {
            action.section != QuestSection.ACCEPTABLE_POOL && optionHasQuestAlert(option.label)
        }

        is GameAction.ClaimQuest -> true
        else -> optionHasQuestAlert(option.label)
    }
}

@Composable
internal fun ExplorationAreaActionCard(
    label: String,
    onClick: () -> Unit
) {
    val preview = explorationAreaPreview(label)
    GamePanel(
        modifier = Modifier
            .fillMaxWidth(0.88f),
        title = preview.title
    ) {
        preview.description?.let { description ->
            Text(
                text = description,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        GamePrimaryButton(
            label = "Entrar",
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth(0.52f)
                .align(Alignment.CenterHorizontally)
        )
    }
}

private fun shopFilterLabel(raw: String): String {
    return raw
        .replace("Filtro:", "", ignoreCase = true)
        .replace("(ativo)", "", ignoreCase = true)
        .trim()
}

internal fun defaultBottomNavItems(
    section: MainSection,
    hasProgressAlert: Boolean,
    onOpenCharacter: () -> Unit,
    onOpenProduction: () -> Unit,
    onOpenHub: () -> Unit,
    onOpenCity: () -> Unit,
    onOpenProgression: () -> Unit
): List<BottomNavItem> {
    return listOf(
        BottomNavItem(
            key = "character",
            label = "Personagem",
            icon = "\uD83E\uDDD9",
            selected = section == MainSection.CHARACTER,
            onClick = onOpenCharacter
        ),
        BottomNavItem(
            key = "production",
            label = "Producao",
            icon = "\u2692",
            selected = section == MainSection.PRODUCTION,
            onClick = onOpenProduction
        ),
        BottomNavItem(
            key = "explore",
            label = "Explorar",
            icon = "\uD83E\uDDED",
            selected = section == MainSection.EXPLORATION,
            onClick = onOpenHub
        ),
        BottomNavItem(
            key = "city",
            label = "Cidade",
            icon = "\uD83C\uDFD9",
            selected = section == MainSection.CITY,
            onClick = onOpenCity
        ),
        BottomNavItem(
            key = "progress",
            label = "Progresso",
            icon = "\uD83D\uDCC8",
            selected = section == MainSection.PROGRESSION,
            hasAlert = hasProgressAlert,
            onClick = onOpenProgression
        )
    )
}
