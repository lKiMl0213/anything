package rpg.classquest.progress

import rpg.achievement.AchievementTracker
import rpg.achievement.AchievementUpdate
import rpg.classquest.ClassQuestUnlockType
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.SpecializationUnlockProgress
import rpg.model.SpecializationUnlockStage
import rpg.model.SubclassUnlockProgress
import rpg.model.SubclassUnlockStage
import rpg.talent.TalentTreeService

internal class ClassProgressionSupport(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val talentTreeService: TalentTreeService,
    private val achievementTracker: AchievementTracker,
    private val applyAchievementUpdate: (AchievementUpdate) -> PlayerState,
    private val notify: (String) -> Unit
) {
    fun checkSubclassUnlock(state: GameState): GameState {
        val player = state.player
        if (player.subclassId != null) return state
        val classDef = repo.classes[player.classId] ?: return state
        if (classDef.secondClassIds.isEmpty()) return state
        val unlockLevel = classDef.secondClassUnlockLevel.coerceAtLeast(1)
        if (player.level < unlockLevel) return state

        val chosenPath = engine.classQuestService.completedPathFor(player, ClassQuestUnlockType.SUBCLASS)
            ?.lowercase()
            ?: return state
        if (chosenPath !in classDef.secondClassIds.map { it.lowercase() }.toSet()) return state
        val chosen = repo.subclasses[chosenPath] ?: return state
        if (chosen.parentClassId.lowercase() != classDef.id.lowercase()) return state

        val updatedProgress = player.subclassUnlockProgressByClass + (
            classDef.id to SubclassUnlockProgress(
                stage = SubclassUnlockStage.CHOSEN,
                questTemplateId = classDef.secondClassUnlockQuestTemplateId.orEmpty(),
                preparedAtLevel = player.level,
                preparedAtEpochMs = System.currentTimeMillis()
            )
        )
        var updated = player.copy(
            subclassId = chosen.id,
            subclassUnlockProgressByClass = updatedProgress
        )
        notify("2a classe liberada via quest de classe: ${chosen.name}.")
        updated = applyFullTalentReset(updated)
        notify("Reset completo de talentos aplicado. Pontos de talento devolvidos.")
        updated = applyAchievementUpdate(achievementTracker.onSubclassUnlocked(updated))
        updated = applyAchievementUpdate(achievementTracker.onClassResetTriggered(updated))
        return state.copy(player = updated)
    }

    fun checkSpecializationUnlock(state: GameState): GameState {
        val player = state.player
        if (player.subclassId == null) return state
        if (player.specializationId != null) return state
        val classDef = repo.classes[player.classId] ?: return state

        val unlockLevel = classDef.specializationUnlockLevel.coerceAtLeast(1)
        if (player.level < unlockLevel) return state

        val chosenPath = engine.classQuestService.completedPathFor(player, ClassQuestUnlockType.SPECIALIZATION)
            ?.lowercase()
            ?: return state
        val options = engine.classSystem.specializationOptions(classDef, player.subclassId)
        if (options.isEmpty()) return state
        val chosen = options.firstOrNull { it.id.lowercase() == chosenPath } ?: return state

        val updatedProgress = player.specializationUnlockProgressByClass + (
            classDef.id to SpecializationUnlockProgress(
                stage = SpecializationUnlockStage.CHOSEN,
                questTemplateId = classDef.specializationUnlockQuestTemplateId.orEmpty(),
                preparedAtLevel = player.level,
                preparedAtEpochMs = System.currentTimeMillis()
            )
        )
        var updated = player.copy(
            specializationId = chosen.id,
            specializationUnlockProgressByClass = updatedProgress
        )
        notify("Especializacao liberada via quest de classe: ${chosen.name}.")
        updated = applyAchievementUpdate(achievementTracker.onSpecializationUnlocked(updated))
        updated = applyFullTalentReset(updated)
        notify("Reset completo de talentos aplicado. Pontos de talento devolvidos.")
        updated = applyAchievementUpdate(achievementTracker.onClassResetTriggered(updated))
        return state.copy(player = updated)
    }

    fun applyFullTalentReset(player: PlayerState): PlayerState {
        val resetV2 = talentTreeService.buildResetState(player, repo.talentTreesV2.values)
        return resetV2.copy(
            talentNodeRanks = emptyMap(),
            unlockedTalentTrees = emptyList(),
            unspentSkillPoints = resetV2.unspentSkillPoints.coerceAtLeast(0)
        )
    }

    fun applySafeClassReset(player: PlayerState): PlayerState {
        val talentReset = applyFullTalentReset(player)
        val currentClass = player.classId.lowercase()
        val cleanedClassQuestProgress = talentReset.classQuestProgressByKey
            .filterValues { progress -> progress.classId.lowercase() != currentClass }
        return talentReset.copy(
            subclassId = null,
            specializationId = null,
            subclassUnlockProgressByClass = talentReset.subclassUnlockProgressByClass
                .filterKeys { it.lowercase() != currentClass },
            specializationUnlockProgressByClass = talentReset.specializationUnlockProgressByClass
                .filterKeys { it.lowercase() != currentClass },
            classQuestProgressByKey = cleanedClassQuestProgress
        )
    }
}
