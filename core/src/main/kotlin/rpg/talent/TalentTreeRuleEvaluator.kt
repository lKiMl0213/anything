package rpg.talent

import rpg.model.PlayerState
import rpg.model.TalentNode
import rpg.model.TalentNodeType
import rpg.model.TalentRequirement
import rpg.model.TalentRequirementType
import rpg.model.TalentTree

internal class TalentTreeRuleEvaluator {
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

    fun prerequisitesMet(player: PlayerState, tree: TalentTree, node: TalentNode): Boolean {
        val byNode = tree.nodes.associateBy { it.id }
        for (prereq in node.prerequisites) {
            val prerequisiteNode = byNode[prereq.nodeId] ?: return false
            val rank = nodeCurrentRank(player, prerequisiteNode)
            if (rank < prereq.minRank.coerceAtLeast(1)) return false
        }
        return true
    }

    fun exclusiveGroupAllows(player: PlayerState, tree: TalentTree, node: TalentNode): Boolean {
        val group = node.exclusiveGroup?.trim().orEmpty()
        if (group.isBlank()) return true
        for (candidate in tree.nodes) {
            if (candidate.id == node.id) continue
            if (candidate.exclusiveGroup?.trim() != group) continue
            if (nodeCurrentRank(player, candidate) > 0) return false
        }
        return true
    }

    fun rankCost(node: TalentNode, nextRank: Int): Int {
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

    private fun defaultBaselineRank(node: TalentNode): Int {
        // Active skills should never be granted "for free" by data baseline rank.
        // They must come from explicit player progression (talentNodeRanks).
        if (node.nodeType == TalentNodeType.ACTIVE_SKILL) return 0
        return node.currentRank.coerceAtLeast(0)
    }
}
