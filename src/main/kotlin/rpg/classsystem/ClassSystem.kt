package rpg.classsystem

import rpg.classquest.ClassQuestUnlockType
import rpg.io.DataRepository
import rpg.model.Bonuses
import rpg.model.ClassDef
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.RaceDef
import rpg.model.SpecializationDef
import rpg.model.SubclassDef
import rpg.talent.TalentTreeService

class ClassSystem(private val repo: DataRepository) {
    private val talentTreeService = TalentTreeService(repo.balance.talentPoints)

    private val legacyBaseClassIdMap = mapOf(
        "warrior" to "swordman"
    )
    private val legacyPathIdMap = mapOf(
        "fighter" to "warrior",
        "imperial_guard" to "elite_guard",
        "wanderer" to "shadow_hunter",
        "sorcerer" to "elementalist"
    )
    private val legacyQuestTemplateIdMap = mapOf(
        "unlock_warrior_subclass_lv25" to "unlock_swordman_subclass_lv25",
        "unlock_warrior_specialization_lv50" to "unlock_swordman_specialization_lv50"
    )
    private val legacyTalentTreeIdMap = mapOf(
        "archer_base_v2" to "Archer_base",
        "archer_hunter_v2" to "A_hunter",
        "archer_bounty_hunter_v2" to "A_hunter_bounty_hunter",
        "archer_assassin_v2" to "A_hunter_assassin",
        "archer_ranger_v2" to "A_ranger",
        "archer_wanderer_v2" to "A_ranger",
        "archer_sharpshooter_v2" to "A_ranger_sharpshooter",
        "archer_shadow_hunter_v2" to "A_ranger_shadow_hunter",
        "mage_base_v2" to "Mage_base",
        "mage_arcanist_v2" to "M_arcanist",
        "mage_archmage_v2" to "M_arcanist_archmage",
        "mage_cleric_v2" to "M_arcanist_cleric",
        "mage_elementalist_v2" to "M_elementalist",
        "mage_pyromancer_v2" to "M_elementalist_pyromancer",
        "mage_elemental_master_v2" to "M_elementalist_elemental_master",
        "warrior_base_v2" to "Swordman_base",
        "warrior_fighter_v2" to "S_warrior",
        "warrior_barbarian_v2" to "S_barbarian",
        "warrior_paladin_v2" to "S_warrior_paladin",
        "warrior_elite_guard_v2" to "S_warrior_elite_guard",
        "warrior_imperial_guard_v2" to "S_warrior_elite_guard",
        "warrior_predator_v2" to "S_barbarian_predator",
        "warrior_berserker_v2" to "S_barbarian_berserker"
    )
    private val legacyTalentNodeIdMap = mapOf(
        "hunter_marksmanship" to "A_hunter_predator_eye",
        "hunter_mark_prey" to "A_hunter_precise_barrage",
        "hunter_venom_tip" to "A_hunter_deadly_trap",
        "hunter_mark_exploit" to "A_hunter_marked_arrow",
        "hunter_steady_cycle" to "A_hunter_relentless_hunt",
        "A_hunter_crit_path" to "A_hunter_marked_arrow",
        "A_hunter_sustain_path" to "A_hunter_extra_quiver",
        "bounty_hunter_contract_sight" to "A_hunter_bounty_hunter_reward_scent",
        "bounty_hunter_warrant_shot" to "A_hunter_bounty_hunter_paralyzing_shot",
        "bounty_hunter_execution_round" to "A_hunter_bounty_hunter_precise_execution",
        "bounty_hunter_claim_bonus" to "A_hunter_bounty_hunter_reward_scent",
        "bounty_hunter_chain_contract" to "A_hunter_bounty_hunter_hunting_thirst",
        "A_hunter_bounty_hunter_execution_path" to "A_hunter_bounty_hunter_flawless_tracking",
        "A_hunter_bounty_hunter_tactical_path" to "A_hunter_bounty_hunter_control_specialist",
        "assassin_shadow_strike" to "A_hunter_assassin_lightning_ambush",
        "assassin_toxic_execution" to "A_hunter_assassin_finishing_blow",
        "assassin_kill_window" to "A_hunter_assassin_deadly_precision",
        "assassin_evasive_instinct" to "A_hunter_assassin_shadow_camouflage",
        "assassin_reposition" to "A_hunter_assassin_shadow_agility",
        "A_hunter_assassin_crit_path" to "A_hunter_assassin_vital_target",
        "A_hunter_assassin_sustain_path" to "A_hunter_assassin_silent_poison",
        "A_ranger_flowstep" to "A_ranger_windstep",
        "A_ranger_skirmish_shot" to "A_ranger_explosive_shot",
        "A_ranger_survival_route" to "A_ranger_nature_mastery",
        "A_ranger_moving_hunt" to "A_ranger_ricochet_arrow",
        "A_ranger_field_medicine" to "A_ranger_wild_vigor",
        "A_ranger_crit_path" to "A_ranger_longbow_mastery",
        "A_ranger_sustain_path" to "A_ranger_nature_mastery",
        "A_ranger_single_target_path" to "A_ranger_beast_companion",
        "A_ranger_aoe_path" to "A_ranger_explosive_shot",
        "sharpshooter_eagle_eye" to "A_ranger_sharpshooter_eagle_eye",
        "sharpshooter_charged_piercer" to "A_ranger_sharpshooter_deadly_shot",
        "sharpshooter_target_lock" to "A_ranger_sharpshooter_perfect_calibration",
        "sharpshooter_killing_line" to "A_ranger_sharpshooter_piercing_vision",
        "sharpshooter_precision_break" to "A_ranger_sharpshooter_focused_shot",
        "A_ranger_sharpshooter_crit_path" to "A_ranger_sharpshooter_controlled_breath",
        "A_ranger_sharpshooter_sustain_path" to "A_ranger_sharpshooter_focused_shot",
        "A_ranger_sharpshooter_single_target_path" to "A_ranger_sharpshooter_deadly_shot",
        "A_ranger_sharpshooter_aoe_path" to "A_ranger_sharpshooter_long_barrage",
        "shadow_hunter_umbral_step" to "A_ranger_shadow_hunter_fleeting_shadow",
        "shadow_hunter_gloom_arrow" to "A_ranger_shadow_hunter_shadow_shot",
        "shadow_hunter_midnight_pursuit" to "A_ranger_shadow_hunter_trail_of_the_dead",
        "shadow_hunter_ambush_instinct" to "A_ranger_shadow_hunter_ghost_blade",
        "shadow_hunter_smoke_cycle" to "A_ranger_shadow_hunter_silent_path",
        "A_ranger_shadow_hunter_predator_path" to "A_ranger_shadow_hunter_trail_of_the_dead",
        "A_ranger_shadow_hunter_control_path" to "A_ranger_shadow_hunter_poison_expertise",
        "arcanist_flux_core" to "M_arcanist_mystic_channeling",
        "arcanist_chain_bolt" to "M_arcanist_arcane_blast",
        "arcanist_time_shear" to "M_arcanist_temporal_distortion",
        "arcanist_mana_echo" to "M_arcanist_arcane_study",
        "arcanist_phase_ricochet" to "M_arcanist_temporal_flow",
        "M_arcanist_burst_path" to "M_arcanist_runic_script",
        "M_arcanist_control_path" to "M_arcanist_magic_barrier",
        "archmage_supreme_focus" to "M_arcanist_archmage_brilliant_mind",
        "archmage_starfall" to "M_arcanist_archmage_arcane_meteor",
        "archmage_arcane_overdrive" to "M_arcanist_archmage_power_aura",
        "archmage_critical_formula" to "M_arcanist_archmage_supreme_magic",
        "archmage_void_lance" to "M_arcanist_archmage_mystic_catalyst",
        "M_arcanist_archmage_burst_path" to "M_arcanist_archmage_supreme_magic",
        "M_arcanist_archmage_control_path" to "M_arcanist_archmage_brilliant_mind",
        "M_arcanist_archmage_single_target_path" to "M_arcanist_archmage_arcane_amplification",
        "M_arcanist_archmage_aoe_path" to "M_arcanist_archmage_arcane_meteor",
        "cleric_sanctified_core" to "M_arcanist_cleric_healing_blessing",
        "cleric_holy_smite" to "M_arcanist_cleric_purifying_light",
        "cleric_sanctuary_pulse" to "M_arcanist_cleric_healing_prayer",
        "cleric_aegis_prayer" to "M_arcanist_cleric_divine_protection",
        "cleric_grace_cycle" to "M_arcanist_cleric_inspiring_presence",
        "M_arcanist_cleric_wrath_path" to "M_arcanist_cleric_force_of_faith",
        "M_arcanist_cleric_guard_path" to "M_arcanist_cleric_spirit_of_sacrifice",
        "elementalist_primal_study" to "M_elementalist_element_control",
        "elementalist_fire_lance" to "M_elementalist_elemental_storm",
        "elementalist_storm_orb" to "M_elementalist_elemental_golem",
        "elementalist_resonance" to "M_elementalist_elemental_attunement",
        "elementalist_flux_cycle" to "M_elementalist_water_embrace",
        "M_elementalist_burst_path" to "M_elementalist_fire_fury",
        "M_elementalist_control_path" to "M_elementalist_earth_resilience",
        "pyro_flame_attunement" to "M_elementalist_pyromancer_inner_flame",
        "pyro_fireball" to "M_elementalist_pyromancer_infernal_blast",
        "pyro_inferno_wave" to "M_elementalist_pyromancer_meteor_shower",
        "pyro_combustion" to "M_elementalist_pyromancer_lasting_inferno",
        "pyro_quick_incantation" to "M_elementalist_pyromancer_rapid_ignition",
        "M_elementalist_pyromancer_burst_path" to "M_elementalist_pyromancer_heat_wave",
        "M_elementalist_pyromancer_control_path" to "M_elementalist_pyromancer_thermal_convulsion",
        "elemental_master_harmonic_matrix" to "M_elementalist_elemental_master_elemental_harmony",
        "elemental_master_prism_burst" to "M_elementalist_elemental_master_elemental_fusion",
        "elemental_master_frost_rupture" to "M_elementalist_elemental_master_elemental_circle",
        "elemental_master_toxic_tempest" to "M_elementalist_elemental_master_elemental_fusion",
        "elemental_master_hybrid_control" to "M_elementalist_elemental_master_element_transfer",
        "M_elementalist_elemental_master_burst_path" to "M_elementalist_elemental_master_mana_convergence",
        "M_elementalist_elemental_master_control_path" to "M_elementalist_elemental_master_stone_skin",
        "M_elementalist_elemental_master_single_target_path" to "M_elementalist_elemental_master_electric_current",
        "M_elementalist_elemental_master_aoe_path" to "M_elementalist_elemental_master_elemental_circle",
        "S_warrior_bloodrush" to "S_warrior_blade_mastery",
        "S_warrior_crushing_strike" to "S_warrior_spinning_blade",
        "S_warrior_war_charge" to "S_warrior_shield_bash",
        "S_warrior_relentless_assault" to "S_warrior_tactical_offense",
        "S_warrior_overrun" to "S_warrior_defensive_stance",
        "S_warrior_offense_path" to "S_warrior_controlled_fury",
        "S_warrior_control_path" to "S_warrior_guard_technique",
        "paladin_aegis_oath" to "S_warrior_paladin_oath_of_loyalty",
        "paladin_shield_bash" to "S_warrior_paladin_holy_strike",
        "paladin_holy_mend" to "S_warrior_paladin_divine_guard",
        "paladin_sanctuary_aura" to "S_warrior_paladin_light_aura",
        "paladin_reflective_guard" to "S_warrior_paladin_consecrated_shield",
        "S_warrior_paladin_offense_path" to "S_warrior_paladin_righteous_judgment",
        "S_warrior_paladin_control_path" to "S_warrior_paladin_protective_spirit",
        "S_warrior_elite_guard_unbreakable_wall" to "S_warrior_elite_guard_impenetrable_guard",
        "S_warrior_elite_guard_provoking_blow" to "S_warrior_elite_guard_shield_rush",
        "S_warrior_elite_guard_bastion_aura" to "S_warrior_elite_guard_line_strategy",
        "S_warrior_elite_guard_guardian_pulse" to "S_warrior_elite_guard_wall_formation",
        "S_warrior_elite_guard_phalanx" to "S_warrior_elite_guard_elite_training",
        "S_warrior_elite_guard_offense_path" to "S_warrior_elite_guard_counter_attack",
        "S_warrior_elite_guard_control_path" to "S_warrior_elite_guard_unwavering_firmness",
        "S_warrior_elite_guard_single_target_path" to "S_warrior_elite_guard_elite_training",
        "S_warrior_elite_guard_aoe_path" to "S_warrior_elite_guard_patrol_discipline",
        "barbarian_rage_core" to "S_barbarian_savage_fury",
        "barbarian_rending_howl" to "S_barbarian_war_roar",
        "barbarian_pain_fuel" to "S_barbarian_savage_fury",
        "barbarian_war_frenzy" to "S_barbarian_tireless_attack",
        "barbarian_blood_execution" to "S_barbarian_berserker_fury",
        "S_barbarian_offense_path" to "S_barbarian_ancestral_hunter",
        "S_barbarian_control_path" to "S_barbarian_indomitable_spirit",
        "S_barbarian_single_target_path" to "S_barbarian_berserker_fury",
        "S_barbarian_aoe_path" to "S_barbarian_devastating_leap",
        "predator_pack_instinct" to "S_barbarian_predator_relentless_hunt",
        "predator_pounce" to "S_barbarian_predator_stealth_strike",
        "predator_maul_combo" to "S_barbarian_predator_brutal_trap",
        "predator_bloodtrail" to "S_barbarian_predator_hunter_eye",
        "predator_hunt_cycle" to "S_barbarian_predator_blood_instinct",
        "S_barbarian_predator_feral_path" to "S_barbarian_predator_sharpened_hook",
        "S_barbarian_predator_stalker_path" to "S_barbarian_predator_silent_steps",
        "berserker_blood_frenzy" to "S_barbarian_berserker_uncontrolled_wrath",
        "berserker_rampage" to "S_barbarian_berserker_steel_storm",
        "berserker_war_howl" to "S_barbarian_berserker_war_cry",
        "berserker_pain_to_power" to "S_barbarian_berserker_pure_adrenaline",
        "berserker_frenzy_cycle" to "S_barbarian_berserker_frenzied_slaughter",
        "S_barbarian_berserker_massacre_path" to "S_barbarian_berserker_pure_adrenaline",
        "S_barbarian_berserker_endurance_path" to "S_barbarian_berserker_brutal_resilience"
    )
    private val legacyTalentNodeReplacements = listOf(
        "archer_bounty_hunter_v2_" to "A_hunter_bounty_hunter_",
        "archer_shadow_hunter_v2_" to "A_ranger_shadow_hunter_",
        "archer_sharpshooter_v2_" to "A_ranger_sharpshooter_",
        "archer_assassin_v2_" to "A_hunter_assassin_",
        "archer_hunter_v2_" to "A_hunter_",
        "archer_wanderer_v2_" to "A_ranger_",
        "archer_ranger_v2_" to "A_ranger_",
        "mage_elemental_master_v2_" to "M_elementalist_elemental_master_",
        "mage_pyromancer_v2_" to "M_elementalist_pyromancer_",
        "mage_elementalist_v2_" to "M_elementalist_",
        "mage_archmage_v2_" to "M_arcanist_archmage_",
        "mage_cleric_v2_" to "M_arcanist_cleric_",
        "mage_arcanist_v2_" to "M_arcanist_",
        "warrior_imperial_guard_v2_" to "S_warrior_elite_guard_",
        "warrior_elite_guard_v2_" to "S_warrior_elite_guard_",
        "warrior_fighter_v2_" to "S_warrior_",
        "warrior_paladin_v2_" to "S_warrior_paladin_",
        "warrior_barbarian_v2_" to "S_barbarian_",
        "warrior_predator_v2_" to "S_barbarian_predator_",
        "warrior_berserker_v2_" to "S_barbarian_berserker_",
        "archer_v2_" to "Archer_base_",
        "mage_v2_" to "Mage_base_",
        "warrior_v2_" to "Swordman_base_",
        "imperial_guard_" to "S_warrior_elite_guard_",
        "wanderer_" to "A_ranger_",
        "fighter_" to "S_warrior_"
    )
    private val legacyTalentNodeCostById = mapOf(
        "archer_root" to 1,
        "archer_crit" to 1,
        "archer_agility" to 1,
        "archer_burst" to 2,
        "mage_root" to 1,
        "mage_focus" to 1,
        "mage_cdr" to 1,
        "mage_burst" to 2,
        "warrior_root" to 1,
        "warrior_crit" to 1,
        "warrior_guard" to 1,
        "warrior_execute" to 2,
        "paladin_root" to 1,
        "paladin_aura" to 1,
        "cleric_root" to 1,
        "cleric_bless" to 1
    )

