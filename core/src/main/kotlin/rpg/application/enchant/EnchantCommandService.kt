package rpg.application.enchant

import rpg.achievement.AchievementTracker
import rpg.achievement.AchievementCounterKeys
import rpg.application.production.ProductionTimedActionView
import rpg.application.support.OutOfCombatTimeService
import rpg.enchant.EnchantAttemptRequest
import rpg.engine.GameEngine
import rpg.model.GameState

class EnchantCommandService(
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker,
    private val timeService: OutOfCombatTimeService = OutOfCombatTimeService(engine)
) {
    fun maxEnhancementRunesPerAttempt(): Int = engine.enchantService.maxEnhancementRunesPerAttempt()

    fun prepareAttempt(
        state: GameState,
        itemId: String,
        enhancementRunes: Int,
        useProtectionRune: Boolean
    ): EnchantPrepareResult {
        val preview = engine.enchantService.preview(
            player = state.player,
            itemInstances = state.itemInstances,
            request = EnchantAttemptRequest(
                itemId = itemId,
                enhancementRunes = enhancementRunes,
                useProtectionRune = useProtectionRune
            )
        )
        if (!preview.available) {
            return EnchantPrepareResult(
                ready = false,
                messages = if (preview.blockedReasons.isEmpty()) {
                    listOf("Nao foi possivel preparar o encantamento.")
                } else {
                    preview.blockedReasons
                }
            )
        }
        return EnchantPrepareResult(
            ready = true,
            messages = emptyList(),
            timedActionView = ProductionTimedActionView(
                categoryLabel = "Encantamento",
                actionLabel = "Encantando ${preview.itemName}...",
                skillLabel = "enchanting",
                skillLevel = engine.skillSystem.snapshot(state.player, rpg.model.SkillType.ENCHANTING).level,
                durationSeconds = preview.durationSeconds
            )
        )
    }

    fun attempt(
        state: GameState,
        itemId: String,
        enhancementRunes: Int,
        useProtectionRune: Boolean
    ): EnchantMutationResult {
        val result = engine.enchantService.enchant(
            player = state.player,
            itemInstances = state.itemInstances,
            request = EnchantAttemptRequest(
                itemId = itemId,
                enhancementRunes = enhancementRunes,
                useProtectionRune = useProtectionRune
            )
        )

        var updatedPlayer = result.player
        if (result.goldSpent > 0) {
            updatedPlayer = achievementTracker.onGoldSpent(updatedPlayer, result.goldSpent.toLong()).player
        }
        updatedPlayer = achievementTracker.onCustomCounterIncrement(
            updatedPlayer,
            AchievementCounterKeys.Enchant.NAMESPACE,
            AchievementCounterKeys.Enchant.ATTEMPTS,
            amount = 1
        ).player
        if (result.success) {
            updatedPlayer = achievementTracker.onCustomCounterIncrement(
                updatedPlayer,
                AchievementCounterKeys.Enchant.NAMESPACE,
                AchievementCounterKeys.Enchant.SUCCESSES,
                amount = 1
            ).player
            if (result.newEnchantLevel >= 10) {
                updatedPlayer = achievementTracker.onCustomCounterIncrement(
                    updatedPlayer,
                    AchievementCounterKeys.Enchant.NAMESPACE,
                    AchievementCounterKeys.Enchant.ITEMS_PLUS_10_OR_MORE,
                    amount = 1
                ).player
            }
        }
        val spentMinutes = (result.preview?.durationSeconds ?: 0.0) / 60.0
        val advance = timeService.advance(updatedPlayer, result.itemInstances, spentMinutes)
        updatedPlayer = advance.player
        val updatedState = state.copy(
            player = updatedPlayer,
            itemInstances = result.itemInstances,
            worldTimeMinutes = state.worldTimeMinutes + spentMinutes.coerceAtLeast(0.0),
            lastClockSyncEpochMs = System.currentTimeMillis()
        )
        val lines = mutableListOf<String>()
        lines += result.message
        if (spentMinutes > 0.0) {
            lines += "Tempo gasto no encantamento: ${format(spentMinutes)} min."
        }
        lines += advance.messages
        result.skillSnapshot?.let { snapshot ->
            lines += "Skill ${snapshot.skill.name.lowercase()}: +${format(result.gainedXp)} XP (lvl ${snapshot.level})"
        }
        return EnchantMutationResult(
            state = updatedState,
            messages = lines,
            selectedItemId = result.targetItemId
        )
    }

    private fun format(value: Double): String = "%.1f".format(value)
}
