package rpg.application.exploration

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.PendingEncounter
import rpg.application.actions.GameAction
import rpg.engine.GameEngine
import rpg.navigation.NavigationState
import rpg.world.RunRoomType

class ExplorationActionDispatcher(
    private val engine: GameEngine,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenDungeonSelection -> move(session, NavigationState.DungeonSelection)
            is GameAction.EnterDungeon -> enterDungeon(session, action.tierId)
            GameAction.ExitDungeonRun -> exitDungeonRun(session)
            else -> null
        }
    }

    private fun move(session: GameSession, destination: NavigationState): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = destination,
                messages = emptyList()
            )
        )
    }

    private fun enterDungeon(session: GameSession, tierId: String): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val player = normalized.player
        val continuedRun = normalized.currentRun?.takeIf { run ->
            run.isActive && run.tierId?.equals(tierId, ignoreCase = true) == true
        }
        val tier = engine.tierById(tierId)
        if (!engine.canEnterTier(player, tier)) {
            return GameActionResult(session.copy(messages = listOf("Nivel insuficiente para este tier.")))
        }

        val run = continuedRun ?: engine.startRun(tierId)
        val roomType = engine.nextRoomType(run)
        return when (roomType) {
            RunRoomType.EVENT -> {
                val advancedRun = engine.advanceRun(
                    run = run,
                    bossDefeated = false,
                    clearedRoomType = RunRoomType.EVENT,
                    victoryInRoom = false
                )
                GameActionResult(
                    session = session.copy(
                        gameState = normalized.copy(currentRun = advancedRun),
                        navigation = NavigationState.Exploration,
                        messages = listOf(
                            "Voce encontrou uma sala de evento.",
                            "Nada de relevante aconteceu e a expedicao seguiu adiante."
                        )
                    )
                )
            }

            RunRoomType.REST -> {
                val healed = stateSupport.applyRestRoom(normalized.player, normalized.itemInstances)
                val advancedRun = engine.advanceRun(
                    run = run,
                    bossDefeated = false,
                    clearedRoomType = RunRoomType.REST,
                    victoryInRoom = false
                )
                GameActionResult(
                    session = session.copy(
                        gameState = normalized.copy(player = healed, currentRun = advancedRun),
                        navigation = NavigationState.Exploration,
                        messages = listOf("Sala de descanso. HP e MP restaurados parcialmente.")
                    )
                )
            }

            RunRoomType.MONSTER,
            RunRoomType.BOSS -> {
                val isBoss = roomType == RunRoomType.BOSS
                val monster = engine.generateMonster(tier, run, player, isBoss = isBoss)
                val encounter = PendingEncounter(
                    run = run,
                    tier = tier,
                    player = player,
                    itemInstances = normalized.itemInstances,
                    monster = monster,
                    isBoss = isBoss,
                    introLines = listOf(
                        engine.encounterText(monster, tier, engine.computePlayerStats(player, normalized.itemInstances))
                    )
                )
                GameActionResult(
                    session = session.copy(
                        gameState = normalized.copy(currentRun = run),
                        navigation = NavigationState.Combat,
                        pendingEncounter = encounter,
                        messages = emptyList()
                    ),
                    effect = GameEffect.LaunchCombat(encounter)
                )
            }
        }
    }

    private fun exitDungeonRun(session: GameSession): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        if (normalized.currentRun == null) {
            return GameActionResult(
                session = session.copy(
                    gameState = normalized,
                    navigation = NavigationState.Exploration,
                    messages = listOf("Nenhuma run ativa para encerrar.")
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized.copy(currentRun = null),
                navigation = NavigationState.Exploration,
                messages = listOf("Voce saiu da dungeon atual.")
            )
        )
    }
}
