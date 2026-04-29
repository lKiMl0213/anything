package rpg.application

import rpg.achievement.AchievementTracker
import rpg.classquest.progress.ClassProgressionSupport
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.talent.TalentTreeService

class GameStateSupport(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker,
    private val restHealPct: Double = 0.20,
    private val restRegenMultiplier: Double = 3.0
) {
    private val classProgressionSupport = ClassProgressionSupport(
        repo = repo,
        engine = engine,
        talentTreeService = TalentTreeService(repo.balance.talentPoints),
        achievementTracker = achievementTracker,
        applyAchievementUpdate = { it.player },
        notify = {}
    )

    fun normalize(state: GameState): GameState {
        val ammoNormalizedPlayer = rpg.inventory.InventorySystem.normalizeAmmoStorage(
            state.player,
            state.itemInstances,
            engine.itemRegistry
        )
        val migratedPlayer = achievementTracker.synchronize(
            engine.classQuestService.synchronize(
                engine.classSystem.sanitizePlayerHierarchy(
                    engine.skillSystem.ensureProgress(ammoNormalizedPlayer)
                )
            )
        )
        var synchronizedState = state.copy(player = migratedPlayer)
        synchronizedState = classProgressionSupport.checkSubclassUnlock(synchronizedState)
        synchronizedState = classProgressionSupport.checkSpecializationUnlock(synchronizedState)
        val progressionPlayer = synchronizedState.player

        val computed = engine.computePlayerStats(progressionPlayer, state.itemInstances)
        val clampedPlayer = progressionPlayer.copy(
            currentHp = progressionPlayer.currentHp.coerceIn(0.0, computed.derived.hpMax),
            currentMp = progressionPlayer.currentMp.coerceIn(0.0, computed.derived.mpMax)
        )
        val syncedBoard = engine.questProgressTracker.synchronizeCollectProgressFromInventory(
            board = engine.questBoardEngine.synchronize(state.questBoard, clampedPlayer),
            inventory = clampedPlayer.inventory,
            itemInstanceTemplateById = { id -> state.itemInstances[id]?.templateId }
        )
        return state.copy(
            player = clampedPlayer,
            questBoard = syncedBoard,
            lastClockSyncEpochMs = System.currentTimeMillis()
        )
    }

    fun applyRestRoom(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): PlayerState {
        val stats = engine.computePlayerStats(player, itemInstances)
        val hpRestored = stats.derived.hpMax * restHealPct + stats.derived.hpRegen * restRegenMultiplier
        val mpRestored = stats.derived.mpMax * restHealPct + stats.derived.mpRegen * restRegenMultiplier
        return player.copy(
            currentHp = (player.currentHp + hpRestored).coerceAtMost(stats.derived.hpMax),
            currentMp = (player.currentMp + mpRestored).coerceAtMost(stats.derived.mpMax)
        )
    }

    fun applyGoldDeltaAchievements(before: PlayerState, after: PlayerState): PlayerState {
        val delta = after.gold - before.gold
        return when {
            delta > 0 -> achievementTracker.onGoldEarned(after, delta.toLong()).player
            delta < 0 -> achievementTracker.onGoldSpent(after, (-delta).toLong()).player
            else -> after
        }
    }
}
