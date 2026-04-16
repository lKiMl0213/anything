package rpg.model

import kotlinx.serialization.Serializable
import rpg.item.ItemRarity

@Serializable
enum class DropCategory {
    COMMON_SELL,
    COMMON_MATERIAL,
    UNCOMMON_MATERIAL,
    QUEST_ITEM,
    RARE_COMPONENT,
    EQUIPMENT_DROP,
    CONSUMABLE
}

@Serializable
data class DropEntryDef(
    val itemId: String,
    val category: DropCategory = DropCategory.COMMON_MATERIAL,
    val chancePct: Double = 0.0,
    val minQty: Int = 1,
    val maxQty: Int = 1,
    val minRarity: ItemRarity = ItemRarity.COMMON,
    val maxRarity: ItemRarity = ItemRarity.LEGENDARY
)

@Serializable
data class DropTableDef(
    val id: String,
    val baseChancePct: Double = 20.0,
    val entries: List<DropEntryDef> = emptyList()
)
