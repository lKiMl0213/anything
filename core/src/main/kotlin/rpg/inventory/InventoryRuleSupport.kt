package rpg.inventory

import kotlin.math.max
import rpg.model.EquipSlot
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.progression.PermanentUpgradeKeys
import rpg.registry.ItemRegistry

internal object InventoryRuleSupport {
    private const val legacyBackpackSlotKey = "BACKPACK"
    private const val backpackTierPrefix = "backpack_tier:"
    private val backpackSlotsByTier = mapOf(
        1 to "BACKPACK_T1",
        2 to "BACKPACK_T2",
        3 to "BACKPACK_T3"
    )
    private val backpackKnownTierBonuses = mapOf(
        10 to 1,
        20 to 2,
        30 to 3
    )
    private const val slotTagPrefix = "inventory_slots:"
    private const val ammoCapacityPrefix = "ammo_capacity:"
    private const val defaultQuiverCapacity = 30
    private const val maxQuiverCapacity = 1000
    private const val quiverTag = "quiver"
    private val quiverUpgradeSteps = listOf(10, 20, 40, 50, 100, 250, 500)

    fun inventoryLimit(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        val base = player.inventoryBaseSlots.coerceAtLeast(1)
        val equippedBackpacks = equippedBackpackItemIdsByTier(player, itemInstances, itemRegistry)
        if (equippedBackpacks.isEmpty()) return base
        val bonus = equippedBackpacks.values.sumOf { backpackId ->
            parseInventorySlotBonus(resolveItemTags(backpackId, itemInstances, itemRegistry)) ?: 0
        }
        return base + max(0, bonus)
    }

    fun backpackSlotKeyForTier(tier: Int): String? = backpackSlotsByTier[tier]

