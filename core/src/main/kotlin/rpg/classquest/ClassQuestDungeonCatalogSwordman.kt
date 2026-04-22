package rpg.classquest

internal object ClassQuestDungeonCatalogSwordman {
    fun definitionFor(
        canonicalPath: String,
        pathNameResolver: (ClassQuestUnlockType, String) -> String
    ): ClassQuestDungeonDefinition? {
        return when (canonicalPath) {
            "warrior" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "swordman",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SUBCLASS, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_warrior_war_brute", "Bruto de Guerra", "skeleton_warrior", "war_brute", "humanoid", "war_scraps", setOf("war")),
                    dungeonMonster("cq_warrior_vanguard_raider", "Saqueador da Vanguarda", "skeleton_archer", "vanguard_raider", "humanoid", "war_scraps", setOf("vanguard")),
                    dungeonMonster("cq_warrior_wild_gladiator", "Gladiador Selvagem", "mimic", "wild_gladiator", "humanoid", "war_scraps", setOf("arena")),
                    dungeonMonster("cq_warrior_shield_breaker", "Quebra-Escudos", "skeleton_warrior", "shield_breaker", "humanoid", "war_scraps", setOf("crusher")),
                    dungeonMonster("cq_warrior_armored_bull", "Touro Blindado", "slime", "armored_bull", "beast", "wolf_beast", setOf("beast"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_warrior_arena_champion", "Campeao da Arena", "mimic", "arena_champion", "humanoid", "war_scraps", setOf("boss")),
                    dungeonMonster("cq_warrior_field_commander", "Comandante de Campo", "lich", "field_commander", "humanoid", "war_scraps", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_warrior_final_war_tyrant", "Tirano de Guerra", "lich", "war_tyrant", "humanoid", "war_scraps", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_warrior",
                collectibleName = "Insignia de Vanguarda"
            )

            "paladin" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "swordman",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_paladin_profaned_skeleton", "Esqueleto Profanado", "skeleton_warrior", "skeleton", "undead", "undead_relics", setOf("undead")),
                    dungeonMonster("cq_paladin_fallen_knight", "Cavaleiro Caido", "skeleton_warrior", "fallen_knight", "undead", "undead_relics", setOf("corrupted")),
                    dungeonMonster("cq_paladin_corrupted_acolyte", "Acolito Corrompido", "skeleton_archer", "corrupted_acolyte", "undead", "undead_relics", setOf("corrupted")),
                    dungeonMonster("cq_paladin_shadow_herald", "Arauto Sombrio", "slime", "shadow_herald", "undead", "undead_relics", setOf("shadow")),
                    dungeonMonster("cq_paladin_heretic_guardian", "Guardiao Herege", "mimic", "heretic_guardian", "undead", "undead_relics", setOf("undead"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_paladin_dread_inquisitor", "Inquisidor Maldito", "lich", "dread_inquisitor", "undead", "undead_ancient", setOf("boss")),
                    dungeonMonster("cq_paladin_ruin_bearer", "Portador da Ruina", "mimic", "ruin_bearer", "undead", "undead_relics", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_paladin_final_fallen_seraph", "Arcanjo Caido", "lich", "fallen_seraph", "undead", "undead_ancient", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_paladin",
                collectibleName = "Sigilo Luminar"
            )

            "elite_guard" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "swordman",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_elite_guard_bastion_knight", "Cavaleiro do Bastiao", "skeleton_warrior", "bastion_knight", "humanoid", "war_scraps", setOf("tank")),
                    dungeonMonster("cq_elite_guard_steel_lancer", "Lanceiro de Aco", "skeleton_archer", "steel_lancer", "humanoid", "war_scraps", setOf("formation")),
                    dungeonMonster("cq_elite_guard_shield_colossus", "Colosso do Escudo", "mimic", "shield_colossus", "construct", "war_scraps", setOf("defender")),
                    dungeonMonster("cq_elite_guard_sanction_keeper", "Guardiao da Sancao", "lich", "sanction_keeper", "humanoid", "war_scraps", setOf("discipline")),
                    dungeonMonster("cq_elite_guard_wall_hound", "Cao da Muralha", "slime", "wall_hound", "beast", "wolf_beast", setOf("watch"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_elite_guard_bulwark_marshal", "Marechal do Baluarte", "mimic", "bulwark_marshal", "humanoid", "war_scraps", setOf("boss")),
                    dungeonMonster("cq_elite_guard_gate_judicator", "Justicar do Portao", "lich", "gate_judicator", "humanoid", "war_scraps", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_elite_guard_final_citadel_lord", "Senhor da Cidadela", "lich", "citadel_lord", "humanoid", "war_scraps", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_elite_guard",
                collectibleName = "Brasao de Elite"
            )

            "predator" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "swordman",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_predator_pack_stalker", "Perseguidor de Matilha", "skeleton_warrior", "pack_stalker", "beast", "wolf_beast", setOf("predator")),
                    dungeonMonster("cq_predator_rend_panther", "Pantera Dilacerante", "skeleton_archer", "rend_panther", "beast", "wolf_beast", setOf("ambush")),
                    dungeonMonster("cq_predator_bone_howler", "Uivador Oseo", "mimic", "bone_howler", "beast", "wolf_beast", setOf("feral")),
                    dungeonMonster("cq_predator_savage_tracker", "Rastreador Selvagem", "lich", "savage_tracker", "humanoid", "bandit_shadow", setOf("tracker")),
                    dungeonMonster("cq_predator_thorn_maw", "Mandibula de Espinhos", "slime", "thorn_maw", "beast", "wolf_beast", setOf("beast"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_predator_alpha_huntmaster", "Mestre Alfa", "mimic", "alpha_huntmaster", "beast", "wolf_beast", setOf("boss")),
                    dungeonMonster("cq_predator_feral_crown", "Coroa Feral", "lich", "feral_crown", "beast", "wolf_beast", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_predator_final_apex_devourer", "Devorador Apex", "lich", "apex_devourer", "beast", "wolf_beast", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_predator",
                collectibleName = "Garra de Alfa"
            )

            "berserker" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "swordman",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_berserker_blood_raider", "Saqueador Sangrento", "skeleton_warrior", "blood_raider", "humanoid", "war_scraps", setOf("rage")),
                    dungeonMonster("cq_berserker_chain_howler", "Uivador Acorrentado", "skeleton_archer", "chain_howler", "humanoid", "war_scraps", setOf("frenzy")),
                    dungeonMonster("cq_berserker_gore_brute", "Brutamontes da Carnificina", "mimic", "gore_brute", "humanoid", "war_scraps", setOf("arena")),
                    dungeonMonster("cq_berserker_rage_totem", "Totem de Furia", "slime", "rage_totem", "construct", "war_scraps", setOf("totem")),
                    dungeonMonster("cq_berserker_blood_mastiff", "Mastim de Sangue", "lich", "blood_mastiff", "beast", "wolf_beast", setOf("beast"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_berserker_massacre_herald", "Arauto da Carnificina", "mimic", "massacre_herald", "humanoid", "war_scraps", setOf("boss")),
                    dungeonMonster("cq_berserker_red_arena_lord", "Senhor da Arena Rubra", "lich", "red_arena_lord", "humanoid", "war_scraps", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_berserker_final_bloodstorm", "Tempestade de Sangue", "lich", "bloodstorm", "humanoid", "war_scraps", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_berserker",
                collectibleName = "Totem de Carnificina"
            )

            "barbarian" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "swordman",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SUBCLASS, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_barbarian_tribal_butcher", "Carniceiro Tribal", "skeleton_warrior", "tribal_butcher", "humanoid", "war_scraps", setOf("aggressive")),
                    dungeonMonster("cq_barbarian_devastator", "Devastador", "skeleton_archer", "devastator", "humanoid", "war_scraps", setOf("aggressive")),
                    dungeonMonster("cq_barbarian_frenzied_warrior", "Guerreiro Frenetico", "mimic", "frenzied_warrior", "humanoid", "war_scraps", setOf("frenzy")),
                    dungeonMonster("cq_barbarian_war_mastiff", "Mastim de Guerra", "slime", "war_mastiff", "beast", "wolf_beast", setOf("beast")),
                    dungeonMonster("cq_barbarian_wild_behemoth", "Beemote Selvagem", "lich", "wild_behemoth", "beast", "wolf_beast", setOf("beast"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_barbarian_slaughter_lord", "Senhor da Matanca", "mimic", "slaughter_lord", "humanoid", "war_scraps", setOf("boss")),
                    dungeonMonster("cq_barbarian_ancient_rage", "Furia Ancestral", "lich", "ancient_rage", "humanoid", "war_scraps", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_barbarian_final_frenzy_king", "Rei do Frenesi", "lich", "frenzy_king", "humanoid", "war_scraps", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_barbarian",
                collectibleName = "Reliquia de Frenesi"
            )

            else -> null
        }
    }
}
