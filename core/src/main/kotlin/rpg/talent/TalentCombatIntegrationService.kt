package rpg.talent

import rpg.model.PlayerState
import rpg.model.TalentTree
import rpg.status.StatusType

data class TalentAtbModifiers(
    val fillRateMultiplier: Double = 1.0,
    val castTimeMultiplier: Double = 1.0,
    val gcdMultiplier: Double = 1.0,
    val cooldownMultiplier: Double = 1.0,
    val interruptChanceBonusPct: Double = 0.0,
    val interruptResistPct: Double = 0.0,
    val actionBarGainOnHitPct: Double = 0.0,
    val actionBarGainOnCritPct: Double = 0.0,
    val actionBarGainOnDamagedPct: Double = 0.0,
    val actionBarLossOnHitPct: Double = 0.0,
    val manaOnHitFlat: Double = 0.0,
    val manaOnHitPctMax: Double = 0.0,
    val noManaCostChancePct: Double = 0.0,
    val cooldownReductionOnKillSeconds: Double = 0.0,
    val actionBarGainOnStatusApplyPct: Double = 0.0,
    val tempDamageBuffOnStatusApplyPct: Double = 0.0,
    val tempFillRateBuffOnStatusApplyPct: Double = 0.0,
    val tempBuffDurationSeconds: Double = 0.0,
    val hasteMultiplier: Double = 1.0,
    val slowReceivedMultiplier: Double = 1.0,
    val slowInflictedMultiplier: Double = 1.0
)

data class TalentStatusModifiers(
    val applyChanceBonusPct: Double = 0.0,
    val applyChanceBonusByType: Map<StatusType, Double> = emptyMap(),
    val durationMultiplier: Double = 1.0,
    val durationMultiplierByType: Map<StatusType, Double> = emptyMap(),
    val incomingDurationMultiplier: Double = 1.0,
    val incomingDurationMultiplierByType: Map<StatusType, Double> = emptyMap(),
    val consumeChanceByType: Map<StatusType, Double> = emptyMap(),
    val bonusDamageVsStatusPct: Map<StatusType, Double> = emptyMap(),
    val bonusDamageWhileSelfHasStatusPct: Map<StatusType, Double> = emptyMap(),
    val bonusDamageVsLowHpPct: Double = 0.0,
    val lowHpThresholdPct: Double = 30.0,
    val reflectDamagePct: Double = 0.0
)

data class TalentCombatModifiers(
    val atb: TalentAtbModifiers = TalentAtbModifiers(),
    val status: TalentStatusModifiers = TalentStatusModifiers()
)

