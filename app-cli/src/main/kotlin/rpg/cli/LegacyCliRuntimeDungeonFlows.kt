// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.classquest.dungeon.ClassDungeonMonsterService
import rpg.cli.combat.DungeonCombatController
import rpg.combat.DungeonCombatSkillSupport
import rpg.events.DungeonChestEventHandler
import rpg.events.DungeonEventRouter
import rpg.events.DungeonEventService
import rpg.events.DungeonLiquidEventHandler
import rpg.events.DungeonNpcEventHandler
import rpg.quest.progress.CombatQuestProgressService

internal class LegacyCliRuntimeDungeonFlows(
    private val support: LegacyCliRuntimeSupportContext,
    private val inventoryProgressionFlows: LegacyCliRuntimeInventoryProgressionFlows
) {
    private val dungeonEventService = DungeonEventService(support.repo.dungeonEvents)

    val classDungeonMonsterFlow by lazy {
        ClassDungeonMonsterService(
            repo = support.repo,
            engine = support.engine
        )
    }

    val dungeonEntryFlow by lazy {
        DungeonEntryFlow(
            engine = support.engine,
            readMenuChoice = support.ioSupport::readMenuChoice,
            clearRunEffects = support.runResolutionSupport::clearRunEffects,
            autoSave = support::autoSave,
            emit = ::println
        )
    }

    val dungeonOutcomeFlow by lazy {
        DungeonOutcomeFlow(
            autoSave = support::autoSave,
            finalizeRun = support.runResolutionSupport::finalizeRun,
            applyDeathPenalty = support.runResolutionSupport::applyDeathPenalty,
            onRunCompleted = { board, runCount ->
                support.engine.questProgressTracker.onRunCompleted(board, runCount)
            },
            shouldCountRunCompletion = { run ->
                run.victoriesInRun >= 10 && run.bossesDefeatedInRun >= 1 && run.depth >= 10
            },
            emit = ::println
        )
    }

    val dungeonCombatQuestProgress by lazy {
        CombatQuestProgressService(engine = support.engine)
    }

    private val dungeonCombatSkillSupport by lazy {
        DungeonCombatSkillSupport(
            engine = support.engine,
            repo = support.repo,
            talentTreeService = support.talentTreeService,
            format = support::format,
            buildAmmoStacks = { itemIds, itemInstances, selectedTemplateId ->
                inventoryProgressionFlows.quiverFlow.buildAmmoStacks(itemIds, itemInstances, selectedTemplateId)
            }
        )
    }

    val dungeonCombatFlow by lazy {
        DungeonCombatFlow(
            engine = support.engine,
            format = support::format,
            itemDisplayLabelByResolved = { item -> inventoryProgressionFlows.inventoryDetailSupport.itemDisplayLabel(item) },
            itemDisplayLabelByNameAndRarity = { name, rarity ->
                inventoryProgressionFlows.inventoryDetailSupport.itemDisplayLabel(name, rarity)
            },
            applyBattleResolvedAchievement = { player, telemetry, victory, escaped, isBoss, monsterTypeId, monsterStars ->
                support.applyAchievementUpdate(
                    support.achievementTracker.onBattleResolved(
                        player = player,
                        telemetry = telemetry,
                        victory = victory,
                        escaped = escaped,
                        isBoss = isBoss,
                        monsterTypeId = monsterTypeId,
                        monsterStars = monsterStars
                    )
                )
            },
            applyGoldEarnedAchievement = { player, gold ->
                support.applyAchievementUpdate(support.achievementTracker.onGoldEarned(player, gold))
            },
            createController = {
                DungeonCombatController(
                    engine = support.engine,
                    skillSupport = dungeonCombatSkillSupport,
                    readInput = { readLine()?.trim() ?: throw LegacyCliRuntimeSupportContext.InputClosedException() },
                    format = support::format,
                    fixedContextLines = emptyList(),
                    ansiCombatReset = LegacyCliAnsiPalette.combatReset,
                    ansiCombatHeader = LegacyCliAnsiPalette.combatHeader,
                    ansiCombatPlayer = LegacyCliAnsiPalette.combatPlayer,
                    ansiCombatEnemy = LegacyCliAnsiPalette.combatEnemy,
                    ansiCombatLoading = LegacyCliAnsiPalette.combatLoading,
                    ansiCombatReady = LegacyCliAnsiPalette.combatReady,
                    ansiCombatBlocked = LegacyCliAnsiPalette.combatBlocked,
                    ansiCombatCasting = LegacyCliAnsiPalette.combatCasting,
                    ansiCombatPause = LegacyCliAnsiPalette.combatPause,
                    ansiClearLine = LegacyCliAnsiPalette.clearLine,
                    ansiClearToEnd = LegacyCliAnsiPalette.clearToEnd
                )
            },
            emit = ::println
        )
    }

    private val dungeonEventSupport by lazy {
        DungeonEventSupport(
            engine = support.engine,
            itemName = support.stateSupport::itemName,
            canonicalItemId = support::canonicalItemId,
            clampPlayerResources = support.runResolutionSupport::clampPlayerResources,
            onGoldEarned = { player, amount ->
                support.applyAchievementUpdate(support.achievementTracker.onGoldEarned(player, amount))
            },
            onGoldSpent = { player, amount ->
                support.applyAchievementUpdate(support.achievementTracker.onGoldSpent(player, amount))
            },
            emit = ::println
        )
    }

    private val dungeonNpcEventFlow by lazy {
        DungeonNpcEventFlow(
            engine = support.engine,
            dungeonEventService = dungeonEventService,
            readMenuChoice = support.ioSupport::readMenuChoice,
            eventContext = { player, itemInstances, depth ->
                support.engine.buildEventContext(player, itemInstances, depth)
            },
            support = dungeonEventSupport,
            buildInventoryStacks = { player, itemInstances ->
                inventoryProgressionFlows.inventoryFlow.buildInventoryStacks(player, itemInstances)
            },
            battleMonster = { playerState, itemInstances, monster, tier, lootCollector, isBoss ->
                dungeonCombatFlow.battleMonster(
                    playerState = playerState,
                    itemInstances = itemInstances,
                    monster = monster,
                    tier = tier,
                    lootCollector = lootCollector,
                    isBoss = isBoss,
                    classDungeon = null
                )
            },
            applyBattleQuestProgress = dungeonCombatQuestProgress::applyBattleQuestProgress,
            onGoldSpent = { player, amount ->
                support.applyAchievementUpdate(support.achievementTracker.onGoldSpent(player, amount))
            },
            emit = ::println
        )
    }

    private val dungeonLiquidEventFlow by lazy {
        DungeonLiquidEventFlow(
            engine = support.engine,
            dungeonEventService = dungeonEventService,
            readMenuChoice = support.ioSupport::readMenuChoice,
            eventContext = { player, itemInstances, depth ->
                support.engine.buildEventContext(player, itemInstances, depth)
            },
            support = dungeonEventSupport,
            emit = ::println
        )
    }

    private val dungeonChestEventFlow by lazy {
        DungeonChestEventFlow(
            engine = support.engine,
            dungeonEventService = dungeonEventService,
            readMenuChoice = support.ioSupport::readMenuChoice,
            eventContext = { player, itemInstances, depth ->
                support.engine.buildEventContext(player, itemInstances, depth)
            },
            runNpcAmbush = { player, itemInstances, loot, difficultyLevel, tier, questBoard ->
                dungeonNpcEventFlow.runNpcAmbush(
                    player = player,
                    itemInstances = itemInstances,
                    loot = loot,
                    difficultyLevel = difficultyLevel,
                    tier = tier,
                    questBoard = questBoard
                )
            },
            support = dungeonEventSupport,
            emit = ::println
        )
    }

    private val dungeonEventFlow by lazy {
        DungeonEventRouter(
            engine = support.engine,
            biomeNpcEventBonusPct = { biomeId ->
                biomeId?.let { support.repo.biomes[it]?.npcEventBonusPct ?: 0 } ?: 0
            },
            npcHandler = DungeonNpcEventHandler { player, itemInstances, loot, difficultyLevel, depth, tier, questBoard ->
                dungeonNpcEventFlow.npcEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
            },
            liquidHandler = DungeonLiquidEventHandler { player, itemInstances, depth, questBoard ->
                dungeonLiquidEventFlow.liquidEvent(player, itemInstances, depth, questBoard)
            },
            chestHandler = DungeonChestEventHandler { player, itemInstances, loot, difficultyLevel, depth, tier, questBoard ->
                dungeonChestEventFlow.chestEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
            }
        )
    }

    val dungeonRunFlow by lazy {
        DungeonRunFlow(
            engine = support.engine,
            roomTimeMinutes = LegacyCliRuntimeConfig.roomTimeMinutes,
            entryFlow = dungeonEntryFlow,
            outcomeFlow = dungeonOutcomeFlow,
            classDungeonMonsterFlow = classDungeonMonsterFlow,
            synchronizeQuestBoard = support.stateSupport::synchronizeQuestBoard,
            computePlayerStats = support.engine::computePlayerStats,
            battleMonster = dungeonCombatFlow::battleMonster,
            preBossSanctuaryRoom = support.dungeonPreparationSupport::preBossSanctuaryRoom,
            restRoom = support.dungeonPreparationSupport::restRoom,
            eventRoom = dungeonEventFlow::eventRoom,
            applyBattleQuestProgress = dungeonCombatQuestProgress::applyBattleQuestProgress,
            tickEffects = support.statusTimeSupport::tickEffects,
            promptContinue = support.dungeonPreparationSupport::promptContinue,
            finalizeRun = support.runResolutionSupport::finalizeRun,
            onRunCompleted = { board, runCount ->
                support.engine.questProgressTracker.onRunCompleted(board, runCount)
            },
            autoSave = support::autoSave,
            emit = ::println
        )
    }

    val explorationFlow by lazy {
        ExplorationExtraFlow(
            synchronizeClock = support.statusTimeSupport::synchronizeClock,
            synchronizeClassQuest = { current ->
                current.copy(player = support.engine.classQuestService.synchronize(current.player))
            },
            synchronizeAchievements = { current ->
                current.copy(player = support.achievementTracker.synchronize(current.player))
            },
            hasClassMapUnlocked = support.stateSupport::hasClassMapUnlocked,
            readMenuChoice = support.ioSupport::readMenuChoice,
            enterDungeon = { current, forceClassDungeon ->
                dungeonRunFlow.enterDungeon(current, forceClassDungeon)
            },
            emit = ::println
        )
    }
}
