package rpg.android

import rpg.android.state.MainSection
import rpg.navigation.NavigationState

internal fun sectionForNavigation(navigation: NavigationState): MainSection {
    return when (navigation) {
        NavigationState.CharacterMenu,
        NavigationState.Inventory,
        NavigationState.Equipped,
        NavigationState.InventoryFilters,
        NavigationState.InventoryItemDetail,
        NavigationState.EquippedItemDetail,
        NavigationState.Quiver,
        NavigationState.Attributes,
        NavigationState.AttributeDetail,
        NavigationState.Talents,
        NavigationState.TalentTreeDetail,
        NavigationState.TalentNodeDetail -> MainSection.CHARACTER

        NavigationState.ProductionMenu,
        NavigationState.ProductionCraftMenu,
        NavigationState.ProductionRecipeList,
        NavigationState.ProductionRecipeDetail,
        NavigationState.ProductionGatheringList,
        NavigationState.ProductionHuntingSpotList,
        NavigationState.ProductionHuntingDurationList,
        NavigationState.ProductionEnchantMenu,
        NavigationState.ProductionEnchantList,
        NavigationState.ProductionEnchantDetail,
        NavigationState.ProductionFusionSlot1,
        NavigationState.ProductionFusionSlot2,
        NavigationState.ProductionFusionPreview,
        NavigationState.ProductionExtractionSlot1,
        NavigationState.ProductionExtractionPreview -> MainSection.PRODUCTION

        NavigationState.CityMenu,
        NavigationState.Tavern,
        NavigationState.CityShopCategories,
        NavigationState.CityShopItems,
        NavigationState.CityCashTopUp,
        NavigationState.CityUpgradeCategories,
        NavigationState.CityUpgradeList,
        NavigationState.CityPremiumShop -> MainSection.CITY

        NavigationState.ProgressionMenu,
        NavigationState.QuestBoard,
        NavigationState.QuestList,
        NavigationState.QuestDetail,
        NavigationState.ClassQuest,
        NavigationState.ClassQuestCancelConfirm,
        NavigationState.Achievements,
        NavigationState.AchievementCategory,
        NavigationState.AchievementDetail,
        NavigationState.AchievementStatistics -> MainSection.PROGRESSION

        else -> MainSection.EXPLORATION
    }
}
