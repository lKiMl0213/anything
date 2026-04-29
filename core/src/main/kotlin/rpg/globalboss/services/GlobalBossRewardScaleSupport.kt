package rpg.globalboss.services

import kotlin.math.roundToInt
import rpg.globalboss.config.GlobalBossRewardDef

internal object GlobalBossRewardScaleSupport {
    fun scale(reward: GlobalBossRewardDef, rewardMultiplierPct: Double): GlobalBossRewardDef {
        val multiplier = (rewardMultiplierPct / 100.0).coerceAtLeast(0.0)
        if (multiplier == 1.0) return reward
        return GlobalBossRewardDef(
            xp = (reward.xp * multiplier).roundToInt().coerceAtLeast(0),
            gold = (reward.gold * multiplier).roundToInt().coerceAtLeast(0),
            questCurrency = (reward.questCurrency * multiplier).roundToInt().coerceAtLeast(0),
            premiumCash = (reward.premiumCash * multiplier).roundToInt().coerceAtLeast(0)
        )
    }
}
