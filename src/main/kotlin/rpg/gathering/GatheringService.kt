package rpg.gathering

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
    val rejectedItems: Int = 0
)

class GatheringService(
    private val nodes: Map<String, GatherNodeDef>,
    private val itemRegistry: ItemRegistry,
    private val itemEngine: ItemEngine,
    private val skillSystem: SkillSystem,
    private val rng: Random
) {
    fun availableNodes(playerLevel: Int, type: GatheringType? = null): List<GatherNodeDef> {
        return nodes.values
            .asSequence()
            .filter { it.enabled && playerLevel >= it.minPlayerLevel }
            .filter { type == null || it.type == type }
            .sortedBy { it.name }
            .toList()
    }

    fun availableNodes(player: PlayerState, type: GatheringType? = null): List<GatherNodeDef> {
        val prepared = skillSystem.ensureProgress(player)
        return nodes.values
            .asSequence()
            .filter { it.enabled && prepared.level >= it.minPlayerLevel }
            .filter { type == null || it.type == type }
            .filter { node ->
                val skill = nodeSkill(node)
                val snapshot = skillSystem.snapshot(prepared, skill)
                snapshot.level >= node.minSkillLevel
            }
            .sortedBy { it.name }
            .toList()
    }

    fun gather(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        nodeId: String
    ): GatherExecutionResult {
        val preparedPlayer = skillSystem.ensureProgress(player)
        val node = nodes[nodeId]
            ?: return GatherExecutionResult(false, "Ponto de coleta nao encontrado.", preparedPlayer, itemInstances)
        if (!node.enabled) {
            return GatherExecutionResult(false, "Ponto de coleta desativado.", preparedPlayer, itemInstances, node = node)
        }
        if (preparedPlayer.level < node.minPlayerLevel) {
            return GatherExecutionResult(
                false,
                "Nivel insuficiente para este ponto de coleta.",
                preparedPlayer,
                itemInstances,
                node = node
            )
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
        var quantity = if (maxQty == minQty) minQty else rng.nextInt(minQty, maxQty + 1)
        if (rng.nextDouble(0.0, 100.0) <= skillSnapshot.doubleDropChancePct) {
            quantity *= 2
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
                    "Template de recurso invalido.",
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
                    "Recurso invalido.",
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
                message = "Inventario cheio. Nenhum item coletado.",
                player = preparedPlayer,
                itemInstances = itemInstances,
                node = node,
                resourceItemId = resourceId,
                quantity = 0,
                skillType = skillType,
                skillSnapshot = skillSnapshot,
                rejectedItems = withCapacity.rejected.size
            )
        }

        var updatedPlayer = preparedPlayer.copy(inventory = withCapacity.inventory)
        val rarityMultiplier = resourceRarityMultiplier(resourceId)
        val xpResult = skillSystem.gainXp(
            player = updatedPlayer,
            skill = skillType,
            baseXp = node.baseXp * acceptedQty,
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
                    append(" Inventario cheio: ${withCapacity.rejected.size} item(ns) descartado(s).")
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
            rejectedItems = withCapacity.rejected.size
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
}
