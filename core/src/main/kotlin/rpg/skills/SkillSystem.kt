package rpg.skills

import kotlin.math.max
import rpg.model.PlayerState
import rpg.model.SkillDef
import rpg.model.SkillProgressState
import rpg.model.SkillSnapshot
import rpg.model.SkillType
import rpg.model.requiredXpForLevel

data class SkillGainResult(
    val player: PlayerState,
    val skill: SkillType,
    val gainedXp: Double,
    val levelUps: Int,
    val snapshot: SkillSnapshot
)

class SkillSystem(
    private val definitions: Map<SkillType, SkillDef>
) {
    private val defaults: Map<SkillType, SkillDef> = SkillType.entries.associateWith { type ->
        SkillDef(id = type, name = type.name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() })
    }

    fun ensureProgress(player: PlayerState): PlayerState {
        val normalized = ensureProgressMap(player.skillProgress)
        if (normalized == player.skillProgress) return player
        return player.copy(skillProgress = normalized)
    }

    fun snapshot(player: PlayerState, skill: SkillType): SkillSnapshot {
        val progress = ensureProgressMap(player.skillProgress)[skill.name] ?: SkillProgressState()
        return toSnapshot(skill, progress)
    }

    fun actionDurationSeconds(baseSeconds: Double, skillLevel: Int): Double {
        val base = baseSeconds.coerceAtLeast(1.0)
        val efficiency = efficiencyMultiplier(skillLevel)
        return (base / (1.0 + efficiency)).coerceAtLeast(1.0)
    }

    fun efficiencyMultiplier(skillLevel: Int): Double = skillLevel.coerceAtLeast(1) * 0.08

    fun gainXp(
        player: PlayerState,
        skill: SkillType,
        baseXp: Double,
        rarityMultiplier: Double = 1.0,
        difficulty: Double = 1.0,
        tier: Int = 1
    ): SkillGainResult {
        val normalized = ensureProgressMap(player.skillProgress).toMutableMap()
        val progress = normalized[skill.name] ?: SkillProgressState()
        val gained = (baseXp * rarityMultiplier * difficulty * max(1, tier)).coerceAtLeast(1.0)

        var level = progress.level.coerceAtLeast(1)
        var current = progress.currentXp + gained
        var levelUps = 0
        var required = requiredXpFor(skill, level)
        while (current >= required) {
            current -= required
            level++
            levelUps++
            required = requiredXpFor(skill, level)
        }

        val updated = progress.copy(
            level = level,
            currentXp = current,
            lifetimeXp = progress.lifetimeXp + gained
        )
        normalized[skill.name] = updated
        val updatedPlayer = player.copy(skillProgress = normalized)
        return SkillGainResult(
            player = updatedPlayer,
            skill = skill,
            gainedXp = gained,
            levelUps = levelUps,
            snapshot = toSnapshot(skill, updated)
        )
    }

    fun unlocksAtOrBelow(skill: SkillType, level: Int) =
        definition(skill).unlocks.filter { it.level <= level.coerceAtLeast(1) }.sortedBy { it.level }

    fun requiredXpFor(skill: SkillType, level: Int): Double {
        val def = definition(skill)
        return requiredXpForLevel(def.baseXp, level)
    }

    private fun toSnapshot(skill: SkillType, progress: SkillProgressState): SkillSnapshot {
        val level = progress.level.coerceAtLeast(1)
        return SkillSnapshot(
            skill = skill,
            level = level,
            currentXp = progress.currentXp,
            requiredXp = requiredXpFor(skill, level),
            efficiencyMultiplier = efficiencyMultiplier(level),
            doubleDropChancePct = (level * 0.6).coerceAtMost(25.0),
            criticalCraftChancePct = (level * 0.5).coerceAtMost(20.0),
            materialReductionChancePct = (level * 0.35).coerceAtMost(15.0),
            noConsumeChancePct = (level * 0.2).coerceAtMost(10.0),
            unlocks = unlocksAtOrBelow(skill, level)
        )
    }

    private fun definition(skill: SkillType): SkillDef {
        return definitions[skill] ?: defaults.getValue(skill)
    }

    private fun ensureProgressMap(raw: Map<String, SkillProgressState>): Map<String, SkillProgressState> {
        if (raw.isEmpty() && definitions.isEmpty()) {
            return SkillType.entries.associate { it.name to SkillProgressState() }
        }
        val mutable = raw.toMutableMap()
        for (type in SkillType.entries) {
            mutable.putIfAbsent(type.name, SkillProgressState())
        }
        return mutable.toMap()
    }
}
