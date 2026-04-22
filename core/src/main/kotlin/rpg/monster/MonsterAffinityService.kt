package rpg.monster

import rpg.model.DamageChannel
import rpg.model.MonsterArchetypeDef
import rpg.model.MonsterTypeDefinition

class MonsterAffinityService(
    private val typeDefinitions: Map<String, MonsterTypeDefinition>,
    private val archetypes: Map<String, MonsterArchetypeDef>
) {
    fun multiplierForMonster(monster: MonsterInstance, channel: DamageChannel): Double {
        return multiplierFor(
            archetypeId = monster.sourceArchetypeId.ifBlank { monster.archetypeId },
            typeIdHint = monster.monsterTypeId.ifBlank { monster.family },
            tags = monster.tags,
            channel = channel
        )
    }

    fun multiplierFor(
        archetypeId: String,
        typeIdHint: String?,
        tags: Set<String>,
        channel: DamageChannel
    ): Double {
        val archetype = archetypes[archetypeId]
        val normalizedType = resolveTypeId(typeIdHint, archetype)
        val typeMap = normalizedType
            ?.let { typeDefinitions[it] }
            ?.damageAffinities
            .orEmpty()
        val archetypeMap = archetype?.damageAffinities.orEmpty()
        val tagMap = mergeTagAffinities(tags)

        val combined = mutableMapOf<DamageChannel, Double>()
        applyAffinityMap(combined, typeMap)
        applyAffinityMap(combined, archetypeMap)
        applyAffinityMap(combined, tagMap)

        return combined[channel]?.coerceIn(0.2, 3.0) ?: 1.0
    }

    fun buildProfile(
        archetypeId: String,
        typeIdHint: String?,
        tags: Set<String>
    ): Map<DamageChannel, Double> {
        val channels = DamageChannel.entries
        return channels.associateWith { channel ->
            multiplierFor(archetypeId, typeIdHint, tags, channel)
        }.filterValues { value -> kotlin.math.abs(value - 1.0) > 1e-6 }
    }

    private fun applyAffinityMap(
        target: MutableMap<DamageChannel, Double>,
        source: Map<String, Double>
    ) {
        for ((key, multiplier) in source) {
            val channel = DamageChannel.fromKey(key) ?: continue
            target[channel] = multiplier.coerceIn(0.2, 3.0)
        }
    }

    private fun resolveTypeId(typeIdHint: String?, archetype: MonsterArchetypeDef?): String? {
        val hinted = typeIdHint?.trim()?.lowercase().orEmpty()
        if (hinted.isNotBlank()) return hinted
        if (archetype == null) return null
        val fromArchetype = archetype.monsterTypeId.trim().lowercase()
        if (fromArchetype.isNotBlank()) return fromArchetype
        val fromFamily = archetype.family.trim().lowercase()
        if (fromFamily.isNotBlank()) return fromFamily
        val fromArchetypeTag = archetype.archetype.trim().lowercase()
        return fromArchetypeTag.ifBlank { null }
    }

    private fun mergeTagAffinities(tags: Set<String>): Map<String, Double> {
        if (tags.isEmpty()) return emptyMap()
        val normalized = tags.mapTo(mutableSetOf()) { it.trim().lowercase() }
        val multipliers = mutableMapOf<DamageChannel, Double>()

        fun apply(channel: DamageChannel, multiplier: Double) {
            val current = multipliers[channel] ?: 1.0
            multipliers[channel] = (current * multiplier).coerceIn(0.2, 3.0)
        }

        if ("armored" in normalized) {
            apply(DamageChannel.PHYSICAL, 0.85)
            apply(DamageChannel.BLEED, 0.65)
            apply(DamageChannel.MAGIC, 1.1)
        }
        if ("corrupted" in normalized) {
            apply(DamageChannel.SHADOW, 0.75)
            apply(DamageChannel.HOLY, 1.25)
        }
        if ("ancient" in normalized) {
            apply(DamageChannel.ARCANE, 0.9)
            apply(DamageChannel.MAGIC, 0.95)
        }
        if ("swift" in normalized) {
            apply(DamageChannel.PIERCE, 1.05)
            apply(DamageChannel.BLUNT, 0.95)
        }
        if ("frenzied" in normalized) {
            apply(DamageChannel.BLEED, 1.1)
            apply(DamageChannel.POISON, 1.1)
        }

        return multipliers.mapKeys { (channel, _) -> channel.name.lowercase() }
    }
}
