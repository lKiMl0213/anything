package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class CharacterDef(
    val baseAttributePoints: Int = 35,
    val starterGold: Int = 0,
    val starterInventory: List<String> = listOf("potion_small", "potion_small", "ether_small"),
    val starterEquipmentByClass: Map<String, Map<String, String>> = emptyMap(),
    val starterInventoryByClass: Map<String, List<String>> = emptyMap(),
    val equipmentSlots: List<String> = listOf(
        "HEAD",
        "CHEST",
        "LEGS",
        "BOOTS",
        "GLOVES",
        "CAPE",
        "WEAPON_MAIN",
        "WEAPON_OFF",
        "ALJAVA",
        "BACKPACK",
        "ACCESSORY1",
        "ACCESSORY2",
        "ACCESSORY3",
        "ACCESSORY4",
        "ACCESSORY5"
    ),
    val accessorySlots: List<String> = listOf(
        "ACCESSORY1",
        "ACCESSORY2",
        "ACCESSORY3",
        "ACCESSORY4",
        "ACCESSORY5"
    )
)
