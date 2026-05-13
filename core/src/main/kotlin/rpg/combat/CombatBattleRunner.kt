package rpg.combat

import kotlin.math.floor
import rpg.engine.ComputedStats
import rpg.engine.StatsEngine
import rpg.achievement.MonsterTypeMasteryService
import rpg.model.BiomeDef
import rpg.model.DerivedStats
import rpg.model.GameBalanceDef
import rpg.model.ItemInstance
import rpg.model.MapTierDef
import rpg.model.MonsterArchetypeDef
import rpg.model.PlayerState
import rpg.model.TalentTree
import rpg.monster.MonsterBehaviorEngine
import rpg.monster.MonsterInstance
import rpg.talent.TalentCombatIntegrationService
import rpg.talent.TalentCombatModifiers

internal class CombatBattleRunner(
    private val statsEngine: StatsEngine,
    private val behaviorEngine: MonsterBehaviorEngine,
    private val balance: GameBalanceDef,
    private val biomes: Map<String, BiomeDef>,
    private val archetypes: Map<String, MonsterArchetypeDef>,
    private val talentTrees: Collection<TalentTree>,
    private val talentCombatIntegrationService: TalentCombatIntegrationService,
    private val turnResolver: CombatTurnResolver,
    private val statusProcessor: CombatStatusProcessor,
    private val damageResolver: CombatDamageResolver,
    private val playerSupportService: CombatPlayerSupportService,
    private val actionExecutor: CombatActionExecutor,
    private val combatLog: (String) -> Unit
) {
    fun runBattle(
        playerState: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        monster: MonsterInstance,
        tier: MapTierDef,
        displayName: String,
        controller: PlayerCombatController,
        rules: CombatRules
    ): CombatResult {
        var player = playerState
        var instances = itemInstances
        var playerStats = statsEngine.computePlayerStats(player, instances)

        val baseMonsterStats = rpg.engine.StatsCalculator.compute(monster.attributes, listOf(monster.bonuses))
        var currentMonsterStats = baseMonsterStats

        var summonsUsed = 0
        var enrageTurns = 0
        var enrageMultiplier = DerivedStats()
        var evolved = false
        var evolveMultiplier = DerivedStats()
        var combatElapsedSeconds = 0.0
        val telemetry = MutableCombatTelemetry()
        var globalBossScalePct = 0.0
        var resolvedActionCount = 0
        var trackedPlayerDamage = 0.0
        var lastObservedMonsterHp = 0.0

        fun telemetrySnapshot(): CombatTelemetry {
            val dealt = if (rules.mode == CombatMode.GLOBAL_BOSS) {
                kotlin.math.max(telemetry.playerDamageDealt, trackedPlayerDamage)
            } else {
                telemetry.playerDamageDealt
            }
            return CombatTelemetry(
                playerDamageDealt = dealt.coerceAtLeast(0.0),
                playerDamageTaken = telemetry.playerDamageTaken.coerceAtLeast(0.0),
                playerCriticalHits = telemetry.playerCriticalHits.coerceAtLeast(0)
            )
        }

        val playerTalentModifiers = talentCombatIntegrationService.collectForPlayer(
            player = player,
            trees = talentTrees
        )
        val monsterTypeDamageBonus = MonsterTypeMasteryService.trackedTypes
            .associateWith { type ->
                MonsterTypeMasteryService.damageBonusPctForType(
                    killsByType = player.lifetimeStats.killsByBaseType,
                    monsterTypeId = type
                )
            }
            .filterValues { it > 0.0 }
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
            monsterTypeDamageBonusPct = monsterTypeDamageBonus,
            onHitStatuses = statusProcessor.collectPlayerOnHitStatuses(player, instances),
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
        lastObservedMonsterHp = monsterActor.currentHp

        fun refreshMonsterStats(adjustHp: Boolean) {
            val oldMax = currentMonsterStats.derived.hpMax
            var derived = baseMonsterStats.derived
            if (rules.mode == CombatMode.GLOBAL_BOSS && globalBossScalePct > 0.0) {
                derived = derived.scale(1.0 + globalBossScalePct / 100.0)
            }
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

        fun keepGlobalBossAlive() {
            if (rules.mode != CombatMode.GLOBAL_BOSS) return
            if (monsterActor.currentHp > 0.0) return
            monsterActor.currentHp = 1.0
            monsterActor.runtime = monsterActor.runtime.copy(
                state = CombatState.IDLE,
                readySinceSeconds = null
            )
            lastObservedMonsterHp = monsterActor.currentHp
        }

        fun applyGlobalBossScalingIfNeeded() {
            if (rules.mode != CombatMode.GLOBAL_BOSS) return
            val turnSteps = if (rules.turnStepActions > 0 && rules.turnScalePctPerStep > 0.0) {
                resolvedActionCount / rules.turnStepActions
            } else {
                0
            }
            val damageSteps = if (rules.damageStep > 0.0 && rules.damageScalePctPerStep > 0.0) {
                floor(trackedPlayerDamage / rules.damageStep).toInt()
            } else {
                0
            }
            var nextScalePct = turnSteps * rules.turnScalePctPerStep + damageSteps * rules.damageScalePctPerStep
            if (rules.maxScalePct > 0.0) {
                nextScalePct = nextScalePct.coerceAtMost(rules.maxScalePct)
            }
            if (nextScalePct <= globalBossScalePct + 1e-6) return
            globalBossScalePct = nextScalePct.coerceAtLeast(0.0)
            refreshMonsterStats(adjustHp = false)
            combatLog("Boss global se fortalece: +${"%.1f".format(globalBossScalePct)}%.")
        }

        fun trackMonsterDamageDelta() {
            if (rules.mode != CombatMode.GLOBAL_BOSS) return
            val currentHp = monsterActor.currentHp.coerceAtLeast(0.0)
            if (currentHp < lastObservedMonsterHp) {
                trackedPlayerDamage += (lastObservedMonsterHp - currentHp)
            }
            lastObservedMonsterHp = currentHp
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

        fun syncPlayerState(updated: PlayerState, updatedInstances: Map<String, ItemInstance>) {
            player = updated
            instances = updatedInstances
            playerStats = statsEngine.computePlayerStats(player, instances)
            playerActor.stats = playerStats
            playerActor.currentHp = player.currentHp
            playerActor.currentMp = player.currentMp
            playerActor.onHitStatuses = statusProcessor.collectPlayerOnHitStatuses(player, instances)
        }

        fun applyActionDot(actor: CombatActor) {
            if (actor.currentHp <= 0.0 || actor.runtime.state == CombatState.DEAD) return
            val dot = statusProcessor.applyActionDot(actor)
            if (dot.totalDamage <= 0.0) return
            if (actor.kind == CombatantKind.PLAYER) {
                telemetry.playerDamageTaken += dot.totalDamage
            } else {
                telemetry.playerDamageDealt += dot.totalDamage
            }
            trackMonsterDamageDelta()
            keepGlobalBossAlive()
            applyGlobalBossScalingIfNeeded()
        }

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

        val tickSeconds = balance.combat.tickSeconds.coerceAtLeast(0.05)
        val tickMillis = (tickSeconds * 1000.0).toLong().coerceAtLeast(25L)
        var playerDecisionOpen = false

        controller.onFrame(snapshot(paused = false))
        while (playerActor.currentHp > 0.0 && monsterActor.currentHp > 0.0) {
            val tickStartNs = System.nanoTime()

            val pausedAtLoopStart = playerDecisionOpen && playerActor.runtime.state == CombatState.READY
            if (!pausedAtLoopStart) {
                turnResolver.advanceTick(
                    deltaTime = tickSeconds,
                    elapsedSeconds = combatElapsedSeconds,
                    player = playerActor,
                    monster = monsterActor,
                    onCastComplete = { caster, target ->
                        actionExecutor.handleCastComplete(
                            caster = caster,
                            target = target,
                            telemetry = telemetry,
                            playerState = player,
                            itemInstances = instances,
                            rules = rules,
                            onPlayerUpdate = { updated, updatedInstances ->
                                syncPlayerState(updated, updatedInstances)
                            }
                        )
                        applyActionDot(caster)
                        trackMonsterDamageDelta()
                        keepGlobalBossAlive()
                        applyGlobalBossScalingIfNeeded()
                    },
                    telemetry = telemetry
                )
                combatElapsedSeconds += tickSeconds
                trackMonsterDamageDelta()
                keepGlobalBossAlive()
                applyGlobalBossScalingIfNeeded()
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
                if (
                    !decisionPausedLoop &&
                    monsterActor.currentHp > 0.0 &&
                    monsterActor.runtime.state == CombatState.READY &&
                    monsterActor.runtime.gcdRemaining <= 0.0
                ) {
                    readyCandidates += monsterActor
                }
                if (
                    playerActor.currentHp > 0.0 &&
                    playerActor.runtime.state == CombatState.READY &&
                    playerActor.runtime.gcdRemaining <= 0.0 &&
                    pendingPlayerAction != null
                ) {
                    readyCandidates += playerActor
                }
                val actor = turnResolver.nextReadyActor(readyCandidates) ?: break
                if (actor.kind == CombatantKind.PLAYER) {
                    val action = pendingPlayerAction ?: break
                    pendingPlayerAction = null
                    val outcome = actionExecutor.processAction(
                        actor = playerActor,
                        target = monsterActor,
                        action = action,
                        playerState = player,
                        itemInstances = instances,
                        telemetry = telemetry,
                        rules = rules,
                        onPlayerUpdate = { updated, updatedInstances ->
                            syncPlayerState(updated, updatedInstances)
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
                    if (outcome.consumedReady && playerActor.runtime.state != CombatState.CASTING) {
                        applyActionDot(playerActor)
                    }
                    if (outcome.consumedReady) {
                        resolvedActionCount += 1
                    }
                    trackMonsterDamageDelta()
                    keepGlobalBossAlive()
                    applyGlobalBossScalingIfNeeded()
                    if (playerActor.currentHp <= 0.0 || monsterActor.currentHp <= 0.0) {
                        break
                    }
                    if (outcome.consumedReady && playerDecisionOpen) {
                        playerDecisionOpen = false
                        controller.onDecisionEnded()
                    }
                } else {
                    val outcome = actionExecutor.processMonsterAction(
                        actor = monsterActor,
                        target = playerActor,
                        castTime = archetypeCastTime(monster),
                        onMonsterBehavior = { handleMonsterBehavior() },
                        onSummon = {
                            if (behaviorEngine.rollSummon(monster.tags, summonsUsed, summonBonus)) {
                                summonsUsed++
                                combatLog("$displayName invoca reforços! Um ataque extra ocorre.")
                                damageResolver.resolveSkill(
                                    attacker = monsterActor,
                                    defender = playerActor,
                                    preferMagic = monsterActor.preferMagic,
                                    telemetry = telemetry
                                )
                                player = playerSupportService.applyReviveIfNeeded(
                                    playerSupportService.syncPlayerHp(player, playerActor),
                                    playerActor
                                )
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
                        rules = rules,
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
                    if (monsterActor.runtime.state != CombatState.CASTING) {
                        applyActionDot(monsterActor)
                    }
                    resolvedActionCount += 1
                    trackMonsterDamageDelta()
                    keepGlobalBossAlive()
                    applyGlobalBossScalingIfNeeded()
                    if (playerActor.currentHp <= 0.0 || monsterActor.currentHp <= 0.0) {
                        break
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
    }

    private fun archetypeCastTime(monster: MonsterInstance): Double {
        val archetype = archetypes[monster.sourceArchetypeId]
            ?: archetypes[monster.archetypeId]
        return archetype?.baseCastTime ?: 0.0
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
            playerFillRate = turnResolver.computeFillRate(playerActor),
            monsterFillRate = turnResolver.computeFillRate(monsterActor),
            pausedForDecision = pausedForDecision
        )
    }
}

