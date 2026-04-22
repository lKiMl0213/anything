package rpg.creation

import rpg.io.DataRepository
import rpg.model.Attributes
import rpg.model.ClassDef
import rpg.model.RaceDef
import rpg.model.SpecializationDef
import rpg.model.SubclassDef

class CharacterCreationPreviewService(private val repo: DataRepository) {
    data class AttributePreviewLine(
        val code: String,
        val label: String,
        val value: Int
    )

    data class CompatibilitySuggestion(
        val id: String,
        val name: String,
        val score: Double,
        val reason: String
    )

    data class SecondClassPreview(
        val id: String,
        val name: String,
        val specializations: List<String>
    )

    data class RacePreview(
        val race: RaceDef,
        val initialAttributes: List<AttributePreviewLine>,
        val growthAttributes: List<AttributePreviewLine>,
        val suggestedClasses: List<CompatibilitySuggestion>
    )

    data class ClassPreview(
        val clazz: ClassDef,
        val initialAttributes: List<AttributePreviewLine>,
        val growthAttributes: List<AttributePreviewLine>,
        val suggestedRaces: List<CompatibilitySuggestion>,
        val secondClasses: List<SecondClassPreview>
    )

    private data class AttrMeta(
        val code: String,
        val label: String
    )

    private data class CompatibilityComputation(
        val score: Double,
        val contributions: List<Pair<String, Double>>
    )

    private val attrMeta = listOf(
        AttrMeta("STR", "Forca"),
        AttrMeta("AGI", "Agilidade"),
        AttrMeta("DEX", "Destreza"),
        AttrMeta("VIT", "Vitalidade"),
        AttrMeta("INT", "Inteligencia"),
        AttrMeta("SPR", "Espirito"),
        AttrMeta("LUK", "Sorte")
    )

    fun availableRaces(): List<RaceDef> = repo.races.values.sortedBy { it.name }

    fun availableClasses(): List<ClassDef> = repo.classes.values.sortedBy { it.name }

    fun buildRacePreview(race: RaceDef, suggestionLimit: Int = 3): RacePreview {
        return RacePreview(
            race = race,
            initialAttributes = filterPositiveAttributes(race.bonuses.attributes),
            growthAttributes = filterPositiveAttributes(race.growth),
            suggestedClasses = suggestedClassesForRace(race, suggestionLimit)
        )
    }

    fun buildClassPreview(classDef: ClassDef, suggestionLimit: Int = 3): ClassPreview {
        return ClassPreview(
            clazz = classDef,
            initialAttributes = filterNonZeroAttributes(classDef.bonuses.attributes),
            growthAttributes = filterNonZeroAttributes(classDef.growth),
            suggestedRaces = suggestedRacesForClass(classDef, suggestionLimit),
            secondClasses = resolveSecondClassPreviews(classDef)
        )
    }

    private fun filterPositiveAttributes(attributes: Attributes): List<AttributePreviewLine> {
        return attrMeta.mapNotNull { meta ->
            val value = getAttr(attributes, meta.code)
            if (value > 0) AttributePreviewLine(meta.code, meta.label, value) else null
        }
    }

    private fun filterNonZeroAttributes(attributes: Attributes): List<AttributePreviewLine> {
        return attrMeta.mapNotNull { meta ->
            val value = getAttr(attributes, meta.code)
            if (value != 0) AttributePreviewLine(meta.code, meta.label, value) else null
        }
    }

    private fun suggestedClassesForRace(race: RaceDef, limit: Int): List<CompatibilitySuggestion> {
        return repo.classes.values
            .map { classDef ->
                val computation = computeCompatibility(race, classDef)
                CompatibilitySuggestion(
                    id = classDef.id,
                    name = classDef.name,
                    score = computation.score,
                    reason = buildCompatibilityReason(race, computation.contributions)
                )
            }
            .sortedWith(compareByDescending<CompatibilitySuggestion> { it.score }.thenBy { it.name })
            .take(limit.coerceAtLeast(1))
    }

