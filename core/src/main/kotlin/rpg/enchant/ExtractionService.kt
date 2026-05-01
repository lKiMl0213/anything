package rpg.enchant

import java.util.UUID
import kotlin.math.ceil
import kotlin.random.Random
import rpg.item.ItemRarity
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.model.SkillType
import rpg.registry.ItemRegistry
import rpg.skills.SkillSystem

class ExtractionService(
    private val itemRegistry: ItemRegistry,
    private val skillSystem: SkillSystem,
    private val rng: Random,
    private val enchantConfig: EnchantConfig = EnchantConfig(),
    private val extractionConfig: ExtractionConfig = ExtractionConfig(),
    private val chanceCalculator: EnchantChanceCalculator = EnchantChanceCalculator(enchantConfig)
) {
    private val itemSupport = EnchantItemSupport(itemRegistry, enchantConfig)
    private val resourceSelector = ExtractionResourceSelector(extractionConfig)

    fun preview(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        request: ExtractionRequest
    ): ExtractionPreview {
        val prepared = skillSystem.ensureProgress(player)
        val target = itemSupport.resolveTargetForPreview(prepared, itemInstances, request.itemId)
        val item = target?.item
        val reasons = mutableListOf<String>()
        if (item == null) {
            reasons += "Item alvo nao encontrado no inventario."
        }
        if (item != null) {
            if (item.type != ItemType.EQUIPMENT) reasons += "Apenas equipamentos podem passar por extracao."
            if (item.level < extractionConfig.minimumItemLevel) {
                reasons += "Item precisa de nivel ${extractionConfig.minimumItemLevel}+."
            }
            if (item.enchantLevel <= 0) reasons += "O item precisa estar encantado (+1 ou mais)."
        }

        val skillLevel = skillSystem.snapshot(prepared, SkillType.ENCHANTING).level
        val chanceBaseLevel = (item?.enchantLevel ?: 0).coerceIn(0, enchantConfig.maxEnchantLevel)
        val baseChance = chanceCalculator.calculate(
            currentEnchantLevel = chanceBaseLevel,
            enhancementRunes = 0,
            useProtectionRune = false,
            enchantSkillLevel = skillLevel
        ).finalSuccessChancePct
        val selectedRemovalScrollId = if (request.useRemovalScroll) {
            resourceSelector.selectRemovalScrollId(prepared, itemInstances)
        } else {
            null
        }
        val successChance = adjustChance(
            baseChance = baseChance,
            usingRemovalScroll = request.useRemovalScroll,
            removalScrollItemId = selectedRemovalScrollId
        )

        if (request.useRemovalScroll) {
            val owned = resourceSelector.ownedCount(prepared.inventory, itemInstances, extractionConfig.removalScrollItemIds())
            if (owned <= 0) reasons += "Pergaminho de remocao indisponivel."
        }
        if (request.useProtectionScroll) {
            val owned = resourceSelector.ownedCount(prepared.inventory, itemInstances, extractionConfig.protectionScrollItemIds())
            if (owned <= 0) reasons += "Pergaminho de protecao indisponivel."
        }

        val cost = item?.let { goldCostFor(it) } ?: 0
        if (cost > prepared.gold) reasons += "Ouro insuficiente (custo: $cost)."

        val duration = skillSystem.actionDurationSeconds(extractionConfig.baseDurationSeconds, skillLevel)
            .coerceAtLeast(extractionConfig.minDurationSeconds)
        return ExtractionPreview(
            itemId = request.itemId,
            itemName = item?.name ?: request.itemId,
            currentEnchantLevel = item?.enchantLevel ?: 0,
            stoneEnchantLevel = (item?.enchantLevel ?: 0).coerceAtLeast(0),
            successChancePct = successChance,
            goldCost = cost,
            durationSeconds = duration,
            useRemovalScroll = request.useRemovalScroll,
            useProtectionScroll = request.useProtectionScroll,
            blockedReasons = reasons
        )
    }

    fun extract(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        request: ExtractionRequest
    ): ExtractionExecutionResult {
        val prepared = skillSystem.ensureProgress(player)
        val initialPreview = preview(prepared, itemInstances, request)
        if (!initialPreview.available) {
            return ExtractionExecutionResult(
                success = false,
                message = initialPreview.blockedReasons.joinToString(" ").ifBlank { "Extracao indisponivel." },
                player = prepared,
                itemInstances = itemInstances,
                preview = initialPreview
            )
        }

        val materialized = itemSupport.materializeTarget(prepared, itemInstances, request.itemId)
            ?: return ExtractionExecutionResult(
                success = false,
                message = "Item alvo nao encontrado.",
                player = prepared,
                itemInstances = itemInstances,
                preview = initialPreview
            )
        val effectiveRequest = request.copy(itemId = materialized.item.id)
        val preview = preview(materialized.player, materialized.itemInstances, effectiveRequest)
        if (!preview.available) {
            return ExtractionExecutionResult(
                success = false,
                message = preview.blockedReasons.joinToString(" ").ifBlank { "Extracao indisponivel." },
                player = materialized.player,
                itemInstances = materialized.itemInstances,
                preview = preview
            )
        }

        val mutableInventory = materialized.player.inventory.toMutableList()
        val mutableInstances = materialized.itemInstances.toMutableMap()
        var mutablePlayer = materialized.player.copy(gold = (materialized.player.gold - preview.goldCost).coerceAtLeast(0))
        val selectedRemovalScrollId = if (effectiveRequest.useRemovalScroll) {
            resourceSelector.selectRemovalScrollId(materialized.player, materialized.itemInstances)
        } else {
            null
        }
        val selectedProtectionScrollId = if (effectiveRequest.useProtectionScroll) {
            resourceSelector.selectProtectionScrollId(materialized.player, materialized.itemInstances)
        } else {
            null
        }

        if (effectiveRequest.useRemovalScroll) {
            val removed = itemSupport.consumeFromInventory(
                inventory = mutableInventory,
                itemInstances = mutableInstances,
                targetTemplateId = selectedRemovalScrollId ?: extractionConfig.removalScrollItemId,
                quantity = 1
            )
            if (removed < 1) {
                return ExtractionExecutionResult(
                    success = false,
                    message = "Pergaminho de remocao indisponivel.",
                    player = materialized.player,
                    itemInstances = materialized.itemInstances,
                    preview = preview
                )
            }
        }
        if (effectiveRequest.useProtectionScroll) {
            val removed = itemSupport.consumeFromInventory(
                inventory = mutableInventory,
                itemInstances = mutableInstances,
                targetTemplateId = selectedProtectionScrollId ?: extractionConfig.protectionScrollItemId,
                quantity = 1
            )
            if (removed < 1) {
                return ExtractionExecutionResult(
                    success = false,
                    message = "Pergaminho de protecao indisponivel.",
                    player = materialized.player,
                    itemInstances = materialized.itemInstances,
                    preview = preview
                )
            }
        }

        val success = rng.nextDouble(0.0, 100.0) <= preview.successChancePct
        var extractedStoneId: String? = null
        var itemDestroyed = false
        var itemReset = false
        if (success) {
            val stoneLevel = materialized.item.enchantLevel.coerceAtLeast(0)
            val stone = createEnchantStone(
                enchantLevel = stoneLevel,
                sourceLevel = materialized.item.level,
                rarity = materialized.item.rarity
            )
            mutableInstances[stone.id] = stone
            mutableInventory += stone.id
            extractedStoneId = stone.id

            if (effectiveRequest.useProtectionScroll) {
                val resetItem = itemSupport.applyEnchantLevel(materialized.item, 0)
                mutableInstances[resetItem.id] = resetItem
                itemReset = true
            } else {
                mutableInstances.remove(materialized.item.id)
                itemSupport.removeItemReference(mutableInventory, materialized.item.id)
                mutablePlayer = mutablePlayer.copy(
                    equipped = mutablePlayer.equipped.filterValues { it != materialized.item.id },
                    quiverInventory = mutablePlayer.quiverInventory.filterNot { it == materialized.item.id }
                )
                itemDestroyed = true
            }
        }

        mutablePlayer = mutablePlayer.copy(inventory = mutableInventory)
        val xp = skillSystem.gainXp(
            player = mutablePlayer,
            skill = SkillType.ENCHANTING,
            baseXp = extractionConfig.attemptBaseXp + if (success) extractionConfig.successBonusXp else 0.0,
            difficulty = 1.0 + (preview.currentEnchantLevel * extractionConfig.xpPerEnchantLevelDifficulty),
            tier = 1
        )
        mutablePlayer = xp.player

        val message = when {
            success && itemReset -> "Extracao bem-sucedida: pedra +${preview.stoneEnchantLevel} criada e item resetado para +0."
            success && itemDestroyed -> "Extracao bem-sucedida: pedra +${preview.stoneEnchantLevel} criada e item consumido."
            success -> "Extracao bem-sucedida: pedra +${preview.stoneEnchantLevel} criada."
            else -> "Extracao falhou. O encantamento permaneceu no item."
        }

        return ExtractionExecutionResult(
            success = success,
            message = message,
            player = mutablePlayer,
            itemInstances = mutableInstances.toMap(),
            extractedStoneId = extractedStoneId,
            extractedEnchantLevel = if (success) preview.stoneEnchantLevel else 0,
            itemDestroyed = itemDestroyed,
            itemResetToZero = itemReset,
            goldSpent = preview.goldCost,
            gainedXp = xp.gainedXp,
            skillSnapshot = xp.snapshot,
            preview = preview
        )
    }

    private fun goldCostFor(item: ItemInstance): Int {
        val base = extractionConfig.goldBaseCost +
            (item.level * extractionConfig.goldPerItemLevel) +
            (item.enchantLevel * extractionConfig.goldPerEnchantLevel)
        val multiplier = extractionConfig.rarityCostMultiplier(item.rarity.name)
        return ceil(base * multiplier).toInt().coerceAtLeast(1)
    }

    private fun adjustChance(baseChance: Double, usingRemovalScroll: Boolean, removalScrollItemId: String?): Double {
        return if (usingRemovalScroll) {
            val tierMultiplier = removalScrollItemId
                ?.let(extractionConfig::removalScrollChanceMultiplier)
                ?.coerceAtLeast(0.1)
                ?: 1.0
            (baseChance * extractionConfig.withScrollChanceMultiplier * tierMultiplier + extractionConfig.withScrollFlatBonusPct)
                .coerceIn(extractionConfig.withScrollMinChancePct, extractionConfig.withScrollCapPct)
        } else {
            (baseChance * extractionConfig.noScrollChanceMultiplier)
                .coerceIn(0.0, extractionConfig.noScrollChanceCapPct)
        }
    }

    private fun createEnchantStone(enchantLevel: Int, sourceLevel: Int, rarity: ItemRarity): ItemInstance {
        val level = enchantLevel.coerceIn(0, enchantConfig.maxEnchantLevel)
        val stoneTemplateId = extractionConfig.enchantStoneTemplateIdForLevel(level)
        val stoneDef = itemRegistry.item(stoneTemplateId)
        val valueBase = stoneDef?.value ?: 30
        val value = valueBase + (level * extractionConfig.stoneValuePerEnchantLevel.coerceAtLeast(0))
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
            description = stoneDef?.description ?: "Pedra que conserva um nivel de encantamento extraido.",
            enchantLevel = level
        )
    }

    fun enchantStoneTemplateId(): String = extractionConfig.enchantStoneTemplateId
    fun removalScrollItemId(): String = extractionConfig.removalScrollItemId
    fun protectionScrollItemId(): String = extractionConfig.protectionScrollItemId
    fun enchantStoneTemplateIds(): Set<String> = extractionConfig.enchantStoneTemplateIds()
    fun removalScrollItemIds(): Set<String> = extractionConfig.removalScrollItemIds()
    fun protectionScrollItemIds(): Set<String> = extractionConfig.protectionScrollItemIds()
}
