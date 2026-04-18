package rpg.engine

import kotlin.math.ceil
import kotlin.random.Random
import rpg.classquest.ClassQuestService
import rpg.combat.CombatEngine
import rpg.crafting.CraftingService
import rpg.classsystem.AttributeEngine
import rpg.classsystem.ClassSystem
import rpg.economy.DropEngine
import rpg.economy.DropOutcome
import rpg.economy.EconomyEngine
import rpg.events.EventContext
import rpg.inventory.InventorySystem
import rpg.io.DataRepository
import rpg.item.ItemEngine
import rpg.item.ItemResolver
import rpg.gathering.GatheringService
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.monster.MonsterFactory
import rpg.monster.MonsterInstance
import rpg.monster.MonsterAffinityService
import rpg.monster.MonsterXpEngine
import rpg.monster.MonsterGenerator
import rpg.monster.MonsterPersonality
import rpg.monster.MonsterRarity
import rpg.monster.MonsterBehaviorEngine
import rpg.progression.ExperienceEngine
import rpg.procedural.TextEngine
import rpg.quest.QuestBoardEngine
import rpg.quest.QuestGenerator
import rpg.quest.QuestProgressTracker
import rpg.quest.QuestRewardService
import rpg.skills.SkillSystem
import rpg.world.DungeonEngine
import rpg.registry.DropTableRegistry
import rpg.registry.ItemRegistry

class GameEngine(private val repo: DataRepository, private val rng: Random = Random.Default) {
    val balance = repo.balance
    val itemRegistry = ItemRegistry(repo.items, repo.itemTemplates)
    val statsEngine = StatsEngine(repo, itemRegistry)
    val classSystem = ClassSystem(repo)
    val dungeonEngine = DungeonEngine(repo, rng)
    val monsterFactory = MonsterFactory(repo, rng)
    val monsterAffinityService = MonsterAffinityService(repo.monsterTypes, repo.monsterArchetypes)
    val dropTableRegistry = DropTableRegistry(repo.dropTables)
    val itemEngine = ItemEngine(itemRegistry, repo.affixes, rng)
    val itemResolver = ItemResolver(itemRegistry)
    val dropEngine = DropEngine(dropTableRegistry, itemRegistry, itemEngine, rng, balance)
    val economyEngine = EconomyEngine(rng, balance)
    val behaviorEngine = MonsterBehaviorEngine(repo, rng)
    val combatEngine = CombatEngine(
        statsEngine = statsEngine,
        itemResolver = itemResolver,
        itemRegistry = itemRegistry,
        behaviorEngine = behaviorEngine,
        rng = rng,
        balance = balance,
        biomes = repo.biomes,
        archetypes = repo.monsterArchetypes,
        talentTrees = repo.talentTreesV2.values,
        monsterAffinityService = monsterAffinityService
    )
    val textEngine = TextEngine(repo, rng)
    val questGenerator = QuestGenerator(repo, rng)
    val questBoardEngine = QuestBoardEngine(questGenerator)
    val questProgressTracker = QuestProgressTracker()
    val questRewardService = QuestRewardService(itemRegistry, itemEngine, classSystem, rng)
    val classQuestService = ClassQuestService(repo, itemRegistry, classSystem, rng)
    val skillSystem = SkillSystem(repo.skills.values.associateBy { it.id })
    val craftingService = CraftingService(repo.craftRecipes, itemRegistry, itemEngine, skillSystem, rng)
    val gatheringService = GatheringService(repo.gatherNodes, itemRegistry, itemEngine, skillSystem, rng)

    fun computePlayerStats(player: PlayerState, itemInstances: Map<String, ItemInstance>): ComputedStats {
        return statsEngine.computePlayerStats(player, itemInstances)
    }

    fun computeMonsterStats(monster: MonsterInstance): ComputedStats {
        return StatsCalculator.compute(monster.attributes, listOf(monster.bonuses))
    }

    fun canEnterTier(player: PlayerState, tier: MapTierDef): Boolean {
        return player.level >= tier.minLevel
    }

