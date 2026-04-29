package rpg.application.shop

import rpg.application.GameActionResult
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.model.ShopCurrency
import rpg.navigation.NavigationState

class ShopActionDispatcher(
    private val queryService: ShopQueryService,
    private val commandService: ShopCommandService,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenGoldShop -> openShopCategories(session, ShopCurrency.GOLD)
            GameAction.OpenCashShop -> openShopCategories(session, ShopCurrency.CASH)
            GameAction.OpenUpgradeShop -> openUpgradeCategories(session, ShopCurrency.GOLD)
            is GameAction.SetShopCurrency -> setCurrency(session, action.currency)
            is GameAction.OpenShopCategory -> openShopItems(session, action.category)
            is GameAction.SetShopWeaponClass -> setWeaponClass(session, action.category)
            is GameAction.BuyShopEntry -> buyShopEntry(session, action)
            is GameAction.OpenUpgradeCategory -> openUpgradeList(session, action.category)
            is GameAction.BuyUpgrade -> buyUpgrade(session, action)
            else -> null
        }
    }

    private fun openShopCategories(session: GameSession, currency: ShopCurrency): GameActionResult {
        val state = session.gameState ?: return missing(session)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                selectedShopCurrency = currency,
                selectedShopCategory = null,
                selectedWeaponClassCategory = null,
                navigation = NavigationState.CityShopCategories,
                messages = emptyList()
            )
        )
    }

    private fun openUpgradeCategories(session: GameSession, currency: ShopCurrency): GameActionResult {
        val state = session.gameState ?: return missing(session)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                selectedShopCurrency = currency,
                selectedUpgradeCategory = null,
                navigation = NavigationState.CityUpgradeCategories,
                messages = emptyList()
            )
        )
    }

    private fun setCurrency(session: GameSession, currency: ShopCurrency): GameActionResult {
        val state = session.gameState ?: return missing(session)
        val targetNavigation = if (
            session.navigation == NavigationState.CityUpgradeCategories ||
            session.navigation == NavigationState.CityUpgradeList
        ) {
            NavigationState.CityUpgradeCategories
        } else {
            NavigationState.CityShopCategories
        }
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                selectedShopCurrency = currency,
                selectedShopCategory = null,
                selectedWeaponClassCategory = null,
                selectedUpgradeCategory = null,
                navigation = targetNavigation,
                messages = emptyList()
            )
        )
    }

    private fun openShopItems(session: GameSession, category: ShopCategory): GameActionResult {
        val state = session.gameState ?: return missing(session)
        val normalized = stateSupport.normalize(state)
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                selectedShopCategory = category,
                selectedWeaponClassCategory = if (category == ShopCategory.WEAPONS) {
                    session.selectedWeaponClassCategory ?: WeaponClassCategory.GENERAL
                } else {
                    null
                },
                navigation = NavigationState.CityShopItems,
                messages = emptyList()
            )
        )
    }

    private fun setWeaponClass(session: GameSession, category: WeaponClassCategory): GameActionResult {
        val state = session.gameState ?: return missing(session)
        if (session.selectedShopCategory != ShopCategory.WEAPONS) {
            return GameActionResult(session.copy(messages = listOf("Filtro de classe disponivel apenas para armas.")))
        }
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                selectedWeaponClassCategory = category,
                navigation = NavigationState.CityShopItems,
                messages = emptyList()
            )
        )
    }

    private fun buyShopEntry(session: GameSession, action: GameAction.BuyShopEntry): GameActionResult {
        val state = session.gameState ?: return missing(session)
        val currency = session.selectedShopCurrency
            ?: return GameActionResult(session.copy(messages = listOf("Selecione o tipo de loja antes de comprar.")))
        val category = session.selectedShopCategory
            ?: return GameActionResult(session.copy(messages = listOf("Selecione uma categoria da loja.")))
        val normalized = stateSupport.normalize(state)
        val mutation = commandService.buyEntry(
            state = normalized,
            currency = currency,
            category = category,
            weaponClass = session.selectedWeaponClassCategory,
            entryId = action.entryId
        )
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(mutation.state),
                navigation = NavigationState.CityShopItems,
                messages = mutation.messages
            )
        )
    }

    private fun openUpgradeList(session: GameSession, category: UpgradeMenuCategory): GameActionResult {
        val state = session.gameState ?: return missing(session)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                selectedUpgradeCategory = category,
                navigation = NavigationState.CityUpgradeList,
                messages = emptyList()
            )
        )
    }

    private fun buyUpgrade(session: GameSession, action: GameAction.BuyUpgrade): GameActionResult {
        val state = session.gameState ?: return missing(session)
        val normalized = stateSupport.normalize(state)
        val mutation = commandService.purchaseUpgrade(
            state = normalized,
            currency = action.currency,
            upgradeId = action.upgradeId,
            costId = action.costId
        )
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(mutation.state),
                navigation = NavigationState.CityUpgradeList,
                messages = mutation.messages
            )
        )
    }

    private fun missing(session: GameSession): GameActionResult {
        return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
    }
}
