package rpg.application.character

import rpg.application.GameActionResult
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class CharacterActionDispatcher(
    private val queryService: CharacterQueryService,
    private val commandService: CharacterCommandService,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenAttributes -> move(session, NavigationState.Attributes)
            is GameAction.InspectAttribute -> GameActionResult(
                session = session.copy(
                    navigation = NavigationState.AttributeDetail,
                    selectedAttributeCode = action.code,
                    messages = emptyList()
                )
            )

            is GameAction.AllocateAttributePoint -> mutate(
                session,
                NavigationState.Attributes,
                null,
                null,
                null
            ) { commandService.allocateAttributePoint(it, action.code) }

            GameAction.OpenTalents -> move(session, NavigationState.Talents)
            is GameAction.OpenTalentStage -> openTalentStage(session, action.stage)
            is GameAction.InspectTalentNode -> GameActionResult(
                session = session.copy(
                    navigation = NavigationState.TalentNodeDetail,
                    selectedTalentNodeId = action.nodeId,
                    messages = emptyList()
                )
            )

            is GameAction.ConfirmTalentRankUp -> confirmTalentRankUp(session, action.nodeId)
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

    private fun openTalentStage(session: GameSession, stage: Int): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val stageView = queryService.talentStage(normalized, stage)
            ?: return GameActionResult(session.copy(messages = listOf("Essa etapa da classe ainda esta bloqueada.")))
        if (stageView.treeId == null) {
            return GameActionResult(session.copy(messages = listOf("Essa etapa da classe ainda esta bloqueada.")))
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.TalentTreeDetail,
                selectedTalentTreeId = stageView.treeId,
                selectedTalentNodeId = null,
                messages = emptyList()
            )
        )
    }

    private fun confirmTalentRankUp(session: GameSession, nodeId: String): GameActionResult {
        val treeId = session.selectedTalentTreeId
            ?: return GameActionResult(session.copy(messages = listOf("Nenhuma arvore de talento selecionada.")))
        return mutate(
            session,
            NavigationState.TalentTreeDetail,
            null,
            treeId,
            null
        ) { commandService.rankUpTalentNode(it, treeId, nodeId) }
    }

    private fun mutate(
        session: GameSession,
        navigationAfter: NavigationState,
        selectedAttributeCode: String?,
        selectedTalentTreeId: String?,
        selectedTalentNodeId: String?,
        block: (rpg.model.GameState) -> CharacterMutationResult
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
                selectedAttributeCode = selectedAttributeCode,
                selectedTalentTreeId = selectedTalentTreeId,
                selectedTalentNodeId = selectedTalentNodeId,
                messages = mutation.messages
            )
        )
    }
}
