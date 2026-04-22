package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.city.CityQueryService
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class CityScreenPresenter(
    private val cityQueryService: CityQueryService,
    private val support: PresentationSupport
) {
    fun presentTavern(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Taverna")
        val tavern = cityQueryService.tavern(state)
        val options = mutableListOf<ScreenOptionViewModel>()
        options += ScreenOptionViewModel("1", "Descansar - custo ${tavern.restCost}", GameAction.TavernRest)
        options += ScreenOptionViewModel("2", "Dormir - custo ${tavern.sleepCost}", GameAction.TavernSleep)
        options += ScreenOptionViewModel("3", "Purificar 1 stack - custo ${tavern.purifyOneCost}", GameAction.TavernPurifyOne)
        options += ScreenOptionViewModel("4", "Purificar tudo - custo ${tavern.purifyAllCost}", GameAction.TavernPurifyAll)
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Taverna",
            summary = support.playerSummary(state),
            bodyLines = tavern.detailLines,
            options = options,
            messages = session.messages
        )
    }
}
