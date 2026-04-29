package rpg.cli

import rpg.application.CombatFlowResult
import rpg.application.PendingEncounter
import rpg.application.model.AmmoStack
import rpg.cli.combat.DungeonCombatController
import rpg.cli.renderer.CliAnsiPalette
import rpg.combat.CombatTelemetry
import rpg.combat.DungeonCombatSkillSupport
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.io.DataRepository
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.navigation.NavigationState
import rpg.talent.TalentTreeService
import rpg.world.RunRoomType

class CliCombatFlowController(
    private val engine: GameEngine,
    private val repo: DataRepository,
    private val applyBattleResolvedAchievement: (
        player: PlayerState,
        telemetry: CombatTelemetry,
        victory: Boolean,
        escaped: Boolean,
        isBoss: Boolean,
        monsterTypeId: String,
        monsterStars: Int
    ) -> PlayerState,
    private val applyGoldEarnedAchievement: (player: PlayerState, gold: Long) -> PlayerState,
    private val applyDeathAchievement: (player: PlayerState) -> PlayerState
) {
    fun run(gameState: GameState, encounter: PendingEncounter): CombatFlowResult {
        val combatLog = ArrayDeque<String>()
        val displayName = engine.monsterDisplayName(encounter.monster)
        val fixedContextLines = buildFixedContextLines(encounter, displayName)
        val controller = createController(fixedContextLines)
        val result = engine.combatEngine.runBattle(
            playerState = encounter.player,
            itemInstances = encounter.itemInstances,
            monster = encounter.monster,
            tier = encounter.tier,
            displayName = displayName,
            controller = controller,
            eventLogger = { message ->
                controller.onCombatEvent(message)
                val normalized = message.trim()
                if (normalized.isNotBlank()) {
                    combatLog += normalized
                    while (combatLog.size > 8) {
                        combatLog.removeFirst()
                    }
                }
            }
        )
        controller.finalizeDisplay()

        var playerAfterCombat = applyBattleResolvedAchievement(
            result.playerAfter,
            result.telemetry,
            result.victory,
            result.escaped,
            encounter.isBoss,
            encounter.monster.monsterTypeId.ifBlank { encounter.monster.baseType },
            encounter.monster.stars
        )
        if (!result.victory && !result.escaped) {
            playerAfterCombat = applyDeathAchievement(playerAfterCombat)
            playerAfterCombat = applyDungeonDeathDebuff(playerAfterCombat)
        }

        var updatedState = gameState.copy(
            player = playerAfterCombat,
            itemInstances = result.itemInstances
        )

        return when {
            result.escaped -> CombatFlowResult(
                gameState = updatedState.copy(currentRun = null),
                navigation = NavigationState.Exploration,
                messages = listOf("Voce fugiu do combate.") + combatLog.toList()
            )

            !result.victory -> CombatFlowResult(
                gameState = updatedState.copy(currentRun = null),
                navigation = NavigationState.Hub,
                messages = buildDefeatMessages(combatLog)
            )

            else -> {
                val levelBefore = playerAfterCombat.level
                val victory = engine.resolveVictory(
                    player = playerAfterCombat,
                    itemInstances = result.itemInstances,
                    monster = encounter.monster,
                    tier = encounter.tier,
                    collectToLoot = false
                )
                val playerWithAchievementGold = applyGoldEarnedAchievement(victory.player, victory.goldGain.toLong())
                val advancedRun = engine.advanceRun(
                    run = encounter.run,
                    bossDefeated = encounter.roomType == RunRoomType.BOSS && encounter.isBoss,
                    clearedRoomType = encounter.roomType,
                    victoryInRoom = true
                )
                updatedState = updatedState.copy(
                    player = playerWithAchievementGold,
                    itemInstances = victory.itemInstances,
                    currentRun = advancedRun
                )
                val rewardLines = mutableListOf(
                    "$displayName foi derrotado!",
                    "Ganhou ${victory.xpGain} XP e ${victory.goldGain} ouro."
                )
                if (playerWithAchievementGold.level > levelBefore) {
                    rewardLines += "Level up! Agora voce esta no nivel ${playerWithAchievementGold.level}."
                }
                victory.dropOutcome.itemInstance?.let { rewardLines += "Drop: ${it.name}." }
                if (victory.dropOutcome.itemInstance == null && victory.dropOutcome.itemId != null) {
                    rewardLines += "Drop: ${victory.dropOutcome.itemId} x${victory.dropOutcome.quantity.coerceAtLeast(1)}."
                }
                CombatFlowResult(
                    gameState = updatedState,
                    navigation = NavigationState.Exploration,
                    messages = rewardLines
                )
            }
        }
    }

    private fun createController(fixedContextLines: List<String>): DungeonCombatController {
        val skillSupport = DungeonCombatSkillSupport(
            engine = engine,
            repo = repo,
            talentTreeService = TalentTreeService(repo.balance.talentPoints),
            format = ::format,
            buildAmmoStacks = ::buildAmmoStacks
        )
        return DungeonCombatController(
            engine = engine,
            skillSupport = skillSupport,
            readInput = { readLine()?.trim().orEmpty() },
            format = ::format,
            fixedContextLines = fixedContextLines,
            ansiCombatReset = CliAnsiPalette.reset,
            ansiCombatHeader = CliAnsiPalette.combatHeader,
            ansiCombatPlayer = CliAnsiPalette.combatPlayer,
            ansiCombatEnemy = CliAnsiPalette.combatEnemy,
            ansiCombatLoading = CliAnsiPalette.combatLoading,
            ansiCombatReady = CliAnsiPalette.combatReady,
            ansiCombatBlocked = CliAnsiPalette.combatBlocked,
            ansiCombatCasting = CliAnsiPalette.combatCasting,
            ansiCombatPause = CliAnsiPalette.combatPause,
            ansiClearLine = CliAnsiPalette.clearLine,
            ansiClearToEnd = CliAnsiPalette.clearToEnd
        )
    }

    private fun buildFixedContextLines(encounter: PendingEncounter, displayName: String): List<String> {
        val lines = mutableListOf<String>()
        lines += encounter.introLines.map(String::trim).filter(String::isNotBlank)
        lines += "$displayName apareceu!"
        if (encounter.monster.onHitStatuses.isNotEmpty()) {
            val statusInfo = encounter.monster.onHitStatuses.joinToString(" | ") {
                "${rpg.status.StatusSystem.displayName(it.type)} ${format(it.chancePct)}%"
            }
            lines += "Efeitos no golpe do inimigo: $statusInfo"
        }
        return lines
    }

    private fun buildAmmoStacks(
        itemIds: List<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        selectedTemplateId: String?
    ): List<AmmoStack> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (itemId in itemIds) {
            if (!InventorySystem.isArrowAmmo(itemId, itemInstances, engine.itemRegistry)) continue
            val templateId = InventorySystem.ammoTemplateId(itemId, itemInstances, engine.itemRegistry)
            grouped.getOrPut(templateId) { mutableListOf() }.add(itemId)
        }
        return grouped.mapNotNull { (templateId, ids) ->
            val sampleId = ids.firstOrNull() ?: return@mapNotNull null
            val resolved = engine.itemResolver.resolve(sampleId, itemInstances) ?: return@mapNotNull null
            AmmoStack(
                templateId = templateId,
                sampleItemId = sampleId,
                quantity = ids.size,
                itemIds = ids.toList(),
                item = resolved
            )
        }.sortedWith(
            compareByDescending<AmmoStack> { it.templateId == selectedTemplateId?.trim()?.lowercase() }
                .thenBy { it.item.name.lowercase() }
        )
    }

    private fun applyDungeonDeathDebuff(player: PlayerState): PlayerState {
        val nextStacks = if (player.deathDebuffMinutes > 0.0) {
            player.deathDebuffStacks + 1
        } else {
            1
        }
        val durationMinutes = 10.0 + (nextStacks - 1) * 5.0
        return player.copy(
            currentHp = 1.0,
            deathDebuffStacks = nextStacks,
            deathDebuffMinutes = durationMinutes,
            deathXpPenaltyPct = 20.0,
            deathXpPenaltyMinutes = durationMinutes
        )
    }

    private fun buildDefeatMessages(combatLog: ArrayDeque<String>): List<String> {
        val lastEnemyDamage = combatLog
            .toList()
            .asReversed()
            .firstOrNull { stripAnsi(it).startsWith("O inimigo causou", ignoreCase = true) }
            ?.let(::stripAnsi)

        val messages = mutableListOf<String>()
        if (!lastEnemyDamage.isNullOrBlank()) {
            messages += lastEnemyDamage
        }
        messages += "Voce desmaiou."
        messages += "x. Voltar ao menu principal."
        return messages
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\\u001B\\[[;\\d]*m"), "")
    }

    private fun format(value: Double): String = "%.1f".format(value)
}
