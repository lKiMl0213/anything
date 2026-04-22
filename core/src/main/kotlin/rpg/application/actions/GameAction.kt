package rpg.application.actions

import java.nio.file.Path
import rpg.achievement.AchievementCategory
import rpg.application.progression.QuestSection
import rpg.item.ItemRarity
import rpg.model.ItemType

sealed interface GameAction {
    data object StartNewGame : GameAction
    data object ContinueSession : GameAction
    data object OpenLoadGame : GameAction
    data class LoadSave(val path: Path) : GameAction
    data object OpenProductionMenu : GameAction
    data object OpenProgressionMenu : GameAction
    data object OpenCityMenu : GameAction
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
    data object OpenTalents : GameAction
    data class OpenTalentStage(val stage: Int) : GameAction
    data class InspectTalentNode(val nodeId: String) : GameAction
    data class ConfirmTalentRankUp(val nodeId: String) : GameAction
    data object OpenExploration : GameAction
    data object OpenDungeonSelection : GameAction
    data class EnterDungeon(val tierId: String) : GameAction
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
    data object SaveCurrentGame : GameAction
    data object SaveAutosave : GameAction
    data object OpenLegacyExploration : GameAction
    data object OpenLegacyProduction : GameAction
    data object OpenLegacyCity : GameAction
    data object Back : GameAction
    data object Exit : GameAction
    data object Attack : GameAction
    data object EscapeCombat : GameAction
}
