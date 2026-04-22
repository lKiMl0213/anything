package rpg.application.inventory

import rpg.engine.ComputedStats
import rpg.item.ResolvedItem

data class InventoryStackView(
    val sampleItemId: String,
    val quantity: Int,
    val itemIds: List<String>,
    val item: ResolvedItem
)

data class AmmoStackView(
    val templateId: String,
    val sampleItemId: String,
    val quantity: Int,
    val itemIds: List<String>,
    val item: ResolvedItem
)

data class EquippedSlotView(
    val slotKey: String,
    val label: String,
    val equippedItemId: String?,
    val displayLabel: String
)

data class InventoryItemDetailView(
    val sampleItemId: String,
    val quantity: Int,
    val item: ResolvedItem,
    val saleValue: Int,
    val detailLines: List<String>,
    val comparisonSummary: String? = null
)

data class EquippedItemDetailView(
    val slotKey: String,
    val itemId: String,
    val item: ResolvedItem,
    val detailLines: List<String>,
    val removalSummary: String
)

data class EquipComparisonPreviewData(
    val slotKey: String,
    val replacedItem: ResolvedItem?,
    val before: ComputedStats,
    val after: ComputedStats
)
