package rpg.quest

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.min
import rpg.model.PlayerState
import rpg.model.QuestTier

data class QuestBoardActionResult(
    val board: QuestBoardState,
    val success: Boolean,
    val message: String
)

class QuestBoardEngine(
    private val generator: QuestGenerator,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun synchronize(
        board: QuestBoardState,
        player: PlayerState,
        nowMillis: Long = System.currentTimeMillis()
    ): QuestBoardState {
        val context = generator.buildContext(player)
        var updated = board
        updated = synchronizeCycle(
            board = updated,
            tier = QuestTier.DAILY,
            context = context,
            requiredCount = DAILY_QUEST_COUNT,
            cycleStartMillis = cycleStartDaily(nowMillis),
            cycleEndMillis = cycleEndDaily(nowMillis),
            usedReplaces = updated.dailyReplaceUsed
        ) { lastReset, quests, used ->
            updated.copy(
                dailyQuests = quests,
                dailyReplaceUsed = used,
                lastDailyReset = lastReset
            )
        }

        updated = synchronizeCycle(
            board = updated,
            tier = QuestTier.WEEKLY,
            context = context,
            requiredCount = WEEKLY_QUEST_COUNT,
            cycleStartMillis = cycleStartWeekly(nowMillis),
            cycleEndMillis = cycleEndWeekly(nowMillis),
            usedReplaces = updated.weeklyReplaceUsed
        ) { lastReset, quests, used ->
            updated.copy(
                weeklyQuests = quests,
                weeklyReplaceUsed = used,
                lastWeeklyReset = lastReset
            )
        }

        updated = synchronizeCycle(
            board = updated,
            tier = QuestTier.MONTHLY,
            context = context,
            requiredCount = MONTHLY_QUEST_COUNT,
            cycleStartMillis = cycleStartMonthly(nowMillis),
            cycleEndMillis = cycleEndMonthly(nowMillis),
            usedReplaces = updated.monthlyReplaceUsed
        ) { lastReset, quests, used ->
            updated.copy(
                monthlyQuests = quests,
                monthlyReplaceUsed = used,
                lastMonthlyReset = lastReset
            )
        }

        var available = updated.availableAcceptableQuestPool
            .filter { it.status == QuestStatus.ACTIVE }
            .take(MAX_ACCEPTABLE_POOL)
        var lastRoll = updated.lastAcceptableQuestRoll
        if (lastRoll <= 0L) {
            lastRoll = nowMillis - ACCEPTABLE_REFRESH_INTERVAL_MS
        }
        val elapsedTicks = ((nowMillis - lastRoll) / ACCEPTABLE_REFRESH_INTERVAL_MS).toInt().coerceAtLeast(0)
        if (elapsedTicks > 0) {
            val toGenerate = min(elapsedTicks, MAX_ACCEPTABLE_POOL - available.size)
            repeat(toGenerate) {
                val avoid = (available + updated.acceptedQuests).mapTo(mutableSetOf()) { it.templateId }
                val generated = generator.generateSingle(
                    tier = QuestTier.ACCEPTED,
                    context = context,
                    createdAt = nowMillis,
                    expiresAt = null,
                    sourcePool = "acceptable_pool",
                    avoidTemplateIds = avoid
                )
                if (generated != null) {
                    available = (available + generated).take(MAX_ACCEPTABLE_POOL)
                }
            }
            lastRoll += elapsedTicks.toLong() * ACCEPTABLE_REFRESH_INTERVAL_MS
        }

        return updated.copy(
            availableAcceptableQuestPool = available,
            acceptedQuests = updated.acceptedQuests
                .filter { it.status == QuestStatus.ACTIVE || it.status == QuestStatus.READY_TO_CLAIM }
                .take(MAX_ACCEPTED_ACTIVE),
            completedQuests = updated.completedQuests.takeLast(MAX_COMPLETED_HISTORY),
            lastAcceptableQuestRoll = maxOf(lastRoll, nowMillis - ACCEPTABLE_REFRESH_INTERVAL_MS)
        )
    }

    fun acceptQuest(
        board: QuestBoardState,
        instanceId: String,
        acceptedAt: Long = System.currentTimeMillis()
    ): QuestBoardActionResult {
        if (board.acceptedQuests.size >= MAX_ACCEPTED_ACTIVE) {
            return QuestBoardActionResult(
                board = board,
                success = false,
                message = "Limite de $MAX_ACCEPTED_ACTIVE quests aceitas atingido."
            )
        }
        val quest = board.availableAcceptableQuestPool.firstOrNull { it.instanceId == instanceId }
            ?: return QuestBoardActionResult(board, false, "Quest nao encontrada na pool aceitavel.")
        val updatedQuest = quest.copy(
            acceptedAt = acceptedAt,
            status = QuestStatus.ACTIVE,
            canCancel = true,
            sourcePool = "accepted"
        )
        return QuestBoardActionResult(
            board = board.copy(
                acceptedQuests = (board.acceptedQuests + updatedQuest).take(MAX_ACCEPTED_ACTIVE),
                availableAcceptableQuestPool = board.availableAcceptableQuestPool.filterNot { it.instanceId == instanceId }
            ),
            success = true,
            message = "Quest aceita: ${updatedQuest.title}"
        )
    }

    fun cancelAcceptedQuest(board: QuestBoardState, instanceId: String): QuestBoardActionResult {
        val quest = board.acceptedQuests.firstOrNull { it.instanceId == instanceId }
            ?: return QuestBoardActionResult(board, false, "Quest aceita nao encontrada.")
        val updatedAccepted = board.acceptedQuests.filterNot { it.instanceId == instanceId }
        val cancelled = quest.copy(status = QuestStatus.CANCELLED)
        return QuestBoardActionResult(
            board = board.copy(
                acceptedQuests = updatedAccepted,
                completedQuests = (board.completedQuests + cancelled).takeLast(MAX_COMPLETED_HISTORY)
            ),
            success = true,
            message = "Quest cancelada: ${quest.title}"
        )
    }

    fun replaceQuest(
        board: QuestBoardState,
        player: PlayerState,
        tier: QuestTier,
        instanceId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): QuestBoardActionResult {
        if (tier == QuestTier.ACCEPTED) {
            return QuestBoardActionResult(board, false, "Replace nao e permitido para quests aceitas.")
        }

        val context = generator.buildContext(player)
        val (quests, used, limit, expiresAt) = when (tier) {
            QuestTier.DAILY -> Quad(
                board.dailyQuests,
                board.dailyReplaceUsed,
                DAILY_REPLACE_LIMIT,
                cycleEndDaily(nowMillis)
            )
            QuestTier.WEEKLY -> Quad(
                board.weeklyQuests,
                board.weeklyReplaceUsed,
                WEEKLY_REPLACE_LIMIT,
                cycleEndWeekly(nowMillis)
            )
            QuestTier.MONTHLY -> Quad(
                board.monthlyQuests,
                board.monthlyReplaceUsed,
                MONTHLY_REPLACE_LIMIT,
                cycleEndMonthly(nowMillis)
            )
            QuestTier.ACCEPTED -> Quad(emptyList(), 0, 0, null)
        }

        if (used >= limit) {
            return QuestBoardActionResult(
                board = board,
                success = false,
                message = "Limite de replace desta categoria esgotado."
            )
        }
        val index = quests.indexOfFirst { it.instanceId == instanceId }
        if (index < 0) {
            return QuestBoardActionResult(board, false, "Quest nao encontrada para replace.")
        }
        val current = quests[index]
        val avoid = quests.mapTo(mutableSetOf()) { it.templateId }
        avoid.add(current.templateId)
        val replacement = generator.generateSingle(
            tier = tier,
            context = context,
            createdAt = nowMillis,
            expiresAt = expiresAt,
            sourcePool = "${tier.name.lowercase()}_board",
            avoidTemplateIds = avoid
        ) ?: return QuestBoardActionResult(
            board = board,
            success = false,
            message = "Nao foi possivel gerar uma nova quest valida para replace."
        )

        val updatedList = quests.toMutableList().also { it[index] = replacement }
        val updatedBoard = when (tier) {
            QuestTier.DAILY -> board.copy(
                dailyQuests = updatedList,
                dailyReplaceUsed = used + 1
            )
            QuestTier.WEEKLY -> board.copy(
                weeklyQuests = updatedList,
                weeklyReplaceUsed = used + 1
            )
            QuestTier.MONTHLY -> board.copy(
                monthlyQuests = updatedList,
                monthlyReplaceUsed = used + 1
            )
            QuestTier.ACCEPTED -> board
        }
        return QuestBoardActionResult(
            board = updatedBoard,
            success = true,
            message = "Quest substituida: ${replacement.title}"
        )
    }

    private fun synchronizeCycle(
        board: QuestBoardState,
        tier: QuestTier,
        context: QuestGenerationContext,
        requiredCount: Int,
        cycleStartMillis: Long,
        cycleEndMillis: Long,
        usedReplaces: Int,
        writer: (Long, List<QuestInstance>, Int) -> QuestBoardState
    ): QuestBoardState {
        val (lastReset, quests, sourcePool) = when (tier) {
            QuestTier.DAILY -> Triple(board.lastDailyReset, board.dailyQuests, "daily_board")
            QuestTier.WEEKLY -> Triple(board.lastWeeklyReset, board.weeklyQuests, "weekly_board")
            QuestTier.MONTHLY -> Triple(board.lastMonthlyReset, board.monthlyQuests, "monthly_board")
            QuestTier.ACCEPTED -> Triple(0L, emptyList(), "accepted")
        }

        val needsReset = lastReset < cycleStartMillis ||
            quests.size != requiredCount ||
            quests.any { it.expiresAt != null && it.expiresAt <= cycleStartMillis }

        if (!needsReset) {
            return writer(lastReset, quests, usedReplaces)
        }

        val generated = generator.generateBatch(
            tier = tier,
            count = requiredCount,
            context = context,
            createdAt = cycleStartMillis,
            expiresAt = cycleEndMillis,
            sourcePool = sourcePool
        )
        return writer(cycleStartMillis, generated, 0)
    }

    private fun cycleStartDaily(nowMillis: Long): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        return now.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun cycleEndDaily(nowMillis: Long): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        return now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun cycleStartWeekly(nowMillis: Long): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun cycleEndWeekly(nowMillis: Long): Long {
        val weeklyStart = cycleStartWeekly(nowMillis)
        val next = Instant.ofEpochMilli(weeklyStart).atZone(zoneId).plusWeeks(1)
        return next.toInstant().toEpochMilli()
    }

    private fun cycleStartMonthly(nowMillis: Long): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val firstDay = now.toLocalDate().withDayOfMonth(1)
        return firstDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun cycleEndMonthly(nowMillis: Long): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val next = now.toLocalDate().withDayOfMonth(1).plusMonths(1)
        return next.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private data class Quad<A, B, C, D>(
        val a: A,
        val b: B,
        val c: C,
        val d: D
    )

    companion object {
        const val DAILY_QUEST_COUNT = 6
        const val WEEKLY_QUEST_COUNT = 10
        const val MONTHLY_QUEST_COUNT = 12
        const val MAX_ACCEPTED_ACTIVE = 5
        const val MAX_ACCEPTABLE_POOL = 12
        const val DAILY_REPLACE_LIMIT = 3
        const val WEEKLY_REPLACE_LIMIT = 4
        const val MONTHLY_REPLACE_LIMIT = 5
        const val MAX_COMPLETED_HISTORY = 200
        const val ACCEPTABLE_REFRESH_INTERVAL_MS = 20 * 60 * 1000L
    }
}
