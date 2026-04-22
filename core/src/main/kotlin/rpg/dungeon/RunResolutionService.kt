package rpg.dungeon

import kotlin.math.ceil
import rpg.achievement.AchievementUpdate
import rpg.cli.model.DeathPenaltyResult
import rpg.cli.model.RunFinalizeResult
import rpg.engine.ComputedStats
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.model.Attributes
import rpg.model.DerivedStats
import rpg.model.PlayerState

internal class RunResolutionService(
    private val engine: GameEngine,
    private val deathBaseLootLossPct: Double,
    private val deathMinLootLossPct: Double,
    private val deathDebuffBaseMinutes: Double,
    private val deathDebuffExtraMinutes: Double,
    private val deathGoldLossPct: Double,
    private val deathXpPenaltyPct: Double,
    private val applyAchievementUpdate: (AchievementUpdate) -> PlayerState,
    private val onGoldEarned: (player: PlayerState, amount: Long) -> AchievementUpdate,
    private val onDeath: (player: PlayerState) -> AchievementUpdate,
    private val notify: (String) -> Unit,
    private val computePlayerStats: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> ComputedStats
) {
    fun applyRoomEffect(player: PlayerState, multiplier: Double, rooms: Int): PlayerState {
        if (multiplier < 1.0 && player.ignoreNextDebuff) {
            return player.copy(ignoreNextDebuff = false)
        }
        return player.copy(roomEffectMultiplier = multiplier, roomEffectRooms = rooms)
    }

    fun applyHealing(
        player: PlayerState,
        hpDelta: Double,
        mpDelta: Double,
        itemInstances: Map<String, rpg.model.ItemInstance> = emptyMap()
    ): PlayerState {
        val stats = computePlayerStats(player, itemInstances)
        val multiplier = player.nextHealMultiplier
        val newHp = (player.currentHp + hpDelta * multiplier).coerceAtMost(stats.derived.hpMax)
        val newMp = (player.currentMp + mpDelta * multiplier).coerceAtMost(stats.derived.mpMax)
        val consumed = multiplier != 1.0 && (hpDelta > 0.0 || mpDelta > 0.0)
        return if (consumed) {
            player.copy(currentHp = newHp, currentMp = newMp, nextHealMultiplier = 1.0)
        } else {
            player.copy(currentHp = newHp, currentMp = newMp)
        }
    }

    fun clearRunEffects(player: PlayerState): PlayerState {
        return player.copy(
            roomEffectMultiplier = 1.0,
            roomEffectRooms = 0,
            roomAttrBonus = Attributes(),
            roomAttrRooms = 0,
            roomDerivedAdd = DerivedStats(),
            roomDerivedMult = DerivedStats(),
            roomDerivedRooms = 0,
            runAttrBonus = Attributes(),
            runDerivedAdd = DerivedStats(),
            runDerivedMult = DerivedStats(),
            runAttrMultiplier = 1.0,
            nextHealMultiplier = 1.0,
            ignoreNextDebuff = false,
            reviveOnce = false,
            roomRegenHpPct = 0.0,
            roomRegenHpRooms = 0,
            roomRegenMpPct = 0.0,
            roomRegenMpRooms = 0,
            roomAttrRollRooms = 0,
            roomAttrRollAmount = 0
        )
    }

    fun finalizeRun(
        player: PlayerState,
        loot: MutableList<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): RunFinalizeResult {
        val cleared = clearRunEffects(player)
        val withCapacity = InventorySystem.addItemsWithLimit(
            player = cleared,
            itemInstances = itemInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = loot
        )
        val rejectedGenerated = withCapacity.rejected.filter { itemInstances.containsKey(it) }.toSet()
        if (withCapacity.rejected.isNotEmpty()) {
            notify("Inventario cheio: ${withCapacity.rejected.size} item(ns) da run foram perdidos.")
        }
        var updatedPlayer = cleared.copy(
            inventory = withCapacity.inventory,
            quiverInventory = withCapacity.quiverInventory,
            selectedAmmoTemplateId = withCapacity.selectedAmmoTemplateId
        )
        var updatedItemInstances = itemInstances - rejectedGenerated
        if (withCapacity.accepted.isNotEmpty()) {
            val collectedByCanonical = withCapacity.accepted
                .groupingBy { itemId -> updatedItemInstances[itemId]?.templateId ?: itemId }
                .eachCount()
            val classQuestUpdate = engine.classQuestService.onItemsCollected(
                player = updatedPlayer,
                itemInstances = updatedItemInstances,
                collectedItems = collectedByCanonical
            )
            classQuestUpdate.messages.forEach(notify)
            val classQuestGold = (classQuestUpdate.player.gold - updatedPlayer.gold).coerceAtLeast(0)
            updatedPlayer = classQuestUpdate.player
            if (classQuestGold > 0) {
                updatedPlayer = applyAchievementUpdate(
                    onGoldEarned(updatedPlayer, classQuestGold.toLong())
                )
            }
            updatedItemInstances = classQuestUpdate.itemInstances
        }
        return RunFinalizeResult(
            player = updatedPlayer,
            itemInstances = updatedItemInstances
        )
    }

    fun applyDeathPenalty(
        player: PlayerState,
        loot: MutableList<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): DeathPenaltyResult {
        val cleared = clearRunEffects(player)
        val stats = computePlayerStats(cleared, itemInstances)
        val lossReduction = stats.attributes.luk * 0.3
        val lossPct = (deathBaseLootLossPct - lossReduction).coerceIn(deathMinLootLossPct, deathBaseLootLossPct)
        val keepPct = 100.0 - lossPct
        val keepCount = ceil(loot.size * (keepPct / 100.0)).toInt().coerceAtMost(loot.size)

        engine.shuffleLoot(loot)
        val kept = loot.take(keepCount)
        val lost = loot.drop(keepCount).toSet()

        val withCapacity = InventorySystem.addItemsWithLimit(
            player = cleared,
            itemInstances = itemInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = kept
        )
        val allLost = lost + withCapacity.rejected.toSet()

        val nextStacks = if (cleared.deathDebuffMinutes > 0.0) cleared.deathDebuffStacks + 1 else 1
        val duration = deathDebuffBaseMinutes + (nextStacks - 1) * deathDebuffExtraMinutes
        val lostGold = (cleared.gold * deathGoldLossPct).toInt().coerceAtLeast(0)

        notify("Perdeu ${allLost.size} itens no caos da derrota.")
        if (lostGold > 0) {
            notify("Perdeu $lostGold de ouro na derrota.")
        }

        var updatedPlayer = cleared.copy(
            inventory = withCapacity.inventory,
            quiverInventory = withCapacity.quiverInventory,
            selectedAmmoTemplateId = withCapacity.selectedAmmoTemplateId,
            currentHp = 1.0,
            gold = (cleared.gold - lostGold).coerceAtLeast(0),
            deathDebuffStacks = nextStacks,
            deathDebuffMinutes = duration,
            deathXpPenaltyPct = deathXpPenaltyPct,
            deathXpPenaltyMinutes = duration
        )
        updatedPlayer = applyAchievementUpdate(onDeath(updatedPlayer))
        var updatedInstances = itemInstances - allLost.filter { itemInstances.containsKey(it) }.toSet()
        if (withCapacity.accepted.isNotEmpty()) {
            val collectedByCanonical = withCapacity.accepted
                .groupingBy { itemId -> updatedInstances[itemId]?.templateId ?: itemId }
                .eachCount()
            val classQuestUpdate = engine.classQuestService.onItemsCollected(
                player = updatedPlayer,
                itemInstances = updatedInstances,
                collectedItems = collectedByCanonical
            )
            classQuestUpdate.messages.forEach(notify)
            val classQuestGold = (classQuestUpdate.player.gold - updatedPlayer.gold).coerceAtLeast(0)
            updatedPlayer = classQuestUpdate.player
            if (classQuestGold > 0) {
                updatedPlayer = applyAchievementUpdate(
                    onGoldEarned(updatedPlayer, classQuestGold.toLong())
                )
            }
            updatedInstances = classQuestUpdate.itemInstances
        }
        return DeathPenaltyResult(updatedPlayer, updatedInstances)
    }

    fun clampPlayerResources(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance> = emptyMap()
    ): PlayerState {
        val stats = computePlayerStats(player, itemInstances)
        val hp = player.currentHp.coerceIn(0.0, stats.derived.hpMax)
        val mp = player.currentMp.coerceIn(0.0, stats.derived.mpMax)
        return player.copy(currentHp = hp, currentMp = mp)
    }
}
