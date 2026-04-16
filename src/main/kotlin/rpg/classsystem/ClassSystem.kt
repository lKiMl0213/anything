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

    fun classDef(id: String): ClassDef = repo.classes[id] ?: error("Classe nao encontrada: $id")

    fun raceDef(id: String): RaceDef = repo.races[id] ?: error("Raca nao encontrada: $id")

    fun subclassDef(id: String?): SubclassDef? = id?.let { repo.subclasses[it] }

    fun specializationDef(id: String?): SpecializationDef? = id?.let { repo.specializations[it] }

    fun specializationOptions(classDef: ClassDef, subclassId: String?): List<SpecializationDef> {
        return classDef.specializationIds
            .mapNotNull { repo.specializations[it] }
            .filter { specialization ->
                specialization.parentClassId == classDef.id &&
                    (specialization.requiredSubclassIds.isEmpty() || specialization.requiredSubclassIds.contains(subclassId))
            }
    }

    fun totalBonuses(player: PlayerState): Bonuses {
        val classDef = classDef(player.classId)
        val raceDef = raceDef(player.raceId)
        val subclass = subclassDef(player.subclassId)
        val specialization = specializationDef(player.specializationId)
        val talentTrees = collectTalentTrees(classDef, subclass, specialization)
        val talentBonuses = TalentEngine.collectTalentBonuses(talentTrees, player.talents)
        val v2TalentBonuses = talentTreeService.collectBonuses(
            player = player,
            trees = talentTreeService.activeTrees(player, repo.talentTreesV2.values)
        )
        val subclassBonuses = subclass?.bonuses ?: Bonuses()
        val specializationBonuses = specialization?.bonuses ?: Bonuses()
        return classDef.bonuses + raceDef.bonuses + subclassBonuses + specializationBonuses + talentBonuses + v2TalentBonuses
    }

    fun collectTalentTrees(
        classDef: ClassDef,
        subclass: SubclassDef?,
        specialization: SpecializationDef? = null
    ): List<rpg.model.TalentTreeDef> {
        val trees = mutableListOf<rpg.model.TalentTreeDef>()
        classDef.talentTreeId?.let { repo.talentTrees[it]?.let(trees::add) }
        subclass?.talentTreeId?.let { repo.talentTrees[it]?.let(trees::add) }
        specialization?.talentTreeId?.let { repo.talentTrees[it]?.let(trees::add) }
        return trees
    }
}
