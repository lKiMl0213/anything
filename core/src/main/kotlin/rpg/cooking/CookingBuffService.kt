package rpg.cooking

import kotlin.math.max
import rpg.model.Bonuses
import rpg.model.CraftDiscipline
import rpg.model.CraftRecipeDef
import rpg.model.DerivedStats
import rpg.model.PlayerState

data class CookingBuffApplication(
    val player: PlayerState,
    val applied: Boolean,
    val replacedPrevious: Boolean,
    val message: String? = null
)

data class CookingBuffDecayResult(
    val player: PlayerState,
    val expiredBuffName: String? = null
)

class CookingBuffService(
    recipes: Map<String, CraftRecipeDef>,
    private val config: CookingBuffConfig = CookingBuffConfig()
) {
    private val buffsByItemId = config.buffs.associateBy { it.itemId }
    private val recipeProfilesByOutput = recipes.values
        .filter { it.enabled && it.discipline == CraftDiscipline.COOKING }
        .groupBy { it.outputItemId }
        .mapValues { (_, items) ->
            items.maxByOrNull { recipe ->
                recipe.difficulty * max(1, recipe.ingredients.size)
            }
        }

    fun hasBuffForItem(itemId: String): Boolean = buffsByItemId.containsKey(itemId)

    fun applyFromItem(player: PlayerState, itemId: String): CookingBuffApplication {
        val buff = buffsByItemId[itemId]
            ?: return CookingBuffApplication(player = player, applied = false, replacedPrevious = false)
        val profile = recipeProfilesByOutput[itemId]
        val multipliers = multipliersFor(profile)
        val duration = (buff.baseDurationMinutes * multipliers.durationMultiplier)
            .coerceAtLeast(config.defaultDurationMinutes.coerceAtLeast(1.0))
        val value = (buff.baseValue * multipliers.powerMultiplier).coerceAtLeast(0.0)
        val appliedBonuses = bonusesFor(buff.type, value)
        val replaced = player.foodBuffRemainingMinutes > 0.0 && player.foodBuffName.isNotBlank()
        val updated = player.copy(
            foodBuffId = buff.id,
            foodBuffName = buff.name,
            foodBuffRemainingMinutes = duration,
            foodBuffBonuses = appliedBonuses,
            foodBuffTaskId = buff.taskId?.trim()?.lowercase(),
            foodBuffTaskEfficiencyPct = if (buff.type == CookingBuffType.TASK_EFFICIENCY) value else 0.0
        )
        val prefix = if (replaced) "Buff culinario substituido" else "Buff culinario ativado"
        val message = when (buff.type) {
            CookingBuffType.HP_REGEN -> "$prefix: ${buff.name} (+${format(value)} regen HP por ${format(duration)} min)."
            CookingBuffType.MP_REGEN -> "$prefix: ${buff.name} (+${format(value)} regen MP por ${format(duration)} min)."
            CookingBuffType.DAMAGE -> "$prefix: ${buff.name} (+${format(value)} dano leve por ${format(duration)} min)."
            CookingBuffType.DEFENSE -> "$prefix: ${buff.name} (+${format(value)} defesa leve por ${format(duration)} min)."
            CookingBuffType.TASK_EFFICIENCY -> {
                val task = buff.taskId?.ifBlank { "tarefa" } ?: "tarefa"
                "$prefix: ${buff.name} (+${format(value)}% eficiencia em $task por ${format(duration)} min)."
            }
        }
        return CookingBuffApplication(
            player = updated,
            applied = true,
            replacedPrevious = replaced,
            message = message
        )
    }

    fun taskEfficiencyPct(player: PlayerState, taskId: String): Double {
        if (player.foodBuffRemainingMinutes <= 0.0) return 0.0
        val activeTask = player.foodBuffTaskId?.trim()?.lowercase() ?: return 0.0
        if (activeTask != taskId.trim().lowercase()) return 0.0
        return player.foodBuffTaskEfficiencyPct.coerceIn(0.0, 80.0)
    }

    fun decay(player: PlayerState, elapsedMinutes: Double): CookingBuffDecayResult {
        if (elapsedMinutes <= 0.0 || player.foodBuffRemainingMinutes <= 0.0) {
            return CookingBuffDecayResult(player)
        }
        val remaining = (player.foodBuffRemainingMinutes - elapsedMinutes).coerceAtLeast(0.0)
        if (remaining > 0.0) {
            return CookingBuffDecayResult(player.copy(foodBuffRemainingMinutes = remaining))
        }
        val expired = player.foodBuffName.takeIf { it.isNotBlank() }
        return CookingBuffDecayResult(
            player = player.copy(
                foodBuffId = null,
                foodBuffName = "",
                foodBuffRemainingMinutes = 0.0,
                foodBuffBonuses = Bonuses(),
                foodBuffTaskId = null,
                foodBuffTaskEfficiencyPct = 0.0
            ),
            expiredBuffName = expired
        )
    }

    private fun multipliersFor(recipe: CraftRecipeDef?): MultiplierProfile {
        val difficulty = recipe?.difficulty?.coerceAtLeast(1.0) ?: 1.0
        val ingredientCount = recipe?.ingredients?.size?.coerceAtLeast(1) ?: 1
        val power = (
            1.0 +
                (difficulty - 1.0) * config.powerScalePerDifficulty +
                (ingredientCount - 1) * config.powerScalePerIngredient
            ).coerceIn(1.0, config.maxPowerMultiplier.coerceAtLeast(1.0))
        val duration = (
            1.0 +
                (difficulty - 1.0) * config.durationScalePerDifficulty +
                (ingredientCount - 1) * config.durationScalePerIngredient
            ).coerceIn(1.0, config.maxDurationMultiplier.coerceAtLeast(1.0))
        return MultiplierProfile(powerMultiplier = power, durationMultiplier = duration)
    }

    private fun bonusesFor(type: CookingBuffType, value: Double): Bonuses {
        return when (type) {
            CookingBuffType.HP_REGEN -> Bonuses(derivedAdd = DerivedStats(hpRegen = value))
            CookingBuffType.MP_REGEN -> Bonuses(derivedAdd = DerivedStats(mpRegen = value))
            CookingBuffType.DAMAGE -> Bonuses(
                derivedAdd = DerivedStats(
                    damagePhysical = value,
                    damageMagic = value
                )
            )

            CookingBuffType.DEFENSE -> Bonuses(
                derivedAdd = DerivedStats(
                    defPhysical = value,
                    defMagic = value
                )
            )

            CookingBuffType.TASK_EFFICIENCY -> Bonuses()
        }
    }

    private fun format(value: Double): String = "%.1f".format(value)

    private data class MultiplierProfile(
        val powerMultiplier: Double,
        val durationMultiplier: Double
    )
}
