package rpg.application.city

import rpg.application.GameActionResult
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class CityActionDispatcher(
    private val commandService: CityCommandService,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenTavern -> move(session, NavigationState.Tavern)
            GameAction.TavernRest -> mutate(session) { commandService.rest(it) }
            GameAction.TavernSleep -> mutate(session) { commandService.sleep(it) }
            GameAction.TavernPurifyOne -> mutate(session) { commandService.purifyOne(it) }
            GameAction.TavernPurifyAll -> mutate(session) { commandService.purifyAll(it) }
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

    private fun mutate(
        session: GameSession,
        block: (rpg.model.GameState) -> CityMutationResult
    ): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val mutation = block(normalized)
        val updatedState = stateSupport.normalize(mutation.state)
        return GameActionResult(
            session = session.copy(
                gameState = updatedState,
                navigation = NavigationState.Tavern,
                messages = mutation.messages
            )
        )
    }
}
