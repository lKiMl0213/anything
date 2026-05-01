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
            val status = if (spot.available) "Disponível" else spot.blockedReasons.joinToString(" | ")
            ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = "${spot.name} | lvl recomendado ${spot.recommendedLevel} | ciclo base ${spot.minimumCycleSeconds}s | $status",
                action = GameAction.SelectHuntingSpot(spot.id)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        val body = mutableListOf<String>()
        if (spots.isEmpty()) {
            body += "Nenhum spot de caça disponível."
        } else {
            body += "Escolha um spot de caça."
            spots.take(5).forEach { spot ->
                val description = spot.description.ifBlank { "Sem descrição." }
                body += "- ${spot.name}: $description (custo base ${spot.previewCostGold} ouro)"
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
                "Escolha o tempo total real da caça; nível melhora eficiência do ciclo sem alterar o tempo total."
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