    fun tierById(id: String): MapTierDef = dungeonEngine.tierById(id)

    fun availableTiers(player: PlayerState): List<MapTierDef> = dungeonEngine.availableTiers(player.level)

    fun startRun(tierId: String): rpg.model.DungeonRun = dungeonEngine.startRun(tierId)

    fun isBossRoomDue(run: rpg.model.DungeonRun): Boolean = dungeonEngine.isBossRoomDue(run)

    fun nextRoomType(run: rpg.model.DungeonRun): rpg.world.RunRoomType = dungeonEngine.nextRoomType(run)

    fun advanceRun(
        run: rpg.model.DungeonRun,
        bossDefeated: Boolean,
        clearedRoomType: rpg.world.RunRoomType,
        victoryInRoom: Boolean = false
    ): rpg.model.DungeonRun {
        return dungeonEngine.advanceRun(run, bossDefeated, clearedRoomType, victoryInRoom)
    }

    fun generateMonster(tier: MapTierDef, run: rpg.model.DungeonRun, player: PlayerState, isBoss: Boolean): MonsterInstance {
        return monsterFactory.generate(tier, run, player.level, isBoss)
    }

    fun buildMimicMonster(level: Int): MonsterInstance {
        val template = repo.monsterArchetypes["mimic"]
            ?: repo.monsterArchetypes.values.firstOrNull()
            ?: error("Nenhum template de monstro disponivel para mimico.")
        val (attrs, stars) = MonsterGenerator.generateAttributes(template, level, rng)
        val rarity = MonsterRarity.ELITE
        val scaledAttrs = attrs.scale(rarity.statMultiplier)
        val bonus = rpg.model.Bonuses()
        val stats = StatsCalculator.compute(scaledAttrs, listOf(bonus))
        val powerScore = PowerScoreEngine.fromStats(stats, balance)
        val baseType = template.baseType.ifBlank { template.id.substringBefore('_').lowercase() }
        val monsterTypeId = template.monsterTypeId.ifBlank {
            template.family.ifBlank { template.archetype.lowercase().ifBlank { "construct" } }
        }.lowercase()
        val family = template.family.ifBlank { monsterTypeId }
        val displayName = template.displayName.ifBlank { template.name }
        val lootProfile = template.lootProfileId.ifBlank { template.dropTableId }
        val tags = (
            template.tags +
                listOf(baseType, family, monsterTypeId, "base:$baseType", "family:$family", "type:$monsterTypeId")
            ).toSet()
        val questTags = template.questTags.map { it.lowercase() }.toSet()
        return MonsterInstance(
            archetypeId = template.id,
            id = template.id,
            sourceArchetypeId = template.id,
            baseType = baseType.lowercase(),
            monsterTypeId = monsterTypeId,
            family = family.lowercase(),
            name = displayName,
            displayName = displayName,
            variantName = template.variantName,
            level = level,
            rarity = rarity,
            attributes = scaledAttrs,
            bonuses = bonus,
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
            baseXp = template.baseXp,
            baseGold = template.baseGold,
            onHitStatuses = template.onHitStatuses
        )
    }

    fun rewardAfterVictory(
        state: GameState,
        monster: MonsterInstance,
        tier: MapTierDef
    ): GameState {
        val outcome = resolveVictory(state.player, state.itemInstances, monster, tier, collectToLoot = false)
        return state.copy(
            player = outcome.player,
            itemInstances = outcome.itemInstances
        )
    }

