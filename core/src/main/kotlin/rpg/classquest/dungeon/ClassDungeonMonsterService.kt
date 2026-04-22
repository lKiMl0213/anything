package rpg.classquest.dungeon

import rpg.classquest.ClassQuestDungeonDefinition
import rpg.classquest.ClassQuestUnlockType
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.monster.MonsterInstance

internal class ClassDungeonMonsterService(
    private val repo: DataRepository,
    private val engine: GameEngine
) {
    fun generate(
        dungeon: ClassQuestDungeonDefinition?,
        tier: MapTierDef,
        run: rpg.model.DungeonRun,
        player: PlayerState,
        isBoss: Boolean
    ): MonsterInstance {
        if (dungeon == null) {
            return engine.generateMonster(tier, run, player, isBoss)
        }

        val context = engine.classQuestService.currentContext(player)
        val shouldSpawnFinal = if (context != null && context.progress.chosenPath == dungeon.pathId) {
            engine.classQuestService.shouldSpawnFinalBoss(context)
        } else {
            false
        }
        val normalPool = if (dungeon.normalMonsters.isNotEmpty()) dungeon.normalMonsters else listOf(dungeon.finalBoss)
        val bossPool = if (dungeon.bossMonsters.isNotEmpty()) dungeon.bossMonsters else listOf(dungeon.finalBoss)
        val blueprint = when {
            !isBoss -> normalPool[engine.rollInt(normalPool.size)]
            shouldSpawnFinal -> dungeon.finalBoss
            bossPool.isNotEmpty() -> bossPool[engine.rollInt(bossPool.size)]
            else -> dungeon.finalBoss
        }

        val hasTemplate = repo.monsterArchetypes.containsKey(blueprint.baseArchetypeId)
        val templateId = if (hasTemplate) blueprint.baseArchetypeId else repo.monsterArchetypes.keys.first()
        val classTier = adjustedTierForClassDungeon(tier, dungeon.unlockType, isBoss)
            .copy(allowedMonsterTemplates = listOf(templateId))
        val generated = engine.generateMonster(classTier, run, player, isBoss)
        val scaledXp = when (dungeon.unlockType) {
            ClassQuestUnlockType.SUBCLASS -> if (isBoss) 1.25 else 1.15
            ClassQuestUnlockType.SPECIALIZATION -> if (isBoss) 1.45 else 1.30
        }
        val scaledGold = when (dungeon.unlockType) {
            ClassQuestUnlockType.SUBCLASS -> if (isBoss) 1.20 else 1.10
            ClassQuestUnlockType.SPECIALIZATION -> if (isBoss) 1.35 else 1.20
        }
        val bonusTags = setOf(
            "classquest",
            "classquest:${dungeon.unlockType.name.lowercase()}",
            "path:${dungeon.pathId}",
            "base:${blueprint.baseType}",
            "family:${blueprint.family}",
            "type:${blueprint.family}"
        ) + blueprint.identityTags
        val questTags = generated.questTags + bonusTags
        val lootProfileId = blueprint.lootProfileId.ifBlank { generated.lootProfileId }
        return generated.copy(
            archetypeId = blueprint.monsterId,
            id = blueprint.monsterId,
            sourceArchetypeId = generated.sourceArchetypeId,
            baseType = blueprint.baseType,
            monsterTypeId = blueprint.family,
            family = blueprint.family,
            name = blueprint.displayName,
            displayName = blueprint.displayName,
            variantName = "",
            tags = generated.tags + bonusTags,
            questTags = questTags,
            dropTableId = lootProfileId,
            lootProfileId = lootProfileId,
            baseXp = (generated.baseXp * scaledXp).toInt().coerceAtLeast(1),
            baseGold = (generated.baseGold * scaledGold).toInt().coerceAtLeast(1)
        )
    }

    private fun adjustedTierForClassDungeon(
        tier: MapTierDef,
        unlockType: ClassQuestUnlockType,
        isBoss: Boolean
    ): MapTierDef {
        val levelBoost = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> if (isBoss) 3 else 2
            ClassQuestUnlockType.SPECIALIZATION -> if (isBoss) 8 else 5
        }
        val difficultyBoost = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> if (isBoss) 1.12 else 1.08
            ClassQuestUnlockType.SPECIALIZATION -> if (isBoss) 1.35 else 1.22
        }
        return tier.copy(
            baseMonsterLevel = (tier.baseMonsterLevel + levelBoost).coerceAtLeast(1),
            difficultyMultiplier = (tier.difficultyMultiplier * difficultyBoost).coerceAtLeast(1.0)
        )
    }
}
