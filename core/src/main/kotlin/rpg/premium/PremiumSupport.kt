package rpg.premium

import rpg.model.PlayerState

object PremiumSupport {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val WELCOME_BACK_WINDOW_MS = 30L * DAY_MS

    const val PREMIUM_GOLD_BONUS_PCT = 15
    const val PREMIUM_SKILL_XP_BONUS_PCT = 20
    const val PREMIUM_COMBAT_XP_BONUS_PCT = 20
    const val PREMIUM_SHOP_DISCOUNT_PCT = 10
    const val PREMIUM_PRODUCTION_COST_REDUCTION_PCT = 10
    const val PREMIUM_PRODUCTION_SPEED_BONUS_PCT = 10
    const val PREMIUM_QUEST_EXTRA_COUNT = 10
    const val PREMIUM_QUEST_EXTRA_REROLLS = 10
    const val PREMIUM_GLOBAL_BOSS_EXTRA_RUNS = 3

    fun isPremiumActive(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return player.premiumPermanent || player.premiumExpiresAtEpochMs > nowMillis
    }

    fun skillXpMultiplier(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Double {
        return if (isPremiumActive(player, nowMillis)) 1.0 + (PREMIUM_SKILL_XP_BONUS_PCT / 100.0) else 1.0
    }

    fun combatXpMultiplier(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Double {
        return if (isPremiumActive(player, nowMillis)) 1.0 + (PREMIUM_COMBAT_XP_BONUS_PCT / 100.0) else 1.0
    }

    fun goldMultiplier(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Double {
        return if (isPremiumActive(player, nowMillis)) 1.0 + (PREMIUM_GOLD_BONUS_PCT / 100.0) else 1.0
    }

    fun shopDiscountPct(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Double {
        return if (isPremiumActive(player, nowMillis)) PREMIUM_SHOP_DISCOUNT_PCT.toDouble() else 0.0
    }

    fun productionCostReductionPct(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Double {
        return if (isPremiumActive(player, nowMillis)) PREMIUM_PRODUCTION_COST_REDUCTION_PCT.toDouble() else 0.0
    }

    fun productionDurationMultiplier(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Double {
        return if (isPremiumActive(player, nowMillis)) {
            (1.0 - PREMIUM_PRODUCTION_SPEED_BONUS_PCT / 100.0).coerceIn(0.1, 1.0)
        } else {
            1.0
        }
    }

    fun questExtraCount(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Int {
        return if (isPremiumActive(player, nowMillis)) PREMIUM_QUEST_EXTRA_COUNT else 0
    }

    fun questExtraRerolls(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Int {
        return if (isPremiumActive(player, nowMillis)) PREMIUM_QUEST_EXTRA_REROLLS else 0
    }

    fun globalBossExtraRuns(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Int {
        return if (isPremiumActive(player, nowMillis)) PREMIUM_GLOBAL_BOSS_EXTRA_RUNS else 0
    }

    fun applyPremiumDuration(
        player: PlayerState,
        days: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): PlayerState {
        val safeDays = days.coerceAtLeast(1)
        val anchor = maxOf(player.premiumExpiresAtEpochMs, nowMillis)
        return player.copy(premiumExpiresAtEpochMs = anchor + safeDays * DAY_MS)
    }

    fun applyPremiumPermanent(player: PlayerState): PlayerState {
        return player.copy(premiumPermanent = true)
    }

    fun cashWelcomeBackEligible(player: PlayerState, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!player.cashFirstPurchaseBonusConsumed) return false
        val reference = maxOf(player.lastActiveEpochMs, player.lastCashPurchaseEpochMs)
        if (reference <= 0L) return false
        return nowMillis - reference >= WELCOME_BACK_WINDOW_MS
    }
}
