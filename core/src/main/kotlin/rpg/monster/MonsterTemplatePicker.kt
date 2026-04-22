package rpg.monster

import kotlin.random.Random
import rpg.io.DataRepository
import rpg.model.BiomeDef
import rpg.model.MapTierDef
import rpg.model.MonsterArchetypeDef
import rpg.model.WeightedContentDefinition
import rpg.model.WeightedContentPicker

internal class MonsterTemplatePicker(
    private val repo: DataRepository,
    private val rng: Random
) {
    fun pickTemplate(tier: MapTierDef, biome: BiomeDef?): MonsterArchetypeDef {
        val templateIds = tier.allowedMonsterTemplates
        val pool = if (templateIds.isNotEmpty()) {
            templateIds.mapNotNull { repo.monsterArchetypes[it] }
        } else {
            repo.monsterArchetypes.values.toList()
        }
        if (pool.isEmpty()) error("Nenhum template de monstro encontrado.")

        val weighted = pool.map { template ->
            val weight = if (biome == null) 1.0 else {
                template.tags.fold(1.0) { acc, tag ->
                    acc * (biome.monsterTagWeights[tag] ?: 1.0)
                }
            }
            WeightedContentDefinition(content = template, weight = weight)
        }
        return WeightedContentPicker.pick(weighted, rng) ?: pool.first()
    }

    fun resolveVariantName(archetypeVariant: String, modifiers: List<MonsterModifier>): String {
        if (archetypeVariant.isNotBlank()) return archetypeVariant
        val preferred = modifiers.firstOrNull()?.name?.trim().orEmpty()
        return preferred
    }

    fun buildShortDisplayName(base: String, variant: String): String {
        if (variant.isBlank()) return base
        val normalizedBase = base.trim()
        val normalizedVariant = variant.trim()
        if (normalizedBase.lowercase().contains(normalizedVariant.lowercase())) {
            return normalizedBase
        }
        return "$normalizedBase $normalizedVariant"
    }

    fun normalizeBaseType(archetypeId: String, fallbackName: String): String {
        val fromId = archetypeId
            .substringBefore('_')
            .trim()
            .lowercase()
            .takeIf { it.isNotBlank() }
        if (fromId != null) return fromId
        return fallbackName.trim().lowercase().ifBlank { "monster" }
    }
}
