package rpg.enchant

import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt
import rpg.model.Bonuses
import rpg.model.ItemDef
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.registry.ItemRegistry

internal class EnchantItemSupport(
    private val itemRegistry: ItemRegistry,
    private val config: EnchantConfig
) {
    fun goldCostFor(item: ItemInstance): Int {
        val rarityMultiplier = config.rarityCostMultiplier(item.rarity.name)
        val base = config.goldBaseCost + (item.level * config.goldPerItemLevel)
        val levelMultiplier = 1.0 + (item.enchantLevel * config.goldPerEnchantLevelMultiplier)
        return ceil(base * rarityMultiplier * levelMultiplier).toInt().coerceAtLeast(1)
    }

    fun antiExploitXpMultiplier(itemLevel: Int): Double {
        val minFullXpLevel = config.antiExploitMinItemLevelForFullXp.coerceAtLeast(1)
        if (itemLevel >= minFullXpLevel) return 1.0
        val ratio = itemLevel.toDouble() / minFullXpLevel.toDouble()
        return ratio.coerceIn(config.antiExploitXpFloorMultiplier, 1.0)
    }

    fun applyEnchantLevel(item: ItemInstance, level: Int): ItemInstance {
        val targetLevel = level.coerceIn(0, config.maxEnchantLevel)
        val baseBonuses = item.enchantBaseBonuses ?: item.bonuses
        val basePower = item.enchantBasePowerScore ?: item.powerScore
        val growthPerLevel = config.growthPctFor(item.slot, item.type)
        val totalGrowth = growthPerLevel * targetLevel
        val enchantBonus = Bonuses(
            attributes = baseBonuses.attributes.scale(totalGrowth),
            derivedAdd = baseBonuses.derivedAdd.scale(totalGrowth),
            derivedMult = baseBonuses.derivedMult.scale(totalGrowth)
        )
        val updatedPower = (basePower * (1.0 + totalGrowth)).roundToInt().coerceAtLeast(basePower)
        return item.copy(
            bonuses = baseBonuses + enchantBonus,
            powerScore = updatedPower,
            enchantLevel = targetLevel,
            enchantBaseBonuses = baseBonuses,
            enchantBasePowerScore = basePower
        )
    }

    fun resolveTargetForPreview(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemId: String
    ): MaterializedTarget? {
        val existing = itemInstances[itemId]
        if (existing != null) {
            return MaterializedTarget(player, itemInstances, existing)
        }
        if (!player.inventory.contains(itemId)) return null
        val itemDef = itemRegistry.item(itemId) ?: return null
        if (itemDef.type != ItemType.EQUIPMENT) return null
        return MaterializedTarget(player, itemInstances, itemFromDefinition(itemDef, id = itemId))
    }

    fun materializeTarget(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemId: String
    ): MaterializedTarget? {
        val existing = itemInstances[itemId]
        if (existing != null) {
            if (!player.inventory.contains(existing.id)) return null
            return MaterializedTarget(player, itemInstances, existing)
        }
        if (!player.inventory.contains(itemId)) return null
        val itemDef = itemRegistry.item(itemId) ?: return null
        if (itemDef.type != ItemType.EQUIPMENT) return null
        val generatedId = UUID.randomUUID().toString()
        val generated = itemFromDefinition(itemDef, id = generatedId)
        val index = player.inventory.indexOf(itemId)
        if (index < 0) return null
        val mutableInventory = player.inventory.toMutableList()
        mutableInventory[index] = generatedId
        val mutableInstances = itemInstances.toMutableMap()
        mutableInstances[generatedId] = generated
        return MaterializedTarget(
            player = player.copy(inventory = mutableInventory),
            itemInstances = mutableInstances.toMap(),
            item = generated
        )
    }

    fun consumeFromInventory(
        inventory: MutableList<String>,
        itemInstances: MutableMap<String, ItemInstance>,
        targetTemplateId: String,
        quantity: Int
    ): Int {
        if (quantity <= 0) return 0
        val matches = inventory.filter { id ->
            id == targetTemplateId || itemInstances[id]?.templateId == targetTemplateId
        }.take(quantity)
        for (id in matches) {
            val index = inventory.indexOf(id)
            if (index >= 0) inventory.removeAt(index)
            if (itemInstances.containsKey(id)) {
                itemInstances.remove(id)
            }
        }
        return matches.size
    }

    fun removeItemReference(inventory: MutableList<String>, itemId: String) {
        val index = inventory.indexOf(itemId)
        if (index >= 0) inventory.removeAt(index)
    }

    private fun itemFromDefinition(def: ItemDef, id: String): ItemInstance {
        return ItemInstance(
            id = id,
            templateId = def.id,
            name = def.name,
            level = def.minLevel.coerceAtLeast(1),
            minLevel = def.minLevel.coerceAtLeast(1),
            rarity = def.rarity,
            qualityRollPct = 100,
            powerScore = 0,
            type = def.type,
            slot = def.slot,
            twoHanded = def.twoHanded,
            tags = def.tags,
            bonuses = def.bonuses,
            effects = def.effects,
            value = def.value,
            description = def.description,
            affixes = emptyList()
        )
    }
}

internal data class MaterializedTarget(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val item: ItemInstance
)
