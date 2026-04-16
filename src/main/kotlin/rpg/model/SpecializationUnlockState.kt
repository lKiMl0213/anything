package rpg.model

import kotlinx.serialization.Serializable

@Serializable
enum class SpecializationUnlockStage {
    NONE,
    QUEST_PREPARED,
    READY_TO_CHOOSE,
    CHOSEN
}

@Serializable
data class SpecializationUnlockProgress(
    val stage: SpecializationUnlockStage = SpecializationUnlockStage.NONE,
    val questTemplateId: String = "",
    val preparedAtLevel: Int = 0,
    val preparedAtEpochMs: Long = 0L
)
