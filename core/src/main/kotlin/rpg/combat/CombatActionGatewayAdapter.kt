package rpg.combat

import kotlin.random.Random
import rpg.engine.ComputedStats
import rpg.inventory.ArrowConsumeResult
import rpg.model.Bonuses
import rpg.model.CombatStatusApplyDef
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.status.StatusEffectInstance

internal class CombatActionGatewayAdapter(
    override val rng: Random,
    override val ansiYellow: String,
    override val ansiBlue: String,
    private val combatLogHandler: (String) -> Unit,
    private val colorizeHandler: (String, String) -> String,
    private val formatHandler: (Double) -> String,
    private val effectiveCastTimeHandler: (CombatActor, Double) -> Double,
    private val applySkillCooldownHandler: (CombatActor, CombatSkillSpec) -> Unit,
    private val startCastingHandler: (CombatActor, CombatAction, Double, (() -> Unit)?) -> Unit,
    private val endActionHandler: (CombatActor, Double) -> Unit,
    private val resolveSkillHandler: (
        CombatActor,
        CombatActor,
        Boolean?,
        MutableCombatTelemetry,
        Double,
        String?,
        List<CombatStatusApplyDef>,
        Bonuses,
        Double,
        Double,
        Int,
        Int,
        Double
    ) -> SkillResolutionResult,
    private val useItemHandler: (
        CombatActor,
        PlayerState,
        Map<String, ItemInstance>,
        String,
        List<StatusEffectInstance>,
        Double
    ) -> UseItemResult,
    private val applyReviveIfNeededHandler: (PlayerState, CombatActor) -> PlayerState,
    private val syncPlayerHpHandler: (PlayerState, CombatActor) -> PlayerState,
    private val rollEscapeHandler: (ComputedStats, ComputedStats) -> EscapeAttempt,
    private val rangedAmmoRequirementReasonHandler: (PlayerState, Map<String, ItemInstance>) -> String?,
    private val playerUsesBowAmmoHandler: (PlayerState, Map<String, ItemInstance>) -> Boolean,
    private val consumeArrowAmmoHandler: (PlayerState, Map<String, ItemInstance>, Int) -> ArrowConsumeResult?,
    private val previewAmmoPayloadHandler: (PlayerState, Map<String, ItemInstance>, Int) -> AmmoPayload
) : CombatActionGateway {
    override fun combatLog(message: String) {
        combatLogHandler(message)
    }

    override fun colorize(text: String, colorCode: String): String {
        return colorizeHandler(text, colorCode)
    }

    override fun format(value: Double): String {
        return formatHandler(value)
    }

    override fun effectiveCastTime(actor: CombatActor, baseCastSeconds: Double): Double {
        return effectiveCastTimeHandler(actor, baseCastSeconds)
    }

    override fun applySkillCooldown(actor: CombatActor, spec: CombatSkillSpec) {
        applySkillCooldownHandler(actor, spec)
    }

    override fun startCasting(
        actor: CombatActor,
        action: CombatAction,
        castTime: Double,
        afterResolve: (() -> Unit)?
    ) {
        startCastingHandler(actor, action, castTime, afterResolve)
    }

    override fun endAction(actor: CombatActor, actionBarCarryPct: Double) {
        endActionHandler(actor, actionBarCarryPct)
    }

    override fun resolveSkill(
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
        return resolveSkillHandler(
            attacker,
            defender,
            preferMagic,
            telemetry,
            actionMultiplier,
            actionName,
            extraOnHitStatuses,
            extraBonuses,
            selfHealFlat,
            selfHealPctMaxHp,
            skillRank,
            aoeUnlockRank,
            aoeBonusDamagePct
        )
    }

    override fun useItem(
        selfActor: CombatActor,
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemId: String,
        currentStatuses: List<StatusEffectInstance>,
        currentImmunitySeconds: Double
    ): UseItemResult {
        return useItemHandler(
            selfActor,
            player,
            itemInstances,
            itemId,
            currentStatuses,
            currentImmunitySeconds
        )
    }

    override fun applyReviveIfNeeded(player: PlayerState, actor: CombatActor): PlayerState {
        return applyReviveIfNeededHandler(player, actor)
    }

    override fun syncPlayerHp(player: PlayerState, actor: CombatActor): PlayerState {
        return syncPlayerHpHandler(player, actor)
    }

    override fun rollEscape(playerStats: ComputedStats, monsterStats: ComputedStats): EscapeAttempt {
        return rollEscapeHandler(playerStats, monsterStats)
    }

    override fun rangedAmmoRequirementReason(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): String? {
        return rangedAmmoRequirementReasonHandler(player, itemInstances)
    }

    override fun playerUsesBowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): Boolean {
        return playerUsesBowAmmoHandler(player, itemInstances)
    }

    override fun consumeArrowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        amount: Int
    ): ArrowConsumeResult? {
        return consumeArrowAmmoHandler(player, itemInstances, amount)
    }

    override fun previewAmmoPayload(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        amount: Int
    ): AmmoPayload {
        return previewAmmoPayloadHandler(player, itemInstances, amount)
    }
}
