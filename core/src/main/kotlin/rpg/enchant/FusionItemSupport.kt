package rpg.enchant

import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random
import rpg.item.ItemRarity
import rpg.model.AffixDef
import rpg.model.ItemDef
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.registry.ItemRegistry

internal class FusionItemSupport(
    private val itemRegistry: ItemRegistry,
    private val itemSupport: EnchantItemSupport,
    private val fusionConfig: FusionConfig,
    private val affixesByName: Map<String, AffixDef>,
    private val rng: Random
) {
    fun resolveMode(left: FusionSource, right: FusionSource): FusionMode? {
        val leftStone = isEnchantStone(left.item)
        val rightStone = isEnchantStone(right.item)
        if (left.item.type == ItemType.EQUIPMENT && right.item.type == ItemType.EQUIPMENT) {
            if (left.item.templateId != right.item.templateId) return null
            return FusionMode.EQUIPMENT_EQUIPMENT
        }
        if (leftStone && rightStone) return FusionMode.STONE_STONE
        if ((leftStone && right.item.type == ItemType.EQUIPMENT) || (rightStone && left.item.type == ItemType.EQUIPMENT)) {
            return FusionMode.STONE_EQUIPMENT
        }
        return null
    }

    fun resolveSource(
        player: rpg.model.PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemId: String
    ): FusionSource? {
        if (!player.inventory.contains(itemId)) return null
        return resolveSourceFromSnapshot(itemId, itemInstances)
    }

    fun resolveSourceFromSnapshot(itemId: String, itemInstances: Map<String, ItemInstance>): FusionSource? {
        val existing = itemInstances[itemId]
        if (existing != null) return FusionSource(existing)
        val def = itemRegistry.item(itemId) ?: return null
        return FusionSource(materializeFromDefinition(def))
    }

    fun createEnchantStone(enchantLevel: Int, sourceLevel: Int, rarity: ItemRarity): ItemInstance {
        val level = enchantLevel.coerceIn(0, fusionConfig.maxEnchantLevel)
        val stoneTemplateId = fusionConfig.enchantStoneTemplateIdForLevel(level)
        val stoneDef = itemRegistry.item(stoneTemplateId)
        val valueBase = stoneDef?.value ?: 30
        val value = valueBase + (level * fusionConfig.stoneValuePerEnchantLevel.coerceAtLeast(0))
        val tags = (stoneDef?.tags ?: emptyList()) + "enchant_stone"
        return ItemInstance(
            id = UUID.randomUUID().toString(),
            templateId = stoneDef?.id ?: stoneTemplateId,
            name = stoneDef?.name ?: "Pedra de Encantamento",
            level = sourceLevel.coerceAtLeast(1),
            minLevel = sourceLevel.coerceAtLeast(1),
            rarity = rarity,
            type = ItemType.MATERIAL,
            tags = tags,
            value = value,
            description = stoneDef?.description ?: "Pedra que concentra um nivel exato de encantamento.",
            enchantLevel = level
        )
    }

    fun buildEquipmentFusionOutput(left: ItemInstance, right: ItemInstance, outputLevel: Int): ItemInstance {
        val anchor = if (left.powerScore >= right.powerScore) left else right
        val counterpart = if (anchor.id == left.id) right else left
        val mergedAffixes = left.affixes + right.affixes
        val anchorBaseBonuses = anchor.enchantBaseBonuses ?: anchor.bonuses
        val anchorAffixBonuses = EnchantMathSupport.sumAffixBonuses(anchor.affixes, affixesByName)
        val baseIdentityBonuses = EnchantMathSupport.minusBonuses(anchorBaseBonuses, anchorAffixBonuses)
        val mergedAffixBonuses = EnchantMathSupport.sumAffixBonuses(mergedAffixes, affixesByName)
        val mergedBonuses = baseIdentityBonuses + mergedAffixBonuses
        val mergedEffects = EnchantMathSupport.mergeEffects(
            EnchantMathSupport.sumAffixEffects(mergedAffixes, affixesByName),
            EnchantMathSupport.mergeEffects(anchor.effects, counterpart.effects)
        )

        val basePowerLeft = left.enchantBasePowerScore ?: left.powerScore
        val basePowerRight = right.enchantBasePowerScore ?: right.powerScore
        val averageBasePower = ((basePowerLeft + basePowerRight) / 2.0).coerceAtLeast(1.0)
        val variancePct = fusionConfig.equipmentPowerVariancePct.coerceIn(0.0, 0.25)
        val varianceMultiplier = 1.0 + rng.nextDouble(-variancePct, variancePct)
        val fusedBasePower = (averageBasePower * varianceMultiplier).roundToInt().coerceAtLeast(1)
        val baseItem = anchor.copy(
            id = UUID.randomUUID().toString(),
            level = maxOf(left.level, right.level),
            minLevel = maxOf(left.minLevel, right.minLevel),
            rarity = maxRarity(left, right),
            qualityRollPct = ((left.qualityRollPct + right.qualityRollPct) / 2.0).roundToInt(),
            powerScore = fusedBasePower,
            bonuses = mergedBonuses,
            effects = mergedEffects,
            affixes = mergedAffixes,
            enchantLevel = 0,
            enchantBaseBonuses = mergedBonuses,
            enchantBasePowerScore = fusedBasePower
        )
        return itemSupport.applyEnchantLevel(baseItem, outputLevel)
    }

    fun maxRarity(left: ItemInstance, right: ItemInstance): ItemRarity {
        return if (left.rarity.ordinal >= right.rarity.ordinal) left.rarity else right.rarity
    }

    private fun materializeFromDefinition(def: ItemDef): ItemInstance {
        return ItemInstance(
            id = def.id,
            templateId = def.id,
            name = def.name,
            level = def.minLevel.coerceAtLeast(1),
            minLevel = def.minLevel.coerceAtLeast(1),
            rarity = def.rarity,
            type = def.type,
            slot = def.slot,
            twoHanded = def.twoHanded,
            tags = def.tags,
            bonuses = def.bonuses,
            effects = def.effects,
            value = def.value,
            description = def.description
        )
    }

    private fun isEnchantStone(item: ItemInstance): Boolean {
        if (item.type != ItemType.MATERIAL) return false
        val stoneTemplateIds = fusionConfig.enchantStoneTemplateIds()
        if (item.templateId in stoneTemplateIds) return true
        if (item.id in stoneTemplateIds) return true
        return item.tags.any { it.trim().equals("enchant_stone", ignoreCase = true) }
    }
}

internal data class FusionSource(
    val item: ItemInstance
)

internal fun consumeSpecificItem(
    inventory: MutableList<String>,
    itemInstances: MutableMap<String, ItemInstance>,
    itemId: String
): Boolean {
    val index = inventory.indexOf(itemId)
    if (index < 0) return false
    inventory.removeAt(index)
    if (itemInstances.containsKey(itemId)) {
        itemInstances.remove(itemId)
    }
    return true
}
