package rpg.application

sealed interface GameEffect {
    data object None : GameEffect
    data class LaunchCombat(val encounter: PendingEncounter) : GameEffect
}

data class GameActionResult(
    val session: GameSession,
    val effect: GameEffect = GameEffect.None
)