class TalentCombatIntegrationService(
    private val talentTreeService: TalentTreeService
) {
    fun collectForPlayer(player: PlayerState, trees: Iterable<TalentTree>): TalentCombatModifiers {
        val activeTrees = talentTreeService.activeTrees(player, trees)
        if (activeTrees.isEmpty()) return TalentCombatModifiers()

        var fillRatePct = 0.0
        var castTimePct = 0.0
        var gcdPct = 0.0
        var cooldownPct = 0.0
        var interruptChancePct = 0.0
        var interruptResistPct = 0.0
        var barGainOnHitPct = 0.0
        var barGainOnCritPct = 0.0
        var barGainOnDamagedPct = 0.0
        var barLossOnHitPct = 0.0
        var manaOnHitFlat = 0.0
        var manaOnHitPctMax = 0.0
        var noManaCostChancePct = 0.0
        var cooldownReductionOnKillSeconds = 0.0
        var barGainOnStatusApplyPct = 0.0
        var tempDamageBuffOnStatusApplyPct = 0.0
        var tempFillRateBuffOnStatusApplyPct = 0.0
        var tempBuffDurationSeconds = 0.0
        var hastePct = 0.0
        var slowResistPct = 0.0
        var slowAmplifyPct = 0.0

        var applyChancePct = 0.0
        var durationPct = 0.0
        var incomingDurationPct = 0.0
        var bonusDamageVsLowHpPct = 0.0
        var lowHpThresholdPct = 30.0
        var reflectDamagePct = 0.0
        val applyChanceByType = mutableMapOf<StatusType, Double>()
        val durationByTypePct = mutableMapOf<StatusType, Double>()
        val incomingDurationByTypePct = mutableMapOf<StatusType, Double>()
        val consumeChanceByType = mutableMapOf<StatusType, Double>()
        val bonusDamageVsByType = mutableMapOf<StatusType, Double>()
        val bonusDamageWhileSelfByType = mutableMapOf<StatusType, Double>()

        for (tree in activeTrees) {
            for (node in tree.nodes) {
                val rank = talentTreeService.nodeCurrentRank(player, node).coerceAtLeast(0)
                if (rank <= 0) continue
                val scale = rank.toDouble()

                for ((rawKey, rawValue) in node.modifiers.atb) {
                    val key = rawKey.trim()
                    val normalizedKey = key.lowercase()
                    val value = rawValue * scale
                    when (normalizedKey) {
                        "fillratepct" -> fillRatePct += value
                        "casttimepct" -> castTimePct += value
                        "gcdpct" -> gcdPct += value
                        "cooldownpct" -> cooldownPct += value
                        "interruptchancepct" -> interruptChancePct += value
                        "interruptresistpct" -> interruptResistPct += value
                        "bargainonhitpct" -> barGainOnHitPct += value
                        "bargainoncritpct" -> barGainOnCritPct += value
                        "bargainondamagedpct" -> barGainOnDamagedPct += value
                        "barlossonhitpct" -> barLossOnHitPct += value
                        "manaonhit" -> manaOnHitFlat += value
                        "manaonhitpctmax" -> manaOnHitPctMax += value
                        "nomanacostchancepct" -> noManaCostChancePct += value
                        "cooldownreduceonkillseconds" -> cooldownReductionOnKillSeconds += value
                        "bargainonstatusapplypct" -> barGainOnStatusApplyPct += value
                        "tempdamagebuffonstatusapplypct" -> tempDamageBuffOnStatusApplyPct += value
                        "tempfillratebuffonstatusapplypct" -> tempFillRateBuffOnStatusApplyPct += value
                        "tempbuffdurationseconds" -> tempBuffDurationSeconds += value
                        "hastepct" -> hastePct += value
                        "slowresistpct" -> slowResistPct += value
                        "slowamplifypct" -> slowAmplifyPct += value
                    }
                }

                for ((rawKey, rawValue) in node.modifiers.status) {
                    val key = rawKey.trim()
                    val value = rawValue * scale
                    when {
                        key.equals("applyChancePct", ignoreCase = true) -> applyChancePct += value
                        key.equals("durationPct", ignoreCase = true) -> durationPct += value
                        key.equals("incomingDurationPct", ignoreCase = true) -> incomingDurationPct += value
                        key.equals("bonusDamageVsLowHpPct", ignoreCase = true) -> bonusDamageVsLowHpPct += value
                        key.equals("lowHpThresholdPct", ignoreCase = true) ->
                            lowHpThresholdPct = maxOf(lowHpThresholdPct, value)
                        key.equals("reflectDamagePct", ignoreCase = true) -> reflectDamagePct += value
                        key.startsWith("applyChance.", ignoreCase = true) &&
                            key.endsWith(".Pct", ignoreCase = true) -> {
                            statusTypeFromKey(key, prefix = "applyChance.", suffix = ".Pct")?.let { type ->
                                applyChanceByType[type] = (applyChanceByType[type] ?: 0.0) + value
                            }
                        }
                        key.startsWith("duration.", ignoreCase = true) &&
                            key.endsWith(".Pct", ignoreCase = true) -> {
                            statusTypeFromKey(key, prefix = "duration.", suffix = ".Pct")?.let { type ->
                                durationByTypePct[type] = (durationByTypePct[type] ?: 0.0) + value
                            }
                        }
                        key.startsWith("incomingDuration.", ignoreCase = true) &&
                            key.endsWith(".Pct", ignoreCase = true) -> {
                            statusTypeFromKey(key, prefix = "incomingDuration.", suffix = ".Pct")?.let { type ->
                                incomingDurationByTypePct[type] = (incomingDurationByTypePct[type] ?: 0.0) + value
                            }
                        }
                        key.startsWith("consumeChance.", ignoreCase = true) &&
                            key.endsWith(".Pct", ignoreCase = true) -> {
                            statusTypeFromKey(key, prefix = "consumeChance.", suffix = ".Pct")?.let { type ->
                                consumeChanceByType[type] = (consumeChanceByType[type] ?: 0.0) + value
                            }
                        }
                        key.startsWith("bonusDamageVs.", ignoreCase = true) &&
                            key.endsWith(".Pct", ignoreCase = true) -> {
                            statusTypeFromKey(key, prefix = "bonusDamageVs.", suffix = ".Pct")?.let { type ->
                                bonusDamageVsByType[type] = (bonusDamageVsByType[type] ?: 0.0) + value
                            }
                        }
                        key.startsWith("bonusDamageWhileSelf.", ignoreCase = true) &&
                            key.endsWith(".Pct", ignoreCase = true) -> {
                            statusTypeFromKey(key, prefix = "bonusDamageWhileSelf.", suffix = ".Pct")?.let { type ->
                                bonusDamageWhileSelfByType[type] = (bonusDamageWhileSelfByType[type] ?: 0.0) + value
                            }
                        }
                    }
                }
            }
        }

        return TalentCombatModifiers(
            atb = TalentAtbModifiers(
                fillRateMultiplier = pctToMultiplier(fillRatePct),
                castTimeMultiplier = pctToMultiplier(castTimePct),
                gcdMultiplier = pctToMultiplier(gcdPct),
                cooldownMultiplier = pctToMultiplier(cooldownPct),
                interruptChanceBonusPct = interruptChancePct.coerceIn(0.0, 100.0),
                interruptResistPct = interruptResistPct.coerceIn(0.0, 100.0),
                actionBarGainOnHitPct = barGainOnHitPct.coerceIn(0.0, 95.0),
                actionBarGainOnCritPct = barGainOnCritPct.coerceIn(0.0, 95.0),
                actionBarGainOnDamagedPct = barGainOnDamagedPct.coerceIn(0.0, 95.0),
                actionBarLossOnHitPct = barLossOnHitPct.coerceIn(0.0, 95.0),
                manaOnHitFlat = manaOnHitFlat.coerceAtLeast(0.0),
                manaOnHitPctMax = manaOnHitPctMax.coerceAtLeast(0.0),
                noManaCostChancePct = noManaCostChancePct.coerceIn(0.0, 100.0),
                cooldownReductionOnKillSeconds = cooldownReductionOnKillSeconds.coerceAtLeast(0.0),
                actionBarGainOnStatusApplyPct = barGainOnStatusApplyPct.coerceIn(0.0, 95.0),
                tempDamageBuffOnStatusApplyPct = tempDamageBuffOnStatusApplyPct.coerceIn(0.0, 200.0),
                tempFillRateBuffOnStatusApplyPct = tempFillRateBuffOnStatusApplyPct.coerceIn(0.0, 200.0),
                tempBuffDurationSeconds = tempBuffDurationSeconds.coerceAtLeast(0.0),
                hasteMultiplier = pctToMultiplier(hastePct),
                slowReceivedMultiplier = pctToMultiplier(-slowResistPct),
                slowInflictedMultiplier = pctToMultiplier(slowAmplifyPct)
            ),
            status = TalentStatusModifiers(
                applyChanceBonusPct = applyChancePct,
                applyChanceBonusByType = applyChanceByType,
                durationMultiplier = pctToMultiplier(durationPct),
                durationMultiplierByType = durationByTypePct.mapValues { pctToMultiplier(it.value) },
                incomingDurationMultiplier = pctToMultiplier(incomingDurationPct),
                incomingDurationMultiplierByType = incomingDurationByTypePct.mapValues { pctToMultiplier(it.value) },
                consumeChanceByType = consumeChanceByType.mapValues { it.value.coerceIn(0.0, 100.0) },
                bonusDamageVsStatusPct = bonusDamageVsByType,
                bonusDamageWhileSelfHasStatusPct = bonusDamageWhileSelfByType,
                bonusDamageVsLowHpPct = bonusDamageVsLowHpPct,
                lowHpThresholdPct = lowHpThresholdPct.coerceIn(1.0, 95.0),
                reflectDamagePct = reflectDamagePct.coerceIn(0.0, 90.0)
            )
        )
    }

    private fun pctToMultiplier(pct: Double): Double {
        return (1.0 + pct / 100.0).coerceIn(0.1, 5.0)
    }

    private fun statusTypeFromKey(key: String, prefix: String, suffix: String): StatusType? {
        val raw = key.trim()
        if (!raw.startsWith(prefix, ignoreCase = true) || !raw.endsWith(suffix, ignoreCase = true)) {
            return null
        }
        val body = raw
            .substring(prefix.length, raw.length - suffix.length)
            .trim()
        if (body.isBlank()) return null
        val normalized = body
            .uppercase()
            .replace("-", "_")
            .replace(" ", "_")
        return runCatching { StatusType.valueOf(normalized) }.getOrNull()
    }
}
