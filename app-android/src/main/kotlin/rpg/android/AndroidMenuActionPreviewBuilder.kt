package rpg.android

import rpg.android.state.MenuActionPreviewUiModel
import rpg.android.state.MenuQuantityPickerUiModel
import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.shop.ShopCategory
import rpg.application.shop.UpgradeMenuCategory
import rpg.application.shop.WeaponClassCategory
import rpg.classquest.ClassQuestTagRules
import rpg.item.ResolvedItem
import rpg.model.ShopCurrency
import rpg.presentation.model.MenuScreenViewModel

internal fun buildMenuActionPreviews(
    session: GameSession,
    viewModel: MenuScreenViewModel,
    deps: RuntimeDeps
): Map<String, MenuActionPreviewUiModel> {
    val state = session.gameState ?: return emptyMap()
    val previews = mutableMapOf<String, MenuActionPreviewUiModel>()
    val requiresShopEntryPreview = viewModel.options.any { it.action is GameAction.BuyShopEntry }
    val requiresUpgradePreview = viewModel.options.any { option ->
        when (val action = option.action) {
            is GameAction.BuyUpgrade -> action.costId != "__max__" && action.costId != "__unavailable__"
            else -> false
        }
    }
    val shopQuery = deps.actionHandler.shopQueryService()
    val inventorySupport = deps.inventoryQueryService.support()
    val currency = session.selectedShopCurrency ?: ShopCurrency.GOLD
    val selectedCategory = session.selectedShopCategory ?: ShopCategory.ITEMS
    val weaponClass = if (selectedCategory == ShopCategory.WEAPONS) {
        session.selectedWeaponClassCategory ?: WeaponClassCategory.GENERAL
    } else {
        null
    }
    val shopEntries by lazy {
        if (!requiresShopEntryPreview) {
            emptyList()
        } else {
            shopQuery.entries(
                player = state.player,
                itemInstances = state.itemInstances,
                currency = currency,
                category = selectedCategory,
                weaponClass = weaponClass
            )
        }
    }
    val upgrades by lazy {
        if (!requiresUpgradePreview) {
            emptyList()
        } else {
            shopQuery.upgrades(
                player = state.player,
                currency = currency,
                category = session.selectedUpgradeCategory ?: UpgradeMenuCategory.UTILITY
            )
        }
    }

    viewModel.options.forEach { option ->
        when (val action = option.action) {
            is GameAction.BuyShopEntry -> {
                val entry = shopEntries.firstOrNull { it.id == action.entryId } ?: return@forEach
                val resolved = deps.actionHandler.engine().itemResolver.resolve(entry.itemId, state.itemInstances)
                val lines = mutableListOf<String>()
                lines += "Onde usa/equipa: ${usageLabel(resolved, inventorySupport)}"
                lines += "Atributos: ${resolved?.let(inventorySupport::formatItemBonuses).orEmpty().ifBlank { "Sem bonus direto." }}"
                lines += "Valor: ${entry.finalPrice} ${currencyLabel(entry.currency)}"
                lines += "Classe permitida: ${classAllowedLabel(resolved, inventorySupport)}"
                if (entry.description.isNotBlank()) {
                    lines += entry.description
                }
                previews[option.key] = MenuActionPreviewUiModel(
                    optionKey = option.key,
                    title = entry.name,
                    lines = lines,
                    primaryLabel = "Confirmar compra",
                    primaryAction = action
                )
            }

            is GameAction.BuyUpgrade -> {
                if (action.costId == "__max__" || action.costId == "__unavailable__") return@forEach
                val upgrade = upgrades.firstOrNull { it.id == action.upgradeId } ?: return@forEach
                val selectedCost = upgrade.costs.firstOrNull { it.id == action.costId }
                val lines = mutableListOf<String>()
                if (upgrade.description.isNotBlank()) {
                    lines += upgrade.description
                }
                lines += "Nivel atual: ${upgrade.level}/${upgrade.maxLevel}"
                lines += "Atual: ${upgrade.currentLabel}"
                lines += "Proximo: ${upgrade.nextLabel}"
                lines += "Valor: ${selectedCost?.let(::renderUpgradeCost).orEmpty().ifBlank { "Indisponivel" }}"
                previews[option.key] = MenuActionPreviewUiModel(
                    optionKey = option.key,
                    title = upgrade.name,
                    lines = lines,
                    primaryLabel = "Comprar aprimoramento",
                    primaryAction = action
                )
            }

            is GameAction.InspectTalentNode -> {
                val treeId = session.selectedTalentTreeId ?: return@forEach
                val detail = deps.characterQueryService.talentNodeDetail(state, treeId, action.nodeId) ?: return@forEach
                val lines = detail.detailLines
                    .filter { it.isNotBlank() }
                    .take(6)
                val fullDetailLines = detail.detailLines.filter { it.isNotBlank() }
                if (detail.canRankUp) {
                    previews[option.key] = MenuActionPreviewUiModel(
                        optionKey = option.key,
                        title = detail.title,
                        lines = lines,
                        primaryLabel = "Aumentar nivel (custo ${detail.nextCost})",
                        primaryAction = GameAction.ConfirmTalentRankUp(action.nodeId),
                        secondaryLabel = "Ver detalhes",
                        secondaryAction = null,
                        detailPopupTitle = detail.title,
                        detailPopupLines = fullDetailLines
                    )
                } else {
                    previews[option.key] = MenuActionPreviewUiModel(
                        optionKey = option.key,
                        title = detail.title,
                        lines = lines,
                        primaryLabel = "Ver detalhes",
                        primaryAction = action,
                        detailPopupTitle = detail.title,
                        detailPopupLines = fullDetailLines
                    )
                }
            }

            is GameAction.ConfigureCraftRecipeQuantity -> {
                val minValue = 1
                val maxValue = action.maxQuantity.coerceAtLeast(minValue)
                val currentValue = if (session.selectedCraftRecipeId == action.recipeId) {
                    session.selectedCraftRecipeQuantity.coerceIn(minValue, maxValue)
                } else {
                    minValue
                }
                previews[option.key] = MenuActionPreviewUiModel(
                    optionKey = option.key,
                    title = "Quantidade de Craft",
                    lines = listOf(
                        "Defina quantas vezes a receita sera executada.",
                        "O limite ja considera CAP atual e recursos disponiveis."
                    ),
                    primaryLabel = "Aplicar",
                    primaryAction = GameAction.SetCraftRecipeQuantity(action.recipeId, currentValue),
                    secondaryLabel = "Cancelar",
                    secondaryAction = null,
                    quantityPicker = MenuQuantityPickerUiModel(
                        minValue = minValue,
                        maxValue = maxValue,
                        currentValue = currentValue,
                        applyAction = { quantity ->
                            GameAction.SetCraftRecipeQuantity(
                                recipeId = action.recipeId,
                                quantity = quantity.coerceIn(minValue, maxValue)
                            )
                        }
                    )
                )
            }

            else -> Unit
        }
    }
    return previews
}

