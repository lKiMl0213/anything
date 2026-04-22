package rpg.application.character

import rpg.model.GameState
import rpg.model.PlayerState

class CharacterQueryService(
    private val support: CharacterRulesSupport
) {
    fun hasUnspentAttributePoints(player: PlayerState): Boolean = support.hasUnspentAttributePoints(player)

    fun hasTalentPointsAvailable(player: PlayerState): Boolean = support.hasTalentPointsAvailable(player)

    fun attributeRows(state: GameState): List<AttributeRowView> = support.attributeRows(state)

    fun attributeDetail(state: GameState, attributeCode: String): AttributeDetailView? {
        return support.attributeDetail(state, attributeCode)
    }

    fun talentOverview(state: GameState): TalentOverviewView = support.talentOverview(state.player)

    fun talentStage(state: GameState, stage: Int): TalentStageView? = support.talentStage(state.player, stage)

    fun talentTreeDetail(state: GameState, treeId: String): TalentTreeDetailView? {
        return support.talentTreeDetail(state.player, treeId)
    }

    fun talentNodeDetail(state: GameState, treeId: String, nodeId: String): TalentNodeDetailView? {
        return support.talentNodeDetail(state.player, treeId, nodeId)
    }

    fun attributeDisplayLabel(code: String): String = support.attributeDisplayLabel(code)
}
