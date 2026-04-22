package rpg.combat

import kotlin.random.Random
import rpg.engine.ComputedStats
import rpg.engine.StatsEngine
import rpg.item.ItemResolver
import rpg.model.BiomeDef
import rpg.model.Bonuses
import rpg.model.CombatStatusApplyDef
import rpg.model.GameBalanceDef
import rpg.model.ItemInstance
import rpg.model.MapTierDef
import rpg.model.MonsterArchetypeDef
import rpg.model.PlayerState
import rpg.model.TalentTree
import rpg.monster.MonsterAffinityService
import rpg.monster.MonsterBehaviorEngine
import rpg.monster.MonsterInstance
import rpg.registry.ItemRegistry
import rpg.status.StatusEffectInstance
import rpg.talent.TalentCombatIntegrationService
import rpg.talent.TalentTreeService

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
    private val logBuilder = CombatLogBuilder { message -> activeLogger?.invoke(message) }
    private val statusProcessor = CombatStatusProcessor(
        rng = rng,
        balance = balance,
        itemResolver = itemResolver,
        monsterAffinityService = monsterAffinityService,
        logBuilder = logBuilder
    )
    private val turnResolver = CombatTurnResolver(
        balance = balance,
        statusProcessor = statusProcessor
    )
    private val ammoService = CombatAmmoService(
        itemResolver = itemResolver,
        itemRegistry = itemRegistry,
        logBuilder = logBuilder
    )
    private val damageResolver = CombatDamageResolver(
        rng = rng,
        logBuilder = logBuilder,
        statusProcessor = statusProcessor
    )
    private val playerSupportService = CombatPlayerSupportService(
        statsEngine = statsEngine,
        itemResolver = itemResolver,
        rng = rng,
        statusProcessor = statusProcessor,
        logBuilder = logBuilder
    )
    private val ansiYellow = CombatLogBuilder.ansiYellow
    private val ansiBlue = CombatLogBuilder.ansiBlue
    private val actionGateway = CombatActionGatewayAdapter(
        rng = rng,
        ansiYellow = ansiYellow,
        ansiBlue = ansiBlue,
        combatLogHandler = ::combatLog,
        colorizeHandler = ::colorize,
        formatHandler = ::format,
        effectiveCastTimeHandler = ::effectiveCastTime,
        applySkillCooldownHandler = ::applySkillCooldown,
        startCastingHandler = ::startCasting,
        endActionHandler = ::endAction,
        resolveSkillHandler = ::resolveSkill,
        useItemHandler = ::useItem,
        applyReviveIfNeededHandler = ::applyReviveIfNeeded,
        syncPlayerHpHandler = ::syncPlayerHp,
        rollEscapeHandler = ::rollEscape,
        rangedAmmoRequirementReasonHandler = ::rangedAmmoRequirementReason,
        playerUsesBowAmmoHandler = ::playerUsesBowAmmo,
        consumeArrowAmmoHandler = ::consumeArrowAmmo,
        previewAmmoPayloadHandler = ::previewAmmoPayload
    )
    private val actionExecutor = CombatActionExecutor(actionGateway)
    private val battleRunner = CombatBattleRunner(
        statsEngine = statsEngine,
        behaviorEngine = behaviorEngine,
        balance = balance,
        biomes = biomes,
        archetypes = archetypes,
        talentTrees = talentTrees,
        talentCombatIntegrationService = talentCombatIntegrationService,
        turnResolver = turnResolver,
        statusProcessor = statusProcessor,
        damageResolver = damageResolver,
        playerSupportService = playerSupportService,
        actionExecutor = actionExecutor,
        combatLog = ::combatLog
    )

    fun runBattle(
        playerState: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        monster: MonsterInstance,
        tier: MapTierDef,
        displayName: String,
        controller: PlayerCombatController,
        eventLogger: (String) -> Unit = {}
    ): CombatResult {
        val previousLogger = activeLogger
        activeLogger = eventLogger
        return try {
            battleRunner.runBattle(
                playerState = playerState,
                itemInstances = itemInstances,
                monster = monster,
                tier = tier,
                displayName = displayName,
                controller = controller
            )
        } finally {
            activeLogger = previousLogger
        }
    }

    private fun resolveSkill(
        attacker: CombatActor,
        defender: CombatActor,
        preferMagic: Boolean?,
        telemetry: MutableCombatTelemetry,
        actionMultiplier: Double,
        actionName: String?,
        extraOnHitStatuses: List<CombatStatusApplyDef>,
        extraBonuses: Bonuses,
        selfHealFlat: Double,
        selfHealPctMaxHp: Double,
        skillRank: Int,
        aoeUnlockRank: Int,
        aoeBonusDamagePct: Double
    ): SkillResolutionResult {
        return damageResolver.resolveSkill(
            attacker = attacker,
            defender = defender,
            preferMagic = preferMagic,
            telemetry = telemetry,
            actionMultiplier = actionMultiplier,
            actionName = actionName,
            extraOnHitStatuses = extraOnHitStatuses,
            extraBonuses = extraBonuses,
            selfHealFlat = selfHealFlat,
            selfHealPctMaxHp = selfHealPctMaxHp,
            skillRank = skillRank,
            aoeUnlockRank = aoeUnlockRank,
            aoeBonusDamagePct = aoeBonusDamagePct
        )
    }

    private fun useItem(
        selfActor: CombatActor,
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemId: String,
        currentStatuses: List<StatusEffectInstance>,
        currentImmunitySeconds: Double
    ): UseItemResult {
        return playerSupportService.useItem(
            selfActor = selfActor,
            player = player,
            itemInstances = itemInstances,
            itemId = itemId,
            currentStatuses = currentStatuses,
            currentImmunitySeconds = currentImmunitySeconds
        )
    }

    private fun applyReviveIfNeeded(player: PlayerState, actor: CombatActor): PlayerState {
        return playerSupportService.applyReviveIfNeeded(player, actor)
    }

    private fun syncPlayerHp(player: PlayerState, actor: CombatActor): PlayerState {
        return playerSupportService.syncPlayerHp(player, actor)
    }

    private fun rollEscape(playerStats: ComputedStats, monsterStats: ComputedStats): EscapeAttempt {
        return playerSupportService.rollEscape(playerStats, monsterStats)
    }

    private fun applySkillCooldown(actor: CombatActor, spec: CombatSkillSpec) {
        turnResolver.applySkillCooldown(actor, spec)
    }

    private fun effectiveCastTime(actor: CombatActor, baseCastSeconds: Double): Double {
        return turnResolver.effectiveCastTime(actor, baseCastSeconds)
    }

    private fun rangedAmmoRequirementReason(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): String? {
        return ammoService.rangedAmmoRequirementReason(player, itemInstances)
    }

    private fun playerUsesBowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): Boolean {
        return ammoService.playerUsesBowAmmo(player, itemInstances)
    }

    private fun consumeArrowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        amount: Int
    ): rpg.inventory.ArrowConsumeResult? {
        return ammoService.consumeArrowAmmo(player, itemInstances, amount)
    }

    private fun previewAmmoPayload(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        amount: Int
    ): AmmoPayload {
        return ammoService.previewAmmoPayload(player, itemInstances, amount)
    }

    private fun startCasting(
        actor: CombatActor,
        action: CombatAction,
        castTime: Double,
        afterResolve: (() -> Unit)?
    ) {
        turnResolver.startCasting(actor, action, castTime, afterResolve)
    }

    private fun endAction(actor: CombatActor, actionBarCarryPct: Double) {
        turnResolver.endAction(actor, actionBarCarryPct)
    }

    private fun format(value: Double): String = logBuilder.format(value)

    private fun combatLog(message: String) {
        logBuilder.combatLog(message)
    }

    private fun colorize(text: String, colorCode: String): String = logBuilder.colorize(text, colorCode)
}
