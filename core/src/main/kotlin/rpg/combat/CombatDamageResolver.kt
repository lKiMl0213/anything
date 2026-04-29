package rpg.combat

import kotlin.math.max
import kotlin.random.Random
import rpg.achievement.MonsterTypeMasteryService
import rpg.engine.Combat
import rpg.engine.ComputedStats
import rpg.model.Bonuses
import rpg.model.CombatStatusApplyDef

internal class CombatDamageResolver(
    private val rng: Random,
    private val logBuilder: CombatLogBuilder,
    private val statusProcessor: CombatStatusProcessor
) {
    fun resolveSkill(
        attacker: CombatActor,
        defender: CombatActor,
        preferMagic: Boolean?,
        telemetry: MutableCombatTelemetry,
        actionMultiplier: Double = 1.0,
        actionName: String? = null,
        extraOnHitStatuses: List<CombatStatusApplyDef> = emptyList(),
        extraBonuses: Bonuses = Bonuses(),
        selfHealFlat: Double = 0.0,
        selfHealPctMaxHp: Double = 0.0,
        skillRank: Int = 1,
        aoeUnlockRank: Int = 0,
        aoeBonusDamagePct: Double = 0.0
    ): SkillResolutionResult {
        val attackStats = applyTransientBonuses(attacker.stats, extraBonuses)
        val result = Combat.attack(attackStats, defender.stats, rng, preferMagic = preferMagic)
        val conditionalStatusMultiplier = statusProcessor.statusConditionalDamageMultiplier(attacker, defender)
        val finalMultiplier = (
            attacker.runtime.statusDamageMultiplier *
                attacker.runtime.tempBuffDamageMultiplier *
                actionMultiplier *
                conditionalStatusMultiplier
            ).coerceAtLeast(0.1)
        val typeCounterMultiplier = monsterTypeDamageMultiplier(attacker, defender)
        val affinityMultiplier = if (result.hit) {
            statusProcessor.directDamageAffinityMultiplier(defender, result.type)
        } else {
            1.0
        }
        val scaledDamage = if (result.hit) {
            (result.damage * finalMultiplier * affinityMultiplier * typeCounterMultiplier).coerceAtLeast(1.0)
        } else {
            0.0
        }
        val scaledLifesteal = if (result.hit && result.damage > 0.0) {
            (result.lifesteal * (scaledDamage / result.damage)).coerceAtLeast(0.0)
        } else {
            0.0
        }
        if (result.hit && scaledDamage > 0.0) {
            if (attacker.kind == CombatantKind.PLAYER) {
                telemetry.playerDamageDealt += scaledDamage
            } else {
                telemetry.playerDamageTaken += scaledDamage
            }
        }
        if (attacker.kind == CombatantKind.PLAYER) {
            if (result.hit) {
                val critLabel = if (result.crit) " CRITICO!" else ""
                val typeLabel = if (result.type == rpg.engine.DamageType.MAGIC) "magico" else "fisico"
                val abilityLabel = if (!actionName.isNullOrBlank()) " com $actionName" else ""
                val affinityLabel = when {
                    affinityMultiplier >= 1.08 -> " Fraqueza explorada!"
                    affinityMultiplier <= 0.92 -> " O inimigo resistiu."
                    else -> ""
                }
                val typeBonusLabel = if (typeCounterMultiplier > 1.001) {
                    " Mestre de especie ativo."
                } else {
                    ""
                }
                logBuilder.combatLog(
                    logBuilder.colorize(
                        "Voce causou ${logBuilder.format(scaledDamage)} de dano $typeLabel$abilityLabel.$critLabel$affinityLabel$typeBonusLabel",
                        CombatLogBuilder.ansiCyan
                    )
                )
            } else {
                if (actionName.isNullOrBlank()) {
                    logBuilder.combatLog(logBuilder.colorize("Voce errou!", CombatLogBuilder.ansiYellow))
                } else {
                    logBuilder.combatLog(logBuilder.colorize("Voce usou $actionName, mas errou!", CombatLogBuilder.ansiYellow))
                }
            }
        } else {
            if (result.hit) {
                val critLabel = if (result.crit) " CRITICO!" else ""
                val typeLabel = if (result.type == rpg.engine.DamageType.MAGIC) "magico" else "fisico"
                val abilityLabel = if (!actionName.isNullOrBlank()) " com $actionName" else ""
                logBuilder.combatLog(logBuilder.colorize("O inimigo causou ${logBuilder.format(scaledDamage)} de dano $typeLabel$abilityLabel.$critLabel", CombatLogBuilder.ansiRed))
            } else {
                logBuilder.combatLog(logBuilder.colorize("O inimigo errou!", CombatLogBuilder.ansiYellow))
            }
        }

        var statusAppliedCount = 0
        var carryBonusPct = 0.0
        var targetDefeated = false
        if (result.hit) {
            defender.currentHp = max(0.0, defender.currentHp - scaledDamage)
            if (skillRank >= aoeUnlockRank.coerceAtLeast(1) && aoeBonusDamagePct > 0.0) {
                val splash = (scaledDamage * (aoeBonusDamagePct / 100.0)).coerceAtLeast(0.0)
                if (splash > 0.0 && defender.currentHp > 0.0) {
                    defender.currentHp = (defender.currentHp - splash).coerceAtLeast(0.0)
                    if (attacker.kind == CombatantKind.PLAYER) {
                        logBuilder.combatLog(logBuilder.colorize("Impacto em area: ${logBuilder.format(splash)} de dano adicional.", CombatLogBuilder.ansiCyan))
                    }
                }
            }
            if (defender.currentHp < 0.05) {
                defender.currentHp = 0.0
            }
            if (defender.currentHp <= 0.0) {
                targetDefeated = true
                defender.runtime = defender.runtime.copy(
                    state = CombatState.DEAD,
                    readySinceSeconds = null
                )
            }
            if (scaledLifesteal > 0.0) {
                attacker.currentHp = (attacker.currentHp + scaledLifesteal)
                    .coerceAtMost(attacker.stats.derived.hpMax)
            }
            val manaOnHit = attacker.talentModifiers.atb.manaOnHitFlat +
                attacker.stats.derived.mpMax * (attacker.talentModifiers.atb.manaOnHitPctMax / 100.0)
            if (manaOnHit > 0.0) {
                attacker.currentMp = (attacker.currentMp + manaOnHit).coerceAtMost(attacker.stats.derived.mpMax)
            }
            maybeApplyReflectDamage(
                attacker = attacker,
                defender = defender,
                dealtDamage = scaledDamage,
                telemetry = telemetry
            )
            statusProcessor.applyActionBarShiftOnHit(attacker, defender)
            statusProcessor.applyActionBarGainOnDamaged(defender)
            statusProcessor.maybeInterruptCast(attacker, defender)
            statusAppliedCount = statusProcessor.applyOnHitStatuses(attacker, defender, extraOnHitStatuses)
            if (result.crit) {
                if (attacker.kind == CombatantKind.PLAYER) {
                    telemetry.playerCriticalHits += 1
                }
                carryBonusPct += attacker.talentModifiers.atb.actionBarGainOnCritPct
            }
            if (statusAppliedCount > 0) {
                carryBonusPct += attacker.talentModifiers.atb.actionBarGainOnStatusApplyPct * statusAppliedCount
                applyTemporaryBuffOnStatusApplied(attacker, statusAppliedCount)
            }
            if (targetDefeated) {
                applyCooldownReductionOnKill(attacker)
            }
            statusProcessor.consumeTargetStatusesOnHit(attacker, defender)
        }

        val skillHeal = selfHealFlat.coerceAtLeast(0.0) +
            attacker.stats.derived.hpMax * (selfHealPctMaxHp.coerceAtLeast(0.0) / 100.0)
        if (skillHeal > 0.0 && attacker.currentHp > 0.0) {
            val before = attacker.currentHp
            attacker.currentHp = (attacker.currentHp + skillHeal).coerceAtMost(attacker.stats.derived.hpMax)
            val healed = attacker.currentHp - before
            if (healed > 0.0) {
                val subject = if (attacker.kind == CombatantKind.PLAYER) "Voce" else "O inimigo"
                logBuilder.combatLog(logBuilder.colorize("$subject recuperou ${logBuilder.format(healed)} de HP.", CombatLogBuilder.ansiGreen))
            }
        }
        return SkillResolutionResult(
            hit = result.hit,
            crit = result.crit,
            targetDefeated = targetDefeated,
            statusesApplied = statusAppliedCount,
            carryBonusPct = carryBonusPct
        )
    }

    private fun applyCooldownReductionOnKill(attacker: CombatActor) {
        val reduction = attacker.talentModifiers.atb.cooldownReductionOnKillSeconds.coerceAtLeast(0.0)
        if (reduction <= 0.0) return
        if (attacker.runtime.skillCooldowns.isEmpty()) return
        attacker.runtime = attacker.runtime.copy(
            skillCooldowns = attacker.runtime.skillCooldowns.mapValues { (_, seconds) ->
                (seconds - reduction).coerceAtLeast(0.0)
            }.filterValues { it > 0.0 }
        )
        logBuilder.combatLog(logBuilder.colorize("Talento: abate reduziu cooldowns em ${logBuilder.format(reduction)}s.", CombatLogBuilder.ansiBlue))
    }

    private fun applyTemporaryBuffOnStatusApplied(attacker: CombatActor, appliedCount: Int) {
        if (appliedCount <= 0) return
        val atb = attacker.talentModifiers.atb
        val duration = atb.tempBuffDurationSeconds.coerceAtLeast(0.0)
        if (duration <= 0.0) return
        val damagePct = atb.tempDamageBuffOnStatusApplyPct * appliedCount
        val fillPct = atb.tempFillRateBuffOnStatusApplyPct * appliedCount
        if (damagePct <= 0.0 && fillPct <= 0.0) return

        val damageMult = (1.0 + damagePct / 100.0).coerceIn(1.0, 5.0)
        val fillMult = (1.0 + fillPct / 100.0).coerceIn(1.0, 5.0)
        val current = attacker.runtime
        attacker.runtime = current.copy(
            tempBuffRemainingSeconds = max(current.tempBuffRemainingSeconds, duration),
            tempBuffDamageMultiplier = max(current.tempBuffDamageMultiplier, damageMult),
            tempBuffFillRateMultiplier = max(current.tempBuffFillRateMultiplier, fillMult)
        )
        logBuilder.combatLog(
            logBuilder.colorize(
                "Talento: bonus temporario ativado por aplicacao de status (${logBuilder.format(duration)}s).",
                CombatLogBuilder.ansiBlue
            )
        )
    }

    private fun maybeApplyReflectDamage(
        attacker: CombatActor,
        defender: CombatActor,
        dealtDamage: Double,
        telemetry: MutableCombatTelemetry
    ) {
        if (dealtDamage <= 0.0) return
        if (attacker.currentHp <= 0.0 || attacker.runtime.state == CombatState.DEAD) return
        val reflectPct = defender.talentModifiers.status.reflectDamagePct.coerceIn(0.0, 90.0)
        if (reflectPct <= 0.0) return

        val reflectedDamage = (dealtDamage * (reflectPct / 100.0)).coerceAtLeast(0.0)
        if (reflectedDamage <= 0.0) return

        attacker.currentHp = (attacker.currentHp - reflectedDamage).coerceAtLeast(0.0)
        if (attacker.kind == CombatantKind.PLAYER) {
            telemetry.playerDamageTaken += reflectedDamage
        } else {
            telemetry.playerDamageDealt += reflectedDamage
        }
        if (attacker.currentHp < 0.05) {
            attacker.currentHp = 0.0
        }
        if (attacker.currentHp <= 0.0) {
            attacker.runtime = attacker.runtime.copy(
                state = CombatState.DEAD,
                readySinceSeconds = null
            )
        }

        val subject = logBuilder.subjectLabel(attacker)
        logBuilder.combatLog(logBuilder.colorize("Reflexo: $subject sofreu ${logBuilder.format(reflectedDamage)} de dano.", CombatLogBuilder.ansiBlue))
    }

    private fun applyTransientBonuses(stats: ComputedStats, bonuses: Bonuses): ComputedStats {
        if (bonuses == Bonuses()) return stats
        val attributes = stats.attributes + bonuses.attributes
        val derived = (stats.derived + bonuses.derivedAdd).applyMultiplier(bonuses.derivedMult)
        return ComputedStats(attributes = attributes, derived = derived)
    }

    private fun monsterTypeDamageMultiplier(attacker: CombatActor, defender: CombatActor): Double {
        if (attacker.kind != CombatantKind.PLAYER || defender.kind != CombatantKind.MONSTER) return 1.0
        if (attacker.monsterTypeDamageBonusPct.isEmpty()) return 1.0
        val typeId = MonsterTypeMasteryService.normalizeType(defender.monsterTypeId)
        val bonusPct = attacker.monsterTypeDamageBonusPct[typeId]?.coerceIn(0.0, 10.0) ?: 0.0
        return 1.0 + (bonusPct / 100.0)
    }
}
