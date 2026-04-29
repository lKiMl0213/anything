package rpg.application.actions

import java.nio.file.Path
import rpg.achievement.AchievementCategory
import rpg.application.shop.ShopCategory
import rpg.application.shop.UpgradeMenuCategory
import rpg.application.shop.WeaponClassCategory
import rpg.application.progression.QuestSection
import rpg.item.ItemRarity
import rpg.model.CraftDiscipline
import rpg.model.GatheringType
import rpg.model.ItemType
import rpg.model.ShopCurrency

sealed interface GameAction {
    data object StartNewGame : GameAction
    data object ContinueSession : GameAction
    data object OpenLoadGame : GameAction
    data class LoadSave(val path: Path) : GameAction
    data object OpenProductionMenu : GameAction
    data object OpenCraftMenu : GameAction
    data class OpenCraftDiscipline(val discipline: CraftDiscipline) : GameAction
    data class CraftRecipe(val recipeId: String) : GameAction
    data class OpenGatheringType(val type: GatheringType) : GameAction
    data class GatherNode(val nodeId: String) : GameAction
    data object OpenProgressionMenu : GameAction
    data object OpenCityMenu : GameAction
    data object OpenGoldShop : GameAction
    data object OpenCashShop : GameAction
    data object OpenUpgradeShop : GameAction
    data class SetShopCurrency(val currency: ShopCurrency) : GameAction
    data class OpenShopCategory(val category: ShopCategory) : GameAction
    data class SetShopWeaponClass(val category: WeaponClassCategory) : GameAction
    data class BuyShopEntry(val entryId: String) : GameAction
    data class OpenUpgradeCategory(val category: UpgradeMenuCategory) : GameAction
    data class BuyUpgrade(val upgradeId: String, val costId: String, val currency: ShopCurrency) : GameAction
    data object OpenQuests : GameAction
    data object OpenClassQuest : GameAction
    data class OpenQuestSection(val section: QuestSection) : GameAction
    data class InspectQuest(val section: QuestSection, val instanceId: String) : GameAction
    data class AcceptQuest(val instanceId: String) : GameAction
    data class CancelQuest(val instanceId: String) : GameAction
    data class ReplaceQuest(val section: QuestSection, val instanceId: String) : GameAction
    data class ClaimQuest(val instanceId: String) : GameAction
    data class ChooseClassQuestPath(val pathId: String) : GameAction
    data object RequestCancelClassQuest : GameAction
    data object ConfirmCancelClassQuest : GameAction
    data object OpenAchievements : GameAction
    data class OpenAchievementCategory(val category: AchievementCategory) : GameAction
    data class InspectAchievement(val category: AchievementCategory, val achievementId: String) : GameAction
    data class ClaimAchievementReward(val achievementId: String) : GameAction
    data object OpenAchievementStatistics : GameAction
    data object OpenTavern : GameAction
    data object TavernRest : GameAction
    data object TavernSleep : GameAction
    data object TavernPurifyOne : GameAction
    data object TavernPurifyAll : GameAction
    data object OpenSaveMenu : GameAction
    data object OpenCharacterMenu : GameAction
    data object OpenAttributes : GameAction
    data class InspectAttribute(val code: String) : GameAction
    data class AllocateAttributePoint(val code: String) : GameAction
    data class AllocateAttributePoints(val code: String, val amount: Int) : GameAction
    data object OpenTalents : GameAction
    data class OpenTalentStage(val stage: Int) : GameAction
    data class InspectTalentNode(val nodeId: String) : GameAction
    data class ConfirmTalentRankUp(val nodeId: String) : GameAction
    data object OpenExploration : GameAction
    data object ConfirmLowHpExploration : GameAction
    data object OpenDungeonSelection : GameAction
    data class EnterDungeon(val tierId: String) : GameAction
    data class ResolveDungeonEvent(val choice: Int) : GameAction
    data object ExitDungeonRun : GameAction
    data object OpenInventory : GameAction
    data object OpenEquipped : GameAction
    data object OpenInventoryFilters : GameAction
    data object OpenQuiver : GameAction
    data class InspectInventoryItem(val itemId: String) : GameAction
    data class InspectEquippedSlot(val slotKey: String) : GameAction
    data class EquipInventoryItem(val itemId: String) : GameAction
    data class UseInventoryItem(val itemId: String) : GameAction
    data class SellInventoryItem(val itemId: String) : GameAction
    data class UnequipSlot(val slotKey: String) : GameAction
    data class SetInventoryFilterType(val type: ItemType?) : GameAction
    data class SetInventoryMinimumRarity(val rarity: ItemRarity?) : GameAction
    data object ClearInventoryFilters : GameAction
    data class SelectActiveAmmo(val templateId: String) : GameAction
    data class LoadAmmoToQuiver(val itemId: String) : GameAction
    data class UnloadAmmoFromQuiver(val itemId: String) : GameAction
    data class SellLoadedAmmo(val itemId: String) : GameAction
    data object OpenCharacterCreationRace : GameAction
    data object OpenCharacterCreationClass : GameAction
    data object OpenCharacterCreationAttributes : GameAction
    data object CycleCharacterCreationName : GameAction
    data class SetCharacterCreationName(val name: String) : GameAction
    data class SelectCharacterCreationRace(val raceId: String) : GameAction
    data object ConfirmCharacterCreationRace : GameAction
    data class SelectCharacterCreationClass(val classId: String) : GameAction
    data object ConfirmCharacterCreationClass : GameAction
    data class EditCharacterCreationAttribute(val code: String) : GameAction
    data class IncreaseCharacterCreationAttribute(val code: String) : GameAction
    data class DecreaseCharacterCreationAttribute(val code: String) : GameAction
    data class SetCharacterCreationAttribute(val code: String, val allocated: Int) : GameAction
    data object ConfirmCharacterCreation : GameAction
    data object SaveCurrentGame : GameAction
    data object SaveAutosave : GameAction
    data object Back : GameAction
    data object Exit : GameAction
    data object Attack : GameAction
    data object EscapeCombat : GameAction
}