    fun classDef(id: String): ClassDef = repo.classes[migrateBaseClassId(id)] ?: error("Classe nao encontrada: $id")

    fun raceDef(id: String): RaceDef = repo.races[id] ?: error("Raca nao encontrada: $id")

    fun subclassDef(id: String?): SubclassDef? = id?.let { repo.subclasses[migratePathId(it)] }

    fun specializationDef(id: String?): SpecializationDef? = id?.let { repo.specializations[migratePathId(it)] }

    fun secondClassOptions(classDef: ClassDef): List<SubclassDef> {
        return classDef.secondClassIds
            .mapNotNull { repo.subclasses[it] }
            .filter { it.parentClassId.equals(classDef.id, ignoreCase = true) }
    }

    fun specializationOptions(classDef: ClassDef, subclassId: String?): List<SpecializationDef> {
        val subclass = subclassDef(subclassId) ?: return emptyList()
        if (!subclass.parentClassId.equals(classDef.id, ignoreCase = true)) return emptyList()
        return subclass.specializationIds
            .mapNotNull { repo.specializations[it] }
            .filter { specialization ->
                specialization.parentClassId.equals(classDef.id, ignoreCase = true) &&
                    specialization.parentSubclassId.equals(subclass.id, ignoreCase = true)
            }
    }

