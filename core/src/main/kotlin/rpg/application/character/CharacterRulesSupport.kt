package rpg.application.character

import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.TalentTree
import rpg.talent.TalentTreeService

class CharacterRulesSupport(
    private val repo: DataRepository,
    private val engine: GameEngine
) {
    private val talentTreeService = TalentTreeService(repo.balance.talentPoints)
    private val attributeSupport = CharacterAttributeSupport(engine)
    private val talentEffectFormatter = CharacterTalentEffectFormatter()
    private val talentViewSupport = CharacterTalentViewSupport(
        engine = engine,
        talentTreeService = talentTreeService,
        allTalentTreesProvider = { repo.talentTreesV2.values },
        effectFormatter = talentEffectFormatter
    )

    fun hasUnspentAttributePoints(player: PlayerState): Boolean {
        return attributeSupport.hasUnspentAttributePoints(player)
    }

    fun hasTalentPointsAvailable(player: PlayerState): Boolean {
        return talentViewSupport.hasTalentPointsAvailable(player)
    }

    fun activeTalentTrees(player: PlayerState): List<TalentTree> {
        return talentViewSupport.activeTalentTrees(player)
    }

    fun attributeRows(state: GameState): List<AttributeRowView> {
        return attributeSupport.attributeRows(state)
    }

    fun attributeDetail(state: GameState, attributeCode: String): AttributeDetailView? {
        return attributeSupport.attributeDetail(state, attributeCode)
    }

    fun attributeDisplayLabel(code: String): String {
        return attributeSupport.attributeDisplayLabel(code)
    }

    fun talentOverview(player: PlayerState): TalentOverviewView {
        return talentViewSupport.talentOverview(player)
    }

    fun talentStage(player: PlayerState, stage: Int): TalentStageView? {
        return talentViewSupport.talentStage(player, stage)
    }

    fun talentTreeDetail(player: PlayerState, treeId: String): TalentTreeDetailView? {
        return talentViewSupport.talentTreeDetail(player, treeId)
    }

    fun talentNodeDetail(player: PlayerState, treeId: String, nodeId: String): TalentNodeDetailView? {
        return talentViewSupport.talentNodeDetail(player, treeId, nodeId)
    }

    fun allocateAttributePoint(state: GameState, attributeCode: String): CharacterMutationResult {
        return attributeSupport.allocateAttributePoint(state, attributeCode)
    }

    fun rankUpTalentNode(state: GameState, treeId: String, nodeId: String): CharacterMutationResult {
        val trees = activeTalentTrees(state.player)
        val tree = trees.firstOrNull { it.id == treeId }
            ?: return CharacterMutationResult(state, listOf("Essa arvore nao esta disponivel no momento."))
        val result = talentTreeService.rankUp(state.player, tree, nodeId, trees)
        val updatedPlayer = attributeSupport.clampPlayerResources(result.player, state.itemInstances)
        return CharacterMutationResult(
            state.copy(player = updatedPlayer),
            listOf(result.message)
        )
    }
}
