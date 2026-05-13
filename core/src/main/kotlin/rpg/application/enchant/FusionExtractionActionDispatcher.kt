package rpg.application.enchant

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class FusionExtractionActionDispatcher(
    private val fusionCommandService: FusionCommandService,
    private val extractionCommandService: ExtractionCommandService,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenFusionMenu -> openFusion(session)
            GameAction.OpenExtractionMenu -> openExtraction(session)
            is GameAction.SelectFusionSlot1 -> selectFusionSlot1(session, action.itemId)
            is GameAction.SelectFusionSlot2 -> selectFusionSlot2(session, action.itemId)
            is GameAction.AttemptFusion -> queueFusion(session, action)
            is GameAction.ExecuteFusion -> executeFusion(session, action)
            is GameAction.SelectExtractionItem -> selectExtractionItem(session, action.itemId)
            GameAction.ToggleExtractionRemovalScroll -> toggleRemovalScroll(session)
            GameAction.ToggleExtractionProtectionScroll -> toggleProtectionScroll(session)
            is GameAction.AttemptExtraction -> queueExtraction(session, action)
            is GameAction.ExecuteExtraction -> executeExtraction(session, action)
            else -> null
        }
    }

    private fun openFusion(session: GameSession): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionFusionSlot1,
                selectedFusionSlot1ItemId = null,
                selectedFusionSlot2ItemId = null,
                messages = emptyList()
            )
        )
    }

    private fun openExtraction(session: GameSession): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionExtractionSlot1,
                selectedExtractionItemId = null,
                selectedExtractionUseRemovalScroll = false,
                selectedExtractionUseProtectionScroll = false,
                messages = emptyList()
            )
        )
    }

    private fun selectFusionSlot1(session: GameSession, itemId: String): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionFusionSlot2,
                selectedFusionSlot1ItemId = itemId,
                selectedFusionSlot2ItemId = null,
                messages = emptyList()
            )
        )
    }

    private fun selectFusionSlot2(session: GameSession, itemId: String): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        val slot1 = session.selectedFusionSlot1ItemId
            ?: return GameActionResult(session.copy(messages = listOf("Selecione o Slot 1 primeiro.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionFusionPreview,
                selectedFusionSlot1ItemId = slot1,
                selectedFusionSlot2ItemId = itemId,
                messages = emptyList()
            )
        )
    }

    private fun queueFusion(session: GameSession, action: GameAction.AttemptFusion): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        val normalized = stateSupport.normalize(state)
        val preparation = fusionCommandService.prepare(normalized, action.slot1ItemId, action.slot2ItemId)
        val timedView = preparation.timedActionView
        if (!preparation.ready || timedView == null) {
            return GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    navigation = NavigationState.ProductionFusionPreview,
                    selectedFusionSlot1ItemId = action.slot1ItemId,
                    selectedFusionSlot2ItemId = action.slot2ItemId,
                    messages = preparation.messages
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.ProductionFusionPreview,
                selectedFusionSlot1ItemId = action.slot1ItemId,
                selectedFusionSlot2ItemId = action.slot2ItemId,
                messages = emptyList()
            ),
            effect = GameEffect.LaunchProductionTimedAction(
                view = timedView,
                completionAction = GameAction.ExecuteFusion(action.slot1ItemId, action.slot2ItemId)
            )
        )
    }

    private fun executeFusion(session: GameSession, action: GameAction.ExecuteFusion): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        val normalized = stateSupport.normalize(state)
        val mutation = fusionCommandService.fuse(normalized, action.slot1ItemId, action.slot2ItemId)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(mutation.state),
                navigation = NavigationState.ProductionFusionSlot1,
                selectedFusionSlot1ItemId = null,
                selectedFusionSlot2ItemId = null,
                messages = mutation.messages
            )
        )
    }

    private fun selectExtractionItem(session: GameSession, itemId: String): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionExtractionPreview,
                selectedExtractionItemId = itemId,
                selectedExtractionUseRemovalScroll = false,
                selectedExtractionUseProtectionScroll = false,
                messages = emptyList()
            )
        )
    }

    private fun toggleRemovalScroll(session: GameSession): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        val selectedItem = session.selectedExtractionItemId
            ?: return GameActionResult(session.copy(messages = listOf("Selecione um item primeiro.")))
        val next = !session.selectedExtractionUseRemovalScroll
        val protection = if (next) session.selectedExtractionUseProtectionScroll else false
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionExtractionPreview,
                selectedExtractionItemId = selectedItem,
                selectedExtractionUseRemovalScroll = next,
                selectedExtractionUseProtectionScroll = protection,
                messages = listOf(if (next) "Pergaminho de remoção: SIM" else "Pergaminho de remoção: Não")
            )
        )
    }

    private fun toggleProtectionScroll(session: GameSession): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        val selectedItem = session.selectedExtractionItemId
            ?: return GameActionResult(session.copy(messages = listOf("Selecione um item primeiro.")))
        val removalEnabled = session.selectedExtractionUseRemovalScroll
        if (!removalEnabled) {
            return GameActionResult(
                session = session.copy(
                    gameState = stateSupport.normalize(state),
                    navigation = NavigationState.ProductionExtractionPreview,
                    selectedExtractionItemId = selectedItem,
                    selectedExtractionUseProtectionScroll = false,
                    messages = listOf("Ative o pergaminho de remoção antes da proteção.")
                )
            )
        }
        val next = !session.selectedExtractionUseProtectionScroll
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.ProductionExtractionPreview,
                selectedExtractionItemId = selectedItem,
                selectedExtractionUseRemovalScroll = true,
                selectedExtractionUseProtectionScroll = next,
                messages = listOf(if (next) "Pergaminho de proteção: SIM" else "Pergaminho de proteção: Não")
            )
        )
    }

    private fun queueExtraction(session: GameSession, action: GameAction.AttemptExtraction): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        val normalized = stateSupport.normalize(state)
        val preparation = extractionCommandService.prepare(
            state = normalized,
            itemId = action.itemId,
            useRemovalScroll = action.useRemovalScroll,
            useProtectionScroll = action.useProtectionScroll
        )
        val timedView = preparation.timedActionView
        if (!preparation.ready || timedView == null) {
            return GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    navigation = NavigationState.ProductionExtractionPreview,
                    selectedExtractionItemId = action.itemId,
                    selectedExtractionUseRemovalScroll = action.useRemovalScroll,
                    selectedExtractionUseProtectionScroll = action.useProtectionScroll,
                    messages = preparation.messages
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.ProductionExtractionPreview,
                selectedExtractionItemId = action.itemId,
                selectedExtractionUseRemovalScroll = action.useRemovalScroll,
                selectedExtractionUseProtectionScroll = action.useProtectionScroll,
                messages = emptyList()
            ),
            effect = GameEffect.LaunchProductionTimedAction(
                view = timedView,
                completionAction = GameAction.ExecuteExtraction(
                    itemId = action.itemId,
                    useRemovalScroll = action.useRemovalScroll,
                    useProtectionScroll = action.useProtectionScroll
                )
            )
        )
    }

    private fun executeExtraction(session: GameSession, action: GameAction.ExecuteExtraction): GameActionResult {
        val state = session.gameState ?: return missingState(session)
        val normalized = stateSupport.normalize(state)
        val mutation = extractionCommandService.extract(
            state = normalized,
            itemId = action.itemId,
            useRemovalScroll = action.useRemovalScroll,
            useProtectionScroll = action.useProtectionScroll
        )
        val destination = if (mutation.selectedItemId == null) {
            NavigationState.ProductionExtractionSlot1
        } else {
            NavigationState.ProductionExtractionPreview
        }
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(mutation.state),
                navigation = destination,
                selectedExtractionItemId = mutation.selectedItemId,
                selectedExtractionUseRemovalScroll = if (mutation.selectedItemId == null) false else action.useRemovalScroll,
                selectedExtractionUseProtectionScroll = if (mutation.selectedItemId == null) false else action.useProtectionScroll,
                messages = mutation.messages
            )
        )
    }

    private fun missingState(session: GameSession): GameActionResult {
        return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
    }
}



