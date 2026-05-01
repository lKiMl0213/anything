package rpg.enchant

import rpg.model.ItemInstance
import rpg.model.PlayerState

internal class EnchantResourcePlanner(
    private val config: EnchantConfig
) {
    fun planEnhancementRunes(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        requestedRunes: Int
    ): RuneConsumptionPlan {
        val requested = requestedRunes.coerceAtLeast(0)
        if (requested <= 0) return RuneConsumptionPlan()

        val enhancementRuneIds = config.enhancementRuneItemIds()
        if (enhancementRuneIds.isEmpty()) return RuneConsumptionPlan()
        val availableByItemId = ownedCountByTemplate(player.inventory, itemInstances, enhancementRuneIds)
        var remaining = requested
        var totalBonusPct = 0.0
        val consumption = linkedMapOf<String, Int>()
        val prioritizedRuneIds = config.sortedEnhancementRuneItemIdsByBonusDesc()
        for (runeId in prioritizedRuneIds) {
            if (remaining <= 0) break
            val available = availableByItemId[runeId]?.coerceAtLeast(0) ?: 0
            if (available <= 0) continue
            val consume = minOf(remaining, available)
            if (consume <= 0) continue
            consumption[runeId] = consume
            totalBonusPct += consume * config.enhancementRuneBonusPctForItem(runeId)
            remaining -= consume
        }
        if (remaining > 0) {
            for ((runeId, available) in availableByItemId.entries.sortedBy { it.key }) {
                if (remaining <= 0) break
                if (runeId in consumption) continue
                val consume = minOf(remaining, available.coerceAtLeast(0))
                if (consume <= 0) continue
                consumption[runeId] = consume
                totalBonusPct += consume * config.enhancementRuneBonusPctForItem(runeId)
                remaining -= consume
            }
        }
        return RuneConsumptionPlan(
            totalConsumed = requested - remaining,
            totalBonusPct = totalBonusPct.coerceAtLeast(0.0),
            consumptionByItemId = consumption
        )
    }

    fun selectProtectionRuneId(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): String? {
        val protectionRuneIds = config.protectionRuneItemIds()
        if (protectionRuneIds.isEmpty()) return null
        val ownedById = ownedCountByTemplate(player.inventory, itemInstances, protectionRuneIds)
        for (itemId in config.sortedProtectionRuneItemIdsByTier()) {
            if ((ownedById[itemId] ?: 0) > 0) return itemId
        }
        return ownedById.entries.firstOrNull { it.value > 0 }?.key
    }

    private fun ownedCountByTemplate(
        inventory: List<String>,
        itemInstances: Map<String, ItemInstance>,
        templateIds: Set<String>
    ): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        if (templateIds.isEmpty()) return counts
        for (inventoryId in inventory) {
            val templateId = itemInstances[inventoryId]?.templateId
            val canonicalId = when {
                inventoryId in templateIds -> inventoryId
                templateId != null && templateId in templateIds -> templateId
                else -> null
            } ?: continue
            counts[canonicalId] = (counts[canonicalId] ?: 0) + 1
        }
        return counts
    }
}

internal data class RuneConsumptionPlan(
    val totalConsumed: Int = 0,
    val totalBonusPct: Double = 0.0,
    val consumptionByItemId: Map<String, Int> = emptyMap()
)
