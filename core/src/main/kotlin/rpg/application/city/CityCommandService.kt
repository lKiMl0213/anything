package rpg.application.city

import rpg.achievement.AchievementTracker
import rpg.model.GameState

class CityCommandService(
    private val achievementTracker: AchievementTracker,
    private val support: CityRulesSupport
) {
    fun rest(state: GameState): CityMutationResult {
        val pricing = support.tavernPricing(state.player)
        if (state.player.gold < pricing.restCost) {
            return CityMutationResult(state, listOf("Ouro insuficiente."))
        }
        var updatedState = support.applyRest(state)
        val goldUpdate = achievementTracker.onGoldSpent(updatedState.player, pricing.restCost.toLong())
        updatedState = updatedState.copy(player = goldUpdate.player)
        return CityMutationResult(
            state = updatedState,
            messages = listOf("Voce descansou na taverna.") + support.achievementNotificationLines(goldUpdate.unlockedTiers)
        )
    }

    fun sleep(state: GameState): CityMutationResult {
        val pricing = support.tavernPricing(state.player)
        if (state.player.gold < pricing.sleepCost) {
            return CityMutationResult(state, listOf("Ouro insuficiente."))
        }
        var updatedState = support.applySleep(state)
        val goldUpdate = achievementTracker.onGoldSpent(updatedState.player, pricing.sleepCost.toLong())
        updatedState = updatedState.copy(player = goldUpdate.player)
        val sleepUpdate = achievementTracker.onFullRestSleep(updatedState.player)
        updatedState = updatedState.copy(player = sleepUpdate.player)
        return CityMutationResult(
            state = updatedState,
            messages = listOf("Voce dormiu e acordou renovado.") +
                support.achievementNotificationLines(goldUpdate.unlockedTiers) +
                support.achievementNotificationLines(sleepUpdate.unlockedTiers)
        )
    }

    fun purifyOne(state: GameState): CityMutationResult {
        val pricing = support.tavernPricing(state.player)
        if (state.player.deathDebuffStacks <= 0) {
            return CityMutationResult(state, listOf("Nenhum debuff ativo."))
        }
        if (state.player.gold < pricing.purifyOneCost) {
            return CityMutationResult(state, listOf("Ouro insuficiente."))
        }
        var updatedState = support.applyPurifyOne(state)
        val goldUpdate = achievementTracker.onGoldSpent(updatedState.player, pricing.purifyOneCost.toLong())
        updatedState = updatedState.copy(player = goldUpdate.player)
        return CityMutationResult(
            state = updatedState,
            messages = listOf("Um stack do debuff foi removido.") + support.achievementNotificationLines(goldUpdate.unlockedTiers)
        )
    }

    fun purifyAll(state: GameState): CityMutationResult {
        val pricing = support.tavernPricing(state.player)
        if (state.player.deathDebuffStacks <= 0) {
            return CityMutationResult(state, listOf("Nenhum debuff ativo."))
        }
        if (state.player.gold < pricing.purifyAllCost) {
            return CityMutationResult(state, listOf("Ouro insuficiente."))
        }
        var updatedState = support.applyPurifyAll(state)
        val goldUpdate = achievementTracker.onGoldSpent(updatedState.player, pricing.purifyAllCost.toLong())
        updatedState = updatedState.copy(player = goldUpdate.player)
        return CityMutationResult(
            state = updatedState,
            messages = listOf("Todo o debuff foi removido.") + support.achievementNotificationLines(goldUpdate.unlockedTiers)
        )
    }
}
