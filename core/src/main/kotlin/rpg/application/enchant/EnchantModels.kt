package rpg.application.enchant

import rpg.application.production.ProductionTimedActionView

data class EnchantInventoryItemView(
    val itemId: String,
    val displayLabel: String,
    val quantity: Int,
    val enchantLevel: Int,
    val maxEnchantLevel: Int,
    val available: Boolean,
    val blockedReasons: List<String>
)

data class EnchantAttemptOptionView(
    val enhancementRunes: Int,
    val useProtectionRune: Boolean,
    val displayLabel: String,
    val available: Boolean,
    val blockedReasons: List<String>
)

data class EnchantItemDetailView(
    val itemId: String,
    val itemLabel: String,
    val enchantLevel: Int,
    val maxEnchantLevel: Int,
    val attemptOptions: List<EnchantAttemptOptionView>,
    val detailLines: List<String>
)

data class EnchantPrepareResult(
    val ready: Boolean,
    val messages: List<String>,
    val timedActionView: ProductionTimedActionView? = null
)

data class EnchantMutationResult(
    val state: rpg.model.GameState,
    val messages: List<String>,
    val selectedItemId: String? = null
)

data class FusionInventoryItemView(
    val itemId: String,
    val displayLabel: String,
    val enchantLevel: Int,
    val modeLabel: String
)

data class FusionPreviewView(
    val slot1ItemId: String,
    val slot2ItemId: String,
    val slot1Label: String,
    val slot2Label: String,
    val detailLines: List<String>,
    val available: Boolean,
    val blockedReasons: List<String>
)

data class FusionPrepareResult(
    val ready: Boolean,
    val messages: List<String>,
    val timedActionView: ProductionTimedActionView? = null
)

data class FusionMutationResult(
    val state: rpg.model.GameState,
    val messages: List<String>,
    val outputItemId: String? = null
)

data class ExtractionInventoryItemView(
    val itemId: String,
    val displayLabel: String,
    val enchantLevel: Int,
    val available: Boolean,
    val blockedReasons: List<String>
)

data class ExtractionAttemptOptionView(
    val useRemovalScroll: Boolean,
    val useProtectionScroll: Boolean,
    val displayLabel: String,
    val available: Boolean,
    val blockedReasons: List<String>
)

data class ExtractionDetailView(
    val itemId: String,
    val itemLabel: String,
    val enchantLevel: Int,
    val attemptOptions: List<ExtractionAttemptOptionView>,
    val detailLines: List<String>
)

data class ExtractionPrepareResult(
    val ready: Boolean,
    val messages: List<String>,
    val timedActionView: ProductionTimedActionView? = null
)

data class ExtractionMutationResult(
    val state: rpg.model.GameState,
    val messages: List<String>,
    val selectedItemId: String? = null
)
