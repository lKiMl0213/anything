package rpg.navigation

sealed interface NavigationState {
    data object MainMenu : NavigationState
    data object SaveSelection : NavigationState
    data object Hub : NavigationState
    data object ProductionMenu : NavigationState
    data object ProgressionMenu : NavigationState
    data object QuestBoard : NavigationState
    data object QuestList : NavigationState
    data object QuestDetail : NavigationState
    data object ClassQuest : NavigationState
    data object ClassQuestCancelConfirm : NavigationState
    data object Achievements : NavigationState
    data object AchievementCategory : NavigationState
    data object AchievementDetail : NavigationState
    data object AchievementStatistics : NavigationState
    data object CityMenu : NavigationState
    data object Tavern : NavigationState
    data object SaveMenu : NavigationState
    data object CharacterMenu : NavigationState
    data object Attributes : NavigationState
    data object AttributeDetail : NavigationState
    data object Talents : NavigationState
    data object TalentTreeDetail : NavigationState
    data object TalentNodeDetail : NavigationState
    data object Exploration : NavigationState
    data object DungeonSelection : NavigationState
    data object Inventory : NavigationState
    data object InventoryFilters : NavigationState
    data object InventoryItemDetail : NavigationState
    data object Equipped : NavigationState
    data object EquippedItemDetail : NavigationState
    data object Quiver : NavigationState
    data object Combat : NavigationState
    data object Exit : NavigationState
}
