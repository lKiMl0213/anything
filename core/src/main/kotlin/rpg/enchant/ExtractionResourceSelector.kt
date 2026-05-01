package rpg.enchant

import rpg.model.ItemInstance
import rpg.model.PlayerState

internal class ExtractionResourceSelector(
    private val config: ExtractionConfig
) {
    fun selectRemovalScrollId(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): String? {
        val ids = config.removalScrollItemIds()
        if (ids.isEmpty()) return null
        val owned = ownedByTemplate(player.inventory, itemInstances, ids)
        for (itemId in config.sortedRemovalScrollItemIdsByTierDesc()) {
            if ((owned[itemId] ?: 0) > 0) return itemId
        }
        return owned.entries.firstOrNull { it.value > 0 }?.key
    }

    fun selectProtectionScrollId(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): String? {
        val ids = config.protectionScrollItemIds()
        if (ids.isEmpty()) return null
        val owned = ownedByTemplate(player.inventory, itemInstances, ids)
        for (itemId in config.sortedProtectionScrollItemIdsByTierAsc()) {
            if ((owned[itemId] ?: 0) > 0) return itemId
        }
        return owned.entries.firstOrNull { it.value > 0 }?.key
    }

    fun ownedCount(
        inventory: List<String>,
        itemInstances: Map<String, ItemInstance>,
        targetItemIds: Set<String>
    ): Int {
        if (targetItemIds.isEmpty()) return 0
        return inventory.count { id ->
            id in targetItemIds || (itemInstances[id]?.templateId in targetItemIds)
        }
    }

    private fun ownedByTemplate(
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
