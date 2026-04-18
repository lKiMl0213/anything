package rpg.combat

import kotlin.math.max
import kotlin.random.Random
import rpg.engine.Combat
import rpg.engine.ComputedStats
import rpg.engine.StatsCalculator
import rpg.engine.StatsEngine
import rpg.inventory.InventorySystem
import rpg.item.ItemResolver
import rpg.model.BiomeDef
import rpg.model.Bonuses
import rpg.model.CombatStatusApplyDef
import rpg.model.DamageChannel
import rpg.model.DerivedStats
import rpg.model.EquipSlot
import rpg.model.GameBalanceDef
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.MapTierDef
import rpg.model.MonsterArchetypeDef
import rpg.model.PlayerState
import rpg.model.TalentTree
import rpg.monster.MonsterBehaviorEngine
import rpg.monster.MonsterAffinityService
import rpg.monster.MonsterInstance
import rpg.status.StatusType
import rpg.status.StatusSystem
import rpg.talent.TalentCombatIntegrationService
import rpg.talent.TalentCombatModifiers
import rpg.talent.TalentTreeService
import rpg.registry.ItemRegistry

sealed class CombatAction {
    data class Attack(val preferMagic: Boolean? = null) : CombatAction()
    data class Skill(val spec: CombatSkillSpec) : CombatAction()
    data class UseItem(val itemId: String) : CombatAction()
    data object Escape : CombatAction()
}

data class CombatSkillSpec(
    val id: String,
    val name: String,
    val mpCost: Double,
    val cooldownSeconds: Double,
    val damageMultiplier: Double = 1.0,
    val preferMagic: Boolean? = null,
    val castTimeSeconds: Double = 0.0,
    val onHitStatuses: List<CombatStatusApplyDef> = emptyList(),
    val selfHealFlat: Double = 0.0,
    val selfHealPctMaxHp: Double = 0.0,
    val ammoCost: Int = 1,
    val rank: Int = 1,
    val aoeUnlockRank: Int = 0,
    val aoeBonusDamagePct: Double = 0.0
)

data class CombatSnapshot(
    val player: PlayerState,
    val playerStats: ComputedStats,
    val monster: MonsterInstance,
    val monsterStats: ComputedStats,
    val monsterHp: Double,
    val itemInstances: Map<String, ItemInstance>,
    val playerRuntime: CombatRuntimeState,
    val monsterRuntime: CombatRuntimeState,
    val playerFillRate: Double,
    val monsterFillRate: Double,
    val pausedForDecision: Boolean
)

interface PlayerCombatController {
    fun onFrame(snapshot: CombatSnapshot) {}
    fun onDecisionStarted(snapshot: CombatSnapshot) {}
    fun onDecisionEnded() {}
    fun pollAction(snapshot: CombatSnapshot): CombatAction?
}

data class CombatResult(
    val playerAfter: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val victory: Boolean,
    val escaped: Boolean = false,
    val telemetry: CombatTelemetry = CombatTelemetry()
)

data class CombatTelemetry(
    val playerDamageDealt: Double = 0.0,
    val playerDamageTaken: Double = 0.0,
    val playerCriticalHits: Int = 0
)

private data class MutableCombatTelemetry(
    var playerDamageDealt: Double = 0.0,
    var playerDamageTaken: Double = 0.0,
    var playerCriticalHits: Int = 0
)

private enum class CombatantKind {
    PLAYER,
    MONSTER
}

private data class CombatActor(
    val id: String,
    val name: String,
    val kind: CombatantKind,
    val monsterArchetypeId: String? = null,
    val monsterTypeId: String? = null,
    val monsterTags: Set<String> = emptySet(),
    var stats: ComputedStats,
    var currentHp: Double,
    var currentMp: Double,
    var runtime: CombatRuntimeState,
    var pendingAction: CombatAction? = null,
    var pendingAfterResolve: (() -> Unit)? = null,
    var preferMagic: Boolean? = null,
    var speedBonusPct: Double = 0.0,
    var onHitStatuses: List<CombatStatusApplyDef> = emptyList(),
    val talentModifiers: TalentCombatModifiers = TalentCombatModifiers()
)

