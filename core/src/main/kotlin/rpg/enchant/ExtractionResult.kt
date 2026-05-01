package rpg.enchant

import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.SkillSnapshot

data class ExtractionRequest(
    val itemId: String,
    val useRemovalScroll: Boolean = false,
    val useProtectionScroll: Boolean = false
)

data class ExtractionPreview(
    val itemId: String,
    val itemName: String,
    val currentEnchantLevel: Int,
    val stoneEnchantLevel: Int,
    val successChancePct: Double,
    val goldCost: Int,
    val durationSeconds: Double,
    val useRemovalScroll: Boolean,
    val useProtectionScroll: Boolean,
    val blockedReasons: List<String> = emptyList()
) {
    val available: Boolean get() = blockedReasons.isEmpty()
}

data class ExtractionExecutionResult(
    val success: Boolean,
    val message: String,
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val extractedStoneId: String? = null,
    val extractedEnchantLevel: Int = 0,
    val itemDestroyed: Boolean = false,
    val itemResetToZero: Boolean = false,
    val goldSpent: Int = 0,
    val gainedXp: Double = 0.0,
    val skillSnapshot: SkillSnapshot? = null,
    val preview: ExtractionPreview? = null
)

