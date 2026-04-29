package rpg.cli

import rpg.cli.model.BattleOutcome
import rpg.cli.model.DeathPenaltyResult
import rpg.cli.model.RunFinalizeResult
import rpg.model.GameState

internal class DungeonOutcomeFlow(
    private val autoSave: (GameState) -> Unit,
    private val finalizeRun: (
        player: rpg.model.PlayerState,
        loot: MutableList<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> RunFinalizeResult,
    private val applyDeathPenalty: (
        player: rpg.model.PlayerState,
        loot: MutableList<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> DeathPenaltyResult,
    private val onRunCompleted: (
        board: rpg.quest.QuestBoardState,
        runCount: Int
    ) -> rpg.quest.QuestBoardState,
    private val shouldCountRunCompletion: (rpg.model.DungeonRun) -> Boolean,
    private val emit: (String) -> Unit
) {
    fun handleRunFailure(
        state: GameState,
        outcome: BattleOutcome,
        loot: MutableList<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        run: rpg.model.DungeonRun,
        questBoard: rpg.quest.QuestBoardState,
        worldTimeMinutes: Double,
        lastClockSync: Long
    ): GameState {
        return if (outcome.escaped) {
            val finalized = finalizeRun(outcome.playerAfter, loot, itemInstances)
            val updated = finalized.player
            val progressedBoard = if (shouldCountRunCompletion(run)) {
                onRunCompleted(questBoard, 1)
            } else {
                questBoard
            }
            emit("\nVoce saiu da run com ${loot.size} itens.")
            val updatedState = state.copy(
                player = updated,
                currentRun = null,
                itemInstances = finalized.itemInstances,
                questBoard = progressedBoard,
                worldTimeMinutes = worldTimeMinutes,
                lastClockSyncEpochMs = lastClockSync
            )
            autoSave(updatedState)
            updatedState
        } else {
            val result = applyDeathPenalty(outcome.playerAfter, loot, itemInstances)
            emit("\nVoce foi derrotado e expulso da dungeon.")
            val updatedState = state.copy(
                player = result.player,
                currentRun = null,
                itemInstances = result.itemInstances,
                questBoard = questBoard,
                worldTimeMinutes = worldTimeMinutes,
                lastClockSyncEpochMs = lastClockSync
            )
            autoSave(updatedState)
            updatedState
        }
    }
}
