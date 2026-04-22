package rpg.quest.progress

import rpg.engine.GameEngine
import rpg.monster.MonsterInstance
import rpg.monster.MonsterRarity
import rpg.cli.model.BattleOutcome

internal class CombatQuestProgressService(
    private val engine: GameEngine
) {
    fun applyBattleQuestProgress(
        board: rpg.quest.QuestBoardState,
        monster: MonsterInstance,
        outcome: BattleOutcome,
        isBoss: Boolean
    ): rpg.quest.QuestBoardState {
        if (!outcome.victory) return board
        val tags = monster.tags.mapTo(mutableSetOf()) { it.lowercase() }
        tags.add(monster.baseType.lowercase())
        tags.add("base:${monster.baseType.lowercase()}")
        tags.add(monster.family.lowercase())
        tags.add("family:${monster.family.lowercase()}")
        tags.addAll(monster.questTags.map { it.lowercase() })
        tags.add(monster.rarity.name.lowercase())
        if (monster.rarity.ordinal >= MonsterRarity.ELITE.ordinal) {
            tags.add("elite")
        }
        if (isBoss) {
            tags.add("boss")
            tags.add("elite")
        }
        var updated = engine.questProgressTracker.onMonsterKilled(
            board = board,
            monsterId = monster.archetypeId,
            monsterBaseType = monster.baseType,
            monsterTags = tags,
            amount = 1
        )
        for ((itemId, qty) in outcome.collectedItems) {
            updated = engine.questProgressTracker.onItemCollected(
                board = updated,
                itemId = itemId,
                quantity = qty
            )
        }
        return updated
    }
}
