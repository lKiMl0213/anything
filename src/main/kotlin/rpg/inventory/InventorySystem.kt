package rpg.inventory

import kotlin.math.max
import rpg.model.EquipSlot
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.registry.ItemRegistry

data class InventoryInsertResult(
    val inventory: List<String>,
    val quiverInventory: List<String>,
    val selectedAmmoTemplateId: String?,
    val accepted: List<String>,
    val rejected: List<String>,
    val slotLimit: Int,
    val slotsUsed: Int,
    val quiverCapacity: Int,
    val quiverUsed: Int
)

data class ArrowConsumeResult(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val consumedArrowIds: List<String>
)

object InventorySystem {
    private const val backpackSlotKey = "BACKPACK"
    private const val slotTagPrefix = "inventory_slots:"
    private const val ammoCapacityPrefix = "ammo_capacity:"
    private const val defaultQuiverCapacity = 30
    private const val quiverTag = "quiver"

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
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        val limit = inventoryLimit(normalizedPlayer, itemInstances, itemRegistry)
        val quiverLimit = quiverCapacity(normalizedPlayer, itemInstances, itemRegistry)
        if (incomingItemIds.isEmpty()) {
            val slots = slotsUsed(normalizedPlayer, itemInstances, itemRegistry)
            return InventoryInsertResult(
                inventory = normalizedPlayer.inventory,
                quiverInventory = normalizedPlayer.quiverInventory,
                selectedAmmoTemplateId = normalizedPlayer.selectedAmmoTemplateId,
                accepted = emptyList(),
                rejected = emptyList(),
                slotLimit = limit,
                slotsUsed = slots,
                quiverCapacity = quiverLimit,
                quiverUsed = quiverAmmoCount(normalizedPlayer, itemInstances, itemRegistry)
            )
        }

        val inventory = normalizedPlayer.inventory.toMutableList()
        val quiverInventory = normalizedPlayer.quiverInventory.toMutableList()
        val currentKeys = inventory
            .asSequence()
            .map { stackKey(it, itemInstances, itemRegistry) }
            .toMutableSet()

        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        var selectedAmmoTemplateId = normalizedPlayer.selectedAmmoTemplateId

        for (id in incomingItemIds) {
            if (isArrowAmmo(id, itemInstances, itemRegistry) && quiverInventory.size < quiverLimit) {
                quiverInventory += id
                selectedAmmoTemplateId = selectedAmmoTemplateId
                    ?: ammoTemplateId(id, itemInstances, itemRegistry)
                accepted += id
                continue
            }
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

        selectedAmmoTemplateId = normalizeSelectedAmmoTemplateId(
            selectedAmmoTemplateId = selectedAmmoTemplateId,
            quiverInventory = quiverInventory,
            itemInstances = itemInstances,
            itemRegistry = itemRegistry
        )

        return InventoryInsertResult(
            inventory = inventory,
            quiverInventory = quiverInventory,
            selectedAmmoTemplateId = selectedAmmoTemplateId,
            accepted = accepted,
            rejected = rejected,
            slotLimit = limit,
            slotsUsed = currentKeys.size,
            quiverCapacity = quiverLimit,
            quiverUsed = quiverInventory.size
        )
    }

    fun normalizeAmmoStorage(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): PlayerState {
        val inventory = player.inventory.toMutableList()
        val validQuiverAmmo = player.quiverInventory.filter { isArrowAmmo(it, itemInstances, itemRegistry) }
        val capacity = quiverCapacity(player, itemInstances, itemRegistry)

        val finalQuiverInventory = when {
            capacity <= 0 -> {
                inventory += validQuiverAmmo
                emptyList()
            }
            validQuiverAmmo.size > capacity -> {
                val kept = validQuiverAmmo.take(capacity)
                inventory += validQuiverAmmo.drop(capacity)
                kept
            }
            else -> validQuiverAmmo
        }

        val normalizedSelectedAmmo = normalizeSelectedAmmoTemplateId(
            selectedAmmoTemplateId = player.selectedAmmoTemplateId,
            quiverInventory = finalQuiverInventory,
            itemInstances = itemInstances,
            itemRegistry = itemRegistry
        )

        if (
            inventory == player.inventory &&
            finalQuiverInventory == player.quiverInventory &&
            normalizedSelectedAmmo == player.selectedAmmoTemplateId
        ) {
            return player
        }

        return player.copy(
            inventory = inventory,
            quiverInventory = finalQuiverInventory,
            selectedAmmoTemplateId = normalizedSelectedAmmo
        )
    }

