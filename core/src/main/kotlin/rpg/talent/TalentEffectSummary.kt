package rpg.talent

import kotlin.math.round
import rpg.model.TalentNode
import rpg.status.StatusSystem
import rpg.status.StatusType

internal class TalentEffectSummary {
    fun summarize(node: TalentNode): String {
        val pieces = mutableListOf<String>()
        val maxRank = node.maxRank.coerceAtLeast(1)
        val bonuses = node.modifiers.bonuses
        val add = bonuses.derivedAdd
        val mult = bonuses.derivedMult
        val attrs = bonuses.attributes

        addEffectSeries(pieces, "Forca", maxRank, attrs.str.toDouble())
        addEffectSeries(pieces, "Agilidade", maxRank, attrs.agi.toDouble())
        addEffectSeries(pieces, "Destreza", maxRank, attrs.dex.toDouble())
        addEffectSeries(pieces, "Vitalidade", maxRank, attrs.vit.toDouble())
        addEffectSeries(pieces, "Inteligencia", maxRank, attrs.`int`.toDouble())
        addEffectSeries(pieces, "Espirito", maxRank, attrs.spr.toDouble())
        addEffectSeries(pieces, "Sorte", maxRank, attrs.luk.toDouble())

        addEffectSeries(pieces, "Dano fisico", maxRank, add.damagePhysical)
        addEffectSeries(pieces, "Dano magico", maxRank, add.damageMagic)
        addEffectSeries(pieces, "HP maximo", maxRank, add.hpMax)
        addEffectSeries(pieces, "MP maximo", maxRank, add.mpMax)
        addEffectSeries(pieces, "Defesa fisica", maxRank, add.defPhysical)
        addEffectSeries(pieces, "Defesa magica", maxRank, add.defMagic)
        addEffectSeries(pieces, "Velocidade de ataque", maxRank, add.attackSpeed)
        addEffectSeries(pieces, "Velocidade de movimento", maxRank, add.moveSpeed)
        addEffectSeries(pieces, "Chance critica", maxRank, add.critChancePct, suffix = "%")
        addEffectSeries(pieces, "Dano critico", maxRank, add.critDamagePct, suffix = "%")
        addEffectSeries(pieces, "Vampirismo", maxRank, add.vampirismPct, suffix = "%")
        addEffectSeries(pieces, "Recarga", maxRank, add.cdrPct, suffix = "%")
        addEffectSeries(pieces, "Penetracao fisica", maxRank, add.penPhysical)
        addEffectSeries(pieces, "Penetracao magica", maxRank, add.penMagic)
        addEffectSeries(pieces, "Regeneracao de HP", maxRank, add.hpRegen)
        addEffectSeries(pieces, "Regeneracao de MP", maxRank, add.mpRegen)
        addEffectSeries(pieces, "Precisao", maxRank, add.accuracy)
        addEffectSeries(pieces, "Esquiva", maxRank, add.evasion)
        addEffectSeries(pieces, "Tenacidade", maxRank, add.tenacityPct, suffix = "%")
        addEffectSeries(pieces, "Reducao de dano", maxRank, add.damageReductionPct, suffix = "%")

        addEffectSeries(pieces, "Dano fisico", maxRank, mult.damagePhysical, suffix = "%")
        addEffectSeries(pieces, "Dano magico", maxRank, mult.damageMagic, suffix = "%")
        addEffectSeries(pieces, "HP maximo", maxRank, mult.hpMax, suffix = "%")
        addEffectSeries(pieces, "MP maximo", maxRank, mult.mpMax, suffix = "%")
        addEffectSeries(pieces, "Defesa fisica", maxRank, mult.defPhysical, suffix = "%")
        addEffectSeries(pieces, "Defesa magica", maxRank, mult.defMagic, suffix = "%")
        addEffectSeries(pieces, "Velocidade de ataque", maxRank, mult.attackSpeed, suffix = "%")
        addEffectSeries(pieces, "Velocidade de movimento", maxRank, mult.moveSpeed, suffix = "%")
        addEffectSeries(pieces, "Chance critica", maxRank, mult.critChancePct, suffix = "%")
        addEffectSeries(pieces, "Dano critico", maxRank, mult.critDamagePct, suffix = "%")
        addEffectSeries(pieces, "Vampirismo", maxRank, mult.vampirismPct, suffix = "%")
        addEffectSeries(pieces, "Recarga", maxRank, mult.cdrPct, suffix = "%")
        addEffectSeries(pieces, "Regeneracao de HP", maxRank, mult.hpRegen, suffix = "%")
        addEffectSeries(pieces, "Regeneracao de MP", maxRank, mult.mpRegen, suffix = "%")
        addEffectSeries(pieces, "Precisao", maxRank, mult.accuracy, suffix = "%")
        addEffectSeries(pieces, "Esquiva", maxRank, mult.evasion, suffix = "%")
        addEffectSeries(pieces, "Tenacidade", maxRank, mult.tenacityPct, suffix = "%")
        addEffectSeries(pieces, "Reducao de dano", maxRank, mult.damageReductionPct, suffix = "%")

        node.modifiers.atb.forEach { (rawKey, value) ->
            when (rawKey.trim().lowercase()) {
                "fillratepct" -> addEffectSeries(pieces, "Velocidade da barra", maxRank, value, suffix = "%")
                "casttimepct" -> addEffectSeries(pieces, "Tempo de conjuracao", maxRank, value, suffix = "%")
                "gcdpct" -> addEffectSeries(pieces, "Recarga global", maxRank, value, suffix = "%")
                "cooldownpct" -> addEffectSeries(pieces, "Cooldown", maxRank, value, suffix = "%")
                "interruptchancepct" -> addEffectSeries(pieces, "Chance de interrupcao", maxRank, value, suffix = "%")
                "interruptresistpct" -> addEffectSeries(
                    pieces,
                    "Resistencia a interrupcao",
                    maxRank,
                    value,
                    suffix = "%"
                )
                "bargainonhitpct" -> addEffectSeries(pieces, "Barra ganha ao acertar", maxRank, value, suffix = "%")
                "bargainoncritpct" -> addEffectSeries(pieces, "Barra ganha ao critar", maxRank, value, suffix = "%")
                "bargainondamagedpct" -> addEffectSeries(
                    pieces,
                    "Barra ganha ao sofrer dano",
                    maxRank,
                    value,
                    suffix = "%"
                )
                "barlossonhitpct" -> addEffectSeries(pieces, "Barra removida do alvo", maxRank, value, suffix = "%")
                "manaonhit" -> addEffectSeries(pieces, "Mana por acerto", maxRank, value)
                "manaonhitpctmax" -> addEffectSeries(pieces, "Mana por acerto", maxRank, value, suffix = "% do MP max")
                "nomanacostchancepct" -> addEffectSeries(
                    pieces,
                    "Chance de nao gastar mana",
                    maxRank,
                    value,
                    suffix = "%",
                    includePlus = false
                )
                "cooldownreduceonkillseconds" -> addEffectSeries(
                    pieces,
                    "Reducao de cooldown ao abater",
                    maxRank,
                    value,
                    suffix = "s"
                )
                "bargainonstatusapplypct" -> addEffectSeries(
                    pieces,
                    "Barra ganha ao aplicar status",
                    maxRank,
                    value,
                    suffix = "%"
                )
                "tempdamagebuffonstatusapplypct" -> addEffectSeries(
                    pieces,
                    "Buff temporario de dano ao aplicar status",
                    maxRank,
                    value,
                    suffix = "%"
                )
                "tempfillratebuffonstatusapplypct" -> addEffectSeries(
                    pieces,
                    "Buff temporario de barra ao aplicar status",
                    maxRank,
                    value,
                    suffix = "%"
                )
                "tempbuffdurationseconds" -> addEffectSeries(
                    pieces,
                    "Duracao do buff temporario",
                    maxRank,
                    value,
                    suffix = "s",
                    includePlus = false
                )
                "hastepct" -> addEffectSeries(pieces, "Aceleracao", maxRank, value, suffix = "%")
                "slowresistpct" -> addEffectSeries(pieces, "Resistencia a lentidao", maxRank, value, suffix = "%")
                "slowamplifypct" -> addEffectSeries(
                    pieces,
                    "Potencia de lentidao aplicada",
                    maxRank,
                    value,
                    suffix = "%"
                )
            }
        }

        val lowHpBonus = node.modifiers.status["bonusDamageVsLowHpPct"]
        val lowHpThreshold = node.modifiers.status["lowHpThresholdPct"]
        if (lowHpBonus != null && lowHpBonus != 0.0) {
            val bonusValues = effectSequence(maxRank) { rank -> lowHpBonus * rank }
            val thresholdText = lowHpThreshold?.let { " abaixo de ${formatCompact(it)}% de HP" }.orEmpty()
            pieces += "Dano contra alvo$thresholdText ${formatSequence(bonusValues, "%")}"
        }

        node.modifiers.status.forEach { (rawKey, value) ->
            val key = rawKey.trim()
            when {
                key.equals("applyChancePct", ignoreCase = true) ->
                    addEffectSeries(pieces, "Chance de aplicar status", maxRank, value, suffix = "%")
                key.equals("durationPct", ignoreCase = true) ->
                    addEffectSeries(pieces, "Duracao dos status causados", maxRank, value, suffix = "%")
                key.equals("incomingDurationPct", ignoreCase = true) ->
                    addEffectSeries(pieces, "Duracao dos status recebidos", maxRank, value, suffix = "%")
                key.equals("reflectDamagePct", ignoreCase = true) ->
                    addEffectSeries(pieces, "Dano refletido", maxRank, value, suffix = "%")
                key.equals("bonusDamageVsLowHpPct", ignoreCase = true) -> Unit
                key.equals("lowHpThresholdPct", ignoreCase = true) -> Unit
                key.startsWith("applyChance.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    statusNameFromKey(key, "applyChance.", ".Pct")?.let { statusName ->
                        addEffectSeries(pieces, "Chance de aplicar $statusName", maxRank, value, suffix = "%")
                    }
                key.startsWith("duration.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    statusNameFromKey(key, "duration.", ".Pct")?.let { statusName ->
                        addEffectSeries(pieces, "Duracao de $statusName", maxRank, value, suffix = "%")
                    }
                key.startsWith("incomingDuration.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    statusNameFromKey(key, "incomingDuration.", ".Pct")?.let { statusName ->
                        addEffectSeries(
                            pieces,
                            "Duracao de $statusName recebido",
                            maxRank,
                            value,
                            suffix = "%"
                        )
                    }
                key.startsWith("consumeChance.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    statusNameFromKey(key, "consumeChance.", ".Pct")?.let { statusName ->
                        addEffectSeries(
                            pieces,
                            "Chance de consumir $statusName",
                            maxRank,
                            value,
                            suffix = "%",
                            includePlus = false
                        )
                    }
                key.startsWith("bonusDamageVs.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    statusNameFromKey(key, "bonusDamageVs.", ".Pct")?.let { statusName ->
                        addEffectSeries(pieces, "Dano contra $statusName", maxRank, value, suffix = "%")
                    }
                key.startsWith("bonusDamageWhileSelf.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    statusNameFromKey(key, "bonusDamageWhileSelf.", ".Pct")?.let { statusName ->
                        addEffectSeries(
                            pieces,
                            "Dano enquanto voce estiver com $statusName",
                            maxRank,
                            value,
                            suffix = "%"
                        )
                    }
            }
        }

        val combat = node.modifiers.combat
        if (combat.isNotEmpty()) {
            combat["damageMultiplier"]?.let { base ->
                val growth = combat["damageMultiplierPerRank"] ?: 0.0
                val values = effectSequence(maxRank) { rank -> (base + (rank - 1) * growth) * 100.0 }
                pieces += "Dano ${formatSequence(values, "%", includePlus = false)}"
            }
            (combat["mpCost"] ?: combat["manaCost"])?.let { base ->
                val growth = combat["mpCostPerRank"] ?: combat["manaCostPerRank"] ?: 0.0
                val values = effectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Custo ${formatSequence(values, " MP", includePlus = false)}"
            }
            (combat["cooldownSeconds"] ?: combat["cooldown"])?.let { base ->
                val growth = combat["cooldownPerRank"] ?: 0.0
                val values = effectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Cooldown ${formatSequence(values, "s", includePlus = false)}"
            }
            (combat["castTimeSeconds"] ?: combat["cast"])?.let { base ->
                val growth = combat["castTimePerRank"] ?: 0.0
                val values = effectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Conjuracao ${formatSequence(values, "s", includePlus = false)}"
            }
            combat["selfHealFlat"]?.let { base ->
                val growth = combat["selfHealFlatPerRank"] ?: 0.0
                val values = effectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Cura propria ${formatSequence(values, "", includePlus = false)}"
            }
            combat["selfHealPctMaxHp"]?.let { base ->
                val growth = combat["selfHealPctMaxHpPerRank"] ?: 0.0
                val values = effectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Cura propria ${formatSequence(values, "% do HP max", includePlus = false)}"
            }
            val aoeUnlockRank = (combat["aoeUnlockRank"] ?: 0.0).toInt()
            val aoeBonusDamagePct = combat["aoeBonusDamagePct"] ?: 0.0
            if (aoeUnlockRank > 0 && aoeBonusDamagePct > 0.0) {
                pieces += "No NV$aoeUnlockRank acerta area com ${formatCompact(aoeBonusDamagePct)}% de dano extra"
            }
        }

        if (node.modifiers.applyStatuses.isNotEmpty()) {
            node.modifiers.applyStatuses.forEach { status ->
                val chancePerRank = combat["statusChancePerRankPct"] ?: 0.0
                val durationPerRank = combat["statusDurationPerRankSeconds"] ?: 0.0
                val effectPerRank = combat["statusEffectPerRank"] ?: 0.0
                val stacksPerRank = combat["statusMaxStacksPerRank"] ?: 0.0
                val chanceValues = effectSequence(maxRank) { rank ->
                    status.chancePct + (rank - 1) * chancePerRank
                }
                val durationValues = effectSequence(maxRank) { rank ->
                    status.durationSeconds + (rank - 1) * durationPerRank
                }
                val effectValues = effectSequence(maxRank) { rank ->
                    status.effectValue + (rank - 1) * effectPerRank
                }
                val stackValues = effectSequence(maxRank) { rank ->
                    (status.maxStacks + ((rank - 1) * stacksPerRank).toInt()).toDouble()
                }
                val detail = when (status.type) {
                    StatusType.BLEEDING ->
                        formatSequence(effectValues.map { it * 100.0 }, "% do HP max por tique", includePlus = false)
                    StatusType.BURNING, StatusType.POISONED ->
                        formatSequence(effectValues, " por tique", includePlus = false)
                    StatusType.SLOW, StatusType.WEAKNESS ->
                        formatSequence(effectValues, "%", includePlus = false)
                    StatusType.PARALYZED ->
                        formatSequence(effectValues, "% de falha", includePlus = false)
                    StatusType.FROZEN -> "congela o alvo"
                    StatusType.MARKED -> "marca o alvo"
                }
                val stackText = if (status.stackable || status.maxStacks > 1) {
                    " | acumulos ${formatSequence(stackValues, "", includePlus = false)}"
                } else {
                    ""
                }
                pieces += "Aplica ${StatusSystem.displayName(status.type)} " +
                    "${formatSequence(chanceValues, "%", includePlus = false)} por " +
                    "${formatSequence(durationValues, "s", includePlus = false)} | efeito $detail$stackText"
            }
        }

        return if (pieces.isEmpty()) {
            "Sem efeito definido."
        } else {
            pieces.joinToString(". ")
        }
    }

