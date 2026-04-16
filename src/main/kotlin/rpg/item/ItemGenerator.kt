package rpg.item

import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import rpg.model.AffixDef
import rpg.model.AffixKind
import rpg.model.Bonuses
import rpg.model.DerivedStats
import rpg.model.ItemBaseStat
import rpg.model.ItemInstance
import rpg.model.ItemTemplateDef

object ItemGenerator {
    fun generate(
        template: ItemTemplateDef,
        level: Int,
        rarity: ItemRarity,
        rng: Random,
        affixPool: Map<String, AffixDef>
    ): ItemInstance {
        val basePower = rollBasePower(level, template.baseScaling, rng) * rarity.powerMultiplier
        val baseBonuses = baseBonuses(template.baseStat, basePower)
        val budget = powerBudget(level, rarity)
        val (affixBonuses, affixNames) = rollAffixes(template.allowedAffixes, budget, rarity, rng, affixPool)
        val (specialBonuses, specialNames) = if (rarity == ItemRarity.LEGENDARY) {
            rollSpecialAffix(rng, affixPool)
        } else {
            Bonuses() to emptyList()
        }
        val totalBonuses = baseBonuses + affixBonuses + specialBonuses
        val allAffixes = affixNames + specialNames
        val name = ItemNameGenerator.generate(template.name, rarity, allAffixes, rng)
        val value = max(1, (basePower * template.vendorBaseValue).roundToInt())

        return ItemInstance(
            id = UUID.randomUUID().toString(),
            templateId = template.id,
            name = name,
            level = level,
            minLevel = max(template.minLevel, level),
            rarity = rarity,
            type = template.type,
            slot = template.slot,
            twoHanded = template.twoHanded,
            tags = template.tags,
            bonuses = totalBonuses,
            value = value,
            description = allAffixes.joinToString(", "),
            affixes = allAffixes
        )
    }

    private fun rollBasePower(level: Int, scaling: Double, rng: Random): Double {
        val base = level.coerceAtLeast(1) * scaling
        val variance = base * 0.10
        return base + rng.nextDouble(-variance, variance)
    }

    private fun powerBudget(level: Int, rarity: ItemRarity): Int {
        val budget = level.coerceAtLeast(1) * 10
        return (budget * rarity.powerMultiplier).roundToInt()
    }

    private fun rollAffixes(
        allowed: List<String>,
        budget: Int,
        rarity: ItemRarity,
        rng: Random,
        affixPool: Map<String, AffixDef>
    ): Pair<Bonuses, List<String>> {
        if (allowed.isEmpty() || rarity.affixMax == 0) return Bonuses() to emptyList()
        val affixIds = allowed.filter { affixPool.containsKey(it) }.toMutableList()
        if (affixIds.isEmpty()) return Bonuses() to emptyList()

        val count = rng.nextInt(rarity.affixMin, rarity.affixMax + 1)
        var remaining = budget
        var bonuses = Bonuses()
        val names = mutableListOf<String>()

        repeat(count) {
            val candidates = affixIds.filter { id ->
                val cost = affixPool[id]?.cost ?: 0
                cost <= remaining
            }
            if (candidates.isEmpty()) return@repeat
            val choice = pickWeighted(candidates, affixPool, rng)
            val affix = affixPool[choice] ?: return@repeat
            remaining -= affix.cost
            bonuses += affix.bonuses
            names.add(affix.name)
            affixIds.remove(choice)
        }

        return bonuses to names
    }

    private fun rollSpecialAffix(
        rng: Random,
        affixPool: Map<String, AffixDef>
    ): Pair<Bonuses, List<String>> {
        val specials = affixPool.values.filter { it.kind == AffixKind.SPECIAL }
        if (specials.isEmpty()) return Bonuses() to emptyList()
        val choice = pickWeighted(specials.map { it.id }, affixPool, rng)
        val affix = affixPool[choice] ?: return Bonuses() to emptyList()
        return affix.bonuses to listOf(affix.name)
    }

    private fun pickWeighted(
        candidates: List<String>,
        pool: Map<String, AffixDef>,
        rng: Random
    ): String {
        val total = candidates.sumOf { pool[it]?.weight ?: 0 }.coerceAtLeast(1)
        var roll = rng.nextInt(total)
        for (id in candidates) {
            roll -= pool[id]?.weight ?: 0
            if (roll < 0) return id
        }
        return candidates.first()
    }

    private fun baseBonuses(baseStat: ItemBaseStat, power: Double): Bonuses {
        val derivedAdd = when (baseStat) {
            ItemBaseStat.DAMAGE_PHYSICAL -> DerivedStats(damagePhysical = power)
            ItemBaseStat.DAMAGE_MAGIC -> DerivedStats(damageMagic = power)
            ItemBaseStat.DEF_PHYSICAL -> DerivedStats(defPhysical = power)
            ItemBaseStat.DEF_MAGIC -> DerivedStats(defMagic = power)
            ItemBaseStat.HP_MAX -> DerivedStats(hpMax = power * 5)
            ItemBaseStat.MP_MAX -> DerivedStats(mpMax = power * 4)
            ItemBaseStat.CRIT_CHANCE -> DerivedStats(critChancePct = power * 0.3)
            ItemBaseStat.ATTACK_SPEED -> DerivedStats(attackSpeed = power * 0.5)
        }
        return Bonuses(derivedAdd = derivedAdd)
    }
}
