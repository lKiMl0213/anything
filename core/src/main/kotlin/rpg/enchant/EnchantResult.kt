package rpg.enchant

import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.SkillSnapshot

data class EnchantAttemptRequest(
    val itemId: String,
    val enhancementRunes: Int = 0,
    val useProtectionRune: Boolean = false
)

data class EnchantAttemptPreview(
    val itemId: String,
    val itemName: String,
    val currentEnchantLevel: Int,
    val nextEnchantLevel: Int,
    val maxEnchantLevel: Int,
    val successChancePct: Double,
    val breakChancePct: Double,
    val goldCost: Int,
    val enhancementRunesRequired: Int,
    val useProtectionRune: Boolean,
    val durationSeconds: Double,
    val blockedReasons: List<String> = emptyList()
) {
    val available: Boolean get() = blockedReasons.isEmpty()
}

data class EnchantExecutionResult(
    val success: Boolean,
    val message: String,
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val targetItemId: String? = null,
    val destroyed: Boolean = false,
    val previousEnchantLevel: Int = 0,
    val newEnchantLevel: Int = 0,
    val consumedEnhancementRunes: Int = 0,
    val consumedProtectionRune: Boolean = false,
    val goldSpent: Int = 0,
    val gainedXp: Double = 0.0,
    val skillSnapshot: SkillSnapshot? = null,
    val preview: EnchantAttemptPreview? = null
)
