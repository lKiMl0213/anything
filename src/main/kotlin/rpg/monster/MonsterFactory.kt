package rpg.monster

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import rpg.engine.PowerScoreEngine
import rpg.engine.StatsCalculator
import rpg.io.DataRepository
import rpg.model.BiomeDef
import rpg.model.Bonuses
import rpg.model.CombatStatusApplyDef
import rpg.model.DungeonRun
import rpg.model.MapTierDef
import rpg.model.MonsterArchetypeDef
import rpg.model.WeightedContentDefinition
import rpg.model.WeightedContentPicker
import rpg.status.StatusType

private data class MonsterThreatProfile(
    val progression: Double,
    val varianceScale: Double,
    val statusScale: Double,
    val rarityPressure: Double,
    val maxModifierCount: Int,
    val starCap: Int
)

class MonsterFactory(private val repo: DataRepository, private val rng: Random) {
    fun generate(
        tier: MapTierDef,
        run: DungeonRun,
        playerLevel: Int,
        isBoss: Boolean
    ): MonsterInstance {
        val profile = buildThreatProfile(tier = tier, run = run, isBoss = isBoss)
        val biome = tier.biomeId?.let { repo.biomes[it] }
        val archetype = pickTemplate(tier, biome)
        val level = monsterLevel(tier, run, playerLevel)
        val tunedTemplate = archetype.copy(
            variancePct = (archetype.variancePct * profile.varianceScale).coerceIn(0.06, 0.45)
        )
        val (rolledAttributes, rolledStars) = MonsterGenerator.generateAttributes(tunedTemplate, level, rng)
        val stars = rolledStars.coerceAtMost(profile.starCap)
        val rarity = rollRarity(
            difficulty = run.difficultyLevel,
            isBoss = isBoss,
            rarityPressure = profile.rarityPressure
        )
        val modifierCount = modifierCount(
            rarity = rarity,
            mutationTier = run.mutationTier,
            progression = profile.progression,
            maxModifierCount = profile.maxModifierCount
        )
        val modifiers = rollModifiers(modifierCount)
        val rarityScaled = rolledAttributes.scale(rarity.statMultiplier)
        val modifiedAttributes = applyModifiersToAttributes(rarityScaled, modifiers)
        val bonus = modifiers.fold(Bonuses()) { acc, mod -> acc + mod.bonuses }
        val baseType = archetype.baseType.ifBlank { normalizeBaseType(archetype.id, archetype.name) }
        val monsterTypeId = archetype.monsterTypeId.ifBlank {
            archetype.family.ifBlank { archetype.archetype.lowercase().ifBlank { "neutral" } }
        }
        val family = archetype.family.ifBlank { monsterTypeId }
        val displayName = archetype.displayName.ifBlank { archetype.name }
        val variantName = resolveVariantName(archetype.variantName, modifiers)
        val tags = archetype.tags.toMutableSet()
        tags.add(baseType.lowercase())
        tags.add(family.lowercase())
        tags.add(monsterTypeId.lowercase())
        tags.add(archetype.id.lowercase())
        tags.add("base:$baseType")
        tags.add("family:$family")
        tags.add("type:$monsterTypeId")
        for (questTag in archetype.questTags) {
            tags.add(questTag.lowercase())
            tags.add("quest:$questTag")
        }
        if (archetype.behaviorProfileId.isNotBlank()) {
            tags.add(archetype.behaviorProfileId)
        }
        modifiers.flatMapTo(tags) { it.addTags }
        val personality = MonsterPersonality.roll(rng)
        val stats = StatsCalculator.compute(modifiedAttributes, listOf(bonus))
        val powerScore = PowerScoreEngine.fromStats(stats, repo.balance)

        val modifierIds = modifiers.map { it.id }
        val affixNames = modifiers.map { it.name }
        val scaledArchetypeStatuses = scaleArchetypeStatuses(
            statuses = archetype.onHitStatuses,
            profile = profile,
            isBoss = isBoss
        )
        val starStatus = rollStarStatus(stars = stars, isBoss = isBoss, profile = profile)
        val onHitStatuses = scaledArchetypeStatuses + starStatus
        val lootProfileId = archetype.lootProfileId.ifBlank { archetype.dropTableId }
        val shortName = buildShortDisplayName(displayName, variantName)
        val questTags = archetype.questTags.map { it.lowercase() }.toSet()

        return MonsterInstance(
            archetypeId = archetype.id,
            id = archetype.id,
            sourceArchetypeId = archetype.id,
            baseType = baseType.lowercase(),
            monsterTypeId = monsterTypeId.lowercase(),
            family = family.lowercase(),
            name = shortName,
            displayName = displayName,
            variantName = variantName,
            level = level,
            rarity = rarity,
            attributes = modifiedAttributes,
            bonuses = bonus,
            tags = tags,
            questTags = questTags,
            modifiers = modifierIds,
            affixes = affixNames,
            personality = personality,
            starCount = stars,
            stars = stars,
            maxStatsCapAmount = stars,
            powerScore = powerScore,
            dropTableId = lootProfileId,
            lootProfileId = lootProfileId,
            baseXp = archetype.baseXp,
            baseGold = archetype.baseGold,
            onHitStatuses = onHitStatuses
        )
    }

