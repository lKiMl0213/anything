package rpg.application.session

import rpg.application.GameActionResult
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.SaveGameGateway
import rpg.application.actions.GameAction
import rpg.application.creation.CharacterCreationQueryService
import rpg.navigation.NavigationState

class SessionActionDispatcher(
    private val saveGateway: SaveGameGateway,
    private val stateSupport: GameStateSupport,
    private val creationQueryService: CharacterCreationQueryService
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.StartNewGame -> startNewGame(session)

            GameAction.ContinueSession -> continueSession(session)
            GameAction.OpenLoadGame -> openLoadGame(session)
            is GameAction.LoadSave -> loadSave(session, action)
            GameAction.OpenSaveMenu -> move(session, NavigationState.SaveMenu)
            GameAction.SaveCurrentGame -> saveCurrentGame(session)
            GameAction.SaveAutosave -> saveAutosave(session)
            GameAction.Back -> back(session)
            GameAction.Exit -> GameActionResult(
                session = session.copy(
                    navigation = NavigationState.Exit,
                    exitRequested = true,
                    messages = emptyList()
                )
            )

            else -> null
        }
    }

    private fun continueSession(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhuma sessao carregada.")))
        val normalized = stateSupport.normalize(state)
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                creationDraft = null,
                selectedCreationRaceId = null,
                selectedCreationClassId = null,
                navigation = NavigationState.Hub,
                selectedCraftDiscipline = null,
                selectedGatheringType = null,
                selectedShopCurrency = null,
                selectedShopCategory = null,
                selectedWeaponClassCategory = null,
                selectedUpgradeCategory = null,
                selectedAttributeCode = null,
                selectedInventoryItemId = null,
                selectedEquipmentSlot = null,
                selectedTalentTreeId = null,
                selectedTalentNodeId = null,
                selectedQuestSection = null,
                selectedQuestId = null,
                selectedAchievementCategory = null,
                selectedAchievementId = null,
                messages = emptyList()
            )
        )
    }

    private fun openLoadGame(session: GameSession): GameActionResult {
        val saves = saveGateway.listSaves()
        return GameActionResult(
            session = session.copy(
                navigation = NavigationState.SaveSelection,
                availableSaves = saves,
                messages = if (saves.isEmpty()) listOf("Nenhum save encontrado.") else emptyList()
            )
        )
    }

    private fun loadSave(session: GameSession, action: GameAction.LoadSave): GameActionResult {
        val loaded = stateSupport.normalize(saveGateway.load(action.path))
        return GameActionResult(
            session = session.copy(
                gameState = loaded,
                currentSavePath = action.path,
                currentSaveName = action.path.fileName.toString(),
                creationDraft = null,
                selectedCreationRaceId = null,
                selectedCreationClassId = null,
                navigation = NavigationState.Hub,
                pendingEncounter = null,
                availableSaves = emptyList(),
                selectedCraftDiscipline = null,
                selectedGatheringType = null,
                selectedShopCurrency = null,
                selectedShopCategory = null,
                selectedWeaponClassCategory = null,
                selectedUpgradeCategory = null,
                selectedAttributeCode = null,
                selectedInventoryItemId = null,
                selectedEquipmentSlot = null,
                selectedTalentTreeId = null,
                selectedTalentNodeId = null,
                selectedQuestSection = null,
                selectedQuestId = null,
                selectedAchievementCategory = null,
                selectedAchievementId = null,
                messages = emptyList()
            )
        )
    }

    private fun saveCurrentGame(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val targetPath = session.currentSavePath
        return if (targetPath != null) {
            val savedPath = saveGateway.save(targetPath, normalized)
            GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    currentSavePath = savedPath,
                    currentSaveName = savedPath.fileName.toString(),
                    selectedCreationRaceId = null,
                    selectedCreationClassId = null,
                    navigation = NavigationState.Hub,
                    selectedCraftDiscipline = null,
                    selectedGatheringType = null,
                    selectedShopCurrency = null,
                    selectedShopCategory = null,
                    selectedWeaponClassCategory = null,
                    selectedUpgradeCategory = null,
                    selectedAttributeCode = null,
                    selectedInventoryItemId = null,
                    selectedEquipmentSlot = null,
                    selectedTalentTreeId = null,
                    selectedTalentNodeId = null,
                    selectedQuestSection = null,
                    selectedQuestId = null,
                    selectedAchievementCategory = null,
                    selectedAchievementId = null,
                    messages = listOf("Save atualizado em ${savedPath.fileName}.")
                )
            )
        } else {
            val savedPath = saveGateway.saveAutosave(normalized)
            GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    currentSavePath = savedPath,
                    currentSaveName = savedPath.fileName.toString(),
                    selectedCreationRaceId = null,
                    selectedCreationClassId = null,
                    navigation = NavigationState.Hub,
                    selectedAttributeCode = null,
                    selectedInventoryItemId = null,
                    selectedEquipmentSlot = null,
                    selectedTalentTreeId = null,
                    selectedTalentNodeId = null,
                    selectedQuestSection = null,
                    selectedQuestId = null,
                    selectedAchievementCategory = null,
                    selectedAchievementId = null,
                    messages = listOf("Sessao salva em ${savedPath.fileName}.")
                )
            )
        }
    }

    private fun saveAutosave(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val savedPath = saveGateway.saveAutosave(normalized)
        val keepCurrentPath = session.currentSavePath ?: savedPath
        val keepCurrentName = session.currentSaveName ?: savedPath.fileName.toString()
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                currentSavePath = keepCurrentPath,
                currentSaveName = keepCurrentName,
                selectedCreationRaceId = null,
                selectedCreationClassId = null,
                navigation = NavigationState.Hub,
                selectedCraftDiscipline = null,
                selectedGatheringType = null,
                selectedShopCurrency = null,
                selectedShopCategory = null,
                selectedWeaponClassCategory = null,
                selectedUpgradeCategory = null,
                selectedAttributeCode = null,
                selectedInventoryItemId = null,
                selectedEquipmentSlot = null,
                selectedTalentTreeId = null,
                selectedTalentNodeId = null,
                selectedQuestSection = null,
                selectedQuestId = null,
                selectedAchievementCategory = null,
                selectedAchievementId = null,
                messages = listOf("Autosave criado em ${savedPath.fileName}.")
            )
        )
    }

    private fun back(session: GameSession): GameActionResult {
        return GameActionResult(session = SessionNavigationSupport.buildBackSession(session))
    }

    private fun startNewGame(session: GameSession): GameActionResult {
        val draft = creationQueryService.initialDraft()
        return GameActionResult(
            session = session.copy(
                creationDraft = draft,
                selectedCreationRaceId = null,
                selectedCreationClassId = null,
                navigation = NavigationState.CharacterCreation,
                pendingEncounter = null,
                selectedCraftDiscipline = null,
                selectedGatheringType = null,
                selectedShopCurrency = null,
                selectedShopCategory = null,
                selectedWeaponClassCategory = null,
                selectedUpgradeCategory = null,
                selectedAttributeCode = null,
                selectedInventoryItemId = null,
                selectedEquipmentSlot = null,
                selectedTalentTreeId = null,
                selectedTalentNodeId = null,
                selectedQuestSection = null,
                selectedQuestId = null,
                selectedAchievementCategory = null,
                selectedAchievementId = null,
                messages = emptyList()
            )
        )
    }

    private fun move(session: GameSession, destination: NavigationState): GameActionResult {
        val state = session.gameState
        if (destination != NavigationState.MainMenu && destination != NavigationState.SaveSelection && state == null) {
            return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        }
        return GameActionResult(
            session = session.copy(
                navigation = destination,
                messages = emptyList()
            )
        )
    }
}
