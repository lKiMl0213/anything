package rpg.application.globalboss

import rpg.application.CombatFlowResult
import rpg.application.PendingEncounter
import rpg.combat.CombatMode
import rpg.engine.GameEngine
import rpg.globalboss.services.GlobalBossCatalogService
import rpg.globalboss.services.GlobalBossProgressService
import rpg.model.DungeonRun
import rpg.model.GameState
import rpg.navigation.NavigationState
import rpg.world.RunRoomType

data class GlobalBossCommandResult(
    val success: Boolean,
    val state: GameState,
    val messages: List<String> = emptyList(),
    val encounter: PendingEncounter? = null
)

class GlobalBossCommandService(
    private val engine: GameEngine,
    private val catalogService: GlobalBossCatalogService,
    private val progressService: GlobalBossProgressService
) {
    fun startRun(state: GameState, eventId: String, encounterText: (PendingEncounter) -> String): GlobalBossCommandResult {
        val event = catalogService.eventById(eventId)
            ?: return GlobalBossCommandResult(false, state, listOf("Evento global não encontrado."))
        val attempt = progressService.consumeRunAttempt(state, event.id)
        if (!attempt.success) {
            return GlobalBossCommandResult(false, attempt.state, attempt.messages)
        }
        val updatedState = attempt.state
        val playerStats = engine.computePlayerStats(updatedState.player, updatedState.itemInstances)
        val eventPlayer = updatedState.player.copy(
            currentHp = playerStats.derived.hpMax,
            currentMp = playerStats.derived.mpMax
        )
        val tier = catalogService.resolveTier(event)
        val boss = catalogService.buildBoss(event, updatedState.player.level)
        val basePending = PendingEncounter(
            run = DungeonRun(
                depth = 0,
                difficultyLevel = 1,
                isActive = false,
                tierId = event.id
            ),
            tier = tier,
            player = eventPlayer,
            itemInstances = updatedState.itemInstances,
            monster = boss,
            isBoss = true,
            roomType = RunRoomType.BOSS,
            introLines = emptyList(),
            combatMode = CombatMode.GLOBAL_BOSS,
            globalBossEventId = event.id
        )
        val pending = basePending.copy(
            introLines = buildList {
                add("Evento global iniciado: ${event.title}")
                if (event.description.isNotBlank()) add(event.description)
                add(encounterText(basePending))
            }
        )
        return GlobalBossCommandResult(
            success = true,
            state = updatedState,
            messages = attempt.messages + "Run do boss global iniciada.",
            encounter = pending
        )
    }

    fun buyRunAttempt(state: GameState, eventId: String): GlobalBossCommandResult {
        val result = progressService.buyPaidAttempt(state, eventId)
        return GlobalBossCommandResult(result.success, result.state, result.messages)
    }

    fun autoClear(state: GameState, eventId: String): GlobalBossCommandResult {
        val result = progressService.applyAutoClear(state, eventId)
        return GlobalBossCommandResult(result.success, result.state, result.messages)
    }

    fun claimMilestone(state: GameState, eventId: String, milestoneId: String): GlobalBossCommandResult {
        val result = progressService.claimMilestone(state, eventId, milestoneId)
        return GlobalBossCommandResult(result.success, result.state, result.messages)
    }

    fun resolveCombat(
        state: GameState,
        encounter: PendingEncounter,
        combatResult: rpg.combat.CombatResult,
        combatLog: List<String>
    ): CombatFlowResult {
        val eventId = encounter.globalBossEventId
            ?: return CombatFlowResult(
                gameState = state,
                navigation = NavigationState.GlobalBossEventDetail,
                messages = listOf("Falha ao localizar o evento do boss global.")
            )
        val progressResult = progressService.applyManualRun(
            state = state,
            eventId = eventId,
            runDamage = combatResult.telemetry.playerDamageDealt,
            playerAfterCombat = combatResult.playerAfter.copy(
                currentHp = state.player.currentHp,
                currentMp = state.player.currentMp
            ),
            itemInstances = combatResult.itemInstances
        )
        val messages = mutableListOf<String>()
        val lastCombatLines = combatLog.takeLast(3)
        messages += lastCombatLines
        if (combatResult.playerAfter.currentHp <= 0.0) {
            messages += "Run encerrada: você caiu em combate."
        } else {
            messages += "Run encerrada."
        }
        messages += progressResult.messages
        return CombatFlowResult(
            gameState = progressResult.state,
            navigation = NavigationState.GlobalBossEventDetail,
            messages = messages.filter { it.isNotBlank() }
        )
    }
}



