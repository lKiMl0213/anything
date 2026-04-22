package rpg.events

import java.util.UUID
import kotlin.random.Random

object EventEngine {
    fun generateEvent(source: EventSource, context: EventContext): EventDefinition {
        val rarity = rollRarity(context)
        val eligible = EventPool.recipesFor(source).filter { isEligible(it, context) }
        val candidates = eligible.filter { it.rarity == rarity }
        val selected = if (candidates.isEmpty()) {
            pickWeighted(eligible.ifEmpty { EventPool.recipesFor(source) }, context.rng)
        } else {
            pickWeighted(candidates, context.rng)
        }
        val builder = EventBuilder()
            .source(source)
            .rarity(rarity)
            .addEffects(selected.effects)

        val extraEffects = rollCompositeEffects(rarity, context)
        if (extraEffects.isNotEmpty()) {
            builder.addEffects(extraEffects)
        }

        return builder.build(context.rng)
    }

    private fun rollRarity(context: EventContext): Rarity {
        val base = Rarity.values().associateWith { it.weight }.toMutableMap()
        if (context.depth < 3) {
            base[Rarity.EPIC] = 0
            base[Rarity.LEGENDARY] = 0
        } else if (context.depth < 6) {
            base[Rarity.LEGENDARY] = 0
        }
        if (context.playerLevel < 5) {
            base[Rarity.RARE] = (base[Rarity.RARE] ?: 0) / 2
        }
        val total = base.values.sum().coerceAtLeast(1)
        val roll = context.rng.nextInt(total)
        var cumulative = 0
        for (rarity in Rarity.values()) {
            cumulative += base[rarity] ?: 0
            if (roll < cumulative) return rarity
        }
        return Rarity.COMMON
    }

    private fun isEligible(recipe: EventRecipe, context: EventContext): Boolean {
        return context.depth in recipe.minDepth..recipe.maxDepth &&
            context.playerLevel in recipe.minLevel..recipe.maxLevel
    }

    private fun rollCompositeEffects(rarity: Rarity, context: EventContext): List<EventEffect> {
        val chance = when (rarity) {
            Rarity.COMMON -> 0.15
            Rarity.RARE -> 0.25
            Rarity.EPIC -> 0.35
            Rarity.LEGENDARY -> 0.5
        }
        if (context.rng.nextDouble() > chance) return emptyList()
        val pool = EventPool.compositeFor(rarity)
        if (pool.isEmpty()) return emptyList()
        val selected = pickWeighted(pool, context.rng)
        return selected.effects
    }
}

class EventBuilder {
    private var source: EventSource = EventSource.LIQUID
    private var rarity: Rarity = Rarity.COMMON
    private val effects = mutableListOf<EventEffect>()

    fun source(value: EventSource) = apply { source = value }

    fun rarity(r: Rarity) = apply { rarity = r }

    fun addEffect(effect: EventEffect) = apply { effects.add(effect) }

    fun addEffects(items: List<EventEffect>) = apply { effects.addAll(items) }

    fun build(rng: Random): EventDefinition {
        val description = EventTextGenerator.generate(source, rarity, effects, rng)
        return EventDefinition(
            id = UUID.randomUUID().toString(),
            rarity = rarity,
            description = description,
            effects = effects.toList()
        )
    }
}

private fun pickWeighted(recipes: List<EventRecipe>, rng: Random): EventRecipe {
    val total = recipes.sumOf { it.weight }
    var roll = rng.nextInt(total)
    for (recipe in recipes) {
        roll -= recipe.weight
        if (roll < 0) return recipe
    }
    return recipes.first()
}
