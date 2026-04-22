package rpg.classquest

import rpg.model.ItemInstance
import rpg.model.PlayerState

data class ClassQuestUpdate(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val messages: List<String> = emptyList(),
    val grantedItems: Map<String, Int> = emptyMap()
)

data class ClassQuestContext(
    val definition: ClassQuestDefinition,
    val progress: ClassQuestProgress
)

data class ClassQuestStageSnapshot(
    val stage: ClassQuestStageDefinition,
    val mobTargets: Set<String>,
    val mobBaseTypes: Set<String>,
    val bossTargets: Set<String>,
    val bossBaseTypes: Set<String>,
    val collectTargets: Set<String>,
    val finalBossTargets: Set<String>
)

data class ClassQuestDungeonMonster(
    val monsterId: String,
    val displayName: String,
    val baseArchetypeId: String,
    val baseType: String,
    val family: String,
    val lootProfileId: String,
    val identityTags: Set<String> = emptySet()
)

data class ClassQuestDungeonDefinition(
    val unlockType: ClassQuestUnlockType,
    val classId: String,
    val pathId: String,
    val pathName: String,
    val normalMonsters: List<ClassQuestDungeonMonster>,
    val bossMonsters: List<ClassQuestDungeonMonster>,
    val finalBoss: ClassQuestDungeonMonster,
    val collectibleTemplateId: String,
    val collectibleName: String
) {
    fun normalIds(): Set<String> = normalMonsters.map { it.monsterId }.toSet()
    fun bossIds(): Set<String> = bossMonsters.map { it.monsterId }.toSet()
    fun finalBossIds(): Set<String> = setOf(finalBoss.monsterId)
    fun allIds(): Set<String> = normalIds() + bossIds() + finalBossIds()
}
