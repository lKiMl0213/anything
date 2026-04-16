package rpg.model

import kotlinx.serialization.Serializable

@Serializable
enum class TalentNodeType {
    ACTIVE_SKILL,
    PASSIVE,
    MODIFIER,
    UPGRADE,
    CHOICE
}

@Serializable
enum class TalentPointMode {
    SHARED_POOL,
    PER_TREE
}

@Serializable
data class TalentPointPolicy(
    val mode: TalentPointMode = TalentPointMode.SHARED_POOL,
    val sharedPointsPerLevel: Int = 1,
    val sharedStartingPoints: Int = 0,
    val perTreePointsPerLevelDefault: Int = 1,
    val perTreePointsPerLevel: Map<String, Int> = emptyMap(),
    val perTreeStartingPoints: Map<String, Int> = emptyMap()
)

@Serializable
enum class TalentRequirementType {
    PLAYER_LEVEL,
    CLASS_ID,
    SUBCLASS_ID,
    SPECIALIZATION_ID,
    TREE_UNLOCKED,
    NODE_RANK_AT_LEAST
}

@Serializable
data class TalentRequirement(
    val type: TalentRequirementType,
    val targetId: String = "",
    val expectedValue: String = "",
    val minValue: Int = 0
)

@Serializable
data class TalentNodePrerequisite(
    val nodeId: String,
    val minRank: Int = 1
)

@Serializable
data class TalentNodeModifiers(
    val bonuses: Bonuses = Bonuses(),
    val combat: Map<String, Double> = emptyMap(),
    val status: Map<String, Double> = emptyMap(),
    val atb: Map<String, Double> = emptyMap(),
    val progression: Map<String, Double> = emptyMap(),
    val applyStatuses: List<CombatStatusApplyDef> = emptyList()
)

@Serializable
data class TalentNode(
    val id: String,
    val name: String,
    val description: String = "",
    val nodeType: TalentNodeType = TalentNodeType.PASSIVE,
    val maxRank: Int = 1,
    val currentRank: Int = 0,
    val costPerRank: List<Int> = listOf(1),
    val prerequisites: List<TalentNodePrerequisite> = emptyList(),
    val exclusiveGroup: String? = null,
    val unlocksSkillId: String? = null,
    val modifiers: TalentNodeModifiers = TalentNodeModifiers(),
    val tags: List<String> = emptyList()
)

@Serializable
data class TalentTree(
    val id: String,
    val classId: String,
    val tier: Int = 1,
    val nodes: List<TalentNode> = emptyList(),
    val unlockLevel: Int = 1,
    val requirements: List<TalentRequirement> = emptyList()
)
