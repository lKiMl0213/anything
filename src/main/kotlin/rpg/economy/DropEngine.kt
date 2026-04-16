package rpg.economy

import kotlin.math.max
import kotlin.random.Random
import rpg.item.ItemEngine
import rpg.item.ItemRarity
import rpg.model.DropTableDef
import rpg.model.GameBalanceDef
import rpg.model.ItemInstance
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.monster.MonsterInstance
import rpg.registry.DropTableRegistry
import rpg.registry.ItemRegistry

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
        val baseChance = dropTable.baseChancePct + statsDropBonusPct
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

        val entry = pickWeighted(dropTable) ?: return DropOutcome()
        val qty = rollQuantity(entry.minQty, entry.maxQty)
        if (qty <= 0) return DropOutcome()

        val registryEntry = itemRegistry.entry(entry.itemId)
            ?: error("Item nao encontrado no registry: ${entry.itemId}")
        val rarityRange = if (antiExploit) {
            ItemRarity.COMMON to ItemRarity.COMMON
        } else {
            entry.minRarity to entry.maxRarity
        }
        val rolledRarity = rollRarityInRange(rarityRange.first, rarityRange.second)
        val rare = rolledRarity >= ItemRarity.RARE
        return if (registryEntry.template != null) {
            val item = itemEngine.generateFromTemplate(registryEntry.template, monster.level, rolledRarity)
            DropOutcome(itemInstance = item, quantity = 1, rareDropped = rare)
        } else {
            DropOutcome(itemId = registryEntry.id, quantity = qty, rareDropped = rare)
        }
    }

    private fun pickWeighted(dropTable: DropTableDef): rpg.model.DropEntryDef? {
        val entries = dropTable.entries.filter { it.chancePct > 0.0 }
        if (entries.isEmpty()) return null
        val total = entries.sumOf { it.chancePct }.coerceAtLeast(0.0001)
        var roll = rng.nextDouble(0.0, total)
        for (entry in entries) {
            roll -= entry.chancePct
            if (roll <= 0.0) return entry
        }
        return entries.first()
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
}
