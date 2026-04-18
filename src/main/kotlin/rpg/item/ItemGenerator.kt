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
import rpg.model.ItemEffects
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
        val effectiveRarity = ItemRarity.clamp(rarity, template.rarity, template.maxRarity)
        val qualityRollPct = rollQuality(effectiveRarity, rng)
        val qualityMultiplier = qualityRollPct / 100.0
        val basePower = rollBasePower(level, template.baseScaling, rng) * effectiveRarity.powerMultiplier * qualityMultiplier
        val baseBonuses = baseBonuses(template.baseStat, basePower)
        val budget = powerBudget(level, effectiveRarity, qualityRollPct)
        val affixResult = rollAffixes(
            template = template,
            budget = budget,
            rarity = effectiveRarity,
            rng = rng,
            affixPool = affixPool
        )
        val specialResult = if (rollSpecialAffixChance(effectiveRarity, rng)) {
            rollSpecialAffix(template, effectiveRarity, rng, affixPool)
        } else {
            AffixRollResult()
        }

        val totalBonuses = baseBonuses + affixResult.bonuses + specialResult.bonuses
        val totalEffects = affixResult.effects.merge(specialResult.effects)
        val allAffixes = affixResult.names + specialResult.names
        val name = ItemNameGenerator.generate(template.name, effectiveRarity, allAffixes, rng)
        val value = max(
            1,
            (
                basePower *
                    template.vendorBaseValue *
                    effectiveRarity.valueMultiplier *
                    (1.0 + allAffixes.size * 0.16)
            ).roundToInt()
        )
        val powerScore = (
            basePower +
                totalBonusMagnitude(totalBonuses) * 0.8 +
                allAffixes.size * 8 +
                (qualityRollPct - 100).coerceAtLeast(0) * 0.6
            ).roundToInt().coerceAtLeast(1)

        val descriptionParts = mutableListOf<String>()
        if (template.description.isNotBlank()) {
            descriptionParts += template.description.trim()
        }
        if (allAffixes.isNotEmpty()) {
            descriptionParts += "Afixos: ${allAffixes.joinToString(", ")}"
        }
        descriptionParts += "Qualidade: $qualityRollPct%"

        return ItemInstance(
            id = UUID.randomUUID().toString(),
            templateId = template.id,
            name = name,
            level = level,
            minLevel = max(template.minLevel, level),
            rarity = effectiveRarity,
            qualityRollPct = qualityRollPct,
            powerScore = powerScore,
            type = template.type,
            slot = template.slot,
            twoHanded = template.twoHanded,
            tags = template.tags,
            bonuses = totalBonuses,
            effects = totalEffects,
            value = value,
            description = descriptionParts.joinToString(" | "),
            affixes = allAffixes
        )
    }

    private fun rollBasePower(level: Int, scaling: Double, rng: Random): Double {
        val base = level.coerceAtLeast(1) * scaling.coerceAtLeast(0.1)
        val variance = base * 0.10
        return base + rng.nextDouble(-variance, variance)
    }

    private fun rollQuality(rarity: ItemRarity, rng: Random): Int {
        val min = when (rarity) {
            ItemRarity.COMMON -> 88
            ItemRarity.UNCOMMON -> 92
            ItemRarity.RARE -> 96
            ItemRarity.EPIC -> 101
            ItemRarity.LEGENDARY -> 106
            ItemRarity.MYTHIC -> 112
        }
        val max = when (rarity) {
            ItemRarity.COMMON -> 103
            ItemRarity.UNCOMMON -> 107
            ItemRarity.RARE -> 111
            ItemRarity.EPIC -> 116
            ItemRarity.LEGENDARY -> 122
            ItemRarity.MYTHIC -> 130
        }
        return rng.nextInt(min, max + 1)
    }

    private fun powerBudget(level: Int, rarity: ItemRarity, qualityRollPct: Int): Int {
        val budget = level.coerceAtLeast(1) * 10
        val rarityBudget = budget * rarity.powerMultiplier
        val qualityMultiplier = 1.0 + ((qualityRollPct - 100).coerceAtLeast(-10) / 100.0)
        return (rarityBudget * qualityMultiplier).roundToInt().coerceAtLeast(1)
    }

    private fun rollAffixes(
        template: ItemTemplateDef,
        budget: Int,
        rarity: ItemRarity,
        rng: Random,
        affixPool: Map<String, AffixDef>
    ): AffixRollResult {
        if (template.allowedAffixes.isEmpty() || rarity.affixMax == 0) return AffixRollResult()
        val templateTags = template.tags.map { it.trim().lowercase() }
        val affixIds = template.allowedAffixes.filter { id ->
            val affix = affixPool[id] ?: return@filter false
            affixAvailableForTemplate(affix, template, templateTags, rarity)
        }.toMutableList()
        if (affixIds.isEmpty()) return AffixRollResult()

        val count = rng.nextInt(rarity.affixMin, rarity.affixMax + 1)
        var remaining = budget
        var bonuses = Bonuses()
        var effects = ItemEffects()
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
            effects = effects.merge(affix.effects)
            names.add(affix.name)
            affixIds.remove(choice)
        }

        return AffixRollResult(bonuses = bonuses, effects = effects, names = names)
    }

    private fun rollSpecialAffix(
        template: ItemTemplateDef,
        rarity: ItemRarity,
        rng: Random,
        affixPool: Map<String, AffixDef>
    ): AffixRollResult {
        val templateTags = template.tags.map { it.trim().lowercase() }
        val specials = affixPool.values.filter {
            it.kind == AffixKind.SPECIAL && affixAvailableForTemplate(it, template, templateTags, rarity)
        }
        if (specials.isEmpty()) return AffixRollResult()
        val choice = pickWeighted(specials.map { it.id }, affixPool, rng)
        val affix = affixPool[choice] ?: return AffixRollResult()
        return AffixRollResult(
            bonuses = affix.bonuses,
            effects = affix.effects,
            names = listOf(affix.name)
        )
    }

    private fun rollSpecialAffixChance(rarity: ItemRarity, rng: Random): Boolean {
        val chance = when (rarity) {
            ItemRarity.COMMON, ItemRarity.UNCOMMON, ItemRarity.RARE -> 0.0
            ItemRarity.EPIC -> 8.0
            ItemRarity.LEGENDARY -> 28.0
            ItemRarity.MYTHIC -> 65.0
        }
        return rng.nextDouble(0.0, 100.0) <= chance
    }

    private fun affixAvailableForTemplate(
        affix: AffixDef,
        template: ItemTemplateDef,
        templateTags: List<String>,
        rarity: ItemRarity
    ): Boolean {
        if (rarity.ordinal !in affix.minRarity.ordinal..affix.maxRarity.ordinal) return false
        if (affix.allowedTypes.isNotEmpty() && template.type !in affix.allowedTypes) return false
        if (affix.allowedSlots.isNotEmpty() && template.slot !in affix.allowedSlots) return false
        if (affix.requiredTagsAny.isNotEmpty() && affix.requiredTagsAny.none { it.lowercase() in templateTags }) return false
        if (affix.requiredTagsAll.any { it.lowercase() !in templateTags }) return false
        if (affix.blockedTags.any { it.lowercase() in templateTags }) return false
        return true
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

    private fun totalBonusMagnitude(bonuses: Bonuses): Double {
        val attrs = bonuses.attributes
        val add = bonuses.derivedAdd
        val mult = bonuses.derivedMult
        return attrs.str +
            attrs.agi +
            attrs.dex +
            attrs.vit +
            attrs.`int` +
            attrs.spr +
            attrs.luk +
            add.damagePhysical +
            add.damageMagic +
            add.hpMax * 0.2 +
            add.mpMax * 0.18 +
            add.defPhysical +
            add.defMagic +
            add.attackSpeed * 12 +
            add.moveSpeed * 15 +
            add.critChancePct * 6 +
            add.critDamagePct * 0.8 +
            add.vampirismPct * 3 +
            add.cdrPct * 5 +
            add.dropBonusPct * 3 +
            add.penPhysical * 1.5 +
            add.penMagic * 1.5 +
            add.hpRegen * 8 +
            add.mpRegen * 8 +
            add.accuracy * 0.8 +
            add.evasion * 0.8 +
            add.tenacityPct * 3 +
            add.damageReductionPct * 6 +
            add.xpGainPct * 2 +
            mult.damagePhysical * 1.1 +
            mult.damageMagic * 1.1 +
            mult.defPhysical * 0.9 +
            mult.defMagic * 0.9 +
            mult.hpMax * 0.8 +
            mult.mpMax * 0.8 +
            mult.attackSpeed * 1.2 +
            mult.moveSpeed * 1.2 +
            mult.critChancePct * 1.5 +
            mult.critDamagePct * 0.9 +
            mult.vampirismPct * 0.9 +
            mult.cdrPct * 1.2 +
            mult.dropBonusPct * 0.7 +
            mult.penPhysical * 0.8 +
            mult.penMagic * 0.8 +
            mult.hpRegen * 0.8 +
            mult.mpRegen * 0.8 +
            mult.accuracy * 0.8 +
            mult.evasion * 0.8 +
            mult.tenacityPct * 0.8 +
            mult.damageReductionPct * 1.4 +
            mult.xpGainPct * 0.7
    }

    private data class AffixRollResult(
        val bonuses: Bonuses = Bonuses(),
        val effects: ItemEffects = ItemEffects(),
        val names: List<String> = emptyList()
    )

    private fun ItemEffects.merge(other: ItemEffects): ItemEffects {
        if (other == ItemEffects()) return this
        return copy(
            hpRestore = hpRestore + other.hpRestore,
            mpRestore = mpRestore + other.mpRestore,
            hpRestorePct = hpRestorePct + other.hpRestorePct,
            mpRestorePct = mpRestorePct + other.mpRestorePct,
            fullRestore = fullRestore || other.fullRestore,
            clearNegativeStatuses = clearNegativeStatuses || other.clearNegativeStatuses,
            statusImmunitySeconds = statusImmunitySeconds + other.statusImmunitySeconds,
            roomAttributeMultiplierPct = roomAttributeMultiplierPct + other.roomAttributeMultiplierPct,
            roomAttributeDurationRooms = max(roomAttributeDurationRooms, other.roomAttributeDurationRooms),
            runAttributeMultiplierPct = runAttributeMultiplierPct + other.runAttributeMultiplierPct,
            applyStatuses = applyStatuses + other.applyStatuses
        )
    }
}
