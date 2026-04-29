package rpg.monster

import kotlin.random.Random
import rpg.engine.PowerScoreEngine
import rpg.engine.StatsCalculator
import rpg.io.DataRepository
import rpg.model.Bonuses
import rpg.model.DungeonRun
import rpg.model.MapTierDef

class MonsterFactory(private val repo: DataRepository, rng: Random) {
    private val rng = rng
    private val threatService = MonsterThreatService(rng)
    private val templatePicker = MonsterTemplatePicker(repo, rng)
    private val modifierService = MonsterModifierService(repo, rng)

    fun generate(
        tier: MapTierDef,
        run: DungeonRun,
        playerLevel: Int,
        isBoss: Boolean,
        rarityBonusPct: Double = 0.0
    ): MonsterInstance {
        val profile = threatService.buildThreatProfile(tier = tier, run = run, isBoss = isBoss)
        val biome = tier.biomeId?.let { repo.biomes[it] }
        val archetype = templatePicker.pickTemplate(tier, biome)
        val level = threatService.monsterLevel(tier, run, playerLevel)
        val tunedTemplate = archetype.copy(
            variancePct = (archetype.variancePct * profile.varianceScale).coerceIn(0.06, 0.45)
        )
        val (rolledAttributes, rolledStars) = MonsterGenerator.generateAttributes(tunedTemplate, level, rng)
        val stars = rolledStars.coerceAtMost(profile.starCap)
        val rarity = threatService.rollRarity(
            difficulty = run.difficultyLevel,
            isBoss = isBoss,
            rarityPressure = profile.rarityPressure,
            rarityBonusPct = rarityBonusPct
        )
        val modifierCount = threatService.modifierCount(
            rarity = rarity,
            mutationTier = run.mutationTier,
            progression = profile.progression,
            maxModifierCount = profile.maxModifierCount
        )
        val modifiers = modifierService.rollModifiers(modifierCount)
        val rarityScaled = rolledAttributes.scale(rarity.statMultiplier)
        val modifiedAttributes = modifierService.applyModifiersToAttributes(rarityScaled, modifiers)
        val bonus = modifiers.fold(Bonuses()) { acc, mod -> acc + mod.bonuses }
        val baseType = archetype.baseType.ifBlank {
            templatePicker.normalizeBaseType(archetype.id, archetype.name)
        }
        val monsterTypeId = archetype.monsterTypeId.ifBlank {
            archetype.family.ifBlank { archetype.archetype.lowercase().ifBlank { "neutral" } }
        }
        val family = archetype.family.ifBlank { monsterTypeId }
        val displayName = archetype.displayName.ifBlank { archetype.name }
        val variantName = templatePicker.resolveVariantName(archetype.variantName, modifiers)
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
        val scaledArchetypeStatuses = threatService.scaleArchetypeStatuses(
            statuses = archetype.onHitStatuses,
            profile = profile,
            isBoss = isBoss
        )
        val starStatus = threatService.rollStarStatus(stars = stars, isBoss = isBoss, profile = profile)
        val onHitStatuses = scaledArchetypeStatuses + starStatus
        val lootProfileId = archetype.lootProfileId.ifBlank { archetype.dropTableId }
        val shortName = templatePicker.buildShortDisplayName(displayName, variantName)
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
}
