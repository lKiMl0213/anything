package rpg.globalboss.services

import rpg.engine.GameEngine
import rpg.globalboss.config.GlobalBossRewardDef
import rpg.model.PlayerState
import rpg.progression.ExperienceEngine

internal data class GlobalBossRewardApplyResult(
    val player: PlayerState,
    val lines: List<String>
)

internal class GlobalBossRewardService(
    private val engine: GameEngine
) {
    fun apply(player: PlayerState, reward: GlobalBossRewardDef): GlobalBossRewardApplyResult {
        var updated = player
        val lines = mutableListOf<String>()
        if (reward.gold != 0) {
            updated = updated.copy(gold = updated.gold + reward.gold)
            lines += "Recompensa: Ouro ${if (reward.gold >= 0) "+" else ""}${reward.gold}"
        }
        if (reward.questCurrency != 0) {
            updated = updated.copy(questCurrency = updated.questCurrency + reward.questCurrency)
            lines += "Recompensa: Moeda de quest ${if (reward.questCurrency >= 0) "+" else ""}${reward.questCurrency}"
        }
        if (reward.premiumCash != 0) {
            updated = updated.copy(premiumCash = updated.premiumCash + reward.premiumCash)
            lines += "Recompensa: CASH ${if (reward.premiumCash >= 0) "+" else ""}${reward.premiumCash}"
        }
        if (reward.xp > 0) {
            val beforeLevel = updated.level
            updated = ExperienceEngine.applyXp(updated, reward.xp)
            if (updated.level > beforeLevel) {
                val classDef = engine.classSystem.classDef(updated.classId)
                val raceDef = engine.classSystem.raceDef(updated.raceId)
                val subclassDef = engine.classSystem.subclassDef(updated.subclassId)
                val specializationDef = engine.classSystem.specializationDef(updated.specializationId)
                repeat(updated.level - beforeLevel) {
                    updated = engine.applyAutoPoints(updated, classDef, raceDef, subclassDef, specializationDef)
                }
            }
            lines += "Recompensa: XP +${reward.xp}"
            if (updated.level > beforeLevel) {
                lines += "Level up! Agora no nível ${updated.level}."
            }
        }
        return GlobalBossRewardApplyResult(updated, lines)
    }
}

