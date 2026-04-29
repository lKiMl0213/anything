package rpg.progression

import kotlin.math.ceil
import rpg.model.CraftDiscipline
import rpg.model.GatheringType
import rpg.model.ItemInstance
import rpg.model.PermanentUpgradeCostDef
import rpg.model.PermanentUpgradeDef
import rpg.model.PermanentUpgradeEffectType
import rpg.model.PermanentUpgradeValueMode
import rpg.model.PlayerState
import rpg.model.ShopCurrency
import rpg.model.SkillType
import rpg.registry.ItemRegistry

data class PermanentUpgradePurchaseResult(
    val success: Boolean,
    val message: String,
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val spentGold: Int = 0,
    val spentCash: Int = 0,
    val consumedItemId: String? = null,
    val consumedItemQty: Int = 0
)

class PermanentUpgradeService(
    definitions: Map<String, PermanentUpgradeDef>,
    private val itemRegistry: ItemRegistry
) {
    private val defs = definitions.values
        .filter { it.enabled }
        .associateBy { it.id.lowercase() }

    fun definitions(): List<PermanentUpgradeDef> = defs.values.sortedBy { it.name }

    fun definition(id: String): PermanentUpgradeDef? = defs[id.trim().lowercase()]

    fun currentLevel(player: PlayerState, upgradeId: String): Int {
        val key = upgradeId.trim().lowercase()
        val max = definition(key)?.maxLevel ?: 0
        return player.permanentUpgradeLevels[key]?.coerceIn(0, max) ?: 0
    }

    fun currentEffectValue(player: PlayerState, upgradeId: String): Double {
        val def = definition(upgradeId) ?: return 0.0
        return valueForDefinition(player, def)
    }

    fun nextEffectValue(player: PlayerState, upgradeId: String): Double? {
        val def = definition(upgradeId) ?: return null
        val current = currentLevel(player, def.id)
        if (current >= def.maxLevel) return null
        val levels = player.permanentUpgradeLevels.toMutableMap()
        levels[def.id] = current + 1
        val previewPlayer = player.copy(permanentUpgradeLevels = levels)
        return valueForDefinition(previewPlayer, def)
    }

    fun valueForEffect(player: PlayerState, effectType: PermanentUpgradeEffectType): Double {
        val matching = defs.values.filter { it.effectType == effectType }
        if (matching.isEmpty()) return 0.0
        return matching.maxOf { def -> valueForDefinition(player, def) }
    }

    fun craftBatchLimit(player: PlayerState): Int {
        val base = 10
        val extra = valueForEffect(player, PermanentUpgradeEffectType.CRAFT_BATCH_BONUS)
        return (base + extra.toInt()).coerceIn(base, 100)
    }

    fun professionXpMultiplier(player: PlayerState, skillType: SkillType): Double {
        val applies = when (skillType) {
            SkillType.GATHERING, SkillType.MINING, SkillType.FISHING, SkillType.BLACKSMITH -> true
            else -> false
        }
        if (!applies) return 1.0
        val bonusPct = valueForEffect(player, PermanentUpgradeEffectType.PROFESSION_XP_BOOST)
        return 1.0 + (bonusPct / 100.0)
    }

    fun combatXpMultiplier(player: PlayerState): Double {
        val bonusPct = valueForEffect(player, PermanentUpgradeEffectType.COMBAT_XP_BOOST)
        return 1.0 + (bonusPct / 100.0)
    }

    fun monsterRarityBonusPct(player: PlayerState): Double {
        return valueForEffect(player, PermanentUpgradeEffectType.MONSTER_RARITY_BONUS).coerceIn(0.0, 60.0)
    }

    fun questItemKeepChancePct(player: PlayerState): Double {
        return valueForEffect(player, PermanentUpgradeEffectType.QUEST_ITEM_KEEP_CHANCE).coerceIn(0.0, 100.0)
    }

    fun gatherDoubleBonusPct(player: PlayerState, type: GatheringType): Double {
        val effect = when (type) {
            GatheringType.FISHING -> PermanentUpgradeEffectType.FISHING_DOUBLE_CHANCE
            GatheringType.HERBALISM -> PermanentUpgradeEffectType.HERBALISM_DOUBLE_CHANCE
            GatheringType.MINING -> PermanentUpgradeEffectType.MINING_DOUBLE_CHANCE
            GatheringType.WOODCUTTING -> PermanentUpgradeEffectType.WOODCUTTING_DOUBLE_CHANCE
        }
        return valueForEffect(player, effect).coerceIn(0.0, 100.0)
    }

    fun craftingCostReductionPct(player: PlayerState, discipline: CraftDiscipline): Double {
        val effect = when (discipline) {
            CraftDiscipline.FORGE -> PermanentUpgradeEffectType.FORGE_COST_REDUCTION
            CraftDiscipline.COOKING -> PermanentUpgradeEffectType.COOKING_COST_REDUCTION
            CraftDiscipline.ALCHEMY -> PermanentUpgradeEffectType.ALCHEMY_COST_REDUCTION
        }
        return valueForEffect(player, effect).coerceIn(0.0, 90.0)
    }

    fun tavernCostReductionPct(player: PlayerState): Double {
        return valueForEffect(player, PermanentUpgradeEffectType.TAVERN_COST_REDUCTION).coerceIn(0.0, 90.0)
    }

    fun quiverCapacityBonus(player: PlayerState): Int {
        return valueForEffect(player, PermanentUpgradeEffectType.QUIVER_CAPACITY_BONUS).toInt().coerceAtLeast(0)
    }

    fun nextLevelCosts(
        player: PlayerState,
        upgradeId: String,
        shopCurrency: ShopCurrency
    ): List<PermanentUpgradeCostDef> {
        val def = definition(upgradeId) ?: return emptyList()
        val level = currentLevel(player, def.id)
        val next = def.levels.firstOrNull { it.level == level + 1 } ?: return emptyList()
        return next.costs.filter { cost ->
            cost.shopCurrencies.isEmpty() || cost.shopCurrencies.contains(shopCurrency)
        }
    }

    fun effectLabel(def: PermanentUpgradeDef, value: Double): String {
        return when (def.valueMode) {
            PermanentUpgradeValueMode.PERCENT -> "${value.toInt()}%"
            PermanentUpgradeValueMode.FLAT -> value.toInt().toString()
        }
    }

    fun purchase(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        upgradeId: String,
        costId: String,
        shopCurrency: ShopCurrency
    ): PermanentUpgradePurchaseResult {
        val def = definition(upgradeId) ?: return PermanentUpgradePurchaseResult(
            success = false,
            message = "Aprimoramento nao encontrado.",
            player = player,
            itemInstances = itemInstances
        )
        val current = currentLevel(player, def.id)
        if (current >= def.maxLevel) {
            return PermanentUpgradePurchaseResult(
                success = false,
                message = "${def.name} ja esta no nivel maximo.",
                player = player,
                itemInstances = itemInstances
            )
        }
        val nextLevel = def.levels.firstOrNull { it.level == current + 1 }
            ?: return PermanentUpgradePurchaseResult(false, "Dados de nivel invalido.", player, itemInstances)
        val selectedCost = nextLevel.costs.firstOrNull {
            it.id.equals(costId, ignoreCase = true) &&
                (it.shopCurrencies.isEmpty() || it.shopCurrencies.contains(shopCurrency))
        } ?: return PermanentUpgradePurchaseResult(false, "Opcao de custo indisponivel.", player, itemInstances)

        if (player.gold < selectedCost.goldCost) {
            return PermanentUpgradePurchaseResult(false, "Ouro insuficiente.", player, itemInstances)
        }
        if (player.premiumCash < selectedCost.cashCost) {
            return PermanentUpgradePurchaseResult(false, "CASH insuficiente.", player, itemInstances)
        }

        val inventory = player.inventory.toMutableList()
        val updatedInstances = itemInstances.toMutableMap()
        if (!consumeRequiredItem(inventory, updatedInstances, selectedCost)) {
            val reqName = selectedCost.requiredItemId?.let { itemRegistry.entry(it)?.name ?: it } ?: "item"
            val reqQty = selectedCost.requiredItemQty.coerceAtLeast(1)
            return PermanentUpgradePurchaseResult(
                success = false,
                message = "Recursos insuficientes: $reqName x$reqQty.",
                player = player,
                itemInstances = itemInstances
            )
        }

        val levels = player.permanentUpgradeLevels.toMutableMap()
        levels[def.id] = nextLevel.level
        val updatedPlayer = player.copy(
            gold = player.gold - selectedCost.goldCost,
            premiumCash = player.premiumCash - selectedCost.cashCost,
            inventory = inventory,
            permanentUpgradeLevels = levels
        )
        val currentValue = effectLabel(def, valueForDefinition(updatedPlayer, def))
        return PermanentUpgradePurchaseResult(
            success = true,
            message = "${def.name} evoluiu para Nv ${nextLevel.level}/${def.maxLevel}. Efeito atual: $currentValue.",
            player = updatedPlayer,
            itemInstances = updatedInstances,
            spentGold = selectedCost.goldCost,
            spentCash = selectedCost.cashCost,
            consumedItemId = selectedCost.requiredItemId,
            consumedItemQty = selectedCost.requiredItemQty.coerceAtLeast(0)
        )
    }

    private fun valueForDefinition(player: PlayerState, def: PermanentUpgradeDef): Double {
        val level = currentLevel(player, def.id)
        if (level <= 0) return 0.0
        if (def.effectType == PermanentUpgradeEffectType.CRAFT_BATCH_BONUS ||
            def.effectType == PermanentUpgradeEffectType.QUIVER_CAPACITY_BONUS
        ) {
            return def.levels
                .sortedBy { it.level }
                .take(level)
                .sumOf { it.value }
        }
        return def.levels.firstOrNull { it.level == level }?.value ?: 0.0
    }

    private fun consumeRequiredItem(
        inventory: MutableList<String>,
        itemInstances: MutableMap<String, ItemInstance>,
        cost: PermanentUpgradeCostDef
    ): Boolean {
        val requiredId = cost.requiredItemId?.trim()?.takeIf { it.isNotBlank() } ?: return true
        val requiredQty = cost.requiredItemQty.coerceAtLeast(1)
        val matching = inventory.filter { id ->
            id == requiredId || itemInstances[id]?.templateId == requiredId
        }
        if (matching.size < requiredQty) return false

        val toConsume = matching.take(requiredQty)
        for (itemId in toConsume) {
            val index = inventory.indexOf(itemId)
            if (index >= 0) {
                inventory.removeAt(index)
            }
            if (itemInstances.containsKey(itemId)) {
                itemInstances.remove(itemId)
            }
        }
        return true
    }

    fun discountedCost(baseCost: Int, reductionPct: Double): Int {
        if (baseCost <= 0) return 0
        val multiplier = (1.0 - reductionPct / 100.0).coerceIn(0.1, 1.0)
        return ceil(baseCost * multiplier).toInt().coerceAtLeast(1)
    }
}
