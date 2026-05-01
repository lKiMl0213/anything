package rpg.application.hunting

import rpg.application.production.ProductionTimedActionView

data class HuntingSpotView(
    val id: String,
    val name: String,
    val recommendedLevel: Int,
    val minimumCycleSeconds: Int,
    val description: String,
    val previewCostGold: Int,
    val available: Boolean,
    val blockedReasons: List<String>
)

data class HuntingDurationOptionView(
    val durationSeconds: Int,
    val label: String,
    val available: Boolean,
    val blockedReasons: List<String>
)

data class HuntingPrepareResult(
    val ready: Boolean,
    val messages: List<String>,
    val timedActionView: ProductionTimedActionView? = null
)

data class HuntingMutationResult(
    val state: rpg.model.GameState,
    val messages: List<String>
)
