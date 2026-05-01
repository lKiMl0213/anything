package rpg.enchant

import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.SkillSnapshot

enum class FusionMode(val configKey: String) {
    EQUIPMENT_EQUIPMENT("equipment_equipment"),
    STONE_STONE("stone_stone"),
    STONE_EQUIPMENT("stone_equipment")
}

data class FusionRequest(
    val slot1ItemId: String,
    val slot2ItemId: String
)

data class FusionPreview(
    val slot1ItemId: String,
    val slot2ItemId: String,
    val slot1Label: String,
    val slot2Label: String,
    val mode: FusionMode?,
    val baseEnchantLevel: Int,
    val successNormalEnchantLevel: Int,
    val successUpgradeEnchantLevel: Int,
    val failureMinEnchantLevel: Int,
    val failureMaxEnchantLevel: Int,
    val successChancePct: Double,
    val goldCost: Int,
    val durationSeconds: Double,
    val blockedReasons: List<String> = emptyList()
) {
    val available: Boolean get() = blockedReasons.isEmpty()
}

data class FusionExecutionResult(
    val success: Boolean,
    val message: String,
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val outputItemId: String? = null,
    val outputEnchantLevel: Int = 0,
    val goldSpent: Int = 0,
    val gainedXp: Double = 0.0,
    val skillSnapshot: SkillSnapshot? = null,
    val preview: FusionPreview? = null
)

