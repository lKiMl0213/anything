package rpg.application.exploration

import rpg.application.GameActionResult
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.PendingDungeonEvent
import rpg.engine.GameEngine
import rpg.model.DungeonRun
import rpg.model.GameState
import rpg.model.MapTierDef

internal class DungeonEventFlowCoordinator(
    engine: GameEngine,
    stateSupport: GameStateSupport
) {
    private val preparationService = DungeonEventPreparationService(engine)
    private val resolutionService = DungeonEventResolutionService(engine, stateSupport)

    fun preparePendingEvent(
        state: GameState,
        run: DungeonRun,
        tier: MapTierDef
    ): PendingDungeonEvent {
        return preparationService.preparePendingEvent(state, run, tier)
    }

    fun resolve(
        session: GameSession,
        choice: Int
    ): GameActionResult {
        return resolutionService.resolve(session, choice)
    }
}
