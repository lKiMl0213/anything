package rpg.application.city

import rpg.achievement.AchievementTracker
import rpg.model.GameState

class CityCommandService(
    private val achievementTracker: AchievementTracker,
    private val support: CityRulesSupport
) {
    fun rest(state: GameState): CityMutationResult {
        val pricing = support.tavernPricing(state.player, state.itemInstances)
        val shouldCharge = support.hasRecoverableResources(state)
        if (shouldCharge && state.player.gold < pricing.restCost) {
            return CityMutationResult(state, listOf("Ouro insuficiente."))
        }
        var updatedState = support.applyRest(state)
        val goldUpdate = if (shouldCharge && pricing.restCost > 0) {
            achievementTracker.onGoldSpent(updatedState.player, pricing.restCost.toLong())
        } else {
            rpg.achievement.AchievementUpdate(updatedState.player)
        }
        if (shouldCharge && pricing.restCost > 0) {
            updatedState = updatedState.copy(player = goldUpdate.player)
        }
        return CityMutationResult(
            state = updatedState,
            messages = listOf(
                if (shouldCharge) {
                    "Voce descansou na taverna. Recuperacao parcial aplicada."
                } else {
                    "Voce descansou, mas ja estava com HP/MP cheios. Sem custo."
                }
            ) + support.achievementNotificationLines(goldUpdate.unlockedTiers)
        )
    }

    fun sleep(state: GameState): CityMutationResult {
        val pricing = support.tavernPricing(state.player, state.itemInstances)
        val shouldCharge = support.hasRecoverableResources(state)
        if (shouldCharge && state.player.gold < pricing.sleepCost) {
            return CityMutationResult(state, listOf("Ouro insuficiente."))
        }
        var updatedState = support.applySleep(state)
        val goldUpdate = if (shouldCharge && pricing.sleepCost > 0) {
            achievementTracker.onGoldSpent(updatedState.player, pricing.sleepCost.toLong())
        } else {
            rpg.achievement.AchievementUpdate(updatedState.player)
        }
        if (shouldCharge && pricing.sleepCost > 0) {
            updatedState = updatedState.copy(player = goldUpdate.player)
        }
        val sleepUpdate = if (shouldCharge) {
            achievementTracker.onFullRestSleep(updatedState.player)
        } else {
            rpg.achievement.AchievementUpdate(updatedState.player)
        }
        if (shouldCharge) {
            updatedState = updatedState.copy(player = sleepUpdate.player)
        }
        return CityMutationResult(
            state = updatedState,
            messages = listOf(
                if (shouldCharge) {
                    "Voce dormiu e acordou renovado."
                } else {
                    "Voce dormiu, mas ja estava com HP/MP cheios. Sem custo."
                }
            ) +
                support.achievementNotificationLines(goldUpdate.unlockedTiers) +
                support.achievementNotificationLines(sleepUpdate.unlockedTiers)
        )
    }

    fun purifyOne(state: GameState): CityMutationResult {
        val pricing = support.tavernPricing(state.player, state.itemInstances)
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
        val pricing = support.tavernPricing(state.player, state.itemInstances)
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
