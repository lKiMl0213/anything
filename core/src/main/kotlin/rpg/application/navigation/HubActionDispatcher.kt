package rpg.application.navigation

import rpg.application.GameActionResult
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.engine.GameEngine
import rpg.navigation.NavigationState

class HubActionDispatcher(
    private val engine: GameEngine,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenCharacterMenu -> move(session, NavigationState.CharacterMenu)
            GameAction.OpenExploration -> openExploration(session)
            GameAction.ConfirmLowHpExploration -> move(session, NavigationState.Exploration)
            GameAction.OpenProductionMenu -> move(session, NavigationState.ProductionMenu)
            GameAction.OpenProgressionMenu -> move(session, NavigationState.ProgressionMenu)
            GameAction.OpenCityMenu -> move(session, NavigationState.CityMenu)
            else -> null
        }
    }

    private fun openExploration(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val stats = engine.computePlayerStats(normalized.player, normalized.itemInstances)
        val hpMax = stats.derived.hpMax.coerceAtLeast(1.0)
        val hpNow = normalized.player.currentHp.coerceAtLeast(0.0)
        if (hpNow <= 0.0) {
            return GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    navigation = NavigationState.Hub,
                    messages = listOf("Cure-se antes de prosseguir.")
                )
            )
        }
        val hpRatio = hpNow / hpMax
        if (hpRatio < 0.20) {
            return GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    navigation = NavigationState.ExplorationLowHpConfirm,
                    messages = emptyList()
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.Exploration,
                messages = emptyList()
            )
        )
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
}
