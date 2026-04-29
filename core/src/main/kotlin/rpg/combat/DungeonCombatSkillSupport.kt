package rpg.combat

import kotlin.math.ceil
import rpg.application.model.AmmoStack
import rpg.application.model.CombatMenuAction
import rpg.application.model.CombatSkillOption
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.io.DataRepository
import rpg.model.EquipSlot
import rpg.model.ItemType
import rpg.model.TalentNodeType
import rpg.talent.TalentTreeService

internal data class AttackDecisionBuild(
    val actions: List<CombatMenuAction>,
    val skills: List<CombatSkillOption>
)

internal class DungeonCombatSkillSupport(
    private val engine: GameEngine,
    private val repo: DataRepository,
    private val talentTreeService: TalentTreeService,
    private val format: (Double) -> String,
    private val buildAmmoStacks: (
        itemIds: List<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        selectedTemplateId: String?
    ) -> List<AmmoStack>
) {
    fun buildAttackDecisionActions(snapshot: rpg.combat.CombatSnapshot): AttackDecisionBuild {
        val actions = mutableListOf<CombatMenuAction>()
        val ammoRestriction = rangedAmmoRestriction(snapshot)
        actions += CombatMenuAction.BasicAttack(
            preferMagic = inferBasicAttackMagic(snapshot),
            available = ammoRestriction == null,
            unavailableReason = ammoRestriction
        )
        val decisionSkills = buildCombatSkillOptions(snapshot)
        decisionSkills.forEach { skill ->
            actions += CombatMenuAction.SkillAttack(skill)
        }
        return AttackDecisionBuild(actions = actions, skills = decisionSkills)
    }

    fun buildCombatSkillOptions(snapshot: rpg.combat.CombatSnapshot): List<CombatSkillOption> {
        val basicPreferMagic = inferBasicAttackMagic(snapshot)
        val unlocked = collectV2CombatSkillOptions(snapshot, basicPreferMagic).distinctBy { it.id }
        if (unlocked.isEmpty()) return emptyList()

        val stateReady = snapshot.playerRuntime.state == rpg.combat.CombatState.READY
        val gcdReady = snapshot.playerRuntime.gcdRemaining <= 0.0
        val stateValid = stateReady && gcdReady
        val ammoRestriction = rangedAmmoRestriction(snapshot)

        return unlocked.map { skill ->
            val cooldownRemain = snapshot.playerRuntime.skillCooldowns[skill.id] ?: 0.0
            val cooldownTurns = if (cooldownRemain <= 0.0) {
                0
            } else {
                ceil(cooldownRemain / repo.balance.combat.globalCooldownSeconds).toInt().coerceAtLeast(1)
            }
            val hasMana = snapshot.player.currentMp + 1e-6 >= skill.mpCost
            val available = stateValid && hasMana && cooldownRemain <= 0.0 && ammoRestriction == null
            val unavailableReason = when {
                ammoRestriction != null -> ammoRestriction
                !stateReady -> "Estado: ${combatStateLabel(snapshot.playerRuntime.state)}"
                !gcdReady -> "GCD: ${format(snapshot.playerRuntime.gcdRemaining)}s"
                !hasMana -> "Sem mana"
                cooldownRemain > 0.0 -> "Cooldown: ${cooldownTurns} turnos"
                else -> null
            }
            skill.copy(
                available = available,
                unavailableReason = unavailableReason,
                cooldownRemainingSeconds = cooldownRemain
            )
        }
    }

    fun rangedAmmoStatusLine(snapshot: rpg.combat.CombatSnapshot): String? {
        if (!playerUsesBow(snapshot)) return null
        val normalizedPlayer = InventorySystem.normalizeAmmoStorage(snapshot.player, snapshot.itemInstances, engine.itemRegistry)
        val quiverName = snapshot.player.equipped[EquipSlot.ALJAVA.name]?.let { quiverId ->
            engine.itemResolver.resolve(quiverId, snapshot.itemInstances)?.name ?: quiverId
        } ?: "nenhuma"
        val quiverCapacity = InventorySystem.quiverCapacity(normalizedPlayer, snapshot.itemInstances, engine.itemRegistry)
        val reserve = InventorySystem.inventoryArrowReserveCount(normalizedPlayer, snapshot.itemInstances, engine.itemRegistry)
        val activeAmmo = normalizedPlayer.selectedAmmoTemplateId?.let { templateId ->
            buildAmmoStacks(normalizedPlayer.quiverInventory, snapshot.itemInstances, normalizedPlayer.selectedAmmoTemplateId)
                .firstOrNull { it.templateId == templateId }
                ?.item
                ?.name
        } ?: "-"
        return "Municao: ${countArrows(snapshot)}/$quiverCapacity flecha(s) | Reserva: $reserve | Ativa: $activeAmmo | Aljava: $quiverName"
    }

    private fun collectV2CombatSkillOptions(
        snapshot: rpg.combat.CombatSnapshot,
        basicPreferMagic: Boolean?
    ): List<CombatSkillOption> {
        val player = snapshot.player
        val trees = talentTreeService.activeTrees(player, repo.talentTreesV2.values)
        if (trees.isEmpty()) return emptyList()

        val options = mutableListOf<CombatSkillOption>()
        for (tree in trees) {
            for (node in tree.nodes) {
                if (node.nodeType != TalentNodeType.ACTIVE_SKILL) continue
                val rank = talentTreeService.nodeCurrentRank(player, node)
                if (rank <= 0) continue

                val combat = node.modifiers.combat
                val preferMagic = resolveNodePreferMagic(
                    node = node,
                    basicPreferMagic = basicPreferMagic
                )
                val damageBias = if (preferMagic == true) {
                    node.modifiers.bonuses.derivedMult.damageMagic +
                        node.modifiers.bonuses.derivedAdd.damageMagic +
                        node.modifiers.bonuses.attributes.`int` * 0.6 +
                        node.modifiers.bonuses.attributes.spr * 0.3
                } else {
                    node.modifiers.bonuses.derivedMult.damagePhysical +
                        node.modifiers.bonuses.derivedAdd.damagePhysical +
                        node.modifiers.bonuses.attributes.str * 0.6 +
                        node.modifiers.bonuses.attributes.dex * 0.3
                }

                val rankFactor = rank.toDouble().coerceAtLeast(1.0)
                val pointCost = node.costPerRank
                    .getOrElse((rank - 1).coerceAtMost(node.costPerRank.lastIndex)) { node.costPerRank.lastOrNull() ?: 1 }
                    .coerceAtLeast(1)

                val mpBase = combat["mpCost"] ?: combat["manaCost"] ?: (4.0 + pointCost * 3.2 + rankFactor * 1.1)
                val mpGrowth = combat["mpCostPerRank"] ?: combat["manaCostPerRank"] ?: 0.0
                val cooldownBase = combat["cooldownSeconds"] ?: combat["cooldown"] ?: (0.8 + pointCost * 0.45)
                val cooldownGrowth = combat["cooldownPerRank"] ?: 0.0
                val dmgBase = combat["damageMultiplier"] ?: combat["multiplier"] ?: (1.05 + damageBias.coerceAtLeast(0.0) / 100.0)
                val dmgGrowth = combat["damageMultiplierPerRank"] ?: combat["multiplierPerRank"] ?: 0.0
                val castBase = combat["castTimeSeconds"] ?: combat["cast"] ?: 0.0
                val castGrowth = combat["castTimePerRank"] ?: 0.0
                val healFlatBase = combat["selfHealFlat"] ?: combat["healFlat"] ?: 0.0
                val healFlatGrowth = combat["selfHealFlatPerRank"] ?: combat["healFlatPerRank"] ?: 0.0
                val healPctBase = combat["selfHealPctMaxHp"] ?: combat["healPctMaxHp"] ?: 0.0
                val healPctGrowth = combat["selfHealPctMaxHpPerRank"] ?: combat["healPctMaxHpPerRank"] ?: 0.0
                val ammoCostBase = (combat["ammoCost"] ?: 1.0).toInt().coerceAtLeast(1)
                val ammoCostGrowth = (combat["ammoCostPerRank"] ?: 0.0).toInt()

                val skillId = node.unlocksSkillId?.takeIf { it.isNotBlank() } ?: node.id
                val statusChancePerRank = combat["statusChancePerRankPct"] ?: 0.0
                val statusDurationPerRank = combat["statusDurationPerRankSeconds"] ?: 0.0
                val statusEffectPerRank = combat["statusEffectPerRank"] ?: 0.0
                val statusStacksPerRank = combat["statusMaxStacksPerRank"] ?: 0.0
                val onHitStatuses = node.modifiers.applyStatuses.map { status ->
                    val deltaRank = (rankFactor - 1.0).coerceAtLeast(0.0)
                    status.copy(
                        chancePct = (status.chancePct + statusChancePerRank * deltaRank).coerceIn(0.0, 100.0),
                        durationSeconds = (status.durationSeconds + statusDurationPerRank * deltaRank).coerceAtLeast(0.1),
                        effectValue = (status.effectValue + statusEffectPerRank * deltaRank).coerceAtLeast(0.0),
                        maxStacks = (status.maxStacks + (statusStacksPerRank * deltaRank).toInt()).coerceAtLeast(1)
                    )
                }
                options += CombatSkillOption(
                    id = skillId,
                    name = node.name,
                    mpCost = (mpBase + (rankFactor - 1.0) * mpGrowth).coerceAtLeast(0.0),
                    cooldownSeconds = (cooldownBase + (rankFactor - 1.0) * cooldownGrowth).coerceAtLeast(0.0),
                    castTimeSeconds = (castBase + (rankFactor - 1.0) * castGrowth).coerceAtLeast(0.0),
                    damageMultiplier = (dmgBase + (rankFactor - 1.0) * dmgGrowth).coerceAtLeast(0.1),
                    preferMagic = preferMagic,
                    onHitStatuses = onHitStatuses,
                    selfHealFlat = (healFlatBase + (rankFactor - 1.0) * healFlatGrowth).coerceAtLeast(0.0),
                    selfHealPctMaxHp = (healPctBase + (rankFactor - 1.0) * healPctGrowth).coerceAtLeast(0.0),
                    ammoCost = (ammoCostBase + ((rank - 1).coerceAtLeast(0) * ammoCostGrowth)).coerceAtLeast(1),
                    rank = rank,
                    maxRank = node.maxRank.coerceAtLeast(1),
                    aoeUnlockRank = (combat["aoeUnlockRank"] ?: 0.0).toInt().coerceAtLeast(0),
                    aoeBonusDamagePct = (combat["aoeBonusDamagePct"] ?: 0.0).coerceAtLeast(0.0),
                    available = false,
                    unavailableReason = null,
                    cooldownRemainingSeconds = 0.0
                )
            }
        }
        return options
    }

    private fun resolveNodePreferMagic(
        node: rpg.model.TalentNode,
        basicPreferMagic: Boolean?
    ): Boolean? {
        node.modifiers.combat["damageTypeMagic"]?.let { return it > 0.0 }

        val magicBias = node.modifiers.bonuses.derivedMult.damageMagic +
            node.modifiers.bonuses.derivedAdd.damageMagic +
            node.modifiers.bonuses.attributes.`int` +
            node.modifiers.bonuses.attributes.spr
        val physicalBias = node.modifiers.bonuses.derivedMult.damagePhysical +
            node.modifiers.bonuses.derivedAdd.damagePhysical +
            node.modifiers.bonuses.attributes.str +
            node.modifiers.bonuses.attributes.dex
        if (magicBias > physicalBias) return true
        if (physicalBias > magicBias) return false
        return basicPreferMagic
    }

    private fun inferBasicAttackMagic(snapshot: rpg.combat.CombatSnapshot): Boolean? {
        val player = snapshot.player
        var score = 0
        val magicClasses = setOf(
            "mage",
            "arcanist",
            "elementalist",
            "archmage",
            "cleric",
            "pyromancer",
            "elemental_master"
        )
        val physicalClasses = setOf(
            "swordman",
            "warrior",
            "archer",
            "barbarian",
            "hunter",
            "ranger",
            "bounty_hunter",
            "assassin",
            "sharpshooter",
            "shadow_hunter",
            "paladin",
            "elite_guard",
            "predator",
            "berserker"
        )
        if (player.classId in magicClasses) score += 2
        if (player.classId in physicalClasses) score -= 2
        player.subclassId?.let {
            if (it in magicClasses) score += 2
            if (it in physicalClasses) score -= 2
        }
        val mainWeaponId = player.equipped[EquipSlot.WEAPON_MAIN.name]
        if (!mainWeaponId.isNullOrBlank()) {
            val weapon = engine.itemResolver.resolve(mainWeaponId, snapshot.itemInstances)
            val source = "${weapon?.id.orEmpty()} ${weapon?.name.orEmpty()} ${weapon?.tags?.joinToString(" ").orEmpty()}".lowercase()
            if (listOf("staff", "cajado", "scepter", "cetro", "wand", "arcane", "magic", "magico").any { it in source }) {
                score += 3
            }
            if (listOf("sword", "espada", "axe", "machado", "bow", "arco", "lanca", "spear").any { it in source }) {
                score -= 2
            }
        }
        if (snapshot.playerStats.derived.damageMagic > snapshot.playerStats.derived.damagePhysical * 1.1) {
            score += 1
        } else if (snapshot.playerStats.derived.damagePhysical > snapshot.playerStats.derived.damageMagic * 1.1) {
            score -= 1
        }
        return when {
            score > 0 -> true
            score < 0 -> false
            else -> null
        }
    }

    private fun rangedAmmoRestriction(snapshot: rpg.combat.CombatSnapshot): String? {
        if (!playerUsesBow(snapshot)) return null
        if (snapshot.player.equipped[EquipSlot.ALJAVA.name].isNullOrBlank()) {
            return "Sem aljava equipada"
        }
        if (countArrows(snapshot) <= 0) {
            return "Sem flechas"
        }
        return null
    }

    private fun playerUsesBow(snapshot: rpg.combat.CombatSnapshot): Boolean {
        val mainWeaponId = snapshot.player.equipped[EquipSlot.WEAPON_MAIN.name] ?: return false
        val weapon = engine.itemResolver.resolve(mainWeaponId, snapshot.itemInstances) ?: return false
        val normalizedTags = weapon.tags.mapTo(mutableSetOf()) { it.trim().lowercase() }
        if ("bow" in normalizedTags) return true
        val source = "${weapon.id} ${weapon.name}".lowercase()
        return "bow" in source || "arco" in source
    }

    private fun countArrows(snapshot: rpg.combat.CombatSnapshot): Int {
        val normalizedPlayer = InventorySystem.normalizeAmmoStorage(snapshot.player, snapshot.itemInstances, engine.itemRegistry)
        return InventorySystem.quiverAmmoCount(normalizedPlayer, snapshot.itemInstances, engine.itemRegistry)
    }

    private fun combatStateLabel(state: rpg.combat.CombatState): String = when (state) {
        rpg.combat.CombatState.IDLE -> "Carregando"
        rpg.combat.CombatState.READY -> "Pronto"
        rpg.combat.CombatState.CASTING -> "Castando"
        rpg.combat.CombatState.STUNNED -> "Atordoado"
        rpg.combat.CombatState.DEAD -> "Morto"
    }
}
