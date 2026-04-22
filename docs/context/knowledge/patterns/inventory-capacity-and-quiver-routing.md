# Pattern: Inventory Capacity And Quiver Routing

## Description
Inventory insertion is centralized so capacity checks, stack grouping, quiver routing, and ammo selection stay consistent across combat rewards, crafting, gathering, and quests.

## When to Use
- When adding a new source of item rewards
- When introducing a new ammo flow or inventory rule
- When a system must place items without bypassing capacity logic

## Pattern
Always send incoming items through `InventorySystem.addItemsWithLimit(...)`, and let the inventory system decide whether items go to the backpack, quiver, or rejection list.

## Example
```kotlin
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
```

```kotlin
fun normalizeAmmoStorage(
    player: PlayerState,
    itemInstances: Map<String, ItemInstance>,
    itemRegistry: ItemRegistry
): PlayerState {
    val inventory = player.inventory.toMutableList()
    val validQuiverAmmo = player.quiverInventory.filter { isArrowAmmo(it, itemInstances, itemRegistry) }
    val capacity = quiverCapacity(player, itemInstances, itemRegistry)
    ...
}
```

## Files Using This Pattern
- [InventorySystem.kt](../../../src/main/kotlin/rpg/inventory/InventorySystem.kt) - Central inventory and quiver routing rules
- [QuestRewardService.kt](../../../src/main/kotlin/rpg/quest/QuestRewardService.kt) - Uses the shared insertion path for rewards
- [CraftingService.kt](../../../src/main/kotlin/rpg/crafting/CraftingService.kt) - Routes crafted outputs through capacity checks
- [GatheringService.kt](../../../src/main/kotlin/rpg/gathering/GatheringService.kt) - Routes gathered outputs through the same flow

## Related
- [Decision: Randomized Item And Drop Model](../../decisions/005-randomized-item-and-drop-model.md)
- [Feature: Items, Loot, And Economy](../../intent/feature-items-loot-economy.md)

## Status
- **Created**: 2026-04-18
- **Status**: Active
