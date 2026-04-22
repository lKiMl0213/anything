package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class DerivedStats(
    val damagePhysical: Double = 0.0,
    val damageMagic: Double = 0.0,
    val hpMax: Double = 0.0,
    val mpMax: Double = 0.0,
    val defPhysical: Double = 0.0,
    val defMagic: Double = 0.0,
    val attackSpeed: Double = 0.0,
    val moveSpeed: Double = 0.0,
    val critChancePct: Double = 0.0,
    val critDamagePct: Double = 0.0,
    val vampirismPct: Double = 0.0,
    val cdrPct: Double = 0.0,
    val dropBonusPct: Double = 0.0,
    val penPhysical: Double = 0.0,
    val penMagic: Double = 0.0,
    val hpRegen: Double = 0.0,
    val mpRegen: Double = 0.0,
    val accuracy: Double = 0.0,
    val evasion: Double = 0.0,
    val tenacityPct: Double = 0.0,
    val damageReductionPct: Double = 0.0,
    val xpGainPct: Double = 0.0
) {
    operator fun plus(other: DerivedStats): DerivedStats = DerivedStats(
        damagePhysical = damagePhysical + other.damagePhysical,
        damageMagic = damageMagic + other.damageMagic,
        hpMax = hpMax + other.hpMax,
        mpMax = mpMax + other.mpMax,
        defPhysical = defPhysical + other.defPhysical,
        defMagic = defMagic + other.defMagic,
        attackSpeed = attackSpeed + other.attackSpeed,
        moveSpeed = moveSpeed + other.moveSpeed,
        critChancePct = critChancePct + other.critChancePct,
        critDamagePct = critDamagePct + other.critDamagePct,
        vampirismPct = vampirismPct + other.vampirismPct,
        cdrPct = cdrPct + other.cdrPct,
        dropBonusPct = dropBonusPct + other.dropBonusPct,
        penPhysical = penPhysical + other.penPhysical,
        penMagic = penMagic + other.penMagic,
        hpRegen = hpRegen + other.hpRegen,
        mpRegen = mpRegen + other.mpRegen,
        accuracy = accuracy + other.accuracy,
        evasion = evasion + other.evasion,
        tenacityPct = tenacityPct + other.tenacityPct,
        damageReductionPct = damageReductionPct + other.damageReductionPct,
        xpGainPct = xpGainPct + other.xpGainPct
    )

    fun applyMultiplier(mult: DerivedStats): DerivedStats = DerivedStats(
        damagePhysical = damagePhysical * (1.0 + mult.damagePhysical / 100.0),
        damageMagic = damageMagic * (1.0 + mult.damageMagic / 100.0),
        hpMax = hpMax * (1.0 + mult.hpMax / 100.0),
        mpMax = mpMax * (1.0 + mult.mpMax / 100.0),
        defPhysical = defPhysical * (1.0 + mult.defPhysical / 100.0),
        defMagic = defMagic * (1.0 + mult.defMagic / 100.0),
        attackSpeed = attackSpeed * (1.0 + mult.attackSpeed / 100.0),
        moveSpeed = moveSpeed * (1.0 + mult.moveSpeed / 100.0),
        critChancePct = critChancePct * (1.0 + mult.critChancePct / 100.0),
        critDamagePct = critDamagePct * (1.0 + mult.critDamagePct / 100.0),
        vampirismPct = vampirismPct * (1.0 + mult.vampirismPct / 100.0),
        cdrPct = cdrPct * (1.0 + mult.cdrPct / 100.0),
        dropBonusPct = dropBonusPct * (1.0 + mult.dropBonusPct / 100.0),
        penPhysical = penPhysical * (1.0 + mult.penPhysical / 100.0),
        penMagic = penMagic * (1.0 + mult.penMagic / 100.0),
        hpRegen = hpRegen * (1.0 + mult.hpRegen / 100.0),
        mpRegen = mpRegen * (1.0 + mult.mpRegen / 100.0),
        accuracy = accuracy * (1.0 + mult.accuracy / 100.0),
        evasion = evasion * (1.0 + mult.evasion / 100.0),
        tenacityPct = tenacityPct * (1.0 + mult.tenacityPct / 100.0),
        damageReductionPct = damageReductionPct * (1.0 + mult.damageReductionPct / 100.0),
        xpGainPct = xpGainPct * (1.0 + mult.xpGainPct / 100.0)
    )

    fun scale(factor: Double): DerivedStats {
        val safe = factor.coerceAtLeast(0.0)
        return DerivedStats(
            damagePhysical = damagePhysical * safe,
            damageMagic = damageMagic * safe,
            hpMax = hpMax * safe,
            mpMax = mpMax * safe,
            defPhysical = defPhysical * safe,
            defMagic = defMagic * safe,
            attackSpeed = attackSpeed * safe,
            moveSpeed = moveSpeed * safe,
            critChancePct = critChancePct * safe,
            critDamagePct = critDamagePct * safe,
            vampirismPct = vampirismPct * safe,
            cdrPct = cdrPct * safe,
            dropBonusPct = dropBonusPct * safe,
            penPhysical = penPhysical * safe,
            penMagic = penMagic * safe,
            hpRegen = hpRegen * safe,
            mpRegen = mpRegen * safe,
            accuracy = accuracy * safe,
            evasion = evasion * safe,
            tenacityPct = tenacityPct * safe,
            damageReductionPct = damageReductionPct * safe,
            xpGainPct = xpGainPct * safe
        )
    }
}
