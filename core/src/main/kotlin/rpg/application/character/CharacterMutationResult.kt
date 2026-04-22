package rpg.application.character

import rpg.model.GameState

data class CharacterMutationResult(
    val state: GameState,
    val messages: List<String> = emptyList()
)
