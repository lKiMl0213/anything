package rpg.talent

import rpg.model.PlayerState
import rpg.model.TalentRequirementType
import rpg.model.TalentTree

internal class TalentTreeValidationService(
    private val evaluator: TalentTreeRuleEvaluator,
    private val pointService: TalentPointService
) {
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
            if (!evaluator.treeUnlocked(player, owner)) {
                errors += "Tree ${owner.id} bloqueada, mas possui nodes ranqueados."
            }
            if (!evaluator.prerequisitesMet(player, owner, node)) {
                errors += "Node ${node.id}: prerequisitos nao atendidos."
            }
        }

        for (tree in treeList) {
            val groups = mutableMapOf<String, Int>()
            for (node in tree.nodes) {
                val rank = evaluator.nodeCurrentRank(player, node)
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
        if (ledger.mode == rpg.model.TalentPointMode.SHARED_POOL && ledger.sharedAvailable < 0) {
            errors += "Pontos insuficientes no pool compartilhado."
        }
        val invalidTreePools = if (ledger.mode == rpg.model.TalentPointMode.PER_TREE) {
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
}
