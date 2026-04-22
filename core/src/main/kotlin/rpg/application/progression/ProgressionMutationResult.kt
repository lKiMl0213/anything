package rpg.application.progression

import rpg.model.GameState

data class ProgressionMutationResult(
    val state: GameState,
    val messages: List<String> = emptyList()
)
