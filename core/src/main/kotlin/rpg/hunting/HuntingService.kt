package rpg.hunting

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import rpg.inventory.InventorySystem
import rpg.item.ItemEngine
import rpg.item.ItemRarity
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.SkillSnapshot
import rpg.model.SkillType
import rpg.premium.PremiumSupport
import rpg.progression.PermanentUpgradeService
import rpg.registry.ItemRegistry
import rpg.skills.SkillSystem

data class HuntingPreview(
    val spotId: String,
    val spotName: String,
    val selectedDurationSeconds: Int,
    val cycleDurationSeconds: Double,
    val cycles: Int,
    val successChancePct: Double,
    val expectedRolls: Int,
    val goldCost: Int,
    val durationSeconds: Double,
    val blockedReasons: List<String> = emptyList()
) {
    val available: Boolean get() = blockedReasons.isEmpty()
}

data class HuntingExecutionResult(
    val success: Boolean,
    val message: String,
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val spot: HuntingSpot? = null,
    val selectedDurationSeconds: Int = 0,
    val goldSpent: Int = 0,
    val gainedXp: Double = 0.0,
    val skillSnapshot: SkillSnapshot? = null,
    val collectedByItemId: Map<String, Int> = emptyMap(),
    val rareDropCount: Int = 0,
    val rejectedItems: Int = 0,
    val preview: HuntingPreview? = null
)

