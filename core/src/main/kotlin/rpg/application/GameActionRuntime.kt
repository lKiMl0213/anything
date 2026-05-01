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
import rpg.application.enchant.EnchantActionDispatcher
import rpg.application.enchant.EnchantCommandService
import rpg.application.enchant.EnchantQueryService
import rpg.application.enchant.ExtractionCommandService
import rpg.application.enchant.ExtractionQueryService
import rpg.application.enchant.FusionExtractionActionDispatcher
import rpg.application.enchant.FusionCommandService
import rpg.application.enchant.FusionQueryService
import rpg.application.exploration.ExplorationActionDispatcher
import rpg.application.globalboss.GlobalBossActionDispatcher
import rpg.application.globalboss.GlobalBossCommandService
import rpg.application.globalboss.GlobalBossQueryService
import rpg.application.hunting.HuntingActionDispatcher
import rpg.application.hunting.HuntingCommandService
import rpg.application.hunting.HuntingQueryService
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
import rpg.classquest.dungeon.ClassDungeonMonsterService
import rpg.combat.CombatResult
import rpg.combat.CombatTelemetry
import rpg.engine.GameEngine
import rpg.globalboss.services.GlobalBossCatalogService
import rpg.globalboss.services.GlobalBossProgressService
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
    val huntingQueryService = HuntingQueryService(engine)
    private val huntingCommandService = HuntingCommandService(engine, achievementTracker)
    val enchantQueryService = EnchantQueryService(engine)
    val fusionQueryService = FusionQueryService(engine)
    val extractionQueryService = ExtractionQueryService(engine)
    private val enchantCommandService = EnchantCommandService(engine, achievementTracker)
    private val fusionCommandService = FusionCommandService(engine, achievementTracker)
    private val extractionCommandService = ExtractionCommandService(engine, achievementTracker)
    val shopQueryService = ShopQueryService(engine, repo, engine.permanentUpgradeService)
    private val shopCommandService = ShopCommandService(
        engine = engine,
        repo = repo,
        queryService = shopQueryService,
        permanentUpgradeService = engine.permanentUpgradeService,
        achievementTracker = achievementTracker
    )
    private val globalBossCatalogService = GlobalBossCatalogService(repo, engine)
    private val globalBossProgressService = GlobalBossProgressService(
        engine = engine,
        config = repo.globalBossSystem,
        eventsById = repo.globalBossEvents
    )
    val globalBossQueryService = GlobalBossQueryService(
        repo = repo,
        catalogService = globalBossCatalogService,
        progressService = globalBossProgressService
    )
    private val globalBossCommandService = GlobalBossCommandService(
        engine = engine,
        catalogService = globalBossCatalogService,
        progressService = globalBossProgressService
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
    private val huntingActionDispatcher = HuntingActionDispatcher(
        commandService = huntingCommandService,
        stateSupport = stateSupport
    )
    private val enchantActionDispatcher = EnchantActionDispatcher(
        commandService = enchantCommandService,
        stateSupport = stateSupport
    )
    private val fusionExtractionActionDispatcher = FusionExtractionActionDispatcher(
        fusionCommandService = fusionCommandService,
        extractionCommandService = extractionCommandService,
        stateSupport = stateSupport
    )
    private val shopActionDispatcher = ShopActionDispatcher(
        queryService = shopQueryService,
        commandService = shopCommandService,
        stateSupport = stateSupport
    )
    private val classDungeonMonsterService = ClassDungeonMonsterService(repo, engine)
    private val globalBossActionDispatcher = GlobalBossActionDispatcher(
        engine = engine,
        queryService = globalBossQueryService,
        commandService = globalBossCommandService,
        stateSupport = stateSupport
    )

    private val explorationDispatcher = ExplorationActionDispatcher(
        engine = engine,
        stateSupport = stateSupport,
        classDungeonMonsterService = classDungeonMonsterService
    )

    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return sessionDispatcher.handle(session, action)
            ?: characterCreationActionDispatcher.handle(session, action)
            ?: hubActionDispatcher.handle(session, action)
            ?: characterActionDispatcher.handle(session, action)
            ?: inventoryActionDispatcher.handle(session, action)
            ?: progressionActionDispatcher.handle(session, action)
            ?: cityActionDispatcher.handle(session, action)
            ?: productionActionDispatcher.handle(session, action)
            ?: huntingActionDispatcher.handle(session, action)
            ?: enchantActionDispatcher.handle(session, action)
            ?: fusionExtractionActionDispatcher.handle(session, action)
            ?: shopActionDispatcher.handle(session, action)
            ?: globalBossActionDispatcher.handle(session, action)
            ?: explorationDispatcher.handle(session, action)
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

    fun resolveGlobalBossCombat(
        state: rpg.model.GameState,
        encounter: PendingEncounter,
        combatResult: CombatResult,
        combatLog: List<String>
    ): CombatFlowResult {
        return globalBossCommandService.resolveCombat(
            state = state,
            encounter = encounter,
            combatResult = combatResult,
            combatLog = combatLog
        )
    }
}
