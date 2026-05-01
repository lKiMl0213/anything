package rpg.enchant

import kotlinx.serialization.Serializable
import rpg.model.EquipSlot
import rpg.model.ItemType

@Serializable
data class EnchantConfig(
    val maxEnchantLevel: Int = 15,
    val baseChanceByLevel: Map<Int, Double> = defaultBaseChanceByLevel(),
    val breakChanceByLevel: Map<Int, Double> = defaultBreakChanceByLevel(),
    val enhancementRuneItemId: String = "enchant_rune_aprimoramento",
    val protectionRuneItemId: String = "enchant_rune_protecao",
    val enhancementRuneItemIdsByTier: Map<String, String> = defaultEnhancementRuneItemIdsByTier(),
    val protectionRuneItemIdsByTier: Map<String, String> = defaultProtectionRuneItemIdsByTier(),
    val enhancementRuneBonusPctByTier: Map<String, Double> = defaultEnhancementRuneBonusPctByTier(),
    val enhancementRuneBonusPctPerUnit: Double = 4.0,
    val maxEnhancementRunesPerAttempt: Int = 20,
    val baseDurationSeconds: Double = 8.0,
    val minDurationSeconds: Double = 1.5,
    val durationReductionPerSkillLevelPct: Double = 1.4,
    val maxDurationReductionPct: Double = 22.0,
    val successBonusPerSkillLevelPct: Double = 0.6,
    val maxSuccessBonusPct: Double = 12.0,
    val breakReductionPerSkillLevelPct: Double = 0.35,
    val maxBreakReductionPct: Double = 6.0,
    val goldBaseCost: Int = 120,
    val goldPerItemLevel: Double = 10.0,
    val goldPerEnchantLevelMultiplier: Double = 0.26,
    val rarityCostMultiplierByName: Map<String, Double> = defaultRarityCostMultipliers(),
    val growthPctByCategory: Map<String, Double> = defaultGrowthPctByCategory(),
    val minimumEnchantableItemLevel: Int = 5,
    val attemptBaseXp: Double = 8.0,
    val successBonusXp: Double = 18.0,
    val xpPerEnchantLevelDifficulty: Double = 0.08,
    val antiExploitMinItemLevelForFullXp: Int = 30,
    val antiExploitXpFloorMultiplier: Double = 0.10
) {
    fun enhancementRuneItemIds(): Set<String> {
        return normalizedItemIdsByTier(enhancementRuneItemIdsByTier, enhancementRuneItemId).values.toSet()
    }

    fun protectionRuneItemIds(): Set<String> {
        return normalizedItemIdsByTier(protectionRuneItemIdsByTier, protectionRuneItemId).values.toSet()
    }

    fun enhancementRuneBonusPctForItem(itemId: String): Double {
        if (itemId.isBlank()) return 0.0
        val normalizedId = itemId.trim()
        val itemIdsByTier = normalizedItemIdsByTier(enhancementRuneItemIdsByTier, enhancementRuneItemId)
        val tier = itemIdsByTier.entries.firstOrNull { (_, id) -> id.equals(normalizedId, ignoreCase = true) }?.key
        if (tier != null) {
            return enhancementRuneBonusPctByTier[normalizedTierKey(tier)]?.coerceAtLeast(0.0)
                ?: enhancementRuneBonusPctPerUnit.coerceAtLeast(0.0)
        }
        return if (normalizedId.equals(enhancementRuneItemId, ignoreCase = true)) {
            enhancementRuneBonusPctByTier[defaultTierKey()]?.coerceAtLeast(0.0)
                ?: enhancementRuneBonusPctPerUnit.coerceAtLeast(0.0)
        } else {
            0.0
        }
    }

    fun sortedEnhancementRuneItemIdsByBonusDesc(): List<String> {
        val itemIdsByTier = normalizedItemIdsByTier(enhancementRuneItemIdsByTier, enhancementRuneItemId)
        return itemIdsByTier.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, String>> { entry ->
                    enhancementRuneBonusPctByTier[normalizedTierKey(entry.key)] ?: enhancementRuneBonusPctPerUnit
                }.thenBy { it.key }
            )
            .map { it.value }
    }

    fun sortedProtectionRuneItemIdsByTier(): List<String> {
        val itemIdsByTier = normalizedItemIdsByTier(protectionRuneItemIdsByTier, protectionRuneItemId)
        return itemIdsByTier.entries
            .sortedWith(
                compareBy<Map.Entry<String, String>> { tierRank(it.key) }
                    .thenBy { it.key }
            )
            .map { it.value }
    }

    fun baseChanceForLevel(level: Int): Double {
        return chanceAtLevel(baseChanceByLevel, level).coerceIn(0.0, 100.0)
    }

    fun breakChanceForLevel(level: Int): Double {
        return chanceAtLevel(breakChanceByLevel, level).coerceIn(0.0, 100.0)
    }

    fun rarityCostMultiplier(rarityName: String): Double {
        val normalized = rarityName.trim().uppercase()
        return rarityCostMultiplierByName[normalized]?.coerceAtLeast(0.1) ?: 1.0
    }

    fun growthPctFor(slot: EquipSlot?, type: ItemType): Double {
        val category = enchantCategory(slot, type)
        return growthPctByCategory[category]?.coerceAtLeast(0.0)
            ?: growthPctByCategory["default"]?.coerceAtLeast(0.0)
            ?: 0.0
    }

    private fun chanceAtLevel(table: Map<Int, Double>, level: Int): Double {
        if (table.isEmpty()) return 0.0
        table[level]?.let { return it }
        val normalizedLevel = level.coerceAtLeast(0)
        val nearestLower = table.keys.filter { it <= normalizedLevel }.maxOrNull()
        if (nearestLower != null) return table.getValue(nearestLower)
        val nearestUpper = table.keys.minOrNull() ?: return 0.0
        return table.getValue(nearestUpper)
    }

    private fun enchantCategory(slot: EquipSlot?, type: ItemType): String {
        if (type != ItemType.EQUIPMENT) return "default"
        return when (slot) {
            EquipSlot.WEAPON_MAIN -> "weapon_main"
            EquipSlot.WEAPON_OFF -> "weapon_off"
            EquipSlot.ALJAVA -> "quiver"
            EquipSlot.ACCESSORY -> "accessory"
            EquipSlot.HEAD,
            EquipSlot.CHEST,
            EquipSlot.LEGS,
            EquipSlot.BOOTS,
            EquipSlot.GLOVES,
            EquipSlot.CAPE -> "armor"
            else -> "default"
        }
    }

    private fun normalizedItemIdsByTier(
        itemIdsByTier: Map<String, String>,
        fallbackItemId: String
    ): Map<String, String> {
        val normalized = linkedMapOf<String, String>()
        for ((tier, itemId) in itemIdsByTier) {
            if (itemId.isBlank()) continue
            normalized[normalizedTierKey(tier)] = itemId.trim()
        }
        val defaultKey = defaultTierKey()
        if (normalized.isEmpty()) {
            if (fallbackItemId.isNotBlank()) {
                normalized[defaultKey] = fallbackItemId.trim()
            }
            return normalized
        }
        if (fallbackItemId.isNotBlank() && normalized.values.none { it.equals(fallbackItemId.trim(), ignoreCase = true) }) {
            normalized.putIfAbsent(defaultKey, fallbackItemId.trim())
        }
        return normalized
    }

    private fun normalizedTierKey(value: String): String = value.trim().lowercase()
    private fun defaultTierKey(): String = "common"

    private fun tierRank(value: String): Int = when (normalizedTierKey(value)) {
        "common" -> 0
        "uncommon" -> 1
        "rare" -> 2
        "epic" -> 3
        "legendary" -> 4
        else -> 100
    }
}

