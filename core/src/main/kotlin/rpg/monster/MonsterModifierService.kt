package rpg.monster

import kotlin.random.Random
import rpg.io.DataRepository
import rpg.model.Attributes
import rpg.model.WeightedContentDefinition
import rpg.model.WeightedContentPicker

internal class MonsterModifierService(
    private val repo: DataRepository,
    private val rng: Random
) {
    fun rollModifiers(count: Int): List<MonsterModifier> {
        if (count <= 0) return emptyList()
        val pool = repo.monsterModifiers.values.toMutableList()
        val result = mutableListOf<MonsterModifier>()
        repeat(count) {
            if (pool.isEmpty()) return@repeat
            val weighted = pool.map { mod ->
                WeightedContentDefinition(mod, mod.weight.toDouble().coerceAtLeast(0.1))
            }
            val chosen = WeightedContentPicker.pick(weighted, rng) ?: return@repeat
            result += chosen
            pool.remove(chosen)
        }
        return result
    }

    fun applyModifiersToAttributes(
        attrs: Attributes,
        modifiers: List<MonsterModifier>
    ): Attributes {
        var result = attrs
        for (modifier in modifiers) {
            if (modifier.attributeMultiplier != 1.0) {
                result = result.scale(modifier.attributeMultiplier)
            }
        }
        return result
    }
}
