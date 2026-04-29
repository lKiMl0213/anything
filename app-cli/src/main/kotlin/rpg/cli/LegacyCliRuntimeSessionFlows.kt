// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.model.GameState
import rpg.session.SessionBridge

internal class LegacyCliRuntimeSessionFlows(
    private val support: LegacyCliRuntimeSupportContext,
    private val inventoryProgressionFlows: LegacyCliRuntimeInventoryProgressionFlows,
    private val dungeonFlows: LegacyCliRuntimeDungeonFlows
) {
    val hubFlow by lazy {
        LegacyHubFlow(
            readMenuChoice = support.ioSupport::readMenuChoice,
            clearScreen = support.ioSupport::clearScreen,
            synchronizeClock = support.statusTimeSupport::synchronizeClock,
            normalizeLoadedState = support.stateSupport::normalizeLoadedState,
            synchronizeClassQuest = { current ->
                current.copy(player = support.engine.classQuestService.synchronize(current.player))
            },
            synchronizeAchievements = { current ->
                current.copy(player = support.achievementTracker.synchronize(current.player))
            },
            synchronizeQuestBoard = { current ->
                current.copy(
                    questBoard = support.stateSupport.synchronizeQuestBoard(
                        current.questBoard,
                        current.player,
                        current.itemInstances
                    )
                )
            },
            checkSubclassUnlock = support.classProgressionSupport::checkSubclassUnlock,
            checkSpecializationUnlock = support.classProgressionSupport::checkSpecializationUnlock,
            showClock = support.statusTimeSupport::showClock,
            showStatus = support.statusTimeSupport::showStatus,
            showDebuff = support.statusTimeSupport::showDebuff,
            menuAlert = support.menuFormattingSupport::menuAlert,
            labelWithAlert = support.menuFormattingSupport::labelWithAlert,
            hasUnspentAttributePoints = support.stateSupport::hasUnspentAttributePoints,
            hasTalentPointsAvailable = support.stateSupport::hasTalentPointsAvailable,
            hasReadyToClaim = { board -> support.stateSupport.hasReadyToClaim(board) },
            hasAchievementRewardReady = support.stateSupport::hasAchievementRewardReady,
            openExploreMenu = dungeonFlows.explorationFlow::openExploreMenu,
            openProductionMenu = inventoryProgressionFlows.productionFlow::openProductionMenu,
            openCityMenu = inventoryProgressionFlows.cityServicesFlow::openCityMenu,
            saveGame = support::saveGame,
            openEquipped = inventoryProgressionFlows.equipmentFlow::openEquipped,
            openInventory = inventoryProgressionFlows.inventoryFlow::openInventory,
            allocateUnspentPoints = support.attributePointSupport::allocateUnspentPoints,
            openTalents = inventoryProgressionFlows.talentFlow::openTalents,
            openQuestBoard = inventoryProgressionFlows.questFlow::openQuestBoard,
            openAchievementMenu = inventoryProgressionFlows.achievementFlow::openAchievementMenu
        )
    }

    private val sessionBridge by lazy {
        SessionBridge(
            repo = support.repo,
            engine = support.engine,
            characterDef = support.characterDef,
            characterCreationPreview = support.characterCreationPreview,
            attributeMeta = support.attributeMeta,
            readNonEmpty = support.ioSupport::readNonEmpty,
            readMenuChoice = support.ioSupport::readMenuChoice,
            chooseSave = support.ioSupport::choose,
            format = support::format,
            applyTwoHandedLoadout = inventoryProgressionFlows.equipmentFlow::applyTwoHandedLoadout,
            computePlayerStats = support.engine::computePlayerStats,
            ensureSkillProgress = { player -> support.engine.skillSystem.ensureProgress(player) },
            synchronizeAchievements = { player -> support.achievementTracker.synchronize(player) },
            synchronizeClock = support.statusTimeSupport::synchronizeClock,
            normalizeLoadedState = support.stateSupport::normalizeLoadedState,
            hubLoop = hubFlow::hubLoop,
            notify = ::println
        )
    }

    fun newGame(): GameState? = sessionBridge.newGame()
    fun loadGame() = sessionBridge.loadGame()
    fun runLegacySection(initialState: GameState, block: (GameState) -> GameState): GameState? {
        return sessionBridge.runLegacySection(initialState, block)
    }
}
