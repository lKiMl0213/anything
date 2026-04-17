package rpg.model

import kotlin.math.max
import kotlin.random.Random

data class BaseDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val tags: Set<String> = emptySet(),
    val tier: Int? = null,
    val weight: Double = 1.0
)

data class WeightedContentDefinition<T>(
    val content: T,
    val weight: Double
)

object WeightedContentPicker {
    fun <T> pick(
        options: List<WeightedContentDefinition<T>>,
        rng: Random
    ): T? {
        if (options.isEmpty()) return null
        val normalized = options.map { option ->
            option.copy(weight = max(0.0, option.weight))
        }
        val total = normalized.sumOf { it.weight }
        if (total <= 0.0) return normalized.first().content

        var roll = rng.nextDouble(0.0, total)
        for (option in normalized) {
            roll -= option.weight
            if (roll <= 0.0) return option.content
        }
        return normalized.last().content
    }
}

object ContentDefinitionMapper {
    fun fromMonsterArchetype(def: MonsterArchetypeDef): BaseDefinition {
        val baseType = def.baseType.ifBlank { def.id.substringBefore('_') }.lowercase()
        val family = def.family.ifBlank { def.archetype }.lowercase()
        return BaseDefinition(
            id = def.id,
            name = def.displayName.ifBlank { def.name },
            description = "Monstro base $baseType da familia $family.",
            tags = (def.tags + def.questTags + listOf(baseType, family, "monster")).map { it.lowercase() }.toSet(),
            tier = null,
            weight = 1.0
        )
    }

    fun fromClass(def: ClassDef): BaseDefinition {
        return BaseDefinition(
            id = def.id,
            name = def.name,
            description = def.description,
            tags = buildSet {
                add("class")
                add(def.id.lowercase())
                addAll(def.secondClassIds.map { "second_class:$it" })
            }
        )
    }

    fun fromRace(def: RaceDef): BaseDefinition {
        return BaseDefinition(
            id = def.id,
            name = def.name,
            description = def.description,
            tags = setOf("race", def.id.lowercase())
        )
    }

    fun fromMap(def: MapDef): BaseDefinition {
        return BaseDefinition(
            id = def.id,
            name = def.name,
            description = "Mapa com ${def.rooms.size} salas.",
            tags = setOf("map", def.id.lowercase())
        )
    }

    fun fromItem(def: ItemDef): BaseDefinition {
        return BaseDefinition(
            id = def.id,
            name = def.name,
            description = def.description,
            tags = (def.tags + listOf("item", def.type.name.lowercase())).map { it.lowercase() }.toSet(),
            tier = def.minLevel
        )
    }
}
