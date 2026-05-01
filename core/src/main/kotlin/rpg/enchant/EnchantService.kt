package rpg.enchant

import kotlin.random.Random
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.SkillType
import rpg.registry.ItemRegistry
import rpg.skills.SkillSystem

class EnchantService(
    itemRegistry: ItemRegistry,
    private val skillSystem: SkillSystem,
    private val rng: Random,
    private val config: EnchantConfig = EnchantConfig(),
    private val chanceCalculator: EnchantChanceCalculator = EnchantChanceCalculator(config),
    private val validator: EnchantValidator = EnchantValidator(config)
) {
    private val itemSupport = EnchantItemSupport(itemRegistry, config)
    private val resourcePlanner = EnchantResourcePlanner(config)

    fun preview(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        request: EnchantAttemptRequest
    ): EnchantAttemptPreview {
        val prepared = skillSystem.ensureProgress(player)
        val target = itemSupport.resolveTargetForPreview(prepared, itemInstances, request.itemId)
        val skillLevel = skillSystem.snapshot(prepared, SkillType.ENCHANTING).level
        val normalizedEnhancementRunes = request.enhancementRunes.coerceAtLeast(0)
        val runePlan = resourcePlanner.planEnhancementRunes(prepared, itemInstances, normalizedEnhancementRunes)
        val item = target?.item
        val chance = chanceCalculator.calculate(
            currentEnchantLevel = item?.enchantLevel ?: 0,
            enhancementRunes = runePlan.totalConsumed,
            useProtectionRune = request.useProtectionRune,
            enchantSkillLevel = skillLevel,
            totalRuneBonusPctOverride = runePlan.totalBonusPct
        )
        val cost = item?.let(itemSupport::goldCostFor) ?: 0
        val reasons = validator.validate(
            player = prepared,
            itemInstances = itemInstances,
            item = item,
            request = request.copy(
                enhancementRunes = normalizedEnhancementRunes,
                useProtectionRune = request.useProtectionRune
            ),
            goldCost = cost
        )
        return EnchantAttemptPreview(
            itemId = request.itemId,
            itemName = item?.name ?: request.itemId,
            currentEnchantLevel = item?.enchantLevel ?: 0,
            nextEnchantLevel = ((item?.enchantLevel ?: 0) + 1).coerceAtMost(config.maxEnchantLevel),
            maxEnchantLevel = config.maxEnchantLevel,
            successChancePct = chance.finalSuccessChancePct,
            breakChancePct = chance.finalBreakChancePct,
            goldCost = cost,
            enhancementRunesRequired = normalizedEnhancementRunes,
            useProtectionRune = request.useProtectionRune,
            durationSeconds = chance.durationSeconds,
            blockedReasons = reasons
        )
    }

    fun enchant(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        request: EnchantAttemptRequest
    ): EnchantExecutionResult {
        val prepared = skillSystem.ensureProgress(player)
        val normalizedRequest = request.copy(
            enhancementRunes = request.enhancementRunes.coerceAtLeast(0),
            useProtectionRune = request.useProtectionRune
        )
        val initialPreview = preview(prepared, itemInstances, normalizedRequest)
        if (!initialPreview.available) {
            return EnchantExecutionResult(
                success = false,
                message = initialPreview.blockedReasons.joinToString(" "),
                player = prepared,
                itemInstances = itemInstances,
                targetItemId = normalizedRequest.itemId,
                preview = initialPreview
            )
        }

        val materialized = itemSupport.materializeTarget(prepared, itemInstances, normalizedRequest.itemId)
            ?: return EnchantExecutionResult(
                success = false,
                message = "Item alvo nao encontrado no inventario.",
                player = prepared,
                itemInstances = itemInstances
            )
        val effectiveRequest = normalizedRequest.copy(itemId = materialized.item.id)
        val preview = preview(materialized.player, materialized.itemInstances, effectiveRequest)

        val mutableInventory = materialized.player.inventory.toMutableList()
        val mutableInstances = materialized.itemInstances.toMutableMap()
        var mutablePlayer = materialized.player.copy(gold = materialized.player.gold - preview.goldCost)

        val enhancementPlan = resourcePlanner.planEnhancementRunes(
            player = materialized.player,
            itemInstances = materialized.itemInstances,
            requestedRunes = normalizedRequest.enhancementRunes
        )
        if (enhancementPlan.totalConsumed < normalizedRequest.enhancementRunes) {
            return EnchantExecutionResult(
                success = false,
                message = "Runas de aprimoramento insuficientes.",
                player = materialized.player,
                itemInstances = materialized.itemInstances,
                targetItemId = materialized.item.id,
                preview = preview
            )
        }
        for ((runeItemId, quantity) in enhancementPlan.consumptionByItemId) {
            val removed = itemSupport.consumeFromInventory(
                inventory = mutableInventory,
                itemInstances = mutableInstances,
                targetTemplateId = runeItemId,
                quantity = quantity
            )
            if (removed < quantity) {
                return EnchantExecutionResult(
                    success = false,
                    message = "Runas de aprimoramento insuficientes.",
                    player = materialized.player,
                    itemInstances = materialized.itemInstances,
                    targetItemId = materialized.item.id,
                    preview = preview
                )
            }
        }

        val consumedProtection = if (normalizedRequest.useProtectionRune) {
            val protectionRuneId = resourcePlanner.selectProtectionRuneId(materialized.player, materialized.itemInstances)
            itemSupport.consumeFromInventory(
                inventory = mutableInventory,
                itemInstances = mutableInstances,
                targetTemplateId = protectionRuneId ?: config.protectionRuneItemId,
                quantity = 1
            ) == 1
        } else {
            false
        }
        if (normalizedRequest.useProtectionRune && !consumedProtection) {
            return EnchantExecutionResult(
                success = false,
                message = "Runa de protecao indisponivel.",
                player = materialized.player,
                itemInstances = materialized.itemInstances,
                targetItemId = materialized.item.id,
                preview = preview
            )
        }

        val skillLevel = skillSystem.snapshot(mutablePlayer, SkillType.ENCHANTING).level
        val chance = chanceCalculator.calculate(
            currentEnchantLevel = materialized.item.enchantLevel,
            enhancementRunes = enhancementPlan.totalConsumed,
            useProtectionRune = normalizedRequest.useProtectionRune,
            enchantSkillLevel = skillLevel,
            totalRuneBonusPctOverride = enhancementPlan.totalBonusPct
        )

        val successRoll = rng.nextDouble(0.0, 100.0) <= chance.finalSuccessChancePct
        var destroyed = false
        var updatedItemId: String? = materialized.item.id
        var newEnchantLevel = materialized.item.enchantLevel
        if (successRoll) {
            val upgraded = itemSupport.applyEnchantLevel(materialized.item, materialized.item.enchantLevel + 1)
            mutableInstances[upgraded.id] = upgraded
            newEnchantLevel = upgraded.enchantLevel
        } else {
            val breakRoll = rng.nextDouble(0.0, 100.0) <= chance.finalBreakChancePct
            if (breakRoll && !normalizedRequest.useProtectionRune) {
                destroyed = true
                updatedItemId = null
                mutableInstances.remove(materialized.item.id)
                itemSupport.removeItemReference(mutableInventory, materialized.item.id)
                mutablePlayer = mutablePlayer.copy(
                    equipped = mutablePlayer.equipped.filterValues { it != materialized.item.id },
                    quiverInventory = mutablePlayer.quiverInventory.filterNot { it == materialized.item.id },
                    selectedAmmoTemplateId = mutablePlayer.selectedAmmoTemplateId
                )
            }
        }

        mutablePlayer = mutablePlayer.copy(inventory = mutableInventory)
        val baseXp = config.attemptBaseXp + if (successRoll) config.successBonusXp else 0.0
        val xpResult = skillSystem.gainXp(
            player = mutablePlayer,
            skill = SkillType.ENCHANTING,
            baseXp = baseXp * itemSupport.antiExploitXpMultiplier(materialized.item.level),
            difficulty = 1.0 + (materialized.item.enchantLevel * config.xpPerEnchantLevelDifficulty),
            tier = 1
        )
        mutablePlayer = xpResult.player

        val message = when {
            successRoll -> "Encantamento bem-sucedido: ${materialized.item.name} +${newEnchantLevel}."
            destroyed -> "Falha critica: ${materialized.item.name} foi destruido."
            else -> "Falha no encantamento: ${materialized.item.name} manteve +${materialized.item.enchantLevel}."
        }
        return EnchantExecutionResult(
            success = successRoll,
            message = message,
            player = mutablePlayer,
            itemInstances = mutableInstances.toMap(),
            targetItemId = updatedItemId,
            destroyed = destroyed,
            previousEnchantLevel = materialized.item.enchantLevel,
            newEnchantLevel = newEnchantLevel,
            consumedEnhancementRunes = enhancementPlan.totalConsumed,
            consumedProtectionRune = normalizedRequest.useProtectionRune,
            goldSpent = preview.goldCost,
            gainedXp = xpResult.gainedXp,
            skillSnapshot = xpResult.snapshot,
            preview = preview
        )
    }

    fun maxEnchantLevel(): Int = config.maxEnchantLevel
    fun enhancementRuneItemId(): String = config.enhancementRuneItemId
    fun protectionRuneItemId(): String = config.protectionRuneItemId
    fun enhancementRuneItemIds(): Set<String> = config.enhancementRuneItemIds()
    fun protectionRuneItemIds(): Set<String> = config.protectionRuneItemIds()
    fun maxEnhancementRunesPerAttempt(): Int = config.maxEnhancementRunesPerAttempt
}