    fun quiverCapacity(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        val quiverId = player.equipped[EquipSlot.ALJAVA.name] ?: return 0
        val tags = itemInstances[quiverId]?.tags
            ?: itemRegistry.item(quiverId)?.tags
            ?: itemRegistry.template(quiverId)?.tags
            ?: emptyList()
        if (tags.none { it.trim().equals(quiverTag, ignoreCase = true) }) {
            return 0
        }
        return tags.firstNotNullOfOrNull { tag ->
            val normalized = tag.trim().lowercase()
            if (!normalized.startsWith(ammoCapacityPrefix)) return@firstNotNullOfOrNull null
            normalized.removePrefix(ammoCapacityPrefix).toIntOrNull()
        }?.coerceAtLeast(0) ?: defaultQuiverCapacity
    }

    fun quiverAmmoCount(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        return normalizedPlayer.quiverInventory.count { isArrowAmmo(it, itemInstances, itemRegistry) }
    }

    fun inventoryArrowReserveCount(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        return normalizedPlayer.inventory.count { isArrowAmmo(it, itemInstances, itemRegistry) }
    }

    fun peekArrowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        amount: Int = 1
    ): List<String> {
        if (amount <= 0) return emptyList()
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        return orderedQuiverAmmo(normalizedPlayer, itemInstances, itemRegistry)
            .take(amount)
    }

    fun consumeArrowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        amount: Int = 1
    ): ArrowConsumeResult? {
        val ammoRequired = amount.coerceAtLeast(1)
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        val available = orderedQuiverAmmo(normalizedPlayer, itemInstances, itemRegistry)
        if (available.size < ammoRequired) return null

        val updatedInstances = itemInstances.toMutableMap()
        val quiverInventory = normalizedPlayer.quiverInventory.toMutableList()
        val consumed = available.take(ammoRequired)
        for (arrowId in consumed) {
            if (quiverInventory.remove(arrowId) && updatedInstances.containsKey(arrowId)) {
                updatedInstances.remove(arrowId)
            }
        }

        val afterConsume = normalizeAmmoStorage(
            normalizedPlayer.copy(quiverInventory = quiverInventory),
            updatedInstances,
            itemRegistry
        )
        return ArrowConsumeResult(
            player = afterConsume,
            itemInstances = updatedInstances.toMap(),
            consumedArrowIds = consumed
        )
    }

    fun moveAmmoToQuiver(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        ammoItemIds: List<String>
    ): InventoryInsertResult {
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        val inventory = normalizedPlayer.inventory.toMutableList()
        val quiverInventory = normalizedPlayer.quiverInventory.toMutableList()
        val capacity = quiverCapacity(normalizedPlayer, itemInstances, itemRegistry)
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        var selectedAmmoTemplateId = normalizedPlayer.selectedAmmoTemplateId

        for (itemId in ammoItemIds) {
            if (!isArrowAmmo(itemId, itemInstances, itemRegistry)) {
                rejected += itemId
                continue
            }
            if (quiverInventory.size >= capacity) {
                rejected += itemId
                continue
            }
            if (inventory.remove(itemId)) {
                quiverInventory += itemId
                selectedAmmoTemplateId = selectedAmmoTemplateId
                    ?: ammoTemplateId(itemId, itemInstances, itemRegistry)
                accepted += itemId
            } else {
                rejected += itemId
            }
        }

        selectedAmmoTemplateId = normalizeSelectedAmmoTemplateId(
            selectedAmmoTemplateId = selectedAmmoTemplateId,
            quiverInventory = quiverInventory,
            itemInstances = itemInstances,
            itemRegistry = itemRegistry
        )

        return InventoryInsertResult(
            inventory = inventory,
            quiverInventory = quiverInventory,
            selectedAmmoTemplateId = selectedAmmoTemplateId,
            accepted = accepted,
            rejected = rejected,
            slotLimit = inventoryLimit(normalizedPlayer, itemInstances, itemRegistry),
            slotsUsed = inventory
                .asSequence()
                .map { stackKey(it, itemInstances, itemRegistry) }
                .distinct()
                .count(),
            quiverCapacity = capacity,
            quiverUsed = quiverInventory.size
        )
    }

    fun unloadAmmoFromQuiver(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        ammoItemIds: List<String>
    ): InventoryInsertResult {
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        val inventory = normalizedPlayer.inventory.toMutableList()
        val quiverInventory = normalizedPlayer.quiverInventory.toMutableList()
        val limit = inventoryLimit(normalizedPlayer, itemInstances, itemRegistry)
        val currentKeys = inventory
            .asSequence()
            .map { stackKey(it, itemInstances, itemRegistry) }
            .toMutableSet()
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()

        for (itemId in ammoItemIds) {
            if (!quiverInventory.contains(itemId)) {
                rejected += itemId
                continue
            }
            val key = stackKey(itemId, itemInstances, itemRegistry)
            val isNewStack = key !in currentKeys
            if (isNewStack && currentKeys.size >= limit) {
                rejected += itemId
                continue
            }
            quiverInventory.remove(itemId)
            inventory += itemId
            accepted += itemId
            currentKeys += key
        }

        val selectedAmmoTemplateId = normalizeSelectedAmmoTemplateId(
            selectedAmmoTemplateId = normalizedPlayer.selectedAmmoTemplateId,
            quiverInventory = quiverInventory,
            itemInstances = itemInstances,
            itemRegistry = itemRegistry
        )

        return InventoryInsertResult(
            inventory = inventory,
            quiverInventory = quiverInventory,
            selectedAmmoTemplateId = selectedAmmoTemplateId,
            accepted = accepted,
            rejected = rejected,
            slotLimit = limit,
            slotsUsed = currentKeys.size,
            quiverCapacity = quiverCapacity(normalizedPlayer, itemInstances, itemRegistry),
            quiverUsed = quiverInventory.size
        )
    }

    fun selectAmmoTemplate(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        templateId: String?
    ): PlayerState {
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        val selectedAmmoTemplateId = normalizeSelectedAmmoTemplateId(
            selectedAmmoTemplateId = templateId,
            quiverInventory = normalizedPlayer.quiverInventory,
            itemInstances = itemInstances,
            itemRegistry = itemRegistry
        )
        if (selectedAmmoTemplateId == normalizedPlayer.selectedAmmoTemplateId) return normalizedPlayer
        return normalizedPlayer.copy(selectedAmmoTemplateId = selectedAmmoTemplateId)
    }

    fun ammoTemplateId(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): String {
        val templateId = itemInstances[itemId]?.templateId
        return (
            templateId
                ?: itemRegistry.item(itemId)?.id
                ?: itemRegistry.template(itemId)?.id
                ?: itemId
            ).trim().lowercase()
    }

    fun isArrowAmmo(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Boolean {
        val instance = itemInstances[itemId]
        val templateId = instance?.templateId
        val resolvedId = instance?.id ?: templateId ?: itemId
        val tags = instance?.tags
            ?: itemRegistry.item(itemId)?.tags
            ?: templateId?.let { itemRegistry.item(it)?.tags ?: itemRegistry.template(it)?.tags }
            ?: itemRegistry.template(itemId)?.tags
            ?: emptyList()
        val normalizedTags = tags.mapTo(mutableSetOf()) { it.trim().lowercase() }
        return "arrow" in normalizedTags || resolvedId.lowercase().startsWith("arrow_")
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

    private fun normalizeSelectedAmmoTemplateId(
        selectedAmmoTemplateId: String?,
        quiverInventory: List<String>,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): String? {
        val availableTemplates = quiverInventory
            .filter { isArrowAmmo(it, itemInstances, itemRegistry) }
            .map { ammoTemplateId(it, itemInstances, itemRegistry) }
            .distinct()
        if (availableTemplates.isEmpty()) return null
        val normalizedSelected = selectedAmmoTemplateId
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        return normalizedSelected?.takeIf { it in availableTemplates } ?: availableTemplates.first()
    }

    private fun orderedQuiverAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): List<String> {
        val selectedTemplateId = player.selectedAmmoTemplateId
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        val arrows = player.quiverInventory.filter { isArrowAmmo(it, itemInstances, itemRegistry) }
        if (selectedTemplateId == null) return arrows
        val selected = arrows.filter { ammoTemplateId(it, itemInstances, itemRegistry) == selectedTemplateId }
        val others = arrows.filter { ammoTemplateId(it, itemInstances, itemRegistry) != selectedTemplateId }
        return selected + others
    }
}
