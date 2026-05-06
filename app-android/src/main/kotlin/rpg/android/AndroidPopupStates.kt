package rpg.android

internal data class SellQuantityState(
    val itemId: String,
    val quantity: Int,
    val maxQuantity: Int,
    val unitValue: Int
)
