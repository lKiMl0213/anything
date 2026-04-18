package rpg.classsystem

import rpg.io.DataRepository
import rpg.model.Bonuses
import rpg.model.ClassDef
import rpg.model.PlayerState
import rpg.model.RaceDef
import rpg.model.SpecializationDef
import rpg.model.SubclassDef
import rpg.talent.TalentTreeService

class ClassSystem(private val repo: DataRepository) {
    private val talentTreeService = TalentTreeService(repo.balance.talentPoints)

    fun classDef(id: String): ClassDef {
        val normalizedId = id.trim().lowercase()
        return repo.classes[normalizedId] ?: error("Classe nao encontrada: $id")
    }

    fun raceDef(id: String): RaceDef = repo.races[id] ?: error("Raca nao encontrada: $id")

    fun subclassDef(id: String?): SubclassDef? {
        return id
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?.let { repo.subclasses[it] }
    }

    fun specializationDef(id: String?): SpecializationDef? {
        return id
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?.let { repo.specializations[it] }
    }

    fun secondClassOptions(classDef: ClassDef): List<SubclassDef> {
        return classDef.secondClassIds
            .mapNotNull { repo.subclasses[it] }
            .filter { it.parentClassId.equals(classDef.id, ignoreCase = true) }
    }

    fun specializationOptions(classDef: ClassDef, subclassId: String?): List<SpecializationDef> {
        val subclass = subclassDef(subclassId) ?: return emptyList()
        if (!subclass.parentClassId.equals(classDef.id, ignoreCase = true)) return emptyList()
        return subclass.specializationIds
            .mapNotNull { repo.specializations[it] }
            .filter { specialization ->
                specialization.parentClassId.equals(classDef.id, ignoreCase = true) &&
                    specialization.parentSubclassId.equals(subclass.id, ignoreCase = true)
            }
    }

    fun sanitizePlayerHierarchy(player: PlayerState): PlayerState {
        val normalizedClassId = player.classId.trim().lowercase()
        val classDef = repo.classes[normalizedClassId]
            ?: return player.copy(
                classId = normalizedClassId,
                subclassId = null,
                specializationId = null
            )

        val normalizedSubclassId = player.subclassId
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        val sanitizedSubclassId = normalizedSubclassId?.takeIf { secondClassId ->
            secondClassOptions(classDef).any { it.id.equals(secondClassId, ignoreCase = true) }
        }

        val normalizedSpecializationId = player.specializationId
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        val sanitizedSpecializationId = normalizedSpecializationId?.takeIf { specializationId ->
            specializationOptions(classDef, sanitizedSubclassId)
                .any { it.id.equals(specializationId, ignoreCase = true) }
        }

        if (
            normalizedClassId == player.classId &&
            sanitizedSubclassId == normalizedSubclassId &&
            sanitizedSpecializationId == normalizedSpecializationId
        ) {
            return player
        }

        return player.copy(
            classId = normalizedClassId,
            subclassId = sanitizedSubclassId,
            specializationId = sanitizedSpecializationId
        )
    }

    fun totalBonuses(player: PlayerState): Bonuses {
        val classDef = classDef(player.classId)
        val raceDef = raceDef(player.raceId)
        val subclass = subclassDef(player.subclassId)
        val specialization = specializationDef(player.specializationId)
        val v2TalentBonuses = talentTreeService.collectBonuses(
            player = player,
            trees = talentTreeService.activeTrees(player, repo.talentTreesV2.values)
        )
        val subclassBonuses = subclass?.bonuses ?: Bonuses()
        val specializationBonuses = specialization?.bonuses ?: Bonuses()
        return classDef.bonuses + raceDef.bonuses + subclassBonuses + specializationBonuses + v2TalentBonuses
    }
}
