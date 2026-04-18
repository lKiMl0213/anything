package rpg.classquest

import kotlinx.serialization.Serializable
import rpg.model.EquipSlot
import rpg.model.PlayerState

@Serializable
enum class ClassQuestUnlockType {
    SUBCLASS,
    SPECIALIZATION
}

@Serializable
enum class ClassQuestStatus {
    NOT_AVAILABLE,
    AVAILABLE,
    IN_PROGRESS,
    COMPLETED,
    CANCELED
}

@Serializable
data class ClassQuestProgress(
    val classId: String = "",
    val unlockType: ClassQuestUnlockType = ClassQuestUnlockType.SUBCLASS,
    val status: ClassQuestStatus = ClassQuestStatus.NOT_AVAILABLE,
    val chosenPath: String? = null,
    val currentStage: Int = 1,
    val killCount: Int = 0,
    val collectCount: Int = 0,
    val bossKillCount: Int = 0,
    val finalBossKilled: Boolean = false,
    val rewardsClaimed: List<Int> = emptyList()
)

data class ClassQuestReward(
    val xp: Int,
    val gold: Int,
    val hpPotionId: String = "hp_potion_medium",
    val hpPotionQty: Int = 1,
    val mpPotionId: String = "mp_potion_medium",
    val mpPotionQty: Int = 1,
    val equipmentSlots: List<EquipSlot> = emptyList()
)

data class ClassQuestStageDefinition(
    val stage: Int,
    val killTarget: Int = 0,
    val collectTarget: Int = 0,
    val bossKillTarget: Int = 0,
    val requiresFinalBoss: Boolean = false,
    val reward: ClassQuestReward
)

data class ClassQuestDefinition(
    val classId: String,
    val className: String,
    val unlockType: ClassQuestUnlockType,
    val unlockLevel: Int,
    val pathA: String,
    val pathAName: String,
    val pathB: String,
    val pathBName: String,
    val stages: List<ClassQuestStageDefinition>
) {
    fun paths(): List<String> = listOf(pathA, pathB)
}

object ClassQuestTagRules {
    const val anyClassTag = "any.class"

    private const val classLockPrefix = "classlocked:"
    private const val pathLockPrefix = "pathlocked:"
    private const val sellValuePrefix = "sellvalue:"
    private const val questRewardFlag = "questreward:true"
    private const val classTagPrefix = "class:"
    private const val classTagAltPrefix = "classtag:"

    fun classLocked(tags: List<String>): String? {
        return tags.firstNotNullOfOrNull { tag ->
            val normalized = tag.trim().lowercase()
            if (!normalized.startsWith(classLockPrefix)) return@firstNotNullOfOrNull null
            normalized.removePrefix(classLockPrefix).takeIf { it.isNotBlank() }
        }
    }

    fun pathLocked(tags: List<String>): String? {
        return tags.firstNotNullOfOrNull { tag ->
            val normalized = tag.trim().lowercase()
            if (!normalized.startsWith(pathLockPrefix)) return@firstNotNullOfOrNull null
            normalized.removePrefix(pathLockPrefix).takeIf { it.isNotBlank() }
        }
    }

    fun forcedSellValue(tags: List<String>): Int? {
        return tags.firstNotNullOfOrNull { tag ->
            val normalized = tag.trim().lowercase()
            if (!normalized.startsWith(sellValuePrefix)) return@firstNotNullOfOrNull null
            normalized.removePrefix(sellValuePrefix).toIntOrNull()
        }
    }

    fun isQuestReward(tags: List<String>): Boolean {
        return tags.any { it.trim().lowercase() == questRewardFlag }
    }

    fun classTag(tags: List<String>): String? = classTags(tags).firstOrNull()

    fun classTags(tags: List<String>): List<String> {
        return tags.mapNotNull { parseExplicitClassTag(it) }.distinct()
    }

    fun effectiveClassTag(tags: List<String>): String {
        return classTag(tags)
            ?: pathLocked(tags)
            ?: classLocked(tags)
            ?: anyClassTag
    }

    fun allowedClassHierarchy(player: PlayerState): Set<String> {
        val allowed = linkedSetOf<String>()
        allowed += player.classId.lowercase()
        player.subclassId?.lowercase()?.let { allowed += it }
        player.specializationId?.lowercase()?.let { allowed += it }
        for (progress in player.classQuestProgressByKey.values) {
            progress.chosenPath?.lowercase()?.takeIf { it.isNotBlank() }?.let { allowed += it }
        }
        return allowed
    }

    fun canUseClassTag(player: PlayerState, classTag: String?): Boolean {
        val normalized = classTag?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank() || normalized == anyClassTag) return true
        return allowedClassHierarchy(player).contains(normalized)
    }

    fun canEquip(player: PlayerState, tags: List<String>): Boolean {
        if (!canUseClassTag(player, effectiveClassTag(tags))) {
            return false
        }

        val classLock = classLocked(tags)
        if (classLock != null && classLock != player.classId.lowercase()) {
            return false
        }

        val pathLock = pathLocked(tags)
        if (pathLock != null) {
            val ownerClass = classLock ?: player.classId.lowercase()
            if (!allowedPaths(player, ownerClass).contains(pathLock)) {
                return false
            }
        }
        return true
    }

    fun allowedPaths(player: PlayerState, classId: String): Set<String> {
        val allowed = linkedSetOf<String>()
        player.subclassId?.lowercase()?.let { allowed += it }
        player.specializationId?.lowercase()?.let { allowed += it }
        for (progress in player.classQuestProgressByKey.values) {
            if (progress.classId.lowercase() != classId.lowercase()) continue
            progress.chosenPath?.lowercase()?.let { allowed += it }
        }
        return allowed
    }

    fun classTagLiteral(classId: String): String {
        return "{${classId.trim().lowercase()}}"
    }

    private fun parseExplicitClassTag(tag: String): String? {
        val trimmed = tag.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return normalizeClassToken(trimmed.removePrefix("{").removeSuffix("}"))
        }
        val normalized = trimmed.lowercase()
        if (normalized.startsWith(classTagPrefix)) {
            return normalizeClassToken(normalized.removePrefix(classTagPrefix))
        }
        if (normalized.startsWith(classTagAltPrefix)) {
            return normalizeClassToken(normalized.removePrefix(classTagAltPrefix))
        }
        return null
    }

    private fun normalizeClassToken(value: String): String? {
        val normalized = value.trim().lowercase()
        return normalized.takeIf { it.isNotBlank() }
    }
}
