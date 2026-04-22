package rpg.application

import rpg.application.actions.GameAction
import rpg.application.character.CharacterQueryService
import rpg.application.city.CityQueryService
import rpg.application.inventory.InventoryQueryService
import rpg.application.progression.AchievementQueryService
import rpg.application.progression.QuestQueryService
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.navigation.NavigationState

class GameActionHandler(
    repo: DataRepository,
    saveGateway: SaveGameGateway = SaveGameGateway()
) {
    private val runtime = GameActionRuntime(repo, saveGateway)

    fun engine(): GameEngine = runtime.engine
    fun characterQueryService(): CharacterQueryService = runtime.characterQueryService
    fun inventoryQueryService(): InventoryQueryService = runtime.inventoryQueryService
    fun questQueryService(): QuestQueryService = runtime.questQueryService
    fun achievementQueryService(): AchievementQueryService = runtime.achievementQueryService
    fun cityQueryService(): CityQueryService = runtime.cityQueryService

    fun handle(session: GameSession, action: GameAction): GameActionResult {
        return runtime.handle(session, action)
            ?: when (action) {
                GameAction.Attack,
                GameAction.EscapeCombat -> GameActionResult(session)
                else -> GameActionResult(session.copy(messages = listOf("Acao ainda nao suportada no fluxo modular.")))
            }
    }

    fun applyCombatResult(session: GameSession, result: CombatFlowResult): GameSession {
        return session.copy(
            gameState = runtime.normalizeForCombat(result.gameState),
            navigation = result.navigation,
            pendingEncounter = null,
            messages = result.messages
        )
    }

    fun applyLegacyReturn(session: GameSession, updatedState: rpg.model.GameState?): GameSession {
        val normalized = runtime.normalizeLoadedState(updatedState) ?: session.gameState
        return session.copy(
            gameState = normalized,
            navigation = if (normalized != null) NavigationState.Hub else NavigationState.MainMenu,
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
            messages = emptyList()
        )
    }

    fun applyLegacyNewGameReturn(session: GameSession, updatedState: rpg.model.GameState?): GameSession {
        val normalized = runtime.normalizeLoadedState(updatedState)
        return session.copy(
            gameState = normalized,
            currentSavePath = null,
            currentSaveName = null,
            navigation = if (normalized != null) NavigationState.Hub else NavigationState.MainMenu,
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
            messages = emptyList()
        )
    }
}
