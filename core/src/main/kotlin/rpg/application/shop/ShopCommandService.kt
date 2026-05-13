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
import rpg.premium.PremiumSupport
import kotlin.math.roundToInt

class ShopCommandService(
    private val engine: GameEngine,
    private val repo: DataRepository,
    private val queryService: ShopQueryService,
    private val permanentUpgradeService: PermanentUpgradeService,
    private val achievementTracker: AchievementTracker
) {
    fun buyCashPack(
        state: GameState,
        packId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ShopMutationResult {
        val pack = repo.cashPacks.values.firstOrNull {
            it.enabled && it.id.equals(packId, ignoreCase = true)
        } ?: return ShopMutationResult(state, listOf("Pacote de cash nao encontrado."))
        val player = state.player
        val firstBonus = !player.cashFirstPurchaseBonusConsumed
        val welcomeBackBonus = !firstBonus && PremiumSupport.cashWelcomeBackEligible(player, nowMillis)
        val bonusPct = when {
            firstBonus -> 10
            welcomeBackBonus -> 10
            else -> 0
        }
        val finalCash = if (bonusPct <= 0) {
            pack.premiumCashAmount
        } else {
            (pack.premiumCashAmount * (1.0 + bonusPct / 100.0)).roundToInt()
        }
        val updatedPlayer = player.copy(
            premiumCash = player.premiumCash + finalCash,
            cashFirstPurchaseBonusConsumed = true,
            lastCashPurchaseEpochMs = nowMillis
        )
        val bonusLine = when {
            firstBonus -> "Bonus aplicado: primeira compra +10%."
            welcomeBackBonus -> "Bonus aplicado: bem-vindo de volta +10%."
            else -> "Sem bonus adicional nesta compra."
        }
        return ShopMutationResult(
            state = state.copy(player = updatedPlayer),
            messages = listOf(
                "Compra concluida: ${pack.name} -> +$finalCash CASH (${pack.platformPriceLabel}).",
                bonusLine
            )
        )
    }

    fun buyPremiumPlan(
        state: GameState,
        planId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ShopMutationResult {
        val plan = queryService.premiumPlans().firstOrNull { it.id.equals(planId, ignoreCase = true) }
            ?: return ShopMutationResult(state, listOf("Plano premium nao encontrado."))
        val player = state.player
        val paidPlayer = when (plan.currency) {
            ShopCurrency.GOLD -> {
                if (player.gold < plan.cost) {
                    return ShopMutationResult(state, listOf("Ouro insuficiente."))
                }
                player.copy(gold = player.gold - plan.cost)
            }

            ShopCurrency.CASH -> {
                if (player.premiumCash < plan.cost) {
                    return ShopMutationResult(state, listOf("CASH insuficiente."))
                }
                player.copy(premiumCash = player.premiumCash - plan.cost)
            }
        }
        val upgradedPlayer = if (plan.currency == ShopCurrency.GOLD && plan.cost > 0) {
            achievementTracker.onGoldSpent(paidPlayer, plan.cost.toLong()).player
        } else {
            paidPlayer
        }
        val updatedPlayer = upgradedPlayer.let { basePlayer ->
            if (plan.permanent) {
                PremiumSupport.applyPremiumPermanent(basePlayer)
            } else {
                PremiumSupport.applyPremiumDuration(
                    player = basePlayer,
                    days = plan.durationDays ?: 0,
                    nowMillis = nowMillis
                )
            }
        }
        val message = if (plan.permanent) {
            "Premium permanente ativado com sucesso."
        } else {
            "Premium ativado por ${plan.durationDays} dias."
        }
        return ShopMutationResult(
            state = state.copy(player = updatedPlayer),
            messages = listOf(message)
        )
    }

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
