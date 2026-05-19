package rpg.application.inventory

import rpg.item.ItemRarity
import rpg.model.ItemType

enum class InventorySortMode {
    TYPE,
    RARITY,
    VALUE
}

data class InventoryFilterState(
    val type: ItemType? = null,
    val minimumRarity: ItemRarity? = null,
    val sortMode: InventorySortMode = InventorySortMode.RARITY
)
