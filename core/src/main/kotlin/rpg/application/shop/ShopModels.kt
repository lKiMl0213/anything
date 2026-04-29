package rpg.application.shop

import rpg.model.ShopCurrency

enum class ShopCategory(val label: String) {
    WEAPONS("Armas"),
    ARMORS("Armaduras"),
    ITEMS("Itens"),
    ACCESSORIES("Acessorios")
}

enum class WeaponClassCategory(val label: String) {
    WARRIOR("Espadachim"),
    ARCHER("Arqueiro"),
    MAGE("Mago"),
    GENERAL("Geral")
}

enum class UpgradeMenuCategory(val label: String) {
    PRODUCTION("Producao"),
    BATTLE("Batalha"),
    UTILITY("Utilidade")
}

data class ShopCategorySummary(
    val category: ShopCategory,
    val count: Int
)

data class ShopDisplayEntry(
    val id: String,
    val itemId: String,
    val name: String,
    val description: String,
    val quantity: Int,
    val requiredLevel: Int,
    val finalPrice: Int,
    val currency: ShopCurrency,
    val category: ShopCategory,
    val weaponClassCategory: WeaponClassCategory,
    val inStock: Boolean,
    val specialOffer: Boolean
)

data class UpgradeCategorySummary(
    val category: UpgradeMenuCategory,
    val count: Int
)

data class UpgradeDisplayEntry(
    val id: String,
    val name: String,
    val description: String,
    val level: Int,
    val maxLevel: Int,
    val currentLabel: String,
    val nextLabel: String,
    val costs: List<rpg.model.PermanentUpgradeCostDef>,
    val atMaxLevel: Boolean
)

data class ShopMutationResult(
    val state: rpg.model.GameState,
    val messages: List<String>
)
