package rpg.android

import rpg.android.state.PopupDetailUiModel
import rpg.android.state.PopupQuantityUiModel
import rpg.application.GameSession
import rpg.model.ItemType
import rpg.navigation.NavigationState

internal fun buildAndroidPopupDetail(
    session: GameSession,
    deps: RuntimeDeps,
    onUseItem: (String) -> Unit,
    onEquipItem: (String) -> Unit,
    onSell: (String, Int) -> Unit,
    onUnequip: (String) -> Unit,
    sellQuantityState: SellQuantityState?,
    onDecreaseSellQuantity: () -> Unit,
    onIncreaseSellQuantity: () -> Unit
): PopupDetailUiModel? {
    val state = session.gameState ?: return null
    return when (session.navigation) {
        NavigationState.InventoryItemDetail -> {
            val itemId = session.selectedInventoryItemId ?: return null
            val detail = deps.inventoryQueryService.inventoryItemDetail(state, itemId) ?: return null
            val primary = when (detail.item.type) {
                ItemType.CONSUMABLE -> "Usar" to { onUseItem(itemId) }
                ItemType.EQUIPMENT -> "Equipar" to { onEquipItem(itemId) }
                else -> null
            }
            val maxQty = detail.quantity.coerceAtLeast(1)
            val currentQty = sellQuantityState
                ?.takeIf { it.itemId == itemId }
                ?.quantity
                ?.coerceIn(1, maxQty)
                ?: 1
            val totalValue = detail.saleValue * currentQty
            PopupDetailUiModel(
                title = detail.item.name,
                lines = detail.detailLines + detail.comparisonLines,
                primaryLabel = primary?.first,
                onPrimary = primary?.second,
                secondaryLabel = "Vender (${totalValue} ouro)",
                onSecondary = { onSell(itemId, currentQty) },
                quantity = if (maxQty > 1) {
                    PopupQuantityUiModel(
                        value = currentQty,
                        minValue = 1,
                        maxValue = maxQty,
                        unitValue = detail.saleValue,
                        totalValue = totalValue,
                        onDecrease = onDecreaseSellQuantity,
                        onIncrease = onIncreaseSellQuantity
                    )
                } else {
                    null
                }
            )
        }

        NavigationState.EquippedItemDetail -> {
            val slot = session.selectedEquipmentSlot ?: return null
            val detail = deps.inventoryQueryService.equippedDetail(state, slot) ?: return null
            PopupDetailUiModel(
                title = detail.item.name,
                lines = detail.detailLines + detail.removalLines,
                primaryLabel = "Desequipar",
                onPrimary = { onUnequip(slot) }
            )
        }

        else -> null
    }
}
