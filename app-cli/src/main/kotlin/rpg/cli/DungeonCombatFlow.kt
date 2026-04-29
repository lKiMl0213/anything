package rpg.cli

import rpg.cli.combat.DungeonCombatDisplayController
import rpg.cli.model.BattleOutcome
import rpg.classquest.ClassQuestDungeonDefinition
import rpg.combat.CombatTelemetry
import rpg.engine.GameEngine
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.monster.MonsterInstance

internal class DungeonCombatFlow(
    private val engine: GameEngine,
    private val format: (Double) -> String,
    private val itemDisplayLabelByResolved: (rpg.item.ResolvedItem) -> String,
    private val itemDisplayLabelByNameAndRarity: (name: String, rarity: rpg.item.ItemRarity) -> String,
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
    private val createController: () -> DungeonCombatDisplayController,
    private val emit: (String) -> Unit
) {
    fun battleMonster(
        playerState: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        monster: MonsterInstance,
        tier: MapTierDef,
        lootCollector: MutableList<String>?,
        isBoss: Boolean,
        classDungeon: ClassQuestDungeonDefinition? = null
    ): BattleOutcome {
        val bossLabel = if (isBoss) "BOSS " else ""
        val displayName = engine.monsterDisplayName(monster)
        emit("\n$bossLabel$displayName apareceu!")
        if (monster.onHitStatuses.isNotEmpty()) {
            val statusInfo = monster.onHitStatuses.joinToString(" | ") {
                "${rpg.status.StatusSystem.displayName(it.type)} ${format(it.chancePct)}%"
            }
            emit("Efeitos no golpe do inimigo: $statusInfo")
        }

        val combatController = createController()
        val result = engine.combatEngine.runBattle(
            playerState = playerState,
            itemInstances = itemInstances,
            monster = monster,
            tier = tier,
            displayName = displayName,
            controller = combatController,
            eventLogger = { message -> combatController.onCombatEvent(message) }
        )
        combatController.finalizeDisplay()
        emit("")

        var player = result.playerAfter
        var instances = result.itemInstances
        player = applyBattleResolvedAchievement(
            player,
            result.telemetry,
            result.victory,
            result.escaped,
            isBoss,
            monster.monsterTypeId.ifBlank { monster.baseType },
            monster.stars
        )

        if (result.escaped) {
            return BattleOutcome(player, instances, victory = false, escaped = true)
        }
        if (!result.victory) {
            return BattleOutcome(player, instances, victory = false)
        }

        emit("$displayName foi derrotado!")
        val levelBefore = player.level
        val victory = engine.resolveVictory(player, instances, monster, tier, collectToLoot = lootCollector != null)
        player = victory.player
        instances = victory.itemInstances
        player = applyGoldEarnedAchievement(player, victory.goldGain.toLong())
        val collectedItems = mutableMapOf<String, Int>()
        emit("Ganhou ${victory.xpGain} XP e ${victory.goldGain} ouro.")

        if (player.level > levelBefore) {
            emit("\nLevel up! Agora voce esta no nivel ${player.level}.")
        }

        if (lootCollector != null) {
            val outcome = victory.dropOutcome
            if (outcome.itemInstance != null) {
                lootCollector.add(outcome.itemInstance.id)
                val canonicalId = outcome.itemInstance.templateId
                collectedItems[canonicalId] = (collectedItems[canonicalId] ?: 0) + 1
                emit("Drop encontrado: ${itemDisplayLabelByNameAndRarity(outcome.itemInstance.name, outcome.itemInstance.rarity)}")
            } else if (outcome.itemId != null) {
                val resolved = engine.itemResolver.resolve(outcome.itemId, instances)
                val name = resolved?.let(itemDisplayLabelByResolved) ?: outcome.itemId
                val qty = outcome.quantity.coerceAtLeast(1)
                repeat(qty) { lootCollector.add(outcome.itemId) }
                collectedItems[outcome.itemId] = (collectedItems[outcome.itemId] ?: 0) + qty
                val label = if (qty > 1) "$name x$qty" else name
                emit("Drop encontrado: $label")
            }
        } else {
            val outcome = victory.dropOutcome
            if (outcome.itemInstance != null) {
                val canonicalId = outcome.itemInstance.templateId
                collectedItems[canonicalId] = (collectedItems[canonicalId] ?: 0) + 1
            } else if (outcome.itemId != null) {
                val qty = outcome.quantity.coerceAtLeast(1)
                collectedItems[outcome.itemId] = (collectedItems[outcome.itemId] ?: 0) + qty
            }
        }

        if (classDungeon != null && lootCollector != null) {
            val collectibleDrops = engine.classQuestService.collectibleDropsForDungeonKill(
                player = player,
                monsterId = monster.archetypeId,
                isBoss = isBoss
            )
            if (collectibleDrops.isNotEmpty()) {
                val updatedInstances = instances.toMutableMap()
                for (drop in collectibleDrops) {
                    updatedInstances[drop.id] = drop
                    lootCollector.add(drop.id)
                    val canonicalId = drop.templateId
                    collectedItems[canonicalId] = (collectedItems[canonicalId] ?: 0) + 1
                }
                instances = updatedInstances.toMap()
                val grouped = collectibleDrops.groupingBy { it.templateId }.eachCount()
                for ((templateId, qty) in grouped) {
                    val name = collectibleDrops.firstOrNull { it.templateId == templateId }?.name ?: templateId
                    val label = if (qty > 1) "$name x$qty" else name
                    emit("Drop exclusivo da instancia: $label")
                }
            }
        }

        val classQuestUpdate = engine.classQuestService.onCombatOutcome(
            player = player,
            itemInstances = instances,
            monsterId = monster.archetypeId,
            isBoss = isBoss,
            monsterBaseType = monster.baseType
        )
        classQuestUpdate.messages.forEach { emit(it) }
        val classQuestGold = (classQuestUpdate.player.gold - player.gold).coerceAtLeast(0)
        player = classQuestUpdate.player
        if (classQuestGold > 0) {
            player = applyGoldEarnedAchievement(player, classQuestGold.toLong())
        }
        instances = classQuestUpdate.itemInstances

        return BattleOutcome(
            playerAfter = player,
            itemInstances = instances,
            victory = true,
            collectedItems = collectedItems
        )
    }
}
