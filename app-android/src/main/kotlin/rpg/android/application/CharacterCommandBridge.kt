package rpg.android.application

import rpg.application.character.CharacterCommandService
import rpg.application.character.CharacterMutationResult
import rpg.model.GameState

class CharacterCommandBridge(
    private val characterCommandService: CharacterCommandService
) {
    fun applyAttributes(
        state: GameState,
        targetValues: Map<String, Int>
    ): CharacterMutationResult {
        return characterCommandService.applyAttributes(state, targetValues)
    }
}
