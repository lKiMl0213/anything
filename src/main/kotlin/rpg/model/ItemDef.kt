package rpg.model

import kotlinx.serialization.Serializable
import rpg.item.ItemRarity

@Serializable
enum class ItemType {
    EQUIPMENT,
    CONSUMABLE,
    MATERIAL
}

@Serializable
enum class EquipSlot {
    HEAD,
    CHEST,
    LEGS,
    BOOTS,
    GLOVES,
    CAPE,
    WEAPON_MAIN,
    WEAPON_OFF,
    ALJAVA,
    BACKPACK,
    ACCESSORY
}

@Serializable
data class ItemEffects(
    val hpRestore: Double = 0.0,
    val mpRestore: Double = 0.0,
    val hpRestorePct: Double = 0.0,
    val mpRestorePct: Double = 0.0,
    val fullRestore: Boolean = false,
    val clearNegativeStatuses: Boolean = false,
    val statusImmunitySeconds: Double = 0.0,
    val roomAttributeMultiplierPct: Double = 0.0,
    val roomAttributeDurationRooms: Int = 0,
    val runAttributeMultiplierPct: Double = 0.0,
    val applyStatuses: List<CombatStatusApplyDef> = emptyList()
)

@Serializable
data class ItemDef(
    val id: String,
    val name: String,
    val type: ItemType,
    val minLevel: Int = 1,
    val rarity: ItemRarity = ItemRarity.COMMON,
    val tags: List<String> = emptyList(),
    val slot: EquipSlot? = null,
    val twoHanded: Boolean = false,
    val bonuses: Bonuses = Bonuses(),
    val effects: ItemEffects = ItemEffects(),
    val value: Int = 0,
    val description: String = ""
)
