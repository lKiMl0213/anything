package rpg.inventory

import kotlin.math.max
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.registry.ItemRegistry

data class InventoryInsertResult(
    val inventory: List<String>,
    val accepted: List<String>,
    val rejected: List<String>,
    val slotLimit: Int,
    val slotsUsed: Int
)

object InventorySystem {
    private const val backpackSlotKey = "BACKPACK"
    private const val slotTagPrefix = "inventory_slots:"

    fun inventoryLimit(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        val base = player.inventoryBaseSlots.coerceAtLeast(1)
        val backpackId = player.equipped[backpackSlotKey] ?: return base
        val tags = itemInstances[backpackId]?.tags
            ?: itemRegistry.item(backpackId)?.tags
            ?: emptyList()
        val bonus = tags.firstNotNullOfOrNull { tag ->
            if (!tag.startsWith(slotTagPrefix)) return@firstNotNullOfOrNull null
            tag.removePrefix(slotTagPrefix).toIntOrNull()
        } ?: 0
        return base + max(0, bonus)
    }

    fun slotsUsed(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        return player.inventory
            .asSequence()
            .map { stackKey(it, itemInstances, itemRegistry) }
            .distinct()
            .count()
    }

    fun canAddItems(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        incomingItemIds: List<String>
    ): Boolean {
        val merged = addItemsWithLimit(player, itemInstances, itemRegistry, incomingItemIds)
        return merged.rejected.isEmpty()
    }

    fun addItemsWithLimit(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        incomingItemIds: List<String>
    ): InventoryInsertResult {
        if (incomingItemIds.isEmpty()) {
            val slots = slotsUsed(player, itemInstances, itemRegistry)
            val limit = inventoryLimit(player, itemInstances, itemRegistry)
            return InventoryInsertResult(
                inventory = player.inventory,
                accepted = emptyList(),
                rejected = emptyList(),
                slotLimit = limit,
                slotsUsed = slots
            )
        }
        val limit = inventoryLimit(player, itemInstances, itemRegistry)
        val inventory = player.inventory.toMutableList()
        val currentKeys = inventory
            .asSequence()
            .map { stackKey(it, itemInstances, itemRegistry) }
            .toMutableSet()

        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        for (id in incomingItemIds) {
            val key = stackKey(id, itemInstances, itemRegistry)
            val isNewStack = key !in currentKeys
            if (isNewStack && currentKeys.size >= limit) {
                rejected += id
                continue
            }
            inventory += id
            accepted += id
            currentKeys += key
        }

        return InventoryInsertResult(
            inventory = inventory,
            accepted = accepted,
            rejected = rejected,
            slotLimit = limit,
            slotsUsed = currentKeys.size
        )
    }

    fun stackKey(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): String {
        val instance = itemInstances[itemId]
        if (instance != null) {
            return if (instance.type == ItemType.EQUIPMENT) {
                "equip:${instance.id}"
            } else {
                "stack:${instance.templateId}"
            }
        }
        val item = itemRegistry.item(itemId)
        if (item != null && item.type == ItemType.EQUIPMENT) {
            return "equip:$itemId"
        }
        return "stack:$itemId"
    }
}
