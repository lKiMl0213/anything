package rpg.application

import rpg.achievement.AchievementTracker
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState

class GameStateSupport(
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker,
    private val restHealPct: Double = 0.20,
    private val restRegenMultiplier: Double = 3.0
) {
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
        val computed = engine.computePlayerStats(migratedPlayer, state.itemInstances)
        val clampedPlayer = migratedPlayer.copy(
            currentHp = migratedPlayer.currentHp.coerceIn(0.0, computed.derived.hpMax),
            currentMp = migratedPlayer.currentMp.coerceIn(0.0, computed.derived.mpMax)
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
}
