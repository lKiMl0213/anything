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
    val itemId: String = "",
    val templateIds: List<String> = emptyList(),
    val category: DropCategory = DropCategory.COMMON_MATERIAL,
    val chancePct: Double = 0.0,
    val minQty: Int = 1,
    val maxQty: Int = 1,
    val minRarity: ItemRarity = ItemRarity.COMMON,
    val maxRarity: ItemRarity = ItemRarity.MYTHIC,
    val minMonsterLevel: Int = 1,
    val maxMonsterLevel: Int = 999,
    val requiredBiomeIds: List<String> = emptyList(),
    val blockedBiomeIds: List<String> = emptyList(),
    val requiredTierIds: List<String> = emptyList(),
    val blockedTierIds: List<String> = emptyList(),
    val minMapDropTier: Int = 1,
    val maxMapDropTier: Int = 999,
    val requiredMonsterTags: List<String> = emptyList(),
    val blockedMonsterTags: List<String> = emptyList()
)

@Serializable
data class DropTableDef(
    val id: String,
    val baseChancePct: Double = 20.0,
    val entries: List<DropEntryDef> = emptyList()
)
