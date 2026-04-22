package rpg.application

import rpg.model.GameState

sealed interface GameEffect {
    data object None : GameEffect
    data object LaunchLegacyNewGame : GameEffect
    data class LaunchLegacyExploration(val state: GameState) : GameEffect
    data class LaunchLegacyProduction(val state: GameState) : GameEffect
    data class LaunchLegacyCity(val state: GameState) : GameEffect
    data class LaunchCombat(val encounter: PendingEncounter) : GameEffect
}

data class GameActionResult(
    val session: GameSession,
    val effect: GameEffect = GameEffect.None
)
