package rpg.application.shop

import rpg.achievement.AchievementTracker
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.ShopCurrency
import rpg.progression.PermanentUpgradeService
import rpg.io.DataRepository

class ShopCommandService(
    private val engine: GameEngine,
    private val repo: DataRepository,
    private val queryService: ShopQueryService,
    private val permanentUpgradeService: PermanentUpgradeService,
    private val achievementTracker: AchievementTracker
) {
    fun buyEntry(
        state: GameState,
        currency: ShopCurrency,
        category: ShopCategory,
        weaponClass: WeaponClassCategory?,
        entryId: String
    ): ShopMutationResult {
        val player = state.player
        val display = queryService.entries(player, state.itemInstances, currency, category, weaponClass)
            .firstOrNull { it.id == entryId }
            ?: return ShopMutationResult(state, listOf("Item nao encontrado nesta categoria."))
        val sourceEntry = findShopEntryById(entryId, currency)
            ?: return ShopMutationResult(state, listOf("Item da loja nao encontrado."))

        if (!display.inStock) {
            return ShopMutationResult(state, listOf("Item indisponivel no estoque desta rodada."))
        }
        if (player.level < display.requiredLevel) {
            return ShopMutationResult(state, listOf("Nivel insuficiente. Requer nivel ${display.requiredLevel}."))
        }
        if (currency == ShopCurrency.GOLD && player.gold < display.finalPrice) {
            return ShopMutationResult(state, listOf("Ouro insuficiente."))
        }
        if (currency == ShopCurrency.CASH && player.premiumCash < display.finalPrice) {
            return ShopMutationResult(state, listOf("CASH insuficiente."))
        }

        val qty = sourceEntry.quantity.coerceAtLeast(1)
        val workingInstances = state.itemInstances.toMutableMap()
        val incoming = mutableListOf<String>()
        repeat(qty) {
            if (engine.itemRegistry.isTemplate(sourceEntry.itemId)) {
                val template = engine.itemRegistry.template(sourceEntry.itemId)
                    ?: return ShopMutationResult(state, listOf("Template invalido: ${sourceEntry.itemId}."))
                val generated = engine.itemEngine.generateFromTemplate(
                    template = template,
                    level = maxOf(player.level, template.minLevel),
                    rarity = template.rarity
                )
                workingInstances[generated.id] = generated
                incoming += generated.id
            } else {
                if (engine.itemRegistry.item(sourceEntry.itemId) == null) {
                    return ShopMutationResult(state, listOf("Item invalido: ${sourceEntry.itemId}."))
                }
                incoming += sourceEntry.itemId
            }
        }

        val insertion = InventorySystem.addItemsWithLimit(
            player = player,
            itemInstances = workingInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = incoming
        )
        if (insertion.rejected.isNotEmpty()) {
            return ShopMutationResult(state, listOf("Inventario sem espaco para essa compra."))
        }

        var updatedPlayer = when (currency) {
            ShopCurrency.GOLD -> player.copy(gold = player.gold - display.finalPrice)
            ShopCurrency.CASH -> player.copy(premiumCash = player.premiumCash - display.finalPrice)
        }
        updatedPlayer = updatedPlayer.copy(
            inventory = insertion.inventory,
            quiverInventory = insertion.quiverInventory,
            selectedAmmoTemplateId = insertion.selectedAmmoTemplateId
        )
        if (currency == ShopCurrency.GOLD && display.finalPrice > 0) {
            updatedPlayer = achievementTracker.onGoldSpent(updatedPlayer, display.finalPrice.toLong()).player
        }

        val updatedState = state.copy(
            player = updatedPlayer,
            itemInstances = workingInstances.toMap()
        )
        return ShopMutationResult(
            state = updatedState,
            messages = listOf(
                "Compra concluida: ${display.name} x${display.quantity} por ${display.finalPrice} ${currencyLabel(currency)}."
            )
        )
    }

    fun purchaseUpgrade(
        state: GameState,
        currency: ShopCurrency,
        upgradeId: String,
        costId: String
    ): ShopMutationResult {
        val purchase = permanentUpgradeService.purchase(
            player = state.player,
            itemInstances = state.itemInstances,
            upgradeId = upgradeId,
            costId = costId,
            shopCurrency = currency
        )
        if (!purchase.success) {
            return ShopMutationResult(state, listOf(purchase.message))
        }
        var updatedPlayer = purchase.player
        if (purchase.spentGold > 0) {
            updatedPlayer = achievementTracker.onGoldSpent(updatedPlayer, purchase.spentGold.toLong()).player
        }
        return ShopMutationResult(
            state = state.copy(player = updatedPlayer, itemInstances = purchase.itemInstances),
            messages = listOf(purchase.message)
        )
    }

    private fun findShopEntryById(id: String, currency: ShopCurrency): rpg.model.ShopEntryDef? {
        return repo.shopEntries.values.firstOrNull {
            it.enabled && it.currency == currency && it.id.equals(id, ignoreCase = true)
        }
    }

    private fun currencyLabel(currency: ShopCurrency): String = if (currency == ShopCurrency.GOLD) "ouro" else "CASH"
}
