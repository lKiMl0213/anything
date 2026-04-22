package rpg.application.city

import rpg.model.GameState

data class CityMutationResult(
    val state: GameState,
    val messages: List<String> = emptyList()
)