    fun migrateLegacyState(state: GameState): GameState {
        val migratedPlayer = migrateLegacyIds(state.player)
        val migratedInstances = state.itemInstances.mapValues { (_, item) -> migrateLegacyItemInstance(item) }
        return if (migratedPlayer == state.player && migratedInstances == state.itemInstances) {
            state
        } else {
            state.copy(player = migratedPlayer, itemInstances = migratedInstances)
        }
    }

    fun migrateLegacyIds(player: PlayerState): PlayerState {
        val migratedTalentRanks = linkedMapOf<String, Int>()
        for ((nodeId, rank) in player.talentNodeRanks) {
            val migratedNodeId = migrateTalentNodeId(nodeId)
            val current = migratedTalentRanks[migratedNodeId] ?: 0
            migratedTalentRanks[migratedNodeId] = current.coerceAtLeast(rank)
        }

        val migratedSubclassUnlockProgress = linkedMapOf<String, rpg.model.SubclassUnlockProgress>()
        for ((classId, progress) in player.subclassUnlockProgressByClass) {
            migratedSubclassUnlockProgress[migrateBaseClassId(classId)] = progress.copy(
                questTemplateId = migrateQuestTemplateId(progress.questTemplateId)
            )
        }

        val migratedSpecializationUnlockProgress = linkedMapOf<String, rpg.model.SpecializationUnlockProgress>()
        for ((classId, progress) in player.specializationUnlockProgressByClass) {
            migratedSpecializationUnlockProgress[migrateBaseClassId(classId)] = progress.copy(
                questTemplateId = migrateQuestTemplateId(progress.questTemplateId)
            )
        }

        val migratedQuestProgress = linkedMapOf<String, rpg.classquest.ClassQuestProgress>()
        for ((key, progress) in player.classQuestProgressByKey) {
            val migratedClassId = migrateBaseClassId(progress.classId.ifBlank { key.substringBefore(':') })
            val migratedProgress = progress.copy(
                classId = migratedClassId,
                chosenPath = progress.chosenPath?.let(::migratePathId)
            )
            migratedQuestProgress[migrateProgressKey(key, migratedProgress.classId, migratedProgress.unlockType)] = migratedProgress
        }

        val legacyTalentRefund = refundLegacyTalentPoints(player.legacyTalentIds)
        val migratedPlayer = player.copy(
            classId = migrateBaseClassId(player.classId),
            subclassId = player.subclassId?.let(::migratePathId),
            specializationId = player.specializationId?.let(::migratePathId),
            talentNodeRanks = migratedTalentRanks,
            unlockedTalentTrees = player.unlockedTalentTrees.map(::migrateTalentTreeId).distinct(),
            legacyTalentIds = emptyList(),
            subclassUnlockProgressByClass = migratedSubclassUnlockProgress,
            specializationUnlockProgressByClass = migratedSpecializationUnlockProgress,
            classQuestProgressByKey = migratedQuestProgress,
            unspentSkillPoints = (player.unspentSkillPoints + legacyTalentRefund).coerceAtLeast(0)
        )
        return if (migratedPlayer == player) player else migratedPlayer
    }

