package rpg.application.navigation

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class HubActionDispatcher(
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenCharacterMenu -> move(session, NavigationState.CharacterMenu)
            GameAction.OpenExploration -> move(session, NavigationState.Exploration)
            GameAction.OpenProductionMenu -> launchLegacyProduction(session)
            GameAction.OpenProgressionMenu -> move(session, NavigationState.ProgressionMenu)
            GameAction.OpenCityMenu -> move(session, NavigationState.CityMenu)
            GameAction.OpenLegacyExploration -> launchLegacyExploration(session)
            GameAction.OpenLegacyProduction -> launchLegacyProduction(session)
            GameAction.OpenLegacyCity -> launchLegacyCity(session)
            else -> null
        }
    }

    private fun move(session: GameSession, destination: NavigationState): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = destination,
                messages = emptyList()
            )
        )
    }

    private fun launchLegacyExploration(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(messages = emptyList()),
            effect = GameEffect.LaunchLegacyExploration(stateSupport.normalize(state))
        )
    }

    private fun launchLegacyProduction(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(messages = emptyList()),
            effect = GameEffect.LaunchLegacyProduction(stateSupport.normalize(state))
        )
    }

    private fun launchLegacyCity(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(messages = emptyList()),
            effect = GameEffect.LaunchLegacyCity(stateSupport.normalize(state))
        )
    }
}
