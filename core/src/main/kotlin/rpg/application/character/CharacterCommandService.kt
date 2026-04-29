package rpg.application.character

import rpg.model.GameState

class CharacterCommandService(
    private val support: CharacterRulesSupport
) {
    fun allocateAttributePoint(state: GameState, attributeCode: String): CharacterMutationResult {
        return support.allocateAttributePoint(state, attributeCode)
    }

    fun allocateAttributePoints(state: GameState, attributeCode: String, amount: Int): CharacterMutationResult {
        return support.allocateAttributePoints(state, attributeCode, amount)
    }

    fun applyAttributes(state: GameState, targetValues: Map<String, Int>): CharacterMutationResult {
        return support.applyAttributes(state, targetValues)
    }

    fun rankUpTalentNode(state: GameState, treeId: String, nodeId: String): CharacterMutationResult {
        return support.rankUpTalentNode(state, treeId, nodeId)
    }
}
