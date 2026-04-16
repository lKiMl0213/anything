package rpg.model

import kotlinx.serialization.Serializable

@Serializable
enum class GatheringType {
    MINING,
    HERBALISM,
    WOODCUTTING,
    FISHING
}

@Serializable
data class GatherNodeDef(
    val id: String,
    val name: String,
    val type: GatheringType,
    val resourceItemId: String,
    val minQty: Int = 1,
    val maxQty: Int = 1,
    val minPlayerLevel: Int = 1,
    val minSkillLevel: Int = 1,
    val skillType: SkillType? = null,
    val baseDurationSeconds: Double = 6.0,
    val baseXp: Double = 16.0,
    val difficulty: Double = 1.0,
    val tier: Int = 1,
    val description: String = "",
    val enabled: Boolean = true,
    val tags: List<String> = emptyList()
)
