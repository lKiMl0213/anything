package rpg.classquest

internal object ClassQuestDungeonCatalogMage {
    fun definitionFor(
        canonicalPath: String,
        pathNameResolver: (ClassQuestUnlockType, String) -> String
    ): ClassQuestDungeonDefinition? {
        return when (canonicalPath) {
            "pyromancer" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "mage",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_pyromancer_elemental_fire", "Elemental de Fogo", "slime", "elemental", "elemental", "elemental_fire", setOf("fogo")),
                    dungeonMonster("cq_pyromancer_salamander", "Salamandra Ignea", "skeleton_warrior", "salamander", "elemental", "elemental_fire", setOf("fogo")),
                    dungeonMonster("cq_pyromancer_magma_core", "Nucleo de Magma", "mimic", "magma_core", "elemental", "elemental_fire", setOf("magma")),
                    dungeonMonster("cq_pyromancer_ash_cultist", "Cultista das Cinzas", "skeleton_archer", "ash_cultist", "humanoid", "elemental_fire", setOf("cultist")),
                    dungeonMonster("cq_pyromancer_volcanic_guardian", "Guardiao Vulcanico", "skeleton_warrior", "volcanic_guardian", "construct", "elemental_fire", setOf("vulcanic"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_pyromancer_igneous_priest", "Sacerdote Igneo", "lich", "igneous_priest", "humanoid", "elemental_fire", setOf("boss")),
                    dungeonMonster("cq_pyromancer_volcanic_colossus", "Colosso Vulcanico", "mimic", "volcanic_colossus", "construct", "elemental_fire", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_pyromancer_final_furnace_avatar", "Avatar da Fornalha", "lich", "furnace_avatar", "elemental", "elemental_fire", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_pyromancer",
                collectibleName = "Nucleo de Brasa"
            )

            "arcanist" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "mage",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SUBCLASS, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_arcanist_arcane_sentinel", "Sentinela Arcana", "skeleton_warrior", "arcane_sentinel", "construct", "construct_arcane", setOf("arcane")),
                    dungeonMonster("cq_arcanist_unstable_orb", "Orbe Instavel", "slime", "unstable_orb", "construct", "construct_arcane", setOf("arcane")),
                    dungeonMonster("cq_arcanist_temporal_echo", "Eco Temporal", "skeleton_archer", "temporal_echo", "construct", "construct_arcane", setOf("temporal")),
                    dungeonMonster("cq_arcanist_ether_watcher", "Vigia do Eter", "skeleton_archer", "ether_watcher", "construct", "construct_arcane", setOf("ether")),
                    dungeonMonster("cq_arcanist_arcane_construct", "Construto Arcano", "mimic", "arcane_construct", "construct", "construct_arcane", setOf("construct"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_arcanist_void_magister", "Magistrado do Vazio", "lich", "void_magister", "construct", "construct_arcane", setOf("boss")),
                    dungeonMonster("cq_arcanist_runic_executioner", "Executor Runico", "mimic", "runic_executioner", "construct", "construct_arcane", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_arcanist_final_time_judge", "Juiz das Cronorunas", "lich", "time_judge", "construct", "construct_arcane", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_arcanist",
                collectibleName = "Fragmento Temporal"
            )

            "elementalist" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "mage",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SUBCLASS, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_elementalist_ember_adept", "Adepto das Brasas", "skeleton_archer", "ember_adept", "humanoid", "elemental_fire", setOf("fire")),
                    dungeonMonster("cq_elementalist_storm_wisp", "Faisca da Tempestade", "slime", "storm_wisp", "elemental", "elemental_fire", setOf("storm")),
                    dungeonMonster("cq_elementalist_granite_sentinel", "Sentinela Granitica", "skeleton_warrior", "granite_sentinel", "construct", "elemental_fire", setOf("stone")),
                    dungeonMonster("cq_elementalist_tide_spirit", "Espirito da Mare", "slime", "tide_spirit", "elemental", "elemental_fire", setOf("water")),
                    dungeonMonster("cq_elementalist_salamander_warden", "Guardia Salamandra", "mimic", "salamander_warden", "elemental", "elemental_fire", setOf("fire"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_elementalist_primal_conduit", "Condutor Primordial", "lich", "primal_conduit", "elemental", "elemental_fire", setOf("boss")),
                    dungeonMonster("cq_elementalist_storm_forge", "Forja Tempestuosa", "mimic", "storm_forge", "construct", "elemental_fire", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_elementalist_final_equilibrium_core", "Nucleo do Equilibrio", "lich", "equilibrium_core", "elemental", "elemental_fire", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_elementalist",
                collectibleName = "Essencia Primordial"
            )

            "archmage" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "mage",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_archmage_arcane_devourer", "Devorador Arcano", "skeleton_warrior", "arcane_devourer", "construct", "construct_arcane", setOf("arcane")),
                    dungeonMonster("cq_archmage_spectral_magistrate", "Magistrado Espectral", "lich", "spectral_magistrate", "construct", "construct_arcane", setOf("spectral")),
                    dungeonMonster("cq_archmage_celestial_watcher", "Observador Celeste", "skeleton_archer", "celestial_watcher", "construct", "construct_arcane", setOf("celestial")),
                    dungeonMonster("cq_archmage_living_mana_fragment", "Fragmento de Mana Viva", "slime", "living_mana_fragment", "construct", "construct_arcane", setOf("mana")),
                    dungeonMonster("cq_archmage_veil_lord", "Senhor do Veu", "mimic", "veil_lord", "construct", "construct_arcane", setOf("void"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_archmage_void_architect", "Arquiteto do Vazio", "lich", "void_architect", "construct", "construct_arcane", setOf("boss")),
                    dungeonMonster("cq_archmage_star_regent", "Regente Estelar", "mimic", "star_regent", "construct", "construct_arcane", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_archmage_final_astral_noble", "Nobre Astral", "lich", "astral_noble", "construct", "construct_arcane", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_archmage",
                collectibleName = "Foco Arcano"
            )

            "cleric" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "mage",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_cleric_fallen_choir", "Coro Caido", "skeleton_archer", "fallen_choir", "undead", "undead_relics", setOf("holy")),
                    dungeonMonster("cq_cleric_blight_priest", "Sacerdote Maculado", "lich", "blight_priest", "undead", "undead_ancient", setOf("curse")),
                    dungeonMonster("cq_cleric_relic_guard", "Guardiao do Relicario", "skeleton_warrior", "relic_guard", "undead", "undead_relics", setOf("relic")),
                    dungeonMonster("cq_cleric_sanctified_flame", "Chama Consagrada", "slime", "sanctified_flame", "elemental", "elemental_fire", setOf("light")),
                    dungeonMonster("cq_cleric_grave_herald", "Arauto do Sepulcro", "mimic", "grave_herald", "undead", "undead_ancient", setOf("grave"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_cleric_requiem_bishop", "Bispo do Requiem", "lich", "requiem_bishop", "undead", "undead_ancient", setOf("boss")),
                    dungeonMonster("cq_cleric_censer_knight", "Cavaleiro do Turibulo", "mimic", "censer_knight", "undead", "undead_relics", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_cleric_final_cathedral_remnant", "Remanescente da Catedral", "lich", "cathedral_remnant", "undead", "undead_ancient", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_cleric",
                collectibleName = "Sigilo Consagrado"
            )

            "elemental_master" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "mage",
                pathId = canonicalPath,
                pathName = pathNameResolver(ClassQuestUnlockType.SPECIALIZATION, canonicalPath),
                normalMonsters = listOf(
                    dungeonMonster("cq_elemental_hybrid_elemental", "Elemental Hibrido", "skeleton_warrior", "elemental_hybrid", "elemental", "elemental_fire", setOf("elemental")),
                    dungeonMonster("cq_elemental_storm_spirit", "Espirito da Tempestade", "skeleton_archer", "storm_spirit", "elemental", "elemental_fire", setOf("storm")),
                    dungeonMonster("cq_elemental_glacial_guardian", "Guardiao Glacial", "mimic", "glacial_guardian", "elemental", "elemental_fire", setOf("ice")),
                    dungeonMonster("cq_elemental_lava_heart", "Coracao de Lava", "slime", "lava_heart", "elemental", "elemental_fire", setOf("lava")),
                    dungeonMonster("cq_elemental_elemental_avatar", "Avatar Elemental", "lich", "elemental_avatar", "elemental", "elemental_fire", setOf("elemental"))
                ),
                bossMonsters = listOf(
                    dungeonMonster("cq_elemental_prismatic_core", "Nucleo Prismatico", "mimic", "prismatic_core", "elemental", "elemental_fire", setOf("boss")),
                    dungeonMonster("cq_elemental_current_master", "Mestre das Correntes", "lich", "current_master", "elemental", "elemental_fire", setOf("boss"))
                ),
                finalBoss = dungeonMonster("cq_elemental_final_prismatic_lord", "Soberano Prismatico", "lich", "prismatic_lord", "elemental", "elemental_fire", setOf("boss_final")),
                collectibleTemplateId = "cq_collect_elemental_master",
                collectibleName = "Essencia Prismatica"
            )

            else -> null
        }
    }
}
