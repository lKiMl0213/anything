package rpg.classquest

import rpg.classsystem.ClassSystem
import rpg.io.DataRepository
import rpg.model.EquipSlot
import rpg.model.PlayerState

internal class ClassQuestPathCatalog(
    private val repo: DataRepository,
    private val classSystem: ClassSystem
) {
    fun pathName(unlockType: ClassQuestUnlockType, pathId: String): String {
        val id = pathId.lowercase()
        return when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> repo.subclasses[id]?.name ?: id
            ClassQuestUnlockType.SPECIALIZATION -> repo.specializations[id]?.name ?: id
        }
    }

    fun unlockLabel(unlockType: ClassQuestUnlockType): String = when (unlockType) {
        ClassQuestUnlockType.SUBCLASS -> "2a Classe"
        ClassQuestUnlockType.SPECIALIZATION -> "Especializacao"
    }

    fun activeDefinition(player: PlayerState): ClassQuestDefinition? {
        if (player.subclassId == null) {
            return definitionFor(player, ClassQuestUnlockType.SUBCLASS)
        }
        if (player.specializationId == null) {
            return definitionFor(player, ClassQuestUnlockType.SPECIALIZATION)
        }
        return null
    }

    fun definitionFor(
        player: PlayerState,
        unlockType: ClassQuestUnlockType
    ): ClassQuestDefinition? {
        val classDef = runCatching { classSystem.classDef(player.classId) }.getOrNull() ?: return null
        val paths = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> classDef.secondClassIds
            ClassQuestUnlockType.SPECIALIZATION -> classSystem
                .specializationOptions(classDef, player.subclassId)
                .map { it.id }
        }.map { it.lowercase() }.distinct()
        if (paths.size < 2) return null

        val pathA = paths[0]
        val pathB = paths[1]
        val unlockLevel = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> classDef.secondClassUnlockLevel.coerceAtLeast(1)
            ClassQuestUnlockType.SPECIALIZATION -> classDef.specializationUnlockLevel.coerceAtLeast(1)
        }
        return ClassQuestDefinition(
            classId = classDef.id.lowercase(),
            className = classDef.name,
            unlockType = unlockType,
            unlockLevel = unlockLevel,
            pathA = pathA,
            pathAName = pathName(unlockType, pathA),
            pathB = pathB,
            pathBName = pathName(unlockType, pathB),
            stages = stageDefinitions(unlockType, classDef.id)
        )
    }

    fun shouldShowQuest(
        player: PlayerState,
        definition: ClassQuestDefinition
    ): Boolean {
        if (player.level < definition.unlockLevel) return false
        return when (definition.unlockType) {
            ClassQuestUnlockType.SUBCLASS -> player.subclassId == null
            ClassQuestUnlockType.SPECIALIZATION ->
                player.subclassId != null &&
                    player.specializationId == null &&
                    classSystem.specializationOptions(
                        classSystem.classDef(player.classId),
                        player.subclassId
                    ).size >= 2
        }
    }

    private fun stageDefinitions(
        unlockType: ClassQuestUnlockType,
        classId: String
    ): List<ClassQuestStageDefinition> {
        val collectStage2 = if (unlockType == ClassQuestUnlockType.SUBCLASS) 15 else 20
        val collectStage4 = if (unlockType == ClassQuestUnlockType.SUBCLASS) 20 else 25
        val killStage4 = if (unlockType == ClassQuestUnlockType.SUBCLASS) 20 else 30
        val finalStageSlots = if (classId.lowercase() == "archer") {
            listOf(EquipSlot.WEAPON_MAIN, EquipSlot.ALJAVA)
        } else {
            listOf(EquipSlot.WEAPON_MAIN)
        }
        return listOf(
            ClassQuestStageDefinition(
                stage = 1,
                killTarget = 25,
                reward = stageReward(unlockType, stage = 1, equipment = listOf(EquipSlot.HEAD))
            ),
            ClassQuestStageDefinition(
                stage = 2,
                collectTarget = collectStage2,
                reward = stageReward(unlockType, stage = 2, equipment = listOf(EquipSlot.CHEST, EquipSlot.GLOVES))
            ),
            ClassQuestStageDefinition(
                stage = 3,
                bossKillTarget = 10,
                reward = stageReward(unlockType, stage = 3, equipment = listOf(EquipSlot.LEGS, EquipSlot.BOOTS))
            ),
            ClassQuestStageDefinition(
                stage = 4,
                collectTarget = collectStage4,
                killTarget = killStage4,
                requiresFinalBoss = true,
                reward = stageReward(unlockType, stage = 4, equipment = finalStageSlots)
            )
        )
    }

    private fun stageReward(
        unlockType: ClassQuestUnlockType,
        stage: Int,
        equipment: List<EquipSlot>
    ): ClassQuestReward {
        val (xp, gold) = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> when (stage) {
                1 -> 700 to 130
                2 -> 1050 to 200
                3 -> 1600 to 300
                else -> 2400 to 420
            }
            ClassQuestUnlockType.SPECIALIZATION -> when (stage) {
                1 -> 1600 to 280
                2 -> 2300 to 420
                3 -> 3200 to 620
                else -> 4700 to 900
            }
        }
        return ClassQuestReward(
            xp = xp,
            gold = gold,
            hpPotionId = "hp_potion_medium",
            hpPotionQty = 1,
            mpPotionId = "mp_potion_medium",
            mpPotionQty = 1,
            equipmentSlots = equipment
        )
    }
}
