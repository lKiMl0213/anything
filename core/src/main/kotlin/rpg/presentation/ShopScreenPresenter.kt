package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.shop.ShopCategory
import rpg.application.shop.ShopQueryService
import rpg.application.shop.UpgradeMenuCategory
import rpg.application.shop.WeaponClassCategory
import rpg.model.ShopCurrency
import rpg.premium.PremiumSupport
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class ShopScreenPresenter(
    private val queryService: ShopQueryService,
    private val support: PresentationSupport
) {
    fun presentShopCategories(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Loja")
        val currency = session.selectedShopCurrency ?: ShopCurrency.GOLD
        val categories = queryService.categories(state.player, currency)
        val options = categories.mapIndexed { index, category ->
            ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = "${category.category.label} (${category.count})",
                action = GameAction.OpenShopCategory(category.category)
            )
        }.toMutableList()
        if (currency == ShopCurrency.CASH) {
            options += ScreenOptionViewModel(
                key = (options.size + 1).toString(),
                label = "Comprar Cash",
                action = GameAction.OpenCashTopUp
            )
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Loja (${currencyLabel(currency)})",
            summary = support.playerSummary(state),
            bodyLines = listOf(
                "Saldo atual: ${if (currency == ShopCurrency.GOLD) state.player.gold else state.player.premiumCash} ${currencyLabel(currency)}",
                "Selecione uma categoria."
            ),
            options = options,
            messages = session.messages
        )
    }

    fun presentCashTopUp(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Comprar Cash")
        val packs = queryService.cashPacks(state.player)
        val options = packs.mapIndexed { index, pack ->
            ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = "${pack.platformPriceLabel} | ${pack.finalCashAmount} CASH",
                action = GameAction.BuyCashPack(pack.id)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf<String>()
        body += "Selecione um pacote para adicionar CASH (simulacao local, sem pagamento real)."
        body += "Quanto maior o valor, melhor a taxa de CASH por R$."
        if (packs.isNotEmpty()) {
            body += "Bônus atual: ${packs.first().bonusLabel}"
            packs.forEach { pack ->
                body += "- ${pack.platformPriceLabel} -> ${pack.finalCashAmount} CASH: ${pack.description.ifBlank { "Pacote de recarga." }}"
            }
        }

        return MenuScreenViewModel(
            title = "Comprar Cash",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentPremiumShop(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Premium")
        val plans = queryService.premiumPlans()
        val options = plans.mapIndexed { index, plan ->
            val costLabel = if (plan.currency == ShopCurrency.GOLD) "${plan.cost} ouro" else "${plan.cost} CASH"
            ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = "${plan.label} | $costLabel",
                action = GameAction.BuyPremiumPlan(plan.id)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val active = PremiumSupport.isPremiumActive(state.player)
        val statusLine = if (state.player.premiumPermanent) {
            "Status premium: permanente"
        } else if (active) {
            "Status premium: ativo até ${java.time.Instant.ofEpochMilli(state.player.premiumExpiresAtEpochMs)}"
        } else {
            "Status premium: inativo"
        }

        val body = listOf(
            statusLine,
            "Beneficios premium:",
            "Missões: +10 aceitáveis, +10 diarias, +10 semanais, +10 mensais, +10 rerolls por categoria.",
            "Produção: -10% custo e +10% velocidade.",
            "Loja: -10% nos precos.",
            "XP: +20% (skills e batalha).",
            "Ouro em quests/vendas: +15%.",
            "Boss global: +3 runs gratis por dia."
        )

        return MenuScreenViewModel(
            title = "Premium",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentShopItems(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Loja")
        val currency = session.selectedShopCurrency ?: ShopCurrency.GOLD
        val category = session.selectedShopCategory ?: ShopCategory.ITEMS
        val weaponClass = if (category == ShopCategory.WEAPONS) {
            session.selectedWeaponClassCategory ?: WeaponClassCategory.GENERAL
        } else {
            null
        }
        val entries = queryService.entries(state.player, state.itemInstances, currency, category, weaponClass)
        val options = mutableListOf<ScreenOptionViewModel>()
        var cursor = 1
        if (category == ShopCategory.WEAPONS) {
            WeaponClassCategory.entries.forEach { filter ->
                options += ScreenOptionViewModel(
                    key = cursor.toString(),
                    label = "Filtro: ${filter.label}" + if (filter == weaponClass) " (ativo)" else "",
                    action = GameAction.SetShopWeaponClass(filter)
                )
                cursor++
            }
        }
        entries.forEach { entry ->
            val balance = if (currency == ShopCurrency.GOLD) state.player.gold else state.player.premiumCash
            val status = when {
                state.player.level < entry.requiredLevel -> "Indisponivel (requer nível ${entry.requiredLevel})"
                balance < entry.finalPrice -> "Indisponivel (saldo insuficiente)"
                else -> "Disponivel"
            }
            options += ScreenOptionViewModel(
                key = cursor.toString(),
                label = "${entry.name} x${entry.quantity} - ${entry.finalPrice} ${currencyLabel(currency)} | $status",
                action = GameAction.BuyShopEntry(entry.id)
            )
            cursor++
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf<String>()
        body += "Categoria: ${category.label}"
        body += "Saldo: ${if (currency == ShopCurrency.GOLD) state.player.gold else state.player.premiumCash} ${currencyLabel(currency)}"
        if (entries.isEmpty()) {
            body += "Nenhum item disponível nesta categoria."
        } else {
            body += "Itens em destaque:"
            entries.take(5).forEach { entry ->
                body += "- ${entry.name}: ${entry.description.ifBlank { "Sem descrição." }}"
            }
            if (entries.size > 5) body += "... (${entries.size - 5} itens adicionais nas opções)."
        }

        return MenuScreenViewModel(
            title = "Loja > ${category.label}",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentUpgradeCategories(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Aprimoramentos")
        val currency = session.selectedShopCurrency ?: ShopCurrency.GOLD
        val categories = queryService.upgradeCategories(currency)
        val options = mutableListOf<ScreenOptionViewModel>()
        categories.forEachIndexed { index, summary ->
            options += ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = "${summary.category.label} (${summary.count})",
                action = GameAction.OpenUpgradeCategory(summary.category)
            )
        }
        val switchedCurrency = if (currency == ShopCurrency.GOLD) ShopCurrency.CASH else ShopCurrency.GOLD
        options += ScreenOptionViewModel(
            key = (categories.size + 1).toString(),
            label = "Trocar moeda (${currencyMenuLabel(switchedCurrency)})",
            action = GameAction.SetShopCurrency(switchedCurrency)
        )
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Aprimoramentos (${currencyLabel(currency)})",
            summary = support.playerSummary(state),
            bodyLines = listOf(
                "Selecione uma categoria de aprimoramentos permanentes. (Moeda selecionada: ${currencyMenuLabel(currency)})"
            ),
            options = options,
            messages = session.messages
        )
    }

    fun presentUpgradeList(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Aprimoramentos")
        val currency = session.selectedShopCurrency ?: ShopCurrency.GOLD
        val category = session.selectedUpgradeCategory ?: UpgradeMenuCategory.UTILITY
        val upgrades = queryService.upgrades(state.player, currency, category)
        val options = mutableListOf<ScreenOptionViewModel>()
        var index = 1
        upgrades.forEach { upgrade ->
            if (upgrade.atMaxLevel) {
                options += ScreenOptionViewModel(
                    key = index.toString(),
                    label = "${upgrade.name} (${upgrade.level}/${upgrade.maxLevel}) -> MAX",
                    action = GameAction.BuyUpgrade(upgrade.id, "__max__", currency)
                )
            } else {
                val cost = upgrade.costs.firstOrNull()
                val costLabel = cost?.let(::renderUpgradeCost) ?: "indisponível"
                val costId = cost?.id ?: "__unavailable__"
                options += ScreenOptionViewModel(
                    key = index.toString(),
                    label = "${upgrade.name} (${upgrade.level}/${upgrade.maxLevel}) -> custo $costLabel",
                    action = GameAction.BuyUpgrade(upgrade.id, costId, currency)
                )
            }
            index++
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf<String>()
        body += "Categoria: ${category.label}"
        body += "Saldo atual: ${if (currency == ShopCurrency.GOLD) state.player.gold else state.player.premiumCash} ${currencyLabel(currency)}"
        if (upgrades.isEmpty()) {
            body += "Nenhum aprimoramento disponível."
        } else {
            upgrades.forEach { upgrade ->
                val description = upgrade.description.ifBlank {
                    fallbackUpgradeDescription(upgrade.id)
                }
                body += "- ${upgrade.name}: $description"
                body += "  Atual ${upgrade.currentLabel} | Proximo ${upgrade.nextLabel}"
                body += ""
            }
            if (body.lastOrNull().isNullOrBlank()) {
                body.removeAt(body.lastIndex)
            }
        }

        return MenuScreenViewModel(
            title = "Aprimoramentos > ${category.label}",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
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

    private fun fallbackUpgradeDescription(upgradeId: String): String = when (upgradeId.trim().lowercase()) {
        "profession_mastery" -> "Aumenta a XP de forja, coleta de ervas, mineração e pesca."
        "fishermans_instinct" -> "Chance de pesca render coleta dobrada."
        else -> "Sem descrição."
    }

    private fun currencyLabel(currency: ShopCurrency): String = if (currency == ShopCurrency.GOLD) "ouro" else "CASH"
    private fun currencyMenuLabel(currency: ShopCurrency): String = if (currency == ShopCurrency.GOLD) "OURO" else "CASH"
}




