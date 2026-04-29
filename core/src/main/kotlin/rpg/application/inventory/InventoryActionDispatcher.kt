package rpg.application.inventory

import rpg.application.GameActionResult
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class InventoryActionDispatcher(
    private val commandService: InventoryCommandService,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenInventory -> openInventory(session)
            GameAction.OpenEquipped -> move(session, NavigationState.Equipped)
            GameAction.OpenInventoryFilters -> move(session, NavigationState.InventoryFilters)
            GameAction.OpenQuiver -> move(session, NavigationState.Quiver)
            is GameAction.InspectInventoryItem -> GameActionResult(
                session = session.copy(
                    navigation = NavigationState.InventoryItemDetail,
                    selectedInventoryItemId = action.itemId,
                    messages = emptyList()
                )
            )

            is GameAction.InspectEquippedSlot -> GameActionResult(
                session = session.copy(
                    navigation = NavigationState.EquippedItemDetail,
                    selectedEquipmentSlot = action.slotKey,
                    messages = emptyList()
                )
            )

            is GameAction.EquipInventoryItem -> mutate(
                session,
                NavigationState.Inventory,
                null,
                null
            ) { commandService.equipItem(it, action.itemId) }

            is GameAction.UseInventoryItem -> mutate(
                session,
                NavigationState.Inventory,
                null,
                null
            ) { commandService.useItem(it, action.itemId) }

            is GameAction.SellInventoryItem -> mutate(
                session,
                NavigationState.Inventory,
                null,
                null
            ) { commandService.sellInventoryItem(it, action.itemId) }

            is GameAction.UnequipSlot -> mutate(
                session,
                NavigationState.Equipped,
                null,
                null
            ) { commandService.unequipSlot(it, action.slotKey) }

            is GameAction.SetInventoryFilterType -> GameActionResult(
                session = session.copy(
                    inventoryFilter = session.inventoryFilter.copy(type = action.type),
                    navigation = NavigationState.Inventory,
                    messages = emptyList()
                )
            )

            is GameAction.SetInventoryMinimumRarity -> GameActionResult(
                session = session.copy(
                    inventoryFilter = session.inventoryFilter.copy(minimumRarity = action.rarity),
                    navigation = NavigationState.Inventory,
                    messages = emptyList()
                )
            )

            GameAction.ClearInventoryFilters -> GameActionResult(
                session = session.copy(
                    inventoryFilter = InventoryFilterState(),
                    navigation = NavigationState.Inventory,
                    messages = emptyList()
                )
            )

            is GameAction.SelectActiveAmmo -> mutate(
                session,
                NavigationState.Quiver,
                null,
                null
            ) { commandService.selectActiveAmmo(it, action.templateId) }

            is GameAction.LoadAmmoToQuiver -> mutate(
                session,
                NavigationState.Quiver,
                null,
                null
            ) { commandService.loadAmmoToQuiver(it, action.itemId) }

            is GameAction.UnloadAmmoFromQuiver -> mutate(
                session,
                NavigationState.Quiver,
                null,
                null
            ) { commandService.unloadAmmoFromQuiver(it, action.itemId) }

            is GameAction.SellLoadedAmmo -> mutate(
                session,
                NavigationState.Quiver,
                null,
                null
            ) { commandService.sellLoadedAmmo(it, action.itemId) }

            else -> null
        }
    }

    private fun openInventory(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val returnNavigation = if (
            session.navigation == NavigationState.Exploration &&
            state.currentRun != null
        ) {
            NavigationState.Exploration
        } else {
            null
        }
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.Inventory,
                inventoryReturnNavigation = returnNavigation,
                messages = emptyList()
            )
        )
    }

    private fun move(session: GameSession, destination: NavigationState): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = destination,
                messages = emptyList()
            )
        )
    }

    private fun mutate(
        session: GameSession,
        navigationAfter: NavigationState,
        selectedInventoryItemId: String?,
        selectedEquipmentSlot: String?,
        block: (rpg.model.GameState) -> InventoryMutationResult
    ): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val mutation = block(normalized)
        val updatedState = stateSupport.normalize(mutation.state)
        return GameActionResult(
            session = session.copy(
                gameState = updatedState,
                navigation = navigationAfter,
                selectedInventoryItemId = selectedInventoryItemId,
                selectedEquipmentSlot = selectedEquipmentSlot,
                messages = mutation.messages
            )
        )
    }
}
