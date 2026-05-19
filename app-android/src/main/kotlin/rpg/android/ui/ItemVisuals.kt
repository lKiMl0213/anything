package rpg.android.ui

import rpg.model.ItemType

private const val ICON_FISH = "\uD83D\uDC1F"
private const val ICON_PICKAXE = "\u26CF"
private const val ICON_WOOD = "\uD83E\uDEB5"
private const val ICON_HERB = "\uD83C\uDF3F"
private const val ICON_POTION = "\uD83E\uDDEA"
private const val ICON_GEM = "\uD83D\uDC8D"
private const val ICON_AMULET = "\uD83D\uDFFF"
private const val ICON_BOW = "\uD83C\uDFF9"
private const val ICON_SWORD = "\uD83D\uDDE1"
private const val ICON_SHIELD = "\uD83D\uDEE1"
private const val ICON_HELM = "\uD83E\uDE96"
private const val ICON_BOOT = "\uD83E\uDD7E"
private const val ICON_GLOVE = "\uD83E\uDDE4"
private const val ICON_CAPE = "\uD83E\uDDE5"
private const val ICON_LEGS = "\uD83E\uDDB5"
private const val ICON_FOOD = "\uD83C\uDF56"
private const val ICON_EQUIPMENT = "\uD83D\uDEE0"
private const val ICON_CONSUMABLE = "\uD83E\uDDF4"
private const val ICON_BOX = "\uD83D\uDCE6"
private const val ICON_WHITE_CIRCLE = "\u2B1C"

internal fun itemEmoji(name: String, type: ItemType, tags: List<String>): String {
    val lower = name.lowercase()
    val allTags = tags.map { it.lowercase() }
    return when {
        "fish" in lower || "peixe" in lower -> ICON_FISH
        "ore" in lower || "minerio" in lower -> ICON_PICKAXE
        "wood" in lower || "madeira" in lower -> ICON_WOOD
        "herb" in lower || "erva" in lower -> ICON_HERB
        "potion" in lower || "poção" in lower -> ICON_POTION
        "ring" in lower || "anel" in lower -> ICON_GEM
        "amulet" in lower || "amuleto" in lower -> ICON_AMULET
        "bow" in lower || "arco" in lower -> ICON_BOW
        "sword" in lower || "espada" in lower -> ICON_SWORD
        "shield" in lower || "escudo" in lower -> ICON_SHIELD
        "armor" in lower || "armadura" in lower || "cota" in lower || "peitoral" in lower -> ICON_SHIELD
        "helmet" in lower || "capacete" in lower -> ICON_HELM
        "boot" in lower || "bota" in lower -> ICON_BOOT
        "glove" in lower || "luva" in lower -> ICON_GLOVE
        "cape" in lower || "capa" in lower -> ICON_CAPE
        "pants" in lower || "calça" in lower || "perneira" in lower || "pernas" in lower -> ICON_LEGS
        "food" in allTags || "comida" in allTags -> ICON_FOOD
        type == ItemType.EQUIPMENT -> ICON_EQUIPMENT
        type == ItemType.CONSUMABLE -> ICON_CONSUMABLE
        else -> ICON_BOX
    }
}

internal fun slotEmoji(slotKey: String): String {
    return when (slotKey.uppercase()) {
        "WEAPON_MAIN" -> ICON_SWORD
        "WEAPON_OFF" -> ICON_SHIELD
        "ALJAVA" -> ICON_BOW
        "HEAD" -> ICON_HELM
        "CHEST" -> ICON_SHIELD
        "LEGS" -> ICON_LEGS
        "GLOVES" -> ICON_GLOVE
        "BOOTS" -> ICON_BOOT
        else -> if (slotKey.uppercase().startsWith("ACCESSORY")) ICON_GEM else ICON_WHITE_CIRCLE
    }
}

internal fun compactSlotLabel(slotKey: String): String {
    return when (slotKey.uppercase()) {
        "WEAPON_MAIN" -> "Arma"
        "WEAPON_OFF" -> "Sec."
        "ALJAVA" -> "Alj."
        "HEAD" -> "Cab."
        "CHEST" -> "Torso"
        "LEGS" -> "Pern."
        "GLOVES" -> "Luva"
        "BOOTS" -> "Bota"
        else -> if (slotKey.uppercase().startsWith("ACCESSORY")) "Acc" else slotKey
    }
}