    private fun addEffectSeries(
        pieces: MutableList<String>,
        label: String,
        maxRank: Int,
        baseValue: Double,
        suffix: String = "",
        includePlus: Boolean = true
    ) {
        if (baseValue == 0.0) return
        val values = effectSequence(maxRank) { rank -> baseValue * rank }
        pieces += "$label ${formatSequence(values, suffix, includePlus)}"
    }

    private fun effectSequence(maxRank: Int, valueAtRank: (Int) -> Double): List<Double> {
        return (1..maxRank.coerceAtLeast(1)).map(valueAtRank)
    }

    private fun formatSequence(
        values: List<Double>,
        suffix: String = "",
        includePlus: Boolean = true
    ): String {
        return values.joinToString("/") { value ->
            val sign = if (includePlus && value > 0.0) "+" else ""
            "$sign${formatCompact(value)}$suffix"
        }
    }

    private fun formatCompact(value: Double): String {
        val rounded = round(value * 100.0) / 100.0
        if (rounded == rounded.toLong().toDouble()) {
            return rounded.toLong().toString()
        }
        if ((rounded * 10.0) == (rounded * 10.0).toLong().toDouble()) {
            return "%.1f".format(rounded)
        }
        return "%.2f".format(rounded)
    }

    private fun statusNameFromKey(rawKey: String, prefix: String, suffix: String): String? {
        val body = rawKey
            .substring(prefix.length, rawKey.length - suffix.length)
            .trim()
            .uppercase()
            .replace("-", "_")
            .replace(" ", "_")
        val type = runCatching { StatusType.valueOf(body) }.getOrNull() ?: return null
        return StatusSystem.displayName(type)
    }
}
