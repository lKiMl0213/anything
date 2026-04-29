// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.cli.model.AttrMeta
import rpg.cli.model.AttributeDistributionState
import rpg.cli.model.InitialAttributeAllocation
import rpg.model.Attributes
import rpg.model.ClassDef
import rpg.model.RaceDef

internal class LegacyAttributeAllocationFlow(
    private val attributeMeta: List<AttrMeta>
) {
    fun allocateAttributesWithBonuses(
        points: Int,
        raceDef: RaceDef,
        classDef: ClassDef
    ): InitialAttributeAllocation {
        while (true) {
            val allocation = runGuidedAttributeDistribution(points, raceDef, classDef)
            renderAttributeDistributionSummary(
                base = allocation.baseAttributes,
                raceDef = raceDef,
                classDef = classDef,
                remainingPoints = allocation.unspentPoints
            )
            if (confirmAttributeDistribution()) {
                return allocation
            }
            println("Redistribuindo atributos...")
        }
    }

    private fun runGuidedAttributeDistribution(
        points: Int,
        raceDef: RaceDef,
        classDef: ClassDef
    ): InitialAttributeAllocation {
        var state = AttributeDistributionState(allocated = Attributes(), remainingPoints = points.coerceAtLeast(0))
        for (meta in attributeMeta) {
            state = promptAttributeStep(meta, state, raceDef, classDef)
        }
        return InitialAttributeAllocation(baseAttributes = state.allocated, unspentPoints = state.remainingPoints)
    }

    private fun promptAttributeStep(
        attribute: AttrMeta,
        initial: AttributeDistributionState,
        raceDef: RaceDef,
        classDef: ClassDef
    ): AttributeDistributionState {
        var state = initial
        while (true) {
            renderAttributeStep(attribute, state, raceDef, classDef)
            val toAllocate = readAttributeStepInput(state.remainingPoints)
            return state.copy(
                allocated = applyAttributePoints(state.allocated, attribute.code, toAllocate),
                remainingPoints = state.remainingPoints - toAllocate
            )
        }
    }

    private fun renderAttributeStep(
        attribute: AttrMeta,
        state: AttributeDistributionState,
        raceDef: RaceDef,
        classDef: ClassDef
    ) {
        val raceBonus = getAttr(raceDef.bonuses.attributes, attribute.code)
        val classBonus = getAttr(classDef.bonuses.attributes, attribute.code)
        val allocated = getAttr(state.allocated, attribute.code)
        val projectedTotal = allocated + raceBonus + classBonus

        println("\n----------------------------------")
        println("Distribuicao de atributos")
        println()
        println("Atributos restantes: ${state.remainingPoints}")
        println()
        println("${attribute.code} (${attribute.label})")
        println()
        println(
            "Atual: base $allocated | bonus racial ${formatSigned(raceBonus)} | " +
                "bonus classe ${formatSigned(classBonus)} | total projetado $projectedTotal"
        )
        println()
        println("Descricao:")
        generateAttributeDescription(attribute).forEach { println("- $it") }
        println()
        println("Impacto na gameplay:")
        generateGameplayImpact(attribute).forEach { println("- $it") }
        println()
    }

    private fun readAttributeStepInput(remainingPoints: Int): Int {
        while (true) {
            val input = readLine()?.trim() ?: throw IllegalStateException("Entrada encerrada.")
            val parsed = input.toIntOrNull()
            if (parsed != null && parsed in 0..remainingPoints) {
                return parsed
            }
            println("Entrada invalida. Informe um numero entre 0 e $remainingPoints.")
        }
    }

    private fun renderAttributeDistributionSummary(
        base: Attributes,
        raceDef: RaceDef,
        classDef: ClassDef,
        remainingPoints: Int
    ) {
        println("\nResumo final:")
        println()
        attributeMeta.forEach { meta ->
            val baseValue = getAttr(base, meta.code)
            val raceBonus = getAttr(raceDef.bonuses.attributes, meta.code)
            val classBonus = getAttr(classDef.bonuses.attributes, meta.code)
            val totalBonus = raceBonus + classBonus
            val total = baseValue + totalBonus
            println("${meta.code} = $baseValue (${formatSigned(totalBonus)}) -> Total $total")
            println("   Bonus racial: ${formatSigned(raceBonus)} | Bonus classe: ${formatSigned(classBonus)}")
        }
        if (remainingPoints > 0) {
            println("\nPontos nao distribuidos: $remainingPoints")
        }
    }

    private fun confirmAttributeDistribution(): Boolean {
        while (true) {
            println("Deseja confirmar a selecao de atributos? (S/N)")
            val input = readLine()?.trim()?.uppercase() ?: throw IllegalStateException("Entrada encerrada.")
            when (input) {
                "S", "SIM", "Y", "YES" -> return true
                "N", "NAO", "NO" -> return false
                else -> println("Resposta invalida. Digite S para confirmar ou N para redistribuir.")
            }
        }
    }

    private fun applyAttributePoints(current: Attributes, attributeCode: String, points: Int): Attributes {
        return when (attributeCode.uppercase()) {
            "STR" -> current.copy(str = current.str + points)
            "AGI" -> current.copy(agi = current.agi + points)
            "DEX" -> current.copy(dex = current.dex + points)
            "VIT" -> current.copy(vit = current.vit + points)
            "INT" -> current.copy(`int` = current.`int` + points)
            "SPR" -> current.copy(spr = current.spr + points)
            "LUK" -> current.copy(luk = current.luk + points)
            else -> current
        }
    }

    private fun generateAttributeDescription(attribute: AttrMeta): List<String> = when (attribute.code) {
        "STR" -> listOf(
            "Aumenta o dano fisico base em ataques corpo a corpo.",
            "Contribui para penetracao fisica e impacto de golpes pesados."
        )

        "AGI" -> listOf(
            "Aumenta a velocidade de ataque e recarga de acoes basicas.",
            "Melhora levemente a evasao em combate."
        )

        "DEX" -> listOf(
            "Melhora precisao e chance critica de ataques fisicos.",
            "Fortalece performance com armas de alcance."
        )

        "VIT" -> listOf(
            "Aumenta HP maximo e resistencia fisica.",
            "Reduz risco de morrer em sequencias de dano."
        )

        "INT" -> listOf(
            "Aumenta dano magico e eficiencia de habilidades arcanas.",
            "Eleva efetividade de escalas magicas."
        )

        "SPR" -> listOf(
            "Aumenta MP maximo e regeneracao de mana.",
            "Melhora resistencia magica e sustain em lutas longas."
        )

        "LUK" -> listOf(
            "Aumenta sorte geral: critico, drops e eventos favoraveis.",
            "Melhora levemente chance de efeitos secundarios."
        )

        else -> listOf("Atributo sem descricao.")
    }

    private fun generateGameplayImpact(attribute: AttrMeta): List<String> = when (attribute.code) {
        "STR" -> listOf("Mais dano por hit em builds de espada e armas pesadas.")
        "AGI" -> listOf("Mais cadencia de golpes e melhor ritmo no semi-ATB.")
        "DEX" -> listOf("Menos erros de acerto e mais criticos consistentes.")
        "VIT" -> listOf("Mais margem para sobreviver em bosses e elites.")
        "INT" -> listOf("Magias batem mais forte e escalam melhor.")
        "SPR" -> listOf("Mais mana sustentada e menor risco de ficar sem recurso.")
        "LUK" -> listOf("Mais variacao positiva em combate e loot.")
        else -> listOf("Sem impacto especifico definido.")
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

    private fun formatSigned(value: Int): String = if (value >= 0) "+$value" else value.toString()
}
