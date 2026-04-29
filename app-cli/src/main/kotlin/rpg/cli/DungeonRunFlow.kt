package rpg.cli

import rpg.classquest.dungeon.ClassDungeonMonsterService
import rpg.cli.model.BattleOutcome
import rpg.cli.model.EventRoomOutcome
import rpg.cli.model.RunFinalizeResult
import rpg.classquest.ClassQuestDungeonDefinition
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.monster.MonsterInstance
import rpg.world.RunRoomType

internal class DungeonRunFlow(
    private val engine: GameEngine,
    private val roomTimeMinutes: Double,
    private val entryFlow: DungeonEntryFlow,
    private val outcomeFlow: DungeonOutcomeFlow,
    private val classDungeonMonsterFlow: ClassDungeonMonsterService,
    private val synchronizeQuestBoard: (
        board: rpg.quest.QuestBoardState,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> rpg.quest.QuestBoardState,
    private val computePlayerStats: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> rpg.engine.ComputedStats,
    private val battleMonster: (
        playerState: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        monster: MonsterInstance,
        tier: MapTierDef,
        lootCollector: MutableList<String>?,
        isBoss: Boolean,
        classDungeon: ClassQuestDungeonDefinition?
    ) -> BattleOutcome,
    private val preBossSanctuaryRoom: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val restRoom: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        tier: MapTierDef
    ) -> PlayerState,
    private val eventRoom: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ) -> EventRoomOutcome,
    private val applyBattleQuestProgress: (
        board: rpg.quest.QuestBoardState,
        monster: MonsterInstance,
        outcome: BattleOutcome,
        isBoss: Boolean
    ) -> rpg.quest.QuestBoardState,
    private val tickEffects: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val promptContinue: () -> Boolean,
    private val finalizeRun: (
        player: PlayerState,
        loot: MutableList<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> RunFinalizeResult,
    private val onRunCompleted: (
        board: rpg.quest.QuestBoardState,
        runCount: Int
    ) -> rpg.quest.QuestBoardState,
    private val autoSave: (GameState) -> Unit,
    private val emit: (String) -> Unit
) {
    private fun runCountsAsCompleted(run: rpg.model.DungeonRun): Boolean {
        return run.victoriesInRun >= 10 && run.bossesDefeatedInRun >= 1 && run.depth >= 10
    }

    fun enterDungeon(state: GameState, forceClassDungeon: Boolean = false): GameState {
        val entry = entryFlow.prepareRun(state, forceClassDungeon) ?: return state
        val chosenTier = entry.chosenTier
        val classDungeonMode = entry.classDungeonMode
        val clearedState = entry.clearedState

        var player = clearedState.player
        var itemInstances = clearedState.itemInstances
        var questBoard = synchronizeQuestBoard(clearedState.questBoard, player, itemInstances)
        var run = engine.startRun(chosenTier.id)
        var worldTimeMinutes = clearedState.worldTimeMinutes
        var lastClockSync = clearedState.lastClockSyncEpochMs
        val loot = mutableListOf<String>()

        while (run.isActive) {
            val roomType = if (classDungeonMode == null) {
                engine.nextRoomType(run)
            } else {
                if (engine.isBossRoomDue(run)) RunRoomType.BOSS else RunRoomType.MONSTER
            }
            emit("\n--- Sala ${run.roomsCleared + 1} | Dificuldade ${run.difficultyLevel} ---")

            when (roomType) {
                RunRoomType.BOSS -> {
                    player = preBossSanctuaryRoom(player, itemInstances)
                    val monster = if (classDungeonMode == null) {
                        engine.generateMonster(chosenTier, run, player, isBoss = true)
                    } else {
                        classDungeonMonsterFlow.generate(
                            dungeon = classDungeonMode,
                            tier = chosenTier,
                            run = run,
                            player = player,
                            isBoss = true
                        )
                    }
                    emit(engine.encounterText(monster, chosenTier, computePlayerStats(player, itemInstances)))
                    val outcome = battleMonster(
                        player,
                        itemInstances,
                        monster,
                        chosenTier,
                        loot,
                        true,
                        classDungeonMode
                    )
                    itemInstances = outcome.itemInstances
                    if (!outcome.victory) {
                        return outcomeFlow.handleRunFailure(
                                state = state,
                                outcome = outcome,
                                loot = loot,
                                itemInstances = itemInstances,
                                run = run,
                                questBoard = questBoard,
                                worldTimeMinutes = worldTimeMinutes,
                                lastClockSync = lastClockSync
                            )
                    }
                    player = outcome.playerAfter
                    questBoard = applyBattleQuestProgress(questBoard, monster, outcome, true)
                    run = engine.advanceRun(
                        run = run,
                        bossDefeated = true,
                        clearedRoomType = RunRoomType.BOSS,
                        victoryInRoom = true
                    )
                    questBoard = engine.questProgressTracker.onFloorReached(questBoard, run.depth)
                    if (classDungeonMode == null) {
                        emit("Boss derrotado! A dificuldade aumentou.")
                    } else {
                        emit("Boss da instancia derrotado.")
                    }
                }
                RunRoomType.MONSTER -> {
                    val monster = if (classDungeonMode == null) {
                        engine.generateMonster(chosenTier, run, player, isBoss = false)
                    } else {
                        classDungeonMonsterFlow.generate(
                            dungeon = classDungeonMode,
                            tier = chosenTier,
                            run = run,
                            player = player,
                            isBoss = false
                        )
                    }
                    emit(engine.encounterText(monster, chosenTier, computePlayerStats(player, itemInstances)))
                    val outcome = battleMonster(
                        player,
                        itemInstances,
                        monster,
                        chosenTier,
                        loot,
                        false,
                        classDungeonMode
                    )
                    itemInstances = outcome.itemInstances
                    if (!outcome.victory) {
                        return outcomeFlow.handleRunFailure(
                                state = state,
                                outcome = outcome,
                                loot = loot,
                                itemInstances = itemInstances,
                                run = run,
                                questBoard = questBoard,
                                worldTimeMinutes = worldTimeMinutes,
                                lastClockSync = lastClockSync
                            )
                    }
                    player = outcome.playerAfter
                    questBoard = applyBattleQuestProgress(questBoard, monster, outcome, false)
                    run = engine.advanceRun(
                        run = run,
                        bossDefeated = false,
                        clearedRoomType = RunRoomType.MONSTER,
                        victoryInRoom = true
                    )
                    questBoard = engine.questProgressTracker.onFloorReached(questBoard, run.depth)
                }
                RunRoomType.REST -> {
                    player = restRoom(player, itemInstances, chosenTier)
                    run = engine.advanceRun(
                        run = run,
                        bossDefeated = false,
                        clearedRoomType = RunRoomType.REST,
                        victoryInRoom = false
                    )
                    questBoard = engine.questProgressTracker.onFloorReached(questBoard, run.depth)
                }
                RunRoomType.EVENT -> {
                    val eventOutcome = eventRoom(
                        player,
                        itemInstances,
                        loot,
                        run.difficultyLevel,
                        run.depth,
                        chosenTier,
                        questBoard
                    )
                    if (eventOutcome.battleOutcome != null) {
                        val outcome = eventOutcome.battleOutcome
                        itemInstances = outcome.itemInstances
                        if (!outcome.victory) {
                            return outcomeFlow.handleRunFailure(
                                state = state,
                                outcome = outcome,
                                loot = loot,
                                itemInstances = itemInstances,
                                run = run,
                                questBoard = eventOutcome.questBoard,
                                worldTimeMinutes = worldTimeMinutes,
                                lastClockSync = lastClockSync
                            )
                        }
                        player = outcome.playerAfter
                    } else {
                        player = eventOutcome.player
                        itemInstances = eventOutcome.itemInstances ?: itemInstances
                    }
                    questBoard = eventOutcome.questBoard
                    run = engine.advanceRun(
                        run = run,
                        bossDefeated = false,
                        clearedRoomType = RunRoomType.EVENT,
                        victoryInRoom = eventOutcome.battleOutcome?.victory == true
                    )
                    questBoard = engine.questProgressTracker.onFloorReached(questBoard, run.depth)
                }
            }

            run = run.copy(lootCollected = loot.toList())
            player = tickEffects(player, itemInstances)
            worldTimeMinutes += roomTimeMinutes
            lastClockSync = System.currentTimeMillis()

            if (!promptContinue()) {
                val finalized = finalizeRun(player, loot, itemInstances)
                val updated = finalized.player
                itemInstances = finalized.itemInstances
                val runCompleted = runCountsAsCompleted(run)
                if (runCompleted) {
                    questBoard = onRunCompleted(questBoard, 1)
                }
                val updatedState = state.copy(
                    player = updated,
                    currentRun = null,
                    itemInstances = itemInstances,
                    questBoard = questBoard,
                    worldTimeMinutes = worldTimeMinutes,
                    lastClockSyncEpochMs = lastClockSync
                )
                autoSave(updatedState)
                emit("\nRun encerrada. Loot guardado: ${loot.size} itens.")
                if (!runCompleted) {
                    emit("Run nao contou como concluida: elimine o boss da rodada para validar quests de conclusao.")
                }
                return updatedState
            }
        }

        return state.copy(
            player = player,
            currentRun = null,
            itemInstances = itemInstances,
            questBoard = questBoard,
            worldTimeMinutes = worldTimeMinutes,
            lastClockSyncEpochMs = lastClockSync
        )
    }
}