class HuntingService(
    private val spots: Map<String, HuntingSpot>,
    private val itemRegistry: ItemRegistry,
    private val itemEngine: ItemEngine,
    private val skillSystem: SkillSystem,
    private val permanentUpgradeService: PermanentUpgradeService,
    private val dropResolver: HuntingDropResolver,
    private val config: HuntingConfig = HuntingConfig(),
    private val raceProfessionBonusPct: (PlayerState, SkillType) -> Double = { _, _ -> 0.0 }
) {
    private val spotComparator = compareBy<HuntingSpot>({ it.recommendedLevel }, { it.name.lowercase() }, { it.id.lowercase() })
    private val enabledSpotCatalog: List<HuntingSpot> = spots.values
        .asSequence()
        .filter { it.drops.isNotEmpty() }
        .sortedWith(spotComparator)
        .toList()
    private val catalogRevision: Int = buildCatalogRevision()

    fun spotCatalogRevision(): Int = catalogRevision

    fun spotCatalog(): List<HuntingSpot> = enabledSpotCatalog

    fun availableSpots(playerLevel: Int): List<HuntingSpot> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredPlayerLevel = playerLevel
        return enabledSpotCatalog
    }

    fun preview(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        spotId: String,
        selectedDurationSeconds: Int
    ): HuntingPreview {
        val prepared = skillSystem.ensureProgress(player)
        val spot = spots[spotId]
        val reasons = mutableListOf<String>()
        if (spot == null) reasons += "Área de caça inválida."
        if (spot != null && player.level < spot.recommendedLevel.coerceAtLeast(1)) {
            reasons += "Desbloqueado no nv ${spot.recommendedLevel.coerceAtLeast(1)}."
        }
        val normalizedDurationSeconds = config.normalizeSelectedDurationSeconds(selectedDurationSeconds)
        val skillLevel = skillSystem.snapshot(prepared, SkillType.HUNTING).level
        val taskEfficiency = activeTaskEfficiencyPct(prepared, "hunting")
        val baseCycleSeconds = spot?.minCycleSeconds?.coerceAtLeast(1) ?: 1
        val cycleDurationSeconds = config.cycleDurationSeconds(baseCycleSeconds, skillLevel, taskEfficiency)
        val durationSeconds = config.actionDurationSeconds(normalizedDurationSeconds) *
            PremiumSupport.productionDurationMultiplier(prepared)
        val cycles = if (spot == null) 0 else config.resolveCycles(normalizedDurationSeconds, cycleDurationSeconds)
        val baseCost = if (spot == null) 0 else goldCostFor(spot, normalizedDurationSeconds)
        val cost = applyProductionCostReduction(prepared, baseCost)
        if (spot != null && cycles <= 0) {
            reasons += "Tempo escolhido insuficiente para completar 1 ciclo."
        }
        if (prepared.gold < cost) {
            reasons += "Ouro insuficiente (custo: $cost)."
        }
        return HuntingPreview(
            spotId = spotId,
            spotName = spot?.name ?: spotId,
            selectedDurationSeconds = normalizedDurationSeconds,
            cycleDurationSeconds = cycleDurationSeconds,
            cycles = cycles,
            successChancePct = if (spot == null || cycles <= 0) 0.0 else 100.0,
            expectedRolls = cycles,
            goldCost = cost,
            durationSeconds = durationSeconds,
            blockedReasons = reasons
        )
    }

    fun hunt(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        spotId: String,
        selectedDurationSeconds: Int
    ): HuntingExecutionResult {
        val prepared = skillSystem.ensureProgress(player)
        val spot = spots[spotId]
            ?: return HuntingExecutionResult(
                success = false,
                message = "Área de caça inválida.",
                player = prepared,
                itemInstances = itemInstances
            )
        val preview = preview(prepared, itemInstances, spotId, selectedDurationSeconds)
        if (!preview.available) {
            return HuntingExecutionResult(
                success = false,
                message = preview.blockedReasons.joinToString(" "),
                player = prepared,
                itemInstances = itemInstances,
                spot = spot,
                selectedDurationSeconds = preview.selectedDurationSeconds,
                preview = preview
            )
        }

        val skillSnapshot = skillSystem.snapshot(prepared, SkillType.HUNTING)
        val taskEfficiency = activeTaskEfficiencyPct(prepared, "hunting")
        val roll = dropResolver.resolve(
            spot = spot,
            playerLevel = prepared.level,
            huntingSkillLevel = skillSnapshot.level,
            selectedDurationSeconds = preview.selectedDurationSeconds,
            cycleDurationSeconds = preview.cycleDurationSeconds,
            taskEfficiencyPct = taskEfficiency
        )
        if (roll.totalUnits <= 0) {
            return HuntingExecutionResult(
                success = false,
                message = "A caça não rendeu nada desta vez.",
                player = prepared,
                itemInstances = itemInstances,
                spot = spot,
                selectedDurationSeconds = preview.selectedDurationSeconds,
                preview = preview
            )
        }

        val mutableInstances = itemInstances.toMutableMap()
        val generatedItemIds = mutableListOf<String>()
        for ((itemId, qty) in roll.collectedByItemId) {
            if (qty <= 0) continue
            val template = itemRegistry.template(itemId)
            if (template != null) {
                repeat(qty) {
                    val generated = itemEngine.generateFromTemplate(
                        template = template,
                        level = max(prepared.level, spot.recommendedLevel),
                        rarity = template.rarity
                    )
                    mutableInstances[generated.id] = generated
                    generatedItemIds += generated.id
                }
                continue
            }
            val itemDef = itemRegistry.item(itemId)
            if (itemDef != null) {
                repeat(qty) { generatedItemIds += itemDef.id }
            }
        }
        if (generatedItemIds.isEmpty()) {
            return HuntingExecutionResult(
                success = false,
                message = "Tabela de caça sem itens válidos.",
                player = prepared,
                itemInstances = itemInstances,
                spot = spot,
                selectedDurationSeconds = preview.selectedDurationSeconds,
                preview = preview
            )
        }

        val inserted = InventorySystem.addItemsWithLimit(
            player = prepared.copy(gold = prepared.gold - preview.goldCost),
            itemInstances = mutableInstances,
            itemRegistry = itemRegistry,
            incomingItemIds = generatedItemIds
        )
        val rejectedGenerated = inserted.rejected.filter { mutableInstances.containsKey(it) }
        for (id in rejectedGenerated) {
            mutableInstances.remove(id)
        }
        if (inserted.accepted.isEmpty()) {
            return HuntingExecutionResult(
                success = false,
                message = "Inventário cheio. Nenhum item de caça foi armazenado.",
                player = prepared,
                itemInstances = itemInstances,
                spot = spot,
                selectedDurationSeconds = preview.selectedDurationSeconds,
                goldSpent = preview.goldCost,
                rejectedItems = inserted.rejected.size,
                preview = preview
            )
        }

        var updatedPlayer = prepared.copy(
            inventory = inserted.inventory,
            quiverInventory = inserted.quiverInventory,
            selectedAmmoTemplateId = inserted.selectedAmmoTemplateId,
            gold = (prepared.gold - preview.goldCost).coerceAtLeast(0)
        )
        val professionXpMultiplier = permanentUpgradeService.professionXpMultiplier(updatedPlayer, SkillType.HUNTING)
        val raceBonusMultiplier = 1.0 + (
            raceProfessionBonusPct(updatedPlayer, SkillType.HUNTING).coerceIn(-50.0, 250.0) / 100.0
            )
        val gained = skillSystem.gainXp(
            player = updatedPlayer,
            skill = SkillType.HUNTING,
            baseXp = spot.baseXp * preview.cycles * professionXpMultiplier * raceBonusMultiplier,
            rarityMultiplier = rarityMultiplierForRoll(roll.collectedByItemId.keys),
            difficulty = spot.difficulty,
            tier = max(1, (spot.recommendedLevel / 12))
        )
        updatedPlayer = gained.player

        val collectedAccepted = inserted.accepted
            .groupingBy { id -> mutableInstances[id]?.templateId ?: id }
            .eachCount()
            .mapValues { (_, qty) -> qty.coerceAtLeast(0) }
            .filterValues { it > 0 }

        val rareAccepted = (roll.rareDropCount * (inserted.accepted.size.toDouble() / generatedItemIds.size.toDouble()))
            .roundToInt()
            .coerceAtLeast(0)
        val message = buildString {
            append("Caça concluída em ${spot.name}: ${inserted.accepted.size} item(ns) obtido(s).")
            if (inserted.rejected.isNotEmpty()) {
                append(" Inventário cheio: ${inserted.rejected.size} item(ns) perdido(s).")
            }
            if (rareAccepted > 0) {
                append(" Drops raros identificados: $rareAccepted.")
            }
        }
        return HuntingExecutionResult(
            success = true,
            message = message,
            player = updatedPlayer,
            itemInstances = mutableInstances.toMap(),
            spot = spot,
            selectedDurationSeconds = preview.selectedDurationSeconds,
            goldSpent = preview.goldCost,
            gainedXp = gained.gainedXp,
            skillSnapshot = gained.snapshot,
            collectedByItemId = collectedAccepted,
            rareDropCount = rareAccepted,
            rejectedItems = inserted.rejected.size,
            preview = preview
        )
    }

    fun durationOptionsSeconds(): List<Int> = config.normalizedDurationOptionsSeconds()

    private fun goldCostFor(spot: HuntingSpot, durationSeconds: Int): Int {
        val rarityBudget = spot.drops
            .mapNotNull { itemRegistry.entry(it.itemId)?.rarity }
            .ifEmpty { listOf(ItemRarity.COMMON) }
            .averageOf { it.ordinal.toDouble() }
        val durationMinutes = durationSeconds.coerceAtLeast(1) / 60.0
        val base = config.goldBaseCost +
            durationMinutes * config.goldPerMinute +
            spot.recommendedLevel * config.goldPerRecommendedLevel +
            rarityBudget * config.goldPerRarityStep
        return ceil(base).toInt().coerceAtLeast(1)
    }

    private fun rarityMultiplierForRoll(itemIds: Set<String>): Double {
        if (itemIds.isEmpty()) return 1.0
        val weights = itemIds.mapNotNull { itemRegistry.entry(it)?.rarity?.ordinal?.toDouble() }
        if (weights.isEmpty()) return 1.0
        val avgOrdinal = weights.average().coerceIn(0.0, ItemRarity.MYTHIC.ordinal.toDouble())
        return 1.0 + avgOrdinal * 0.08
    }

    private fun activeTaskEfficiencyPct(player: PlayerState, taskId: String): Double {
        if (player.foodBuffRemainingMinutes <= 0.0) return 0.0
        val active = player.foodBuffTaskId?.trim()?.lowercase()
        if (active != taskId.trim().lowercase()) return 0.0
        return player.foodBuffTaskEfficiencyPct.coerceIn(0.0, 80.0)
    }

    private fun List<ItemRarity>.averageOf(selector: (ItemRarity) -> Double): Double {
        if (isEmpty()) return 0.0
        return sumOf(selector) / size.toDouble()
    }

    private fun applyProductionCostReduction(player: PlayerState, baseCost: Int): Int {
        if (baseCost <= 0) return 0
        val multiplier = (1.0 - PremiumSupport.productionCostReductionPct(player) / 100.0).coerceIn(0.1, 1.0)
        return ceil(baseCost * multiplier).toInt().coerceAtLeast(1)
    }

    private fun buildCatalogRevision(): Int {
        var signature = 17
        enabledSpotCatalog.forEach { spot ->
            signature = (signature * 31) + spot.id.hashCode()
            signature = (signature * 31) + spot.name.lowercase().hashCode()
            signature = (signature * 31) + spot.recommendedLevel
            signature = (signature * 31) + spot.minCycleSeconds
            signature = (signature * 31) + spot.baseXp.toString().hashCode()
            signature = (signature * 31) + spot.drops.size
        }
        return signature
    }
}




