package rpg.inventory

import rpg.model.ItemInstance
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
    fun inventoryLimit(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        return InventoryRuleSupport.inventoryLimit(player, itemInstances, itemRegistry)
    }

    fun slotsUsed(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        return InventoryRuleSupport.slotsUsed(player, itemInstances, itemRegistry)
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
        return InventoryMutationSupport.addItemsWithLimit(player, itemInstances, itemRegistry, incomingItemIds)
    }

    fun normalizeAmmoStorage(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): PlayerState {
        return InventoryMutationSupport.normalizeAmmoStorage(player, itemInstances, itemRegistry)
    }

    fun quiverCapacity(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        return InventoryRuleSupport.quiverCapacity(player, itemInstances, itemRegistry)
    }

    fun quiverAmmoCount(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        return InventoryMutationSupport.quiverAmmoCount(player, itemInstances, itemRegistry)
    }

    fun inventoryArrowReserveCount(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        return InventoryMutationSupport.inventoryArrowReserveCount(player, itemInstances, itemRegistry)
    }

    fun peekArrowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        amount: Int = 1
    ): List<String> {
        return InventoryMutationSupport.peekArrowAmmo(player, itemInstances, itemRegistry, amount)
    }

    fun consumeArrowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        amount: Int = 1
    ): ArrowConsumeResult? {
        return InventoryMutationSupport.consumeArrowAmmo(player, itemInstances, itemRegistry, amount)
    }

    fun moveAmmoToQuiver(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        ammoItemIds: List<String>
    ): InventoryInsertResult {
        return InventoryMutationSupport.moveAmmoToQuiver(player, itemInstances, itemRegistry, ammoItemIds)
    }

    fun unloadAmmoFromQuiver(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        ammoItemIds: List<String>
    ): InventoryInsertResult {
        return InventoryMutationSupport.unloadAmmoFromQuiver(player, itemInstances, itemRegistry, ammoItemIds)
    }

    fun selectAmmoTemplate(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        templateId: String?
    ): PlayerState {
        return InventoryMutationSupport.selectAmmoTemplate(player, itemInstances, itemRegistry, templateId)
    }

    fun ammoTemplateId(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): String {
        return InventoryRuleSupport.ammoTemplateId(itemId, itemInstances, itemRegistry)
    }

    fun isArrowAmmo(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Boolean {
        return InventoryRuleSupport.isArrowAmmo(itemId, itemInstances, itemRegistry)
    }

    fun stackKey(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): String {
        return InventoryRuleSupport.stackKey(itemId, itemInstances, itemRegistry)
    }
}
