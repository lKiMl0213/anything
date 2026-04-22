package rpg.classquest

import rpg.model.EquipSlot

internal object ClassQuestCatalogSupport {
    fun slotDisplayName(slot: EquipSlot): String = when (slot) {
        EquipSlot.HEAD -> "Elmo"
        EquipSlot.CHEST -> "Peitoral"
        EquipSlot.LEGS -> "Calcas"
        EquipSlot.BOOTS -> "Botas"
        EquipSlot.GLOVES -> "Luvas"
        EquipSlot.WEAPON_MAIN -> "Arma"
        EquipSlot.WEAPON_OFF -> "Secundaria"
        EquipSlot.ALJAVA -> "Aljava"
        EquipSlot.CAPE -> "Capa"
        EquipSlot.BACKPACK -> "Mochila"
        EquipSlot.ACCESSORY -> "Acessorio"
    }

    fun classQuestRewardPrefix(
        unlockType: ClassQuestUnlockType,
        classId: String,
        pathId: String
    ): String? {
        val normalizedClassId = classId.lowercase()
        val normalizedPathId = pathId.lowercase()
        return when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> when (normalizedClassId) {
                "archer" -> when (normalizedPathId) {
                    "hunter" -> "a_hunter"
                    "ranger" -> "a_ranger"
                    else -> null
                }
                "mage" -> when (normalizedPathId) {
                    "arcanist" -> "m_arcanist"
                    "elementalist" -> "m_elementalist"
                    else -> null
                }
                "swordman" -> when (normalizedPathId) {
                    "warrior" -> "s_warrior"
                    "barbarian" -> "s_barbarian"
                    else -> null
                }
                else -> null
            }

            ClassQuestUnlockType.SPECIALIZATION -> when (normalizedPathId) {
                "bounty_hunter" -> "a_hunter_bounty_hunter"
                "assassin" -> "a_hunter_assassin"
                "sharpshooter" -> "a_ranger_sharpshooter"
                "shadow_hunter" -> "a_ranger_shadow_hunter"
                "archmage" -> "m_arcanist_archmage"
                "cleric" -> "m_arcanist_cleric"
                "pyromancer" -> "m_elementalist_pyromancer"
                "elemental_master" -> "m_elementalist_elemental_master"
                "paladin" -> "s_warrior_paladin"
                "elite_guard" -> "s_warrior_elite_guard"
                "predator" -> "s_barbarian_predator"
                "berserker" -> "s_barbarian_berserker"
                else -> null
            }
        }
    }

    fun canonicalDungeonPathId(
        unlockType: ClassQuestUnlockType,
        classId: String,
        pathId: String
    ): String? {
        val baseClass = classId.lowercase()
        val path = pathId.lowercase()
        return when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> when (baseClass) {
                "archer" -> when (path) {
                    "hunter" -> "hunter"
                    "ranger" -> "ranger"
                    else -> null
                }

                "mage" -> when (path) {
                    "arcanist" -> "arcanist"
                    "elementalist" -> "elementalist"
                    else -> null
                }

                "swordman" -> when (path) {
                    "warrior" -> "warrior"
                    "barbarian" -> "barbarian"
                    else -> null
                }

                else -> null
            }

            ClassQuestUnlockType.SPECIALIZATION -> when (baseClass) {
                "archer" -> when (path) {
                    "bounty_hunter" -> "bounty_hunter"
                    "assassin" -> "assassin"
                    "sharpshooter" -> "sharpshooter"
                    "shadow_hunter" -> "shadow_hunter"
                    else -> null
                }

                "mage" -> when (path) {
                    "archmage" -> "archmage"
                    "cleric" -> "cleric"
                    "pyromancer" -> "pyromancer"
                    "elemental_master" -> "elemental_master"
                    else -> null
                }

                "swordman" -> when (path) {
                    "paladin" -> "paladin"
                    "elite_guard" -> "elite_guard"
                    "predator" -> "predator"
                    "berserker" -> "berserker"
                    else -> null
                }

                else -> null
            }
        }
    }

    fun pathWeaponName(
        unlockType: ClassQuestUnlockType,
        pathId: String,
        classId: String
    ): String {
        val path = pathId.lowercase()
        return when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> when (path) {
                "arcanist" -> "Tomo Arcano"
                "elementalist" -> "Catalisador Elemental"
                "hunter" -> "Arco de Caca"
                "ranger" -> "Arco do Rastreador"
                "warrior" -> "Lamina de Vanguarda"
                "barbarian" -> "Machado Tribal"
                else -> when (classId.lowercase()) {
                    "mage" -> "Tomo Arcano"
                    "archer" -> "Arco de Caca"
                    else -> "Lamina de Vanguarda"
                }
            }
            ClassQuestUnlockType.SPECIALIZATION -> when (path) {
                "archmage" -> "Orbe Arcana"
                "cleric" -> "Cetro Consagrado"
                "pyromancer" -> "Catalisador Igneo"
                "elemental_master" -> "Nucleo Elemental"
                "bounty_hunter" -> "Balestra de Contrato"
                "assassin" -> "Lamina de Sombra"
                "sharpshooter" -> "Arcobalista de Elite"
                "shadow_hunter" -> "Arco Umbral"
                "paladin" -> "Martelo Sagrado"
                "elite_guard" -> "Lanca de Elite"
                "predator" -> "Lamina Feral"
                "berserker" -> "Machado de Carnificina"
                else -> when (classId.lowercase()) {
                    "mage" -> "Orbe Arcana"
                    "archer" -> "Arcobalista de Elite"
                    else -> "Lanca Imperial"
                }
            }
        }
    }

    fun isMagicPath(pathId: String, classId: String): Boolean {
        val id = pathId.lowercase()
        if (classId.lowercase() == "mage") return true
        val magicTokens = listOf("mage", "arc", "pyro", "element", "cleric", "sorc")
        return magicTokens.any { token -> token in id }
    }

    fun defaultLootProfileFor(baseType: String, family: String): String {
        val normalizedBase = baseType.trim().lowercase()
        val normalizedFamily = family.trim().lowercase()
        return when {
            containsAny(normalizedBase, "slime", "gel", "ooze", "viscous") -> "slime_core"
            containsAny(normalizedBase, "wolf", "panther", "bear", "hawk", "beast", "raptor", "mastiff", "behemoth", "predator") ||
                normalizedFamily in setOf("beast", "animal", "predator") -> "wolf_beast"
            containsAny(normalizedBase, "elemental", "salamander", "magma", "lava", "fire", "storm", "glacial", "prismatic") ||
                normalizedFamily == "elemental" -> "elemental_fire"
            containsAny(normalizedBase, "skeleton", "lich", "undead", "ghoul", "corrupt", "fallen", "heretic", "acolyte", "seraph") ||
                normalizedFamily in setOf("undead", "shadow") -> {
                if (containsAny(normalizedBase, "lich", "seraph")) "undead_ancient" else "undead_relics"
            }
            containsAny(normalizedBase, "bandit", "mercenary", "duelist", "assassin", "blade", "stalker", "reaper", "ranger") ||
                normalizedFamily == "shadow" -> "bandit_shadow"
            containsAny(normalizedBase, "construct", "sentinel", "orb", "arcane", "golem", "watcher", "magister", "core", "colossus") ||
                normalizedFamily in setOf("construct", "arcane") -> "construct_arcane"
            else -> "war_scraps"
        }
    }

    fun defaultFamilyForBaseType(baseType: String): String {
        val normalized = baseType.trim().lowercase()
        return when {
            containsAny(normalized, "slime", "wolf", "panther", "bear", "hawk", "predator", "behemoth", "mastiff", "beast", "raptor") -> "beast"
            containsAny(normalized, "elemental", "salamander", "magma", "lava", "fire", "spirit", "avatar", "storm", "glacial") -> "elemental"
            containsAny(normalized, "skeleton", "knight", "acolyte", "herald", "heretic", "lich", "profaned", "corrupt", "fallen", "seraph") -> "undead"
            containsAny(normalized, "sentinel", "construct", "orb", "watcher", "magister", "devourer", "fragment", "core", "colossus") -> "construct"
            containsAny(normalized, "bandit", "mercenary", "duelist", "hunter", "gladiator", "brute", "berserker", "champion", "captain", "warrior", "ranger") -> "humanoid"
            else -> "humanoid"
        }
    }

    fun classQuestWeaponTemplateId(
        pathId: String,
        classId: String,
        unlockType: ClassQuestUnlockType
    ): String? {
        val path = pathId.trim().lowercase()
        return when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> when (path) {
                "hunter" -> "hunter_trackers_bow"
                "ranger" -> "ranger_longwatch_bow"
                "arcanist" -> "arcanist_rune_staff"
                "elementalist" -> "elementalist_primal_staff"
                "warrior" -> "warrior_guardblade"
                "barbarian" -> "barbarian_raider_axe"
                else -> null
            }
            ClassQuestUnlockType.SPECIALIZATION -> when (path) {
                "bounty_hunter" -> "bounty_writ_bow"
                "assassin" -> "assassin_fang_bow"
                "sharpshooter" -> "sharpshooter_siegebow"
                "shadow_hunter" -> "shadow_hunter_gloombow"
                "archmage" -> "archmage_comet_staff"
                "cleric" -> "cleric_sanctified_crozier"
                "pyromancer" -> "pyromancer_cinder_staff"
                "elemental_master" -> "elemental_master_conflux_staff"
                "paladin" -> "paladin_sunblade"
                "elite_guard" -> "elite_guard_bastion_blade"
                "predator" -> "predator_ripper_axe"
                "berserker" -> "berserker_frenzy_axe"
                else -> when (classId.trim().lowercase()) {
                    "archer" -> "sharpshooter_siegebow"
                    "mage" -> "archmage_comet_staff"
                    "swordman" -> "elite_guard_bastion_blade"
                    else -> null
                }
            }
        }
    }

    fun classQuestQuiverTemplateId(pathId: String): String? {
        return when (pathId.trim().lowercase()) {
            "hunter" -> "hunter_field_quiver"
            "ranger" -> "ranger_wayfarer_quiver"
            "bounty_hunter" -> "hunter_field_quiver"
            "assassin" -> "hunter_field_quiver"
            "sharpshooter" -> "sharpshooter_siege_quiver"
            "shadow_hunter" -> "shadow_hunter_night_quiver"
            else -> null
        }
    }

    private fun containsAny(value: String, vararg tokens: String): Boolean {
        return tokens.any { token -> token in value }
    }
}