private fun defaultBaseChanceByLevel(): Map<Int, Double> = mapOf(
    0 to 100.0,
    1 to 95.0,
    2 to 90.0,
    3 to 86.0,
    4 to 82.0,
    5 to 76.0,
    6 to 69.0,
    7 to 61.0,
    8 to 54.0,
    9 to 46.0,
    10 to 38.0,
    11 to 31.0,
    12 to 25.0,
    13 to 19.0,
    14 to 13.0
)

private fun defaultBreakChanceByLevel(): Map<Int, Double> = mapOf(
    0 to 0.0,
    1 to 0.0,
    2 to 0.0,
    3 to 2.0,
    4 to 3.0,
    5 to 5.0,
    6 to 7.0,
    7 to 10.0,
    8 to 13.0,
    9 to 16.0,
    10 to 20.0,
    11 to 25.0,
    12 to 30.0,
    13 to 35.0,
    14 to 40.0
)

private fun defaultRarityCostMultipliers(): Map<String, Double> = mapOf(
    "COMMON" to 1.0,
    "UNCOMMON" to 1.08,
    "RARE" to 1.18,
    "EPIC" to 1.32,
    "LEGENDARY" to 1.55,
    "MYTHIC" to 1.85
)

private fun defaultEnhancementRuneItemIdsByTier(): Map<String, String> = mapOf(
    "common" to "enchant_rune_aprimoramento",
    "uncommon" to "enchant_rune_aprimoramento_uncommon",
    "rare" to "enchant_rune_aprimoramento_rare",
    "epic" to "enchant_rune_aprimoramento_epic",
    "legendary" to "enchant_rune_aprimoramento_legendary"
)

private fun defaultProtectionRuneItemIdsByTier(): Map<String, String> = mapOf(
    "common" to "enchant_rune_protecao",
    "uncommon" to "enchant_rune_protecao_uncommon",
    "rare" to "enchant_rune_protecao_rare",
    "epic" to "enchant_rune_protecao_epic",
    "legendary" to "enchant_rune_protecao_legendary"
)

private fun defaultEnhancementRuneBonusPctByTier(): Map<String, Double> = mapOf(
    "common" to 5.0,
    "uncommon" to 10.0,
    "rare" to 20.0,
    "epic" to 25.0,
    "legendary" to 30.0
)

private fun defaultGrowthPctByCategory(): Map<String, Double> = mapOf(
    "weapon_main" to 0.080,
    "weapon_off" to 0.062,
    "quiver" to 0.060,
    "armor" to 0.052,
    "accessory" to 0.046,
    "default" to 0.048
)