    private fun suggestedRacesForClass(classDef: ClassDef, limit: Int): List<CompatibilitySuggestion> {
        return repo.races.values
            .map { race ->
                val computation = computeCompatibility(race, classDef)
                CompatibilitySuggestion(
                    id = race.id,
                    name = race.name,
                    score = computation.score,
                    reason = buildCompatibilityReason(race, computation.contributions)
                )
            }
            .sortedWith(compareByDescending<CompatibilitySuggestion> { it.score }.thenBy { it.name })
            .take(limit.coerceAtLeast(1))
    }

    private fun computeCompatibility(race: RaceDef, classDef: ClassDef): CompatibilityComputation {
        val raceVector = buildRaceVector(race)
        val classVector = buildClassVector(classDef)
        val contributions = attrMeta.map { meta ->
            val weight = (raceVector[meta.code] ?: 0.0) * (classVector[meta.code] ?: 0.0)
            meta.code to weight
        }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
        val rawScore = contributions.sumOf { it.second }
        val normalizedByClassDemand = classVector.values.sum().coerceAtLeast(1.0)
        return CompatibilityComputation(
            score = rawScore / normalizedByClassDemand,
            contributions = contributions
        )
    }

    private fun buildRaceVector(race: RaceDef): Map<String, Double> {
        val raceBaseWeight = 1.0
        val raceGrowthWeight = 1.35
        return attrMeta.associate { meta ->
            val base = getAttr(race.bonuses.attributes, meta.code).coerceAtLeast(0)
            val growth = getAttr(race.growth, meta.code).coerceAtLeast(0)
            meta.code to (base * raceBaseWeight + growth * raceGrowthWeight)
        }
    }

    private fun buildClassVector(classDef: ClassDef): Map<String, Double> {
        val autoPointWeight = 2.5
        val classBaseWeight = 1.0
        val classGrowthWeight = 1.5
        return attrMeta.associate { meta ->
            val autoPoints = getAttr(classDef.autoPointWeights, meta.code).coerceAtLeast(0)
            val base = getAttr(classDef.bonuses.attributes, meta.code).coerceAtLeast(0)
            val growth = getAttr(classDef.growth, meta.code).coerceAtLeast(0)
            meta.code to (autoPoints * autoPointWeight + base * classBaseWeight + growth * classGrowthWeight)
        }
    }

    private fun buildCompatibilityReason(race: RaceDef, contributions: List<Pair<String, Double>>): String {
        if (contributions.isEmpty()) {
            return "Sem atributo dominante compartilhado."
        }
        val top = contributions.take(2)
        val fragments = top.map { (code, _) ->
            val base = getAttr(race.bonuses.attributes, code)
            val growth = getAttr(race.growth, code)
            when {
                base > 0 && growth > 0 -> "$code inicial + crescimento"
                base > 0 -> "$code inicial forte"
                growth > 0 -> "crescimento em $code"
                else -> "sinergia em $code"
            }
        }.distinct()
        return fragments.joinToString(" | ")
    }

    private fun resolveSecondClassPreviews(classDef: ClassDef): List<SecondClassPreview> {
        return classDef.secondClassIds
            .mapNotNull { repo.subclasses[it] }
            .filter { it.parentClassId.equals(classDef.id, ignoreCase = true) }
            .distinctBy { it.id }
            .map { secondClass ->
                SecondClassPreview(
                    id = secondClass.id,
                    name = secondClass.name,
                    specializations = secondClass.specializationIds.map { specializationId ->
                        repo.specializations[specializationId]?.name ?: specializationId
                    }
                )
            }
    }

    private fun getAttr(attrs: Attributes, code: String): Int = when (code) {
        "STR" -> attrs.str
        "AGI" -> attrs.agi
        "DEX" -> attrs.dex
        "VIT" -> attrs.vit
        "INT" -> attrs.`int`
        "SPR" -> attrs.spr
        "LUK" -> attrs.luk
        else -> 0
    }
}
