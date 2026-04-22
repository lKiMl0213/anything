package rpg.application.inventory

import rpg.item.ItemRarity
import rpg.model.ItemType

data class InventoryFilterState(
    val type: ItemType? = null,
    val minimumRarity: ItemRarity? = null
)