    fun resolveVictory(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        monster: MonsterInstance,
        tier: MapTierDef,
        collectToLoot: Boolean
    ): VictoryOutcome {
        val stats = computePlayerStats(player, itemInstances)
        val playerPower = PowerScoreEngine.fromStats(stats, balance)
        val difficultyMod = PowerScoreEngine.difficultyModifier(monster.powerScore, playerPower, balance)
        val baseXp = MonsterXpEngine.baseXp(monster.powerScore, monster.baseXp, balance)
        val tierPenalty = if (player.level >= tier.recommendedLevel + balance.antiFarmLevelGap) {
            balance.antiFarmXpMultiplier
        } else {
            1.0
        }
        val deathXpPenaltyMultiplier = if (player.deathXpPenaltyMinutes > 0.0) {
            (1.0 - (player.deathXpPenaltyPct / 100.0)).coerceIn(0.1, 1.0)
        } else {
            1.0
        }
        val starXpMultiplier = balance.starRewards.xpMultiplier(monster.stars)
        val xpGain = (ExperienceEngine.computeXpGain(
            baseXp = baseXp,
            playerLevel = player.level,
            monsterLevel = monster.level,
            xpBonusPct = stats.derived.xpGainPct
        ) * tier.xpMultiplier * tierPenalty * difficultyMod * deathXpPenaltyMultiplier * starXpMultiplier)
            .toInt()
            .coerceAtLeast(1)

        val baseGoldGain = economyEngine.computeGold(
            monsterPower = monster.powerScore,
            baseGold = monster.baseGold,
            tier = tier,
            playerLevel = player.level,
            difficultyModifier = difficultyMod
        )
        val starGoldMultiplier = balance.starRewards.goldMultiplier(monster.stars)
        val goldGain = (baseGoldGain * starGoldMultiplier).toInt().coerceAtLeast(1)

        var updatedPlayer = player.copy(gold = player.gold + goldGain)

        val beforeLevel = updatedPlayer.level
        updatedPlayer = ExperienceEngine.applyXp(updatedPlayer, xpGain)
        val levelsGained = updatedPlayer.level - beforeLevel
        if (levelsGained > 0) {
            val classDef = classSystem.classDef(updatedPlayer.classId)
            val raceDef = classSystem.raceDef(updatedPlayer.raceId)
            val subclassDef = classSystem.subclassDef(updatedPlayer.subclassId)
            val specializationDef = classSystem.specializationDef(updatedPlayer.specializationId)
            repeat(levelsGained) {
                updatedPlayer = applyAutoPoints(updatedPlayer, classDef, raceDef, subclassDef, specializationDef)
            }
        }

        val dropOutcome = rollDrop(
            updatedPlayer,
            stats,
            tier,
            monster,
            itemInstances,
            collectToLoot = collectToLoot
        )

        return VictoryOutcome(
            player = dropOutcome.player,
            itemInstances = dropOutcome.itemInstances,
            xpGain = xpGain,
            goldGain = goldGain,
            dropOutcome = dropOutcome.outcome
        )
    }

    fun rollDrop(
        player: PlayerState,
        stats: ComputedStats,
        tier: MapTierDef,
        monster: MonsterInstance,
        itemInstances: Map<String, ItemInstance>,
        collectToLoot: Boolean
    ): DropResult {
        val playerPower = PowerScoreEngine.fromStats(stats, balance)
        val difficultyMod = PowerScoreEngine.difficultyModifier(monster.powerScore, playerPower, balance)
        val outcome = dropEngine.rollDrop(player, stats.derived.dropBonusPct, tier, monster, difficultyMod)
        var updatedPlayer = player
        var instances = itemInstances
        val incomingInventoryIds = mutableListOf<String>()
        var rareDropPity = player.rareDropPity

        if (outcome.itemInstance != null) {
            val item = outcome.itemInstance
            instances = instances + (item.id to item)
            if (!collectToLoot) {
                incomingInventoryIds += item.id
            }
        } else if (outcome.itemId != null) {
            val qty = outcome.quantity.coerceAtLeast(1)
            if (!collectToLoot) {
                repeat(qty) { incomingInventoryIds += outcome.itemId }
            }
        }

        rareDropPity = if (outcome.rareDropped) 0 else rareDropPity + 1
        val finalStorage = if (collectToLoot || incomingInventoryIds.isEmpty()) {
            Triple(player.inventory, player.quiverInventory, player.selectedAmmoTemplateId)
        } else {
            val insert = InventorySystem.addItemsWithLimit(
                player = player,
                itemInstances = instances,
                itemRegistry = itemRegistry,
                incomingItemIds = incomingInventoryIds
            )
            val rejectedGenerated = insert.rejected.filter { instances.containsKey(it) }
            if (rejectedGenerated.isNotEmpty()) {
                instances = instances - rejectedGenerated.toSet()
            }
            Triple(insert.inventory, insert.quiverInventory, insert.selectedAmmoTemplateId)
        }
        updatedPlayer = updatedPlayer.copy(
            inventory = finalStorage.first,
            quiverInventory = finalStorage.second,
            selectedAmmoTemplateId = finalStorage.third,
            rareDropPity = rareDropPity
        )

        return DropResult(updatedPlayer, instances, outcome)
    }

