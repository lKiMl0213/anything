package rpg.android.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import rpg.android.state.MainSection
import rpg.android.state.MenuActionPreviewUiModel
import rpg.android.state.TalentTreeGraphUiModel
import rpg.android.ui.components.GamePrimaryButton
import rpg.application.actions.GameAction
import rpg.presentation.model.ScreenOptionViewModel

@Composable
internal fun TalentNodeGrid(
    options: List<ScreenOptionViewModel>,
    actionPreviews: Map<String, MenuActionPreviewUiModel>,
    graph: TalentTreeGraphUiModel?,
    onAction: (GameAction) -> Unit,
    onPreview: (MenuActionPreviewUiModel) -> Unit
) {
    val backOption = options.firstOrNull { it.action is GameAction.Back }
    val cards = buildTalentNodeCards(options, actionPreviews, graph)

    graph?.let {
        Text("${it.stageLabel} | pontos: ${it.pointsAvailable}")
    }

    if (cards.isEmpty()) {
        Text("Nenhum talento disponivel neste momento.")
    } else {
        TalentTreeGraph(
            cards = cards,
            actionPreviews = actionPreviews,
            onAction = onAction,
            onPreview = onPreview
        )
    }

    backOption?.let { back ->
        GamePrimaryButton(
            label = stripAlertMarker(back.label),
            onClick = { onAction(back.action) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal fun formattedOptionLabel(
    option: ScreenOptionViewModel,
    section: MainSection,
    isAchievementContext: Boolean = false,
    isQuestContext: Boolean = false,
    screenTitle: String = ""
): String {
    val cleaned = when {
        section == MainSection.PRODUCTION -> compactProductionOptionLabel(option)
        isAchievementContext -> compactAchievementOptionLabel(option.label)
        isQuestContext -> compactQuestOptionLabel(
            label = option.label,
            hideInProgressLabel = screenTitle.contains("pool aceit", ignoreCase = true)
        )
        else -> stripAlertMarker(option.label)
    }
    val adjusted = if (section == MainSection.CITY) {
        cleaned.replace("Aprimoramentos", "Melhorias", ignoreCase = true)
    } else {
        cleaned
    }
    return if (optionHasAlert(option.label)) "! $adjusted" else adjusted
}

internal fun optionHasAlert(label: String): Boolean {
    return label.contains("(!)")
}

internal fun optionHasQuestAlert(label: String): Boolean {
    return optionHasAlert(label)
}

internal fun craftAvailabilityForUi(option: ScreenOptionViewModel): Boolean? {
    return when (option.action) {
        is GameAction.InspectCraftRecipe,
        is GameAction.CraftRecipe -> {
            val label = option.label
            when {
                label.contains("indispon", ignoreCase = true) ||
                    label.contains("bloqueado", ignoreCase = true) -> false
                label.contains("dispon", ignoreCase = true) -> true
                else -> null
            }
        }

        else -> null
    }
}

internal fun compactProductionSummary(lines: List<String>): List<String> {
    val normalized = lines
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it.contains("skill", ignoreCase = true) }
        .filterNot { it.contains("dispon", ignoreCase = true) }
    val unique = linkedSetOf<String>()
    normalized.forEach { unique += it }

    return unique.mapNotNull { line ->
        when {
            line.contains("Selecione uma receita", ignoreCase = true) -> "Escolha uma opcao abaixo para ver o detalhe."
            line.contains("Categoria:", ignoreCase = true) -> line
            line.contains("Disciplina:", ignoreCase = true) -> line
            line.startsWith("- ") -> null
            else -> line.replace("|", " - ")
        }
    }.take(6)
}

internal fun compactTalentSummary(lines: List<String>): List<String> {
    val keywords = listOf("Classe", "Pontos", "desbloqueadas", "bloqueadas", "Pode evoluir", "cuidado")
    val selected = lines.filter { line -> keywords.any { line.contains(it, ignoreCase = true) } }
    return if (selected.isNotEmpty()) selected.take(6) else lines.take(6)
}

internal fun compactUpgradeSummary(lines: List<String>): List<String> {
    val summary = lines
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it.startsWith("- ") }
        .filterNot { it.contains("Nivel atual", ignoreCase = true) }
        .filterNot { it.contains("Atual:", ignoreCase = true) }
        .filterNot { it.contains("Proximo:", ignoreCase = true) }
        .take(3)
        .toMutableList()
    summary += "Toque em um aprimoramento para ver detalhes e confirmar compra."
    return summary
}

internal fun compactStatisticsSummary(lines: List<String>): List<String> {
    return lines
        .filter { it.isNotBlank() }
        .filterNot { it.equals("MOBS", ignoreCase = true) || it.equals("GERAL", ignoreCase = true) }
        .take(14)
}

internal fun toInfoButtonLabel(raw: String): String {
    val parts = raw.split("|")
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (parts.size < 2) {
        return raw
    }
    val first = parts.first()
    val second = parts.drop(1).firstOrNull().orEmpty()
    return if (second.isBlank()) first else "$first\n$second"
}

internal fun cityRelevantMessages(messages: List<String>): List<String> {
    return messages.filterNot { line ->
        line.contains("Conquista concluida", ignoreCase = true) ||
            line.contains("Recompensa disponivel", ignoreCase = true)
    }.takeLast(6)
}

internal fun visibleOptions(options: List<ScreenOptionViewModel>): List<ScreenOptionViewModel> {
    return options.filterNot { option ->
        option.action is GameAction.OpenSaveMenu ||
            option.action is GameAction.SaveCurrentGame ||
            option.action is GameAction.SaveAutosave ||
            option.action is GameAction.Exit
    }
}

internal fun isGlobalBossContext(title: String, options: List<ScreenOptionViewModel>): Boolean {
    if (title.contains("Boss Global", ignoreCase = true)) {
        return true
    }
    return options.any { option -> isGlobalBossAction(option.action) }
}

internal fun isQuestContext(title: String, options: List<ScreenOptionViewModel>): Boolean {
    if (title.contains("quest", ignoreCase = true)) {
        return true
    }
    return options.any { option -> isQuestAction(option.action) }
}

internal fun isAchievementContext(title: String, options: List<ScreenOptionViewModel>): Boolean {
    if (title.contains("conquista", ignoreCase = true)) {
        return true
    }
    return options.any { option ->
        when (option.action) {
            GameAction.OpenAchievements,
            GameAction.OpenAchievementStatistics -> true
            is GameAction.OpenAchievementCategory,
            is GameAction.InspectAchievement,
            is GameAction.ClaimAchievementReward -> true
            else -> false
        }
    }
}

internal fun isExplorationAreasContext(title: String, options: List<ScreenOptionViewModel>): Boolean {
    if (!title.contains("Areas de Exploracao", ignoreCase = true)) {
        return false
    }
    return options.any { it.action is GameAction.EnterDungeon || it.action is GameAction.EnterClassDungeon }
}

internal data class ExplorationAreaPreview(
    val title: String,
    val description: String?
)

internal fun explorationAreaPreview(label: String): ExplorationAreaPreview {
    val cleaned = stripAlertMarker(label)
    val parts = cleaned.split("|").map { it.trim() }.filter { it.isNotBlank() }
    val title = parts.firstOrNull()
        ?.replace(Regex("\\(\\s*nv minimo[^)]*\\)", RegexOption.IGNORE_CASE), "")
        ?.replace(Regex("\\(\\s*n[ií]vel m[ií]nimo[^)]*\\)", RegexOption.IGNORE_CASE), "")
        ?.trim()
        .orEmpty()
        .ifBlank { cleaned.substringBefore("|").trim().ifBlank { cleaned } }
    val description = parts.drop(1).firstOrNull()?.trim()
        ?.takeUnless { it.equals(title, ignoreCase = true) }
        ?.takeUnless { line ->
            title.isNotBlank() && line.contains(title, ignoreCase = true)
        }
    return ExplorationAreaPreview(
        title = title,
        description = description?.takeIf { it.isNotBlank() }
    )
}

private fun compactProductionOptionLabel(option: ScreenOptionViewModel): String {
    val label = stripAlertMarker(option.label)
    return when (option.action) {
        is GameAction.InspectCraftRecipe,
        is GameAction.GatherNode,
        is GameAction.SelectHuntingSpot -> {
            val target = label.substringBefore("->").trim().ifBlank { label.substringBefore("|").trim() }
            val parts = label.split("|").map { it.trim() }.filter { it.isNotBlank() }
            val time = parts.firstOrNull {
                it.contains("tempo", ignoreCase = true) || it.contains("acao", ignoreCase = true)
            }
            val compact = listOfNotNull(target.ifBlank { null }, time).joinToString(" - ")
            compact.ifBlank { cleanupProductionLabel(label) }
        }

        else -> cleanupProductionLabel(label)
    }
}

private fun stripAlertMarker(label: String): String {
    return label.replace("(!)", "").replace("( ! )", "").trim()
}

private fun compactAchievementOptionLabel(label: String): String {
    val cleaned = stripAlertMarker(label)
    val parts = cleaned.split("|").map { it.trim() }.filter { it.isNotBlank() }
    if (parts.isEmpty()) return cleaned
    val title = parts.first()
    val ready = parts.firstOrNull { it.contains("pront", ignoreCase = true) }
    val status = parts.firstOrNull {
        it.contains("MAX", ignoreCase = true) || it.contains("status", ignoreCase = true)
    }
    val suffix = listOfNotNull(ready, status).joinToString(" | ")
    return if (suffix.isBlank()) title else "$title | $suffix"
}

private fun compactQuestOptionLabel(label: String, hideInProgressLabel: Boolean): String {
    val cleaned = stripAlertMarker(label)
    val parts = cleaned.split("|").map { it.trim() }.filter { it.isNotBlank() }
    if (parts.isEmpty()) return cleaned
    val title = parts.first()
    val status = parts.firstOrNull {
        it.contains("concluir", ignoreCase = true) ||
            it.contains("pronta", ignoreCase = true) ||
            it.contains("status", ignoreCase = true) ||
            it.contains("em andamento", ignoreCase = true)
    } ?: parts.getOrNull(1)
    val normalizedStatus = status?.takeUnless {
        hideInProgressLabel && it.contains("em andamento", ignoreCase = true)
    }
    return listOfNotNull(title, normalizedStatus).joinToString(" | ").ifBlank { title }
}

private fun cleanupProductionLabel(raw: String): String {
    return raw
        .replace("|", " - ")
        .replace("Disponivel", "", ignoreCase = true)
        .replace("Indisponivel", "", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .replace(" - - ", " - ")
        .replace("--", "-")
        .trim(' ', '-')
}

private fun isQuestAction(action: GameAction): Boolean {
    return when (action) {
        GameAction.OpenQuests,
        GameAction.OpenClassQuest,
        GameAction.RequestCancelClassQuest,
        GameAction.ConfirmCancelClassQuest -> true
        is GameAction.OpenQuestSection,
        is GameAction.InspectQuest,
        is GameAction.AcceptQuest,
        is GameAction.ClaimQuest,
        is GameAction.CancelQuest,
        is GameAction.ReplaceQuest,
        is GameAction.ChooseClassQuestPath -> true
        else -> false
    }
}

private fun isGlobalBossAction(action: GameAction): Boolean {
    return when (action) {
        GameAction.OpenGlobalBossMenu,
        GameAction.OpenGlobalBossWeekly,
        GameAction.OpenGlobalBossMonthly -> true
        is GameAction.OpenGlobalBossMilestones,
        is GameAction.StartGlobalBossRun,
        is GameAction.AutoClearGlobalBossRun,
        is GameAction.BuyGlobalBossRunAttempt,
        is GameAction.ClaimGlobalBossMilestone -> true
        else -> false
    }
}
