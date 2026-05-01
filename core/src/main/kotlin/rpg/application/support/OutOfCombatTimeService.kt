package rpg.application.support

import rpg.engine.GameEngine
import rpg.model.ItemInstance
import rpg.model.PlayerState

data class OutOfCombatAdvanceResult(
    val player: PlayerState,
    val messages: List<String> = emptyList()
)

class OutOfCombatTimeService(
    private val engine: GameEngine
) {
    fun advance(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        minutes: Double
    ): OutOfCombatAdvanceResult {
        if (minutes <= 0.0) return OutOfCombatAdvanceResult(player)

        val stats = engine.computePlayerStats(player, itemInstances)
        val newHp = (player.currentHp + stats.derived.hpRegen * minutes).coerceAtMost(stats.derived.hpMax)
        val newMp = (player.currentMp + stats.derived.mpRegen * minutes).coerceAtMost(stats.derived.mpMax)
        var updated = player.copy(currentHp = newHp, currentMp = newMp)
        val messages = mutableListOf<String>()

        val foodDecay = engine.cookingBuffService.decay(updated, minutes)
        updated = foodDecay.player
        foodDecay.expiredBuffName?.let { name ->
            messages += "Buff culinario expirou: $name."
        }

        if (updated.deathDebuffMinutes > 0.0) {
            val remaining = (updated.deathDebuffMinutes - minutes).coerceAtLeast(0.0)
            updated = if (remaining <= 0.0) {
                updated.copy(
                    deathDebuffMinutes = 0.0,
                    deathDebuffStacks = 0,
                    deathXpPenaltyMinutes = 0.0,
                    deathXpPenaltyPct = 0.0
                )
            } else {
                updated.copy(
                    deathDebuffMinutes = remaining,
                    deathXpPenaltyMinutes = remaining
                )
            }
        } else if (updated.deathXpPenaltyMinutes > 0.0) {
            val remaining = (updated.deathXpPenaltyMinutes - minutes).coerceAtLeast(0.0)
            updated = if (remaining <= 0.0) {
                updated.copy(deathXpPenaltyMinutes = 0.0, deathXpPenaltyPct = 0.0)
            } else {
                updated.copy(deathXpPenaltyMinutes = remaining)
            }
        }

        return OutOfCombatAdvanceResult(player = updated, messages = messages)
    }
}
