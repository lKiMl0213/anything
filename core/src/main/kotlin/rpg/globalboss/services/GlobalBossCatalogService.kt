package rpg.globalboss.services

import kotlin.random.Random
import rpg.engine.GameEngine
import rpg.engine.PowerScoreEngine
import rpg.engine.StatsCalculator
import rpg.globalboss.config.GlobalBossCadence
import rpg.globalboss.config.GlobalBossEventDef
import rpg.io.DataRepository
import rpg.model.Bonuses
import rpg.model.DerivedStats
import rpg.model.MapTierDef
import rpg.monster.MonsterGenerator
import rpg.monster.MonsterInstance
import rpg.monster.MonsterPersonality
import rpg.monster.MonsterRarity

class GlobalBossCatalogService(
    private val repo: DataRepository,
    private val engine: GameEngine
) {
    private val eventsById = repo.globalBossEvents
    private val eventsByCadence = eventsById.values.associateBy { it.cadence }

    fun allEvents(): List<GlobalBossEventDef> = eventsById.values.sortedBy { it.cadence.name }

    fun eventById(eventId: String): GlobalBossEventDef? = eventsById[eventId.trim().lowercase()]

    fun eventByCadence(cadence: GlobalBossCadence): GlobalBossEventDef? = eventsByCadence[cadence]

    fun resolveTier(event: GlobalBossEventDef): MapTierDef {
        return runCatching { engine.tierById(event.tierId) }.getOrElse {
            repo.mapTiers.values.firstOrNull()
                ?: error("Nenhum map tier carregado para boss global.")
        }
    }

    fun buildBoss(event: GlobalBossEventDef, playerLevel: Int): MonsterInstance {
        val archetype = repo.monsterArchetypes[event.bossArchetypeId]
            ?: repo.monsterArchetypes.values.firstOrNull()
            ?: error("Nenhum arquetipo de monstro disponível para boss global.")
        val level = (playerLevel + event.levelOffset).coerceIn(
            event.minBossLevel.coerceAtLeast(1),
            event.maxBossLevel.coerceAtLeast(event.minBossLevel.coerceAtLeast(1))
        )
        val rngSeed = (event.id.lowercase().hashCode().toLong() shl 32) xor level.toLong()
        val (baseAttributes, stars) = MonsterGenerator.generateAttributes(archetype, level, Random(rngSeed))
        val rarity = MonsterRarity.LEGENDARY
        val attributes = baseAttributes.scale(rarity.statMultiplier)
        val balance = event.balance
        val bonuses = Bonuses(
            derivedMult = DerivedStats(
                damagePhysical = balance.baseDamageMultiplierPct - 100.0,
                damageMagic = balance.baseDamageMultiplierPct - 100.0,
                defPhysical = balance.baseDefenseMultiplierPct - 100.0,
                defMagic = balance.baseDefenseMultiplierPct - 100.0,
                hpMax = balance.baseHpMultiplierPct - 100.0
            ),
            derivedAdd = DerivedStats(
                damageReductionPct = balance.baseDamageReductionPct
            )
        )
        val stats = StatsCalculator.compute(attributes, listOf(bonuses))
        val powerScore = PowerScoreEngine.fromStats(stats, engine.balance)
        val baseType = archetype.baseType.ifBlank { archetype.id.substringBefore('_').lowercase() }
        val monsterTypeId = archetype.monsterTypeId.ifBlank {
            archetype.family.ifBlank { archetype.archetype.ifBlank { baseType } }
        }.lowercase()
        val family = archetype.family.ifBlank { monsterTypeId }.lowercase()
        val displayName = archetype.displayName.ifBlank { archetype.name }
        val tags = (archetype.tags + listOf(baseType, family, monsterTypeId, "global_boss")).map { it.lowercase() }.toSet()
        val questTags = archetype.questTags.map { it.lowercase() }.toSet()
        val lootProfile = archetype.lootProfileId.ifBlank { archetype.dropTableId }
        return MonsterInstance(
            archetypeId = archetype.id,
            id = archetype.id,
            sourceArchetypeId = archetype.id,
            baseType = baseType.lowercase(),
            monsterTypeId = monsterTypeId,
            family = family,
            name = displayName,
            displayName = displayName,
            variantName = archetype.variantName,
            level = level,
            rarity = rarity,
            attributes = attributes,
            bonuses = bonuses,
            tags = tags,
            questTags = questTags,
            modifiers = emptyList(),
            affixes = emptyList(),
            personality = MonsterPersonality.BALANCED,
            starCount = stars,
            stars = stars,
            maxStatsCapAmount = stars,
            powerScore = powerScore,
            dropTableId = lootProfile,
            lootProfileId = lootProfile,
            baseXp = archetype.baseXp,
            baseGold = archetype.baseGold,
            onHitStatuses = archetype.onHitStatuses
        )
    }

}

