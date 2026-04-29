package rpg.presentation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import rpg.application.actions.GameAction
import rpg.engine.GameEngine
import rpg.engine.Progression
import rpg.inventory.InventorySystem
import rpg.model.GameState
import rpg.model.SkillType
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.PlayerSummaryViewModel
import rpg.presentation.model.ProgressBarViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class PresentationSupport(
    private val engine: GameEngine
) {
    private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun presentMissingState(title: String): ScreenViewModel {
        return MenuScreenViewModel(
            title = title,
            bodyLines = listOf("Nenhum jogo carregado."),
            options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back))
        )
    }

    fun playerSummary(state: GameState): PlayerSummaryViewModel {
        val stats = engine.computePlayerStats(state.player, state.itemInstances)
        val classDef = engine.classSystem.classDef(state.player.classId)
        val secondClass = state.player.subclassId?.let(engine.classSystem::subclassDef)?.name ?: "-"
        val specialization = state.player.specializationId?.let(engine.classSystem::specializationDef)?.name ?: "-"
        val classLabel = "${classDef.name} | 2a: $secondClass | Esp: $specialization"
        return PlayerSummaryViewModel(
            name = state.player.name,
            level = state.player.level,
            classLabel = classLabel,
            gold = state.player.gold,
            hp = ProgressBarViewModel("HP", state.player.currentHp, stats.derived.hpMax),
            mp = ProgressBarViewModel("MP", state.player.currentMp, stats.derived.mpMax)
        )
    }

    fun attributeSummaryLine(state: GameState): String {
        val attrs = engine.computePlayerStats(state.player, state.itemInstances).attributes
        return "Atributos: STR ${attrs.str} | AGI ${attrs.agi} | DEX ${attrs.dex} | VIT ${attrs.vit} | INT ${attrs.`int`} | SPR ${attrs.spr} | LUK ${attrs.luk}"
    }

    fun productionSkillsSummaryLine(state: GameState): String {
        val parts = productionSkillOrder.map { (type, label) ->
            val level = engine.skillSystem.snapshot(state.player, type).level
            "$label $level"
        }
        return "Producao: ${parts.joinToString(" | ")}"
    }

    fun hubOverviewLines(state: GameState): List<String> {
        val player = state.player
        val stats = engine.computePlayerStats(player, state.itemInstances)
        val classDef = engine.classSystem.classDef(player.classId)
        val secondClass = player.subclassId?.let(engine.classSystem::subclassDef)?.name ?: "-"
        val specialization = player.specializationId?.let(engine.classSystem::specializationDef)?.name ?: "-"
        val slotLimit = InventorySystem.inventoryLimit(player, state.itemInstances, engine.itemRegistry)
        val slotUsed = InventorySystem.slotsUsed(player, state.itemInstances, engine.itemRegistry)
        val hpEta = etaMinutesToFull(player.currentHp, stats.derived.hpMax, stats.derived.hpRegen)
        val mpEta = etaMinutesToFull(player.currentMp, stats.derived.mpMax, stats.derived.mpRegen)
        val hpLine = "HP ${compactBar(player.currentHp, stats.derived.hpMax)} ${player.currentHp.toInt()}/${stats.derived.hpMax.toInt()}"
        val mpLine = "MP ${compactBar(player.currentMp, stats.derived.mpMax)} ${player.currentMp.toInt()}/${stats.derived.mpMax.toInt()}"
        val hpRegenLine = regenCompactLabel("HP", hpEta)
        val mpRegenLine = regenCompactLabel("MP", mpEta)

        return buildList {
            add("Clock sistema: ${Instant.now().atZone(ZoneId.systemDefault()).format(clockFormatter)}")
            add("")
            add("${player.name} | Nivel ${player.level} | XP: ${player.xp}/${Progression.xpForNext(player.level)}")
            add("${classDef.name} | 2a: $secondClass | Esp: $specialization")
            add("")
            add("$hpLine | $mpLine | Ouro ${player.gold}")
            add("Regen:$hpRegenLine | $mpRegenLine")
            add("")
            add("CASH: ${player.premiumCash} | Inventario: $slotUsed/$slotLimit slots")
            add("")
            add(productionSkillsSummaryLine(state))
            add(attributeSummaryLine(state))
            if (player.deathDebuffStacks > 0) {
                val debuffPct = (player.deathDebuffStacks * 20).coerceAtLeast(0)
                add("[${player.deathDebuffStacks} Stacks de morte. Debuff nos atributos em $debuffPct%]")
            }
        }
    }

    fun formatSignedInt(value: Int): String = if (value >= 0) "+$value" else value.toString()

    private val productionSkillOrder: List<Pair<SkillType, String>> = listOf(
        SkillType.BLACKSMITH to "Forja",
        SkillType.FISHING to "Pesca",
        SkillType.MINING to "Mineracao",
        SkillType.GATHERING to "Coleta",
        SkillType.WOODCUTTING to "Lenhador",
        SkillType.ALCHEMIST to "Alquimia",
        SkillType.COOKING to "Culinaria"
    )

    private fun etaMinutesToFull(current: Double, maxValue: Double, regenPerMinute: Double): Double? {
        if (regenPerMinute <= 0.0) return null
        if (current >= maxValue) return 0.0
        return ((maxValue - current) / regenPerMinute).coerceAtLeast(0.0)
    }

    private fun regenCompactLabel(label: String, etaMinutes: Double?): String {
        return when {
            etaMinutes == null -> "$label sem regen"
            etaMinutes == 0.0 -> "$label cheio"
            else -> "$label cheio em ${formatMinutesBr(etaMinutes)} min"
        }
    }

    private fun compactBar(current: Double, maxValue: Double, width: Int = 12): String {
        if (maxValue <= 0.0) {
            return "[${"-".repeat(width)}]"
        }
        val ratio = (current / maxValue).coerceIn(0.0, 1.0)
        val filled = (ratio * width).toInt().coerceIn(0, width)
        val empty = (width - filled).coerceAtLeast(0)
        return "[${"#".repeat(filled)}${"-".repeat(empty)}]"
    }

    private fun formatMinutesBr(value: Double): String = "%.1f".format(value).replace('.', ',')

    private fun format(value: Double): String = "%.1f".format(value)
}
