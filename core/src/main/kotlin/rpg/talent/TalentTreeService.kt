package rpg.talent

import rpg.model.Bonuses
import rpg.model.PlayerState
import rpg.model.TalentNode
import rpg.model.TalentNodeType
import rpg.model.TalentPointPolicy
import rpg.model.TalentTree

data class TalentValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

data class TalentRankCheck(
    val allowed: Boolean,
    val reason: String? = null,
    val nextRank: Int = 0,
    val nextRankCost: Int = 0
)

data class TalentRankUpResult(
    val success: Boolean,
    val message: String,
    val player: PlayerState
)

class TalentTreeService(
    private val pointPolicy: TalentPointPolicy = TalentPointPolicy()
) {
    private val pointService = TalentPointService(pointPolicy)
    private val evaluator = TalentTreeRuleEvaluator()
    private val validationService = TalentTreeValidationService(
        evaluator = evaluator,
        pointService = pointService
    )

    fun activeTrees(player: PlayerState, trees: Iterable<TalentTree>): List<TalentTree> {
        return trees
            .filter { treeUnlocked(player, it) }
            .sortedWith(compareBy<TalentTree> { it.tier }.thenBy { it.id })
    }

    fun treeUnlocked(player: PlayerState, tree: TalentTree): Boolean {
        return evaluator.treeUnlocked(player, tree)
    }

    fun requirementsMet(player: PlayerState, requirements: List<rpg.model.TalentRequirement>): Boolean {
        return evaluator.requirementsMet(player, requirements)
    }

    fun nodeCurrentRank(player: PlayerState, node: TalentNode): Int {
        return evaluator.nodeCurrentRank(player, node)
    }

    fun nodeById(tree: TalentTree, nodeId: String): TalentNode? = tree.nodes.firstOrNull { it.id == nodeId }

    fun canRankUp(
        player: PlayerState,
        tree: TalentTree,
        nodeId: String,
        allTrees: Iterable<TalentTree> = listOf(tree)
    ): TalentRankCheck {
        if (!treeUnlocked(player, tree)) {
            return TalentRankCheck(false, "Arvore bloqueada.", 0, 0)
        }
        val node = nodeById(tree, nodeId) ?: return TalentRankCheck(false, "Node nao encontrado.", 0, 0)
        val current = nodeCurrentRank(player, node)
        if (current >= node.maxRank.coerceAtLeast(1)) {
            return TalentRankCheck(false, "Rank maximo atingido.", current, 0)
        }
        val nextRank = current + 1
        val nextCost = evaluator.rankCost(node, nextRank)
        if (nextCost <= 0) {
            return TalentRankCheck(false, "Custo invalido para o proximo rank.", nextRank, nextCost)
        }
        val ledger = pointService.ledger(player, allTrees)
        if (!pointService.canSpend(tree.id, nextCost, ledger)) {
            return TalentRankCheck(false, "Pontos de talento insuficientes.", nextRank, nextCost)
        }
        if (!evaluator.prerequisitesMet(player, tree, node)) {
            return TalentRankCheck(false, "Prerequisitos nao atendidos.", nextRank, nextCost)
        }
        if (!evaluator.exclusiveGroupAllows(player, tree, node)) {
            return TalentRankCheck(false, "Conflito de exclusividade.", nextRank, nextCost)
        }
        return TalentRankCheck(true, null, nextRank, nextCost)
    }

    fun rankUp(
        player: PlayerState,
        tree: TalentTree,
        nodeId: String,
        allTrees: Iterable<TalentTree> = listOf(tree)
    ): TalentRankUpResult {
        val check = canRankUp(player, tree, nodeId, allTrees)
        if (!check.allowed) {
            return TalentRankUpResult(
                success = false,
                message = check.reason ?: "Nao foi possivel evoluir node.",
                player = player
            )
        }
        val node = nodeById(tree, nodeId) ?: return TalentRankUpResult(false, "Node nao encontrado.", player)
        val current = nodeCurrentRank(player, node)
        val next = current + 1
        val updatedRanks = player.talentNodeRanks.toMutableMap()
        updatedRanks[node.id] = next

        val updatedUnlockedTrees = if (player.unlockedTalentTrees.contains(tree.id)) {
            player.unlockedTalentTrees
        } else {
            player.unlockedTalentTrees + tree.id
        }

        val updated = player.copy(
            talentNodeRanks = updatedRanks,
            unlockedTalentTrees = updatedUnlockedTrees
        )
        val buildValidation = validateBuild(updated, allTrees)
        if (!buildValidation.valid) {
            return TalentRankUpResult(
                success = false,
                message = buildValidation.errors.joinToString(" | "),
                player = player
            )
        }
        return TalentRankUpResult(
            success = true,
            message = "Node ${node.name} evoluiu para rank $next.",
            player = updated
        )
    }

    fun collectBonuses(player: PlayerState, trees: Iterable<TalentTree>): Bonuses {
        var total = Bonuses()
        for (tree in activeTrees(player, trees)) {
            for (node in tree.nodes) {
                if (node.nodeType == TalentNodeType.ACTIVE_SKILL) continue
                val rank = nodeCurrentRank(player, node).coerceAtMost(node.maxRank.coerceAtLeast(1))
                if (rank <= 0) continue
                total += scale(node.modifiers.bonuses, rank)
            }
        }
        return total
    }

    fun pointsLedger(player: PlayerState, trees: Iterable<TalentTree>): TalentPointLedger {
        return pointService.ledger(player, trees)
    }

    fun spentPoints(player: PlayerState, trees: Iterable<TalentTree>): Int {
        return pointService.spentPoints(pointService.ledger(player, trees))
    }

    fun spentPointsByTree(player: PlayerState, trees: Iterable<TalentTree>): Map<String, Int> {
        return pointService.spentPointsByTree(pointService.ledger(player, trees))
    }

    fun resetPreview(player: PlayerState, trees: Iterable<TalentTree>): TalentResetPreview {
        return pointService.resetPreview(pointService.ledger(player, trees))
    }

    fun buildResetState(
        player: PlayerState,
        trees: Iterable<TalentTree>,
        treeIds: Set<String>? = null
    ): PlayerState {
        val selected = if (treeIds.isNullOrEmpty()) {
            trees.toList()
        } else {
            trees.filter { treeIds.contains(it.id) }.toList()
        }
        if (selected.isEmpty()) return player

        val nodeIdsToClear = selected.flatMap { it.nodes }.map { it.id }.toSet()
        val updatedRanks = player.talentNodeRanks.toMutableMap().apply {
            keys.filter { nodeIdsToClear.contains(it) }.forEach { remove(it) }
        }
        return player.copy(talentNodeRanks = updatedRanks)
    }

    fun validateBuild(player: PlayerState, trees: Iterable<TalentTree>): TalentValidationResult {
        return validationService.validateBuild(player, trees)
    }

    fun validateTrees(trees: Iterable<TalentTree>): TalentValidationResult {
        return validationService.validateTrees(trees)
    }

    private fun scale(bonuses: Bonuses, rank: Int): Bonuses {
        val factor = rank.toDouble().coerceAtLeast(0.0)
        if (factor <= 0.0) return Bonuses()
        return Bonuses(
            attributes = bonuses.attributes.scale(factor),
            derivedAdd = bonuses.derivedAdd.scale(factor),
            derivedMult = bonuses.derivedMult.scale(factor)
        )
    }
}
