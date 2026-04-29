package rpg.model

import kotlinx.serialization.Serializable

@Serializable
enum class PermanentUpgradeEffectType {
    PROFESSION_XP_BOOST,
    COMBAT_XP_BOOST,
    MONSTER_RARITY_BONUS,
    QUEST_ITEM_KEEP_CHANCE,
    CRAFT_BATCH_BONUS,
    FISHING_DOUBLE_CHANCE,
    HERBALISM_DOUBLE_CHANCE,
    MINING_DOUBLE_CHANCE,
    WOODCUTTING_DOUBLE_CHANCE,
    FORGE_COST_REDUCTION,
    COOKING_COST_REDUCTION,
    ALCHEMY_COST_REDUCTION,
    TAVERN_COST_REDUCTION,
    QUIVER_CAPACITY_BONUS
}

@Serializable
enum class PermanentUpgradeValueMode {
    PERCENT,
    FLAT
}

@Serializable
data class PermanentUpgradeCostDef(
    val id: String,
    val label: String = "",
    val shopCurrencies: List<ShopCurrency> = emptyList(),
    val goldCost: Int = 0,
    val cashCost: Int = 0,
    val requiredItemId: String? = null,
    val requiredItemQty: Int = 0
)

@Serializable
data class PermanentUpgradeLevelDef(
    val level: Int,
    val value: Double,
    val costs: List<PermanentUpgradeCostDef> = emptyList()
)

@Serializable
data class PermanentUpgradeDef(
    val id: String,
    val name: String,
    val description: String,
    val effectType: PermanentUpgradeEffectType,
    val valueMode: PermanentUpgradeValueMode = PermanentUpgradeValueMode.PERCENT,
    val levels: List<PermanentUpgradeLevelDef> = emptyList(),
    val enabled: Boolean = true,
    val tags: List<String> = emptyList()
) {
    val maxLevel: Int
        get() = levels.size
}
