package rpg.inventory

import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.registry.ItemRegistry

internal object InventoryMutationSupport {
    fun addItemsWithLimit(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        incomingItemIds: List<String>
    ): InventoryInsertResult {
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        val limit = InventoryRuleSupport.inventoryLimit(normalizedPlayer, itemInstances, itemRegistry)
        val quiverLimit = InventoryRuleSupport.quiverCapacity(normalizedPlayer, itemInstances, itemRegistry)
        if (incomingItemIds.isEmpty()) {
            val slots = InventoryRuleSupport.slotsUsed(normalizedPlayer, itemInstances, itemRegistry)
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
            .map { InventoryRuleSupport.stackKey(it, itemInstances, itemRegistry) }
            .toMutableSet()

        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        var selectedAmmoTemplateId = normalizedPlayer.selectedAmmoTemplateId

        for (id in incomingItemIds) {
            if (InventoryRuleSupport.isArrowAmmo(id, itemInstances, itemRegistry) && quiverInventory.size < quiverLimit) {
                quiverInventory += id
                selectedAmmoTemplateId = selectedAmmoTemplateId
                    ?: InventoryRuleSupport.ammoTemplateId(id, itemInstances, itemRegistry)
                accepted += id
                continue
            }
            val key = InventoryRuleSupport.stackKey(id, itemInstances, itemRegistry)
            val isNewStack = key !in currentKeys
            if (isNewStack && currentKeys.size >= limit) {
                rejected += id
                continue
            }
            inventory += id
            accepted += id
            currentKeys += key
        }

        selectedAmmoTemplateId = InventoryRuleSupport.normalizeSelectedAmmoTemplateId(
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
        val validQuiverAmmo = player.quiverInventory.filter { InventoryRuleSupport.isArrowAmmo(it, itemInstances, itemRegistry) }
        val capacity = InventoryRuleSupport.quiverCapacity(player, itemInstances, itemRegistry)

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

        val normalizedSelectedAmmo = InventoryRuleSupport.normalizeSelectedAmmoTemplateId(
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

    fun quiverAmmoCount(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        return normalizedPlayer.quiverInventory.count { InventoryRuleSupport.isArrowAmmo(it, itemInstances, itemRegistry) }
    }

    fun inventoryArrowReserveCount(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        return normalizedPlayer.inventory.count { InventoryRuleSupport.isArrowAmmo(it, itemInstances, itemRegistry) }
    }

    fun peekArrowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        amount: Int = 1
    ): List<String> {
        if (amount <= 0) return emptyList()
        val normalizedPlayer = normalizeAmmoStorage(player, itemInstances, itemRegistry)
        return InventoryRuleSupport.orderedQuiverAmmo(normalizedPlayer, itemInstances, itemRegistry)
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
        val available = InventoryRuleSupport.orderedQuiverAmmo(normalizedPlayer, itemInstances, itemRegistry)
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
        val capacity = InventoryRuleSupport.quiverCapacity(normalizedPlayer, itemInstances, itemRegistry)
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        var selectedAmmoTemplateId = normalizedPlayer.selectedAmmoTemplateId

        for (itemId in ammoItemIds) {
            if (!InventoryRuleSupport.isArrowAmmo(itemId, itemInstances, itemRegistry)) {
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
                    ?: InventoryRuleSupport.ammoTemplateId(itemId, itemInstances, itemRegistry)
                accepted += itemId
            } else {
                rejected += itemId
            }
        }

        selectedAmmoTemplateId = InventoryRuleSupport.normalizeSelectedAmmoTemplateId(
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
            slotLimit = InventoryRuleSupport.inventoryLimit(normalizedPlayer, itemInstances, itemRegistry),
            slotsUsed = inventory
                .asSequence()
                .map { InventoryRuleSupport.stackKey(it, itemInstances, itemRegistry) }
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
        val limit = InventoryRuleSupport.inventoryLimit(normalizedPlayer, itemInstances, itemRegistry)
        val currentKeys = inventory
            .asSequence()
            .map { InventoryRuleSupport.stackKey(it, itemInstances, itemRegistry) }
            .toMutableSet()
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()

        for (itemId in ammoItemIds) {
            if (!quiverInventory.contains(itemId)) {
                rejected += itemId
                continue
            }
            val key = InventoryRuleSupport.stackKey(itemId, itemInstances, itemRegistry)
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

        val selectedAmmoTemplateId = InventoryRuleSupport.normalizeSelectedAmmoTemplateId(
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
            quiverCapacity = InventoryRuleSupport.quiverCapacity(normalizedPlayer, itemInstances, itemRegistry),
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
        val selectedAmmoTemplateId = InventoryRuleSupport.normalizeSelectedAmmoTemplateId(
            selectedAmmoTemplateId = templateId,
            quiverInventory = normalizedPlayer.quiverInventory,
            itemInstances = itemInstances,
            itemRegistry = itemRegistry
        )
        if (selectedAmmoTemplateId == normalizedPlayer.selectedAmmoTemplateId) return normalizedPlayer
        return normalizedPlayer.copy(selectedAmmoTemplateId = selectedAmmoTemplateId)
    }
}
