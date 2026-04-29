package rpg.application

import rpg.achievement.AchievementMenu
import rpg.achievement.AchievementService
import rpg.achievement.AchievementTracker
import rpg.application.actions.GameAction
import rpg.application.character.CharacterActionDispatcher
import rpg.application.character.CharacterCommandService
import rpg.application.character.CharacterQueryService
import rpg.application.character.CharacterRulesSupport
import rpg.application.creation.CharacterCreationActionDispatcher
import rpg.application.creation.CharacterCreationCommandService
import rpg.application.creation.CharacterCreationQueryService
import rpg.application.city.CityActionDispatcher
import rpg.application.city.CityCommandService
import rpg.application.city.CityQueryService
import rpg.application.city.CityRulesSupport
import rpg.application.exploration.ExplorationActionDispatcher
import rpg.application.inventory.InventoryActionDispatcher
import rpg.application.inventory.InventoryCommandService
import rpg.application.inventory.InventoryQueryService
import rpg.application.inventory.InventoryRulesSupport
import rpg.application.navigation.HubActionDispatcher
import rpg.application.production.ProductionActionDispatcher
import rpg.application.production.ProductionCommandService
import rpg.application.production.ProductionQueryService
import rpg.application.progression.AchievementCommandService
import rpg.application.progression.AchievementQueryService
import rpg.application.progression.ProgressionActionDispatcher
import rpg.application.progression.QuestCommandService
import rpg.application.progression.QuestQueryService
import rpg.application.progression.QuestRulesSupport
import rpg.application.session.SessionActionDispatcher
import rpg.application.shop.ShopActionDispatcher
import rpg.application.shop.ShopCommandService
import rpg.application.shop.ShopQueryService
import rpg.classquest.ClassQuestMenu
import rpg.combat.CombatTelemetry
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.PlayerState

internal class GameActionRuntime(
    repo: DataRepository,
    saveGateway: SaveGameGateway
) {
    val engine = GameEngine(repo)

    private val achievementService = AchievementService()
    private val achievementMenu = AchievementMenu(achievementService)
    private val achievementTracker = AchievementTracker(achievementService)
    private val stateSupport = GameStateSupport(repo, engine, achievementTracker)

    private val characterSupport = CharacterRulesSupport(repo, engine)
    val characterQueryService = CharacterQueryService(characterSupport)
    private val characterCommandService = CharacterCommandService(characterSupport)
    val characterCreationQueryService = CharacterCreationQueryService(
        repo = repo,
        previewService = rpg.creation.CharacterCreationPreviewService(repo)
    )
    private val characterCreationCommandService = CharacterCreationCommandService(
        repo = repo,
        engine = engine,
        queryService = characterCreationQueryService
    )

    private val inventorySupport = InventoryRulesSupport(repo, engine)
    val inventoryQueryService = InventoryQueryService(engine, inventorySupport)
    private val inventoryCommandService = InventoryCommandService(
        engine,
        achievementTracker,
        inventorySupport
    )

    private val questRulesSupport = QuestRulesSupport(
        classQuestMenu = ClassQuestMenu(engine.classQuestService)
    )
    val questQueryService = QuestQueryService(engine, questRulesSupport)
    private val questCommandService = QuestCommandService(
        engine = engine,
        achievementTracker = achievementTracker,
        classQuestService = engine.classQuestService,
        support = questRulesSupport
    )

    val achievementQueryService = AchievementQueryService(
        achievementService = achievementService,
        achievementMenu = achievementMenu,
        knownBaseTypes = (
            repo.monsterArchetypes.values.map { archetype ->
                archetype.baseType.ifBlank { archetype.id.substringBefore('_') }
            } + listOf("slime", "wolf", "elemental")
            ).map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    )
    private val achievementCommandService = AchievementCommandService(
        achievementService = achievementService,
        support = questRulesSupport
    )

    private val cityRulesSupport = CityRulesSupport(engine)
    val cityQueryService = CityQueryService(cityRulesSupport)
    private val cityCommandService = CityCommandService(achievementTracker, cityRulesSupport)
    val productionQueryService = ProductionQueryService(engine)
    private val productionCommandService = ProductionCommandService(engine, achievementTracker)
    val shopQueryService = ShopQueryService(engine, repo, engine.permanentUpgradeService)
    private val shopCommandService = ShopCommandService(
        engine = engine,
        repo = repo,
        queryService = shopQueryService,
        permanentUpgradeService = engine.permanentUpgradeService,
        achievementTracker = achievementTracker
    )

    private val sessionDispatcher = SessionActionDispatcher(saveGateway, stateSupport, characterCreationQueryService)
    private val characterCreationActionDispatcher = CharacterCreationActionDispatcher(
        queryService = characterCreationQueryService,
        commandService = characterCreationCommandService,
        saveGateway = saveGateway,
        stateSupport = stateSupport
    )
    private val hubActionDispatcher = HubActionDispatcher(engine, stateSupport)
    private val characterActionDispatcher = CharacterActionDispatcher(
        characterQueryService,
        characterCommandService,
        stateSupport
    )
    private val inventoryActionDispatcher = InventoryActionDispatcher(
        inventoryCommandService,
        stateSupport
    )
    private val progressionActionDispatcher = ProgressionActionDispatcher(
        questQueryService = questQueryService,
        questCommandService = questCommandService,
        achievementCommandService = achievementCommandService,
        stateSupport = stateSupport
    )
    private val cityActionDispatcher = CityActionDispatcher(
        commandService = cityCommandService,
        stateSupport = stateSupport
    )
    private val productionActionDispatcher = ProductionActionDispatcher(
        commandService = productionCommandService,
        stateSupport = stateSupport
    )
    private val shopActionDispatcher = ShopActionDispatcher(
        queryService = shopQueryService,
        commandService = shopCommandService,
        stateSupport = stateSupport
    )
    private val explorationActionDispatcher = ExplorationActionDispatcher(engine, stateSupport)

    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return sessionDispatcher.handle(session, action)
            ?: characterCreationActionDispatcher.handle(session, action)
            ?: hubActionDispatcher.handle(session, action)
            ?: characterActionDispatcher.handle(session, action)
            ?: inventoryActionDispatcher.handle(session, action)
            ?: progressionActionDispatcher.handle(session, action)
            ?: cityActionDispatcher.handle(session, action)
            ?: productionActionDispatcher.handle(session, action)
            ?: shopActionDispatcher.handle(session, action)
            ?: explorationActionDispatcher.handle(session, action)
    }

    fun normalizeLoadedState(state: rpg.model.GameState?): rpg.model.GameState? {
        return state?.let(stateSupport::normalize)
    }

    fun normalizeForCombat(state: rpg.model.GameState): rpg.model.GameState {
        return stateSupport.normalize(state)
    }

    fun applyBattleResolvedAchievement(
        player: PlayerState,
        telemetry: CombatTelemetry,
        victory: Boolean,
        escaped: Boolean,
        isBoss: Boolean,
        monsterTypeId: String,
        monsterStars: Int
    ): PlayerState {
        return achievementTracker.onBattleResolved(
            player = player,
            telemetry = telemetry,
            victory = victory,
            escaped = escaped,
            isBoss = isBoss,
            monsterTypeId = monsterTypeId,
            monsterStars = monsterStars
        ).player
    }

    fun applyGoldEarnedAchievement(player: PlayerState, gold: Long): PlayerState {
        return achievementTracker.onGoldEarned(player, gold).player
    }

    fun applyDeathAchievement(player: PlayerState): PlayerState {
        return achievementTracker.onDeath(player).player
    }
}
