package rpg.talent

import rpg.model.Bonuses
import rpg.model.PlayerState
import rpg.model.TalentNode
import rpg.model.TalentPointPolicy
import rpg.model.TalentPointMode
import rpg.model.TalentNodeType
import rpg.model.TalentRequirement
import rpg.model.TalentRequirementType
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

    fun activeTrees(player: PlayerState, trees: Iterable<TalentTree>): List<TalentTree> {
        return trees
            .filter { treeUnlocked(player, it) }
            .sortedWith(compareBy<TalentTree> { it.tier }.thenBy { it.id })
    }

    fun treeUnlocked(player: PlayerState, tree: TalentTree): Boolean {
        if (player.level < tree.unlockLevel) return false
        if (tree.classId.isNotBlank() && tree.classId != player.classId) return false
        return requirementsMet(player, tree.requirements)
    }

    fun requirementsMet(player: PlayerState, requirements: List<TalentRequirement>): Boolean {
        return requirements.all { requirementMet(player, it) }
    }

    fun nodeCurrentRank(player: PlayerState, node: TalentNode): Int {
        val baseline = defaultBaselineRank(node)
        return (player.talentNodeRanks[node.id] ?: 0).coerceAtLeast(baseline).coerceAtLeast(0)
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
        val nextCost = rankCost(node, nextRank)
        if (nextCost <= 0) {
            return TalentRankCheck(false, "Custo invalido para o proximo rank.", nextRank, nextCost)
        }
        val ledger = pointService.ledger(player, allTrees)
        if (!pointService.canSpend(tree.id, nextCost, ledger)) {
            return TalentRankCheck(false, "Pontos de talento insuficientes.", nextRank, nextCost)
        }
        if (!prerequisitesMet(player, tree, node)) {
            return TalentRankCheck(false, "Prerequisitos nao atendidos.", nextRank, nextCost)
        }
        if (!exclusiveGroupAllows(player, tree, node)) {
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
        val treeList = trees.toList()
        val errors = mutableListOf<String>()
        val treeById = treeList.associateBy { it.id }

        for ((nodeId, rank) in player.talentNodeRanks) {
            if (rank <= 0) continue
            val owner = treeList.firstOrNull { tree -> tree.nodes.any { it.id == nodeId } }
            if (owner == null) {
                errors += "Node ranqueado inexistente no conjunto atual: $nodeId."
                continue
            }
            val node = owner.nodes.first { it.id == nodeId }
            if (rank > node.maxRank.coerceAtLeast(1)) {
                errors += "Node ${node.id}: rank $rank acima do maximo ${node.maxRank}."
            }
            if (!treeUnlocked(player, owner)) {
                errors += "Tree ${owner.id} bloqueada, mas possui nodes ranqueados."
            }
            if (!prerequisitesMet(player, owner, node)) {
                errors += "Node ${node.id}: prerequisitos nao atendidos."
            }
        }

        for (tree in treeList) {
            val groups = mutableMapOf<String, Int>()
            for (node in tree.nodes) {
                val rank = nodeCurrentRank(player, node)
                if (rank <= 0) continue
                val group = node.exclusiveGroup?.trim().orEmpty()
                if (group.isBlank()) continue
                groups[group] = (groups[group] ?: 0) + 1
            }
            for ((group, count) in groups) {
                if (count > 1) {
                    errors += "Tree ${tree.id}: grupo exclusivo '$group' com $count nodes ativos."
                }
            }
            for (req in tree.requirements) {
                if (req.type == TalentRequirementType.TREE_UNLOCKED &&
                    req.targetId.isNotBlank() &&
                    !treeById.containsKey(req.targetId)
                ) {
                    errors += "Tree ${tree.id}: requirement TREE_UNLOCKED aponta para tree ausente ${req.targetId}."
                }
            }
        }

        val ledger = pointService.ledger(player, treeList)
        if (ledger.mode == TalentPointMode.SHARED_POOL && ledger.sharedAvailable < 0) {
            errors += "Pontos insuficientes no pool compartilhado."
        }
        val invalidTreePools = if (ledger.mode == TalentPointMode.PER_TREE) {
            ledger.availableByTree.filterValues { it < 0 }
        } else {
            emptyMap()
        }
        for ((treeId, available) in invalidTreePools) {
            errors += "Tree $treeId com saldo de pontos negativo ($available)."
        }

        return TalentValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    fun validateTrees(trees: Iterable<TalentTree>): TalentValidationResult {
        val errors = mutableListOf<String>()
        val byTree = trees.associateBy { it.id }
        val globalNodeIds = mutableSetOf<String>()

        for (tree in byTree.values) {
            if (tree.id.isBlank()) errors += "Tree com id vazio."
            if (tree.classId.isBlank()) errors += "Tree ${tree.id}: classId vazio."
            if (tree.unlockLevel < 1) errors += "Tree ${tree.id}: unlockLevel deve ser >= 1."

            val byNode = tree.nodes.associateBy { it.id }
            for (node in tree.nodes) {
                if (node.id.isBlank()) {
                    errors += "Tree ${tree.id}: node com id vazio."
                    continue
                }
                if (!globalNodeIds.add(node.id)) {
                    errors += "Node id duplicado entre arvores: ${node.id}"
                }
                if (node.maxRank < 1) {
                    errors += "Tree ${tree.id} node ${node.id}: maxRank deve ser >= 1."
                }
                if (node.costPerRank.isEmpty()) {
                    errors += "Tree ${tree.id} node ${node.id}: costPerRank vazio."
                }
                if (node.costPerRank.any { it <= 0 }) {
                    errors += "Tree ${tree.id} node ${node.id}: costPerRank deve ter custos > 0."
                }
                if (node.currentRank < 0 || node.currentRank > node.maxRank.coerceAtLeast(1)) {
                    errors += "Tree ${tree.id} node ${node.id}: currentRank fora do intervalo."
                }
                for (prereq in node.prerequisites) {
                    if (!byNode.containsKey(prereq.nodeId)) {
                        errors += "Tree ${tree.id} node ${node.id}: prerequisito inexistente ${prereq.nodeId}."
                    }
                    if (prereq.minRank < 1) {
                        errors += "Tree ${tree.id} node ${node.id}: prerequisito ${prereq.nodeId} minRank invalido."
                    }
                }
            }
            for (req in tree.requirements) {
                if (req.type == TalentRequirementType.TREE_UNLOCKED && !byTree.containsKey(req.targetId)) {
                    errors += "Tree ${tree.id}: requirement TREE_UNLOCKED aponta para tree inexistente ${req.targetId}."
                }
            }
            errors += detectCycles(tree)
        }

        return TalentValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    private fun detectCycles(tree: TalentTree): List<String> {
        val nodes = tree.nodes.associateBy { it.id }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val errors = mutableListOf<String>()

        fun visit(nodeId: String) {
            if (nodeId in visited) return
            if (!visiting.add(nodeId)) {
                errors += "Tree ${tree.id}: ciclo detectado envolvendo $nodeId."
                return
            }
            val node = nodes[nodeId]
            if (node != null) {
                for (pr in node.prerequisites) {
                    if (nodes.containsKey(pr.nodeId)) {
                        visit(pr.nodeId)
                    }
                }
            }
            visiting.remove(nodeId)
            visited.add(nodeId)
        }

        for (node in tree.nodes) {
            visit(node.id)
        }
        return errors
    }

    private fun prerequisitesMet(player: PlayerState, tree: TalentTree, node: TalentNode): Boolean {
        val byNode = tree.nodes.associateBy { it.id }
        for (prereq in node.prerequisites) {
            val prerequisiteNode = byNode[prereq.nodeId] ?: return false
            val rank = nodeCurrentRank(player, prerequisiteNode)
            if (rank < prereq.minRank.coerceAtLeast(1)) return false
        }
        return true
    }

    private fun exclusiveGroupAllows(player: PlayerState, tree: TalentTree, node: TalentNode): Boolean {
        val group = node.exclusiveGroup?.trim().orEmpty()
        if (group.isBlank()) return true
        for (candidate in tree.nodes) {
            if (candidate.id == node.id) continue
            if (candidate.exclusiveGroup?.trim() != group) continue
            if (nodeCurrentRank(player, candidate) > 0) return false
        }
        return true
    }

    private fun rankCost(node: TalentNode, nextRank: Int): Int {
        if (nextRank <= 0) return 0
        val index = (nextRank - 1).coerceAtLeast(0)
        return if (index < node.costPerRank.size) {
            node.costPerRank[index]
        } else {
            node.costPerRank.lastOrNull() ?: 0
        }
    }

    private fun requirementMet(player: PlayerState, requirement: TalentRequirement): Boolean {
        return when (requirement.type) {
            TalentRequirementType.PLAYER_LEVEL -> {
                player.level >= requirement.minValue.coerceAtLeast(1)
            }

            TalentRequirementType.CLASS_ID -> {
                requirement.expectedValue.isNotBlank() && player.classId == requirement.expectedValue
            }

            TalentRequirementType.SUBCLASS_ID -> {
                requirement.expectedValue.isNotBlank() && player.subclassId == requirement.expectedValue
            }

            TalentRequirementType.SPECIALIZATION_ID -> {
                requirement.expectedValue.isNotBlank() && player.specializationId == requirement.expectedValue
            }

            TalentRequirementType.TREE_UNLOCKED -> {
                requirement.targetId.isNotBlank() && player.unlockedTalentTrees.contains(requirement.targetId)
            }

            TalentRequirementType.NODE_RANK_AT_LEAST -> {
                if (requirement.targetId.isBlank()) return false
                val current = player.talentNodeRanks[requirement.targetId] ?: 0
                current >= requirement.minValue.coerceAtLeast(1)
            }
        }
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

    private fun defaultBaselineRank(node: TalentNode): Int {
        // Active skills should never be granted "for free" by data baseline rank.
        // They must come from explicit player progression (talentNodeRanks).
        if (node.nodeType == TalentNodeType.ACTIVE_SKILL) return 0
        return node.currentRank.coerceAtLeast(0)
    }
}
