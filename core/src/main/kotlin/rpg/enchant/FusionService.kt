package rpg.enchant

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random
import rpg.model.AffixDef
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.model.SkillType
import rpg.premium.PremiumSupport
import rpg.registry.ItemRegistry
import rpg.skills.SkillSystem

class FusionService(
    itemRegistry: ItemRegistry,
    private val skillSystem: SkillSystem,
    private val rng: Random,
    private val enchantConfig: EnchantConfig = EnchantConfig(),
    private val fusionConfig: FusionConfig = FusionConfig(),
    affixes: Map<String, AffixDef> = emptyMap(),
    private val chanceCalculator: EnchantChanceCalculator = EnchantChanceCalculator(enchantConfig)
) {
    private val affixesByName = affixes.values.associateBy { it.name.trim().lowercase() }
    private val itemSupport = EnchantItemSupport(itemRegistry, enchantConfig)
    private val fusionItemSupport = FusionItemSupport(
        itemRegistry = itemRegistry,
        itemSupport = itemSupport,
        fusionConfig = fusionConfig,
        affixesByName = affixesByName,
        rng = rng
    )

    fun preview(player: PlayerState, itemInstances: Map<String, ItemInstance>, request: FusionRequest): FusionPreview {
        val prepared = skillSystem.ensureProgress(player)
        val left = fusionItemSupport.resolveSource(prepared, itemInstances, request.slot1ItemId)
        val right = fusionItemSupport.resolveSource(prepared, itemInstances, request.slot2ItemId)
        val mode = left?.let { l -> right?.let { r -> fusionItemSupport.resolveMode(l, r) } }
        val reasons = validationReasons(prepared, request, left, right, mode)
        val leftItem = left?.item
        val rightItem = right?.item
        val levels = resolvedEnchantLevels(leftItem, rightItem)
        val levelPlan = calculateLevelPlan(levels.first, levels.second)
        val skillLevel = skillSystem.snapshot(prepared, SkillType.ENCHANTING).level
        val successChance = mode?.let { computeSuccessChance(levelPlan.baseLevel, skillLevel, it) } ?: 0.0
        val baseGoldCost = if (leftItem != null && rightItem != null && mode != null) {
            goldCostFor(leftItem, rightItem, levels.first, levels.second, mode)
        } else {
            0
        }
        val goldCost = applyProductionCostReduction(prepared, baseGoldCost)
        if (goldCost > prepared.gold) {
            reasons += "Ouro insuficiente (custo: $goldCost)."
        }
        val durationSeconds = (
            skillSystem.actionDurationSeconds(fusionConfig.baseDurationSeconds, skillLevel)
                .coerceAtLeast(fusionConfig.minDurationSeconds)
            ) * PremiumSupport.productionDurationMultiplier(prepared)
        return FusionPreview(
            slot1ItemId = request.slot1ItemId,
            slot2ItemId = request.slot2ItemId,
            slot1Label = leftItem?.name ?: request.slot1ItemId,
            slot2Label = rightItem?.name ?: request.slot2ItemId,
            mode = mode,
            baseEnchantLevel = levelPlan.baseLevel,
            successNormalEnchantLevel = levelPlan.normalLevel,
            successUpgradeEnchantLevel = levelPlan.upgradeLevel,
            failureMinEnchantLevel = levelPlan.failureMinLevel,
            failureMaxEnchantLevel = levelPlan.failureMaxLevel,
            successChancePct = successChance,
            goldCost = goldCost,
            durationSeconds = durationSeconds,
            blockedReasons = reasons
        )
    }

    fun fuse(player: PlayerState, itemInstances: Map<String, ItemInstance>, request: FusionRequest): FusionExecutionResult {
        val prepared = skillSystem.ensureProgress(player)
        val initialPreview = preview(prepared, itemInstances, request)
        val mode = initialPreview.mode
        if (!initialPreview.available || mode == null) {
            return FusionExecutionResult(
                success = false,
                message = initialPreview.blockedReasons.joinToString(" ").ifBlank { "Nao foi possivel realizar a fusao." },
                player = prepared,
                itemInstances = itemInstances,
                preview = initialPreview
            )
        }

        val left = fusionItemSupport.resolveSourceFromSnapshot(request.slot1ItemId, itemInstances)
            ?: return failedResult(prepared, itemInstances, initialPreview, "Falha ao resolver slot 1.")
        val right = fusionItemSupport.resolveSourceFromSnapshot(request.slot2ItemId, itemInstances)
            ?: return failedResult(prepared, itemInstances, initialPreview, "Falha ao resolver slot 2.")

        val mutableInventory = prepared.inventory.toMutableList()
        val mutableInstances = itemInstances.toMutableMap()
        if (!consumeSpecificItem(mutableInventory, mutableInstances, request.slot1ItemId) ||
            !consumeSpecificItem(mutableInventory, mutableInstances, request.slot2ItemId)
        ) {
            return failedResult(prepared, itemInstances, initialPreview, "Itens de fusao indisponiveis no inventario.")
        }

        val levels = resolvedEnchantLevels(left.item, right.item)
        val levelPlan = calculateLevelPlan(levels.first, levels.second)
        val success = rng.nextDouble(0.0, 100.0) <= initialPreview.successChancePct
        val outputLevel = resolveOutputLevel(success, levelPlan)
        val outputItem = when {
            !success -> fusionItemSupport.createEnchantStone(outputLevel, maxOf(left.item.level, right.item.level), fusionItemSupport.maxRarity(left.item, right.item))
            mode == FusionMode.EQUIPMENT_EQUIPMENT -> fusionItemSupport.buildEquipmentFusionOutput(left.item, right.item, outputLevel)
            mode == FusionMode.STONE_STONE -> fusionItemSupport.createEnchantStone(outputLevel, maxOf(left.item.level, right.item.level), fusionItemSupport.maxRarity(left.item, right.item))
            else -> {
                val equipment = if (left.item.type == ItemType.EQUIPMENT) left.item else right.item
                itemSupport.applyEnchantLevel(equipment.copy(id = java.util.UUID.randomUUID().toString()), outputLevel)
            }
        }
        mutableInstances[outputItem.id] = outputItem
        mutableInventory += outputItem.id

        var updatedPlayer = prepared.copy(
            inventory = mutableInventory,
            gold = (prepared.gold - initialPreview.goldCost).coerceAtLeast(0)
        )
        val xp = skillSystem.gainXp(
            player = updatedPlayer,
            skill = SkillType.ENCHANTING,
            baseXp = (fusionConfig.attemptBaseXp + if (success) fusionConfig.successBonusXp else 0.0) *
                PremiumSupport.skillXpMultiplier(updatedPlayer),
            difficulty = 1.0 + (levelPlan.baseLevel * fusionConfig.xpPerEnchantLevelDifficulty),
            tier = 1
        )
        updatedPlayer = xp.player

        val successLabel = if (success) "Fusao concluida." else "Fusao instavel."
        val resultLabel = if (outputItem.type == ItemType.EQUIPMENT) {
            "${outputItem.name} +${outputItem.enchantLevel}"
        } else {
            "${outputItem.name} +$outputLevel"
        }
        return FusionExecutionResult(
            success = success,
            message = "$successLabel Resultado: $resultLabel",
            player = updatedPlayer,
            itemInstances = mutableInstances.toMap(),
            outputItemId = outputItem.id,
            outputEnchantLevel = outputLevel,
            goldSpent = initialPreview.goldCost,
            gainedXp = xp.gainedXp,
            skillSnapshot = xp.snapshot,
            preview = initialPreview
        )
    }

    private fun validationReasons(
        player: PlayerState,
        request: FusionRequest,
        left: FusionSource?,
        right: FusionSource?,
        mode: FusionMode?
    ): MutableList<String> {
        val reasons = mutableListOf<String>()
        if (left == null) reasons += "Slot 1 invalido ou fora do inventario."
        if (right == null) reasons += "Slot 2 invalido ou fora do inventario."
        if (mode == null && left != null && right != null) {
            reasons += "Combinacao invalida. Use equipamento+equipamento (mesmo template), pedra+pedra ou pedra+equipamento."
        }
        if (request.slot1ItemId == request.slot2ItemId && player.inventory.count { it == request.slot1ItemId } < 2) {
            reasons += "Voce precisa de 2 unidades para usar o mesmo item nos dois slots."
        }
        val minEquipmentLevel = fusionConfig.minimumEquipmentLevel.coerceAtLeast(1)
        val equipLowLevel = listOfNotNull(left?.item, right?.item)
            .any { it.type == ItemType.EQUIPMENT && it.level < minEquipmentLevel }
        if (equipLowLevel) reasons += "Equipamentos precisam ser nivel $minEquipmentLevel+ para fusao."
        return reasons
    }

    private fun resolveOutputLevel(success: Boolean, levelPlan: FusionLevelPlan): Int {
        if (!success) {
            if (levelPlan.failureMaxLevel <= levelPlan.failureMinLevel) return levelPlan.failureMinLevel
            return rng.nextInt(levelPlan.failureMinLevel, levelPlan.failureMaxLevel + 1)
        }
        val upgradeChance = fusionConfig.upgradeChanceForBaseLevel(levelPlan.baseLevel)
        val rolledUpgrade = rng.nextDouble(0.0, 100.0) <= upgradeChance
        return if (rolledUpgrade && levelPlan.upgradeLevel > levelPlan.normalLevel) {
            levelPlan.upgradeLevel
        } else {
            levelPlan.normalLevel
        }
    }

    private fun resolvedEnchantLevels(left: ItemInstance?, right: ItemInstance?): Pair<Int, Int> {
        return (left?.enchantLevel ?: 0).coerceAtLeast(0) to (right?.enchantLevel ?: 0).coerceAtLeast(0)
    }

    private fun calculateLevelPlan(leftEnchant: Int, rightEnchant: Int): FusionLevelPlan {
        val base = floor((leftEnchant + rightEnchant) / 2.0).toInt().coerceIn(0, fusionConfig.maxEnchantLevel)
        val successCap = minOf(fusionConfig.maxEnchantLevel, maxOf(leftEnchant, rightEnchant) + 1)
        val upgrade = minOf(successCap, base + 1)
        val normal = base.coerceIn(0, upgrade)
        val failurePenalty = fusionConfig.failurePenaltyForBaseLevel(base)
        val failMin = (base - failurePenalty).coerceAtLeast(fusionConfig.failureStoneLevelFloor)
        val failMax = (base - 1).coerceAtLeast(fusionConfig.failureStoneLevelFloor)
        return FusionLevelPlan(
            baseLevel = base,
            normalLevel = normal,
            upgradeLevel = upgrade,
            failureMinLevel = minOf(failMin, failMax),
            failureMaxLevel = maxOf(failMin, failMax)
        )
    }

    private fun computeSuccessChance(baseLevel: Int, skillLevel: Int, mode: FusionMode): Double {
        val chance = chanceCalculator.calculate(
            currentEnchantLevel = baseLevel.coerceIn(0, enchantConfig.maxEnchantLevel),
            enhancementRunes = 0,
            useProtectionRune = false,
            enchantSkillLevel = skillLevel
        )
        val adjusted = chance.finalSuccessChancePct * fusionConfig.successMultiplier(mode)
        return adjusted.coerceIn(fusionConfig.minSuccessChancePct, fusionConfig.maxSuccessChancePct)
    }

    private fun goldCostFor(left: ItemInstance, right: ItemInstance, leftLevel: Int, rightLevel: Int, mode: FusionMode): Int {
        val baseCost = fusionConfig.goldBaseCost +
            (maxOf(left.level, right.level) * fusionConfig.goldPerItemLevel) +
            (maxOf(leftLevel, rightLevel) * fusionConfig.goldPerEnchantLevel)
        val rarityMultiplier = maxOf(
            fusionConfig.rarityCostMultiplier(left.rarity.name),
            fusionConfig.rarityCostMultiplier(right.rarity.name)
        )
        return ceil(baseCost * rarityMultiplier * fusionConfig.costMultiplier(mode)).toInt().coerceAtLeast(1)
    }

    private fun failedResult(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        preview: FusionPreview,
        message: String
    ): FusionExecutionResult {
        return FusionExecutionResult(
            success = false,
            message = message,
            player = player,
            itemInstances = itemInstances,
            preview = preview
        )
    }

    private fun applyProductionCostReduction(player: PlayerState, baseCost: Int): Int {
        if (baseCost <= 0) return 0
        val multiplier = (1.0 - PremiumSupport.productionCostReductionPct(player) / 100.0).coerceIn(0.1, 1.0)
        return ceil(baseCost * multiplier).toInt().coerceAtLeast(1)
    }
}

private data class FusionLevelPlan(
    val baseLevel: Int,
    val normalLevel: Int,
    val upgradeLevel: Int,
    val failureMinLevel: Int,
    val failureMaxLevel: Int
)