private fun usageLabel(
    item: ResolvedItem?,
    inventorySupport: rpg.application.inventory.InventoryRulesSupport
): String {
    item ?: return "Item de uso geral."
    return when (item.type) {
        rpg.model.ItemType.EQUIPMENT -> {
            val slot = item.slot?.name?.let(inventorySupport::equippedSlotLabel) ?: "Slot indefinido"
            if (item.twoHanded) "$slot (duas maos)" else slot
        }
        rpg.model.ItemType.CONSUMABLE -> "Consumivel"
        rpg.model.ItemType.MATERIAL -> "Material de craft/coleta"
    }
}

private fun classAllowedLabel(
    item: ResolvedItem?,
    inventorySupport: rpg.application.inventory.InventoryRulesSupport
): String {
    item ?: return "Todas"
    val classTag = ClassQuestTagRules.effectiveClassTag(item.tags)
    return inventorySupport.classTagDisplayLabel(classTag)
}

private fun renderUpgradeCost(cost: rpg.model.PermanentUpgradeCostDef): String {
    val parts = mutableListOf<String>()
    if (cost.goldCost > 0) parts += "${cost.goldCost} ouro"
    if (cost.cashCost > 0) parts += "${cost.cashCost} CASH"
    val reqId = cost.requiredItemId?.takeIf { it.isNotBlank() }
    if (reqId != null && cost.requiredItemQty > 0) {
        parts += "$reqId x${cost.requiredItemQty}"
    }
    return parts.joinToString(" + ").ifBlank { "Sem custo" }
}

private fun currencyLabel(currency: ShopCurrency): String {
    return if (currency == ShopCurrency.GOLD) "ouro" else "CASH"
}
