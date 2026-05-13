package rpg.globalboss.services

import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max
import rpg.engine.GameEngine
import rpg.globalboss.config.GlobalBossEventDef
import rpg.globalboss.config.GlobalBossQuestObjectiveType
import rpg.globalboss.config.GlobalBossRunLimitConfig
import rpg.globalboss.config.GlobalBossSystemConfig
import rpg.globalboss.models.GlobalBossEventProgress
import rpg.globalboss.models.GlobalBossState
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.premium.PremiumSupport

class GlobalBossProgressService(
    private val engine: GameEngine,
    private val config: GlobalBossSystemConfig,
    eventsById: Map<String, GlobalBossEventDef>,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val events = eventsById.mapKeys { it.key.lowercase() }
    private val pointsDivisor = config.pointsDamageDivisor.coerceAtLeast(0.1)
    private val rewardService = GlobalBossRewardService(engine)
    private val cycleSupport = GlobalBossProgressCycleSupport(zoneId)

    fun runLimits(player: PlayerState): GlobalBossRunLimitConfig {
        val bonusRuns = PremiumSupport.globalBossExtraRuns(player).coerceAtLeast(0)
        if (bonusRuns <= 0) return config.runLimits
        return config.runLimits.copy(
            freeRunsPerDay = config.runLimits.freeRunsPerDay + bonusRuns,
            maxRunsPerDay = config.runLimits.maxRunsPerDay + bonusRuns
        )
    }

    fun synchronize(state: GameState, nowMillis: Long = System.currentTimeMillis()): GameState {
        if (events.isEmpty()) return state
        val currentDay = cycleSupport.currentEpochDay(nowMillis)
        val updatedMap = state.globalBoss.events.toMutableMap()
        for (event in events.values) {
            val key = event.id.lowercase()
            val cycleAnchor = cycleSupport.cycleAnchor(event.cadence, nowMillis)
            val current = updatedMap[key] ?: cycleSupport.defaultProgress(event.id, cycleAnchor, currentDay)
            val cycleAligned = if (current.cycleAnchorEpochMs != cycleAnchor) {
                cycleSupport.defaultProgress(event.id, cycleAnchor, currentDay)
            } else {
                current.copy(eventId = event.id)
            }
            val dayAligned = if (cycleAligned.dailyAnchorEpochDay != currentDay) {
                cycleAligned.copy(
                    dailyAnchorEpochDay = currentDay,
                    runsUsed = 0,
                    dailyFreeRunsUsed = 0,
                    dailyPaidRunsBought = 0,
                    dailyPaidRunsUsed = 0
                )
            } else {
                cycleAligned
            }
            val migrated = if (!dayAligned.milestoneClaimMigrationDone) {
                dayAligned.copy(
                    claimedMilestones = dayAligned.claimedMilestones + dayAligned.milestones,
                    milestoneClaimMigrationDone = true
                )
            } else {
                dayAligned
            }
            updatedMap[key] = migrated
        }
        return state.copy(globalBoss = GlobalBossState(events = updatedMap))
    }

    fun consumeRunAttempt(state: GameState, eventId: String): GlobalBossAttemptResult {
        val (normalized, event, progress) = resolveEventAndProgress(state, eventId)
            ?: return GlobalBossAttemptResult(false, state, listOf("Evento global não encontrado."))
        val limits = runLimits(normalized.player)
        if (progress.runsUsed >= limits.maxRunsPerDay) {
            return GlobalBossAttemptResult(false, normalized, listOf("Limite diario de ${limits.maxRunsPerDay} runs atingido."))
        }
        return when {
            progress.dailyFreeRunsUsed < limits.freeRunsPerDay -> {
                val updated = progress.copy(
                    runsUsed = progress.runsUsed + 1,
                    cycleRunsUsed = progress.cycleRunsUsed + 1,
                    dailyFreeRunsUsed = progress.dailyFreeRunsUsed + 1
                )
                GlobalBossAttemptResult(
                    success = true,
                    state = writeProgress(normalized, event.id, updated),
                    messages = listOf("Tentativa gratis consumida (${updated.dailyFreeRunsUsed}/${limits.freeRunsPerDay})."),
                    attemptType = GlobalBossAttemptType.FREE
                )
            }

            progress.dailyPaidRunsUsed < progress.dailyPaidRunsBought -> {
                val updated = progress.copy(
                    runsUsed = progress.runsUsed + 1,
                    cycleRunsUsed = progress.cycleRunsUsed + 1,
                    dailyPaidRunsUsed = progress.dailyPaidRunsUsed + 1
                )
                GlobalBossAttemptResult(
                    success = true,
                    state = writeProgress(normalized, event.id, updated),
                    messages = listOf("Tentativa comprada consumida (${updated.dailyPaidRunsUsed}/${updated.dailyPaidRunsBought})."),
                    attemptType = GlobalBossAttemptType.PAID
                )
            }

            progress.dailyPaidRunsBought < limits.purchasableRunsPerDay -> GlobalBossAttemptResult(
                false,
                normalized,
                listOf("Sem tentativas disponíveis. Compre mais na opção de compra.")
            )

            else -> GlobalBossAttemptResult(
                false,
                normalized,
                listOf("Sem tentativas disponíveis hoje.")
            )
        }
    }

    fun buyPaidAttempt(state: GameState, eventId: String): GlobalBossAttemptResult {
        val (normalized, event, progress) = resolveEventAndProgress(state, eventId)
            ?: return GlobalBossAttemptResult(false, state, listOf("Evento global não encontrado."))
        val limits = runLimits(normalized.player)
        if (progress.dailyPaidRunsBought >= limits.purchasableRunsPerDay) {
            return GlobalBossAttemptResult(false, normalized, listOf("Limite diario de compras atingido."))
        }
        if (progress.runsUsed >= limits.maxRunsPerDay) {
            return GlobalBossAttemptResult(false, normalized, listOf("Limite diario de runs atingido hoje."))
        }
        if (normalized.player.premiumCash < limits.purchasedRunCashCost) {
            return GlobalBossAttemptResult(false, normalized, listOf("CASH insuficiente para comprar tentativa."))
        }
        val updatedProgress = progress.copy(dailyPaidRunsBought = progress.dailyPaidRunsBought + 1)
        val updatedState = writeProgress(
            normalized.copy(
                player = normalized.player.copy(
                    premiumCash = normalized.player.premiumCash - limits.purchasedRunCashCost
                )
            ),
            event.id,
            updatedProgress
        )
        return GlobalBossAttemptResult(
            success = true,
            state = updatedState,
            messages = listOf("Tentativa extra comprada por ${limits.purchasedRunCashCost} CASH.")
        )
    }

    fun applyManualRun(
        state: GameState,
        eventId: String,
        runDamage: Double,
        playerAfterCombat: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): GlobalBossProgressResult {
        val (normalized, event, progress) = resolveEventAndProgress(state, eventId)
            ?: return GlobalBossProgressResult(false, state, listOf("Evento global não encontrado."))
        val points = floor(runDamage.coerceAtLeast(0.0) / pointsDivisor).toLong().coerceAtLeast(0L)
        return applyRunProgress(
            state = normalized.copy(player = playerAfterCombat, itemInstances = itemInstances),
            event = event,
            current = progress,
            runDamage = runDamage.coerceAtLeast(0.0),
            runPoints = points,
            runLabel = "Run concluida"
        )
    }

    fun applyAutoClear(state: GameState, eventId: String): GlobalBossProgressResult {
        val (normalized, event, progress) = resolveEventAndProgress(state, eventId)
            ?: return GlobalBossProgressResult(false, state, listOf("Evento global não encontrado."))
        if (progress.bestRun <= 0L) {
            return GlobalBossProgressResult(false, normalized, listOf("Auto clear indisponivel: registre uma run primeiro."))
        }
        val attempt = consumeRunAttempt(normalized, eventId)
        if (!attempt.success) {
            return GlobalBossProgressResult(false, attempt.state, attempt.messages)
        }
        val afterProgress = attempt.state.globalBoss.events[event.id.lowercase()] ?: progress
        val runPoints = afterProgress.bestRun.coerceAtLeast(0L)
        val runDamage = runPoints * pointsDivisor
        return applyRunProgress(
            state = attempt.state,
            event = event,
            current = afterProgress,
            runDamage = runDamage,
            runPoints = runPoints,
            runLabel = "Auto clear aplicado"
        )
    }

    private fun applyRunProgress(
        state: GameState,
        event: GlobalBossEventDef,
        current: GlobalBossEventProgress,
        runDamage: Double,
        runPoints: Long,
        runLabel: String
    ): GlobalBossProgressResult {
        var updatedProgress = current.copy(
            totalDamage = current.totalDamage + runDamage,
            totalPoints = current.totalPoints + runPoints,
            bestRun = max(current.bestRun, runPoints)
        )
        var player = state.player
        val lines = mutableListOf(
            "$runLabel: ${"%.1f".format(runDamage)} de dano.",
            "Pontos desta run: $runPoints",
            "Total acumulado: ${updatedProgress.totalPoints} pontos."
        )

        for (milestone in event.milestones.sortedBy { it.pointsRequired }) {
            if (milestone.id in updatedProgress.milestones || updatedProgress.totalPoints < milestone.pointsRequired) continue
            updatedProgress = updatedProgress.copy(milestones = updatedProgress.milestones + milestone.id)
            lines += "Milestone disponível para resgate: ${milestone.pointsRequired} pontos."
        }

        for (quest in event.quests) {
            if (quest.id in updatedProgress.quests) continue
            val completed = when (quest.objective) {
                GlobalBossQuestObjectiveType.TOTAL_POINTS -> updatedProgress.totalPoints >= quest.targetValue
                GlobalBossQuestObjectiveType.SINGLE_RUN_POINTS -> runPoints >= quest.targetValue
                GlobalBossQuestObjectiveType.RUNS_COMPLETED -> updatedProgress.cycleRunsUsed >= quest.targetValue
            }
            if (!completed) continue
            val rewardResult = rewardService.apply(player, scaleReward(event, quest.reward))
            player = rewardResult.player
            updatedProgress = updatedProgress.copy(quests = updatedProgress.quests + quest.id)
            lines += "Quest global concluida: ${quest.title}"
            rewardResult.lines.forEach { lines += it }
        }

        val updatedState = writeProgress(state.copy(player = player), event.id, updatedProgress)
        return GlobalBossProgressResult(true, updatedState, lines, runPoints = runPoints, runDamage = runDamage)
    }

    fun claimMilestone(
        state: GameState,
        eventId: String,
        milestoneId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): GlobalBossProgressResult {
        val (normalized, event, progress) = resolveEventAndProgress(state, eventId)
            ?: return GlobalBossProgressResult(false, state, listOf("Evento global não encontrado."))
        val milestone = event.milestones.firstOrNull { it.id.equals(milestoneId, ignoreCase = true) }
            ?: return GlobalBossProgressResult(false, normalized, listOf("Milestone não encontrado."))
        if (milestone.id in progress.claimedMilestones) {
            return GlobalBossProgressResult(false, normalized, listOf("Milestone ja resgatado."))
        }
        val isReached = milestone.id in progress.milestones || progress.totalPoints >= milestone.pointsRequired
        if (!isReached) {
            return GlobalBossProgressResult(false, normalized, listOf("Milestone ainda não concluido."))
        }
        var updatedProgress = progress
        if (milestone.id !in updatedProgress.milestones) {
            updatedProgress = updatedProgress.copy(milestones = updatedProgress.milestones + milestone.id)
        }
        val rewardResult = rewardService.apply(normalized.player, scaleReward(event, milestone.reward))
        updatedProgress = updatedProgress.copy(
            claimedMilestones = updatedProgress.claimedMilestones + milestone.id,
            milestoneClaimedAtEpochMs = updatedProgress.milestoneClaimedAtEpochMs + (milestone.id to nowMillis),
            milestoneClaimMigrationDone = true
        )
        val updatedState = writeProgress(normalized.copy(player = rewardResult.player), event.id, updatedProgress)
        return GlobalBossProgressResult(
            success = true,
            state = updatedState,
            messages = listOf("Milestone resgatado: ${milestone.pointsRequired} pontos.") + rewardResult.lines
        )
    }

    private fun scaleReward(event: GlobalBossEventDef, reward: rpg.globalboss.config.GlobalBossRewardDef) =
        GlobalBossRewardScaleSupport.scale(reward, event.balance.rewardMultiplierPct)

    private fun resolveEventAndProgress(
        state: GameState,
        eventId: String
    ): Triple<GameState, GlobalBossEventDef, GlobalBossEventProgress>? {
        val normalized = synchronize(state)
        val event = events[eventId.trim().lowercase()] ?: return null
        val progress = normalized.globalBoss.events[event.id.lowercase()]
            ?: cycleSupport.defaultProgress(
                event.id,
                cycleSupport.cycleAnchor(event.cadence, System.currentTimeMillis()),
                cycleSupport.currentEpochDay(System.currentTimeMillis())
            )
        return Triple(normalized, event, progress)
    }

    private fun writeProgress(state: GameState, eventId: String, progress: GlobalBossEventProgress): GameState {
        val key = eventId.lowercase()
        return state.copy(globalBoss = state.globalBoss.copy(events = state.globalBoss.events + (key to progress)))
    }
}



