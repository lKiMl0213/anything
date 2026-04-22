package rpg.cli.combat

import rpg.cli.model.CombatMenuAction
import rpg.cli.model.CombatSkillOption
import rpg.cli.model.DecisionView
import rpg.combat.DungeonCombatSkillSupport
import rpg.engine.GameEngine
import rpg.model.ItemType

internal interface DungeonCombatDisplayController : rpg.combat.PlayerCombatController {
    fun onCombatEvent(rawMessage: String)
    fun finalizeDisplay()
}

internal class DungeonCombatController(
    private val engine: GameEngine,
    private val skillSupport: DungeonCombatSkillSupport,
    private val readInput: () -> String,
    private val format: (Double) -> String,
    ansiCombatReset: String,
    ansiCombatHeader: String,
    ansiCombatPlayer: String,
    ansiCombatEnemy: String,
    ansiCombatLoading: String,
    ansiCombatReady: String,
    ansiCombatBlocked: String,
    ansiCombatCasting: String,
    ansiCombatPause: String,
    ansiClearLine: String,
    ansiClearToEnd: String
) : DungeonCombatDisplayController {
    private var decisionActive: Boolean = false
    private var decisionView: DecisionView = DecisionView.MAIN
    private var decisionConsumables: List<String> = emptyList()
    private var decisionSkills: List<CombatSkillOption> = emptyList()

    private val renderer = DungeonCombatRenderer(
        engine = engine,
        format = format,
        ansiCombatReset = ansiCombatReset,
        ansiCombatHeader = ansiCombatHeader,
        ansiCombatPlayer = ansiCombatPlayer,
        ansiCombatEnemy = ansiCombatEnemy,
        ansiCombatLoading = ansiCombatLoading,
        ansiCombatReady = ansiCombatReady,
        ansiCombatBlocked = ansiCombatBlocked,
        ansiCombatCasting = ansiCombatCasting,
        ansiCombatPause = ansiCombatPause,
        ansiClearLine = ansiClearLine,
        ansiClearToEnd = ansiClearToEnd
    )

    override fun onFrame(snapshot: rpg.combat.CombatSnapshot) {
        renderer.onFrame(snapshot, ::buildDecisionSectionLines)
    }

    override fun onDecisionStarted(snapshot: rpg.combat.CombatSnapshot) {
        if (decisionActive) return
        decisionActive = true
        decisionView = DecisionView.MAIN
        decisionConsumables = emptyList()
        decisionSkills = skillSupport.buildCombatSkillOptions(snapshot)
        renderer.renderNow(snapshot, ::buildDecisionSectionLines)
    }

    override fun onDecisionEnded() {
        decisionActive = false
        decisionView = DecisionView.MAIN
        decisionConsumables = emptyList()
        decisionSkills = emptyList()
    }

    override fun onCombatEvent(rawMessage: String) {
        renderer.appendCombatHistory(rawMessage)
    }

    override fun pollAction(snapshot: rpg.combat.CombatSnapshot): rpg.combat.CombatAction? {
        if (!decisionActive) {
            onDecisionStarted(snapshot)
        }
        renderer.renderNow(snapshot, ::buildDecisionSectionLines)
        val input = readInput().lowercase()
        renderer.onInputCaptured()
        return when (decisionView) {
            DecisionView.MAIN -> parseMainDecisionInput(input, snapshot)
            DecisionView.ATTACK -> parseAttackDecisionInput(input, snapshot)
            DecisionView.ITEM -> parseItemDecisionInput(input)
        }
    }

    override fun finalizeDisplay() {
        renderer.finalizeDisplay()
    }

    private fun parseMainDecisionInput(
        input: String,
        snapshot: rpg.combat.CombatSnapshot
    ): rpg.combat.CombatAction? {
        return when (input) {
            "1" -> {
                decisionView = DecisionView.ATTACK
                null
            }
            "2" -> {
                val consumables = snapshot.player.inventory.filter { id ->
                    engine.itemResolver.resolve(id, snapshot.itemInstances)?.type == ItemType.CONSUMABLE
                }
                if (consumables.isEmpty()) {
                    renderer.appendCombatHistory("Nenhum consumivel disponivel.")
                    null
                } else {
                    decisionConsumables = consumables
                    decisionView = DecisionView.ITEM
                    null
                }
            }
            "3" -> {
                rpg.combat.CombatAction.Escape
            }
            else -> {
                renderer.appendCombatHistory("Opcao invalida.")
                null
            }
        }
    }

    private fun parseAttackDecisionInput(
        input: String,
        snapshot: rpg.combat.CombatSnapshot
    ): rpg.combat.CombatAction? {
        if (input == "x") {
            decisionView = DecisionView.MAIN
            return null
        }
        val actions = buildAttackDecisionActions(snapshot)
        val index = input.toIntOrNull()
        if (index == null || index !in 1..actions.size) {
            renderer.appendCombatHistory("Opcao invalida.")
            return null
        }
        return when (val action = actions[index - 1]) {
            is CombatMenuAction.BasicAttack -> {
                if (!action.available) {
                    renderer.appendCombatHistory(action.unavailableReason ?: "Ataque indisponivel.")
                    null
                } else {
                    rpg.combat.CombatAction.Attack(preferMagic = action.preferMagic)
                }
            }
            is CombatMenuAction.SkillAttack -> {
                if (!action.skill.available) {
                    renderer.appendCombatHistory(action.skill.unavailableReason ?: "Habilidade indisponivel.")
                    null
                } else {
                    rpg.combat.CombatAction.Skill(
                        spec = rpg.combat.CombatSkillSpec(
                            id = action.skill.id,
                            name = action.skill.name,
                            mpCost = action.skill.mpCost,
                            cooldownSeconds = action.skill.cooldownSeconds,
                            damageMultiplier = action.skill.damageMultiplier,
                            preferMagic = action.skill.preferMagic,
                            castTimeSeconds = action.skill.castTimeSeconds,
                            onHitStatuses = action.skill.onHitStatuses,
                            selfHealFlat = action.skill.selfHealFlat,
                            selfHealPctMaxHp = action.skill.selfHealPctMaxHp,
                            ammoCost = action.skill.ammoCost,
                            rank = action.skill.rank,
                            aoeUnlockRank = action.skill.aoeUnlockRank,
                            aoeBonusDamagePct = action.skill.aoeBonusDamagePct
                        )
                    )
                }
            }
        }
    }

    private fun parseItemDecisionInput(input: String): rpg.combat.CombatAction? {
        if (input == "x") {
            decisionView = DecisionView.MAIN
            return null
        }
        val index = input.toIntOrNull()
        if (index == null || index !in 1..decisionConsumables.size) {
            renderer.appendCombatHistory("Opcao invalida.")
            return null
        }
        val itemId = decisionConsumables[index - 1]
        return rpg.combat.CombatAction.UseItem(itemId)
    }

    private fun buildDecisionSectionLines(snapshot: rpg.combat.CombatSnapshot): List<String> {
        if (!snapshot.pausedForDecision || !decisionActive) {
            return listOf("")
        }
        return when (decisionView) {
            DecisionView.MAIN -> mainDecisionMenuLines()
            DecisionView.ATTACK -> attackDecisionMenuLines(snapshot)
            DecisionView.ITEM -> itemDecisionMenuLines(snapshot.itemInstances)
        }
    }

    private fun mainDecisionMenuLines(): List<String> {
        return listOf(
            "Voce esta pronto para agir.",
            "1. Atacar",
            "2. Usar item",
            "3. Fugir",
            "Escolha: "
        )
    }

    private fun attackDecisionMenuLines(snapshot: rpg.combat.CombatSnapshot): List<String> {
        val actions = buildAttackDecisionActions(snapshot)
        val lines = mutableListOf<String>()
        lines += "Ataques:"
        skillSupport.rangedAmmoStatusLine(snapshot)?.let { lines += it }
        actions.forEachIndexed { index, action ->
            val label = when (action) {
                is CombatMenuAction.BasicAttack -> {
                    val base = "Ataque Basico"
                    if (action.available) base else "$base [${action.unavailableReason}]"
                }
                is CombatMenuAction.SkillAttack -> {
                    val cost = format(action.skill.mpCost)
                    val castLabel = if (action.skill.castTimeSeconds > 0.0) {
                        " | Cast ${format(action.skill.castTimeSeconds)}s"
                    } else {
                        ""
                    }
                    val healLabel = when {
                        action.skill.selfHealPctMaxHp > 0.0 -> " | Cura ${format(action.skill.selfHealPctMaxHp)}% HP"
                        action.skill.selfHealFlat > 0.0 -> " | Cura ${format(action.skill.selfHealFlat)} HP"
                        else -> ""
                    }
                    val rankLabel = if (action.skill.maxRank > 1) " | Rank ${action.skill.rank}/${action.skill.maxRank}" else ""
                    val aoeLabel = if (action.skill.aoeUnlockRank > 0) {
                        val status = if (action.skill.rank >= action.skill.aoeUnlockRank) "AOE ativo" else "AOE no rank ${action.skill.aoeUnlockRank}"
                        " | $status"
                    } else {
                        ""
                    }
                    val base = "${action.skill.name} ($cost MP | CD ${format(action.skill.cooldownSeconds)}s$castLabel$healLabel$rankLabel$aoeLabel)"
                    if (action.skill.available) base else "$base [${action.skill.unavailableReason}]"
                }
            }
            lines += "${index + 1}. $label"
        }
        lines += "x. Voltar"
        lines += "Escolha: "
        return lines
    }

    private fun itemDecisionMenuLines(itemInstances: Map<String, rpg.model.ItemInstance>): List<String> {
        val lines = mutableListOf<String>()
        lines += "Consumiveis:"
        decisionConsumables.forEachIndexed { index, itemId ->
            val name = engine.itemResolver.resolve(itemId, itemInstances)?.name ?: itemId
            lines += "${index + 1}. $name"
        }
        lines += "x. Voltar"
        lines += "Escolha: "
        return lines
    }

    private fun buildAttackDecisionActions(snapshot: rpg.combat.CombatSnapshot): List<CombatMenuAction> {
        val built = skillSupport.buildAttackDecisionActions(snapshot)
        decisionSkills = built.skills
        return built.actions
    }
}
