package rpg.talent

import rpg.model.PlayerState
import rpg.model.TalentNode
import rpg.model.TalentNodeType
import rpg.model.TalentPointMode
import rpg.model.TalentPointPolicy
import rpg.model.TalentTree

data class TalentPointLedger(
    val mode: TalentPointMode,
    val sharedEarned: Int,
    val sharedSpent: Int,
    val sharedAvailable: Int,
    val earnedByTree: Map<String, Int>,
    val spentByTree: Map<String, Int>,
    val availableByTree: Map<String, Int>
)

data class TalentResetPreview(
    val mode: TalentPointMode,
    val refundableShared: Int,
    val refundableByTree: Map<String, Int>,
    val totalRefundable: Int
)

class TalentPointService(private val policy: TalentPointPolicy) {
    fun ledger(player: PlayerState, trees: Iterable<TalentTree>): TalentPointLedger {
        val normalizedTrees = trees.associateBy { it.id }
        val gainedLevels = (player.level - 1).coerceAtLeast(0)

        val earnedByTree = normalizedTrees.values.associate { tree ->
            val perLevel = policy.perTreePointsPerLevel[tree.id] ?: policy.perTreePointsPerLevelDefault
            val start = policy.perTreeStartingPoints[tree.id] ?: 0
            tree.id to (start + gainedLevels * perLevel).coerceAtLeast(0)
        }

        val spentByTree = mutableMapOf<String, Int>().withDefault { 0 }
        var totalSpent = 0
        for (tree in normalizedTrees.values) {
            for (node in tree.nodes) {
                val rank = nodeRank(player, node).coerceAtMost(node.maxRank.coerceAtLeast(1))
                if (rank <= 0) continue
                val spent = spentCost(node, rank)
                spentByTree[tree.id] = spentByTree.getValue(tree.id) + spent
                totalSpent += spent
            }
        }

        val sharedEarned = (policy.sharedStartingPoints + gainedLevels * policy.sharedPointsPerLevel).coerceAtLeast(0)
        val availableByTree = earnedByTree.mapValues { (treeId, earned) ->
            val spent = spentByTree[treeId] ?: 0
            earned - spent
        }
        val sharedAvailable = sharedEarned - totalSpent

        return TalentPointLedger(
            mode = policy.mode,
            sharedEarned = sharedEarned,
            sharedSpent = totalSpent,
            sharedAvailable = sharedAvailable,
            earnedByTree = earnedByTree,
            spentByTree = spentByTree,
            availableByTree = availableByTree
        )
    }

    fun canSpend(treeId: String, cost: Int, ledger: TalentPointLedger): Boolean {
        if (cost <= 0) return false
        return when (ledger.mode) {
            TalentPointMode.SHARED_POOL -> ledger.sharedAvailable >= cost
            TalentPointMode.PER_TREE -> (ledger.availableByTree[treeId] ?: 0) >= cost
        }
    }

    fun spentPoints(ledger: TalentPointLedger): Int = ledger.sharedSpent

    fun spentPointsByTree(ledger: TalentPointLedger): Map<String, Int> = ledger.spentByTree

    fun resetPreview(ledger: TalentPointLedger): TalentResetPreview {
        return TalentResetPreview(
            mode = ledger.mode,
            refundableShared = ledger.sharedSpent,
            refundableByTree = ledger.spentByTree,
            totalRefundable = ledger.sharedSpent
        )
    }

    private fun nodeRank(player: PlayerState, node: TalentNode): Int {
        val baseline = defaultBaselineRank(node)
        return (player.talentNodeRanks[node.id] ?: 0).coerceAtLeast(baseline).coerceAtLeast(0)
    }

    private fun spentCost(node: TalentNode, rank: Int): Int {
        if (rank <= 0) return 0
        val maxRank = node.maxRank.coerceAtLeast(1)
        val baseline = defaultBaselineRank(node).coerceIn(0, maxRank)
        if (rank <= baseline) return 0

        var total = 0
        for (i in (baseline + 1)..rank) {
            total += rankCost(node, i)
        }
        return total
    }

    private fun rankCost(node: TalentNode, rank: Int): Int {
        if (rank <= 0) return 0
        val index = (rank - 1).coerceAtLeast(0)
        return if (index < node.costPerRank.size) {
            node.costPerRank[index]
        } else {
            node.costPerRank.lastOrNull() ?: 0
        }.coerceAtLeast(0)
    }

    private fun defaultBaselineRank(node: TalentNode): Int {
        if (node.nodeType == TalentNodeType.ACTIVE_SKILL) return 0
        return node.currentRank.coerceAtLeast(0)
    }
}