    fun sanitizePlayerHierarchy(player: PlayerState): PlayerState {
        val classDef = repo.classes[player.classId] ?: return player.copy(
            subclassId = null,
            specializationId = null
        )
        val requestedSecondClassId = player.subclassId?.lowercase()
        val sanitizedSecondClassId = requestedSecondClassId?.takeIf { secondClassId ->
            secondClassOptions(classDef).any { it.id.equals(secondClassId, ignoreCase = true) }
        }
        val requestedSpecializationId = player.specializationId?.lowercase()
        val sanitizedSpecializationId = requestedSpecializationId?.takeIf { specializationId ->
            specializationOptions(classDef, sanitizedSecondClassId)
                .any { it.id.equals(specializationId, ignoreCase = true) }
        }
        if (
            sanitizedSecondClassId == requestedSecondClassId &&
            sanitizedSpecializationId == requestedSpecializationId
        ) {
            return player
        }
        return player.copy(
            subclassId = sanitizedSecondClassId,
            specializationId = sanitizedSpecializationId
        )
    }

    fun totalBonuses(player: PlayerState): Bonuses {
        val classDef = classDef(player.classId)
        val raceDef = raceDef(player.raceId)
        val subclass = subclassDef(player.subclassId)
        val specialization = specializationDef(player.specializationId)
        val v2TalentBonuses = talentTreeService.collectBonuses(
            player = player,
            trees = talentTreeService.activeTrees(player, repo.talentTreesV2.values)
        )
        val subclassBonuses = subclass?.bonuses ?: Bonuses()
        val specializationBonuses = specialization?.bonuses ?: Bonuses()
        return classDef.bonuses + raceDef.bonuses + subclassBonuses + specializationBonuses + v2TalentBonuses
    }

