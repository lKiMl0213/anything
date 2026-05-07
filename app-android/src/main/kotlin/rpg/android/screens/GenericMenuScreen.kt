package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.state.MainSection
import rpg.android.state.MenuActionPreviewUiModel
import rpg.android.state.TalentTreeGraphUiModel
import rpg.android.ui.components.GameBackIconButton
import rpg.android.ui.components.GameButtonDensity
import rpg.android.ui.components.GameBottomNav
import rpg.android.ui.components.GameButtonTone
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameScreenRoot
import rpg.application.actions.GameAction
import rpg.presentation.model.MenuScreenViewModel

@Composable
fun GenericMenuScreen(
    viewModel: MenuScreenViewModel,
    section: MainSection,
    actionPreviews: Map<String, MenuActionPreviewUiModel>,
    talentTreeGraph: TalentTreeGraphUiModel?,
    hasProgressAlert: Boolean,
    onAction: (GameAction) -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenProduction: () -> Unit,
    onOpenHub: () -> Unit,
    onOpenCity: () -> Unit,
    onOpenProgression: () -> Unit
) {
    var pendingPreview by remember { mutableStateOf<MenuActionPreviewUiModel?>(null) }
    val background = when (section) {
        MainSection.CHARACTER -> R.drawable.bg_character
        MainSection.PRODUCTION -> R.drawable.bg_production
        MainSection.EXPLORATION -> R.drawable.bg_main_hub
        MainSection.CITY -> R.drawable.bg_city
        MainSection.PROGRESSION -> R.drawable.bg_progression
    }
    val rawOptions = visibleOptions(viewModel.options)
    val backOption = rawOptions.firstOrNull { it.action is GameAction.Back }
    val allActionOptions = rawOptions.filterNot { it.action is GameAction.Back }
    val shopFilterOptions = allActionOptions.filter { it.action is GameAction.SetShopWeaponClass }
    val options = allActionOptions.filterNot { it.action is GameAction.SetShopWeaponClass }
    val hasTalentStageOptions = options.any { it.action is GameAction.OpenTalentStage }
    val hasTalentNodeOptions = options.any { it.action is GameAction.InspectTalentNode }
    val isTalentContext = hasTalentStageOptions || hasTalentNodeOptions || viewModel.title.contains("talento", ignoreCase = true)
    val isUpgradeContext = viewModel.title.contains("aprimoramentos", ignoreCase = true)
    val isStatisticsContext = viewModel.title.contains("estatisticas", ignoreCase = true)
    val isQuestContext = isQuestContext(viewModel.title, rawOptions)
    val isAchievementContext = isAchievementContext(viewModel.title, rawOptions)
    val isExplorationAreasContext = isExplorationAreasContext(viewModel.title, rawOptions)
    val isGlobalBossContext = isGlobalBossContext(viewModel.title, rawOptions)
    val useCompactActionGrid =
        section == MainSection.PRODUCTION ||
            section == MainSection.CITY ||
            isQuestContext ||
            isAchievementContext
    val showPlayerSummary =
        section != MainSection.PRODUCTION &&
            section != MainSection.PROGRESSION &&
            !isGlobalBossContext &&
            !isTalentContext &&
            !isExplorationAreasContext
    val bodyLines = when {
        isTalentContext -> compactTalentSummary(viewModel.bodyLines)
        isUpgradeContext -> compactUpgradeSummary(viewModel.bodyLines)
        isStatisticsContext -> compactStatisticsSummary(viewModel.bodyLines)
        isExplorationAreasContext -> listOf(
            "Escolha uma area para iniciar a exploracao!",
            "Cada area possui um ecossistema proprio!"
        )
        section == MainSection.PRODUCTION -> compactProductionSummary(viewModel.bodyLines)
        else -> viewModel.bodyLines
    }
    val productionLogLines = if (section == MainSection.PRODUCTION) {
        viewModel.messages
            .filterNot { it.contains("skill", ignoreCase = true) }
            .takeLast(6)
    } else {
        emptyList()
    }
    val sectionMessages = when {
        isGlobalBossContext -> emptyList()
        isExplorationAreasContext -> emptyList()
        section == MainSection.EXPLORATION -> viewModel.messages.takeLast(4)
        section == MainSection.CITY -> cityRelevantMessages(viewModel.messages)
        else -> viewModel.messages
    }
    val displayTitle = if (section == MainSection.CITY) {
        viewModel.title.replace("Aprimoramentos", "Melhorias", ignoreCase = true)
    } else {
        viewModel.title
    }

    GameScreenRoot(
        backgroundRes = background,
        bottomNav = {
            GameBottomNav(
                items = defaultBottomNavItems(
                    section = section,
                    hasProgressAlert = hasProgressAlert,
                    onOpenCharacter = onOpenCharacter,
                    onOpenProduction = onOpenProduction,
                    onOpenHub = onOpenHub,
                    onOpenCity = onOpenCity,
                    onOpenProgression = onOpenProgression
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            backOption?.let { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    GameBackIconButton(onClick = { onAction(option.action) })
                }
            }

            GenericMenuHeaderPanel(
                title = displayTitle,
                subtitle = viewModel.subtitle,
                isGlobalBossContext = isGlobalBossContext,
                showPlayerSummary = showPlayerSummary,
                summary = viewModel.summary,
                bodyLines = bodyLines,
                isExplorationAreasContext = isExplorationAreasContext,
                section = section,
                sectionMessages = sectionMessages
            )

            if (shopFilterOptions.isNotEmpty()) {
                ShopFilterPanel(
                    filterOptions = shopFilterOptions,
                    onAction = onAction
                )
            }

            GamePanel(
                modifier = if (hasTalentNodeOptions) Modifier.heightIn(min = 420.dp) else Modifier,
                title = when {
                    isTalentContext -> "Arvore de Talentos"
                    isExplorationAreasContext -> "Areas de Exploracao"
                    else -> null
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (options.isEmpty()) {
                        Text("Autosave ativo. Nenhuma acao manual necessaria nesta tela.")
                    } else if (hasTalentNodeOptions) {
                        TalentNodeGrid(
                            options = options,
                            actionPreviews = actionPreviews,
                            graph = talentTreeGraph,
                            onAction = onAction,
                            onPreview = { pendingPreview = it }
                        )
                    } else if (isExplorationAreasContext) {
                        options.forEach { option ->
                            ExplorationAreaActionCard(
                                label = option.label,
                                onClick = { onAction(option.action) }
                            )
                        }
                    } else if (useCompactActionGrid) {
                        val compactRows = options.chunked(2)
                        compactRows.forEach { rowOptions ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(0.90f)
                                    .align(Alignment.CenterHorizontally),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowOptions.forEach { option ->
                                    val preview = actionPreviews[option.key]
                                    val rawLabel = formattedOptionLabel(
                                        option = option,
                                        section = section,
                                        isAchievementContext = isAchievementContext,
                                        isQuestContext = isQuestContext
                                    )
                                    GamePrimaryButton(
                                        label = toInfoButtonLabel(rawLabel),
                                        onClick = {
                                            if (preview != null) {
                                                pendingPreview = preview
                                            } else {
                                                onAction(option.action)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        tone = if (optionShouldAlert(option, isQuestContext)) {
                                            GameButtonTone.ALERT
                                        } else {
                                            GameButtonTone.DEFAULT
                                        },
                                        density = GameButtonDensity.INFO
                                    )
                                }
                                if (rowOptions.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        options.forEach { option ->
                            val preview = actionPreviews[option.key]
                            val rawLabel = formattedOptionLabel(
                                option = option,
                                section = section,
                                isAchievementContext = isAchievementContext,
                                isQuestContext = isQuestContext
                            )
                            GamePrimaryButton(
                                label = toInfoButtonLabel(rawLabel),
                                onClick = {
                                    if (preview != null) {
                                        pendingPreview = preview
                                    } else {
                                        onAction(option.action)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth(if (isAchievementContext) 0.70f else 0.78f)
                                    .align(Alignment.CenterHorizontally),
                                tone = if (optionShouldAlert(option, isQuestContext)) {
                                    GameButtonTone.ALERT
                                } else {
                                    GameButtonTone.DEFAULT
                                },
                                density = GameButtonDensity.INFO
                            )
                        }
                    }
                }
            }

            if (section == MainSection.PRODUCTION) {
                GamePanel(title = "Log de producao") {
                    if (productionLogLines.isEmpty()) {
                        Text("Sem eventos recentes.")
                    } else {
                        productionLogLines.forEach { line -> Text(line) }
                    }
                }
            }
        }
    }

    pendingPreview?.let { preview ->
        MenuActionPreviewPopup(
            preview = preview,
            section = section,
            onDismiss = { pendingPreview = null },
            onAction = onAction
        )
    }
}
