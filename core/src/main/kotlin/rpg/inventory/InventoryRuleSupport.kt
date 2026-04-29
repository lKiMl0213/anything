package rpg.inventory

import kotlin.math.max
import rpg.model.EquipSlot
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.progression.PermanentUpgradeKeys
import rpg.registry.ItemRegistry

internal object InventoryRuleSupport {
    private const val backpackSlotKey = "BACKPACK"
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
        val backpackId = player.equipped[backpackSlotKey] ?: return base
        val tags = itemInstances[backpackId]?.tags
            ?: itemRegistry.item(backpackId)?.tags
            ?: emptyList()
        val bonus = tags.firstNotNullOfOrNull { tag ->
            if (!tag.startsWith(slotTagPrefix)) return@firstNotNullOfOrNull null
            tag.removePrefix(slotTagPrefix).toIntOrNull()
        } ?: 0
        return base + max(0, bonus)
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
}
