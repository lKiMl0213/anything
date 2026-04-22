package rpg.application.character

import kotlin.math.round
import rpg.model.TalentNode
import rpg.status.StatusSystem

internal class CharacterTalentEffectFormatter {
    fun talentNodeEffectSummary(node: TalentNode): String {
        val pieces = mutableListOf<String>()
        val maxRank = node.maxRank.coerceAtLeast(1)
        val bonuses = node.modifiers.bonuses
        val add = bonuses.derivedAdd
        val mult = bonuses.derivedMult
        val attrs = bonuses.attributes
        val combat = node.modifiers.combat

        addTalentEffectSeries(pieces, "Forca", maxRank, attrs.str.toDouble())
        addTalentEffectSeries(pieces, "Agilidade", maxRank, attrs.agi.toDouble())
        addTalentEffectSeries(pieces, "Destreza", maxRank, attrs.dex.toDouble())
        addTalentEffectSeries(pieces, "Vitalidade", maxRank, attrs.vit.toDouble())
        addTalentEffectSeries(pieces, "Inteligencia", maxRank, attrs.`int`.toDouble())
        addTalentEffectSeries(pieces, "Espirito", maxRank, attrs.spr.toDouble())
        addTalentEffectSeries(pieces, "Sorte", maxRank, attrs.luk.toDouble())
        addTalentEffectSeries(pieces, "Dano fisico", maxRank, add.damagePhysical)
        addTalentEffectSeries(pieces, "Dano magico", maxRank, add.damageMagic)
        addTalentEffectSeries(pieces, "HP maximo", maxRank, add.hpMax)
        addTalentEffectSeries(pieces, "MP maximo", maxRank, add.mpMax)
        addTalentEffectSeries(pieces, "Defesa fisica", maxRank, add.defPhysical)
        addTalentEffectSeries(pieces, "Defesa magica", maxRank, add.defMagic)
        addTalentEffectSeries(pieces, "Velocidade de ataque", maxRank, add.attackSpeed)
        addTalentEffectSeries(pieces, "Velocidade de movimento", maxRank, add.moveSpeed)
        addTalentEffectSeries(pieces, "Chance critica", maxRank, add.critChancePct, suffix = "%")
        addTalentEffectSeries(pieces, "Dano critico", maxRank, add.critDamagePct, suffix = "%")
        addTalentEffectSeries(pieces, "Vampirismo", maxRank, add.vampirismPct, suffix = "%")
        addTalentEffectSeries(pieces, "Recarga", maxRank, add.cdrPct, suffix = "%")
        addTalentEffectSeries(pieces, "Penetracao fisica", maxRank, add.penPhysical)
        addTalentEffectSeries(pieces, "Penetracao magica", maxRank, add.penMagic)
        addTalentEffectSeries(pieces, "Regeneracao de HP", maxRank, add.hpRegen)
        addTalentEffectSeries(pieces, "Regeneracao de MP", maxRank, add.mpRegen)
        addTalentEffectSeries(pieces, "Precisao", maxRank, add.accuracy)
        addTalentEffectSeries(pieces, "Esquiva", maxRank, add.evasion)
        addTalentEffectSeries(pieces, "Tenacidade", maxRank, add.tenacityPct, suffix = "%")
        addTalentEffectSeries(pieces, "Reducao de dano", maxRank, add.damageReductionPct, suffix = "%")
        addTalentEffectSeries(pieces, "Dano fisico", maxRank, mult.damagePhysical, suffix = "%")
        addTalentEffectSeries(pieces, "Dano magico", maxRank, mult.damageMagic, suffix = "%")
        addTalentEffectSeries(pieces, "HP maximo", maxRank, mult.hpMax, suffix = "%")
        addTalentEffectSeries(pieces, "MP maximo", maxRank, mult.mpMax, suffix = "%")
        addTalentEffectSeries(pieces, "Defesa fisica", maxRank, mult.defPhysical, suffix = "%")
        addTalentEffectSeries(pieces, "Defesa magica", maxRank, mult.defMagic, suffix = "%")
        addTalentEffectSeries(pieces, "Velocidade de ataque", maxRank, mult.attackSpeed, suffix = "%")
        addTalentEffectSeries(pieces, "Velocidade de movimento", maxRank, mult.moveSpeed, suffix = "%")
        addTalentEffectSeries(pieces, "Chance critica", maxRank, mult.critChancePct, suffix = "%")
        addTalentEffectSeries(pieces, "Dano critico", maxRank, mult.critDamagePct, suffix = "%")
        addTalentEffectSeries(pieces, "Vampirismo", maxRank, mult.vampirismPct, suffix = "%")
        addTalentEffectSeries(pieces, "Recarga", maxRank, mult.cdrPct, suffix = "%")
        addTalentEffectSeries(pieces, "Regeneracao de HP", maxRank, mult.hpRegen, suffix = "%")
        addTalentEffectSeries(pieces, "Regeneracao de MP", maxRank, mult.mpRegen, suffix = "%")
        addTalentEffectSeries(pieces, "Precisao", maxRank, mult.accuracy, suffix = "%")
        addTalentEffectSeries(pieces, "Esquiva", maxRank, mult.evasion, suffix = "%")
        addTalentEffectSeries(pieces, "Tenacidade", maxRank, mult.tenacityPct, suffix = "%")
        addTalentEffectSeries(pieces, "Reducao de dano", maxRank, mult.damageReductionPct, suffix = "%")

        node.modifiers.atb.forEach { (rawKey, value) ->
            when (rawKey.trim().lowercase()) {
                "fillratepct" -> addTalentEffectSeries(pieces, "Velocidade da barra", maxRank, value, suffix = "%")
                "casttimepct" -> addTalentEffectSeries(pieces, "Tempo de conjuracao", maxRank, value, suffix = "%")
                "gcdpct" -> addTalentEffectSeries(pieces, "Recarga global", maxRank, value, suffix = "%")
                "cooldownpct" -> addTalentEffectSeries(pieces, "Cooldown", maxRank, value, suffix = "%")
                "interruptchancepct" -> addTalentEffectSeries(pieces, "Chance de interrupcao", maxRank, value, suffix = "%")
                "interruptresistpct" -> addTalentEffectSeries(pieces, "Resistencia a interrupcao", maxRank, value, suffix = "%")
                "bargainonhitpct" -> addTalentEffectSeries(pieces, "Barra ganha ao acertar", maxRank, value, suffix = "%")
                "bargainoncritpct" -> addTalentEffectSeries(pieces, "Barra ganha ao critar", maxRank, value, suffix = "%")
                "bargainondamagedpct" -> addTalentEffectSeries(pieces, "Barra ganha ao sofrer dano", maxRank, value, suffix = "%")
                "barlossonhitpct" -> addTalentEffectSeries(pieces, "Barra removida do alvo", maxRank, value, suffix = "%")
                "manaonhit" -> addTalentEffectSeries(pieces, "Mana por acerto", maxRank, value)
                "manaonhitpctmax" -> addTalentEffectSeries(pieces, "Mana por acerto", maxRank, value, suffix = "% do MP max")
                "nomanacostchancepct" -> addTalentEffectSeries(pieces, "Chance de nao gastar mana", maxRank, value, suffix = "%", includePlus = false)
                "cooldownreduceonkillseconds" -> addTalentEffectSeries(pieces, "Reducao de cooldown ao abater", maxRank, value, suffix = "s")
                "bargainonstatusapplypct" -> addTalentEffectSeries(pieces, "Barra ganha ao aplicar status", maxRank, value, suffix = "%")
                "tempdamagebuffonstatusapplypct" -> addTalentEffectSeries(pieces, "Buff temporario de dano ao aplicar status", maxRank, value, suffix = "%")
                "tempfillratebuffonstatusapplypct" -> addTalentEffectSeries(pieces, "Buff temporario de barra ao aplicar status", maxRank, value, suffix = "%")
                "tempbuffdurationseconds" -> addTalentEffectSeries(pieces, "Duracao do buff temporario", maxRank, value, suffix = "s", includePlus = false)
                "hastepct" -> addTalentEffectSeries(pieces, "Aceleracao", maxRank, value, suffix = "%")
                "slowresistpct" -> addTalentEffectSeries(pieces, "Resistencia a lentidao", maxRank, value, suffix = "%")
                "slowamplifypct" -> addTalentEffectSeries(pieces, "Potencia de lentidao aplicada", maxRank, value, suffix = "%")
            }
        }

        val lowHpBonus = node.modifiers.status["bonusDamageVsLowHpPct"]
        val lowHpThreshold = node.modifiers.status["lowHpThresholdPct"]
        if (lowHpBonus != null && lowHpBonus != 0.0) {
            val bonusValues = talentEffectSequence(maxRank) { rank -> lowHpBonus * rank }
            val thresholdText = lowHpThreshold?.let { " abaixo de ${formatTalentCompact(it)}% de HP" }.orEmpty()
            pieces += "Dano contra alvo$thresholdText ${formatTalentSequence(bonusValues, "%")}"
        }

        node.modifiers.status.forEach { (rawKey, value) ->
            val key = rawKey.trim()
            when {
                key.equals("applyChancePct", ignoreCase = true) ->
                    addTalentEffectSeries(pieces, "Chance de aplicar status", maxRank, value, suffix = "%")
                key.equals("durationPct", ignoreCase = true) ->
                    addTalentEffectSeries(pieces, "Duracao dos status causados", maxRank, value, suffix = "%")
                key.equals("incomingDurationPct", ignoreCase = true) ->
                    addTalentEffectSeries(pieces, "Duracao dos status recebidos", maxRank, value, suffix = "%")
                key.equals("reflectDamagePct", ignoreCase = true) ->
                    addTalentEffectSeries(pieces, "Dano refletido", maxRank, value, suffix = "%")
                key.equals("bonusDamageVsLowHpPct", ignoreCase = true) -> Unit
                key.equals("lowHpThresholdPct", ignoreCase = true) -> Unit
                key.startsWith("applyChance.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "applyChance.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Chance de aplicar $statusName", maxRank, value, suffix = "%")
                    }
                key.startsWith("duration.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "duration.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Duracao de $statusName", maxRank, value, suffix = "%")
                    }
                key.startsWith("incomingDuration.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "incomingDuration.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Duracao de $statusName recebido", maxRank, value, suffix = "%")
                    }
                key.startsWith("consumeChance.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "consumeChance.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Chance de consumir $statusName", maxRank, value, suffix = "%", includePlus = false)
                    }
                key.startsWith("bonusDamageVs.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "bonusDamageVs.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Dano contra $statusName", maxRank, value, suffix = "%")
                    }
                key.startsWith("bonusDamageWhileSelf.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "bonusDamageWhileSelf.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Dano enquanto voce estiver com $statusName", maxRank, value, suffix = "%")
                    }
            }
        }

        if (combat.isNotEmpty()) {
            combat["damageMultiplier"]?.let { base ->
                val growth = combat["damageMultiplierPerRank"] ?: 0.0
                val values = talentEffectSequence(maxRank) { rank -> (base + (rank - 1) * growth) * 100.0 }
                pieces += "Dano ${formatTalentSequence(values, "%", includePlus = false)}"
            }
            (combat["mpCost"] ?: combat["manaCost"])?.let { base ->
                val growth = combat["mpCostPerRank"] ?: combat["manaCostPerRank"] ?: 0.0
                val values = talentEffectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Custo ${formatTalentSequence(values, " MP", includePlus = false)}"
            }
            (combat["cooldownSeconds"] ?: combat["cooldown"])?.let { base ->
                val growth = combat["cooldownPerRank"] ?: 0.0
                val values = talentEffectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Cooldown ${formatTalentSequence(values, "s", includePlus = false)}"
            }
            (combat["castTimeSeconds"] ?: combat["cast"])?.let { base ->
                val growth = combat["castTimePerRank"] ?: 0.0
                val values = talentEffectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Conjuracao ${formatTalentSequence(values, "s", includePlus = false)}"
            }
            val aoeUnlockRank = (combat["aoeUnlockRank"] ?: 0.0).toInt()
            val aoeBonusDamagePct = combat["aoeBonusDamagePct"] ?: 0.0
            if (aoeUnlockRank > 0 && aoeBonusDamagePct > 0.0) {
                pieces += "No NV$aoeUnlockRank acerta area com ${formatTalentCompact(aoeBonusDamagePct)}% de dano extra"
            }
        }

        if (node.modifiers.applyStatuses.isNotEmpty()) {
            node.modifiers.applyStatuses.forEach { status ->
                val chancePerRank = combat["statusChancePerRankPct"] ?: 0.0
                val durationPerRank = combat["statusDurationPerRankSeconds"] ?: 0.0
                val effectPerRank = combat["statusEffectPerRank"] ?: 0.0
                val stacksPerRank = combat["statusMaxStacksPerRank"] ?: 0.0
                val chanceValues = talentEffectSequence(maxRank) { rank -> status.chancePct + (rank - 1) * chancePerRank }
                val durationValues = talentEffectSequence(maxRank) { rank -> status.durationSeconds + (rank - 1) * durationPerRank }
                val effectValues = talentEffectSequence(maxRank) { rank -> status.effectValue + (rank - 1) * effectPerRank }
                val stackValues = talentEffectSequence(maxRank) { rank ->
                    (status.maxStacks + ((rank - 1) * stacksPerRank).toInt()).toDouble()
                }
                val detail = when (status.type) {
                    rpg.status.StatusType.BLEEDING ->
                        formatTalentSequence(effectValues.map { it * 100.0 }, "% do HP max por tique", includePlus = false)
                    rpg.status.StatusType.BURNING, rpg.status.StatusType.POISONED ->
                        formatTalentSequence(effectValues, " por tique", includePlus = false)
                    rpg.status.StatusType.SLOW, rpg.status.StatusType.WEAKNESS ->
                        formatTalentSequence(effectValues, "%", includePlus = false)
                    rpg.status.StatusType.PARALYZED ->
                        formatTalentSequence(effectValues, "% de falha", includePlus = false)
                    rpg.status.StatusType.FROZEN -> "congela o alvo"
                    rpg.status.StatusType.MARKED -> "marca o alvo"
                }
                val stackText = if (status.stackable || status.maxStacks > 1) {
                    " | acumulos ${formatTalentSequence(stackValues, "", includePlus = false)}"
                } else {
                    ""
                }
                pieces += "Aplica ${StatusSystem.displayName(status.type)} " +
                    "${formatTalentSequence(chanceValues, "%", includePlus = false)} por " +
                    "${formatTalentSequence(durationValues, "s", includePlus = false)} | efeito $detail$stackText"
            }
        }

        return if (pieces.isEmpty()) "Sem efeito definido." else pieces.joinToString(". ")
    }