class CombatEngine(
    private val statsEngine: StatsEngine,
    private val itemResolver: ItemResolver,
    private val itemRegistry: ItemRegistry,
    private val behaviorEngine: MonsterBehaviorEngine,
    private val rng: Random,
    private val balance: GameBalanceDef,
    private val biomes: Map<String, BiomeDef>,
    private val archetypes: Map<String, MonsterArchetypeDef>,
    private val talentTrees: Collection<TalentTree> = emptyList(),
    private val talentTreeService: TalentTreeService = TalentTreeService(balance.talentPoints),
    private val talentCombatIntegrationService: TalentCombatIntegrationService = TalentCombatIntegrationService(talentTreeService),
    private val monsterAffinityService: MonsterAffinityService? = null
) {
    private var activeLogger: ((String) -> Unit)? = null

    fun runBattle(
        playerState: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        monster: MonsterInstance,
        tier: MapTierDef,
        displayName: String,
        controller: PlayerCombatController,
        eventLogger: (String) -> Unit = { line -> kotlin.io.println(line) }
    ): CombatResult {
        val previousLogger = activeLogger
        activeLogger = eventLogger
        try {
        var player = playerState
        var instances = itemInstances
        var playerStats = statsEngine.computePlayerStats(player, instances)

        val baseMonsterStats = StatsCalculator.compute(monster.attributes, listOf(monster.bonuses))
        var currentMonsterStats = baseMonsterStats

        var summonsUsed = 0
        var enrageTurns = 0
        var enrageMultiplier = DerivedStats()
        var evolved = false
        var evolveMultiplier = DerivedStats()
        var combatElapsedSeconds = 0.0
        val telemetry = MutableCombatTelemetry()

        fun telemetrySnapshot(): CombatTelemetry {
            return CombatTelemetry(
                playerDamageDealt = telemetry.playerDamageDealt.coerceAtLeast(0.0),
                playerDamageTaken = telemetry.playerDamageTaken.coerceAtLeast(0.0),
                playerCriticalHits = telemetry.playerCriticalHits.coerceAtLeast(0)
            )
        }

        val playerTalentModifiers = talentCombatIntegrationService.collectForPlayer(
            player = player,
            trees = talentTrees
        )
        val summonBonus = tier.biomeId?.let { biomes[it]?.summonChanceBonusPct ?: 0.0 } ?: 0.0
        val playerActor = CombatActor(
            id = "player",
            name = player.name,
            kind = CombatantKind.PLAYER,
            monsterArchetypeId = null,
            monsterTypeId = null,
            monsterTags = emptySet(),
            stats = playerStats,
            currentHp = player.currentHp,
            currentMp = player.currentMp,
            runtime = CombatRuntimeState(actionThreshold = balance.combat.actionThreshold),
            onHitStatuses = collectPlayerOnHitStatuses(player, instances),
            talentModifiers = playerTalentModifiers
        )
        val monsterActor = CombatActor(
            id = monster.archetypeId,
            name = displayName,
            kind = CombatantKind.MONSTER,
            monsterArchetypeId = monster.sourceArchetypeId.ifBlank { monster.archetypeId },
            monsterTypeId = monster.monsterTypeId.ifBlank { monster.family },
            monsterTags = monster.tags.map { it.lowercase() }.toSet(),
            stats = currentMonsterStats,
            currentHp = baseMonsterStats.derived.hpMax,
            currentMp = 0.0,
            runtime = CombatRuntimeState(actionThreshold = balance.combat.actionThreshold),
            preferMagic = monster.personality.preferMagic,
            onHitStatuses = monster.onHitStatuses,
            talentModifiers = TalentCombatModifiers()
        )

        fun refreshMonsterStats(adjustHp: Boolean) {
            val oldMax = currentMonsterStats.derived.hpMax
            var derived = baseMonsterStats.derived
            if (evolved) {
                derived = derived.applyMultiplier(evolveMultiplier)
            }
            if (enrageTurns > 0) {
                derived = derived.applyMultiplier(enrageMultiplier)
            }
            currentMonsterStats = baseMonsterStats.copy(derived = derived)
            monsterActor.stats = currentMonsterStats
            if (adjustHp && oldMax > 0.0) {
                val pct = monsterActor.currentHp / oldMax
                monsterActor.currentHp = (currentMonsterStats.derived.hpMax * pct).coerceAtLeast(1.0)
            }
        }

        fun handleMonsterBehavior() {
            val maxHp = currentMonsterStats.derived.hpMax
            val hpPct = if (maxHp <= 0.0) 0.0 else (monsterActor.currentHp / maxHp) * 100.0
            val evolveTrigger = behaviorEngine.rollEvolve(monster.tags, hpPct, evolved)
            if (evolveTrigger != null) {
                evolved = true
                evolveMultiplier += evolveTrigger.multiplier
                refreshMonsterStats(adjustHp = true)
                combatLog("$displayName evolui durante o combate!")
            }
            val enrageTrigger = behaviorEngine.rollEnrage(monster.tags, hpPct, enrageTurns > 0)
            if (enrageTrigger != null) {
                enrageTurns = enrageTrigger.turns
                enrageMultiplier = enrageTrigger.multiplier
                refreshMonsterStats(adjustHp = false)
                combatLog("$displayName entra em furia!")
            }
        }

        val tickSeconds = balance.combat.tickSeconds.coerceAtLeast(0.05)
        val tickMillis = (tickSeconds * 1000.0).toLong().coerceAtLeast(25L)
        var playerDecisionOpen = false

        fun snapshot(paused: Boolean): CombatSnapshot {
            return buildSnapshot(
                player = player,
                playerStats = playerStats,
                monster = monster,
                monsterStats = currentMonsterStats,
                monsterHp = monsterActor.currentHp,
                itemInstances = instances,
                playerActor = playerActor,
                monsterActor = monsterActor,
                pausedForDecision = paused
            )
        }

        controller.onFrame(snapshot(paused = false))
        while (playerActor.currentHp > 0.0 && monsterActor.currentHp > 0.0) {
            val tickStartNs = System.nanoTime()

            val pausedAtLoopStart = playerDecisionOpen && playerActor.runtime.state == CombatState.READY
            if (!pausedAtLoopStart) {
                advanceTick(
                    deltaTime = tickSeconds,
                    elapsedSeconds = combatElapsedSeconds,
                    player = playerActor,
                    monster = monsterActor,
                    onCastComplete = { caster, target ->
                        handleCastComplete(
                            caster = caster,
                            target = target,
                            telemetry = telemetry,
                            playerState = player,
                            itemInstances = instances,
                            onPlayerUpdate = { updated, updatedInstances ->
                                player = updated
                                instances = updatedInstances
                                playerStats = statsEngine.computePlayerStats(player, instances)
                                playerActor.stats = playerStats
                                playerActor.currentHp = player.currentHp
                                playerActor.currentMp = player.currentMp
                                playerActor.onHitStatuses = collectPlayerOnHitStatuses(player, instances)
                            }
                        )
                    },
                    telemetry = telemetry
                )
                combatElapsedSeconds += tickSeconds
            }

            if (playerActor.currentHp <= 0.0 || monsterActor.currentHp <= 0.0) break

            if (playerActor.runtime.state == CombatState.READY) {
                if (!playerDecisionOpen) {
                    playerDecisionOpen = true
                    controller.onDecisionStarted(snapshot(paused = true))
                }
            } else if (playerDecisionOpen) {
                playerDecisionOpen = false
                controller.onDecisionEnded()
            }

            var pendingPlayerAction: CombatAction? = null
            val decisionPaused = playerDecisionOpen && playerActor.runtime.state == CombatState.READY
            if (decisionPaused) {
                pendingPlayerAction = controller.pollAction(snapshot(paused = true))
            }

            while (playerActor.currentHp > 0.0 && monsterActor.currentHp > 0.0) {
                val decisionPausedLoop = playerDecisionOpen && playerActor.runtime.state == CombatState.READY
                val readyCandidates = mutableListOf<CombatActor>()
                if (!decisionPausedLoop &&
                    monsterActor.currentHp > 0.0 &&
                    monsterActor.runtime.state == CombatState.READY &&
                    monsterActor.runtime.gcdRemaining <= 0.0
                ) {
                    readyCandidates += monsterActor
                }
                if (playerActor.currentHp > 0.0 &&
                    playerActor.runtime.state == CombatState.READY &&
                    playerActor.runtime.gcdRemaining <= 0.0 &&
                    pendingPlayerAction != null
                ) {
                    readyCandidates += playerActor
                }
                val actor = nextReadyActor(readyCandidates) ?: break
                if (actor.kind == CombatantKind.PLAYER) {
                    val action = pendingPlayerAction ?: break
                    pendingPlayerAction = null
                    val outcome = processAction(
                        actor = playerActor,
                        target = monsterActor,
                        action = action,
                        playerState = player,
                        itemInstances = instances,
                        telemetry = telemetry,
                        onPlayerUpdate = { updated, updatedInstances ->
                            player = updated
                            instances = updatedInstances
                            playerStats = statsEngine.computePlayerStats(player, instances)
                            playerActor.stats = playerStats
                            playerActor.currentHp = player.currentHp
                            playerActor.currentMp = player.currentMp
                            playerActor.onHitStatuses = collectPlayerOnHitStatuses(player, instances)
                        }
                    )
                    if (outcome.escaped) {
                        controller.onDecisionEnded()
                        return CombatResult(
                            playerAfter = player,
                            itemInstances = instances,
                            victory = false,
                            escaped = true,
                            telemetry = telemetrySnapshot()
                        )
                    }
                    if (outcome.consumedReady && playerDecisionOpen) {
                        playerDecisionOpen = false
                        controller.onDecisionEnded()
                    }
                } else {
                    val outcome = processMonsterAction(
                        actor = monsterActor,
                        target = playerActor,
                        castTime = archetypeCastTime(monster),
                        onMonsterBehavior = { handleMonsterBehavior() },
                        onSummon = {
                            if (behaviorEngine.rollSummon(monster.tags, summonsUsed, summonBonus)) {
                                summonsUsed++
                                combatLog("$displayName invoca reforcos! Um ataque extra ocorre.")
                                resolveSkill(
                                    attacker = monsterActor,
                                    defender = playerActor,
                                    preferMagic = monsterActor.preferMagic,
                                    telemetry = telemetry
                                )
                                player = applyReviveIfNeeded(syncPlayerHp(player, playerActor), playerActor)
                            }
                        },
                        onAfterAttack = {
                            if (enrageTurns > 0) {
                                enrageTurns -= 1
                                if (enrageTurns == 0) {
                                    enrageMultiplier = DerivedStats()
                                    refreshMonsterStats(adjustHp = false)
                                }
                            }
                        },
                        playerProvider = { player },
                        telemetry = telemetry,
                        onPlayerUpdate = { updated ->
                            player = updated
                            playerActor.currentHp = player.currentHp
                            playerActor.currentMp = player.currentMp
                        }
                    )
                    if (!outcome) {
                        controller.onDecisionEnded()
                        return CombatResult(
                            playerAfter = player,
                            itemInstances = instances,
                            victory = false,
                            telemetry = telemetrySnapshot()
                        )
                    }
                    if (playerDecisionOpen && pendingPlayerAction == null && playerActor.runtime.state == CombatState.READY) {
                        pendingPlayerAction = controller.pollAction(snapshot(paused = true))
                    }
                }
            }

            val paused = playerDecisionOpen && playerActor.runtime.state == CombatState.READY
            controller.onFrame(snapshot(paused = paused))

            val elapsedMs = (System.nanoTime() - tickStartNs) / 1_000_000L
            val sleepMs = tickMillis - elapsedMs
            if (sleepMs > 0L) {
                try {
                    Thread.sleep(sleepMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        player = player.copy(currentHp = playerActor.currentHp, currentMp = playerActor.currentMp)
        val victory = playerActor.currentHp > 0.0 && monsterActor.currentHp <= 0.0
        if (playerDecisionOpen) {
            controller.onDecisionEnded()
        }
        controller.onFrame(
            buildSnapshot(
                player = player,
                playerStats = playerStats,
                monster = monster,
                monsterStats = currentMonsterStats,
                monsterHp = monsterActor.currentHp,
                itemInstances = instances,
                playerActor = playerActor,
                monsterActor = monsterActor,
                pausedForDecision = false
            )
        )
        return CombatResult(
            playerAfter = player,
            itemInstances = instances,
            victory = victory,
            escaped = false,
            telemetry = telemetrySnapshot()
        )
        } finally {
            activeLogger = previousLogger
        }
    }

    private fun nextReadyActor(candidates: List<CombatActor>): CombatActor? {
        val ready = candidates
            .filter { it.currentHp > 0.0 }
            .filter { it.runtime.state == CombatState.READY }
            .filter { it.runtime.gcdRemaining <= 0.0 }
        if (ready.isEmpty()) return null

        return ready.sortedWith(
            compareBy<CombatActor> { it.runtime.readySinceSeconds ?: Double.MAX_VALUE }
                .thenByDescending { computeFillRate(it) }
                .thenBy { if (it.kind == CombatantKind.PLAYER) 0 else 1 }
        ).first()
    }

    private fun advanceTick(
        deltaTime: Double,
        elapsedSeconds: Double,
        player: CombatActor,
        monster: CombatActor,
        onCastComplete: (CombatActor, CombatActor) -> Unit,
        telemetry: MutableCombatTelemetry
    ) {
        listOf(player, monster).forEach { actor ->
            val opponent = if (actor.kind == CombatantKind.PLAYER) monster else player

            if (actor.runtime.state == CombatState.DEAD) return@forEach

            val rawTick = applyStatusEffect(actor, deltaTime)
            val tick = applyMonsterAffinityToStatusTick(actor, rawTick)
            if (tick.dotDamage > 0.0) {
                actor.currentHp = (actor.currentHp - tick.dotDamage).coerceAtLeast(0.0)
                if (actor.kind == CombatantKind.PLAYER) {
                    telemetry.playerDamageTaken += tick.dotDamage
                } else {
                    telemetry.playerDamageDealt += tick.dotDamage
                }
            }
            emitStatusTickMessages(actor, tick)

            if (actor.currentHp <= 0.0) {
                actor.currentHp = 0.0
                actor.runtime = actor.runtime.copy(
                    state = CombatState.DEAD,
                    readySinceSeconds = null
                )
                return@forEach
            }

            var runtime = actor.runtime.copy(
                gcdRemaining = (actor.runtime.gcdRemaining - deltaTime).coerceAtLeast(0.0),
                skillCooldowns = decrementCooldowns(actor.runtime.skillCooldowns, deltaTime),
                statusImmunitySeconds = (actor.runtime.statusImmunitySeconds - deltaTime).coerceAtLeast(0.0),
                tempBuffRemainingSeconds = (actor.runtime.tempBuffRemainingSeconds - deltaTime).coerceAtLeast(0.0)
            )
            if (runtime.tempBuffRemainingSeconds <= 0.0) {
                runtime = runtime.copy(
                    tempBuffDamageMultiplier = 1.0,
                    tempBuffFillRateMultiplier = 1.0
                )
            }

            if (runtime.state == CombatState.CASTING) {
                val castRemaining = (runtime.castRemaining - deltaTime).coerceAtLeast(0.0)
                runtime = runtime.copy(castRemaining = castRemaining)
                actor.runtime = runtime
                if (castRemaining <= 0.0) {
                    onCastComplete(actor, opponent)
                }
                return@forEach
            }

            if (tick.actionBlocked) {
                actor.runtime = runtime.copy(
                    state = CombatState.STUNNED,
                    readySinceSeconds = null
                )
                return@forEach
            }
            if (runtime.state == CombatState.STUNNED) {
                runtime = runtime.copy(state = CombatState.IDLE)
            }

            if (runtime.state == CombatState.READY) {
                actor.runtime = runtime
                return@forEach
            }

            if (runtime.state != CombatState.IDLE || runtime.gcdRemaining > 0.0) {
                actor.runtime = runtime
                return@forEach
            }

            val fillRate = computeFillRate(actor)
            val newBar = (runtime.actionBar + fillRate * deltaTime).coerceAtMost(runtime.actionThreshold)
            runtime = runtime.copy(actionBar = newBar)
            if (newBar >= runtime.actionThreshold) {
                runtime = runtime.copy(
                    state = CombatState.READY,
                    readySinceSeconds = elapsedSeconds + deltaTime
                )
            }
            actor.runtime = runtime
        }
    }

    private fun processAction(
        actor: CombatActor,
        target: CombatActor,
        action: CombatAction,
        playerState: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        telemetry: MutableCombatTelemetry,
        onPlayerUpdate: (PlayerState, Map<String, ItemInstance>) -> Unit
    ): ActionOutcome {
        if (actor.runtime.state != CombatState.READY) {
            return ActionOutcome(consumedReady = false)
        }
        if (StatusSystem.rollParalyzedFailure(actor.runtime.statuses, rng)) {
            combatLog(colorize("Voce esta paralisado e falhou na acao!", ansiYellow))
            endAction(actor, actionBarCarryPct = 0.0)
            return ActionOutcome()
        }

        return when (action) {
            is CombatAction.Attack -> {
                val ammoBlock = rangedAmmoRequirementReason(playerState, itemInstances)
                if (ammoBlock != null) {
                    combatLog(colorize(ammoBlock, ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                val castTime = effectiveCastTime(actor, 0.0)
                if (castTime > 0.0) {
                    startCasting(actor, action, castTime)
                } else {
                    val ammoPayload = previewAmmoPayload(playerState, itemInstances, amount = 1)
                    val ammoConsumed = consumeArrowAmmo(playerState, itemInstances, amount = 1)
                    if (ammoConsumed == null && playerUsesBowAmmo(playerState, itemInstances)) {
                        combatLog(colorize("Voce esta sem flechas para atacar.", ansiYellow))
                        return ActionOutcome(consumedReady = false)
                    }
                    if (ammoConsumed != null) {
                        val syncedPlayer = ammoConsumed.player.copy(currentHp = actor.currentHp, currentMp = actor.currentMp)
                        onPlayerUpdate(syncedPlayer, ammoConsumed.itemInstances)
                    }
                    val resolution = resolveSkill(
                        attacker = actor,
                        defender = target,
                        preferMagic = action.preferMagic,
                        telemetry = telemetry,
                        extraBonuses = ammoPayload.bonuses,
                        extraOnHitStatuses = ammoPayload.statuses
                    )
                    val carryPct = if (resolution.hit) {
                        actor.talentModifiers.atb.actionBarGainOnHitPct + resolution.carryBonusPct
                    } else {
                        resolution.carryBonusPct
                    }
                    endAction(actor, actionBarCarryPct = carryPct)
                }
                ActionOutcome()
            }

            is CombatAction.Skill -> {
                val spec = action.spec
                if (spec.id.isBlank()) {
                    combatLog(colorize("Habilidade invalida.", ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                val ammoBlock = rangedAmmoRequirementReason(playerState, itemInstances)
                if (ammoBlock != null) {
                    combatLog(colorize(ammoBlock, ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                val cooldown = actor.runtime.skillCooldowns[spec.id] ?: 0.0
                if (cooldown > 0.0) {
                    combatLog(colorize("${spec.name} em cooldown (${format(cooldown)}s).", ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                val noManaCostChance = actor.talentModifiers.atb.noManaCostChancePct.coerceIn(0.0, 100.0)
                val freeCast = spec.mpCost > 0.0 && rng.nextDouble(0.0, 100.0) <= noManaCostChance
                if (!freeCast && actor.currentMp + 1e-6 < spec.mpCost) {
                    combatLog(colorize("Mana insuficiente para ${spec.name}.", ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                if (!freeCast) {
                    actor.currentMp = (actor.currentMp - spec.mpCost).coerceAtLeast(0.0)
                } else {
                    combatLog(colorize("Proc de talento: ${spec.name} nao consumiu mana.", ansiBlue))
                }
                applySkillCooldown(actor, spec)
                val castTime = effectiveCastTime(actor, spec.castTimeSeconds)
                if (castTime > 0.0) {
                    startCasting(actor, action, castTime)
                } else {
                    val ammoPayload = previewAmmoPayload(playerState, itemInstances, amount = spec.ammoCost)
                    val ammoConsumed = consumeArrowAmmo(playerState, itemInstances, amount = spec.ammoCost)
                    if (ammoConsumed == null && playerUsesBowAmmo(playerState, itemInstances)) {
                        combatLog(colorize("Voce esta sem flechas para usar ${spec.name}.", ansiYellow))
                        return ActionOutcome(consumedReady = false)
                    }
                    if (ammoConsumed != null) {
                        val syncedPlayer = ammoConsumed.player.copy(currentHp = actor.currentHp, currentMp = actor.currentMp)
                        onPlayerUpdate(syncedPlayer, ammoConsumed.itemInstances)
                    }
                    val resolution = resolveSkill(
                        attacker = actor,
                        defender = target,
                        preferMagic = spec.preferMagic,
                        telemetry = telemetry,
                        actionMultiplier = spec.damageMultiplier.coerceAtLeast(0.1),
                        actionName = spec.name,
                        extraOnHitStatuses = spec.onHitStatuses + ammoPayload.statuses,
                        extraBonuses = ammoPayload.bonuses,
                        selfHealFlat = spec.selfHealFlat,
                        selfHealPctMaxHp = spec.selfHealPctMaxHp,
                        skillRank = spec.rank,
                        aoeUnlockRank = spec.aoeUnlockRank,
                        aoeBonusDamagePct = spec.aoeBonusDamagePct
                    )
                    val carryPct = if (resolution.hit) {
                        actor.talentModifiers.atb.actionBarGainOnHitPct + resolution.carryBonusPct
                    } else {
                        resolution.carryBonusPct
                    }
                    endAction(actor, actionBarCarryPct = carryPct)
                }
                ActionOutcome()
            }

            is CombatAction.UseItem -> {
                if (action.itemId.isBlank()) {
                    combatLog(colorize("Nenhum item selecionado.", ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                val result = useItem(
                    selfActor = actor,
                    player = playerState,
                    itemInstances = itemInstances,
                    itemId = action.itemId,
                    currentStatuses = actor.runtime.statuses,
                    currentImmunitySeconds = actor.runtime.statusImmunitySeconds
                )
                onPlayerUpdate(result.player, result.itemInstances)
                actor.runtime = actor.runtime.copy(
                    statuses = result.statuses,
                    statusImmunitySeconds = result.statusImmunitySeconds
                )
                endAction(actor, actionBarCarryPct = 0.0)
                ActionOutcome()
            }

            is CombatAction.Escape -> {
                val attempt = rollEscape(actor.stats, target.stats)
                if (attempt.success) {
                    combatLog("Voce fugiu! (${format(attempt.chancePct)}%)")
                    ActionOutcome(escaped = true)
                } else {
                    combatLog("Voce tentou fugir, mas falhou. (${format(attempt.chancePct)}%)")
                    endAction(actor, actionBarCarryPct = 0.0)
                    ActionOutcome()
                }
            }
        }
    }

    private fun processMonsterAction(
        actor: CombatActor,
        target: CombatActor,
        castTime: Double,
        onMonsterBehavior: () -> Unit,
        onSummon: () -> Unit,
        onAfterAttack: () -> Unit,
        playerProvider: () -> PlayerState,
        telemetry: MutableCombatTelemetry,
        onPlayerUpdate: (PlayerState) -> Unit
    ): Boolean {
        if (actor.runtime.state != CombatState.READY) return true

        if (StatusSystem.rollParalyzedFailure(actor.runtime.statuses, rng)) {
            combatLog(colorize("${actor.name} falhou em agir (paralisado).", ansiYellow))
            endAction(actor, actionBarCarryPct = 0.0)
            return true
        }
        onMonsterBehavior()
        val effectiveCast = effectiveCastTime(actor, castTime)
        if (effectiveCast > 0.0) {
            startCasting(
                actor,
                CombatAction.Attack(preferMagic = actor.preferMagic),
                effectiveCast
            ) {
                val updated = applyReviveIfNeeded(syncPlayerHp(playerProvider(), target), target)
                onPlayerUpdate(updated)
                onSummon()
                onAfterAttack()
            }
            return true
        }
        val resolution = resolveSkill(
            attacker = actor,
            defender = target,
            preferMagic = actor.preferMagic,
            telemetry = telemetry
        )
        val updated = applyReviveIfNeeded(syncPlayerHp(playerProvider(), target), target)
        onPlayerUpdate(updated)
        onSummon()
        onAfterAttack()
        val carryPct = if (resolution.hit) {
            actor.talentModifiers.atb.actionBarGainOnHitPct + resolution.carryBonusPct
        } else {
            resolution.carryBonusPct
        }
        endAction(actor, actionBarCarryPct = carryPct)
        return true
    }

    private fun handleCastComplete(
        caster: CombatActor,
        target: CombatActor,
        telemetry: MutableCombatTelemetry,
        playerState: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        onPlayerUpdate: (PlayerState, Map<String, ItemInstance>) -> Unit
    ) {
        val pending = caster.pendingAction ?: return
        var resolution = SkillResolutionResult(hit = false)
        when (pending) {
            is CombatAction.Attack -> {
                val ammoPayload = if (caster.kind == CombatantKind.PLAYER) {
                    previewAmmoPayload(playerState, itemInstances, amount = 1)
                } else {
                    AmmoPayload()
                }
                if (caster.kind == CombatantKind.PLAYER) {
                    val ammoConsumed = consumeArrowAmmo(playerState, itemInstances, amount = 1)
                    if (ammoConsumed == null && playerUsesBowAmmo(playerState, itemInstances)) {
                        combatLog(colorize("Voce ficou sem flechas antes do disparo.", ansiYellow))
                        caster.pendingAfterResolve?.invoke()
                        endAction(caster, actionBarCarryPct = 0.0)
                        return
                    }
                    if (ammoConsumed != null) {
                        val syncedPlayer = ammoConsumed.player.copy(currentHp = caster.currentHp, currentMp = caster.currentMp)
                        onPlayerUpdate(syncedPlayer, ammoConsumed.itemInstances)
                    }
                }
                resolution = resolveSkill(
                    attacker = caster,
                    defender = target,
                    preferMagic = pending.preferMagic ?: caster.preferMagic,
                    telemetry = telemetry,
                    extraBonuses = ammoPayload.bonuses,
                    extraOnHitStatuses = ammoPayload.statuses
                )
            }
            is CombatAction.Skill -> {
                val ammoPayload = if (caster.kind == CombatantKind.PLAYER) {
                    previewAmmoPayload(playerState, itemInstances, amount = pending.spec.ammoCost)
                } else {
                    AmmoPayload()
                }
                if (caster.kind == CombatantKind.PLAYER) {
                    val ammoConsumed = consumeArrowAmmo(playerState, itemInstances, amount = pending.spec.ammoCost)
                    if (ammoConsumed == null && playerUsesBowAmmo(playerState, itemInstances)) {
                        combatLog(colorize("Voce ficou sem flechas antes de concluir ${pending.spec.name}.", ansiYellow))
                        caster.pendingAfterResolve?.invoke()
                        endAction(caster, actionBarCarryPct = 0.0)
                        return
                    }
                    if (ammoConsumed != null) {
                        val syncedPlayer = ammoConsumed.player.copy(currentHp = caster.currentHp, currentMp = caster.currentMp)
                        onPlayerUpdate(syncedPlayer, ammoConsumed.itemInstances)
                    }
                }
                resolution = resolveSkill(
                    attacker = caster,
                    defender = target,
                    preferMagic = pending.spec.preferMagic,
                    telemetry = telemetry,
                    actionMultiplier = pending.spec.damageMultiplier.coerceAtLeast(0.1),
                    actionName = pending.spec.name,
                    extraOnHitStatuses = pending.spec.onHitStatuses + ammoPayload.statuses,
                    extraBonuses = ammoPayload.bonuses,
                    selfHealFlat = pending.spec.selfHealFlat,
                    selfHealPctMaxHp = pending.spec.selfHealPctMaxHp,
                    skillRank = pending.spec.rank,
                    aoeUnlockRank = pending.spec.aoeUnlockRank,
                    aoeBonusDamagePct = pending.spec.aoeBonusDamagePct
                )
            }
            is CombatAction.UseItem -> {
                if (caster.kind == CombatantKind.PLAYER) {
                    // Casting for consumables is not supported.
                }
            }

            is CombatAction.Escape -> {
                // Escape is always resolved immediately.
            }
        }
        caster.pendingAfterResolve?.invoke()
        val carryPct = if (resolution.hit) {
            caster.talentModifiers.atb.actionBarGainOnHitPct + resolution.carryBonusPct
        } else {
            resolution.carryBonusPct
        }
        endAction(caster, actionBarCarryPct = carryPct)
    }

    private fun resolveSkill(
        attacker: CombatActor,
        defender: CombatActor,
        preferMagic: Boolean?,
        telemetry: MutableCombatTelemetry,
        actionMultiplier: Double = 1.0,
        actionName: String? = null,
        extraOnHitStatuses: List<CombatStatusApplyDef> = emptyList(),
        extraBonuses: Bonuses = Bonuses(),
        selfHealFlat: Double = 0.0,
        selfHealPctMaxHp: Double = 0.0,
        skillRank: Int = 1,
        aoeUnlockRank: Int = 0,
        aoeBonusDamagePct: Double = 0.0
    ): SkillResolutionResult {
        val attackStats = applyTransientBonuses(attacker.stats, extraBonuses)
        val result = Combat.attack(attackStats, defender.stats, rng, preferMagic = preferMagic)
        val conditionalStatusMultiplier = statusConditionalDamageMultiplier(attacker, defender)
        val finalMultiplier = (
            attacker.runtime.statusDamageMultiplier *
                attacker.runtime.tempBuffDamageMultiplier *
                actionMultiplier *
                conditionalStatusMultiplier
            ).coerceAtLeast(0.1)
        val affinityMultiplier = if (result.hit) {
            directDamageAffinityMultiplier(defender, result.type)
        } else {
            1.0
        }
        val scaledDamage = if (result.hit) {
            (result.damage * finalMultiplier * affinityMultiplier).coerceAtLeast(1.0)
        } else {
            0.0
        }
        val scaledLifesteal = if (result.hit && result.damage > 0.0) {
            (result.lifesteal * (scaledDamage / result.damage)).coerceAtLeast(0.0)
        } else {
            0.0
        }
        if (result.hit && scaledDamage > 0.0) {
            if (attacker.kind == CombatantKind.PLAYER) {
                telemetry.playerDamageDealt += scaledDamage
            } else {
                telemetry.playerDamageTaken += scaledDamage
            }
        }
        if (attacker.kind == CombatantKind.PLAYER) {
            if (result.hit) {
                val critLabel = if (result.crit) " CRITICO!" else ""
                val typeLabel = if (result.type == rpg.engine.DamageType.MAGIC) "magico" else "fisico"
                val abilityLabel = if (!actionName.isNullOrBlank()) " com $actionName" else ""
                val affinityLabel = when {
                    affinityMultiplier >= 1.08 -> " Fraqueza explorada!"
                    affinityMultiplier <= 0.92 -> " O inimigo resistiu."
                    else -> ""
                }
                combatLog(
                    colorize(
                        "Voce causou ${format(scaledDamage)} de dano $typeLabel$abilityLabel.$critLabel$affinityLabel",
                        ansiCyan
                    )
                )
            } else {
                if (actionName.isNullOrBlank()) {
                    combatLog(colorize("Voce errou!", ansiYellow))
                } else {
                    combatLog(colorize("Voce usou $actionName, mas errou!", ansiYellow))
                }
            }
        } else {
            if (result.hit) {
                val critLabel = if (result.crit) " CRITICO!" else ""
                val typeLabel = if (result.type == rpg.engine.DamageType.MAGIC) "magico" else "fisico"
                val abilityLabel = if (!actionName.isNullOrBlank()) " com $actionName" else ""
                combatLog(colorize("O inimigo causou ${format(scaledDamage)} de dano $typeLabel$abilityLabel.$critLabel", ansiRed))
            } else {
                combatLog(colorize("O inimigo errou!", ansiYellow))
            }
        }

        var statusAppliedCount = 0
        var carryBonusPct = 0.0
        var targetDefeated = false
        if (result.hit) {
            defender.currentHp = max(0.0, defender.currentHp - scaledDamage)
            if (skillRank >= aoeUnlockRank.coerceAtLeast(1) && aoeBonusDamagePct > 0.0) {
                val splash = (scaledDamage * (aoeBonusDamagePct / 100.0)).coerceAtLeast(0.0)
                if (splash > 0.0 && defender.currentHp > 0.0) {
                    defender.currentHp = (defender.currentHp - splash).coerceAtLeast(0.0)
                    if (attacker.kind == CombatantKind.PLAYER) {
                        combatLog(colorize("Impacto em area: ${format(splash)} de dano adicional.", ansiCyan))
                    }
                }
            }
            if (defender.currentHp < 0.05) {
                defender.currentHp = 0.0
            }
            if (defender.currentHp <= 0.0) {
                targetDefeated = true
                defender.runtime = defender.runtime.copy(
                    state = CombatState.DEAD,
                    readySinceSeconds = null
                )
            }
            if (scaledLifesteal > 0.0) {
                attacker.currentHp = (attacker.currentHp + scaledLifesteal)
                    .coerceAtMost(attacker.stats.derived.hpMax)
            }
            val manaOnHit = attacker.talentModifiers.atb.manaOnHitFlat +
                attacker.stats.derived.mpMax * (attacker.talentModifiers.atb.manaOnHitPctMax / 100.0)
            if (manaOnHit > 0.0) {
                attacker.currentMp = (attacker.currentMp + manaOnHit).coerceAtMost(attacker.stats.derived.mpMax)
            }
            maybeApplyReflectDamage(
                attacker = attacker,
                defender = defender,
                dealtDamage = scaledDamage,
                telemetry = telemetry
            )
            applyActionBarShiftOnHit(attacker, defender)
            applyActionBarGainOnDamaged(defender)
            maybeInterruptCast(attacker, defender)
            statusAppliedCount = applyOnHitStatuses(attacker, defender, extraOnHitStatuses)
            if (result.crit) {
                if (attacker.kind == CombatantKind.PLAYER) {
                    telemetry.playerCriticalHits += 1
                }
                carryBonusPct += attacker.talentModifiers.atb.actionBarGainOnCritPct
            }
            if (statusAppliedCount > 0) {
                carryBonusPct += attacker.talentModifiers.atb.actionBarGainOnStatusApplyPct * statusAppliedCount
                applyTemporaryBuffOnStatusApplied(attacker, statusAppliedCount)
            }
            if (targetDefeated) {
                applyCooldownReductionOnKill(attacker)
            }
            consumeTargetStatusesOnHit(attacker, defender)
        }

        val skillHeal = selfHealFlat.coerceAtLeast(0.0) +
            attacker.stats.derived.hpMax * (selfHealPctMaxHp.coerceAtLeast(0.0) / 100.0)
        if (skillHeal > 0.0 && attacker.currentHp > 0.0) {
            val before = attacker.currentHp
            attacker.currentHp = (attacker.currentHp + skillHeal).coerceAtMost(attacker.stats.derived.hpMax)
            val healed = attacker.currentHp - before
            if (healed > 0.0) {
                val subject = if (attacker.kind == CombatantKind.PLAYER) "Voce" else "O inimigo"
                combatLog(colorize("$subject recuperou ${format(healed)} de HP.", ansiGreen))
            }
        }
        return SkillResolutionResult(
            hit = result.hit,
            crit = result.crit,
            targetDefeated = targetDefeated,
            statusesApplied = statusAppliedCount,
            carryBonusPct = carryBonusPct
        )
    }

    private fun applyCooldownReductionOnKill(attacker: CombatActor) {
        val reduction = attacker.talentModifiers.atb.cooldownReductionOnKillSeconds.coerceAtLeast(0.0)
        if (reduction <= 0.0) return
        if (attacker.runtime.skillCooldowns.isEmpty()) return
        attacker.runtime = attacker.runtime.copy(
            skillCooldowns = attacker.runtime.skillCooldowns.mapValues { (_, seconds) ->
                (seconds - reduction).coerceAtLeast(0.0)
            }.filterValues { it > 0.0 }
        )
        combatLog(colorize("Talento: abate reduziu cooldowns em ${format(reduction)}s.", ansiBlue))
    }

    private fun applyTemporaryBuffOnStatusApplied(attacker: CombatActor, appliedCount: Int) {
        if (appliedCount <= 0) return
        val atb = attacker.talentModifiers.atb
        val duration = atb.tempBuffDurationSeconds.coerceAtLeast(0.0)
        if (duration <= 0.0) return
        val damagePct = atb.tempDamageBuffOnStatusApplyPct * appliedCount
        val fillPct = atb.tempFillRateBuffOnStatusApplyPct * appliedCount
        if (damagePct <= 0.0 && fillPct <= 0.0) return

        val damageMult = (1.0 + damagePct / 100.0).coerceIn(1.0, 5.0)
        val fillMult = (1.0 + fillPct / 100.0).coerceIn(1.0, 5.0)
        val current = attacker.runtime
        attacker.runtime = current.copy(
            tempBuffRemainingSeconds = max(current.tempBuffRemainingSeconds, duration),
            tempBuffDamageMultiplier = max(current.tempBuffDamageMultiplier, damageMult),
            tempBuffFillRateMultiplier = max(current.tempBuffFillRateMultiplier, fillMult)
        )
        combatLog(
            colorize(
                "Talento: bonus temporario ativado por aplicacao de status (${format(duration)}s).",
                ansiBlue
            )
        )
    }

    private fun maybeApplyReflectDamage(
        attacker: CombatActor,
        defender: CombatActor,
        dealtDamage: Double,
        telemetry: MutableCombatTelemetry
    ) {
        if (dealtDamage <= 0.0) return
        if (attacker.currentHp <= 0.0 || attacker.runtime.state == CombatState.DEAD) return
        val reflectPct = defender.talentModifiers.status.reflectDamagePct.coerceIn(0.0, 90.0)
        if (reflectPct <= 0.0) return

        val reflectedDamage = (dealtDamage * (reflectPct / 100.0)).coerceAtLeast(0.0)
        if (reflectedDamage <= 0.0) return

        attacker.currentHp = (attacker.currentHp - reflectedDamage).coerceAtLeast(0.0)
        if (attacker.kind == CombatantKind.PLAYER) {
            telemetry.playerDamageTaken += reflectedDamage
        } else {
            telemetry.playerDamageDealt += reflectedDamage
        }
        if (attacker.currentHp < 0.05) {
            attacker.currentHp = 0.0
        }
        if (attacker.currentHp <= 0.0) {
            attacker.runtime = attacker.runtime.copy(
                state = CombatState.DEAD,
                readySinceSeconds = null
            )
        }

        val subject = subjectLabel(attacker)
        combatLog(colorize("Reflexo: $subject sofreu ${format(reflectedDamage)} de dano.", ansiBlue))
    }

    private fun useItem(
        selfActor: CombatActor,
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemId: String,
        currentStatuses: List<rpg.status.StatusEffectInstance>,
        currentImmunitySeconds: Double
    ): UseItemResult {
        val item = itemResolver.resolve(itemId, itemInstances)
            ?: return UseItemResult(player, itemInstances, currentStatuses)
        if (item.type != ItemType.CONSUMABLE) return UseItemResult(player, itemInstances, currentStatuses)

        val stats = statsEngine.computePlayerStats(player, itemInstances)
        val hpPct = stats.derived.hpMax * (item.effects.hpRestorePct / 100.0)
        val mpPct = stats.derived.mpMax * (item.effects.mpRestorePct / 100.0)
        var hpRestored = item.effects.hpRestore + hpPct
        var mpRestored = item.effects.mpRestore + mpPct
        if (item.effects.fullRestore) {
            hpRestored = stats.derived.hpMax
            mpRestored = stats.derived.mpMax
        }

        var healed = applyHealing(player, hpRestored, mpRestored, stats)
        if (item.effects.roomAttributeMultiplierPct != 0.0 && item.effects.roomAttributeDurationRooms > 0) {
            val mult = (1.0 + item.effects.roomAttributeMultiplierPct / 100.0).coerceAtLeast(0.1)
            healed = healed.copy(
                roomEffectMultiplier = (healed.roomEffectMultiplier * mult).coerceAtLeast(0.1),
                roomEffectRooms = max(healed.roomEffectRooms, item.effects.roomAttributeDurationRooms)
            )
        }
        if (item.effects.runAttributeMultiplierPct != 0.0) {
            val mult = (1.0 + item.effects.runAttributeMultiplierPct / 100.0).coerceAtLeast(0.1)
            healed = healed.copy(runAttrMultiplier = (healed.runAttrMultiplier * mult).coerceAtLeast(0.1))
        }

        val inventory = healed.inventory.toMutableList()
        inventory.remove(itemId)

        val updatedInstances = if (itemInstances.containsKey(itemId)) {
            itemInstances - itemId
        } else {
            itemInstances
        }

        var statuses = currentStatuses
        if (item.effects.clearNegativeStatuses) {
            statuses = emptyList()
            combatLog(colorize("Status negativos removidos por ${item.name}.", ansiGreen))
        }
        for (statusDef in item.effects.applyStatuses) {
            val tunedStatusDef = tuneStatusApplication(
                base = statusDef,
                sourceModifiers = selfActor.talentModifiers,
                targetModifiers = selfActor.talentModifiers
            )
            val applied = StatusSystem.applyStatus(
                current = statuses,
                application = tunedStatusDef,
                rng = rng,
                defaultSource = item.name
            )
            statuses = applied.statuses
            if (applied.applied) {
                val source = statusDef.source.ifBlank { item.name }
                val sourceSuffix = if (source.isBlank()) "" else " ($source)"
                combatLog(
                    colorize(
                        "Voce esta ${StatusSystem.statusAdjective(statusDef.type)}$sourceSuffix.",
                        ansiGreen
                    )
                )
            }
        }

        val immunitySeconds = if (item.effects.statusImmunitySeconds > 0.0) {
            max(currentImmunitySeconds, item.effects.statusImmunitySeconds)
        } else {
            currentImmunitySeconds
        }
        if (item.effects.statusImmunitySeconds > 0.0) {
            combatLog(
                colorize(
                    "Imunidade a status ativa por ${format(item.effects.statusImmunitySeconds)}s.",
                    ansiGreen
                )
            )
        }

        combatLog(colorize("Usou ${item.name}.", ansiGreen))
        return UseItemResult(
            player = healed.copy(inventory = inventory),
            itemInstances = updatedInstances,
            statuses = statuses,
            statusImmunitySeconds = immunitySeconds
        )
    }

    private fun applyHealing(player: PlayerState, hpDelta: Double, mpDelta: Double, stats: ComputedStats): PlayerState {
        val multiplier = player.nextHealMultiplier
        val newHp = (player.currentHp + hpDelta * multiplier).coerceAtMost(stats.derived.hpMax)
        val newMp = (player.currentMp + mpDelta * multiplier).coerceAtMost(stats.derived.mpMax)
        val consumed = multiplier != 1.0 && (hpDelta > 0.0 || mpDelta > 0.0)
        return if (consumed) {
            player.copy(currentHp = newHp, currentMp = newMp, nextHealMultiplier = 1.0)
        } else {
            player.copy(currentHp = newHp, currentMp = newMp)
        }
    }

    private fun applyReviveIfNeeded(player: PlayerState, actor: CombatActor): PlayerState {
        if (player.currentHp > 0.0 || !player.reviveOnce) return player
        combatLog("Uma segunda chance silenciosa. Voce resiste com 1 HP.")
        actor.currentHp = 1.0
        actor.runtime = actor.runtime.copy(state = CombatState.IDLE)
        return player.copy(currentHp = 1.0, reviveOnce = false)
    }

    private fun syncPlayerHp(player: PlayerState, actor: CombatActor): PlayerState {
        return player.copy(currentHp = actor.currentHp, currentMp = actor.currentMp)
    }

    private fun rollEscape(playerStats: ComputedStats, monsterStats: ComputedStats): EscapeAttempt {
        val playerEscapeScore =
            playerStats.attributes.agi * 0.5 +
                playerStats.attributes.dex * 0.3 +
                playerStats.attributes.luk * 0.2
        val enemyChaseScore =
            monsterStats.attributes.agi * 0.5 +
                monsterStats.attributes.dex * 0.3 +
                monsterStats.attributes.luk * 0.2
        val chancePct = (40.0 + (playerEscapeScore - enemyChaseScore) * 1.6).coerceIn(5.0, 95.0)
        val success = rng.nextDouble(0.0, 100.0) <= chancePct
        return EscapeAttempt(success = success, chancePct = chancePct)
    }

    private fun effectiveCooldownSeconds(
        baseCooldownSeconds: Double,
        cdrPct: Double,
        cooldownMultiplier: Double = 1.0
    ): Double {
        if (baseCooldownSeconds <= 0.0) return 0.0
        val multiplier = (1.0 - cdrPct.coerceIn(0.0, 90.0) / 100.0).coerceAtLeast(0.1)
        val talentMultiplier = cooldownMultiplier.coerceIn(0.1, 5.0)
        return (baseCooldownSeconds * multiplier * talentMultiplier).coerceAtLeast(0.1)
    }

    private fun applySkillCooldown(actor: CombatActor, spec: CombatSkillSpec) {
        if (spec.cooldownSeconds <= 0.0) return
        val effectiveCd = effectiveCooldownSeconds(
            baseCooldownSeconds = spec.cooldownSeconds,
            cdrPct = actor.stats.derived.cdrPct,
            cooldownMultiplier = actor.talentModifiers.atb.cooldownMultiplier
        )
        actor.runtime = actor.runtime.copy(
            skillCooldowns = actor.runtime.skillCooldowns + (spec.id to effectiveCd)
        )
    }

    private fun effectiveCastTime(actor: CombatActor, baseCastSeconds: Double): Double {
        if (baseCastSeconds <= 0.0) return 0.0
        return (baseCastSeconds * actor.talentModifiers.atb.castTimeMultiplier).coerceAtLeast(0.0)
    }

    private fun decrementCooldowns(cooldowns: Map<String, Double>, deltaTime: Double): Map<String, Double> {
        if (cooldowns.isEmpty()) return emptyMap()
        val updated = mutableMapOf<String, Double>()
        for ((id, seconds) in cooldowns) {
            val remain = (seconds - deltaTime).coerceAtLeast(0.0)
            if (remain > 0.0) {
                updated[id] = remain
            }
        }
        return updated
    }

    private fun rangedAmmoRequirementReason(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): String? {
        if (!playerUsesBowAmmo(player, itemInstances)) return null
        if (player.equipped[EquipSlot.ALJAVA.name].isNullOrBlank()) {
            return "Voce precisa equipar uma aljava para usar arcos."
        }
        val normalizedPlayer = InventorySystem.normalizeAmmoStorage(player, itemInstances, itemRegistry)
        if (InventorySystem.quiverAmmoCount(normalizedPlayer, itemInstances, itemRegistry) <= 0) {
            return "Voce esta sem flechas na aljava."
        }
        return null
    }

    private fun playerUsesBowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): Boolean {
        val mainWeaponId = player.equipped[EquipSlot.WEAPON_MAIN.name] ?: return false
        val weapon = itemResolver.resolve(mainWeaponId, itemInstances) ?: return false
        val normalizedTags = weapon.tags.mapTo(mutableSetOf()) { it.trim().lowercase() }
        if ("bow" in normalizedTags) return true
        val source = "${weapon.id} ${weapon.name}".lowercase()
        return "bow" in source || "arco" in source
    }

    private fun consumeArrowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        amount: Int = 1
    ): rpg.inventory.ArrowConsumeResult? {
        if (!playerUsesBowAmmo(player, itemInstances)) return null
        val consumed = InventorySystem.consumeArrowAmmo(
            player = player,
            itemInstances = itemInstances,
            itemRegistry = itemRegistry,
            amount = amount
        ) ?: return null
        val arrowLabel = if (consumed.consumedArrowIds.size == 1) {
            itemResolver.resolve(consumed.consumedArrowIds.first(), itemInstances)?.name ?: "Flecha"
        } else {
            "${consumed.consumedArrowIds.size} flecha(s)"
        }
        combatLog(colorize("Municao consumida: $arrowLabel.", ansiBlue))
        return consumed
    }

    private fun previewAmmoPayload(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        amount: Int
    ): AmmoPayload {
        if (!playerUsesBowAmmo(player, itemInstances)) return AmmoPayload()
        val ammoIds = InventorySystem.peekArrowAmmo(
            player = player,
            itemInstances = itemInstances,
            itemRegistry = itemRegistry,
            amount = amount
        )
        if (ammoIds.isEmpty()) return AmmoPayload()
        var bonuses = Bonuses()
        val statuses = mutableListOf<CombatStatusApplyDef>()
        val labelCounts = linkedMapOf<String, Int>()
        for (ammoId in ammoIds) {
            val ammo = itemResolver.resolve(ammoId, itemInstances) ?: continue
            bonuses += ammo.bonuses
            statuses += ammo.effects.applyStatuses
            labelCounts[ammo.name] = (labelCounts[ammo.name] ?: 0) + 1
        }
        val label = labelCounts.entries.joinToString(", ") { (name, qty) ->
            if (qty > 1) "$name x$qty" else name
        }
        return AmmoPayload(bonuses = bonuses, statuses = statuses, label = label)
    }

    private fun applyTransientBonuses(stats: ComputedStats, bonuses: Bonuses): ComputedStats {
        if (bonuses == Bonuses()) return stats
        val attributes = stats.attributes + bonuses.attributes
        val derived = (stats.derived + bonuses.derivedAdd).applyMultiplier(bonuses.derivedMult)
        return ComputedStats(attributes = attributes, derived = derived)
    }

    private fun startCasting(
        actor: CombatActor,
        action: CombatAction,
        castTime: Double,
        afterResolve: (() -> Unit)? = null
    ) {
        actor.pendingAction = action
        actor.pendingAfterResolve = afterResolve
        actor.runtime = actor.runtime.copy(
            state = CombatState.CASTING,
            castRemaining = castTime,
            castTotal = castTime,
            readySinceSeconds = null,
            currentSkillId = action.javaClass.simpleName
        )
    }

    private fun endAction(actor: CombatActor, actionBarCarryPct: Double = 0.0) {
        actor.pendingAction = null
        actor.pendingAfterResolve = null
        val carryPct = actionBarCarryPct.coerceIn(0.0, 95.0)
        val threshold = actor.runtime.actionThreshold.coerceAtLeast(1.0)
        val carryValue = threshold * (carryPct / 100.0)
        val gcdSeconds = (
            balance.combat.globalCooldownSeconds *
                actor.talentModifiers.atb.gcdMultiplier
            ).coerceAtLeast(0.05)
        actor.runtime = actor.runtime.copy(
            actionBar = carryValue,
            gcdRemaining = gcdSeconds,
            castRemaining = 0.0,
            castTotal = 0.0,
            readySinceSeconds = null,
            state = CombatState.IDLE,
            currentSkillId = null
        )
    }

    private fun computeFillRate(actor: CombatActor): Double {
        if (actor.currentHp <= 0.0 || actor.runtime.state == CombatState.DEAD) return 0.0
        val baseSpeed = actor.stats.derived.attackSpeed
        val speedRating = baseSpeed *
            balance.combat.speedScale *
            (1.0 + actor.speedBonusPct / 100.0) *
            actor.talentModifiers.atb.fillRateMultiplier *
            actor.talentModifiers.atb.hasteMultiplier *
            actor.runtime.statusSpeedMultiplier *
            actor.runtime.tempBuffFillRateMultiplier
        val effectiveSpeed = applySoftCap(speedRating)
        return effectiveSpeed / balance.combat.speedNormalization
    }

    private fun applySoftCap(speed: Double): Double {
        val softCap = balance.combat.softCap
        return when {
            speed <= softCap.threshold1 -> speed
            speed <= softCap.threshold2 -> {
                softCap.threshold1 + (speed - softCap.threshold1) * softCap.multiplier1
            }

            else -> {
                val first = softCap.threshold1
                val second = (softCap.threshold2 - softCap.threshold1) * softCap.multiplier1
                val rest = (speed - softCap.threshold2) * softCap.multiplier2
                first + second + rest
            }
        }
    }

    private fun applyStatusEffect(actor: CombatActor, deltaTime: Double): rpg.status.StatusTickResult {
        val tick = StatusSystem.tick(
            current = actor.runtime.statuses,
            deltaSeconds = deltaTime,
            targetMaxHp = actor.stats.derived.hpMax,
            slowEffectMultiplier = actor.talentModifiers.atb.slowReceivedMultiplier
        )
        actor.runtime = actor.runtime.copy(
            statuses = tick.statuses,
            statusSpeedMultiplier = tick.speedMultiplier,
            statusDamageMultiplier = tick.damageMultiplier
        )
        return tick
    }

    private fun applyMonsterAffinityToStatusTick(
        actor: CombatActor,
        tick: rpg.status.StatusTickResult
    ): rpg.status.StatusTickResult {
        if (tick.damageEvents.isEmpty()) return tick
        if (actor.kind != CombatantKind.MONSTER) return tick
        if (monsterAffinityService == null) return tick

        val adjustedEvents = tick.damageEvents.map { event ->
            val channel = DamageChannel.fromStatusType(event.type)
            val multiplier = if (channel == null) {
                1.0
            } else {
                resolveMonsterAffinityMultiplier(actor, channel)
            }
            event.copy(damage = (event.damage * multiplier).coerceAtLeast(0.0))
        }
        val totalDamage = adjustedEvents.sumOf { it.damage }
        return tick.copy(
            dotDamage = totalDamage,
            damageEvents = adjustedEvents
        )
    }

    private fun directDamageAffinityMultiplier(
        defender: CombatActor,
        type: rpg.engine.DamageType
    ): Double {
        if (defender.kind != CombatantKind.MONSTER) return 1.0
        val channel = if (type == rpg.engine.DamageType.MAGIC) {
            DamageChannel.MAGIC
        } else {
            DamageChannel.PHYSICAL
        }
        return resolveMonsterAffinityMultiplier(defender, channel)
    }

    private fun resolveMonsterAffinityMultiplier(
        defender: CombatActor,
        channel: DamageChannel
    ): Double {
        if (defender.kind != CombatantKind.MONSTER) return 1.0
        val service = monsterAffinityService ?: return 1.0
        val archetypeId = defender.monsterArchetypeId
            ?: defender.id.takeIf { it.isNotBlank() }
            ?: return 1.0
        return service.multiplierFor(
            archetypeId = archetypeId,
            typeIdHint = defender.monsterTypeId,
            tags = defender.monsterTags,
            channel = channel
        )
    }

    private fun archetypeCastTime(monster: MonsterInstance): Double {
        val archetype = archetypes[monster.sourceArchetypeId]
            ?: archetypes[monster.archetypeId]
        return archetype?.baseCastTime ?: 0.0
    }

    private fun collectPlayerOnHitStatuses(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): List<CombatStatusApplyDef> {
        val effects = mutableListOf<CombatStatusApplyDef>()
        for (itemId in player.equipped.values) {
            val resolved = itemResolver.resolve(itemId, itemInstances) ?: continue
            if (resolved.type != ItemType.EQUIPMENT) continue
            effects += resolved.effects.applyStatuses
        }
        return effects
    }

    private fun applyOnHitStatuses(
        attacker: CombatActor,
        defender: CombatActor,
        extraOnHitStatuses: List<CombatStatusApplyDef> = emptyList()
    ): Int {
        val applications = if (extraOnHitStatuses.isEmpty()) {
            attacker.onHitStatuses
        } else {
            attacker.onHitStatuses + extraOnHitStatuses
        }
        if (applications.isEmpty()) return 0
        if (defender.runtime.statusImmunitySeconds > 0.0) {
            val subject = subjectLabel(defender)
            combatLog(
                colorize(
                    "$subject esta imune a status por ${format(defender.runtime.statusImmunitySeconds)}s.",
                    ansiBlue
                )
            )
            return 0
        }
        var current = defender.runtime.statuses
        var appliedCount = 0
        for (application in applications) {
            val source = application.source.ifBlank { attacker.name }
            val tunedApplication = tuneStatusApplication(
                base = application,
                sourceModifiers = attacker.talentModifiers,
                targetModifiers = defender.talentModifiers
            )
            val applied = StatusSystem.applyStatus(
                current = current,
                application = tunedApplication,
                rng = rng,
                defaultSource = source
            )
            current = applied.statuses
            if (applied.applied) {
                appliedCount += 1
                val sourceSuffix = if (source.isBlank()) "" else " ($source)"
                val subject = subjectLabel(defender)
                combatLog(
                    colorize(
                        "$subject esta ${StatusSystem.statusAdjective(application.type)}$sourceSuffix.",
                        ansiYellow
                    )
                )
            }
        }
        defender.runtime = defender.runtime.copy(statuses = current)
        return appliedCount
    }

    private fun tuneStatusApplication(
        base: CombatStatusApplyDef,
        sourceModifiers: TalentCombatModifiers,
        targetModifiers: TalentCombatModifiers
    ): CombatStatusApplyDef {
        val sourceStatus = sourceModifiers.status
        val targetStatus = targetModifiers.status
        val type = base.type

        var chance = base.chancePct +
            sourceStatus.applyChanceBonusPct +
            (sourceStatus.applyChanceBonusByType[type] ?: 0.0)
        chance = chance.coerceIn(0.0, 100.0)

        var durationMultiplier = sourceStatus.durationMultiplier *
            (sourceStatus.durationMultiplierByType[type] ?: 1.0) *
            targetStatus.incomingDurationMultiplier *
            (targetStatus.incomingDurationMultiplierByType[type] ?: 1.0)

        if (type == StatusType.SLOW) {
            durationMultiplier *= sourceModifiers.atb.slowInflictedMultiplier
        }
        durationMultiplier = durationMultiplier.coerceIn(0.1, 5.0)
        val durationSeconds = (base.durationSeconds * durationMultiplier).coerceAtLeast(0.1)

        return base.copy(
            chancePct = chance,
            durationSeconds = durationSeconds
        )
    }

    private fun statusConditionalDamageMultiplier(attacker: CombatActor, defender: CombatActor): Double {
        val mods = attacker.talentModifiers.status
        if (mods.bonusDamageVsStatusPct.isEmpty() &&
            mods.bonusDamageWhileSelfHasStatusPct.isEmpty() &&
            mods.bonusDamageVsLowHpPct <= 0.0
        ) {
            return 1.0
        }

        var bonusPct = 0.0
        for ((type, pct) in mods.bonusDamageVsStatusPct) {
            if (hasStatus(defender.runtime.statuses, type)) {
                bonusPct += pct
            }
        }
        for ((type, pct) in mods.bonusDamageWhileSelfHasStatusPct) {
            if (hasStatus(attacker.runtime.statuses, type)) {
                bonusPct += pct
            }
        }
        val defenderMaxHp = defender.stats.derived.hpMax.coerceAtLeast(1.0)
        val defenderHpPct = (defender.currentHp / defenderMaxHp) * 100.0
        if (defenderHpPct <= mods.lowHpThresholdPct) {
            bonusPct += mods.bonusDamageVsLowHpPct
        }
        return (1.0 + bonusPct / 100.0).coerceIn(0.1, 5.0)
    }

    private fun consumeTargetStatusesOnHit(attacker: CombatActor, defender: CombatActor) {
        val consumeRules = attacker.talentModifiers.status.consumeChanceByType
        if (consumeRules.isEmpty() || defender.runtime.statuses.isEmpty()) return

        var current = defender.runtime.statuses
        var changed = false
        for ((type, chancePct) in consumeRules) {
            val clampedChance = chancePct.coerceIn(0.0, 100.0)
            if (clampedChance <= 0.0) continue
            val index = current.indexOfFirst { it.type == type }
            if (index < 0) continue
            if (rng.nextDouble(0.0, 100.0) > clampedChance) continue

            val (next, consumed) = consumeStatusStackAt(current, index)
            if (!consumed) continue
            current = next
            changed = true
            val subject = subjectLabel(defender)
            combatLog(
                colorize(
                    "$subject perdeu ${StatusSystem.displayName(type)} por consumo de efeito.",
                    ansiBlue
                )
            )
        }
        if (changed) {
            defender.runtime = defender.runtime.copy(statuses = current)
        }
    }

    private fun consumeStatusStackAt(
        statuses: List<rpg.status.StatusEffectInstance>,
        index: Int
    ): Pair<List<rpg.status.StatusEffectInstance>, Boolean> {
        if (index !in statuses.indices) return statuses to false
        val target = statuses[index]
        val mutable = statuses.toMutableList()
        if (target.stackable && target.stacks > 1) {
            mutable[index] = target.copy(stacks = target.stacks - 1)
        } else {
            mutable.removeAt(index)
        }
        return mutable to true
    }

    private fun applyActionBarShiftOnHit(attacker: CombatActor, defender: CombatActor) {
        val lossPct = attacker.talentModifiers.atb.actionBarLossOnHitPct.coerceIn(0.0, 95.0)
        if (lossPct <= 0.0) return
        if (defender.runtime.state == CombatState.DEAD) return

        val threshold = defender.runtime.actionThreshold.coerceAtLeast(1.0)
        val lossValue = threshold * (lossPct / 100.0)
        val newBar = (defender.runtime.actionBar - lossValue).coerceAtLeast(0.0)
        val shouldDropReady = defender.runtime.state == CombatState.READY && newBar < threshold
        val newState = if (shouldDropReady) CombatState.IDLE else defender.runtime.state
        defender.runtime = defender.runtime.copy(
            actionBar = newBar,
            state = newState,
            readySinceSeconds = if (shouldDropReady) null else defender.runtime.readySinceSeconds
        )
    }

    private fun applyActionBarGainOnDamaged(defender: CombatActor) {
        val gainPct = defender.talentModifiers.atb.actionBarGainOnDamagedPct.coerceIn(0.0, 95.0)
        if (gainPct <= 0.0) return
        if (defender.runtime.state == CombatState.DEAD) return
        if (defender.runtime.state != CombatState.IDLE && defender.runtime.state != CombatState.READY) return

        val threshold = defender.runtime.actionThreshold.coerceAtLeast(1.0)
        val gainValue = threshold * (gainPct / 100.0)
        val newBar = (defender.runtime.actionBar + gainValue).coerceAtMost(threshold)
        defender.runtime = defender.runtime.copy(actionBar = newBar)
    }

    private fun maybeInterruptCast(attacker: CombatActor, defender: CombatActor) {
        if (defender.runtime.state != CombatState.CASTING) return
        val chance = (
            attacker.talentModifiers.atb.interruptChanceBonusPct -
                defender.talentModifiers.atb.interruptResistPct
            ).coerceIn(0.0, 100.0)
        if (chance <= 0.0) return
        if (rng.nextDouble(0.0, 100.0) > chance) return

        val threshold = defender.runtime.actionThreshold.coerceAtLeast(1.0)
        val interruptedBar = (defender.runtime.actionBar - threshold * 0.5).coerceAtLeast(0.0)
        val interruptedGcd = max(
            defender.runtime.gcdRemaining,
            (balance.combat.globalCooldownSeconds * defender.talentModifiers.atb.gcdMultiplier).coerceAtLeast(0.05)
        )
        defender.pendingAction = null
        defender.pendingAfterResolve = null
        defender.runtime = defender.runtime.copy(
            state = CombatState.IDLE,
            castRemaining = 0.0,
            castTotal = 0.0,
            currentSkillId = null,
            readySinceSeconds = null,
            actionBar = interruptedBar,
            gcdRemaining = interruptedGcd
        )
        val subject = subjectLabel(defender)
        combatLog(colorize("Cast interrompido: $subject.", ansiYellow))
    }

    private fun hasStatus(
        statuses: List<rpg.status.StatusEffectInstance>,
        type: StatusType
    ): Boolean {
        return statuses.any { it.type == type }
    }

    private fun emitStatusTickMessages(actor: CombatActor, tick: rpg.status.StatusTickResult) {
        val subject = subjectLabel(actor)
        for (event in tick.damageEvents) {
            val sourceSuffix = if (event.source.isBlank()) "" else " (${event.source})"
            combatLog(
                colorize(
                    "$subject esta ${StatusSystem.statusAdjective(event.type)}$sourceSuffix. Sofreu ${format(event.damage)} de dano.",
                    ansiYellow
                )
            )
        }
        for (event in tick.expiredEvents) {
            combatLog(
                colorize(
                    "$subject nao esta mais ${StatusSystem.statusAdjective(event.type)}.",
                    ansiBlue
                )
            )
        }
    }

    private fun buildSnapshot(
        player: PlayerState,
        playerStats: ComputedStats,
        monster: MonsterInstance,
        monsterStats: ComputedStats,
        monsterHp: Double,
        itemInstances: Map<String, ItemInstance>,
        playerActor: CombatActor,
        monsterActor: CombatActor,
        pausedForDecision: Boolean
    ): CombatSnapshot {
        return CombatSnapshot(
            player = player.copy(
                currentHp = playerActor.currentHp,
                currentMp = playerActor.currentMp
            ),
            playerStats = playerStats,
            monster = monster,
            monsterStats = monsterStats,
            monsterHp = monsterHp,
            itemInstances = itemInstances,
            playerRuntime = playerActor.runtime,
            monsterRuntime = monsterActor.runtime,
            playerFillRate = computeFillRate(playerActor),
            monsterFillRate = computeFillRate(monsterActor),
            pausedForDecision = pausedForDecision
        )
    }

    private fun subjectLabel(actor: CombatActor): String = if (actor.kind == CombatantKind.PLAYER) {
        "Voce"
    } else {
        "O inimigo"
    }

    private fun format(value: Double): String = "%.1f".format(value)

    private fun combatLog(message: String) {
        activeLogger?.invoke(message) ?: kotlin.io.println(message)
    }

    private fun colorize(text: String, colorCode: String): String = "$colorCode$text$ansiReset"

    private data class UseItemResult(
        val player: PlayerState,
        val itemInstances: Map<String, ItemInstance>,
        val statuses: List<rpg.status.StatusEffectInstance> = emptyList(),
        val statusImmunitySeconds: Double = 0.0
    )

    private data class AmmoPayload(
        val bonuses: Bonuses = Bonuses(),
        val statuses: List<CombatStatusApplyDef> = emptyList(),
        val label: String = ""
    )

    private data class SkillResolutionResult(
        val hit: Boolean,
        val crit: Boolean = false,
        val targetDefeated: Boolean = false,
        val statusesApplied: Int = 0,
        val carryBonusPct: Double = 0.0
    )

    private data class ActionOutcome(
        val escaped: Boolean = false,
        val consumedReady: Boolean = true
    )

    private data class EscapeAttempt(
        val success: Boolean,
        val chancePct: Double
    )

    private companion object {
        private const val ansiReset = "\u001B[0m"
        private const val ansiRed = "\u001B[31m"
        private const val ansiGreen = "\u001B[32m"
        private const val ansiYellow = "\u001B[33m"
        private const val ansiBlue = "\u001B[34m"
        private const val ansiCyan = "\u001B[36m"
    }
}

