package rpg.application.inventory

import rpg.engine.GameEngine
import rpg.model.GameState

class InventoryQueryService(
    private val engine: GameEngine,
    private val support: InventoryRulesSupport
) {
    fun inventoryStacks(state: GameState, filter: InventoryFilterState): List<InventoryStackView> {
        return support.applyInventoryFilter(
            support.buildInventoryStacks(state.player, state.itemInstances),
            filter
        )
    }

    fun allInventoryStacks(state: GameState): List<InventoryStackView> {
        return support.buildInventoryStacks(state.player, state.itemInstances)
    }

    fun inventoryItemDetail(state: GameState, sampleItemId: String): InventoryItemDetailView? {
        val stack = support.buildInventoryStacks(state.player, state.itemInstances)
            .firstOrNull { it.sampleItemId == sampleItemId }
            ?: return null
        return support.buildInventoryItemDetail(state.player, state.itemInstances, stack)
    }

    fun equippedSlots(state: GameState): List<EquippedSlotView> {
        return support.equippedSlotViews(state.player, state.itemInstances)
    }

    fun equippedDetail(state: GameState, slotKey: String): EquippedItemDetailView? {
        return support.buildEquippedItemDetail(state.player, state.itemInstances, slotKey)
    }

    fun inventoryFilterSummary(filter: InventoryFilterState): String {
        return support.inventoryFilterSummary(filter)
    }

    fun quiverLoadedStacks(state: GameState): List<AmmoStackView> {
        return support.buildAmmoStacks(
            state.player.quiverInventory,
            state.itemInstances,
            state.player.selectedAmmoTemplateId
        )
    }

    fun quiverReserveStacks(state: GameState): List<AmmoStackView> {
        return support.buildAmmoStacks(
            state.player.inventory,
            state.itemInstances,
            state.player.selectedAmmoTemplateId
        )
    }

    fun activeAmmoName(state: GameState): String {
        return quiverLoadedStacks(state)
            .firstOrNull { it.templateId == state.player.selectedAmmoTemplateId }
            ?.item
            ?.name
            ?: "-"
    }

    fun inventoryCapacityLabel(state: GameState): String {
        val slots = rpg.inventory.InventorySystem.slotsUsed(state.player, state.itemInstances, engine.itemRegistry)
        val limit = rpg.inventory.InventorySystem.inventoryLimit(state.player, state.itemInstances, engine.itemRegistry)
        return "$slots/$limit"
    }

    fun quiverCapacityLabel(state: GameState): String {
        val current = rpg.inventory.InventorySystem.quiverAmmoCount(state.player, state.itemInstances, engine.itemRegistry)
        val max = rpg.inventory.InventorySystem.quiverCapacity(state.player, state.itemInstances, engine.itemRegistry)
        return "$current/$max"
    }

    fun reserveAmmoCount(state: GameState): Int {
        return rpg.inventory.InventorySystem.inventoryArrowReserveCount(state.player, state.itemInstances, engine.itemRegistry)
    }

    fun itemDisplayLabel(itemId: String, state: GameState): String {
        val item = engine.itemResolver.resolve(itemId, state.itemInstances) ?: return itemId
        return support.itemDisplayLabel(item)
    }

    fun slotSummary(state: GameState): String {
        val stats = engine.computePlayerStats(state.player, state.itemInstances)
        return "DMG ${format(stats.derived.damagePhysical)} | DEF ${format(stats.derived.defPhysical)} | HP ${format(stats.derived.hpMax)} | MP ${format(stats.derived.mpMax)}"
    }

    fun support(): InventoryRulesSupport = support

    private fun format(value: Double): String = "%.1f".format(value)
}
