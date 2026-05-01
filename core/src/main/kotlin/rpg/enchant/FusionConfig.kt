package rpg.enchant

import kotlinx.serialization.Serializable

@Serializable
data class FusionConfig(
    val enchantStoneTemplateId: String = "enchant_stone",
    val enchantStoneTemplateIdByLevel: Map<Int, String> = defaultFusionStoneTemplateIdByLevel(),
    val maxEnchantLevel: Int = 15,
    val minimumEquipmentLevel: Int = 5,
    val successMultiplierByMode: Map<String, Double> = defaultSuccessMultiplierByMode(),
    val minSuccessChancePct: Double = 8.0,
    val maxSuccessChancePct: Double = 92.0,
    val upgradeChanceByBaseLevel: Map<Int, Double> = defaultUpgradeChanceByBaseLevel(),
    val failurePenaltyMaxByLevel: Map<Int, Int> = defaultFailurePenaltyMaxByLevel(),
    val failureStoneLevelFloor: Int = 0,
    val goldBaseCost: Int = 200,
    val goldPerItemLevel: Double = 8.0,
    val goldPerEnchantLevel: Double = 62.0,
    val rarityCostMultiplierByName: Map<String, Double> = defaultRarityCostMultipliers(),
    val costMultiplierByMode: Map<String, Double> = defaultCostMultiplierByMode(),
    val baseDurationSeconds: Double = 9.0,
    val minDurationSeconds: Double = 1.5,
    val equipmentPowerVariancePct: Double = 0.03,
    val stoneValuePerEnchantLevel: Int = 38,
    val attemptBaseXp: Double = 10.0,
    val successBonusXp: Double = 12.0,
    val xpPerEnchantLevelDifficulty: Double = 0.06
) {
    fun successMultiplier(mode: FusionMode): Double {
        return successMultiplierByMode[mode.configKey]?.coerceAtLeast(0.01) ?: 1.0
    }

    fun costMultiplier(mode: FusionMode): Double {
        return costMultiplierByMode[mode.configKey]?.coerceAtLeast(0.1) ?: 1.0
    }

    fun upgradeChanceForBaseLevel(baseLevel: Int): Double {
        return nearestLevelValue(upgradeChanceByBaseLevel, baseLevel).coerceIn(0.0, 100.0)
    }

    fun failurePenaltyForBaseLevel(baseLevel: Int): Int {
        return nearestLevelIntValue(failurePenaltyMaxByLevel, baseLevel).coerceAtLeast(1)
    }

    fun rarityCostMultiplier(rarityName: String): Double {
        return rarityCostMultiplierByName[rarityName.trim().uppercase()]?.coerceAtLeast(0.1) ?: 1.0
    }

    fun enchantStoneTemplateIdForLevel(level: Int): String {
        val normalizedLevel = level.coerceAtLeast(0)
        if (enchantStoneTemplateIdByLevel.isEmpty()) return enchantStoneTemplateId
        enchantStoneTemplateIdByLevel[normalizedLevel]?.let { if (it.isNotBlank()) return it.trim() }
        val nearestLower = enchantStoneTemplateIdByLevel.keys.filter { it <= normalizedLevel }.maxOrNull()
        if (nearestLower != null) {
            val id = enchantStoneTemplateIdByLevel.getValue(nearestLower).trim()
            if (id.isNotBlank()) return id
        }
        return enchantStoneTemplateId
    }

    fun enchantStoneTemplateIds(): Set<String> {
        val ids = linkedSetOf<String>()
        if (enchantStoneTemplateId.isNotBlank()) ids += enchantStoneTemplateId.trim()
        enchantStoneTemplateIdByLevel.values.mapTo(ids) { it.trim() }
        return ids.filterTo(linkedSetOf()) { it.isNotBlank() }
    }

    private fun nearestLevelValue(table: Map<Int, Double>, level: Int): Double {
        if (table.isEmpty()) return 0.0
        table[level]?.let { return it }
        val normalized = level.coerceAtLeast(0)
        val nearestLower = table.keys.filter { it <= normalized }.maxOrNull()
        if (nearestLower != null) return table.getValue(nearestLower)
        val nearestUpper = table.keys.minOrNull() ?: return 0.0
        return table.getValue(nearestUpper)
    }

    private fun nearestLevelIntValue(table: Map<Int, Int>, level: Int): Int {
        if (table.isEmpty()) return 1
        table[level]?.let { return it }
        val normalized = level.coerceAtLeast(0)
        val nearestLower = table.keys.filter { it <= normalized }.maxOrNull()
        if (nearestLower != null) return table.getValue(nearestLower)
        val nearestUpper = table.keys.minOrNull() ?: return 1
        return table.getValue(nearestUpper)
    }
}

private fun defaultSuccessMultiplierByMode(): Map<String, Double> = mapOf(
    "equipment_equipment" to 0.86,
    "stone_stone" to 0.92,
    "stone_equipment" to 1.0
)

private fun defaultUpgradeChanceByBaseLevel(): Map<Int, Double> = mapOf(
    0 to 70.0,
    3 to 58.0,
    6 to 48.0,
    9 to 36.0,
    12 to 22.0,
    14 to 14.0
)

private fun defaultFailurePenaltyMaxByLevel(): Map<Int, Int> = mapOf(
    0 to 1,
    4 to 2,
    8 to 3,
    12 to 4
)

private fun defaultRarityCostMultipliers(): Map<String, Double> = mapOf(
    "COMMON" to 1.0,
    "UNCOMMON" to 1.1,
    "RARE" to 1.22,
    "EPIC" to 1.36,
    "LEGENDARY" to 1.6,
    "MYTHIC" to 1.9
)

private fun defaultCostMultiplierByMode(): Map<String, Double> = mapOf(
    "equipment_equipment" to 1.15,
    "stone_stone" to 0.92,
    "stone_equipment" to 1.0
)

private fun defaultFusionStoneTemplateIdByLevel(): Map<Int, String> = buildMap {
    put(0, "enchant_stone")
    for (level in 1..15) {
        put(level, "enchant_stone_tier_$level")
    }
}
