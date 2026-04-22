package rpg.application.session

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.SaveGameGateway
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

class SessionActionDispatcher(
    private val saveGateway: SaveGameGateway,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.StartNewGame -> GameActionResult(
                session = session.copy(messages = emptyList()),
                effect = GameEffect.LaunchLegacyNewGame
            )

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
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
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
                navigation = NavigationState.Hub,
                pendingEncounter = null,
                availableSaves = emptyList(),
                selectedAttributeCode = null,
                selectedInventoryItemId = null,
                selectedEquipmentSlot = null,
                selectedTalentTreeId = null,
                selectedTalentNodeId = null,
                selectedQuestSection = null,
                selectedQuestId = null,
                selectedAchievementCategory = null,
                selectedAchievementId = null,
                messages = listOf("Save carregado: ${action.path.fileName}")
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
                messages = listOf("Autosave criado em ${savedPath.fileName}.")
            )
        )
    }

    private fun back(session: GameSession): GameActionResult {
        val target = when (session.navigation) {
            NavigationState.MainMenu -> NavigationState.MainMenu
            NavigationState.SaveSelection -> NavigationState.MainMenu
            NavigationState.Hub -> NavigationState.MainMenu
            NavigationState.ProductionMenu -> NavigationState.Hub
            NavigationState.ProgressionMenu -> NavigationState.Hub
            NavigationState.QuestBoard -> NavigationState.ProgressionMenu
            NavigationState.QuestList -> NavigationState.QuestBoard
            NavigationState.QuestDetail -> NavigationState.QuestList
            NavigationState.ClassQuest -> NavigationState.QuestBoard
            NavigationState.ClassQuestCancelConfirm -> NavigationState.ClassQuest
            NavigationState.Achievements -> NavigationState.ProgressionMenu
            NavigationState.AchievementCategory -> NavigationState.Achievements
            NavigationState.AchievementDetail -> NavigationState.AchievementCategory
            NavigationState.AchievementStatistics -> NavigationState.Achievements
            NavigationState.CityMenu -> NavigationState.Hub
            NavigationState.Tavern -> NavigationState.CityMenu
            NavigationState.SaveMenu -> NavigationState.Hub
            NavigationState.CharacterMenu -> NavigationState.Hub
            NavigationState.Attributes -> NavigationState.CharacterMenu
            NavigationState.AttributeDetail -> NavigationState.Attributes
            NavigationState.Talents -> NavigationState.CharacterMenu
            NavigationState.TalentTreeDetail -> NavigationState.Talents
            NavigationState.TalentNodeDetail -> NavigationState.TalentTreeDetail
            NavigationState.Exploration -> NavigationState.Hub
            NavigationState.DungeonSelection -> NavigationState.Exploration
            NavigationState.Inventory -> NavigationState.CharacterMenu
            NavigationState.InventoryFilters -> NavigationState.Inventory
            NavigationState.InventoryItemDetail -> NavigationState.Inventory
            NavigationState.Equipped -> NavigationState.CharacterMenu
            NavigationState.EquippedItemDetail -> NavigationState.Equipped
            NavigationState.Quiver -> NavigationState.Inventory
            NavigationState.Combat -> NavigationState.Exploration
            NavigationState.Exit -> NavigationState.Exit
        }
        return GameActionResult(
            session = session.copy(
                navigation = target,
                pendingEncounter = null,
                availableSaves = if (target == NavigationState.SaveSelection) session.availableSaves else emptyList(),
                selectedAttributeCode = if (target == NavigationState.AttributeDetail) session.selectedAttributeCode else null,
                selectedInventoryItemId = if (target == NavigationState.InventoryItemDetail) session.selectedInventoryItemId else null,
                selectedEquipmentSlot = if (target == NavigationState.EquippedItemDetail) session.selectedEquipmentSlot else null,
                selectedTalentTreeId = if (
                    target == NavigationState.TalentTreeDetail || target == NavigationState.TalentNodeDetail
                ) session.selectedTalentTreeId else null,
                selectedTalentNodeId = if (target == NavigationState.TalentNodeDetail) session.selectedTalentNodeId else null,
                selectedQuestSection = if (
                    target == NavigationState.QuestList || target == NavigationState.QuestDetail
                ) session.selectedQuestSection else null,
                selectedQuestId = if (target == NavigationState.QuestDetail) session.selectedQuestId else null,
                selectedAchievementCategory = if (
                    target == NavigationState.AchievementCategory || target == NavigationState.AchievementDetail
                ) session.selectedAchievementCategory else null,
                selectedAchievementId = if (target == NavigationState.AchievementDetail) session.selectedAchievementId else null,
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
