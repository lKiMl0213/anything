package rpg.monster

import rpg.combat.CombatRuntimeState
import rpg.model.Attributes
import rpg.model.Bonuses
import rpg.model.CombatStatusApplyDef

data class MonsterInstance(
    val archetypeId: String,
    val id: String = archetypeId,
    val sourceArchetypeId: String = archetypeId,
    val baseType: String = "",
    val monsterTypeId: String = "",
    val family: String = "",
    val name: String,
    val displayName: String = name,
    val variantName: String = "",
    val level: Int,
    val rarity: MonsterRarity,
    val attributes: Attributes,
    val bonuses: Bonuses,
    val tags: Set<String>,
    val questTags: Set<String> = emptySet(),
    val modifiers: List<String> = emptyList(),
    val affixes: List<String> = emptyList(),
    val personality: MonsterPersonality,
    val starCount: Int,
    val stars: Int = starCount,
    val maxStatsCapAmount: Int = starCount,
    val powerScore: Double,
    val dropTableId: String,
    val lootProfileId: String = dropTableId,
    val baseXp: Int,
    val baseGold: Int,
    val onHitStatuses: List<CombatStatusApplyDef> = emptyList(),
    val combatState: CombatRuntimeState = CombatRuntimeState()
)
