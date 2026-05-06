package rpg.application.production

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class ProductionActionDispatcher(
    private val commandService: ProductionCommandService,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenCraftMenu -> move(session, NavigationState.ProductionCraftMenu)
            is GameAction.OpenCraftDiscipline -> openCraftDiscipline(session, action)
            is GameAction.InspectCraftRecipe -> inspectCraftRecipe(session, action)
            is GameAction.ConfigureCraftRecipeQuantity -> configureCraftRecipeQuantity(session, action)
            is GameAction.SetCraftRecipeQuantity -> setCraftRecipeQuantity(session, action)
            is GameAction.CraftRecipe -> queueCraftRecipe(session, action)
            is GameAction.ExecuteCraftRecipe -> executeCraftRecipe(session, action)
            is GameAction.OpenGatheringType -> openGatheringType(session, action)
            is GameAction.GatherNode -> queueGatherNode(session, action)
            is GameAction.ExecuteGatherNode -> executeGatherNode(session, action)
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
                messages = emptyList()
            )
        )
    }

    private fun openCraftDiscipline(session: GameSession, action: GameAction.OpenCraftDiscipline): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                selectedCraftDiscipline = action.discipline,
                selectedCraftRecipeId = null,
                selectedCraftRecipeQuantity = 1,
                selectedGatheringType = null,
                navigation = NavigationState.ProductionRecipeList,
                messages = emptyList()
            )
        )
    }

    private fun inspectCraftRecipe(session: GameSession, action: GameAction.InspectCraftRecipe): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        if (session.selectedCraftDiscipline == null) {
            return GameActionResult(session.copy(messages = listOf("Escolha uma disciplina de craft.")))
        }
        val currentQuantity = if (session.selectedCraftRecipeId == action.recipeId) {
            session.selectedCraftRecipeQuantity
        } else {
            1
        }
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                selectedCraftRecipeId = action.recipeId,
                selectedCraftRecipeQuantity = currentQuantity.coerceAtLeast(1),
                navigation = NavigationState.ProductionRecipeDetail,
                messages = emptyList()
            )
        )
    }

    private fun setCraftRecipeQuantity(session: GameSession, action: GameAction.SetCraftRecipeQuantity): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                selectedCraftRecipeId = action.recipeId,
                selectedCraftRecipeQuantity = action.quantity.coerceAtLeast(1),
                navigation = NavigationState.ProductionRecipeDetail,
                messages = listOf("Quantidade do lote definida para ${action.quantity.coerceAtLeast(1)}.")
            )
        )
    }

    private fun configureCraftRecipeQuantity(
        session: GameSession,
        action: GameAction.ConfigureCraftRecipeQuantity
    ): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val selectedQuantity = action.maxQuantity.coerceAtLeast(1)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                selectedCraftRecipeId = action.recipeId,
                selectedCraftRecipeQuantity = selectedQuantity,
                navigation = NavigationState.ProductionRecipeDetail,
                messages = listOf("Quantidade ajustada para ${selectedQuantity}x.")
            )
        )
    }

    private fun queueCraftRecipe(session: GameSession, action: GameAction.CraftRecipe): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val discipline = session.selectedCraftDiscipline
            ?: return GameActionResult(session.copy(messages = listOf("Escolha uma disciplina de craft.")))
        val quantity = if (session.selectedCraftRecipeId == action.recipeId) {
            session.selectedCraftRecipeQuantity
        } else {
            1
        }.coerceAtLeast(1)
        val normalized = stateSupport.normalize(state)
        val preparation = commandService.prepareCraft(
            state = normalized,
            discipline = discipline,
            recipeId = action.recipeId,
            times = quantity
        )
        val timedView = preparation.timedActionView
        if (!preparation.ready || timedView == null) {
            return GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    navigation = NavigationState.ProductionRecipeDetail,
                    selectedCraftRecipeId = action.recipeId,
                    messages = preparation.messages
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                selectedCraftRecipeId = action.recipeId,
                selectedCraftRecipeQuantity = quantity,
                navigation = NavigationState.ProductionRecipeDetail,
                messages = emptyList()
            ),
            effect = GameEffect.LaunchProductionTimedAction(
                view = timedView,
                completionAction = GameAction.ExecuteCraftRecipe(
                    discipline = discipline,
                    recipeId = action.recipeId,
                    times = quantity
                )
            )
        )
    }

    private fun executeCraftRecipe(session: GameSession, action: GameAction.ExecuteCraftRecipe): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val mutation = commandService.craft(
            state = normalized,
            discipline = action.discipline,
            recipeId = action.recipeId,
            times = action.times
        )
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(mutation.state),
                selectedCraftRecipeId = action.recipeId,
                selectedCraftRecipeQuantity = action.times.coerceAtLeast(1),
                navigation = NavigationState.ProductionRecipeDetail,
                messages = mutation.messages
            )
        )
    }

    private fun openGatheringType(session: GameSession, action: GameAction.OpenGatheringType): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                selectedGatheringType = action.type,
                selectedCraftDiscipline = null,
                navigation = NavigationState.ProductionGatheringList,
                messages = emptyList()
            )
        )
    }

    private fun queueGatherNode(session: GameSession, action: GameAction.GatherNode): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val type = session.selectedGatheringType
            ?: return GameActionResult(session.copy(messages = listOf("Escolha um tipo de coleta.")))
        val normalized = stateSupport.normalize(state)
        val preparation = commandService.prepareGather(normalized, type, action.nodeId)
        val timedView = preparation.timedActionView
        if (!preparation.ready || timedView == null) {
            return GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    navigation = NavigationState.ProductionGatheringList,
                    messages = preparation.messages
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.ProductionGatheringList,
                messages = emptyList()
            ),
            effect = GameEffect.LaunchProductionTimedAction(
                view = timedView,
                completionAction = GameAction.ExecuteGatherNode(type = type, nodeId = action.nodeId)
            )
        )
    }

    private fun executeGatherNode(session: GameSession, action: GameAction.ExecuteGatherNode): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val mutation = commandService.gather(normalized, action.type, action.nodeId)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(mutation.state),
                navigation = NavigationState.ProductionGatheringList,
                messages = mutation.messages
            )
        )
    }
}
