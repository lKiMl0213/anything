package rpg.model

import kotlinx.serialization.Serializable
import rpg.item.ItemRarity

@Serializable
enum class ItemBaseStat {
    DAMAGE_PHYSICAL,
    DAMAGE_MAGIC,
    DEF_PHYSICAL,
    DEF_MAGIC,
    HP_MAX,
    MP_MAX,
    CRIT_CHANCE,
    ATTACK_SPEED
}

@Serializable
data class ItemTemplateDef(
    val id: String,
    val name: String,
    val type: ItemType,
    val minLevel: Int = 1,
    val rarity: ItemRarity = ItemRarity.COMMON,
    val maxRarity: ItemRarity = ItemRarity.MYTHIC,
    val tags: List<String> = emptyList(),
    val lootTags: List<String> = emptyList(),
    val slot: EquipSlot? = null,
    val twoHanded: Boolean = false,
    val baseScaling: Double = 1.0,
    val baseStat: ItemBaseStat = ItemBaseStat.DAMAGE_PHYSICAL,
    val dropTier: Int = 1,
    val dropWeight: Int = 10,
    val vendorBaseValue: Int = 10,
    val allowedAffixes: List<String> = emptyList(),
    val description: String = ""
)
