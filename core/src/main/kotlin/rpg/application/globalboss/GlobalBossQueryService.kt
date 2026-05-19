package rpg.application.globalboss

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import rpg.globalboss.config.GlobalBossCadence
import rpg.globalboss.config.GlobalBossEventDef
import rpg.globalboss.config.GlobalBossRewardDef
import rpg.globalboss.config.GlobalBossRunLimitConfig
import rpg.globalboss.models.GlobalBossEventProgress
import rpg.globalboss.services.GlobalBossCatalogService
import rpg.globalboss.services.GlobalBossProgressService
import rpg.io.DataRepository
import rpg.model.GameState

class GlobalBossQueryService(
    private val repo: DataRepository,
    private val catalogService: GlobalBossCatalogService,
    private val progressService: GlobalBossProgressService,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val claimedAtFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    fun hasAlert(state: GameState): Boolean {
        return menuView(state).hasAlert
    }

    fun eventIdByCadence(cadence: GlobalBossCadence): String? {
        return catalogService.eventByCadence(cadence)?.id
    }

    fun menuView(state: GameState): GlobalBossMenuView {
        val normalized = progressService.synchronize(state)
        val limits = progressService.runLimits(normalized.player)
        val nowMillis = System.currentTimeMillis()
        val items = catalogService.allEvents().map { event ->
            val progress = eventProgress(normalized, event)
            val runsUsed = progress.runsUsed
            val remaining = (limits.maxRunsPerDay - runsUsed).coerceAtLeast(0)
            val claimableCount = claimableMilestones(event, progress).size
            val alert = hasRunAvailable(progress, limits) || claimableCount > 0
            GlobalBossMenuItemView(
                eventId = event.id,
                cadence = event.cadence,
                title = event.title,
                runsLabel = "Runs hoje: $runsUsed/${limits.maxRunsPerDay} (restam $remaining)",
                timeRemainingLabel = cycleRemainingLabel(event.cadence, nowMillis),
                alert = alert
            )
        }
        return GlobalBossMenuView(
            items = items,
            hasAlert = items.any { it.alert }
        )
    }

    fun eventDetail(state: GameState, eventId: String): GlobalBossEventDetailView? {
        val event = catalogService.eventById(eventId) ?: return null
        val normalized = progressService.synchronize(state)
        val nowMillis = System.currentTimeMillis()
        val progress = eventProgress(normalized, event)
        val limits = progressService.runLimits(normalized.player)
        val runCashCost = limits.runCashCost(event.cadence)
        val paidBuyRemaining = (limits.purchasableRunsPerDay - progress.dailyPaidRunsBought).coerceAtLeast(0)
        val runsRemaining = (limits.maxRunsPerDay - progress.runsUsed).coerceAtLeast(0)
        val canStart = hasRunAvailable(progress, limits)
        val canAutoClear = canStart && progress.bestRun > 0
        val canBuy = runsRemaining > 0 &&
            paidBuyRemaining > 0 &&
            normalized.player.premiumCash >= runCashCost

        val milestoneViews = milestoneViews(event, progress)
        val claimableCount = milestoneViews.count { it.claimable }
        val quests = event.quests.map { quest ->
            val completed = quest.id in progress.quests
            GlobalBossQuestView(
                label = "${quest.title} (${quest.targetValue}) ${if (completed) "[OK]" else "[ ]"}",
                completed = completed
            )
        }
        val runsLabel = buildString {
            append("Gratis ")
            append(progress.dailyFreeRunsUsed)
            append("/")
            append(limits.freeRunsPerDay)
            append(" | Compradas ")
            append(progress.dailyPaidRunsBought)
            append("/")
            append(limits.purchasableRunsPerDay)
            append(" | Usadas ")
            append(progress.runsUsed)
            append("/")
            append(limits.maxRunsPerDay)
        }
        return GlobalBossEventDetailView(
            eventId = event.id,
            cadence = event.cadence,
            title = event.title,
            description = event.description,
            bossName = resolveBossName(event),
            totalDamageLabel = "Dano total: ${"%.1f".format(progress.totalDamage)}",
            totalPointsLabel = "Pontos totais: ${progress.totalPoints}",
            bestRunLabel = "Melhor: ${progress.bestRun} pontos",
            runsLabel = runsLabel,
            cycleRemainingLabel = cycleRemainingLabel(event.cadence, nowMillis),
            runsRemaining = runsRemaining,
            claimableMilestonesCount = claimableCount,
            canStartRun = canStart,
            canAutoClear = canAutoClear,
            canBuyAttempt = canBuy,
            buyCostCash = runCashCost,
            milestones = milestoneViews,
            quests = quests,
            rankingLabel = "Ranking global online: indisponivel offline",
            alert = canStart || claimableCount > 0
        )
    }

    fun milestoneMenu(state: GameState, eventId: String): GlobalBossMilestoneMenuView? {
        val event = catalogService.eventById(eventId) ?: return null
        val normalized = progressService.synchronize(state)
        val progress = eventProgress(normalized, event)
        val milestones = milestoneViews(event, progress)
        val claimableCount = milestones.count { it.claimable }
        val summary = listOf(
            "Pontos totais: ${progress.totalPoints}",
            "Milestones disponíveis: $claimableCount"
        )
        return GlobalBossMilestoneMenuView(
            eventId = event.id,
            title = "Milestones",
            summaryLines = summary,
            milestones = milestones,
            claimableMilestonesCount = claimableCount,
            alert = claimableCount > 0
        )
    }

    private fun eventProgress(state: GameState, event: GlobalBossEventDef): GlobalBossEventProgress {
        return state.globalBoss.events[event.id.lowercase()] ?: GlobalBossEventProgress(eventId = event.id)
    }

    private fun claimableMilestones(event: GlobalBossEventDef, progress: GlobalBossEventProgress): List<String> {
        return event.milestones
            .filter { it.pointsRequired <= progress.totalPoints }
            .map { it.id }
            .filter { it !in progress.claimedMilestones }
    }

    private fun hasRunAvailable(progress: GlobalBossEventProgress, limits: GlobalBossRunLimitConfig): Boolean {
        val runsRemaining = (limits.maxRunsPerDay - progress.runsUsed).coerceAtLeast(0)
        if (runsRemaining <= 0) return false
        val freeRemaining = (limits.freeRunsPerDay - progress.dailyFreeRunsUsed).coerceAtLeast(0)
        val paidReady = (progress.dailyPaidRunsBought - progress.dailyPaidRunsUsed).coerceAtLeast(0)
        return freeRemaining > 0 || paidReady > 0
    }

    private fun milestoneViews(event: GlobalBossEventDef, progress: GlobalBossEventProgress): List<GlobalBossMilestoneView> {
        val rows = event.milestones.map { milestone ->
            val reached = milestone.pointsRequired <= progress.totalPoints || milestone.id in progress.milestones
            val claimed = milestone.id in progress.claimedMilestones
            val claimable = reached && !claimed
            val status = when {
                claimable -> "[Disponível]"
                claimed -> "[Resgatado]"
                else -> "[Pendente]"
            }
            val claimedAt = progress.milestoneClaimedAtEpochMs[milestone.id]?.let { epoch ->
                Instant.ofEpochMilli(epoch).atZone(zoneId).format(claimedAtFormatter)
            }
            GlobalBossMilestoneView(
                id = milestone.id,
                label = "Atinja ${milestone.pointsRequired} pontos",
                rewardLabel = "Recompensa: ${rewardLabel(milestone.reward)}",
                statusLabel = "Status: $status",
                claimable = claimable,
                claimed = claimed,
                claimedAtLabel = claimedAt.let { "Recebido em: $it" }
            ) to milestone.pointsRequired
        }
        return rows.sortedWith(
            compareBy<Pair<GlobalBossMilestoneView, Long>> {
                when {
                    it.first.claimable -> 0
                    !it.first.claimed -> 1
                    else -> 2
                }
            }.thenBy { it.second }
        ).map { it.first }
    }

    private fun rewardLabel(reward: GlobalBossRewardDef): String {
        val parts = mutableListOf<String>()
        if (reward.xp > 0) parts += "XP +${reward.xp}"
        if (reward.gold > 0) parts += "Ouro +${reward.gold}"
        if (reward.questCurrency > 0) parts += "Moeda +${reward.questCurrency}"
        if (reward.premiumCash > 0) parts += "CASH +${reward.premiumCash}"
        if (parts.isEmpty()) return "Sem recompensa"
        return parts.joinToString(" | ")
    }

    private fun resolveBossName(event: GlobalBossEventDef): String {
        return repo.monsterArchetypes[event.bossArchetypeId]?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: repo.monsterArchetypes[event.bossArchetypeId]?.name
            ?: event.bossArchetypeId
    }

    private fun cycleRemainingLabel(cadence: GlobalBossCadence, nowMillis: Long): String {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val cycleEnd = when (cadence) {
            GlobalBossCadence.WEEKLY -> {
                now.toLocalDate()
                    .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    .atStartOfDay(zoneId)
            }

            GlobalBossCadence.MONTHLY -> {
                now.toLocalDate()
                    .withDayOfMonth(1)
                    .plusMonths(1)
                    .atStartOfDay(zoneId)
            }
        }
        val remainingSeconds = Duration.between(now, cycleEnd).seconds.coerceAtLeast(0L)
        return formatRemainingTime(remainingSeconds)
    }

    private fun formatRemainingTime(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "agora"
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        return if (days > 0L) {
            "${days}d ${hours}h ${minutes}m"
        } else {
            "${hours}h ${minutes}m"
        }
    }
}