    fun applyDeathPenalty(player: PlayerState, loot: List<String>): Pair<PlayerState, List<String>> {
        val lossReduction = player.baseAttributes.luk * 0.3
        val lossPct = (80.0 - lossReduction).coerceIn(20.0, 80.0)
        val keepPct = 100.0 - lossPct
        val keepCount = ceil(loot.size * (keepPct / 100.0)).toInt().coerceAtMost(loot.size)
        val kept = loot.shuffled(rng).take(keepCount)
        val updated = player.copy(currentHp = 1.0)
        return updated to kept
    }

    fun monsterDisplayName(monster: MonsterInstance): String {
        val baseDisplay = monster.displayName.ifBlank {
            monster.name.ifBlank { monster.baseType.ifBlank { monster.archetypeId } }
        }
        val shortName = if (monster.variantName.isBlank()) {
            baseDisplay
        } else if (baseDisplay.lowercase().contains(monster.variantName.lowercase())) {
            baseDisplay
        } else {
            "$baseDisplay ${monster.variantName}"
        }
        if (monster.stars <= 0) return shortName
        val tag = balance.starTags.sortedByDescending { it.minStars }.firstOrNull { monster.stars >= it.minStars }
        val starSuffix = "(${monster.stars}*)"
        return if (tag != null && tag.label.isNotBlank()) {
            "$shortName ${tag.label} $starSuffix"
        } else {
            "$shortName $starSuffix"
        }
    }

    fun encounterText(monster: MonsterInstance, tier: MapTierDef, playerStats: ComputedStats): String {
        val playerPower = PowerScoreEngine.fromStats(playerStats, balance)
        val danger = textEngine.dangerLevel(monster.powerScore, playerPower)
        return textEngine.encounterText(monster, tier.biomeId, danger)
    }

    fun rollEscape(playerStats: ComputedStats, monsterStats: ComputedStats): Boolean {
        val escapeChance = (40.0 + (playerStats.attributes.agi - monsterStats.attributes.agi) * 2.0)
            .coerceIn(5.0, 95.0)
        return rng.nextDouble(0.0, 100.0) <= escapeChance
    }

    fun shuffleLoot(loot: MutableList<String>) {
        loot.shuffle(rng)
    }

    fun pickRandomAttribute(): String {
        val attrs = listOf("STR", "AGI", "DEX", "VIT", "INT", "SPR", "LUK")
        return attrs.random(rng)
    }

    fun buildEventContext(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        depth: Int
    ): EventContext {
        return EventContext(
            statsProvider = { target -> computePlayerStats(target, itemInstances) },
            rng = rng,
            playerLevel = player.level,
            depth = depth
        )
    }

    fun rollInt(bound: Int): Int = rng.nextInt(bound)

    fun rollChance(chancePct: Double): Boolean = rng.nextDouble(0.0, 100.0) <= chancePct

    fun applyAutoPoints(
        player: PlayerState,
        classDef: rpg.model.ClassDef,
        raceDef: rpg.model.RaceDef,
        subclassDef: rpg.model.SubclassDef?,
        specializationDef: rpg.model.SpecializationDef?
    ): PlayerState = AttributeEngine.applyAutoPoints(player, classDef, raceDef, subclassDef, specializationDef, rng)
}

data class DropResult(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val outcome: DropOutcome
)

data class VictoryOutcome(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val xpGain: Int,
    val goldGain: Int,
    val dropOutcome: DropOutcome
)
