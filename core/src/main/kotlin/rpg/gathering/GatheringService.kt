package rpg.gathering

import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random
import rpg.inventory.InventorySystem
import rpg.item.ItemEngine
import rpg.model.GatherNodeDef
import rpg.model.GatheringType
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.SkillSnapshot
import rpg.model.SkillType
import rpg.skills.SkillSystem
import rpg.progression.PermanentUpgradeService
import rpg.registry.ItemRegistry

data class GatherExecutionResult(
    val success: Boolean,
    val message: String,
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val node: GatherNodeDef? = null,
    val resourceItemId: String? = null,
    val quantity: Int = 0,
    val gainedXp: Double = 0.0,
    val skillType: SkillType? = null,
    val skillSnapshot: SkillSnapshot? = null,
    val rejectedItems: Int = 0,
    val baseQuantity: Int = 0,
    val bonusQuantity: Int = 0
)

class GatheringService(
    private val nodes: Map<String, GatherNodeDef>,
    private val itemRegistry: ItemRegistry,
    private val itemEngine: ItemEngine,
    private val skillSystem: SkillSystem,
    private val rng: Random,
    private val permanentUpgradeService: PermanentUpgradeService,
    private val raceProfessionBonusPct: (PlayerState, SkillType) -> Double = { _, _ -> 0.0 }
) {
    private val nodeComparator = compareBy<GatherNodeDef>({ it.minSkillLevel }, { it.name.lowercase() }, { it.id.lowercase() })
    private val enabledCatalogAll: List<GatherNodeDef> = nodes.values
        .asSequence()
        .filter { it.enabled }
        .sortedWith(nodeComparator)
        .toList()
    private val enabledCatalogByType: Map<GatheringType, List<GatherNodeDef>> =
        GatheringType.entries.associateWith { type ->
            enabledCatalogAll.filter { it.type == type }
        }
    private val catalogRevision: Int = buildCatalogRevision()

    fun nodeCatalogRevision(): Int = catalogRevision

    fun enabledNodeCatalog(type: GatheringType): List<GatherNodeDef> {
        return enabledCatalogByType[type].orEmpty()
    }

    fun availableNodes(playerLevel: Int, type: GatheringType? = null): List<GatherNodeDef> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredPlayerLevel = playerLevel
        return if (type == null) enabledCatalogAll else enabledCatalogByType[type].orEmpty()
    }

    fun availableNodes(player: PlayerState, type: GatheringType? = null): List<GatherNodeDef> {
        val prepared = skillSystem.ensureProgress(player)
        val nodesToFilter = if (type == null) enabledCatalogAll else enabledCatalogByType[type].orEmpty()
        return nodesToFilter.filter { node ->
                val skill = nodeSkill(node)
                val snapshot = skillSystem.snapshot(prepared, skill)
                snapshot.level >= node.minSkillLevel
            }
    }

    fun gather(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        nodeId: String
    ): GatherExecutionResult {
        val preparedPlayer = skillSystem.ensureProgress(player)
        val node = nodes[nodeId]
            ?: return GatherExecutionResult(false, "Ponto de coleta não encontrado.", preparedPlayer, itemInstances)
        if (!node.enabled) {
            return GatherExecutionResult(false, "Ponto de coleta desativado.", preparedPlayer, itemInstances, node = node)
        }
        val skillType = nodeSkill(node)
        val skillSnapshot = skillSystem.snapshot(preparedPlayer, skillType)
        if (skillSnapshot.level < node.minSkillLevel) {
            return GatherExecutionResult(
                false,
                "Skill insuficiente para este ponto de coleta.",
                preparedPlayer,
                itemInstances,
                node = node,
                skillType = skillType,
                skillSnapshot = skillSnapshot
            )
        }

        val minQty = node.minQty.coerceAtLeast(1)
        val maxQty = max(minQty, node.maxQty)
        val baseRolledQuantity = if (maxQty == minQty) minQty else rng.nextInt(minQty, maxQty + 1)
        var quantity = baseRolledQuantity
        var bonusGenerated = 0
        val upgradedDoubleChance = (
            skillSnapshot.doubleDropChancePct +
                permanentUpgradeService.gatherDoubleBonusPct(preparedPlayer, node.type)
            ).coerceIn(0.0, 95.0)
        if (rng.nextDouble(0.0, 100.0) <= upgradedDoubleChance) {
            bonusGenerated = quantity
            quantity += bonusGenerated
        }
        if (quantity <= 0) {
            return GatherExecutionResult(
                false,
                "Nada foi obtido.",
                preparedPlayer,
                itemInstances,
                node = node,
                skillType = skillType,
                skillSnapshot = skillSnapshot
            )
        }

        val updatedInstances = itemInstances.toMutableMap()
        val resourceId = node.resourceItemId
        val generatedIds = mutableListOf<String>()
        if (itemRegistry.isTemplate(resourceId)) {
            val template = itemRegistry.template(resourceId)
                ?: return GatherExecutionResult(
                    false,
                    "Template de recurso inválido.",
                    preparedPlayer,
                    itemInstances,
                    node = node
                )
            repeat(quantity) {
                val generated = itemEngine.generateFromTemplate(
                    template = template,
                    level = preparedPlayer.level,
                    rarity = template.rarity
                )
                updatedInstances[generated.id] = generated
                generatedIds += generated.id
            }
        } else {
            if (itemRegistry.item(resourceId) == null) {
                return GatherExecutionResult(
                    false,
                    "Recurso inválido.",
                    preparedPlayer,
                    itemInstances,
                    node = node
                )
            }
            repeat(quantity) {
                generatedIds += resourceId
            }
        }

        val withCapacity = InventorySystem.addItemsWithLimit(
            player = preparedPlayer,
            itemInstances = updatedInstances,
            itemRegistry = itemRegistry,
            incomingItemIds = generatedIds
        )
        val rejectedGenerated = withCapacity.rejected.filter { updatedInstances.containsKey(it) }
        for (rejectedId in rejectedGenerated) {
            updatedInstances.remove(rejectedId)
        }
        val acceptedQty = withCapacity.accepted.size
        if (acceptedQty <= 0) {
            return GatherExecutionResult(
                success = false,
                message = "Inventário cheio. Nenhum item coletado.",
                player = preparedPlayer,
                itemInstances = itemInstances,
                node = node,
                resourceItemId = resourceId,
                quantity = 0,
                skillType = skillType,
                skillSnapshot = skillSnapshot,
                rejectedItems = withCapacity.rejected.size,
                baseQuantity = 0,
                bonusQuantity = 0
            )
        }
        val (acceptedBase, acceptedBonus) = splitAcceptedQuantity(
            accepted = acceptedQty,
            baseGenerated = baseRolledQuantity,
            bonusGenerated = bonusGenerated
        )

        var updatedPlayer = preparedPlayer.copy(
            inventory = withCapacity.inventory,
            quiverInventory = withCapacity.quiverInventory,
            selectedAmmoTemplateId = withCapacity.selectedAmmoTemplateId
        )
        val rarityMultiplier = resourceRarityMultiplier(resourceId)
        val professionXpMultiplier = permanentUpgradeService.professionXpMultiplier(preparedPlayer, skillType)
        val raceBonusMultiplier = 1.0 + (
            raceProfessionBonusPct(preparedPlayer, skillType).coerceIn(-50.0, 250.0) / 100.0
            )
        val xpResult = skillSystem.gainXp(
            player = updatedPlayer,
            skill = skillType,
            baseXp = node.baseXp * acceptedQty * professionXpMultiplier * raceBonusMultiplier,
            rarityMultiplier = rarityMultiplier,
            difficulty = node.difficulty,
            tier = node.tier
        )
        updatedPlayer = xpResult.player
        return GatherExecutionResult(
            success = true,
            message = buildString {
                append("Coleta concluida: ${node.name} -> $acceptedQty item(ns).")
                if (withCapacity.rejected.isNotEmpty()) {
                    append(" Inventário cheio: ${withCapacity.rejected.size} item(ns) descartado(s).")
                }
            },
            player = updatedPlayer,
            itemInstances = updatedInstances,
            node = node,
            resourceItemId = resourceId,
            quantity = acceptedQty,
            gainedXp = xpResult.gainedXp,
            skillType = skillType,
            skillSnapshot = xpResult.snapshot,
            rejectedItems = withCapacity.rejected.size,
            baseQuantity = acceptedBase,
            bonusQuantity = acceptedBonus
        )
    }

    fun nodeSkill(node: GatherNodeDef): SkillType {
        return node.skillType ?: when (node.type) {
            GatheringType.MINING -> SkillType.MINING
            GatheringType.HERBALISM -> SkillType.GATHERING
            GatheringType.WOODCUTTING -> SkillType.WOODCUTTING
            GatheringType.FISHING -> SkillType.FISHING
        }
    }

    private fun resourceRarityMultiplier(resourceId: String): Double {
        val rarity = itemRegistry.item(resourceId)?.rarity ?: itemRegistry.template(resourceId)?.rarity
        return when (rarity?.name) {
            "COMMON" -> 1.0
            "UNCOMMON" -> 1.15
            "RARE" -> 1.35
            "EPIC" -> 1.7
            "LEGENDARY" -> 2.2
            else -> 1.0
        }
    }

    private fun splitAcceptedQuantity(
        accepted: Int,
        baseGenerated: Int,
        bonusGenerated: Int
    ): Pair<Int, Int> {
        if (accepted <= 0) return 0 to 0
        if (bonusGenerated <= 0) return accepted to 0
        val totalGenerated = (baseGenerated + bonusGenerated).coerceAtLeast(1)
        val baseAccepted = floor((accepted.toDouble() * baseGenerated.toDouble()) / totalGenerated.toDouble())
            .toInt()
            .coerceIn(0, accepted)
        val bonusAccepted = (accepted - baseAccepted).coerceAtLeast(0)
        return baseAccepted to bonusAccepted
    }

    private fun buildCatalogRevision(): Int {
        var signature = 17
        enabledCatalogAll.forEach { node ->
            signature = (signature * 31) + node.id.hashCode()
            signature = (signature * 31) + node.name.lowercase().hashCode()
            signature = (signature * 31) + node.type.name.hashCode()
            signature = (signature * 31) + node.minSkillLevel
            signature = (signature * 31) + node.resourceItemId.hashCode()
            signature = (signature * 31) + node.baseDurationSeconds.toString().hashCode()
        }
        return signature
    }
}



