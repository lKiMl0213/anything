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
import rpg.combat.CombatResult
import rpg.combat.CombatSnapshot
import rpg.combat.CombatMode
import rpg.combat.PlayerCombatController
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.ItemType
import rpg.status.StatusType
import rpg.android.state.CombatActionButtonUi
import rpg.android.state.CombatConsumableUi
import rpg.android.state.CombatUiState

internal class AndroidCombatFlowController(
    private val engine: GameEngine,
    private val outcomeResolver: AndroidCombatOutcomeResolver,
    private val resolveGlobalBossCombat: (
        gameState: GameState,
        encounter: PendingEncounter,
        combatResult: CombatResult,
        combatLog: List<String>
    ) -> CombatFlowResult
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
            activeEffectName = null,
            activeEffectRemainingSeconds = 0,
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
        val logLines = controller.finalLogLines()
        return if (encounter.combatMode == CombatMode.GLOBAL_BOSS) {
            resolveGlobalBossCombat(gameState, encounter, result, logLines)
        } else {
            outcomeResolver.resolve(
                gameState = gameState,
                encounter = encounter,
                result = result,
                combatLog = logLines
            )
        }
    }

    private inner class AndroidController(
        private val encounter: PendingEncounter
    ) : PlayerCombatController {
        private val history = ArrayDeque<String>()
        private val historyLimit = 8

        fun onCombatEvent(message: String) {
            val normalized = sanitizeLogLine(message)
            if (normalized.isBlank()) return
            history += normalized
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
            val (activeEffectName, activeEffectRemainingSeconds) = buildActiveEffect(snapshot)
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
                activeEffectName = activeEffectName,
                activeEffectRemainingSeconds = activeEffectRemainingSeconds,
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
                    "${statusIcon(status.type)} ${format(status.remainingSeconds)}s"
                }
            val enemyStatuses = snapshot.monsterRuntime.statuses
                .joinToString(" | ") { status ->
                    "${statusIcon(status.type)} ${format(status.remainingSeconds)}s"
                }
            return buildList {
                if (playerStatuses.isNotBlank()) add("Você: $playerStatuses")
                if (enemyStatuses.isNotBlank()) add("Inimigo: $enemyStatuses")
            }
        }

        private fun sanitizeLogLine(raw: String): String {
            return raw
                .replace(Regex("\\u001B\\[[;\\d]*m"), "")
                .replace(Regex("\\[(?:\\d{1,3};?)+m"), "")
                .trim()
        }

        private fun statusIcon(type: StatusType): String {
            return when (type) {
                StatusType.BURNING -> "\uD83D\uDD25"
                StatusType.FROZEN -> "\u2744\uFE0F"
                StatusType.POISONED -> "\u2620\uFE0F"
                StatusType.PARALYZED -> "\u26A1"
                StatusType.BLEEDING -> "\uD83E\uDE78"
                StatusType.WEAKNESS -> "\uD83E\uDDE9"
                StatusType.SLOW -> "\uD83D\uDC22"
                StatusType.MARKED -> "\uD83C\uDFAF"
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

    private fun buildActiveEffect(snapshot: CombatSnapshot): Pair<String?, Int> {
        val foodBuffName = snapshot.player.foodBuffName.takeIf {
            snapshot.player.foodBuffRemainingMinutes > 0.0 && it.isNotBlank()
        }
        if (foodBuffName != null) {
            return foodBuffName to (snapshot.player.foodBuffRemainingMinutes * 60.0).toInt().coerceAtLeast(0)
        }

        val statusImmunitySeconds = snapshot.playerRuntime.statusImmunitySeconds
        val tempBuffSeconds = snapshot.playerRuntime.tempBuffRemainingSeconds
        val effectLabels = mutableListOf<String>()
        if (statusImmunitySeconds > 0.0) effectLabels += "Imunidade a status"
        if (tempBuffSeconds > 0.0) effectLabels += "Buff temporário"
        if (effectLabels.isEmpty()) return null to 0
        return effectLabels.joinToString(" + ") to maxOf(statusImmunitySeconds, tempBuffSeconds).toInt().coerceAtLeast(0)
    }
}
