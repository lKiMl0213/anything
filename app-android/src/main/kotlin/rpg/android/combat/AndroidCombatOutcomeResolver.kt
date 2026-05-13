package rpg.android.combat

import rpg.achievement.AchievementCounterKeys
import rpg.application.CombatFlowResult
import rpg.application.PendingEncounter
import rpg.combat.CombatResult
import rpg.combat.CombatTelemetry
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.monster.MonsterInstance
import rpg.monster.MonsterRarity
import rpg.navigation.NavigationState
import rpg.world.RunRoomType

internal class AndroidCombatOutcomeResolver(
    private val engine: GameEngine,
    private val applyBattleResolvedAchievement: (
        player: PlayerState,
        telemetry: CombatTelemetry,
        victory: Boolean,
        escaped: Boolean,
        isBoss: Boolean,
        monsterTypeId: String,
        monsterStars: Int
    ) -> PlayerState,
    private val applyGoldEarnedAchievement: (player: PlayerState, gold: Long) -> PlayerState,
    private val applyDeathAchievement: (player: PlayerState) -> PlayerState
) {
    fun resolve(
        gameState: GameState,
        encounter: PendingEncounter,
        result: CombatResult,
        combatLog: List<String>
    ): CombatFlowResult {
        var playerAfterCombat = applyBattleResolvedAchievement(
            result.playerAfter,
            result.telemetry,
            result.victory,
            result.escaped,
            encounter.isBoss,
            encounter.monster.monsterTypeId.ifBlank { encounter.monster.baseType },
            encounter.monster.stars
        )
        if (!result.victory && !result.escaped) {
            playerAfterCombat = applyDeathAchievement(playerAfterCombat)
            playerAfterCombat = applyDungeonDeathDebuff(playerAfterCombat)
        }

        var updatedState = gameState.copy(
            player = playerAfterCombat,
            itemInstances = result.itemInstances
        )

        return when {
            result.escaped -> CombatFlowResult(
                gameState = updatedState.copy(currentRun = null),
                navigation = NavigationState.Exploration,
                messages = listOf("Você fugiu do combate.") + combatLog
            )

            !result.victory -> CombatFlowResult(
                gameState = updatedState.copy(currentRun = null),
                navigation = NavigationState.Hub,
                messages = listOf(
                    "Você foi derrotado.",
                    "A expedicao terminou e você retornou ao acampamento."
                ) + combatLog
            )

            else -> {
                val levelBefore = playerAfterCombat.level
                val victory = engine.resolveVictory(
                    player = playerAfterCombat,
                    itemInstances = result.itemInstances,
                    monster = encounter.monster,
                    tier = encounter.tier,
                    collectToLoot = false
                )
                val playerWithGoldAchievement = applyGoldEarnedAchievement(victory.player, victory.goldGain.toLong())
                val advancedRun = engine.advanceRun(
                    run = encounter.run,
                    bossDefeated = encounter.roomType == RunRoomType.BOSS && encounter.isBoss,
                    clearedRoomType = encounter.roomType,
                    victoryInRoom = true
                )
                val classRewards = applyClassDungeonCombatRewards(
                    encounter = encounter,
                    player = playerWithGoldAchievement,
                    itemInstances = victory.itemInstances
                )
                val playerWithClassGold = if (classRewards.bonusGold > 0) {
                    applyGoldEarnedAchievement(classRewards.player, classRewards.bonusGold.toLong())
                } else {
                    classRewards.player
                }
                val playerWithTowerProgress = if (encounter.tier.isInfinite) {
                    updateInfiniteHighestFloorCounter(playerWithClassGold, advancedRun.depth)
                } else {
                    playerWithClassGold
                }

                var updatedBoard = applyBattleQuestProgress(
                    board = gameState.questBoard,
                    monster = encounter.monster,
                    collectedItems = buildCollectedItems(victory.dropOutcome.itemInstance, victory.dropOutcome.itemId, victory.dropOutcome.quantity),
                    isBoss = encounter.isBoss
                )
                updatedBoard = engine.questProgressTracker.onFloorReached(updatedBoard, advancedRun.depth)
                if (!runCountsAsCompleted(encounter.run) && runCountsAsCompleted(advancedRun)) {
                    updatedBoard = engine.questProgressTracker.onRunCompleted(updatedBoard, 1)
                }

                updatedState = updatedState.copy(
                    player = playerWithTowerProgress,
                    itemInstances = classRewards.itemInstances,
                    currentRun = advancedRun,
                    questBoard = updatedBoard
                )
                val rewardLines = mutableListOf(
                    "${engine.monsterDisplayName(encounter.monster)} foi derrotado!",
                    "Ganhou ${victory.xpGain} XP e ${victory.goldGain} ouro."
                )
                if (playerWithTowerProgress.level > levelBefore) {
                    rewardLines += "Level up! Agora você está no nível ${playerWithTowerProgress.level}."
                }
                if (encounter.tier.isInfinite) {
                    rewardLines += "Torre infinita: novo andar alcancado ${advancedRun.depth}."
                }
                victory.dropOutcome.itemInstance?.let { rewardLines += "Drop: ${it.name}." }
                if (victory.dropOutcome.itemInstance == null && victory.dropOutcome.itemId != null) {
                    rewardLines += "Drop: ${victory.dropOutcome.itemId} x${victory.dropOutcome.quantity.coerceAtLeast(1)}."
                }
                rewardLines += classRewards.messages
                CombatFlowResult(
                    gameState = updatedState,
                    navigation = NavigationState.Exploration,
                    messages = rewardLines + combatLog.takeLast(3)
                )
            }
        }
    }

    private fun applyBattleQuestProgress(
        board: rpg.quest.QuestBoardState,
        monster: MonsterInstance,
        collectedItems: Map<String, Int>,
        isBoss: Boolean
    ): rpg.quest.QuestBoardState {
        val tags = monster.tags.mapTo(mutableSetOf()) { it.lowercase() }
        tags.add(monster.baseType.lowercase())
        tags.add("base:${monster.baseType.lowercase()}")
        tags.add(monster.family.lowercase())
        tags.add("family:${monster.family.lowercase()}")
        tags.addAll(monster.questTags.map { it.lowercase() })
        tags.add(monster.rarity.name.lowercase())
        if (monster.rarity.ordinal >= MonsterRarity.ELITE.ordinal) tags.add("elite")
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
        collectedItems.forEach { (itemId, qty) ->
            updated = engine.questProgressTracker.onItemCollected(updated, itemId, qty)
        }
        return updated
    }

    private fun buildCollectedItems(
        droppedInstance: ItemInstance?,
        droppedItemId: String?,
        quantity: Int
    ): Map<String, Int> {
        return when {
            droppedInstance != null -> mapOf(droppedInstance.templateId to 1)
            droppedItemId != null -> mapOf(droppedItemId to quantity.coerceAtLeast(1))
            else -> emptyMap()
        }
    }

    private fun runCountsAsCompleted(run: rpg.model.DungeonRun): Boolean {
        return run.victoriesInRun >= 10 && run.bossesDefeatedInRun >= 1 && run.depth >= 10
    }

    private fun applyDungeonDeathDebuff(player: PlayerState): PlayerState {
        val nextStacks = if (player.deathDebuffMinutes > 0.0) player.deathDebuffStacks + 1 else 1
        val durationMinutes = 10.0 + (nextStacks - 1) * 5.0
        return player.copy(
            currentHp = 1.0,
            deathDebuffStacks = nextStacks,
            deathDebuffMinutes = durationMinutes,
            deathXpPenaltyPct = 20.0,
            deathXpPenaltyMinutes = durationMinutes
        )
    }

    private fun updateInfiniteHighestFloorCounter(player: PlayerState, floor: Int): PlayerState {
        if (floor <= 0) return player
        val key = AchievementCounterKeys.customCounterKey(
            AchievementCounterKeys.Dungeon.NAMESPACE,
            AchievementCounterKeys.Dungeon.INFINITE_HIGHEST_FLOOR
        )
        val current = player.lifetimeStats.customCounters[key] ?: 0L
        val next = maxOf(current, floor.toLong())
        if (next <= current) return player
        return player.copy(
            lifetimeStats = player.lifetimeStats.copy(
                customCounters = player.lifetimeStats.customCounters + (key to next)
            )
        )
    }

    private fun applyClassDungeonCombatRewards(
        encounter: PendingEncounter,
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): ClassDungeonCombatResult {
        if (encounter.classDungeon == null) {
            return ClassDungeonCombatResult(player, itemInstances, emptyList(), bonusGold = 0)
        }
        var workingPlayer = player
        var workingInstances = itemInstances
        var bonusGold = 0
        val messages = mutableListOf<String>()
        val collectibleDrops = engine.classQuestService.collectibleDropsForDungeonKill(
            player = workingPlayer,
            monsterId = encounter.monster.archetypeId,
            isBoss = encounter.isBoss
        )
        if (collectibleDrops.isNotEmpty()) {
            val mergedInstances = workingInstances.toMutableMap()
            collectibleDrops.forEach { drop -> mergedInstances[drop.id] = drop }
            workingInstances = mergedInstances
            val insert = InventorySystem.addItemsWithLimit(
                player = workingPlayer,
                itemInstances = workingInstances,
                itemRegistry = engine.itemRegistry,
                incomingItemIds = collectibleDrops.map { it.id }
            )
            val rejectedGenerated = insert.rejected.filter { workingInstances.containsKey(it) }
            if (rejectedGenerated.isNotEmpty()) {
                workingInstances = workingInstances - rejectedGenerated.toSet()
                messages += "Inventário cheio: parte dos drops exclusivos da instancia foi perdida."
            }
            workingPlayer = workingPlayer.copy(
                inventory = insert.inventory,
                quiverInventory = insert.quiverInventory,
                selectedAmmoTemplateId = insert.selectedAmmoTemplateId
            )
            val acceptedSet = insert.accepted.toSet()
            val collectedItems = collectibleDrops
                .filter { it.id in acceptedSet }
                .groupingBy { it.templateId }
                .eachCount()
            if (collectedItems.isNotEmpty()) {
                val collectUpdate = engine.classQuestService.onItemsCollected(
                    player = workingPlayer,
                    itemInstances = workingInstances,
                    collectedItems = collectedItems
                )
                val goldDelta = (collectUpdate.player.gold - workingPlayer.gold).coerceAtLeast(0)
                bonusGold += goldDelta
                workingPlayer = collectUpdate.player
                workingInstances = collectUpdate.itemInstances
                messages += collectUpdate.messages
            }
        }
        val outcomeUpdate = engine.classQuestService.onCombatOutcome(
            player = workingPlayer,
            itemInstances = workingInstances,
            monsterId = encounter.monster.archetypeId,
            isBoss = encounter.isBoss,
            monsterBaseType = encounter.monster.baseType
        )
        val outcomeGold = (outcomeUpdate.player.gold - workingPlayer.gold).coerceAtLeast(0)
        bonusGold += outcomeGold
        return ClassDungeonCombatResult(
            player = outcomeUpdate.player,
            itemInstances = outcomeUpdate.itemInstances,
            messages = messages + outcomeUpdate.messages,
            bonusGold = bonusGold
        )
    }
}

private data class ClassDungeonCombatResult(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val messages: List<String>,
    val bonusGold: Int
)


