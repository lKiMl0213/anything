package rpg.application.character

import rpg.model.GameState

class CharacterCommandService(
    private val support: CharacterRulesSupport
) {
    fun allocateAttributePoint(state: GameState, attributeCode: String): CharacterMutationResult {
        return support.allocateAttributePoint(state, attributeCode)
    }

    fun rankUpTalentNode(state: GameState, treeId: String, nodeId: String): CharacterMutationResult {
        return support.rankUpTalentNode(state, treeId, nodeId)
    }
}
