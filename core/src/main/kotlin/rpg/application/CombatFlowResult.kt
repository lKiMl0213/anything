package rpg.application

import rpg.model.GameState
import rpg.navigation.NavigationState

data class CombatFlowResult(
    val gameState: GameState,
    val navigation: NavigationState,
    val messages: List<String>
)
