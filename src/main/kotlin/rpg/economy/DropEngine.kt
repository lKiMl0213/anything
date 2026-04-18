package rpg.economy

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import rpg.item.ItemEngine
import rpg.item.ItemRarity
import rpg.model.DropEntryDef
import rpg.model.DropTableDef
import rpg.model.GameBalanceDef
import rpg.model.ItemInstance
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.monster.MonsterInstance
import rpg.registry.DropTableRegistry
import rpg.registry.ItemRegistry
import rpg.registry.ItemRegistryEntry

data class DropOutcome(
    val itemId: String? = null,
    val itemInstance: ItemInstance? = null,
    val quantity: Int = 0,
    val rareDropped: Boolean = false
)

class DropEngine(
    private val dropTables: DropTableRegistry,
    private val itemRegistry: ItemRegistry,
    private val itemEngine: ItemEngine,
    private val rng: Random,
    private val balance: GameBalanceDef
) {
    fun rollDrop(
        player: PlayerState,
        statsDropBonusPct: Double,
        tier: MapTierDef,
        monster: MonsterInstance,
        difficultyModifier: Double
    ): DropOutcome {
        val tableId = monster.lootProfileId.ifBlank { monster.dropTableId }
        val dropTable = dropTables.get(tableId) ?: dropTables.get(monster.dropTableId) ?: return DropOutcome()
        val baseChance = dropTable.baseChancePct +
            statsDropBonusPct +
            player.baseAttributes.luk * 0.18 +
            monster.stars * 1.6 +
            monster.rarity.ordinal * 1.8
        val pityBonus = player.rareDropPity * 0.5
        val starDropMultiplier = balance.starRewards.dropMultiplier(monster.stars)
        var chance = (baseChance + pityBonus) * difficultyModifier * tier.dropChanceMultiplier * starDropMultiplier
        val antiExploit = player.level >= tier.recommendedLevel + balance.antiFarmLevelGap
        if (antiExploit) {
            chance *= balance.antiFarmDropMultiplier
        }
        chance = chance.coerceAtMost(100.0)
        val roll = rng.nextDouble(0.0, 100.0)
        if (roll > chance) return DropOutcome()

        val entry = pickWeighted(dropTable, monster, tier) ?: return DropOutcome()
        val registryEntry = resolveRegistryEntry(entry, tier, monster) ?: return DropOutcome()
        val qty = if (registryEntry.template != null) 1 else rollQuantity(entry.minQty, entry.maxQty)
        if (qty <= 0) return DropOutcome()

        val baseRarity = if (antiExploit) {
            ItemRarity.COMMON
        } else {
            rollRarityInRange(entry.minRarity, entry.maxRarity)
        }
        val rolledRarity = if (registryEntry.template != null && !antiExploit) {
            rollEffectiveRarity(
                baseRarity = baseRarity,
                template = registryEntry,
                player = player,
                monster = monster,
                statsDropBonusPct = statsDropBonusPct,
                difficultyModifier = difficultyModifier
            )
        } else {
            baseRarity
        }
        val rare = registryEntry.rarity >= ItemRarity.RARE || rolledRarity >= ItemRarity.RARE

        return if (registryEntry.template != null) {
            val item = itemEngine.generateFromTemplate(registryEntry.template, monster.level, rolledRarity)
            DropOutcome(itemInstance = item, quantity = 1, rareDropped = rare)
        } else {
            DropOutcome(itemId = registryEntry.id, quantity = qty, rareDropped = rare)
        }
    }

    private fun pickWeighted(
        dropTable: DropTableDef,
        monster: MonsterInstance,
        tier: MapTierDef
    ): DropEntryDef? {
        val monsterTags = monsterTagSet(monster)
        val biomeId = tier.biomeId?.trim()?.lowercase()
        val tierId = tier.id.trim().lowercase()
        val entries = dropTable.entries
            .filter { it.chancePct > 0.0 }
            .filter { monster.level in it.minMonsterLevel..it.maxMonsterLevel }
            .filter { entry ->
                entry.requiredBiomeIds.isEmpty() || biomeId != null && entry.requiredBiomeIds.any { id ->
                    id.trim().lowercase() == biomeId
                }
            }
            .filter { entry ->
                biomeId == null || entry.blockedBiomeIds.none { id -> id.trim().lowercase() == biomeId }
            }
            .filter { entry ->
                entry.requiredTierIds.isEmpty() || entry.requiredTierIds.any { id ->
                    id.trim().lowercase() == tierId
                }
            }
            .filter { entry ->
                entry.blockedTierIds.none { id -> id.trim().lowercase() == tierId }
            }
            .filter { tier.dropTier in it.minMapDropTier..it.maxMapDropTier }
            .filter { entry -> entry.requiredMonsterTags.all { tag -> tag.lowercase() in monsterTags } }
            .filter { entry -> entry.blockedMonsterTags.none { tag -> tag.lowercase() in monsterTags } }
        if (entries.isEmpty()) return null
        val total = entries.sumOf { it.chancePct }.coerceAtLeast(0.0001)
        var roll = rng.nextDouble(0.0, total)
        for (entry in entries) {
            roll -= entry.chancePct
            if (roll <= 0.0) return entry
        }
        return entries.first()
    }

    private fun resolveRegistryEntry(
        entry: DropEntryDef,
        tier: MapTierDef,
        monster: MonsterInstance
    ): ItemRegistryEntry? {
        if (entry.itemId.isNotBlank()) {
            return itemRegistry.entry(entry.itemId)
        }
        if (entry.templateIds.isEmpty()) return null
        val templates = entry.templateIds
            .mapNotNull { itemRegistry.template(it) }
            .filter { it.dropTier <= (tier.dropTier + monster.stars.coerceAtMost(2)) }
            .filter { monster.level >= (it.minLevel - 5).coerceAtLeast(1) }
        if (templates.isEmpty()) return null
        val contextTags = lootContextTags(monster, tier)
        val weightedTemplates = templates
            .map { template ->
                template to templateSelectionWeight(
                    template = template,
                    tier = tier,
                    monster = monster,
                    contextTags = contextTags
                )
            }
            .filter { it.second > 0.0 }
        if (weightedTemplates.isEmpty()) return null
        val totalWeight = weightedTemplates.sumOf { (_, weight) ->
            max(1, weight.roundToInt())
        }
        var roll = rng.nextInt(totalWeight)
        for ((template, weight) in weightedTemplates) {
            roll -= max(1, weight.roundToInt())
            if (roll < 0) {
                return itemRegistry.entry(template.id)
            }
        }
        return itemRegistry.entry(weightedTemplates.first().first.id)
    }

    private fun rollQuantity(minQty: Int, maxQty: Int): Int {
        val min = minQty.coerceAtLeast(1)
        val max = max(maxQty, min)
        if (max == min) return min
        return rng.nextInt(min, max + 1)
    }

    private fun rollRarityInRange(min: ItemRarity, max: ItemRarity): ItemRarity {
        val values = ItemRarity.values().filter { it.ordinal in min.ordinal..max.ordinal }
        if (values.isEmpty()) return min
        val total = values.sumOf { it.weight }
        var roll = rng.nextInt(total)
        for (rarity in values) {
            roll -= rarity.weight
            if (roll < 0) return rarity
        }
        return values.first()
    }

    private fun rollEffectiveRarity(
        baseRarity: ItemRarity,
        template: ItemRegistryEntry,
        player: PlayerState,
        monster: MonsterInstance,
        statsDropBonusPct: Double,
        difficultyModifier: Double
    ): ItemRarity {
        var rarity = ItemRarity.clamp(baseRarity, template.rarity, template.maxRarity)
        val promoteChance = (
            player.baseAttributes.luk * 0.30 +
                statsDropBonusPct * 0.18 +
                monster.level * 0.05 +
                monster.stars * 4.5 +
                monster.rarity.ordinal * 4.0 +
                (difficultyModifier - 1.0) * 16.0
            ).coerceIn(0.0, 54.0)
        if (rng.nextDouble(0.0, 100.0) <= promoteChance) {
            rarity = ItemRarity.promote(rarity, 1)
        }
        val extraPromoteChance = (
            promoteChance * 0.24 +
                monster.stars * 1.8 +
                monster.rarity.ordinal * 1.5
            ).coerceIn(0.0, 18.0)
        if (rng.nextDouble(0.0, 100.0) <= extraPromoteChance) {
            rarity = ItemRarity.promote(rarity, 1)
        }
        return ItemRarity.clamp(rarity, template.rarity, template.maxRarity)
    }

    private fun monsterTagSet(monster: MonsterInstance): Set<String> {
        return buildSet {
            add(monster.family.lowercase())
            add(monster.baseType.lowercase())
            add(monster.monsterTypeId.lowercase())
            add(monster.archetypeId.lowercase())
            addAll(monster.tags.map { it.lowercase() })
        }
    }

    private fun lootContextTags(monster: MonsterInstance, tier: MapTierDef): Set<String> {
        val biomeId = tier.biomeId?.trim()?.lowercase()
        val tierId = tier.id.trim().lowercase()
        val levelBand = when {
            monster.level < 15 -> "band:early"
            monster.level < 30 -> "band:mid"
            monster.level < 50 -> "band:late"
            else -> "band:endgame"
        }
        return buildSet {
            addAll(monsterTagSet(monster))
            add("tier:$tierId")
            add(tierId)
            add("drop_tier:${tier.dropTier}")
            add(levelBand)
            add("rarity:${monster.rarity.name.lowercase()}")
            add("stars:${monster.stars.coerceAtLeast(0)}")
            biomeId?.let {
                add(it)
                add("biome:$it")
            }
        }
    }

    private fun templateSelectionWeight(
        template: rpg.model.ItemTemplateDef,
        tier: MapTierDef,
        monster: MonsterInstance,
        contextTags: Set<String>
    ): Double {
        var weight = template.dropWeight.coerceAtLeast(1).toDouble()
        val lootTags = template.lootTags
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        if (lootTags.isNotEmpty()) {
            val matches = lootTags.count { it in contextTags }
            val ratio = matches.toDouble() / lootTags.size.coerceAtLeast(1)
            weight *= when {
                matches == 0 -> 0.18
                ratio < 0.34 -> 0.75
                ratio < 0.67 -> 1.1
                else -> 1.45
            }
            if (lootTags.any { it == "drop_tier:${tier.dropTier}" }) {
                weight *= 1.12
            }
            val biomeId = tier.biomeId?.trim()?.lowercase()
            if (biomeId != null && lootTags.any { it == biomeId || it == "biome:$biomeId" }) {
                weight *= 1.15
            }
        }
        val levelDistance = kotlin.math.abs(monster.level - template.minLevel)
        if (levelDistance <= 6) {
            weight *= 1.08
        } else if (levelDistance >= 25) {
            weight *= 0.82
        }
        return weight.coerceAtLeast(0.0)
    }
}
