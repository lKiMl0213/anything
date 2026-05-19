package rpg.application.hunting

import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.SkillType

class HuntingQueryService(
    private val engine: GameEngine
) {
    fun spots(state: GameState): List<HuntingSpotView> {
        val player = state.player
        val playerLevel = player.level
        val huntingSkillLevel = engine.skillSystem.snapshot(player, SkillType.HUNTING).level
        return engine.huntingService.spotCatalog().map { spot ->
            val unlockLevel = spot.recommendedLevel.coerceAtLeast(1)
            val unlocked = playerLevel >= unlockLevel
            val unlockReason = if (unlocked) null else "Desbloqueado no nv $unlockLevel"
            val baselinePreview = if (unlocked) {
                engine.huntingService.preview(
                    player = player,
                    itemInstances = state.itemInstances,
                    spotId = spot.id,
                    selectedDurationSeconds = spot.minCycleSeconds
                )
            } else {
                null
            }
            val blockedReasons = if (unlocked) {
                baselinePreview?.blockedReasons.orEmpty()
            } else {
                listOfNotNull(unlockReason)
            }
            HuntingSpotView(
                id = spot.id,
                name = spot.name,
                recommendedLevel = spot.recommendedLevel,
                unlockLevel = unlockLevel,
                playerLevel = playerLevel,
                huntingSkillLevel = huntingSkillLevel,
                unlocked = unlocked,
                unlockReason = unlockReason,
                minimumCycleSeconds = spot.minCycleSeconds,
                description = spot.description,
                previewCostGold = baselinePreview?.goldCost ?: 0,
                available = unlocked && (baselinePreview?.available ?: false),
                blockedReasons = blockedReasons
            )
        }
    }

    fun durationOptions(state: GameState, spotId: String): List<HuntingDurationOptionView> {
        val durations = engine.huntingService.durationOptionsSeconds()
        return durations.map { durationSeconds ->
            val preview = engine.huntingService.preview(
                player = state.player,
                itemInstances = state.itemInstances,
                spotId = spotId,
                selectedDurationSeconds = durationSeconds
            )
            HuntingDurationOptionView(
                durationSeconds = durationSeconds,
                label = buildLabel(preview),
                available = preview.available,
                blockedReasons = preview.blockedReasons
            )
        }
    }

    private fun buildLabel(preview: rpg.hunting.HuntingPreview): String {
        val blocked = if (preview.blockedReasons.isEmpty()) "" else " | ${preview.blockedReasons.joinToString(" | ")}"
        return "${formatDuration(preview.selectedDurationSeconds)} | custo ${preview.goldCost} ouro | ciclos ${preview.cycles} | ciclo ${format(preview.cycleDurationSeconds)}s$blocked"
    }

    private fun format(value: Double): String = "%.1f".format(value)

    private fun formatDuration(totalSeconds: Int): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val minutes = clamped / 60
        val seconds = clamped % 60
        return if (minutes > 0) {
            "${minutes}m ${seconds.toString().padStart(2, '0')}s"
        } else {
            "${seconds}s"
        }
    }
}
