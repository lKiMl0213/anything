package rpg.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import rpg.achievement.AchievementProgress
import rpg.achievement.PlayerLifetimeStats
import rpg.classquest.ClassQuestProgress
import rpg.quest.QuestBoardState

@Serializable
data class PlayerState(
    val name: String,
    val level: Int = 1,
    val xp: Int = 0,
    val unspentAttrPoints: Int = 0,
    val unspentSkillPoints: Int = 0,
    val classId: String,
    val subclassId: String? = null,
    val specializationId: String? = null,
    val raceId: String,
    val baseAttributes: Attributes = Attributes(),
    val currentHp: Double = 0.0,
    val currentMp: Double = 0.0,
    val inventory: List<String> = emptyList(),
    val equipped: Map<String, String> = emptyMap(),
    val inventoryBaseSlots: Int = 30,
    val gold: Int = 0,
    val questCurrency: Int = 0,
    val premiumCash: Int = 0,
    val skillProgress: Map<String, SkillProgressState> = emptyMap(),
    val deathDebuffStacks: Int = 0,
    val deathDebuffMinutes: Double = 0.0,
    val deathXpPenaltyPct: Double = 0.0,
    val deathXpPenaltyMinutes: Double = 0.0,
    val roomEffectMultiplier: Double = 1.0,
    val roomEffectRooms: Int = 0,
    val roomAttrBonus: Attributes = Attributes(),
    val roomAttrRooms: Int = 0,
    val roomDerivedAdd: DerivedStats = DerivedStats(),
    val roomDerivedMult: DerivedStats = DerivedStats(),
    val roomDerivedRooms: Int = 0,
    val runAttrBonus: Attributes = Attributes(),
    val runDerivedAdd: DerivedStats = DerivedStats(),
    val runDerivedMult: DerivedStats = DerivedStats(),
    val runAttrMultiplier: Double = 1.0,
    val nextHealMultiplier: Double = 1.0,
    val ignoreNextDebuff: Boolean = false,
    val reviveOnce: Boolean = false,
    val roomRegenHpPct: Double = 0.0,
    val roomRegenHpRooms: Int = 0,
    val roomRegenMpPct: Double = 0.0,
    val roomRegenMpRooms: Int = 0,
    val talentNodeRanks: Map<String, Int> = emptyMap(),
    val unlockedTalentTrees: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("talents")
    val legacyTalentIds: List<String> = emptyList(),
    val subclassUnlockProgressByClass: Map<String, SubclassUnlockProgress> = emptyMap(),
    val specializationUnlockProgressByClass: Map<String, SpecializationUnlockProgress> = emptyMap(),
    val classQuestProgressByKey: Map<String, ClassQuestProgress> = emptyMap(),
    val lifetimeStats: PlayerLifetimeStats = PlayerLifetimeStats(),
    val achievementProgressById: Map<String, AchievementProgress> = emptyMap(),
    val rareDropPity: Int = 0,
    val roomAttrRollRooms: Int = 0,
    val roomAttrRollAmount: Int = 0
)

@Serializable
data class WorldState(
    val mapId: String,
    val currentRoomId: String,
    val clearedRooms: List<String> = emptyList()
)

@Serializable
data class DungeonRun(
    val depth: Int = 0,
    val difficultyLevel: Int = 1,
    val roomsCleared: Int = 0,
    val victoriesInRun: Int = 0,
    val bossesDefeatedInRun: Int = 0,
    val restRoomsInCycle: Int = 0,
    val eventRoomsInCycle: Int = 0,
    val lootCollected: List<String> = emptyList(),
    val isActive: Boolean = true,
    val tierId: String? = null,
    val biomeId: String? = null,
    val mutationTier: Int = 0
)

@Serializable
data class GameState(
    val player: PlayerState,
    val world: WorldState,
    val currentRun: DungeonRun? = null,
    val itemInstances: Map<String, ItemInstance> = emptyMap(),
    val questBoard: QuestBoardState = QuestBoardState(),
    val worldTimeMinutes: Double = 0.0,
    val lastClockSyncEpochMs: Long = 0L
)
