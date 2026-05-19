package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.hunting.HuntingQueryService
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class HuntingScreenPresenter(
    private val queryService: HuntingQueryService,
    private val support: PresentationSupport
) {
    fun presentSpotList(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Caça")
        val spots = queryService.spots(state)
        val options = spots.mapIndexed { index, spot ->
            val status = when {
                !spot.unlocked -> spot.unlockReason ?: "Bloqueado"
                spot.available -> "Disponível"
                else -> spot.blockedReasons.joinToString(" | ").ifBlank { "Indisponível" }
            }
            ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = "${spot.name} | nv desbloq ${spot.unlockLevel} | ciclo base ${spot.minimumCycleSeconds}s | $status",
                action = GameAction.SelectHuntingSpot(spot.id),
                enabled = spot.unlocked,
                lockedReason = spot.unlockReason
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf<String>()
        if (spots.isEmpty()) {
            body += "Nenhum spot de caça disponível."
        } else {
            body += "Escolha um spot de caça."
            spots.take(5).forEach { spot ->
                val description = spot.description.ifBlank { "Sem descrição." }
                val unlockLine = if (spot.unlocked) {
                    "nv personagem ${spot.playerLevel}/${spot.unlockLevel} | nv caça ${spot.huntingSkillLevel}"
                } else {
                    spot.unlockReason ?: "Bloqueado"
                }
                val costLine = if (spot.previewCostGold > 0) {
                    " (custo base ${spot.previewCostGold} ouro)"
                } else {
                    ""
                }
                body += "- ${spot.name}: $description [$unlockLine]$costLine"
            }
            if (spots.size > 5) {
                body += "... (${spots.size - 5} spots adicionais nas opções)."
            }
        }

        return MenuScreenViewModel(
            title = "Caça - Spots",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentDurationList(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Caça")
        val spotId = session.selectedHuntingSpotId
            ?: return MenuScreenViewModel(
                title = "Caça - Duração",
                summary = support.playerSummary(state),
                bodyLines = listOf("Escolha um spot primeiro."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val durations = queryService.durationOptions(state, spotId)
        val options = durations.mapIndexed { index, option ->
            ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = option.label,
                action = GameAction.AttemptHunting(spotId = spotId, durationSeconds = option.durationSeconds)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        val body = if (durations.isEmpty()) {
            listOf("Nenhuma duração válida para este spot.")
        } else {
            listOf(
                "Spot selecionado: $spotId",
                "Escolha o tempo total real da caça; nível de caça melhora eficiência do ciclo sem alterar o tempo total."
            )
        }
        return MenuScreenViewModel(
            title = "Caça - Duração",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }
}
