package rpg.presentation

import rpg.application.GameSession
import rpg.application.creation.CharacterCreationQueryService
import rpg.application.PendingEncounter
import rpg.application.character.CharacterQueryService
import rpg.application.city.CityQueryService
import rpg.application.inventory.InventoryQueryService
import rpg.application.production.ProductionQueryService
import rpg.application.progression.AchievementQueryService
import rpg.application.progression.QuestQueryService
import rpg.application.shop.ShopQueryService
import rpg.engine.GameEngine
import rpg.navigation.NavigationState
import rpg.presentation.model.CombatScreenViewModel
import rpg.presentation.model.ScreenViewModel

class GamePresenter(
    engine: GameEngine,
    creationQueryService: CharacterCreationQueryService,
    inventoryQueryService: InventoryQueryService,
    characterQueryService: CharacterQueryService,
    questQueryService: QuestQueryService,
    achievementQueryService: AchievementQueryService,
    cityQueryService: CityQueryService,
    productionQueryService: ProductionQueryService,
    shopQueryService: ShopQueryService
) {
    private val support = PresentationSupport(engine)
    private val navigationPresenter = NavigationScreenPresenter(
        engine = engine,
        support = support,
        characterQueryService = characterQueryService,
        questQueryService = questQueryService,
        achievementQueryService = achievementQueryService
    )
    private val characterCreationPresenter = CharacterCreationScreenPresenter(creationQueryService)
    private val progressionPresenter = ProgressionScreenPresenter(questQueryService, achievementQueryService, support)
    private val characterPresenter = CharacterScreenPresenter(characterQueryService, support)
    private val inventoryPresenter = InventoryScreenPresenter(inventoryQueryService, support)
    private val cityPresenter = CityScreenPresenter(cityQueryService, support)
    private val productionPresenter = ProductionScreenPresenter(productionQueryService, support)
    private val shopPresenter = ShopScreenPresenter(shopQueryService, support)
    private val combatPresenter = CombatScreenPresenter(engine, support)

    fun present(session: GameSession): ScreenViewModel {
        return when (session.navigation) {
            NavigationState.MainMenu -> navigationPresenter.presentMainMenu(session)
            NavigationState.SaveSelection -> navigationPresenter.presentSaveSelection(session)
            NavigationState.CharacterCreation -> characterCreationPresenter.presentCreationMenu(session)
            NavigationState.CharacterCreationRace -> characterCreationPresenter.presentRaceSelection(session)
            NavigationState.CharacterCreationRaceDetail -> characterCreationPresenter.presentRaceDetail(session)
            NavigationState.CharacterCreationClass -> characterCreationPresenter.presentClassSelection(session)
            NavigationState.CharacterCreationClassDetail -> characterCreationPresenter.presentClassDetail(session)
            NavigationState.CharacterCreationAttributes -> characterCreationPresenter.presentAttributeAllocation(session)
            NavigationState.CharacterCreationAttributeDetail -> characterCreationPresenter.presentAttributeAllocationDetail(session)
            NavigationState.Hub -> navigationPresenter.presentHub(session)
            NavigationState.ProductionMenu -> navigationPresenter.presentProductionMenu(session)
            NavigationState.ProductionCraftMenu -> productionPresenter.presentCraftMenu(session)
            NavigationState.ProductionRecipeList -> productionPresenter.presentRecipeList(session)
            NavigationState.ProductionGatheringList -> productionPresenter.presentGatheringList(session)
            NavigationState.ProgressionMenu -> progressionPresenter.presentProgressionMenu(session)
            NavigationState.QuestBoard -> progressionPresenter.presentQuestBoard(session)
            NavigationState.QuestList -> progressionPresenter.presentQuestList(session)
            NavigationState.QuestDetail -> progressionPresenter.presentQuestDetail(session)
            NavigationState.ClassQuest -> progressionPresenter.presentClassQuest(session)
            NavigationState.ClassQuestCancelConfirm -> progressionPresenter.presentClassQuestCancelConfirm(session)
            NavigationState.Achievements -> progressionPresenter.presentAchievements(session)
            NavigationState.AchievementCategory -> progressionPresenter.presentAchievementCategory(session)
            NavigationState.AchievementDetail -> progressionPresenter.presentAchievementDetail(session)
            NavigationState.AchievementStatistics -> progressionPresenter.presentAchievementStatistics(session)
            NavigationState.CityMenu -> navigationPresenter.presentCityMenu(session)
            NavigationState.Tavern -> cityPresenter.presentTavern(session)
            NavigationState.CityShopCategories -> shopPresenter.presentShopCategories(session)
            NavigationState.CityShopItems -> shopPresenter.presentShopItems(session)
            NavigationState.CityUpgradeCategories -> shopPresenter.presentUpgradeCategories(session)
            NavigationState.CityUpgradeList -> shopPresenter.presentUpgradeList(session)
            NavigationState.SaveMenu -> navigationPresenter.presentSaveMenu(session)
            NavigationState.CharacterMenu -> characterPresenter.presentCharacterMenu(session)
            NavigationState.Attributes -> characterPresenter.presentAttributes(session)
            NavigationState.AttributeDetail -> characterPresenter.presentAttributeDetail(session)
            NavigationState.Talents -> characterPresenter.presentTalents(session)
            NavigationState.TalentTreeDetail -> characterPresenter.presentTalentTreeDetail(session)
            NavigationState.TalentNodeDetail -> characterPresenter.presentTalentNodeDetail(session)
            NavigationState.Exploration -> navigationPresenter.presentExploration(session)
            NavigationState.ExplorationLowHpConfirm -> navigationPresenter.presentExplorationLowHpConfirm(session)
            NavigationState.DungeonSelection -> navigationPresenter.presentDungeonSelection(session)
            NavigationState.Inventory -> inventoryPresenter.presentInventory(session)
            NavigationState.InventoryFilters -> inventoryPresenter.presentInventoryFilters(session)
            NavigationState.InventoryItemDetail -> inventoryPresenter.presentInventoryItemDetail(session)
            NavigationState.Equipped -> inventoryPresenter.presentEquipped(session)
            NavigationState.EquippedItemDetail -> inventoryPresenter.presentEquippedItemDetail(session)
            NavigationState.Quiver -> inventoryPresenter.presentQuiver(session)
            NavigationState.Combat -> combatPresenter.presentCombatFallback(session)
            NavigationState.Exit -> navigationPresenter.presentExit(session)
        }
    }

    fun presentCombat(
        encounter: PendingEncounter,
        logLines: List<String>,
        messages: List<String>,
        playerHp: Double,
        playerMp: Double,
        enemyHp: Double
    ): CombatScreenViewModel {
        return combatPresenter.presentCombat(encounter, logLines, messages, playerHp, playerMp, enemyHp)
    }
}
