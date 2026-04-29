package rpg.globalboss.services

import rpg.model.GameState

enum class GlobalBossAttemptType {
    FREE,
    PAID
}

data class GlobalBossAttemptResult(
    val success: Boolean,
    val state: GameState,
    val messages: List<String>,
    val attemptType: GlobalBossAttemptType? = null
)

data class GlobalBossProgressResult(
    val success: Boolean,
    val state: GameState,
    val messages: List<String>,
    val runPoints: Long = 0L,
    val runDamage: Double = 0.0
)
