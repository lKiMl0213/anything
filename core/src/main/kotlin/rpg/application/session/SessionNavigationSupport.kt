package rpg.application.session

import rpg.application.GameSession
import rpg.navigation.NavigationState

internal object SessionNavigationSupport {
    fun buildBackSession(session: GameSession): GameSession {
        val target = when (session.navigation) {
            NavigationState.MainMenu -> NavigationState.MainMenu
            NavigationState.SaveSelection -> NavigationState.MainMenu
            NavigationState.CharacterCreation -> NavigationState.MainMenu
            NavigationState.CharacterCreationRace -> NavigationState.CharacterCreation
            NavigationState.CharacterCreationRaceDetail -> NavigationState.CharacterCreationRace
            NavigationState.CharacterCreationClass -> NavigationState.CharacterCreation
            NavigationState.CharacterCreationClassDetail -> NavigationState.CharacterCreationClass
            NavigationState.CharacterCreationAttributes -> NavigationState.CharacterCreation
            NavigationState.CharacterCreationAttributeDetail -> NavigationState.CharacterCreationAttributes
            NavigationState.Hub -> NavigationState.MainMenu
            NavigationState.ProductionMenu -> NavigationState.Hub
            NavigationState.ProductionCraftMenu -> NavigationState.ProductionMenu
            NavigationState.ProductionRecipeList -> NavigationState.ProductionCraftMenu
            NavigationState.ProductionGatheringList -> NavigationState.ProductionMenu
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
            NavigationState.CityShopCategories -> NavigationState.CityMenu
            NavigationState.CityShopItems -> NavigationState.CityShopCategories
            NavigationState.CityUpgradeCategories -> NavigationState.CityMenu
            NavigationState.CityUpgradeList -> NavigationState.CityUpgradeCategories
            NavigationState.SaveMenu -> NavigationState.Hub
            NavigationState.CharacterMenu -> NavigationState.Hub
            NavigationState.Attributes -> NavigationState.CharacterMenu
            NavigationState.AttributeDetail -> NavigationState.Attributes
            NavigationState.Talents -> NavigationState.CharacterMenu
            NavigationState.TalentTreeDetail -> NavigationState.Talents
            NavigationState.TalentNodeDetail -> NavigationState.TalentTreeDetail
            NavigationState.Exploration -> NavigationState.Hub
            NavigationState.ExplorationLowHpConfirm -> NavigationState.Hub
            NavigationState.DungeonSelection -> NavigationState.Exploration
            NavigationState.Inventory -> session.inventoryReturnNavigation ?: NavigationState.CharacterMenu
            NavigationState.InventoryFilters -> NavigationState.Inventory
            NavigationState.InventoryItemDetail -> NavigationState.Inventory
            NavigationState.Equipped -> NavigationState.CharacterMenu
            NavigationState.EquippedItemDetail -> NavigationState.Equipped
            NavigationState.Quiver -> NavigationState.Inventory
            NavigationState.Combat -> NavigationState.Exploration
            NavigationState.Exit -> NavigationState.Exit
        }

        return session.copy(
            navigation = target,
            pendingEncounter = null,
            availableSaves = if (target == NavigationState.SaveSelection) session.availableSaves else emptyList(),
            creationDraft = when (target) {
                NavigationState.CharacterCreation,
                NavigationState.CharacterCreationRace,
                NavigationState.CharacterCreationRaceDetail,
                NavigationState.CharacterCreationClass,
                NavigationState.CharacterCreationClassDetail,
                NavigationState.CharacterCreationAttributes,
                NavigationState.CharacterCreationAttributeDetail -> session.creationDraft
                else -> null
            },
            selectedCreationRaceId = if (target == NavigationState.CharacterCreationRaceDetail) {
                session.selectedCreationRaceId
            } else {
                null
            },
            selectedCreationClassId = if (target == NavigationState.CharacterCreationClassDetail) {
                session.selectedCreationClassId
            } else {
                null
            },
            selectedCraftDiscipline = if (
                target == NavigationState.ProductionRecipeList || target == NavigationState.ProductionCraftMenu
            ) session.selectedCraftDiscipline else null,
            selectedGatheringType = if (target == NavigationState.ProductionGatheringList) {
                session.selectedGatheringType
            } else {
                null
            },
            selectedShopCurrency = if (
                target == NavigationState.CityShopCategories ||
                target == NavigationState.CityShopItems ||
                target == NavigationState.CityUpgradeCategories ||
                target == NavigationState.CityUpgradeList
            ) session.selectedShopCurrency else null,
            selectedShopCategory = if (target == NavigationState.CityShopItems) session.selectedShopCategory else null,
            selectedWeaponClassCategory = if (target == NavigationState.CityShopItems) {
                session.selectedWeaponClassCategory
            } else {
                null
            },
            selectedUpgradeCategory = if (target == NavigationState.CityUpgradeList) session.selectedUpgradeCategory else null,
            selectedAttributeCode = if (
                target == NavigationState.AttributeDetail ||
                target == NavigationState.CharacterCreationAttributeDetail
            ) {
                session.selectedAttributeCode
            } else {
                null
            },
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
            inventoryReturnNavigation = if (
                target == NavigationState.Inventory ||
                target == NavigationState.InventoryFilters ||
                target == NavigationState.InventoryItemDetail ||
                target == NavigationState.Equipped ||
                target == NavigationState.EquippedItemDetail ||
                target == NavigationState.Quiver
            ) {
                session.inventoryReturnNavigation
            } else {
                null
            },
            messages = emptyList()
        )
    }
}
