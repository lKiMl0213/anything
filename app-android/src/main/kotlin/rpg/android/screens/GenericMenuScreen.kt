package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.state.MainSection
import rpg.android.state.MenuActionPreviewUiModel
import rpg.android.state.TalentTreeGraphUiModel
import rpg.android.ui.components.BottomNavItem
import rpg.android.ui.components.GameBottomNav
import rpg.android.ui.components.GameButtonTone
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GamePopup
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameScreenRoot
import rpg.application.actions.GameAction
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel

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
    val options = visibleOptions(viewModel.options)
    val hasTalentStageOptions = options.any { it.action is GameAction.OpenTalentStage }
    val hasTalentNodeOptions = options.any { it.action is GameAction.InspectTalentNode }
    val isTalentContext = hasTalentStageOptions || hasTalentNodeOptions || viewModel.title.contains("talento", ignoreCase = true)
    val isUpgradeContext = viewModel.title.contains("aprimoramentos", ignoreCase = true)
    val isStatisticsContext = viewModel.title.contains("estatisticas", ignoreCase = true)
    val showPlayerSummary = section != MainSection.PRODUCTION && section != MainSection.PROGRESSION
    val bodyLines = when {
        isTalentContext -> compactTalentSummary(viewModel.bodyLines)
        isUpgradeContext -> compactUpgradeSummary(viewModel.bodyLines)
        isStatisticsContext -> compactStatisticsSummary(viewModel.bodyLines)
        section == MainSection.PRODUCTION -> compactProductionSummary(viewModel.bodyLines)
        else -> viewModel.bodyLines
    }
    val productionLogLines = if (section == MainSection.PRODUCTION) viewModel.messages.takeLast(6) else emptyList()
    val sectionMessages = when (section) {
        MainSection.CITY -> cityRelevantMessages(viewModel.messages)
        else -> viewModel.messages
    }

    GameScreenRoot(
        backgroundRes = background,
        bottomNav = {
            GameBottomNav(
                items = listOf(
                    BottomNavItem(
                        key = "character",
                        label = "Personagem",
                        selected = section == MainSection.CHARACTER,
                        onClick = onOpenCharacter
                    ),
                    BottomNavItem(
                        key = "production",
                        label = "Producao",
                        selected = section == MainSection.PRODUCTION,
                        onClick = onOpenProduction
                    ),
                    BottomNavItem(
                        key = "explore",
                        label = "Explorar",
                        selected = section == MainSection.EXPLORATION,
                        onClick = onOpenHub
                    ),
                    BottomNavItem(
                        key = "city",
                        label = "Cidade",
                        selected = section == MainSection.CITY,
                        onClick = onOpenCity
                    ),
                    BottomNavItem(
                        key = "progress",
                        label = "Progresso",
                        selected = section == MainSection.PROGRESSION,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GamePanel(title = viewModel.title) {
                viewModel.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it) }
                if (showPlayerSummary) {
                    viewModel.summary?.let { summary ->
                        Text("${summary.name} | Nv ${summary.level}")
                        Text(summary.classLabel)
                        Text("HP ${summary.hp.current.toInt()}/${summary.hp.max.toInt()} | MP ${summary.mp.current.toInt()}/${summary.mp.max.toInt()} | Ouro ${summary.gold}")
                    }
                }
                bodyLines.forEach { Text(it) }
                if (section != MainSection.PRODUCTION) {
                    sectionMessages.forEach { Text(it) }
                }
            }

            GamePanel(
                modifier = if (hasTalentNodeOptions) Modifier.heightIn(min = 420.dp) else Modifier,
                title = if (isTalentContext) "Arvore de Talentos" else "Acoes"
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
                    } else {
                        options.forEach { option ->
                            val preview = actionPreviews[option.key]
                            GamePrimaryButton(
                                label = formattedOptionLabel(option, section),
                                onClick = {
                                    if (preview != null) {
                                        pendingPreview = preview
                                    } else {
                                        onAction(option.action)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                tone = if (optionHasAlert(option.label)) GameButtonTone.ALERT else GameButtonTone.DEFAULT
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
        GamePopup(
            title = preview.title,
            onDismiss = { pendingPreview = null },
            showCloseButton = section != MainSection.PRODUCTION
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                preview.lines.forEach { line ->
                    Text(
                        text = line,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                GamePrimaryButton(
                    label = preview.primaryLabel,
                    onClick = {
                        pendingPreview = null
                        onAction(preview.primaryAction)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (preview.secondaryLabel != null && preview.secondaryAction != null) {
                    GamePrimaryButton(
                        label = preview.secondaryLabel,
                        onClick = {
                            pendingPreview = null
                            onAction(preview.secondaryAction)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (section == MainSection.PRODUCTION) {
                    GamePrimaryButton(
                        label = "Cancelar",
                        onClick = { pendingPreview = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun visibleOptions(options: List<ScreenOptionViewModel>): List<ScreenOptionViewModel> {
    return options.filterNot { option ->
        option.action is GameAction.OpenSaveMenu ||
            option.action is GameAction.SaveCurrentGame ||
            option.action is GameAction.SaveAutosave ||
            option.action is GameAction.Exit
    }
}
