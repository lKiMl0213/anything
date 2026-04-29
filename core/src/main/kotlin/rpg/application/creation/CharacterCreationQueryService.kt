package rpg.application.creation

import rpg.creation.CharacterCreationPreviewService
import rpg.io.DataRepository
import rpg.model.Attributes
import rpg.model.ClassDef
import rpg.model.RaceDef

data class CharacterCreationAttributeDetail(
    val code: String,
    val label: String,
    val directEffects: List<String>,
    val gameplayImpact: List<String>
)

class CharacterCreationQueryService(
    private val repo: DataRepository,
    private val previewService: CharacterCreationPreviewService
) {
    private val attributeMeta = listOf(
        "STR" to "Forca",
        "AGI" to "Agilidade",
        "DEX" to "Destreza",
        "VIT" to "Vitalidade",
        "INT" to "Inteligencia",
        "SPR" to "Espirito",
        "LUK" to "Sorte"
    )

    private val presetNames = listOf(
        "Aventureiro",
        "Guardiao",
        "Explorador",
        "Mercenario",
        "Arcanista",
        "Sentinela",
        "Viajante"
    )

    fun initialDraft(): CharacterCreationDraft {
        return CharacterCreationDraft(
            name = "",
            raceId = null,
            classId = null,
            totalPoints = repo.character.baseAttributePoints.coerceAtLeast(0),
            allocated = Attributes()
        )
    }

    fun cycleName(current: String): String {
        if (presetNames.isEmpty()) return current.ifBlank { "Aventureiro" }
        val normalized = current.trim()
        val index = presetNames.indexOfFirst { it.equals(normalized, ignoreCase = true) }
        return if (index < 0) presetNames.first() else presetNames[(index + 1) % presetNames.size]
    }

    fun availableRaces(): List<RaceDef> = previewService.availableRaces().sortedBy { it.name.lowercase() }

    fun availableClasses(): List<ClassDef> = previewService.availableClasses().sortedBy { it.name.lowercase() }

    fun raceById(id: String?): RaceDef? = id?.let(repo.races::get)

    fun classById(id: String?): ClassDef? = id?.let(repo.classes::get)

    fun attributeLabel(code: String): String? {
        return attributeMeta.firstOrNull { it.first.equals(code, ignoreCase = true) }?.second
    }

    fun attributeDetail(code: String): CharacterCreationAttributeDetail? {
        val upper = code.uppercase()
        val label = attributeLabel(upper) ?: return null
        return CharacterCreationAttributeDetail(
            code = upper,
            label = label,
            directEffects = generateAttributeDescription(upper),
            gameplayImpact = generateGameplayImpact(upper)
        )
    }

    fun raceSummaryLines(raceId: String?): List<String> {
        val race = raceById(raceId) ?: return listOf("Raca nao selecionada.")
        val preview = previewService.buildRacePreview(race)
        val lines = mutableListOf<String>()
        if (race.description.isNotBlank()) lines += race.description
        val attributes = preview.initialAttributes.takeIf { it.isNotEmpty() }?.joinToString(" | ") {
            "${it.code} +${it.value}"
        } ?: "Sem bonus inicial positivo."
        lines += "Bonus iniciais: $attributes"
        val growth = preview.growthAttributes.takeIf { it.isNotEmpty() }?.joinToString(" | ") {
            "${it.code} +${it.value}"
        } ?: "Sem crescimento positivo."
        lines += "Crescimento: $growth"
        if (preview.suggestedClasses.isNotEmpty()) {
            lines += "Classes sugeridas: " + preview.suggestedClasses.joinToString(" | ") { it.name }
        }
        return lines
    }

    fun classSummaryLines(classId: String?): List<String> {
        val clazz = classById(classId) ?: return listOf("Classe nao selecionada.")
        val preview = previewService.buildClassPreview(clazz)
        val lines = mutableListOf<String>()
        if (clazz.description.isNotBlank()) lines += clazz.description
        val initial = preview.initialAttributes.takeIf { it.isNotEmpty() }?.joinToString(" | ") {
            "${it.code} ${if (it.value >= 0) "+" else ""}${it.value}"
        } ?: "Sem bonus inicial."
        lines += "Atributos iniciais: $initial"
        if (preview.suggestedRaces.isNotEmpty()) {
            lines += "Racas sugeridas: " + preview.suggestedRaces.joinToString(" | ") { it.name }
        }
        if (preview.secondClasses.isNotEmpty()) {
            lines += "2as classes: " + preview.secondClasses.joinToString(" | ") { it.name }
        }
        return lines
    }

    fun attributeRows(draft: CharacterCreationDraft): List<CharacterCreationAttributeRow> {
        val raceAttrs = raceById(draft.raceId)?.bonuses?.attributes ?: Attributes()
        val classAttrs = classById(draft.classId)?.bonuses?.attributes ?: Attributes()
        return attributeMeta.map { (code, label) ->
            CharacterCreationAttributeRow(
                code = code,
                label = label,
                raceBonus = readAttr(raceAttrs, code),
                classBonus = readAttr(classAttrs, code),
                allocated = readAttr(draft.allocated, code)
            )
        }
    }

    fun readAllocated(attrs: Attributes, code: String): Int = readAttr(attrs, code)

    private fun readAttr(attrs: Attributes, code: String): Int = when (code.uppercase()) {
        "STR" -> attrs.str
        "AGI" -> attrs.agi
        "DEX" -> attrs.dex
        "VIT" -> attrs.vit
        "INT" -> attrs.`int`
        "SPR" -> attrs.spr
        "LUK" -> attrs.luk
        else -> 0
    }

    private fun generateAttributeDescription(code: String): List<String> = when (code.uppercase()) {
        "STR" -> listOf(
            "Contribui para dano fisico base (+2.5 por ponto).",
            "Aumenta penetracao fisica (+0.5 por ponto).",
            "Contribui para defesa fisica (+0.5 por ponto)."
        )
        "AGI" -> listOf(
            "Aumenta velocidade de ataque base (+0.02 por ponto).",
            "Aumenta esquiva/evasao (+0.8 por ponto).",
            "Aumenta velocidade de movimento (+0.05 por ponto)."
        )
        "DEX" -> listOf(
            "Contribui para dano fisico base (+1.2 por ponto).",
            "Aumenta precisao/acerto (+1.5 por ponto).",
            "Aumenta chance de critico (+0.3) e dano critico (+0.5)."
        )
        "VIT" -> listOf(
            "Aumenta HP maximo (+12 por ponto).",
            "Aumenta defesa fisica (+1.5 por ponto).",
            "Aumenta regeneracao de HP (+0.2 por ponto)."
        )
        "INT" -> listOf(
            "Aumenta dano magico base (+3.0 por ponto).",
            "Aumenta penetracao magica (+0.7 por ponto).",
            "Contribui para defesa magica (+0.7 por ponto)."
        )
        "SPR" -> listOf(
            "Aumenta MP maximo (+10 por ponto).",
            "Aumenta defesa magica (+1.8 por ponto).",
            "Aumenta regeneracao de MP (+0.3) e reducao de cooldown (+0.15%)."
        )
        "LUK" -> listOf(
            "Aumenta chance de critico (+0.2 por ponto).",
            "Aumenta vampirismo (1% a cada 10 pontos).",
            "Aumenta bonus de drop (+0.2% por ponto)."
        )
        else -> listOf("Sem descricao configurada.")
    }

    private fun generateGameplayImpact(code: String): List<String> = when (code.uppercase()) {
        "STR" -> listOf(
            "Mais STR aumenta o dano de ataques fisicos e melhora a consistencia contra defesa fisica.",
            "Builds corpo a corpo sentem ganho direto no burst."
        )
        "AGI" -> listOf(
            "Mais AGI acelera o ritmo do combate e ajuda a evitar golpes.",
            "Classes moveis aproveitam melhor esse atributo."
        )
        "DEX" -> listOf(
            "Mais DEX melhora acerto, critico e dano de ataques de precisao.",
            "Arqueiros e builds criticas escalam muito bem aqui."
        )
        "VIT" -> listOf(
            "Mais VIT aumenta margem de erro, sustain e resistencia fisica.",
            "Excelente para frontliners e setups defensivos."
        )
        "INT" -> listOf(
            "Mais INT aumenta dano magico e perfuracao magica.",
            "Magos ofensivos dependem fortemente deste atributo."
        )
        "SPR" -> listOf(
            "Mais SPR aumenta mana, sustain magico e defesa contra magia.",
            "Tambem acelera cooldowns de forma gradual."
        )
        "LUK" -> listOf(
            "Mais LUK melhora critico, drop e vampirismo.",
            "Bom atributo complementar para builds oportunistas."
        )
        else -> listOf("Sem impacto configurado.")
    }
}
