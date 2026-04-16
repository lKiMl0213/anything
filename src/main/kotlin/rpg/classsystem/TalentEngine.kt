package rpg.classsystem

import rpg.model.Bonuses
import rpg.model.PlayerState
import rpg.model.TalentNodeDef
import rpg.model.TalentTreeDef

object TalentEngine {
    fun collectTalentBonuses(
        talentTrees: Iterable<TalentTreeDef>,
        unlocked: List<String>
    ): Bonuses {
        val nodes = talentTrees.flatMap { it.nodes }
        val total = nodes.filter { unlocked.contains(it.id) }
            .fold(Bonuses()) { acc, node -> acc + node.bonuses }
        return total
    }

    fun availableTalents(
        player: PlayerState,
        trees: Iterable<TalentTreeDef>
    ): List<TalentNodeDef> {
        return trees.flatMap { it.nodes }
            .filter { canUnlock(player, it) }
    }

    fun canUnlock(player: PlayerState, node: TalentNodeDef): Boolean {
        if (player.talents.contains(node.id)) return false
        if (player.level < node.requiredLevel) return false
        if (player.unspentSkillPoints < node.cost) return false
        if (node.prerequisites.any { !player.talents.contains(it) }) return false
        if (node.classId != null && node.classId != player.classId) return false
        if (node.subclassId != null && node.subclassId != player.subclassId) return false
        return true
    }

    fun unlock(player: PlayerState, node: TalentNodeDef): PlayerState {
        if (!canUnlock(player, node)) return player
        val updated = player.talents.toMutableList()
        updated.add(node.id)
        return player.copy(
            talents = updated,
            unspentSkillPoints = player.unspentSkillPoints - node.cost
        )
    }
}
