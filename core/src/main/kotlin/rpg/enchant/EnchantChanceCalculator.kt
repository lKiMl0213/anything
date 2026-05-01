package rpg.enchant

data class EnchantChanceView(
    val baseSuccessChancePct: Double,
    val finalSuccessChancePct: Double,
    val finalBreakChancePct: Double,
    val totalRuneBonusMultiplier: Double,
    val skillSuccessBonusPct: Double,
    val skillBreakReductionPct: Double,
    val durationSeconds: Double
)

class EnchantChanceCalculator(
    private val config: EnchantConfig
) {
    fun calculate(
        currentEnchantLevel: Int,
        enhancementRunes: Int,
        useProtectionRune: Boolean,
        enchantSkillLevel: Int,
        totalRuneBonusPctOverride: Double? = null
    ): EnchantChanceView {
        val level = currentEnchantLevel.coerceAtLeast(0)
        val runeCount = enhancementRunes.coerceAtLeast(0)
        val skillLevel = enchantSkillLevel.coerceAtLeast(1)

        val baseChance = config.baseChanceForLevel(level)
        val runeBonusPct = totalRuneBonusPctOverride?.coerceAtLeast(0.0)
            ?: (runeCount * config.enhancementRuneBonusPctPerUnit.coerceAtLeast(0.0))
        val totalRuneBonusMultiplier = 1.0 + (runeBonusPct / 100.0)
        val chanceAfterRunes = baseChance * totalRuneBonusMultiplier

        val skillSuccessBonusPct = ((skillLevel - 1) * config.successBonusPerSkillLevelPct)
            .coerceIn(0.0, config.maxSuccessBonusPct)
        val finalSuccessChance = (chanceAfterRunes * (1.0 + skillSuccessBonusPct / 100.0))
            .coerceIn(0.0, 100.0)

        val baseBreakChance = config.breakChanceForLevel(level)
        val skillBreakReductionPct = ((skillLevel - 1) * config.breakReductionPerSkillLevelPct)
            .coerceIn(0.0, config.maxBreakReductionPct)
        val finalBreakChance = if (useProtectionRune) {
            0.0
        } else {
            (baseBreakChance * (1.0 - skillBreakReductionPct / 100.0)).coerceIn(0.0, 100.0)
        }

        val reductionPct = ((skillLevel - 1) * config.durationReductionPerSkillLevelPct)
            .coerceIn(0.0, config.maxDurationReductionPct)
        val durationSeconds = (config.baseDurationSeconds * (1.0 - reductionPct / 100.0))
            .coerceAtLeast(config.minDurationSeconds)

        return EnchantChanceView(
            baseSuccessChancePct = baseChance,
            finalSuccessChancePct = finalSuccessChance,
            finalBreakChancePct = finalBreakChance,
            totalRuneBonusMultiplier = totalRuneBonusMultiplier,
            skillSuccessBonusPct = skillSuccessBonusPct,
            skillBreakReductionPct = skillBreakReductionPct,
            durationSeconds = durationSeconds
        )
    }
}
