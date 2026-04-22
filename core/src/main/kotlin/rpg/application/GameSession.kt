package rpg.application

import java.nio.file.Path
import rpg.achievement.AchievementCategory
import rpg.application.inventory.InventoryFilterState
import rpg.application.progression.QuestSection
import rpg.model.DungeonRun
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.monster.MonsterInstance
import rpg.navigation.NavigationState

data class PendingEncounter(
    val run: DungeonRun,
    val tier: MapTierDef,
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val monster: MonsterInstance,
    val isBoss: Boolean,
    val introLines: List<String>
)

data class GameSession(
    val gameState: GameState? = null,
    val navigation: NavigationState = NavigationState.MainMenu,
    val availableSaves: List<Path> = emptyList(),
    val currentSavePath: Path? = null,
    val currentSaveName: String? = null,
    val pendingEncounter: PendingEncounter? = null,
    val inventoryFilter: InventoryFilterState = InventoryFilterState(),
    val selectedAttributeCode: String? = null,
    val selectedInventoryItemId: String? = null,
    val selectedEquipmentSlot: String? = null,
    val selectedTalentTreeId: String? = null,
    val selectedTalentNodeId: String? = null,
    val selectedQuestSection: QuestSection? = null,
    val selectedQuestId: String? = null,
    val selectedAchievementCategory: AchievementCategory? = null,
    val selectedAchievementId: String? = null,
    val messages: List<String> = emptyList(),
    val exitRequested: Boolean = false
)
