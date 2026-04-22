package rpg.combat

import kotlin.math.max
import kotlin.random.Random
import rpg.engine.ComputedStats
import rpg.engine.StatsEngine
import rpg.item.ItemResolver
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState

internal class CombatPlayerSupportService(
    private val statsEngine: StatsEngine,
    private val itemResolver: ItemResolver,
    private val rng: Random,
    private val statusProcessor: CombatStatusProcessor,
    private val logBuilder: CombatLogBuilder
) {
    fun useItem(
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
            logBuilder.combatLog(logBuilder.colorize("Status negativos removidos por ${item.name}.", CombatLogBuilder.ansiGreen))
        }
        for (statusDef in item.effects.applyStatuses) {
            val tunedStatusDef = statusProcessor.tuneStatusApplication(
                base = statusDef,
                sourceModifiers = selfActor.talentModifiers,
                targetModifiers = selfActor.talentModifiers
            )
            val applied = rpg.status.StatusSystem.applyStatus(
                current = statuses,
                application = tunedStatusDef,
                rng = rng,
                defaultSource = item.name
            )
            statuses = applied.statuses
            if (applied.applied) {
                val source = statusDef.source.ifBlank { item.name }
                val sourceSuffix = if (source.isBlank()) "" else " ($source)"
                logBuilder.combatLog(
                    logBuilder.colorize(
                        "Voce esta ${rpg.status.StatusSystem.statusAdjective(statusDef.type)}$sourceSuffix.",
                        CombatLogBuilder.ansiGreen
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
            logBuilder.combatLog(
                logBuilder.colorize(
                    "Imunidade a status ativa por ${logBuilder.format(item.effects.statusImmunitySeconds)}s.",
                    CombatLogBuilder.ansiGreen
                )
            )
        }

        logBuilder.combatLog(logBuilder.colorize("Usou ${item.name}.", CombatLogBuilder.ansiGreen))
        return UseItemResult(
            player = healed.copy(inventory = inventory),
            itemInstances = updatedInstances,
            statuses = statuses,
            statusImmunitySeconds = immunitySeconds
        )
    }

    fun applyReviveIfNeeded(player: PlayerState, actor: CombatActor): PlayerState {
        if (player.currentHp > 0.0 || !player.reviveOnce) return player
        logBuilder.combatLog("Uma segunda chance silenciosa. Voce resiste com 1 HP.")
        actor.currentHp = 1.0
        actor.runtime = actor.runtime.copy(state = CombatState.IDLE)
        return player.copy(currentHp = 1.0, reviveOnce = false)
    }

    fun syncPlayerHp(player: PlayerState, actor: CombatActor): PlayerState {
        return player.copy(currentHp = actor.currentHp, currentMp = actor.currentMp)
    }

    fun rollEscape(playerStats: ComputedStats, monsterStats: ComputedStats): EscapeAttempt {
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
}
