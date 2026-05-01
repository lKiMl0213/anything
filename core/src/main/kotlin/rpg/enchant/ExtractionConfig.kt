package rpg.enchant

import kotlinx.serialization.Serializable

@Serializable
data class ExtractionConfig(
    val enchantStoneTemplateId: String = "enchant_stone",
    val enchantStoneTemplateIdByLevel: Map<Int, String> = defaultEnchantStoneTemplateIdByLevel(),
    val removalScrollItemId: String = "extract_scroll_remocao",
    val protectionScrollItemId: String = "extract_scroll_protecao",
    val removalScrollItemIdsByTier: Map<String, String> = defaultRemovalScrollItemIdsByTier(),
    val protectionScrollItemIdsByTier: Map<String, String> = defaultProtectionScrollItemIdsByTier(),
    val removalScrollChanceMultiplierByTier: Map<String, Double> = defaultRemovalScrollChanceMultiplierByTier(),
    val minimumItemLevel: Int = 5,
    val noScrollChanceMultiplier: Double = 0.12,
    val noScrollChanceCapPct: Double = 8.0,
    val withScrollChanceMultiplier: Double = 1.9,
    val withScrollFlatBonusPct: Double = 12.0,
    val withScrollMinChancePct: Double = 52.0,
    val withScrollCapPct: Double = 96.0,
    val goldBaseCost: Int = 180,
    val goldPerItemLevel: Double = 9.0,
    val goldPerEnchantLevel: Double = 68.0,
    val rarityCostMultiplierByName: Map<String, Double> = defaultExtractionRarityCostMultipliers(),
    val baseDurationSeconds: Double = 7.0,
    val minDurationSeconds: Double = 1.5,
    val stoneValuePerEnchantLevel: Int = 42,
    val attemptBaseXp: Double = 9.0,
    val successBonusXp: Double = 16.0,
    val xpPerEnchantLevelDifficulty: Double = 0.06
) {
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

    fun removalScrollItemIds(): Set<String> {
        return normalizedItemIdsByTier(removalScrollItemIdsByTier, removalScrollItemId).values.toSet()
    }

    fun protectionScrollItemIds(): Set<String> {
        return normalizedItemIdsByTier(protectionScrollItemIdsByTier, protectionScrollItemId).values.toSet()
    }

    fun sortedRemovalScrollItemIdsByTierDesc(): List<String> {
        val idsByTier = normalizedItemIdsByTier(removalScrollItemIdsByTier, removalScrollItemId)
        return idsByTier.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, String>> { tierRank(it.key) }
                    .thenBy { it.key }
            )
            .map { it.value }
    }

    fun sortedProtectionScrollItemIdsByTierAsc(): List<String> {
        val idsByTier = normalizedItemIdsByTier(protectionScrollItemIdsByTier, protectionScrollItemId)
        return idsByTier.entries
            .sortedWith(
                compareBy<Map.Entry<String, String>> { tierRank(it.key) }
                    .thenBy { it.key }
            )
            .map { it.value }
    }

    fun removalScrollChanceMultiplier(itemId: String): Double {
        if (itemId.isBlank()) return 1.0
        val normalizedId = itemId.trim()
        val idsByTier = normalizedItemIdsByTier(removalScrollItemIdsByTier, removalScrollItemId)
        val tier = idsByTier.entries.firstOrNull { (_, id) -> id.equals(normalizedId, ignoreCase = true) }?.key
        if (tier != null) {
            return removalScrollChanceMultiplierByTier[normalizedTierKey(tier)]?.coerceAtLeast(0.1) ?: 1.0
        }
        if (normalizedId.equals(removalScrollItemId, ignoreCase = true)) {
            return removalScrollChanceMultiplierByTier[defaultTierKey()]?.coerceAtLeast(0.1) ?: 1.0
        }
        return 1.0
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

private fun defaultExtractionRarityCostMultipliers(): Map<String, Double> = mapOf(
    "COMMON" to 1.0,
    "UNCOMMON" to 1.1,
    "RARE" to 1.2,
    "EPIC" to 1.35,
    "LEGENDARY" to 1.6,
    "MYTHIC" to 1.9
)

private fun defaultRemovalScrollItemIdsByTier(): Map<String, String> = mapOf(
    "common" to "extract_scroll_remocao",
    "uncommon" to "extract_scroll_remocao_uncommon",
    "rare" to "extract_scroll_remocao_rare",
    "epic" to "extract_scroll_remocao_epic",
    "legendary" to "extract_scroll_remocao_legendary"
)

private fun defaultProtectionScrollItemIdsByTier(): Map<String, String> = mapOf(
    "common" to "extract_scroll_protecao",
    "uncommon" to "extract_scroll_protecao_uncommon",
    "rare" to "extract_scroll_protecao_rare",
    "epic" to "extract_scroll_protecao_epic",
    "legendary" to "extract_scroll_protecao_legendary"
)

private fun defaultRemovalScrollChanceMultiplierByTier(): Map<String, Double> = mapOf(
    "common" to 1.0,
    "uncommon" to 1.08,
    "rare" to 1.16,
    "epic" to 1.24,
    "legendary" to 1.32
)

private fun defaultEnchantStoneTemplateIdByLevel(): Map<Int, String> = buildMap {
    put(0, "enchant_stone")
    for (level in 1..15) {
        put(level, "enchant_stone_tier_$level")
    }
}
