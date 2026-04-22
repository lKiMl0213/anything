package rpg.application.character

import rpg.engine.GameEngine
import rpg.model.PlayerState
import rpg.model.TalentNode
import rpg.model.TalentNodeType
import rpg.model.TalentPointMode
import rpg.model.TalentTree
import rpg.talent.TalentPointLedger
import rpg.talent.TalentRankCheck
import rpg.talent.TalentTreeService

internal class CharacterTalentViewSupport(
    private val engine: GameEngine,
    private val talentTreeService: TalentTreeService,
    private val allTalentTreesProvider: () -> Collection<TalentTree>,
    private val effectFormatter: CharacterTalentEffectFormatter
) {
    fun hasTalentPointsAvailable(player: PlayerState): Boolean {
        val trees = activeTalentTrees(player)
        if (trees.isEmpty()) return false
        val ledger = talentTreeService.pointsLedger(player, trees)
        return when (ledger.mode) {
            TalentPointMode.SHARED_POOL -> ledger.sharedAvailable > 0
            TalentPointMode.PER_TREE -> ledger.availableByTree.values.any { it > 0 }
        }
    }

    fun activeTalentTrees(player: PlayerState): List<TalentTree> {
        return talentTreeService.activeTrees(player, allTalentTreesProvider.invoke())
    }

    fun talentOverview(player: PlayerState): TalentOverviewView {
        val trees = activeTalentTrees(player)
        val ledger = talentTreeService.pointsLedger(player, trees)
        val totalSpent = ledger.spentByTree.values.sum()
        val totalEarned = when (ledger.mode) {
            TalentPointMode.SHARED_POOL -> ledger.sharedEarned
            TalentPointMode.PER_TREE -> ledger.earnedByTree.values.sum()
        }
        val totalAvailable = when (ledger.mode) {
            TalentPointMode.SHARED_POOL -> ledger.sharedAvailable
            TalentPointMode.PER_TREE -> ledger.availableByTree.values.sum()
        }
        return TalentOverviewView(
            totalSpent = totalSpent,
            totalEarned = totalEarned,
            totalAvailable = totalAvailable,
            stages = buildTalentMenuSlots(player, trees, ledger)
        )
    }

    fun talentStage(player: PlayerState, stage: Int): TalentStageView? {
        return talentOverview(player).stages.firstOrNull { it.stage == stage }
    }

    fun talentTreeDetail(player: PlayerState, treeId: String): TalentTreeDetailView? {
        val trees = activeTalentTrees(player)
        val tree = trees.firstOrNull { it.id == treeId } ?: return null
        val ledger = talentTreeService.pointsLedger(player, trees)
        val pointsAvailable = if (ledger.mode == TalentPointMode.PER_TREE) {
            ledger.availableByTree[tree.id] ?: 0
        } else {
            ledger.sharedAvailable
        }
        val unlockedSkillNames = tree.nodes
            .filter { it.nodeType == TalentNodeType.ACTIVE_SKILL && talentTreeService.nodeCurrentRank(player, it) > 0 }
            .map { it.name }
        val blockedSkillNames = tree.nodes
            .filter { it.nodeType == TalentNodeType.ACTIVE_SKILL && talentTreeService.nodeCurrentRank(player, it) <= 0 }
            .map { it.name }
        val availableNodes = tree.nodes.filter { talentTreeService.canRankUp(player, tree, it.id, trees).allowed }
        return TalentTreeDetailView(
            treeId = tree.id,
            title = talentTreeDisplayName(player, tree),
            stageLabel = talentStageLabel(tree.tier),
            pointsAvailable = pointsAvailable,
            unlockedSkillsLabel = if (unlockedSkillNames.isEmpty()) "-" else unlockedSkillNames.joinToString(", "),
            blockedSkillsLabel = if (blockedSkillNames.isEmpty()) "-" else blockedSkillNames.joinToString(", "),
            availableNodesLabel = if (availableNodes.isEmpty()) "-" else availableNodes.joinToString(", ") { it.name },
            nodes = tree.nodes.map { node ->
                val rank = talentTreeService.nodeCurrentRank(player, node)
                val maxRank = node.maxRank.coerceAtLeast(1)
                val check = talentTreeService.canRankUp(player, tree, node.id, trees)
                TalentNodeListItemView(
                    nodeId = node.id,
                    name = node.name,
                    typeLabel = talentNodeTypeLabel(node.nodeType),
                    rankLabel = "$rank/$maxRank",
                    stateLabel = talentNodeStateLabel(rank, maxRank, check),
                    prerequisitesLabel = formatTalentPrerequisites(tree, node),
                    exclusiveLabel = formatTalentExclusiveGroup(tree, node),
                    effectLabel = effectFormatter.talentNodeEffectSummary(node)
                )
            }
        )
    }

    fun talentNodeDetail(player: PlayerState, treeId: String, nodeId: String): TalentNodeDetailView? {
        val trees = activeTalentTrees(player)
        val tree = trees.firstOrNull { it.id == treeId } ?: return null
        val node = tree.nodes.firstOrNull { it.id == nodeId } ?: return null
        val check = talentTreeService.canRankUp(player, tree, node.id, trees)
        val currentRank = talentTreeService.nodeCurrentRank(player, node)
        val lines = mutableListOf<String>()
        lines += "Arvore: ${talentTreeDisplayName(player, tree)}"
        lines += "Etapa: ${talentStageLabel(tree.tier)}"
        lines += "Tipo: ${talentNodeTypeLabel(node.nodeType)}"
        lines += "Rank atual: $currentRank/${node.maxRank.coerceAtLeast(1)}"
        lines += "Pre-req: ${formatTalentPrerequisites(tree, node)}"
        lines += "Exclusivo: ${formatTalentExclusiveGroup(tree, node)}"
        if (node.description.isNotBlank()) lines += "Descricao: ${node.description}"
        node.unlocksSkillId?.takeIf { it.isNotBlank() }?.let { lines += "Skill: $it" }
        lines += "Efeito: ${effectFormatter.talentNodeEffectSummary(node)}"
        if (check.allowed) {
            lines += "Proximo rank: ${check.nextRank}"
            lines += "Custo: ${check.nextRankCost} ponto(s)"
        } else {
            lines += "Estado: ${talentNodeStateLabel(currentRank, node.maxRank.coerceAtLeast(1), check)}"
            check.reason?.let { lines += "Motivo: $it" }
        }
        return TalentNodeDetailView(
            treeId = tree.id,
            nodeId = node.id,
            title = node.name,
            detailLines = lines,
            canRankUp = check.allowed,
            blockedReason = check.reason,
            nextCost = check.nextRankCost
        )
    }

    private fun buildTalentMenuSlots(
        player: PlayerState,
        trees: List<TalentTree>,
        ledger: TalentPointLedger
    ): List<TalentStageView> {
        val treeByTier = trees.groupBy { it.tier }
        val baseTree = treeByTier[1]?.firstOrNull()
        val subclassTree = treeByTier[2]?.firstOrNull()
        val specializationTree = treeByTier[3]?.firstOrNull()
        return listOf(
            TalentStageView(
                stage = 1,
                label = baseTree?.let { talentTreeDisplayName(player, it) } ?: talentClassFamilyName(player.classId),
                treeId = baseTree?.id,
                spentPoints = baseTree?.let { ledger.spentByTree[it.id] ?: 0 } ?: 0
            ),
            TalentStageView(
                stage = 2,
                label = subclassTree?.let { talentTreeDisplayName(player, it) } ?: "[BLOQUEADO]",
                treeId = subclassTree?.id,
                spentPoints = subclassTree?.let { ledger.spentByTree[it.id] ?: 0 } ?: 0
            ),
            TalentStageView(
                stage = 3,
                label = specializationTree?.let { talentTreeDisplayName(player, it) } ?: "[BLOQUEADO]",
                treeId = specializationTree?.id,
                spentPoints = specializationTree?.let { ledger.spentByTree[it.id] ?: 0 } ?: 0
            )
        )
    }

    private fun talentTreeDisplayName(player: PlayerState, tree: TalentTree): String = when (tree.tier) {
        1 -> talentClassFamilyName(player.classId)
        2 -> engine.classSystem.subclassDef(player.subclassId)?.name ?: tree.id
        3 -> engine.classSystem.specializationDef(player.specializationId)?.name ?: tree.id
        else -> tree.id
    }

    private fun talentClassFamilyName(classId: String): String = when (classId) {
        "swordman" -> "Espadachim"
        "archer" -> "Arqueiro"
        "mage" -> "Mago"
        else -> engine.classSystem.classDef(classId).name
    }

    private fun talentStageLabel(stage: Int): String = when (stage) {
        1 -> "1a Classe"
        2 -> "2a Classe"
        3 -> "Especializacao"
        else -> "${stage}a Classe"
    }

    private fun talentNodeTypeLabel(type: TalentNodeType): String = when (type) {
        TalentNodeType.ACTIVE_SKILL -> "Ativa"
        TalentNodeType.PASSIVE -> "Passiva"
        TalentNodeType.MODIFIER -> "Modificador"
        TalentNodeType.UPGRADE -> "Aprimoramento"
        TalentNodeType.CHOICE -> "Escolha"
    }

    private fun talentNodeStateLabel(currentRank: Int, maxRank: Int, check: TalentRankCheck): String {
        return when {
            currentRank >= maxRank -> "MAX"
            check.allowed -> "Disponivel"
            check.reason == "Pontos de talento insuficientes." -> "Sem pontos"
            else -> "Bloqueada"
        }
    }

    private fun formatTalentPrerequisites(tree: TalentTree, node: TalentNode): String {
        if (node.prerequisites.isEmpty()) return "-"
        val byId = tree.nodes.associateBy { it.id }
        return node.prerequisites.joinToString(", ") { req ->
            val name = byId[req.nodeId]?.name ?: req.nodeId
            "$name NV${req.minRank}"
        }
    }

    private fun formatTalentExclusiveGroup(tree: TalentTree, node: TalentNode): String {
        val group = node.exclusiveGroup?.takeIf { it.isNotBlank() } ?: return "-"
        val peers = tree.nodes
            .filter { it.id != node.id && it.exclusiveGroup == group }
            .map { it.name }
        return if (peers.isEmpty()) "Grupo exclusivo" else peers.joinToString(", ")
    }
}
