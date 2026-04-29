package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.shop.ShopCategory
import rpg.application.shop.ShopQueryService
import rpg.application.shop.UpgradeMenuCategory
import rpg.application.shop.WeaponClassCategory
import rpg.model.ShopCurrency
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
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
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
                state.player.level < entry.requiredLevel -> "Indisponivel (requer nivel ${entry.requiredLevel})"
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
            body += "Nenhum item disponivel nesta categoria."
        } else {
            body += "Itens em destaque:"
            entries.take(5).forEach { entry ->
                body += "- ${entry.name}: ${entry.description.ifBlank { "Sem descricao." }}"
            }
            if (entries.size > 5) body += "... (${entries.size - 5} itens adicionais nas opcoes)."
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
        if (currency == ShopCurrency.GOLD) {
            options += ScreenOptionViewModel("1", "Usar loja Ouro", GameAction.SetShopCurrency(ShopCurrency.GOLD))
            options += ScreenOptionViewModel("2", "Trocar para loja CASH", GameAction.SetShopCurrency(ShopCurrency.CASH))
        } else {
            options += ScreenOptionViewModel("1", "Trocar para loja Ouro", GameAction.SetShopCurrency(ShopCurrency.GOLD))
            options += ScreenOptionViewModel("2", "Usar loja CASH", GameAction.SetShopCurrency(ShopCurrency.CASH))
        }
        var index = 3
        categories.forEach { summary ->
            options += ScreenOptionViewModel(
                key = index.toString(),
                label = "${summary.category.label} (${summary.count})",
                action = GameAction.OpenUpgradeCategory(summary.category)
            )
            index++
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Aprimoramentos (${currencyLabel(currency)})",
            summary = support.playerSummary(state),
            bodyLines = listOf("Selecione uma categoria de aprimoramentos permanentes."),
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
            if (upgrade.atMaxLevel) return@forEach
            val cost = upgrade.costs.firstOrNull() ?: return@forEach
            val costLabel = renderUpgradeCost(cost)
            options += ScreenOptionViewModel(
                key = index.toString(),
                label = "${upgrade.name} (${upgrade.level}/${upgrade.maxLevel}) -> custo $costLabel",
                action = GameAction.BuyUpgrade(upgrade.id, cost.id, currency)
            )
            index++
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf<String>()
        body += "Categoria: ${category.label}"
        body += "Saldo atual: ${if (currency == ShopCurrency.GOLD) state.player.gold else state.player.premiumCash} ${currencyLabel(currency)}"
        if (upgrades.isEmpty()) {
            body += "Nenhum aprimoramento disponivel."
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
        "profession_mastery" -> "Aumenta a XP de forja, coleta de ervas, mineracao e pesca."
        "fishermans_instinct" -> "Chance de pesca render coleta dobrada."
        else -> "Sem descricao."
    }

    private fun currencyLabel(currency: ShopCurrency): String = if (currency == ShopCurrency.GOLD) "ouro" else "CASH"
}
