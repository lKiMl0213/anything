package rpg.model

import kotlinx.serialization.Serializable
import rpg.item.ItemRarity

@Serializable
enum class AffixKind {
    PREFIX,
    SUFFIX,
    SPECIAL
}

@Serializable
data class AffixDef(
    val id: String,
    val name: String,
    val kind: AffixKind = AffixKind.SUFFIX,
    val bonuses: Bonuses = Bonuses(),
    val effects: ItemEffects = ItemEffects(),
    val cost: Int = 0,
    val weight: Int = 1,
    val minRarity: ItemRarity = ItemRarity.COMMON,
    val maxRarity: ItemRarity = ItemRarity.MYTHIC,
    val allowedTypes: List<ItemType> = emptyList(),
    val allowedSlots: List<EquipSlot> = emptyList(),
    val requiredTagsAny: List<String> = emptyList(),
    val requiredTagsAll: List<String> = emptyList(),
    val blockedTags: List<String> = emptyList()
)
