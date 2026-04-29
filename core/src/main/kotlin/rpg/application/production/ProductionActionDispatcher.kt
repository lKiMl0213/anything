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
                selectedGatheringType = null,
                navigation = NavigationState.ProductionRecipeList,
                messages = emptyList()
            )
        )
    }

    private fun queueCraftRecipe(session: GameSession, action: GameAction.CraftRecipe): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val discipline = session.selectedCraftDiscipline
            ?: return GameActionResult(session.copy(messages = listOf("Escolha uma disciplina de craft.")))
        val normalized = stateSupport.normalize(state)
        val preparation = commandService.prepareCraft(normalized, discipline, action.recipeId)
        val timedView = preparation.timedActionView
        if (!preparation.ready || timedView == null) {
            return GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    navigation = NavigationState.ProductionRecipeList,
                    messages = preparation.messages
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.ProductionRecipeList,
                messages = emptyList()
            ),
            effect = GameEffect.LaunchProductionTimedAction(
                view = timedView,
                completionAction = GameAction.ExecuteCraftRecipe(discipline = discipline, recipeId = action.recipeId)
            )
        )
    }

    private fun executeCraftRecipe(session: GameSession, action: GameAction.ExecuteCraftRecipe): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val mutation = commandService.craft(normalized, action.discipline, action.recipeId)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(mutation.state),
                navigation = NavigationState.ProductionRecipeList,
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
