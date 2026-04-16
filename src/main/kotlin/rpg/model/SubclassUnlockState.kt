package rpg.model

import kotlinx.serialization.Serializable

@Serializable
enum class SubclassUnlockStage {
    NONE,
    QUEST_PREPARED,
    READY_TO_CHOOSE,
    CHOSEN
}

@Serializable
data class SubclassUnlockProgress(
    val stage: SubclassUnlockStage = SubclassUnlockStage.NONE,
    val questTemplateId: String = "",
    val preparedAtLevel: Int = 0,
    val preparedAtEpochMs: Long = 0L
)