    private fun buildThreatProfile(
        tier: MapTierDef,
        run: DungeonRun,
        isBoss: Boolean
    ): MonsterThreatProfile {
        val winsFactor = (run.victoriesInRun / 45.0).coerceIn(0.0, 1.0)
        val depthFactor = (run.depth / 55.0).coerceIn(0.0, 1.0)
        val roomFactor = (run.roomsCleared / 60.0).coerceIn(0.0, 1.0)
        val difficultyFactor = ((run.difficultyLevel - 1) / 14.0).coerceIn(0.0, 1.0)
        val mutationFactor = (run.mutationTier / 7.0).coerceIn(0.0, 1.0)
        val tierFactor = if (tier.isInfinite) {
            1.0
        } else {
            (tier.recommendedLevel / 35.0).coerceIn(0.0, 1.0)
        }

        val progression = (
            0.08 +
                winsFactor * 0.26 +
                depthFactor * 0.14 +
                roomFactor * 0.05 +
                difficultyFactor * 0.14 +
                mutationFactor * 0.08 +
                tierFactor * 0.25
            ).coerceIn(0.0, 1.0)

        val varianceScale = (
            0.60 +
                progression * 0.70 +
                if (isBoss) 0.12 else 0.0
            ).coerceIn(0.55, 1.45)

        val statusScale = (
            0.72 +
                progression * 0.48 +
                if (isBoss) 0.08 else 0.0
            ).coerceIn(0.65, 1.30)

        val maxModifierCount = when {
            progression < 0.18 -> 0
            progression < 0.42 -> 1
            progression < 0.70 -> 2
            else -> 3
        } + if (isBoss && progression >= 0.80) 1 else 0

        val starCapBase = when {
            progression < 0.14 -> 2
            progression < 0.30 -> 3
            progression < 0.50 -> 4
            progression < 0.68 -> 5
            progression < 0.86 -> 6
            else -> 7
        }
        val starCap = (starCapBase + if (isBoss) 1 else 0).coerceIn(1, 7)

        return MonsterThreatProfile(
            progression = progression,
            varianceScale = varianceScale,
            statusScale = statusScale,
            rarityPressure = progression,
            maxModifierCount = maxModifierCount.coerceIn(0, 5),
            starCap = starCap
        )
    }

