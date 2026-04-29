package rpg.application.production

import rpg.application.GameActionResult
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
            is GameAction.CraftRecipe -> craftRecipe(session, action)
            is GameAction.OpenGatheringType -> openGatheringType(session, action)
            is GameAction.GatherNode -> gatherNode(session, action)
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

    private fun craftRecipe(session: GameSession, action: GameAction.CraftRecipe): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val discipline = session.selectedCraftDiscipline
            ?: return GameActionResult(session.copy(messages = listOf("Escolha uma disciplina de craft.")))
        val normalized = stateSupport.normalize(state)
        val mutation = commandService.craft(normalized, discipline, action.recipeId)
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

    private fun gatherNode(session: GameSession, action: GameAction.GatherNode): GameActionResult {
        val state = session.gameState ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val type = session.selectedGatheringType
            ?: return GameActionResult(session.copy(messages = listOf("Escolha um tipo de coleta.")))
        val normalized = stateSupport.normalize(state)
        val mutation = commandService.gather(normalized, type, action.nodeId)
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(mutation.state),
                navigation = NavigationState.ProductionGatheringList,
                messages = mutation.messages
            )
        )
    }
}
