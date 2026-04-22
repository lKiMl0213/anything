package rpg.application.progression

import rpg.application.GameActionResult
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class ProgressionActionDispatcher(
    private val questQueryService: QuestQueryService,
    private val questCommandService: QuestCommandService,
    private val achievementCommandService: AchievementCommandService,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenQuests -> move(session, NavigationState.QuestBoard)
            GameAction.OpenClassQuest -> openClassQuest(session)
            is GameAction.OpenQuestSection -> openQuestSection(session, action.section)
            is GameAction.InspectQuest -> inspectQuest(session, action.section, action.instanceId)
            is GameAction.AcceptQuest -> mutateQuest(session, NavigationState.QuestList, session.selectedQuestSection, null) {
                questCommandService.acceptQuest(it, action.instanceId)
            }
            is GameAction.CancelQuest -> mutateQuest(session, NavigationState.QuestList, session.selectedQuestSection, null) {
                questCommandService.cancelQuest(it, action.instanceId)
            }
            is GameAction.ReplaceQuest -> mutateQuest(session, NavigationState.QuestList, session.selectedQuestSection, null) {
                questCommandService.replaceQuest(it, action.section, action.instanceId)
            }
            is GameAction.ClaimQuest -> mutateQuest(session, NavigationState.QuestList, session.selectedQuestSection, null) {
                questCommandService.claimQuest(it, action.instanceId)
            }
            is GameAction.ChooseClassQuestPath -> mutateQuest(session, NavigationState.ClassQuest, null, null) {
                questCommandService.chooseClassQuestPath(it, action.pathId)
            }
            GameAction.RequestCancelClassQuest -> GameActionResult(
                session = session.copy(
                    navigation = NavigationState.ClassQuestCancelConfirm,
                    messages = emptyList()
                )
            )
            GameAction.ConfirmCancelClassQuest -> mutateQuest(session, NavigationState.ClassQuest, null, null) {
                questCommandService.cancelClassQuest(it)
            }
            GameAction.OpenAchievements -> move(session, NavigationState.Achievements)
            is GameAction.OpenAchievementCategory -> GameActionResult(
                session = session.copy(
                    navigation = NavigationState.AchievementCategory,
                    selectedAchievementCategory = action.category,
                    selectedAchievementId = null,
                    messages = emptyList()
                )
            )
            is GameAction.InspectAchievement -> GameActionResult(
                session = session.copy(
                    navigation = NavigationState.AchievementDetail,
                    selectedAchievementCategory = action.category,
                    selectedAchievementId = action.achievementId,
                    messages = emptyList()
                )
            )
            is GameAction.ClaimAchievementReward -> mutateQuest(
                session,
                NavigationState.AchievementDetail,
                null,
                null
            ) {
                achievementCommandService.claimReward(it, action.achievementId)
            }
            GameAction.OpenAchievementStatistics -> move(session, NavigationState.AchievementStatistics)
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

    private fun openClassQuest(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val view = questQueryService.classQuestView(normalized)
            ?: return GameActionResult(session.copy(messages = listOf("Nenhuma quest de classe disponivel no momento.")))
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.ClassQuest,
                messages = emptyList()
            )
        )
    }

    private fun openQuestSection(session: GameSession, section: QuestSection): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = NavigationState.QuestList,
                selectedQuestSection = section,
                selectedQuestId = null,
                messages = emptyList()
            )
        )
    }

    private fun inspectQuest(session: GameSession, section: QuestSection, instanceId: String): GameActionResult {
        return GameActionResult(
            session = session.copy(
                navigation = NavigationState.QuestDetail,
                selectedQuestSection = section,
                selectedQuestId = instanceId,
                messages = emptyList()
            )
        )
    }

    private fun mutateQuest(
        session: GameSession,
        navigationAfter: NavigationState,
        selectedQuestSection: QuestSection?,
        selectedQuestId: String?,
        block: (rpg.model.GameState) -> ProgressionMutationResult
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
                selectedQuestSection = selectedQuestSection,
                selectedQuestId = selectedQuestId,
                messages = mutation.messages
            )
        )
    }
}
