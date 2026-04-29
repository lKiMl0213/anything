package rpg.application.creation

import rpg.application.GameActionResult
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.SaveGameGateway
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class CharacterCreationActionDispatcher(
    private val queryService: CharacterCreationQueryService,
    private val commandService: CharacterCreationCommandService,
    private val saveGateway: SaveGameGateway,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenCharacterCreationRace -> move(session, NavigationState.CharacterCreationRace)
            GameAction.OpenCharacterCreationClass -> move(session, NavigationState.CharacterCreationClass)
            GameAction.OpenCharacterCreationAttributes -> move(session, NavigationState.CharacterCreationAttributes)
            is GameAction.SetCharacterCreationName -> mutateDraft(session) { commandService.setName(it, action.name) }
            GameAction.CycleCharacterCreationName -> mutateDraft(session) { commandService.cycleName(it) }
            is GameAction.SelectCharacterCreationRace -> previewRace(session, action.raceId)
            GameAction.ConfirmCharacterCreationRace -> confirmRace(session)
            is GameAction.SelectCharacterCreationClass -> previewClass(session, action.classId)
            GameAction.ConfirmCharacterCreationClass -> confirmClass(session)
            is GameAction.EditCharacterCreationAttribute -> openAttributeDetail(session, action.code)
            is GameAction.IncreaseCharacterCreationAttribute -> {
                if (session.navigation == NavigationState.CharacterCreationAttributeDetail) {
                    mutateDraft(session, navigation = NavigationState.CharacterCreationAttributeDetail) {
                        commandService.increaseAttribute(it, action.code)
                    }
                } else {
                    openAttributeDetail(session, action.code)
                }
            }
            is GameAction.DecreaseCharacterCreationAttribute -> {
                val targetNavigation = if (session.navigation == NavigationState.CharacterCreationAttributeDetail) {
                    NavigationState.CharacterCreationAttributeDetail
                } else {
                    NavigationState.CharacterCreationAttributes
                }
                mutateDraft(session, navigation = targetNavigation) { commandService.decreaseAttribute(it, action.code) }
            }
            is GameAction.SetCharacterCreationAttribute -> {
                mutateDraft(session, navigation = NavigationState.CharacterCreationAttributes) {
                    commandService.setAttributeAllocation(it, action.code, action.allocated)
                }
            }
            GameAction.ConfirmCharacterCreation -> confirm(session)
            else -> null
        }
    }

    private fun move(session: GameSession, navigation: NavigationState): GameActionResult {
        val draft = session.creationDraft ?: queryService.initialDraft()
        return GameActionResult(
            session = session.copy(
                creationDraft = draft,
                navigation = navigation,
                selectedAttributeCode = if (navigation == NavigationState.CharacterCreationAttributeDetail) {
                    session.selectedAttributeCode
                } else {
                    null
                },
                selectedCreationRaceId = if (navigation == NavigationState.CharacterCreationRaceDetail) {
                    session.selectedCreationRaceId
                } else {
                    null
                },
                selectedCreationClassId = if (navigation == NavigationState.CharacterCreationClassDetail) {
                    session.selectedCreationClassId
                } else {
                    null
                },
                messages = emptyList()
            )
        )
    }

    private fun mutateDraft(
        session: GameSession,
        navigation: NavigationState = NavigationState.CharacterCreation,
        mutate: (CharacterCreationDraft) -> CharacterCreationDraft
    ): GameActionResult {
        val current = session.creationDraft ?: queryService.initialDraft()
        val updated = mutate(current)
        return GameActionResult(
            session = session.copy(
                creationDraft = updated,
                navigation = navigation,
                selectedAttributeCode = if (navigation == NavigationState.CharacterCreationAttributeDetail) {
                    session.selectedAttributeCode
                } else {
                    null
                },
                selectedCreationRaceId = null,
                selectedCreationClassId = null,
                messages = emptyList()
            )
        )
    }

    private fun previewRace(session: GameSession, raceId: String): GameActionResult {
        val draft = session.creationDraft ?: queryService.initialDraft()
        if (queryService.raceById(raceId) == null) {
            return GameActionResult(session.copy(messages = listOf("Raca invalida.")))
        }
        return GameActionResult(
            session = session.copy(
                creationDraft = draft,
                selectedCreationRaceId = raceId,
                navigation = NavigationState.CharacterCreationRaceDetail,
                messages = emptyList()
            )
        )
    }

    private fun confirmRace(session: GameSession): GameActionResult {
        val raceId = session.selectedCreationRaceId
            ?: return GameActionResult(session.copy(messages = listOf("Selecione uma raca para confirmar.")))
        return mutateDraft(
            session.copy(selectedCreationRaceId = null)
        ) { commandService.selectRace(it, raceId) }
    }

    private fun previewClass(session: GameSession, classId: String): GameActionResult {
        val draft = session.creationDraft ?: queryService.initialDraft()
        if (queryService.classById(classId) == null) {
            return GameActionResult(session.copy(messages = listOf("Classe invalida.")))
        }
        return GameActionResult(
            session = session.copy(
                creationDraft = draft,
                selectedCreationClassId = classId,
                navigation = NavigationState.CharacterCreationClassDetail,
                messages = emptyList()
            )
        )
    }

    private fun confirmClass(session: GameSession): GameActionResult {
        val classId = session.selectedCreationClassId
            ?: return GameActionResult(session.copy(messages = listOf("Selecione uma classe para confirmar.")))
        return mutateDraft(
            session.copy(selectedCreationClassId = null)
        ) { commandService.selectClass(it, classId) }
    }

    private fun confirm(session: GameSession): GameActionResult {
        val draft = session.creationDraft ?: queryService.initialDraft()
        if (!commandService.canConfirm(draft)) {
            return GameActionResult(
                session = session.copy(messages = listOf("Selecione nome, raca e classe para confirmar a criacao."))
            )
        }
        val created = commandService.createState(draft)
        val normalized = stateSupport.normalize(created)
        val savePath = saveGateway.saveAutosave(normalized)
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                currentSavePath = savePath,
                currentSaveName = savePath.fileName.toString(),
                creationDraft = null,
                selectedCreationRaceId = null,
                selectedCreationClassId = null,
                navigation = NavigationState.Hub,
                messages = listOf("Personagem criado com sucesso. Save inicial em ${savePath.fileName}.")
            )
        )
    }

    private fun openAttributeDetail(session: GameSession, code: String): GameActionResult {
        val draft = session.creationDraft ?: queryService.initialDraft()
        val normalizedCode = code.uppercase()
        if (queryService.attributeLabel(normalizedCode) == null) {
            return GameActionResult(session.copy(messages = listOf("Atributo invalido.")))
        }
        return GameActionResult(
            session = session.copy(
                creationDraft = draft,
                selectedAttributeCode = normalizedCode,
                navigation = NavigationState.CharacterCreationAttributeDetail,
                messages = emptyList()
            )
        )
    }
}
