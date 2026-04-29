package rpg.android.combat

import java.util.ArrayDeque
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import rpg.application.CombatFlowResult
import rpg.application.PendingEncounter
import rpg.application.actions.GameAction
import rpg.combat.CombatAction
import rpg.combat.CombatSnapshot
import rpg.combat.PlayerCombatController
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.ItemType
import rpg.navigation.NavigationState
import rpg.status.StatusSystem
import rpg.world.RunRoomType
import rpg.android.state.CombatActionButtonUi
import rpg.android.state.CombatConsumableUi
import rpg.android.state.CombatUiState

class AndroidCombatFlowController(
    private val engine: GameEngine
) {
    private val actionChannel = Channel<CombatAction>(Channel.UNLIMITED)
    private val _uiState = MutableStateFlow(
        CombatUiState(
            title = "Combate",
            enemyName = "-",
            introLines = emptyList(),
            playerName = "-",
            playerHp = 0.0,
            playerHpMax = 1.0,
            playerMp = 0.0,
            playerMpMax = 1.0,
            enemyHp = 0.0,
            enemyHpMax = 1.0,
            playerAtbProgress = 0f,
            enemyAtbProgress = 0f,
            playerAtbLabel = "0%",
            enemyAtbLabel = "0%",
            playerReady = false,
            statusLines = emptyList(),
            logLines = emptyList(),
            actions = emptyList(),
            consumables = emptyList()
        )
    )

    val uiState: StateFlow<CombatUiState> = _uiState

    fun submitAttack() {
        actionChannel.trySend(CombatAction.Attack())
    }

    fun submitEscape() {
        actionChannel.trySend(CombatAction.Escape)
    }

    fun submitUseItem(itemId: String) {
        actionChannel.trySend(CombatAction.UseItem(itemId))
    }

    fun run(gameState: GameState, encounter: PendingEncounter): CombatFlowResult {
        val controller = AndroidController(encounter)
        val displayName = engine.monsterDisplayName(encounter.monster)
        val result = engine.combatEngine.runBattle(
            playerState = encounter.player,
            itemInstances = encounter.itemInstances,
            monster = encounter.monster,
            tier = encounter.tier,
            displayName = displayName,
            controller = controller,
            eventLogger = { message -> controller.onCombatEvent(message) }
        )

        var updatedState = gameState.copy(
            player = result.playerAfter,
            itemInstances = result.itemInstances
        )

        return when {
            result.escaped -> CombatFlowResult(
                gameState = updatedState.copy(currentRun = null),
                navigation = NavigationState.Exploration,
                messages = listOf("Voce fugiu do combate.") + controller.finalLogLines()
            )

            !result.victory -> CombatFlowResult(
                gameState = updatedState.copy(currentRun = null),
                navigation = NavigationState.Hub,
                messages = listOf(
                    "Voce foi derrotado.",
                    "A expedicao terminou e voce retornou ao acampamento."
                ) + controller.finalLogLines()
            )

            else -> {
                val levelBefore = result.playerAfter.level
                val victory = engine.resolveVictory(
                    player = result.playerAfter,
                    itemInstances = result.itemInstances,
                    monster = encounter.monster,
                    tier = encounter.tier,
                    collectToLoot = false
                )
                val advancedRun = engine.advanceRun(
                    run = encounter.run,
                    bossDefeated = encounter.isBoss,
                    clearedRoomType = if (encounter.isBoss) RunRoomType.BOSS else RunRoomType.MONSTER,
                    victoryInRoom = true
                )
                updatedState = updatedState.copy(
                    player = victory.player,
                    itemInstances = victory.itemInstances,
                    currentRun = advancedRun
                )
                val rewardLines = mutableListOf(
                    "$displayName foi derrotado!",
                    "Ganhou ${victory.xpGain} XP e ${victory.goldGain} ouro."
                )
                if (victory.player.level > levelBefore) {
                    rewardLines += "Level up! Agora voce esta no nivel ${victory.player.level}."
                }
                victory.dropOutcome.itemInstance?.let { rewardLines += "Drop: ${it.name}." }
                if (victory.dropOutcome.itemInstance == null && victory.dropOutcome.itemId != null) {
                    rewardLines += "Drop: ${victory.dropOutcome.itemId} x${victory.dropOutcome.quantity.coerceAtLeast(1)}."
                }
                CombatFlowResult(
                    gameState = updatedState,
                    navigation = NavigationState.Exploration,
                    messages = rewardLines + controller.finalLogLines()
                )
            }
        }
    }

    private inner class AndroidController(
        private val encounter: PendingEncounter
    ) : PlayerCombatController {
        private val history = ArrayDeque<String>()
        private val historyLimit = 10

        fun onCombatEvent(message: String) {
            if (message.isBlank()) return
            history += message
            while (history.size > historyLimit) {
                history.removeFirst()
            }
            publish(snapshot = null, pausedForDecision = false)
        }

        fun finalLogLines(): List<String> = history.toList()

        override fun onFrame(snapshot: CombatSnapshot) {
            publish(snapshot, pausedForDecision = snapshot.pausedForDecision)
        }

        override fun onDecisionStarted(snapshot: CombatSnapshot) {
            publish(snapshot, pausedForDecision = true)
        }

        override fun onDecisionEnded() {
            val current = _uiState.value
            _uiState.value = current.copy(playerReady = false)
        }

        override fun pollAction(snapshot: CombatSnapshot): CombatAction? {
            publish(snapshot, pausedForDecision = true)
            return runBlocking { actionChannel.receive() }
        }

        private fun publish(snapshot: CombatSnapshot?, pausedForDecision: Boolean) {
            val current = _uiState.value
            if (snapshot == null) {
                _uiState.value = current.copy(logLines = history.toList())
                return
            }

            val playerReady = pausedForDecision && snapshot.playerRuntime.state.name == "READY"
            val playerProgress = progress(snapshot.playerRuntime.actionBar, snapshot.playerRuntime.actionThreshold)
            val enemyProgress = progress(snapshot.monsterRuntime.actionBar, snapshot.monsterRuntime.actionThreshold)
            val playerStats = engine.computePlayerStats(snapshot.player, snapshot.itemInstances)
            val enemyStats = engine.computeMonsterStats(snapshot.monster)
            val consumables = buildConsumables(snapshot)
            val statusLines = buildStatusLines(snapshot)
            val actionButtons = listOf(
                CombatActionButtonUi("Atacar", GameAction.Attack, playerReady),
                CombatActionButtonUi("Fugir", GameAction.EscapeCombat, playerReady)
            )

            _uiState.value = CombatUiState(
                title = if (encounter.isBoss) "Combate | Boss" else "Combate",
                enemyName = engine.monsterDisplayName(snapshot.monster),
                introLines = encounter.introLines,
                playerName = snapshot.player.name,
                playerHp = snapshot.player.currentHp,
                playerHpMax = playerStats.derived.hpMax,
                playerMp = snapshot.player.currentMp,
                playerMpMax = playerStats.derived.mpMax,
                enemyHp = snapshot.monsterHp,
                enemyHpMax = enemyStats.derived.hpMax,
                playerAtbProgress = playerProgress,
                enemyAtbProgress = enemyProgress,
                playerAtbLabel = if (playerReady) "PRONTO" else "${(playerProgress * 100f).toInt()}% carregando",
                enemyAtbLabel = "${(enemyProgress * 100f).toInt()}% carregando",
                playerReady = playerReady,
                statusLines = statusLines,
                logLines = history.toList(),
                actions = actionButtons,
                consumables = consumables
            )
        }

        private fun buildStatusLines(snapshot: CombatSnapshot): List<String> {
            val playerStatuses = snapshot.playerRuntime.statuses
                .joinToString(" | ") { status ->
                    "${StatusSystem.displayName(status.type)} ${format(status.remainingSeconds)}s"
                }
            val enemyStatuses = snapshot.monsterRuntime.statuses
                .joinToString(" | ") { status ->
                    "${StatusSystem.displayName(status.type)} ${format(status.remainingSeconds)}s"
                }
            return buildList {
                if (playerStatuses.isNotBlank()) add("Voce: $playerStatuses")
                if (enemyStatuses.isNotBlank()) add("Inimigo: $enemyStatuses")
            }
        }

        private fun buildConsumables(snapshot: CombatSnapshot): List<CombatConsumableUi> {
            val grouped = linkedMapOf<String, Pair<String, Int>>()
            snapshot.player.inventory.forEach { itemId ->
                val resolved = engine.itemResolver.resolve(itemId, snapshot.itemInstances) ?: return@forEach
                if (resolved.type != ItemType.CONSUMABLE) return@forEach
                val current = grouped[itemId]
                val label = resolved.name
                grouped[itemId] = if (current == null) label to 1 else label to (current.second + 1)
            }
            return grouped.map { (itemId, value) ->
                CombatConsumableUi(itemId = itemId, label = "${value.first} x${value.second}")
            }
        }
    }

    private fun progress(current: Double, max: Double): Float {
        if (max <= 0.0) return 0f
        return (current / max).toFloat().coerceIn(0f, 1f)
    }

    private fun format(value: Double): String = "%.1f".format(value)
}