    private fun refundLegacyTalentPoints(legacyTalentIds: List<String>): Int {
        return legacyTalentIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .sumOf { legacyTalentNodeCostById[it] ?: 0 }
    }

    private fun migrateLegacyItemInstance(item: ItemInstance): ItemInstance {
        val migratedTags = item.tags.map(::migrateItemTag)
        return if (migratedTags == item.tags) item else item.copy(tags = migratedTags)
    }

    private fun migrateItemTag(tag: String): String {
        return tag
            .replace("{warrior}", "{swordman}", ignoreCase = true)
            .replace("classlocked:warrior", "classLocked:swordman", ignoreCase = true)
            .replace("class:warrior", "class:swordman", ignoreCase = true)
            .replace("classtag:warrior", "classTag:swordman", ignoreCase = true)
    }

    private fun migrateBaseClassId(value: String): String {
        val normalized = value.trim().lowercase()
        return legacyBaseClassIdMap[normalized] ?: normalized
    }

    private fun migratePathId(value: String): String {
        val normalized = value.trim().lowercase()
        return legacyPathIdMap[normalized] ?: normalized
    }

    private fun migrateQuestTemplateId(value: String): String {
        val normalized = value.trim()
        return legacyQuestTemplateIdMap[normalized.lowercase()] ?: normalized
    }

    private fun migrateTalentTreeId(value: String): String {
        val normalized = value.trim()
        return legacyTalentTreeIdMap[normalized.lowercase()] ?: normalized
    }

    private fun migrateTalentNodeId(value: String): String {
        var updated = value.trim()
        legacyTalentNodeIdMap[updated]?.let { return it }
        for ((oldValue, newValue) in legacyTalentTreeIdMap) {
            updated = updated.replace(oldValue, newValue)
        }
        for ((oldValue, newValue) in legacyTalentNodeReplacements) {
            updated = updated.replace(oldValue, newValue)
        }
        return updated
    }

    private fun migrateProgressKey(
        key: String,
        classId: String,
        unlockType: ClassQuestUnlockType
    ): String {
        val classPart = key.substringBefore(':', classId).ifBlank { classId }
        val unlockPart = key.substringAfter(':', unlockType.name.lowercase()).ifBlank { unlockType.name.lowercase() }
        return "${migrateBaseClassId(classPart)}:${unlockPart.lowercase()}"
    }
}
