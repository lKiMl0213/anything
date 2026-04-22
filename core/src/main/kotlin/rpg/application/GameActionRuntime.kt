package rpg.application

import rpg.achievement.AchievementMenu
import rpg.achievement.AchievementService
import rpg.achievement.AchievementTracker
import rpg.application.actions.GameAction
import rpg.application.character.CharacterActionDispatcher
import rpg.application.character.CharacterCommandService
import rpg.application.character.CharacterQueryService
import rpg.application.character.CharacterRulesSupport
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
import rpg.application.progression.AchievementCommandService
import rpg.application.progression.AchievementQueryService
import rpg.application.progression.ProgressionActionDispatcher
import rpg.application.progression.QuestCommandService
import rpg.application.progression.QuestQueryService
import rpg.application.progression.QuestRulesSupport
import rpg.application.session.SessionActionDispatcher
import rpg.classquest.ClassQuestMenu
import rpg.engine.GameEngine
import rpg.io.DataRepository

internal class GameActionRuntime(
    repo: DataRepository,
    saveGateway: SaveGameGateway
) {
    val engine = GameEngine(repo)

    private val achievementService = AchievementService()
    private val achievementMenu = AchievementMenu(achievementService)
    private val achievementTracker = AchievementTracker(achievementService)
    private val stateSupport = GameStateSupport(engine, achievementTracker)

    private val characterSupport = CharacterRulesSupport(repo, engine)
    val characterQueryService = CharacterQueryService(characterSupport)
    private val characterCommandService = CharacterCommandService(characterSupport)

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

    private val sessionDispatcher = SessionActionDispatcher(saveGateway, stateSupport)
    private val hubActionDispatcher = HubActionDispatcher(stateSupport)
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
    private val explorationActionDispatcher = ExplorationActionDispatcher(engine, stateSupport)

    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return sessionDispatcher.handle(session, action)
            ?: hubActionDispatcher.handle(session, action)
            ?: characterActionDispatcher.handle(session, action)
            ?: inventoryActionDispatcher.handle(session, action)
            ?: progressionActionDispatcher.handle(session, action)
            ?: cityActionDispatcher.handle(session, action)
            ?: explorationActionDispatcher.handle(session, action)
    }

    fun normalizeLoadedState(state: rpg.model.GameState?): rpg.model.GameState? {
        return state?.let(stateSupport::normalize)
    }

    fun normalizeForCombat(state: rpg.model.GameState): rpg.model.GameState {
        return stateSupport.normalize(state)
    }
}
