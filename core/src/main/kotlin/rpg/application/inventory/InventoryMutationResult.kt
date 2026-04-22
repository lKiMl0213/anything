package rpg.application.inventory

import rpg.model.GameState

data class InventoryMutationResult(
    val state: GameState,
    val messages: List<String> = emptyList()
)
