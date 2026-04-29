package rpg.application

import rpg.application.actions.GameAction
import rpg.application.production.ProductionTimedActionView

sealed interface GameEffect {
    data object None : GameEffect
    data class LaunchCombat(val encounter: PendingEncounter) : GameEffect
    data class LaunchProductionTimedAction(
        val view: ProductionTimedActionView,
        val completionAction: GameAction
    ) : GameEffect
}

data class GameActionResult(
    val session: GameSession,
    val effect: GameEffect = GameEffect.None
)