    private fun addTalentEffectSeries(
        pieces: MutableList<String>,
        label: String,
        maxRank: Int,
        baseValue: Double,
        suffix: String = "",
        includePlus: Boolean = true
    ) {
        if (baseValue == 0.0) return
        val values = talentEffectSequence(maxRank) { rank -> baseValue * rank }
        pieces += "$label ${formatTalentSequence(values, suffix, includePlus)}"
    }

    private fun talentEffectSequence(maxRank: Int, valueAtRank: (Int) -> Double): List<Double> {
        return (1..maxRank.coerceAtLeast(1)).map(valueAtRank)
    }

    private fun formatTalentSequence(values: List<Double>, suffix: String = "", includePlus: Boolean = true): String {
        return values.joinToString("/") { value ->
            val sign = if (includePlus && value > 0.0) "+" else ""
            "$sign${formatTalentCompact(value)}$suffix"
        }
    }

    private fun formatTalentCompact(value: Double): String {
        val rounded = round(value * 100.0) / 100.0
        if (rounded == rounded.toLong().toDouble()) return rounded.toLong().toString()
        if ((rounded * 10.0) == (rounded * 10.0).toLong().toDouble()) return "%.1f".format(rounded)
        return "%.2f".format(rounded)
    }

    private fun talentStatusNameFromKey(rawKey: String, prefix: String, suffix: String): String? {
        val body = rawKey
            .substring(prefix.length, rawKey.length - suffix.length)
            .trim()
            .uppercase()
            .replace("-", "_")
            .replace(" ", "_")
        val type = runCatching { rpg.status.StatusType.valueOf(body) }.getOrNull() ?: return null
        return StatusSystem.displayName(type)
    }
}
