package rpg.application.hunting

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class HuntingActionDispatcher(
    private val commandService: HuntingCommandService,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenHuntingMenu -> openHunting(session)
            is GameAction.SelectHuntingSpot -> selectSpot(session, action.spotId)
            is GameAction.AttemptHunting -> queueHunting(session, action.spotId, action.durationSeconds)
            is GameAction.ExecuteHunting -> executeHunting(session, action.spotId, action.durationSeconds)
            else -> null
        }
    }

    private fun openHunting(session: GameSession): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionHuntingSpotList,
                selectedHuntingSpotId = null,
                messages = emptyList()
            )
        )
    }

    private fun selectSpot(session: GameSession, spotId: String): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionHuntingDurationList,
                selectedHuntingSpotId = spotId,
                messages = emptyList()
            )
        )
    }

    private fun queueHunting(session: GameSession, spotId: String, durationSeconds: Int): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        val normalized = stateSupport.normalize(state)
        val preparation = commandService.prepare(normalized, spotId, durationSeconds)
        val timedView = preparation.timedActionView
        if (!preparation.ready || timedView == null) {
            return GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    navigation = NavigationState.ProductionHuntingDurationList,
                    selectedHuntingSpotId = spotId,
                    messages = preparation.messages
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.ProductionHuntingDurationList,
                selectedHuntingSpotId = spotId,
                messages = emptyList()
            ),
            effect = GameEffect.LaunchProductionTimedAction(
                view = timedView,
                completionAction = GameAction.ExecuteHunting(spotId = spotId, durationSeconds = durationSeconds)
            )
        )
    }

    private fun executeHunting(session: GameSession, spotId: String, durationSeconds: Int): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        val normalized = stateSupport.normalize(state)
        val mutation = commandService.hunt(normalized, spotId, durationSeconds)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(mutation.state),
                navigation = NavigationState.ProductionHuntingDurationList,
                selectedHuntingSpotId = spotId,
                messages = mutation.messages
            )
        )
    }

    private fun missingState(session: GameSession): GameActionResult {
        return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
    }
}
