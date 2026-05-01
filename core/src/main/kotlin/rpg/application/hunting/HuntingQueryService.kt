package rpg.application.hunting

import rpg.engine.GameEngine
import rpg.model.GameState

class HuntingQueryService(
    private val engine: GameEngine
) {
    fun spots(state: GameState): List<HuntingSpotView> {
        val player = state.player
        return engine.huntingService.availableSpots(player.level).map { spot ->
            val baselinePreview = engine.huntingService.preview(
                player = player,
                itemInstances = state.itemInstances,
                spotId = spot.id,
                selectedDurationSeconds = spot.minCycleSeconds
            )
            HuntingSpotView(
                id = spot.id,
                name = spot.name,
                recommendedLevel = spot.recommendedLevel,
                minimumCycleSeconds = spot.minCycleSeconds,
                description = spot.description,
                previewCostGold = baselinePreview.goldCost,
                available = baselinePreview.available,
                blockedReasons = baselinePreview.blockedReasons
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