    fun backpackTier(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int? {
        val tags = resolveItemTags(itemId, itemInstances, itemRegistry)
        val taggedTier = tags.firstNotNullOfOrNull { tag ->
            val normalized = tag.trim().lowercase()
            if (!normalized.startsWith(backpackTierPrefix)) return@firstNotNullOfOrNull null
            normalized.removePrefix(backpackTierPrefix).toIntOrNull()
        }
        if (taggedTier != null) {
            return taggedTier.takeIf { it in backpackSlotsByTier.keys }
        }
        val slotBonus = parseInventorySlotBonus(tags) ?: return null
        return backpackKnownTierBonuses[slotBonus]
    }

    fun equippedBackpackItemIdsByTier(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Map<Int, String> {
        val byTier = linkedMapOf<Int, String>()
        backpackSlotsByTier.forEach { (tier, slotKey) ->
            val equippedId = player.equipped[slotKey] ?: return@forEach
            if (backpackTier(equippedId, itemInstances, itemRegistry) == tier) {
                byTier[tier] = equippedId
            }
        }
        val legacyId = player.equipped[legacyBackpackSlotKey]
        val legacyTier = legacyId?.let { backpackTier(it, itemInstances, itemRegistry) }
        if (legacyId != null && legacyTier != null && byTier[legacyTier] == null) {
            byTier[legacyTier] = legacyId
        }
        return byTier.toMap()
    }

    fun hasOwnedBackpackTier(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry,
        tier: Int
    ): Boolean {
        if (tier !in backpackSlotsByTier.keys) return false
        if (equippedBackpackItemIdsByTier(player, itemInstances, itemRegistry).containsKey(tier)) {
            return true
        }
        return player.inventory.any { itemId ->
            backpackTier(itemId, itemInstances, itemRegistry) == tier
        }
    }

    fun slotsUsed(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        return player.inventory
            .asSequence()
            .map { stackKey(it, itemInstances, itemRegistry) }
            .distinct()
            .count()
    }

    fun quiverCapacity(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Int {
        val quiverId = player.equipped[EquipSlot.ALJAVA.name] ?: return 0
        val tags = itemInstances[quiverId]?.tags
            ?: itemRegistry.item(quiverId)?.tags
            ?: itemRegistry.template(quiverId)?.tags
            ?: emptyList()
        if (tags.none { it.trim().equals(quiverTag, ignoreCase = true) }) {
            return 0
        }
        val baseCapacity = tags.firstNotNullOfOrNull { tag ->
            val normalized = tag.trim().lowercase()
            if (!normalized.startsWith(ammoCapacityPrefix)) return@firstNotNullOfOrNull null
            normalized.removePrefix(ammoCapacityPrefix).toIntOrNull()
        }?.coerceAtLeast(0) ?: defaultQuiverCapacity
        val upgradeLevel = player.permanentUpgradeLevels[PermanentUpgradeKeys.QUIVER_CAPACITY] ?: 0
        val upgradeBonus = quiverUpgradeSteps.take(upgradeLevel.coerceAtLeast(0)).sum()
        return (baseCapacity + upgradeBonus).coerceIn(defaultQuiverCapacity, maxQuiverCapacity)
    }

    fun ammoTemplateId(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): String {
        val templateId = itemInstances[itemId]?.templateId
        return (
            templateId
                ?: itemRegistry.item(itemId)?.id
                ?: itemRegistry.template(itemId)?.id
                ?: itemId
            ).trim().lowercase()
    }

    fun isArrowAmmo(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): Boolean {
        val instance = itemInstances[itemId]
        val templateId = instance?.templateId
        val resolvedId = instance?.id ?: templateId ?: itemId
        val tags = instance?.tags
            ?: itemRegistry.item(itemId)?.tags
            ?: templateId?.let { itemRegistry.item(it)?.tags ?: itemRegistry.template(it)?.tags }
            ?: itemRegistry.template(itemId)?.tags
            ?: emptyList()
        val normalizedTags = tags.mapTo(mutableSetOf()) { it.trim().lowercase() }
        return "arrow" in normalizedTags || resolvedId.lowercase().startsWith("arrow_")
    }

    fun stackKey(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): String {
        val instance = itemInstances[itemId]
        if (instance != null) {
            return if (instance.type == ItemType.EQUIPMENT) {
                "equip:${instance.id}"
            } else if (instance.enchantLevel > 0) {
                "stack:${instance.templateId}:enc:${instance.enchantLevel}"
            } else {
                "stack:${instance.templateId}"
            }
        }
        val item = itemRegistry.item(itemId)
        if (item != null && item.type == ItemType.EQUIPMENT) {
            return "equip:$itemId"
        }
        return "stack:$itemId"
    }

    fun normalizeSelectedAmmoTemplateId(
        selectedAmmoTemplateId: String?,
        quiverInventory: List<String>,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): String? {
        val availableTemplates = quiverInventory
            .filter { isArrowAmmo(it, itemInstances, itemRegistry) }
            .map { ammoTemplateId(it, itemInstances, itemRegistry) }
            .distinct()
        if (availableTemplates.isEmpty()) return null
        val normalizedSelected = selectedAmmoTemplateId
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        return normalizedSelected?.takeIf { it in availableTemplates } ?: availableTemplates.first()
    }

    fun orderedQuiverAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): List<String> {
        val selectedTemplateId = player.selectedAmmoTemplateId
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        val arrows = player.quiverInventory.filter { isArrowAmmo(it, itemInstances, itemRegistry) }
        if (selectedTemplateId == null) return arrows
        val selected = arrows.filter { ammoTemplateId(it, itemInstances, itemRegistry) == selectedTemplateId }
        val others = arrows.filter { ammoTemplateId(it, itemInstances, itemRegistry) != selectedTemplateId }
        return selected + others
    }

    private fun parseInventorySlotBonus(tags: List<String>): Int? {
        return tags.firstNotNullOfOrNull { tag ->
            val normalized = tag.trim().lowercase()
            if (!normalized.startsWith(slotTagPrefix)) return@firstNotNullOfOrNull null
            normalized.removePrefix(slotTagPrefix).toIntOrNull()
        }
    }

    private fun resolveItemTags(
        itemId: String,
        itemInstances: Map<String, ItemInstance>,
        itemRegistry: ItemRegistry
    ): List<String> {
        return itemInstances[itemId]?.tags
            ?: itemRegistry.item(itemId)?.tags
            ?: itemRegistry.template(itemId)?.tags
            ?: emptyList()
    }
}

