package rpg.cli

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import rpg.engine.ComputedStats
import rpg.engine.GameEngine
import rpg.engine.Progression
import rpg.inventory.InventorySystem
import rpg.io.DataRepository
import rpg.model.Attributes
import rpg.model.DerivedStats
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.SkillType

internal class LegacyStatusTimeSupport(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val questZoneId: ZoneId,
    private val roomTimeMinutes: Double,
    private val clockSyncEpsilonMs: Long,
    private val deathDebuffPerStack: Double,
    private val ansiUiName: String,
    private val ansiUiLevel: String,
    private val ansiUiHp: String,
    private val ansiUiMp: String,
    private val ansiUiGold: String,
    private val ansiUiCash: String,
    private val uiColor: (text: String, colorCode: String) -> String,
    private val computePlayerStats: (PlayerState, Map<String, rpg.model.ItemInstance>) -> ComputedStats,
    private val format: (Double) -> String
) {
    fun tickEffects(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        var updated = player
        val stats = computePlayerStats(updated, itemInstances)

        if (updated.roomRegenHpRooms > 0) {
            val heal = stats.derived.hpMax * updated.roomRegenHpPct
            val newHp = min(stats.derived.hpMax, updated.currentHp + heal)
            val remaining = updated.roomRegenHpRooms - 1
            updated = if (remaining <= 0) {
                updated.copy(currentHp = newHp, roomRegenHpRooms = 0, roomRegenHpPct = 0.0)
            } else {
                updated.copy(currentHp = newHp, roomRegenHpRooms = remaining)
            }
        }

        if (updated.roomRegenMpRooms > 0) {
            val regen = stats.derived.mpMax * updated.roomRegenMpPct
            val newMp = min(stats.derived.mpMax, updated.currentMp + regen)
            val remaining = updated.roomRegenMpRooms - 1
            updated = if (remaining <= 0) {
                updated.copy(currentMp = newMp, roomRegenMpRooms = 0, roomRegenMpPct = 0.0)
            } else {
                updated.copy(currentMp = newMp, roomRegenMpRooms = remaining)
            }
        }

        if (updated.deathDebuffMinutes > 0.0) {
            val remaining = (updated.deathDebuffMinutes - roomTimeMinutes).coerceAtLeast(0.0)
            updated = if (remaining <= 0.0) {
                updated.copy(
                    deathDebuffMinutes = 0.0,
                    deathDebuffStacks = 0,
                    deathXpPenaltyMinutes = 0.0,
                    deathXpPenaltyPct = 0.0
                )
            } else {
                updated.copy(
                    deathDebuffMinutes = remaining,
                    deathXpPenaltyMinutes = remaining
                )
            }
        }

        if (updated.roomEffectRooms > 0) {
            val remainingRooms = updated.roomEffectRooms - 1
            updated = if (remainingRooms <= 0) {
                updated.copy(roomEffectRooms = 0, roomEffectMultiplier = 1.0)
            } else {
                updated.copy(roomEffectRooms = remainingRooms)
            }
        }

        if (updated.roomAttrRooms > 0) {
            val remaining = updated.roomAttrRooms - 1
            updated = if (remaining <= 0) {
                updated.copy(roomAttrRooms = 0, roomAttrBonus = Attributes())
            } else {
                updated.copy(roomAttrRooms = remaining)
            }
        }

        if (updated.roomDerivedRooms > 0) {
            val remaining = updated.roomDerivedRooms - 1
            updated = if (remaining <= 0) {
                updated.copy(roomDerivedRooms = 0, roomDerivedAdd = DerivedStats(), roomDerivedMult = DerivedStats())
            } else {
                updated.copy(roomDerivedRooms = remaining)
            }
        }

        if (updated.roomAttrRollRooms > 0) {
            val remaining = updated.roomAttrRollRooms - 1
            val chosen = engine.pickRandomAttribute()
            val bonus = addAttr(Attributes(), chosen, max(1, updated.roomAttrRollAmount))
            val updatedBonus = updated.runAttrBonus + bonus
            updated = if (remaining <= 0) {
                updated.copy(runAttrBonus = updatedBonus, roomAttrRollRooms = 0, roomAttrRollAmount = 0)
            } else {
                updated.copy(runAttrBonus = updatedBonus, roomAttrRollRooms = remaining)
            }
        }

        return updated
    }

    fun advanceOutOfCombatTime(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        minutes: Double
    ): PlayerState {
        if (minutes <= 0.0) return player
        val stats = computePlayerStats(player, itemInstances)
        val newHp = (player.currentHp + stats.derived.hpRegen * minutes).coerceAtMost(stats.derived.hpMax)
        val newMp = (player.currentMp + stats.derived.mpRegen * minutes).coerceAtMost(stats.derived.mpMax)
        var updated = player.copy(currentHp = newHp, currentMp = newMp)

        if (updated.deathDebuffMinutes > 0.0) {
            val remaining = (updated.deathDebuffMinutes - minutes).coerceAtLeast(0.0)
            updated = if (remaining <= 0.0) {
                updated.copy(
                    deathDebuffMinutes = 0.0,
                    deathDebuffStacks = 0,
                    deathXpPenaltyMinutes = 0.0,
                    deathXpPenaltyPct = 0.0
                )
            } else {
                updated.copy(
                    deathDebuffMinutes = remaining,
                    deathXpPenaltyMinutes = remaining
                )
            }
        }
        if (updated.deathDebuffMinutes <= 0.0 && updated.deathXpPenaltyMinutes > 0.0) {
            val remainingXpPenalty = (updated.deathXpPenaltyMinutes - minutes).coerceAtLeast(0.0)
            updated = if (remainingXpPenalty <= 0.0) {
                updated.copy(deathXpPenaltyMinutes = 0.0, deathXpPenaltyPct = 0.0)
            } else {
                updated.copy(deathXpPenaltyMinutes = remainingXpPenalty)
            }
        }

        return updated
    }

    fun synchronizeClock(state: GameState): GameState {
        val now = System.currentTimeMillis()
        val last = if (state.lastClockSyncEpochMs > 0L) state.lastClockSyncEpochMs else now
        val elapsedMs = (now - last).coerceAtLeast(0L)
        if (elapsedMs < clockSyncEpsilonMs) {
            return if (state.lastClockSyncEpochMs > 0L) {
                state
            } else {
                state.copy(lastClockSyncEpochMs = now)
            }
        }
        val minutes = elapsedMs / 60000.0
        val updatedPlayer = advanceOutOfCombatTime(state.player, state.itemInstances, minutes)
        return state.copy(
            player = updatedPlayer,
            worldTimeMinutes = state.worldTimeMinutes + minutes,
            lastClockSyncEpochMs = now
        )
    }

    fun showClock(@Suppress("UNUSED_PARAMETER") state: GameState) {
        val now = Instant.now().atZone(questZoneId)
        val date = now.toLocalDate()
        val time = now.toLocalTime().withNano(0)
        println("Clock sistema: $date $time")
    }

    fun formatClock(worldMinutes: Double): String {
        val total = worldMinutes.toInt().coerceAtLeast(0)
        val minutesOfDay = total % 1440
        val hour = minutesOfDay / 60
        val minute = minutesOfDay % 60
        return "%02d:%02d".format(hour, minute)
    }

    fun runProgressBar(label: String, durationSeconds: Double) {
        val totalSeconds = durationSeconds.coerceAtLeast(1.0)
        val steps = 20
        val stepMs = ((totalSeconds * 1000.0) / steps).toLong().coerceAtLeast(30L)
        for (step in 1..steps) {
            Thread.sleep(stepMs)
            val progress = step.toDouble() / steps
            val filled = (progress * 20).toInt().coerceIn(0, 20)
            val bar = "#".repeat(filled) + "-".repeat(20 - filled)
            val pct = (progress * 100).toInt()
            print("\r$label [$bar] ${pct}%")
        }
        println()
    }

    fun showStatus(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) {
        val stats = computePlayerStats(player, itemInstances)
        val className = repo.classes[player.classId]?.name ?: player.classId
        val secondClassName = player.subclassId?.let { repo.subclasses[it]?.name ?: it } ?: "-"
        val specializationName = player.specializationId?.let { repo.specializations[it]?.name ?: it } ?: "-"
        val slotLimit = InventorySystem.inventoryLimit(player, itemInstances, engine.itemRegistry)
        val slotUsed = InventorySystem.slotsUsed(player, itemInstances, engine.itemRegistry)
        println(
            "Nome ${uiColor(player.name, ansiUiName)} | " +
                "Nivel ${uiColor(player.level.toString(), ansiUiLevel)} | " +
                "XP ${player.xp}/${Progression.xpForNext(player.level)}"
        )
        println("Classe base $className | 2a classe $secondClassName | Especializacao $specializationName")
        val questCurrencyLabel = if (player.questCurrency > 0) {
            " | Moeda de Quest ${player.questCurrency}"
        } else {
            ""
        }
        println(
            "HP ${uiColor("${format(player.currentHp)}/${format(stats.derived.hpMax)}", ansiUiHp)} | " +
                "MP ${uiColor("${format(player.currentMp)}/${format(stats.derived.mpMax)}", ansiUiMp)} | " +
                "Ouro ${uiColor(player.gold.toString(), ansiUiGold)} | " +
                "CASH ${uiColor(player.premiumCash.toString(), ansiUiCash)}$questCurrencyLabel"
        )
        println("Inventario: $slotUsed/$slotLimit slots")
        val hpEta = etaMinutesToFull(player.currentHp, stats.derived.hpMax, stats.derived.hpRegen)
        val mpEta = etaMinutesToFull(player.currentMp, stats.derived.mpMax, stats.derived.mpRegen)
        if (hpEta != null || mpEta != null) {
            val hpLabel = hpEta?.let { "${format(it)} min" } ?: "--"
            val mpLabel = mpEta?.let { "${format(it)} min" } ?: "--"
            println("Regen natural: HP cheio em $hpLabel | MP cheio em $mpLabel")
        }
        println(
            "Skills: MIN ${skillLevel(player, SkillType.MINING)} | " +
                "GAT ${skillLevel(player, SkillType.GATHERING)} | " +
                "WOOD ${skillLevel(player, SkillType.WOODCUTTING)} | " +
                "FISH ${skillLevel(player, SkillType.FISHING)} | " +
                "BS ${skillLevel(player, SkillType.BLACKSMITH)} | " +
                "ALCH ${skillLevel(player, SkillType.ALCHEMIST)} | " +
                "COOK ${skillLevel(player, SkillType.COOKING)}"
        )
        println(
            "STR ${stats.attributes.str} AGI ${stats.attributes.agi} DEX ${stats.attributes.dex} " +
                "VIT ${stats.attributes.vit} INT ${stats.attributes.`int`} SPR ${stats.attributes.spr} " +
                "LUK ${stats.attributes.luk}"
        )
    }

    fun showDebuff(player: PlayerState) {
        if (player.deathDebuffStacks > 0) {
            val minutes = "%.1f".format(player.deathDebuffMinutes)
            println("Debuff de morte: -${(deathDebuffPerStack * 100).toInt()}% atributos x${player.deathDebuffStacks} (${minutes} min)")
        }
        if (player.deathXpPenaltyMinutes > 0.0 && player.deathXpPenaltyPct > 0.0) {
            println("Penalidade de XP: -${format(player.deathXpPenaltyPct)}% (${format(player.deathXpPenaltyMinutes)} min)")
        }
        if (player.roomEffectRooms > 0) {
            val percent = ((player.roomEffectMultiplier - 1.0) * 100).toInt()
            val label = if (percent >= 0) "+$percent%" else "$percent%"
            println("Efeito temporario: $label atributos (${player.roomEffectRooms} salas)")
        }
    }

    private fun skillLevel(player: PlayerState, skill: SkillType): Int {
        return engine.skillSystem.snapshot(player, skill).level
    }

    private fun etaMinutesToFull(current: Double, maxValue: Double, regenPerMinute: Double): Double? {
        if (regenPerMinute <= 0.0) return null
        if (current >= maxValue) return 0.0
        return ((maxValue - current) / regenPerMinute).coerceAtLeast(0.0)
    }

    private fun addAttr(attrs: Attributes, code: String, delta: Int): Attributes = when (code) {
        "STR" -> attrs.copy(str = attrs.str + delta)
        "AGI" -> attrs.copy(agi = attrs.agi + delta)
        "DEX" -> attrs.copy(dex = attrs.dex + delta)
        "VIT" -> attrs.copy(vit = attrs.vit + delta)
        "INT" -> attrs.copy(`int` = attrs.`int` + delta)
        "SPR" -> attrs.copy(spr = attrs.spr + delta)
        "LUK" -> attrs.copy(luk = attrs.luk + delta)
        else -> attrs
    }
}

