package rpg.application.city

import kotlin.math.max
import kotlin.math.min
import rpg.achievement.AchievementTierUnlockedNotification
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.PlayerState

class CityRulesSupport(
    private val engine: GameEngine,
    private val tavernRestHealPct: Double = 0.25,
    private val deathDebuffBaseMinutes: Double = 30.0,
    private val deathDebuffExtraMinutes: Double = 15.0,
    private val deathXpPenaltyPct: Double = 15.0
) {
    data class TavernPricing(
        val restCost: Int,
        val sleepCost: Int,
        val purifyOneCost: Int,
        val purifyAllCost: Int
    )

    fun tavernPricing(player: PlayerState): TavernPricing {
        val stacks = player.deathDebuffStacks
        return TavernPricing(
            restCost = max(10, 12 + player.level * 2),
            sleepCost = max(25, 30 + player.level * 4),
            purifyOneCost = if (stacks > 0) 30 + (stacks - 1) * 15 else 0,
            purifyAllCost = if (stacks > 0) 80 + (stacks - 1) * 40 else 0
        )
    }

    fun tavernView(state: GameState): TavernViewData {
        val pricing = tavernPricing(state.player)
        val stats = engine.computePlayerStats(state.player, state.itemInstances)
        return TavernViewData(
            restCost = pricing.restCost,
            sleepCost = pricing.sleepCost,
            purifyOneCost = pricing.purifyOneCost,
            purifyAllCost = pricing.purifyAllCost,
            debuffStacks = state.player.deathDebuffStacks,
            canPurify = state.player.deathDebuffStacks > 0,
            detailLines = listOf(
                "Gold atual: ${state.player.gold}",
                "Descansar recupera 25% de HP/MP.",
                "Dormir restaura HP/MP por completo.",
                "Debuff atual: ${state.player.deathDebuffStacks} stack(s) | ${formatMinutes(state.player.deathDebuffMinutes)} min restantes",
                "HP atual: ${formatValue(state.player.currentHp)}/${formatValue(stats.derived.hpMax)}",
                "MP atual: ${formatValue(state.player.currentMp)}/${formatValue(stats.derived.mpMax)}"
            )
        )
    }

    fun applyRest(state: GameState): GameState {
        val pricing = tavernPricing(state.player)
        val stats = engine.computePlayerStats(state.player, state.itemInstances)
        val player = state.player.copy(
            gold = state.player.gold - pricing.restCost,
            currentHp = min(stats.derived.hpMax, state.player.currentHp + stats.derived.hpMax * tavernRestHealPct),
            currentMp = min(stats.derived.mpMax, state.player.currentMp + stats.derived.mpMax * tavernRestHealPct)
        )
        return state.copy(player = player)
    }

    fun applySleep(state: GameState): GameState {
        val pricing = tavernPricing(state.player)
        val stats = engine.computePlayerStats(state.player, state.itemInstances)
        val player = state.player.copy(
            gold = state.player.gold - pricing.sleepCost,
            currentHp = stats.derived.hpMax,
            currentMp = stats.derived.mpMax
        )
        return state.copy(player = player)
    }

    fun applyPurifyOne(state: GameState): GameState {
        val pricing = tavernPricing(state.player)
        val stacks = state.player.deathDebuffStacks
        val newStacks = max(0, stacks - 1)
        val capMinutes = if (newStacks == 0) 0.0 else deathDebuffBaseMinutes + (newStacks - 1) * deathDebuffExtraMinutes
        val newMinutes = if (newStacks == 0) 0.0 else min(state.player.deathDebuffMinutes, capMinutes)
        val player = state.player.copy(
            gold = state.player.gold - pricing.purifyOneCost,
            deathDebuffStacks = newStacks,
            deathDebuffMinutes = newMinutes,
            deathXpPenaltyMinutes = newMinutes,
            deathXpPenaltyPct = if (newStacks > 0) deathXpPenaltyPct else 0.0
        )
        return state.copy(player = player)
    }

    fun applyPurifyAll(state: GameState): GameState {
        val pricing = tavernPricing(state.player)
        val player = state.player.copy(
            gold = state.player.gold - pricing.purifyAllCost,
            deathDebuffStacks = 0,
            deathDebuffMinutes = 0.0,
            deathXpPenaltyMinutes = 0.0,
            deathXpPenaltyPct = 0.0
        )
        return state.copy(player = player)
    }

    fun achievementNotificationLines(notifications: List<AchievementTierUnlockedNotification>): List<String> {
        if (notifications.isEmpty()) return emptyList()
        return notifications.flatMap { notification ->
            listOf(
                "(!) Conquista concluida (!)",
                notification.displayName,
                notification.displayDescription,
                "Recompensa disponivel: ${notification.rewardGold} ouro"
            )
        }
    }

    private fun formatMinutes(value: Double): String = "%.1f".format(value.coerceAtLeast(0.0))

    private fun formatValue(value: Double): String = "%.0f".format(value)
}
