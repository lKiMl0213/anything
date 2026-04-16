package rpg.model

import kotlinx.serialization.Serializable
import rpg.item.ItemRarity

@Serializable
data class ItemInstance(
    val id: String,
    val templateId: String,
    val name: String,
    val level: Int,
    val minLevel: Int = 1,
    val rarity: ItemRarity,
    val type: ItemType,
    val slot: EquipSlot? = null,
    val twoHanded: Boolean = false,
    val tags: List<String> = emptyList(),
    val bonuses: Bonuses = Bonuses(),
    val effects: ItemEffects = ItemEffects(),
    val value: Int = 0,
    val description: String = "",
    val affixes: List<String> = emptyList()
)
