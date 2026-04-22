package rpg.classquest

internal object ClassQuestDungeonCatalogArcher {
    fun definitionFor(
        canonicalPath: String,
        pathNameResolver: (ClassQuestUnlockType, String) -> String
    ): ClassQuestDungeonDefinition? {
        return when (canonicalPath) {
            "hunter" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "archer",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SUBCLASS, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_hunter_wolf", "Lobo", "skeleton_warrior", "wolf", "beast", "wolf_beast", setOf("predator")),
                    dungeonMonster("cq_hunter_gray_wolf", "Lobo Cinzento", "skeleton_archer", "wolf", "beast", "wolf_beast", setOf("predator")),
                    dungeonMonster("cq_hunter_wild_panther", "Pantera Selvagem", "slime", "panther", "beast", "wolf_beast", setOf("predator")),
                    dungeonMonster("cq_hunter_young_bear", "Urso Jovem", "mimic", "bear", "beast", "wolf_beast", setOf("beast")),
                    dungeonMonster("cq_hunter_predator_hawk", "Falcao Predador", "skeleton_archer", "hawk", "beast", "wolf_beast", setOf("predator"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_hunter_pack_tyrant", "Tirano da Matilha", "mimic", "pack_tyrant", "beast", "wolf_beast", setOf("boss")),
                    dungeonMonster("cq_hunter_primal_alpha", "Alfa Primal", "lich", "primal_alpha", "beast", "wolf_beast", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_hunter_final_fang_lord", "Senhor das Presas", "lich", "fang_lord", "beast", "wolf_beast", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_hunter",
                collectibleName = "Presa Marcada"
            )

            "assassin" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "archer",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_assassin_stealth_bandit", "Bandido Furtivo", "skeleton_archer", "bandit", "humanoid", "bandit_shadow", setOf("shadow")),
                    dungeonMonster("cq_assassin_shadow_blade", "Lamina Sombria", "skeleton_warrior", "shadow_blade", "humanoid", "bandit_shadow", setOf("assassin")),
                    dungeonMonster("cq_assassin_mist_stalker", "Espreitador da Nevoa", "slime", "mist_stalker", "humanoid", "bandit_shadow", setOf("shadow")),
                    dungeonMonster("cq_assassin_night_hunter", "Cacador Noturno", "mimic", "night_hunter", "humanoid", "bandit_shadow", setOf("assassin")),
                    dungeonMonster("cq_assassin_silent_mercenary", "Mercenario Silencioso", "skeleton_warrior", "mercenary", "humanoid", "bandit_shadow", setOf("mercenary"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_assassin_twilight_reaper", "Ceifador do Crepusculo", "lich", "twilight_reaper", "humanoid", "bandit_shadow", setOf("boss")),
                    dungeonMonster("cq_assassin_blade_master", "Mestre das Laminas", "mimic", "blade_master", "humanoid", "bandit_shadow", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_assassin_final_shadow_king", "Rei da Nevoa", "lich", "shadow_king", "humanoid", "bandit_shadow", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_assassin",
                collectibleName = "Selo da Sombra"
            )

            "bounty_hunter" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "archer",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_bounty_hunter_deserter", "Desertor Procurado", "skeleton_warrior", "deserter", "humanoid", "bandit_shadow", setOf("contract")),
                    dungeonMonster("cq_bounty_hunter_outlaw_archer", "Foragido Armado", "skeleton_archer", "outlaw_archer", "humanoid", "bandit_shadow", setOf("ranged")),
                    dungeonMonster("cq_bounty_hunter_smuggler", "Contrabandista", "mimic", "smuggler", "humanoid", "bandit_shadow", setOf("smuggler")),
                    dungeonMonster("cq_bounty_hunter_tracker_hound", "Cao Rastreador", "slime", "tracker_hound", "beast", "wolf_beast", setOf("hound")),
                    dungeonMonster("cq_bounty_hunter_chain_duelist", "Duelista Acorrentado", "lich", "chain_duelist", "humanoid", "bandit_shadow", setOf("duelist"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_bounty_hunter_black_warrant", "Mandado Negro", "mimic", "black_warrant", "humanoid", "bandit_shadow", setOf("boss")),
                    dungeonMonster("cq_bounty_hunter_last_target", "Ultimo Alvo", "lich", "last_target", "humanoid", "bandit_shadow", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_bounty_hunter_final_contract_king", "Rei dos Contratos", "lich", "contract_king", "humanoid", "bandit_shadow", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_bounty_hunter",
                collectibleName = "Contrato Lacrado"
            )

            "sharpshooter" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "archer",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_sharpshooter_armored_spotter", "Olheiro Blindado", "skeleton_archer", "armored_spotter", "humanoid", "war_scraps", setOf("ranged")),
                    dungeonMonster("cq_sharpshooter_veteran_archer", "Arqueiro Veterano", "skeleton_archer", "veteran_archer", "humanoid", "war_scraps", setOf("ranged")),
                    dungeonMonster("cq_sharpshooter_cliff_watcher", "Vigia do Penhasco", "skeleton_warrior", "cliff_watcher", "humanoid", "war_scraps", setOf("sniper")),
                    dungeonMonster("cq_sharpshooter_iron_sentinel", "Sentinela de Ferro", "mimic", "iron_sentinel", "construct", "war_scraps", setOf("siege")),
                    dungeonMonster("cq_sharpshooter_siege_gunner", "Atirador de Cerco", "lich", "siege_gunner", "humanoid", "war_scraps", setOf("siege"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_sharpshooter_siege_commander", "Comandante de Cerco", "mimic", "siege_commander", "humanoid", "war_scraps", setOf("boss")),
                    dungeonMonster("cq_sharpshooter_absolute_sight", "Mira Absoluta", "lich", "absolute_sight", "humanoid", "war_scraps", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_sharpshooter_final_line_judge", "Juiz da Linha de Tiro", "lich", "line_judge", "humanoid", "war_scraps", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_sharpshooter",
                collectibleName = "Nucleo de Precisao"
            )

            "shadow_hunter" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "archer",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_shadow_hunter_gloom_stalker", "Espreitador Umbral", "skeleton_warrior", "gloom_stalker", "humanoid", "bandit_shadow", setOf("shadow")),
                    dungeonMonster("cq_shadow_hunter_dune_raptor", "Raptor da Penumbra", "skeleton_archer", "dune_raptor", "beast", "wolf_beast", setOf("predator")),
                    dungeonMonster("cq_shadow_hunter_nocturne_strider", "Corredor Noturno", "slime", "nocturne_strider", "beast", "wolf_beast", setOf("mobility")),
                    dungeonMonster("cq_shadow_hunter_umbra_falcon", "Falcao Umbral", "mimic", "umbra_falcon", "beast", "wolf_beast", setOf("wind")),
                    dungeonMonster("cq_shadow_hunter_exiled_pathfinder", "Explorador Exilado", "lich", "exiled_pathfinder", "humanoid", "bandit_shadow", setOf("ranger"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_shadow_hunter_night_route_master", "Mestre da Rota Sombria", "mimic", "night_route_master", "humanoid", "bandit_shadow", setOf("boss")),
                    dungeonMonster("cq_shadow_hunter_eclipse_nomad", "Nomade do Eclipse", "lich", "eclipse_nomad", "humanoid", "bandit_shadow", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_shadow_hunter_final_umbra_lord", "Senhor Umbral", "lich", "umbra_lord", "humanoid", "bandit_shadow", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_shadow_hunter",
                collectibleName = "Marca Umbral"
            )

            "ranger" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "archer",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SUBCLASS, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_ranger_longstride_scout", "Batedor Passo Longo", "skeleton_archer", "longstride_scout", "humanoid", "bandit_shadow", setOf("scout")),
                    dungeonMonster("cq_ranger_briar_stalker", "Perseguidor da Sarca", "skeleton_warrior", "briar_stalker", "humanoid", "bandit_shadow", setOf("tracker")),
                    dungeonMonster("cq_ranger_marsh_falcon", "Falcao do Brejo", "slime", "marsh_falcon", "beast", "wolf_beast", setOf("falcon")),
                    dungeonMonster("cq_ranger_rootbound_beast", "Besta Enraizada", "mimic", "rootbound_beast", "beast", "wolf_beast", setOf("wilderness")),
                    dungeonMonster("cq_ranger_trail_watcher", "Vigia da Trilha", "lich", "trail_watcher", "humanoid", "bandit_shadow", setOf("ranger"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_ranger_compass_warden", "Guardiao da Bussola", "mimic", "compass_warden", "humanoid", "bandit_shadow", setOf("boss")),
                    dungeonMonster("cq_ranger_dusk_tracker", "Rastreador do Crepusculo", "lich", "dusk_tracker", "humanoid", "bandit_shadow", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_ranger_final_trail_sovereign", "Soberano das Trilhas", "lich", "trail_sovereign", "humanoid", "bandit_shadow", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_ranger",
                collectibleName = "Insignia do Rastreador"
            )

            else -> null
        }
    }
}
