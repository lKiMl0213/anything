package rpg.application.enchant

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class EnchantActionDispatcher(
    private val commandService: EnchantCommandService,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenEnchantMenu -> move(session, NavigationState.ProductionEnchantMenu)
            GameAction.OpenEnchantItemList -> move(session, NavigationState.ProductionEnchantList)
            is GameAction.InspectEnchantItem -> inspectItem(session, action.itemId)
            GameAction.CycleEnchantEnhancementRunes -> cycleEnhancementRunes(session)
            GameAction.ToggleEnchantProtectionRune -> toggleProtection(session)
            is GameAction.AttemptEnchantItem -> queueAttempt(session, action)
            is GameAction.ExecuteEnchantItem -> executeAttempt(session, action)
            else -> null
        }
    }

    private fun move(session: GameSession, destination: NavigationState): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = destination,
                selectedEnchantItemId = null,
                selectedEnchantEnhancementRunes = 0,
                selectedEnchantUseProtectionRune = false,
                messages = emptyList()
            )
        )
    }

    private fun inspectItem(session: GameSession, itemId: String): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionEnchantDetail,
                selectedEnchantItemId = itemId,
                selectedEnchantEnhancementRunes = 0,
                selectedEnchantUseProtectionRune = false,
                messages = emptyList()
            )
        )
    }

    private fun cycleEnhancementRunes(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val selectedItem = session.selectedEnchantItemId
            ?: return GameActionResult(session.copy(messages = listOf("Selecione um item primeiro.")))
        val max = commandService.maxEnhancementRunesPerAttempt().coerceAtLeast(0)
        val presets = listOf(0, 1, 3, 5, 10, 15, 20)
            .map { it.coerceAtMost(max) }
            .distinct()
            .sorted()
            .ifEmpty { listOf(0) }
        val current = session.selectedEnchantEnhancementRunes.coerceIn(0, max)
        val next = presets.firstOrNull { it > current } ?: presets.first()
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionEnchantDetail,
                selectedEnchantItemId = selectedItem,
                selectedEnchantEnhancementRunes = next,
                messages = listOf("Runas de aprimoramento selecionadas: $next")
            )
        )
    }

    private fun toggleProtection(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val selectedItem = session.selectedEnchantItemId
            ?: return GameActionResult(session.copy(messages = listOf("Selecione um item primeiro.")))
        val next = !session.selectedEnchantUseProtectionRune
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionEnchantDetail,
                selectedEnchantItemId = selectedItem,
                selectedEnchantUseProtectionRune = next,
                messages = listOf(if (next) "Runa de proteção: SIM" else "Runa de proteção: Não")
            )
        )
    }

    private fun queueAttempt(session: GameSession, action: GameAction.AttemptEnchantItem): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val preparation = commandService.prepareAttempt(
            state = normalized,
            itemId = action.itemId,
            enhancementRunes = action.enhancementRunes,
            useProtectionRune = action.useProtectionRune
        )
        val timedView = preparation.timedActionView
        if (!preparation.ready || timedView == null) {
            return GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    navigation = NavigationState.ProductionEnchantDetail,
                    selectedEnchantItemId = action.itemId,
                    messages = preparation.messages
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.ProductionEnchantDetail,
                selectedEnchantItemId = action.itemId,
                messages = emptyList()
            ),
            effect = GameEffect.LaunchProductionTimedAction(
                view = timedView,
                completionAction = GameAction.ExecuteEnchantItem(
                    itemId = action.itemId,
                    enhancementRunes = action.enhancementRunes,
                    useProtectionRune = action.useProtectionRune
                )
            )
        )
    }

    private fun executeAttempt(session: GameSession, action: GameAction.ExecuteEnchantItem): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val mutation = commandService.attempt(
            state = normalized,
            itemId = action.itemId,
            enhancementRunes = action.enhancementRunes,
            useProtectionRune = action.useProtectionRune
        )
        val selectedItem = mutation.selectedItemId
        val destination = if (selectedItem == null) {
            NavigationState.ProductionEnchantList
        } else {
            NavigationState.ProductionEnchantDetail
        }
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(mutation.state),
                navigation = destination,
                selectedEnchantItemId = selectedItem,
                selectedEnchantEnhancementRunes = action.enhancementRunes,
                selectedEnchantUseProtectionRune = action.useProtectionRune,
                messages = mutation.messages
            )
        )
    }
}



