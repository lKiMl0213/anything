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

internal fun formattedOptionLabel(option: ScreenOptionViewModel, section: MainSection): String {
    val cleaned = when {
        section == MainSection.PRODUCTION -> compactProductionOptionLabel(option)
        else -> stripAlertMarker(option.label)
    }
    return if (optionHasAlert(option.label)) "! $cleaned" else cleaned
}

internal fun optionHasAlert(label: String): Boolean {
    return label.contains("(!)")
}

internal fun compactProductionSummary(lines: List<String>): List<String> {
    val normalized = lines
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val unique = linkedSetOf<String>()
    normalized.forEach { unique += it }

    return unique.map { line ->
        when {
            line.contains("Selecione uma receita", ignoreCase = true) -> "Escolha uma opcao abaixo para ver o detalhe."
            line.contains("Categoria:", ignoreCase = true) -> line
            line.contains("Disciplina:", ignoreCase = true) -> line
            else -> line
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

internal fun cityRelevantMessages(messages: List<String>): List<String> {
    return messages.filterNot { line ->
        line.contains("Conquista concluida", ignoreCase = true) ||
            line.contains("Recompensa disponivel", ignoreCase = true)
    }.takeLast(6)
}

private fun compactProductionOptionLabel(option: ScreenOptionViewModel): String {
    val label = stripAlertMarker(option.label)
    return when (option.action) {
        is GameAction.InspectCraftRecipe,
        is GameAction.GatherNode,
        is GameAction.SelectHuntingSpot -> {
            val target = label.substringBefore("->").trim().ifBlank { label.substringBefore("|").trim() }
            val parts = label.split("|").map { it.trim() }.filter { it.isNotBlank() }
            val status = parts.firstOrNull {
                it.contains("Dispon", ignoreCase = true) || it.contains("Skill", ignoreCase = true)
            } ?: parts.getOrNull(1)
            listOfNotNull(target.ifBlank { null }, status).joinToString(" | ").ifBlank { label }
        }

        else -> label
    }
}

private fun stripAlertMarker(label: String): String {
    return label.replace("(!)", "").replace("( ! )", "").trim()
}