    private fun pickTemplate(tier: MapTierDef, biome: BiomeDef?): MonsterArchetypeDef {
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

    private fun monsterLevel(tier: MapTierDef, run: DungeonRun, playerLevel: Int): Int {
        val base = if (tier.isInfinite) {
            max(tier.baseMonsterLevel, (playerLevel * 1.1).roundToInt())
        } else {
            tier.baseMonsterLevel
        }
        val scaled = base + (run.difficultyLevel - 1)
        return max(1, (scaled * tier.difficultyMultiplier).roundToInt())
    }

    private fun rollRarity(
        difficulty: Int,
        isBoss: Boolean,
        rarityPressure: Double
    ): MonsterRarity {
        val pressure = rarityPressure.coerceIn(0.0, 1.0)
        val difficultyBonus = (difficulty - 1).coerceAtLeast(0).toDouble()
        val weights = mutableMapOf(
            MonsterRarity.COMMON to (68.0 - pressure * 28.0),
            MonsterRarity.ELITE to (18.0 + pressure * 8.0 + difficultyBonus * 0.8),
            MonsterRarity.RARE to (9.0 + pressure * 8.0 + difficultyBonus * 0.7),
            MonsterRarity.EPIC to (4.0 + pressure * 6.0 + difficultyBonus * 0.45),
            MonsterRarity.LEGENDARY to (1.0 + pressure * 4.0 + difficultyBonus * 0.25)
        )

        if (!isBoss && pressure < 0.2) {
            weights[MonsterRarity.EPIC] = minOf(weights[MonsterRarity.EPIC] ?: 1.0, 2.0)
            weights[MonsterRarity.LEGENDARY] = minOf(weights[MonsterRarity.LEGENDARY] ?: 1.0, 0.8)
        }
        if (isBoss) {
            weights[MonsterRarity.COMMON] = (weights[MonsterRarity.COMMON] ?: 1.0) * 0.55
            weights[MonsterRarity.ELITE] = (weights[MonsterRarity.ELITE] ?: 1.0) + 6.0
            weights[MonsterRarity.RARE] = (weights[MonsterRarity.RARE] ?: 1.0) + 6.0
            weights[MonsterRarity.EPIC] = (weights[MonsterRarity.EPIC] ?: 1.0) + 4.0
            weights[MonsterRarity.LEGENDARY] = (weights[MonsterRarity.LEGENDARY] ?: 1.0) + 2.0
        }

        val total = weights.values.sumOf { it.coerceAtLeast(0.1) }
        var roll = rng.nextDouble(0.0, total)
        for (rarity in MonsterRarity.entries) {
            roll -= (weights[rarity] ?: 0.1).coerceAtLeast(0.1)
            if (roll <= 0.0) return rarity
        }
        return MonsterRarity.COMMON
    }

    private fun modifierCount(
        rarity: MonsterRarity,
        mutationTier: Int,
        progression: Double,
        maxModifierCount: Int
    ): Int {
        val base = when (rarity) {
            MonsterRarity.COMMON -> 0
            MonsterRarity.ELITE -> 1
            MonsterRarity.RARE -> 1
            MonsterRarity.EPIC -> 2
            MonsterRarity.LEGENDARY -> 3
        }
        val mutationScale = (0.30 + progression * 0.80).coerceIn(0.3, 1.2)
        val mutationBonus = (mutationTier * mutationScale).roundToInt().coerceAtLeast(0)
        return (base + mutationBonus).coerceIn(0, maxModifierCount.coerceAtLeast(0))
    }

    private fun rollModifiers(count: Int): List<MonsterModifier> {
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

    private fun applyModifiersToAttributes(
        attrs: rpg.model.Attributes,
        modifiers: List<MonsterModifier>
    ): rpg.model.Attributes {
        var result = attrs
        for (modifier in modifiers) {
            if (modifier.attributeMultiplier != 1.0) {
                result = result.scale(modifier.attributeMultiplier)
            }
        }
        return result
    }

    private fun scaleArchetypeStatuses(
        statuses: List<CombatStatusApplyDef>,
        profile: MonsterThreatProfile,
        isBoss: Boolean
    ): List<CombatStatusApplyDef> {
        if (statuses.isEmpty()) return emptyList()
        val chanceScale = profile.statusScale
        val effectScale = (0.78 + profile.progression * 0.42 + if (isBoss) 0.08 else 0.0).coerceIn(0.7, 1.35)
        val durationScale = (0.84 + profile.progression * 0.32 + if (isBoss) 0.05 else 0.0).coerceIn(0.75, 1.2)
        return statuses.map { status ->
            val reducedStacks = if (!isBoss && profile.progression < 0.25 && status.stackable) {
                (status.maxStacks - 1).coerceAtLeast(1)
            } else {
                status.maxStacks
            }
            status.copy(
                chancePct = (status.chancePct * chanceScale).coerceIn(1.0, 100.0),
                durationSeconds = (status.durationSeconds * durationScale).coerceAtLeast(0.1),
                effectValue = status.effectValue * effectScale,
                maxStacks = reducedStacks
            )
        }
    }

    private fun resolveVariantName(archetypeVariant: String, modifiers: List<MonsterModifier>): String {
        if (archetypeVariant.isNotBlank()) return archetypeVariant
        val preferred = modifiers.firstOrNull()?.name?.trim().orEmpty()
        return preferred
    }

    private fun buildShortDisplayName(base: String, variant: String): String {
        if (variant.isBlank()) return base
        val normalizedBase = base.trim()
        val normalizedVariant = variant.trim()
        if (normalizedBase.lowercase().contains(normalizedVariant.lowercase())) {
            return normalizedBase
        }
        return "$normalizedBase $normalizedVariant"
    }

    private fun normalizeBaseType(archetypeId: String, fallbackName: String): String {
        val fromId = archetypeId
            .substringBefore('_')
            .trim()
            .lowercase()
            .takeIf { it.isNotBlank() }
        if (fromId != null) return fromId
        return fallbackName.trim().lowercase().ifBlank { "monster" }
    }

    private fun rollStarStatus(
        stars: Int,
        isBoss: Boolean,
        profile: MonsterThreatProfile
    ): List<CombatStatusApplyDef> {
        if (stars <= 0) return emptyList()
        val starChance = (
            stars * (4.0 + profile.progression * 3.0) +
                if (isBoss) 8.0 else 0.0
            ) * profile.statusScale
        val chance = starChance.coerceAtMost(90.0)
        if (rng.nextDouble(0.0, 100.0) > chance) return emptyList()

        val type = StatusType.entries.random(rng)
        val baseValue = when (type) {
            StatusType.BURNING -> 3.0 + stars * 1.1
            StatusType.POISONED -> 2.5 + stars * 0.9
            StatusType.BLEEDING -> 0.008 + stars * 0.002
            StatusType.FROZEN -> 0.0
            StatusType.PARALYZED -> 8.0 + stars * 2.0
            StatusType.WEAKNESS -> 5.0 + stars * 1.5
            StatusType.SLOW -> 6.0 + stars * 1.4
            StatusType.MARKED -> 1.0
        }
        val potencyScale = (0.78 + profile.progression * 0.55 + if (isBoss) 0.1 else 0.0).coerceIn(0.7, 1.45)
        val duration = when (type) {
            StatusType.FROZEN -> 2.5 + stars * 0.2
            else -> 5.0 + stars * 0.5
        } * (0.84 + profile.progression * 0.35).coerceIn(0.8, 1.2)

        return listOf(
            CombatStatusApplyDef(
                type = type,
                chancePct = ((10.0 + stars * 3.5) * profile.statusScale).coerceIn(4.0, 75.0),
                durationSeconds = duration,
                tickIntervalSeconds = 1.5,
                effectValue = baseValue * potencyScale,
                stackable = type in setOf(StatusType.BURNING, StatusType.POISONED, StatusType.BLEEDING),
                maxStacks = if (type in setOf(StatusType.BURNING, StatusType.POISONED, StatusType.BLEEDING)) {
                    if (!isBoss && profile.progression < 0.25) 2 else 3
                } else {
                    1
                },
                source = "StarTrait$stars"
            )
        )
    }
}
