package rpg.globalboss.services

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import rpg.globalboss.config.GlobalBossCadence
import rpg.globalboss.models.GlobalBossEventProgress

internal class GlobalBossProgressCycleSupport(
    private val zoneId: ZoneId
) {
    fun defaultProgress(eventId: String, cycleAnchor: Long, currentDay: Long): GlobalBossEventProgress {
        return GlobalBossEventProgress(
            eventId = eventId,
            cycleAnchorEpochMs = cycleAnchor,
            dailyAnchorEpochDay = currentDay,
            milestoneClaimMigrationDone = true
        )
    }

    fun currentEpochDay(nowMillis: Long): Long {
        return Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().toEpochDay()
    }

    fun cycleAnchor(cadence: GlobalBossCadence, nowMillis: Long): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        return when (cadence) {
            GlobalBossCadence.WEEKLY -> {
                val monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                monday.atStartOfDay(zoneId).toInstant().toEpochMilli()
            }

            GlobalBossCadence.MONTHLY -> {
                now.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
        }
    }
}
