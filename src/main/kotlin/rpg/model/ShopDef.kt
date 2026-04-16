package rpg.model

import kotlinx.serialization.Serializable

@Serializable
enum class ShopCurrency {
    GOLD,
    CASH
}

@Serializable
data class ShopEntryDef(
    val id: String,
    val name: String,
    val itemId: String,
    val price: Int,
    val currency: ShopCurrency,
    val quantity: Int = 1,
    val minPlayerLevel: Int = 1,
    val description: String = "",
    val enabled: Boolean = true,
    val tags: List<String> = emptyList()
)

@Serializable
data class CashPackDef(
    val id: String,
    val name: String,
    val premiumCashAmount: Int,
    val platformPriceLabel: String = "",
    val description: String = "",
    val enabled: Boolean = true
)
