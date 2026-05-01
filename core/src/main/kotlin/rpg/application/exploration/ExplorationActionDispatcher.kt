package rpg.application.exploration

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.PendingEncounter
import rpg.application.actions.GameAction
import rpg.classquest.ClassQuestDungeonDefinition
import rpg.classquest.dungeon.ClassDungeonMonsterService
import rpg.engine.GameEngine
import rpg.navigation.NavigationState
import rpg.world.RunRoomType

internal class ExplorationActionDispatcher(
    private val engine: GameEngine,
    private val stateSupport: GameStateSupport,
    private val classDungeonMonsterService: ClassDungeonMonsterService
) {
    private val dungeonEventCoordinator = DungeonEventFlowCoordinator(engine, stateSupport)

    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenDungeonSelection -> move(session, NavigationState.DungeonSelection)
            is GameAction.EnterDungeon -> enterDungeon(session, action.tierId, forceClassDungeon = false)
            is GameAction.EnterClassDungeon -> enterDungeon(session, action.tierId, forceClassDungeon = true)
            GameAction.ExitDungeonRun -> exitDungeonRun(session)
            is GameAction.ResolveDungeonEvent -> resolveDungeonEvent(session, action.choice)
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

    private fun enterDungeon(
        session: GameSession,
        tierId: String,
        forceClassDungeon: Boolean
    ): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val player = normalized.player
        val normalizedTierId = tierId.trim()
        if (normalizedTierId.isEmpty()) {
            return GameActionResult(session.copy(messages = listOf("Area invalida.")))
        }
        val continuedRun = normalized.currentRun?.takeIf { run ->
            run.isActive && run.tierId?.equals(normalizedTierId, ignoreCase = true) == true
        }
        val classDungeon = resolveClassDungeon(
            player = player,
            continuedRun = continuedRun,
            forceClassDungeon = forceClassDungeon
        )
        if (forceClassDungeon && classDungeon == null) {
            return GameActionResult(
                session.copy(
                    messages = listOf("Instancia de classe indisponivel. Aceite e inicie uma quest de classe primeiro.")
                )
            )
        }
        val tier = engine.tierById(normalizedTierId)
        if (!engine.canEnterTier(player, tier)) {
            return GameActionResult(session.copy(messages = listOf("Nivel insuficiente para este tier.")))
        }

        val run = continuedRun ?: engine.startRun(normalizedTierId).copy(
            classDungeonPathId = classDungeon?.pathId,
            classDungeonUnlockType = classDungeon?.unlockType?.name
        )
        val roomType = nextRoomType(run, classDungeon)
        return when (roomType) {
            RunRoomType.EVENT -> {
                val pendingEvent = dungeonEventCoordinator.preparePendingEvent(
                    state = normalized,
                    run = run,
                    tier = tier
                )
                GameActionResult(
                    session = session.copy(
                        gameState = normalized.copy(currentRun = run),
                        navigation = NavigationState.DungeonEvent,
                        pendingDungeonEvent = pendingEvent,
                        pendingEncounter = null,
                        messages = buildEnterMessages(classDungeon, run, continuedRun)
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
                        pendingDungeonEvent = null,
                        pendingEncounter = null,
                        messages = buildEnterMessages(classDungeon, run, continuedRun) +
                            listOf("Sala de descanso. HP e MP restaurados parcialmente.")
                    )
                )
            }

            RunRoomType.MONSTER,
            RunRoomType.BOSS -> {
                val isBoss = roomType == RunRoomType.BOSS
                val monster = if (classDungeon != null) {
                    classDungeonMonsterService.generate(
                        dungeon = classDungeon,
                        tier = tier,
                        run = run,
                        player = player,
                        isBoss = isBoss
                    )
                } else {
                    engine.generateMonster(tier, run, player, isBoss = isBoss)
                }
                val encounter = PendingEncounter(
                    run = run,
                    tier = tier,
                    player = player,
                    itemInstances = normalized.itemInstances,
                    monster = monster,
                    isBoss = isBoss,
                    roomType = roomType,
                    classDungeon = classDungeon,
                    introLines = listOf(
                        engine.encounterText(monster, tier, engine.computePlayerStats(player, normalized.itemInstances))
                    )
                )
                GameActionResult(
                    session = session.copy(
                        gameState = normalized.copy(currentRun = run),
                        navigation = NavigationState.Combat,
                        pendingDungeonEvent = null,
                        pendingEncounter = encounter,
                        messages = buildEnterMessages(classDungeon, run, continuedRun)
                    ),
                    effect = GameEffect.LaunchCombat(encounter)
                )
            }
        }
    }

    private fun resolveDungeonEvent(session: GameSession, choice: Int): GameActionResult {
        return dungeonEventCoordinator.resolve(session, choice)
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
                    pendingDungeonEvent = null,
                    pendingEncounter = null,
                    messages = listOf("Nenhuma run ativa para encerrar.")
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = normalized.copy(currentRun = null),
                navigation = NavigationState.Exploration,
                pendingDungeonEvent = null,
                pendingEncounter = null,
                messages = listOf("Voce saiu da dungeon atual.")
            )
        )
    }

    private fun resolveClassDungeon(
        player: rpg.model.PlayerState,
        continuedRun: rpg.model.DungeonRun?,
        forceClassDungeon: Boolean
    ): ClassQuestDungeonDefinition? {
        val fromRun = continuedRun?.let {
            engine.classQuestService.resolveDungeonByRunData(
                player = player,
                unlockTypeRaw = it.classDungeonUnlockType,
                pathIdRaw = it.classDungeonPathId
            )
        }
        if (fromRun != null) return fromRun
        if (!forceClassDungeon) return null
        return engine.classQuestService.activeDungeon(player)
    }

    private fun nextRoomType(
        run: rpg.model.DungeonRun,
        classDungeon: ClassQuestDungeonDefinition?
    ): RunRoomType {
        if (classDungeon == null) return engine.nextRoomType(run)
        return if (engine.isBossRoomDue(run)) RunRoomType.BOSS else RunRoomType.MONSTER
    }

    private fun buildEnterMessages(
        classDungeon: ClassQuestDungeonDefinition?,
        run: rpg.model.DungeonRun,
        continuedRun: rpg.model.DungeonRun?
    ): List<String> {
        if (classDungeon == null || continuedRun != null) return emptyList()
        val tierLabel = run.tierId?.takeIf { it.isNotBlank() }?.let { id ->
            runCatching { engine.tierDisplayName(engine.tierById(id)) }.getOrDefault(id)
        } ?: "area desconhecida"
        return listOf("Voce entrou na instancia de classe de ${classDungeon.pathName} (base: $tierLabel).")
    }
}
