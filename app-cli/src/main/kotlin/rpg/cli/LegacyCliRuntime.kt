
package rpg.cli

import java.nio.file.Paths
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min
import rpg.achievement.AchievementMenu
import rpg.achievement.AchievementService
import rpg.achievement.AchievementTierUnlockedNotification
import rpg.achievement.AchievementTracker
import rpg.achievement.AchievementUpdate
import rpg.classquest.ClassQuestDungeonDefinition
import rpg.classquest.ClassQuestMenu
import rpg.classquest.dungeon.ClassDungeonMonsterService
import rpg.classquest.progress.ClassProgressionSupport
import rpg.cli.combat.DungeonCombatController
import rpg.cli.input.CliIoSupport
import rpg.cli.model.*
import rpg.cli.text.TextFormattingSupport
import rpg.combat.DungeonCombatSkillSupport
import rpg.creation.CharacterCreationPreviewService
import rpg.dungeon.RunResolutionService
import rpg.engine.ComputedStats
import rpg.engine.GameEngine
import rpg.events.DungeonEventRouter
import rpg.io.DataRepository
import rpg.io.JsonStore
import rpg.events.DungeonEventService
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.WorldState
import rpg.monster.MonsterInstance
import rpg.quest.QuestInstance
import rpg.quest.progress.CombatQuestProgressService
import rpg.progression.AttributePointAllocator
import rpg.session.SessionBridge
import rpg.state.StateSyncService
import rpg.talent.TalentTreeService

internal class LegacyCliRuntime(private val repo: DataRepository) {
    private class InputClosedException : RuntimeException()

    private val engine = GameEngine(repo)
    private val dungeonEventService = DungeonEventService(repo.dungeonEvents)
    private val talentTreeService = TalentTreeService(repo.balance.talentPoints)
    private val classQuestMenu = ClassQuestMenu(engine.classQuestService)
    private val achievementService = AchievementService()
    private val achievementTracker = AchievementTracker(achievementService)
    private val achievementMenu = AchievementMenu(achievementService)
    private val characterCreationPreview = CharacterCreationPreviewService(repo)

    private val characterDef = repo.character
    private val accessorySlots = characterDef.accessorySlots
    private val offhandBlockedId = "__offhand_blocked__"

    // Rest room healing
    private val restHealPct = 0.20
    private val restRegenMultiplier = 3.0


    // Death penalty + debuff
    private val deathBaseLootLossPct = 80.0
    private val deathMinLootLossPct = 20.0
    private val deathDebuffPerStack = 0.20
    private val deathDebuffBaseMinutes = 10.0
    private val deathDebuffExtraMinutes = 5.0
    private val deathGoldLossPct = 0.20
    private val deathXpPenaltyPct = 20.0

    // In-combat/out-of-combat pacing (minutes)
    private val roomTimeMinutes = 0.5
    private val clockSyncEpsilonMs = 1000L

    private val tavernRestHealPct = 0.25
    private val questZoneId: ZoneId = ZoneId.systemDefault()

    private val ansiCombatReset = "\u001B[0m"
    private val ansiCombatHeader = "\u001B[37m"
    private val ansiCombatPlayer = "\u001B[36m"
    private val ansiCombatEnemy = "\u001B[31m"
    private val ansiCombatLoading = "\u001B[33m"
    private val ansiCombatReady = "\u001B[32m"
    private val ansiCombatBlocked = "\u001B[31m"
    private val ansiCombatCasting = "\u001B[36m"
    private val ansiCombatPause = "\u001B[35m"
    private val ansiClearLine = "\u001B[2K"
    private val ansiClearToEnd = "\u001B[J"
    private val ansiUiName = "\u001B[37m"
    private val ansiUiLevel = "\u001B[36m"
    private val ansiUiHp = "\u001B[32m"
    private val ansiUiMp = "\u001B[34m"
    private val ansiUiGold = "\u001B[33m"
    private val ansiUiCash = "\u001B[35m"
    private val ansiQuestActive = "\u001B[34m"
    private val ansiQuestReady = "\u001B[32m"
    private val ansiQuestAlert = "\u001B[33m"
    private val legacyIoSupport by lazy {
        CliIoSupport(
            onInputClosed = { throw InputClosedException() },
            readInputLine = { readLine() },
            printInline = { text -> print(text) },
            printLine = { text -> println(text) }
        )
    }
    private val legacyMenuFormattingSupport by lazy {
        TextFormattingSupport(
            ansiReset = ansiCombatReset,
            ansiAlert = ansiQuestAlert
        )
    }
    private val legacyStateSupport by lazy {
        StateSyncService(
            repo = repo,
            engine = engine,
            achievementTracker = achievementTracker,
            achievementService = achievementService,
            talentTreeService = talentTreeService
        )
    }
    private val legacyClassProgressionSupport by lazy {
        ClassProgressionSupport(
            repo = repo,
            engine = engine,
            talentTreeService = talentTreeService,
            achievementTracker = achievementTracker,
            applyAchievementUpdate = ::applyAchievementUpdate,
            notify = ::println
        )
    }
    private val legacyStatusTimeSupport by lazy {
        LegacyStatusTimeSupport(
            repo = repo,
            engine = engine,
            questZoneId = questZoneId,
            roomTimeMinutes = roomTimeMinutes,
            clockSyncEpsilonMs = clockSyncEpsilonMs,
            deathDebuffPerStack = deathDebuffPerStack,
            ansiUiName = ansiUiName,
            ansiUiLevel = ansiUiLevel,
            ansiUiHp = ansiUiHp,
            ansiUiMp = ansiUiMp,
            ansiUiGold = ansiUiGold,
            ansiUiCash = ansiUiCash,
            uiColor = { text, colorCode -> legacyMenuFormattingSupport.uiColor(text, colorCode) },
            computePlayerStats = { player, itemInstances ->
                engine.computePlayerStats(player, itemInstances)
            },
            format = ::format
        )
    }
    private val legacyRunResolutionSupport by lazy {
        RunResolutionService(
            engine = engine,
            deathBaseLootLossPct = deathBaseLootLossPct,
            deathMinLootLossPct = deathMinLootLossPct,
            deathDebuffBaseMinutes = deathDebuffBaseMinutes,
            deathDebuffExtraMinutes = deathDebuffExtraMinutes,
            deathGoldLossPct = deathGoldLossPct,
            deathXpPenaltyPct = deathXpPenaltyPct,
            applyAchievementUpdate = ::applyAchievementUpdate,
            onGoldEarned = { player, amount -> achievementTracker.onGoldEarned(player, amount) },
            onDeath = { player -> achievementTracker.onDeath(player) },
            notify = ::println,
            computePlayerStats = engine::computePlayerStats
        )
    }
    private val legacyDungeonPreparationSupport by lazy {
        DungeonPreparationSupport(
            repo = repo,
            restHealPct = restHealPct,
            restRegenMultiplier = restRegenMultiplier,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            computePlayerStats = { player, itemInstances ->
                engine.computePlayerStats(player, itemInstances)
            },
            applyHealing = { player, hpDelta, mpDelta, itemInstances ->
                legacyRunResolutionSupport.applyHealing(player, hpDelta, mpDelta, itemInstances)
            },
            applyRoomEffect = legacyRunResolutionSupport::applyRoomEffect,
            emit = ::println
        )
    }
    private val attributeMeta = listOf(
        AttrMeta("STR", "Forca"),
        AttrMeta("AGI", "Agilidade"),
        AttrMeta("DEX", "Destreza"),
        AttrMeta("VIT", "Vitalidade"),
        AttrMeta("INT", "Inteligencia"),
        AttrMeta("SPR", "Espirito"),
        AttrMeta("LUK", "Sorte")
    )
    private val legacyAttributePointSupport by lazy {
        AttributePointAllocator(
            readInt = legacyIoSupport::readInt,
            clampPlayerResources = legacyRunResolutionSupport::clampPlayerResources,
            notify = ::println
        )
    }
    private val legacyInventoryDetailSupport by lazy {
        LegacyInventoryDetailSupport(
            repo = repo,
            engine = engine,
            accessorySlots = accessorySlots,
            offhandBlockedId = offhandBlockedId,
            ansiCombatReset = ansiCombatReset,
            computePlayerStats = { player, itemInstances ->
                engine.computePlayerStats(player, itemInstances)
            },
            canonicalItemId = ::canonicalItemId,
            equippedSlotLabel = legacyStateSupport::equippedSlotLabel,
            format = ::format,
            formatSigned = ::formatSigned,
            formatSignedDouble = ::formatSignedDouble
        )
    }
    private val legacyItemUseSellFlow by lazy {
        LegacyItemUseSellFlow(
            engine = engine,
            choose = legacyIoSupport::choose,
            readInt = legacyIoSupport::readInt,
            computePlayerStats = { player, itemInstances ->
                engine.computePlayerStats(player, itemInstances)
            },
            applyHealing = { player, hpDelta, mpDelta, itemInstances ->
                legacyRunResolutionSupport.applyHealing(player, hpDelta, mpDelta, itemInstances)
            },
            applyRoomEffect = legacyRunResolutionSupport::applyRoomEffect,
            normalizePlayerStorage = legacyStateSupport::normalizePlayerStorage,
            onGoldEarned = { player, amount ->
                applyAchievementUpdate(achievementTracker.onGoldEarned(player, amount))
            }
        )
    }
    private val legacyQuiverFlow by lazy {
        LegacyQuiverFlow(
            engine = engine,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            readInt = legacyIoSupport::readInt,
            normalizePlayerStorage = legacyStateSupport::normalizePlayerStorage,
            itemDisplayLabel = { item -> legacyInventoryDetailSupport.itemDisplayLabel(item) },
            sellInventoryItem = { player, itemInstances, itemIds, itemName, saleValue, quantity ->
                legacyItemUseSellFlow.sellInventoryItem(
                    player = player,
                    itemInstances = itemInstances,
                    itemIds = itemIds,
                    itemName = itemName,
                    saleValue = saleValue,
                    quantity = quantity
                )
            },
            sellQuiverAmmo = { player, itemInstances, itemIds, itemName, saleValue, quantity ->
                legacyItemUseSellFlow.sellQuiverAmmo(
                    player = player,
                    itemInstances = itemInstances,
                    itemIds = itemIds,
                    itemName = itemName,
                    saleValue = saleValue,
                    quantity = quantity
                )
            },
            chooseSellQuantity = { maxQuantity ->
                legacyItemUseSellFlow.chooseSellQuantity(maxQuantity)
            }
        )
    }
    private val legacyEquipmentFlow by lazy {
        LegacyEquipmentFlow(
            engine = engine,
            accessorySlots = accessorySlots,
            offhandBlockedId = offhandBlockedId,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            readInt = legacyIoSupport::readInt,
            autoSave = ::autoSave,
            computePlayerStats = { player, itemInstances ->
                engine.computePlayerStats(player, itemInstances)
            },
            normalizePlayerStorage = legacyStateSupport::normalizePlayerStorage,
            clampPlayerResources = legacyRunResolutionSupport::clampPlayerResources,
            format = ::format,
            formatSignedDouble = ::formatSignedDouble,
            detailSupport = legacyInventoryDetailSupport,
            itemDisplayLabel = { item -> legacyInventoryDetailSupport.itemDisplayLabel(item) },
            equippedSlotLabel = legacyStateSupport::equippedSlotLabel
        )
    }
    private val legacyInventoryFlow by lazy {
        LegacyInventoryFlow(
            engine = engine,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            autoSave = ::autoSave,
            normalizePlayerStorage = legacyStateSupport::normalizePlayerStorage,
            detailSupport = legacyInventoryDetailSupport,
            equipmentFlow = legacyEquipmentFlow,
            quiverFlow = legacyQuiverFlow,
            itemUseSellFlow = legacyItemUseSellFlow
        )
    }
    private val legacyTalentFlow by lazy {
        LegacyTalentFlow(
            repo = repo,
            engine = engine,
            talentTreeService = talentTreeService,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            menuAlert = legacyMenuFormattingSupport::menuAlert,
            labelWithAlert = legacyMenuFormattingSupport::labelWithAlert,
            hasTalentPointsAvailable = legacyStateSupport::hasTalentPointsAvailable
        )
    }
    private val legacyQuestFlowSupport by lazy {
        LegacyQuestFlowSupport(
            itemName = legacyStateSupport::itemName,
            uiColor = legacyMenuFormattingSupport::uiColor,
            ansiQuestActive = ansiQuestActive,
            ansiQuestReady = ansiQuestReady,
            questZoneId = questZoneId
        )
    }
    private val legacyQuestFlow by lazy {
        LegacyQuestFlow(
            engine = engine,
            classQuestMenu = classQuestMenu,
            achievementTracker = achievementTracker,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            autoSave = ::autoSave,
            synchronizeQuestBoard = legacyStateSupport::synchronizeQuestBoard,
            applyAchievementUpdate = ::applyAchievementUpdate,
            uiColor = legacyMenuFormattingSupport::uiColor,
            ansiQuestAlert = ansiQuestAlert,
            support = legacyQuestFlowSupport
        )
    }
    private val legacyAchievementFlow by lazy {
        LegacyAchievementFlow(
            repo = repo,
            achievementService = achievementService,
            achievementTracker = achievementTracker,
            achievementMenu = achievementMenu,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            autoSave = ::autoSave,
            uiColor = legacyMenuFormattingSupport::uiColor,
            labelWithAlert = legacyMenuFormattingSupport::labelWithAlert,
            ansiQuestAlert = ansiQuestAlert,
            pauseForEnter = {
                readLine() ?: throw InputClosedException()
            },
            showAchievementNotifications = ::showAchievementNotifications
        )
    }
    private val legacyClassDungeonMonsterFlow by lazy {
        ClassDungeonMonsterService(
            repo = repo,
            engine = engine
        )
    }
    private val legacyDungeonEntryFlow by lazy {
        DungeonEntryFlow(
            engine = engine,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            clearRunEffects = legacyRunResolutionSupport::clearRunEffects,
            autoSave = ::autoSave,
            emit = ::println
        )
    }
    private val legacyDungeonOutcomeFlow by lazy {
        DungeonOutcomeFlow(
            autoSave = ::autoSave,
            finalizeRun = legacyRunResolutionSupport::finalizeRun,
            applyDeathPenalty = legacyRunResolutionSupport::applyDeathPenalty,
            onRunCompleted = { board, runCount ->
                engine.questProgressTracker.onRunCompleted(board, runCount)
            },
            emit = ::println
        )
    }
    private val legacyDungeonCombatQuestProgress by lazy {
        CombatQuestProgressService(engine = engine)
    }
    private val legacyDungeonCombatSkillSupport by lazy {
        DungeonCombatSkillSupport(
            engine = engine,
            repo = repo,
            talentTreeService = talentTreeService,
            format = ::format,
            buildAmmoStacks = { itemIds, itemInstances, selectedTemplateId ->
                legacyQuiverFlow.buildAmmoStacks(itemIds, itemInstances, selectedTemplateId)
            }
        )
    }
    private val legacyDungeonCombatFlow by lazy {
        DungeonCombatFlow(
            engine = engine,
            format = ::format,
            itemDisplayLabelByResolved = { item -> legacyInventoryDetailSupport.itemDisplayLabel(item) },
            itemDisplayLabelByNameAndRarity = { name, rarity ->
                legacyInventoryDetailSupport.itemDisplayLabel(name, rarity)
            },
            applyBattleResolvedAchievement = { player, telemetry, victory, escaped, isBoss, monsterBaseType, monsterStars ->
                applyAchievementUpdate(
                    achievementTracker.onBattleResolved(
                        player = player,
                        telemetry = telemetry,
                        victory = victory,
                        escaped = escaped,
                        isBoss = isBoss,
                        monsterBaseType = monsterBaseType,
                        monsterStars = monsterStars
                    )
                )
            },
            applyGoldEarnedAchievement = { player, gold ->
                applyAchievementUpdate(
                    achievementTracker.onGoldEarned(player, gold)
                )
            },
            createController = {
                DungeonCombatController(
                    engine = engine,
                    skillSupport = legacyDungeonCombatSkillSupport,
                    readInput = { readLine()?.trim() ?: throw InputClosedException() },
                    format = ::format,
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
            },
            emit = ::println
        )
    }
    private val legacyDungeonEventSupport by lazy {
        DungeonEventSupport(
            engine = engine,
            itemName = legacyStateSupport::itemName,
            canonicalItemId = ::canonicalItemId,
            clampPlayerResources = legacyRunResolutionSupport::clampPlayerResources,
            onGoldEarned = { player, amount ->
                applyAchievementUpdate(achievementTracker.onGoldEarned(player, amount))
            },
            onGoldSpent = { player, amount ->
                applyAchievementUpdate(achievementTracker.onGoldSpent(player, amount))
            },
            emit = ::println
        )
    }
    private val legacyDungeonNpcEventFlow by lazy {
        DungeonNpcEventFlow(
            engine = engine,
            dungeonEventService = dungeonEventService,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            eventContext = { player, itemInstances, depth ->
                engine.buildEventContext(player, itemInstances, depth)
            },
            support = legacyDungeonEventSupport,
            buildInventoryStacks = { player, itemInstances ->
                legacyInventoryFlow.buildInventoryStacks(player, itemInstances)
            },
            battleMonster = { playerState, itemInstances, monster, tier, lootCollector, isBoss ->
                legacyDungeonCombatFlow.battleMonster(
                    playerState = playerState,
                    itemInstances = itemInstances,
                    monster = monster,
                    tier = tier,
                    lootCollector = lootCollector,
                    isBoss = isBoss,
                    classDungeon = null
                )
            },
            applyBattleQuestProgress = legacyDungeonCombatQuestProgress::applyBattleQuestProgress,
            onGoldSpent = { player, amount ->
                applyAchievementUpdate(achievementTracker.onGoldSpent(player, amount))
            },
            emit = ::println
        )
    }
    private val legacyDungeonLiquidEventFlow by lazy {
        DungeonLiquidEventFlow(
            engine = engine,
            dungeonEventService = dungeonEventService,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            eventContext = { player, itemInstances, depth ->
                engine.buildEventContext(player, itemInstances, depth)
            },
            support = legacyDungeonEventSupport,
            emit = ::println
        )
    }
    private val legacyDungeonChestEventFlow by lazy {
        DungeonChestEventFlow(
            engine = engine,
            dungeonEventService = dungeonEventService,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            eventContext = { player, itemInstances, depth ->
                engine.buildEventContext(player, itemInstances, depth)
            },
            runNpcAmbush = { player, itemInstances, loot, difficultyLevel, tier, questBoard ->
                legacyDungeonNpcEventFlow.runNpcAmbush(
                    player = player,
                    itemInstances = itemInstances,
                    loot = loot,
                    difficultyLevel = difficultyLevel,
                    tier = tier,
                    questBoard = questBoard
                )
            },
            support = legacyDungeonEventSupport,
            emit = ::println
        )
    }
    private val legacyDungeonEventFlow by lazy {
        DungeonEventRouter(
            engine = engine,
            biomeNpcEventBonusPct = { biomeId ->
                biomeId?.let { repo.biomes[it]?.npcEventBonusPct ?: 0 } ?: 0
            },
            npcFlow = legacyDungeonNpcEventFlow,
            liquidFlow = legacyDungeonLiquidEventFlow,
            chestFlow = legacyDungeonChestEventFlow
        )
    }
    private val legacyDungeonRunFlow by lazy {
        DungeonRunFlow(
            engine = engine,
            roomTimeMinutes = roomTimeMinutes,
            entryFlow = legacyDungeonEntryFlow,
            outcomeFlow = legacyDungeonOutcomeFlow,
            classDungeonMonsterFlow = legacyClassDungeonMonsterFlow,
            synchronizeQuestBoard = legacyStateSupport::synchronizeQuestBoard,
            computePlayerStats = engine::computePlayerStats,
            battleMonster = legacyDungeonCombatFlow::battleMonster,
            preBossSanctuaryRoom = legacyDungeonPreparationSupport::preBossSanctuaryRoom,
            restRoom = legacyDungeonPreparationSupport::restRoom,
            eventRoom = legacyDungeonEventFlow::eventRoom,
            applyBattleQuestProgress = legacyDungeonCombatQuestProgress::applyBattleQuestProgress,
            tickEffects = legacyStatusTimeSupport::tickEffects,
            promptContinue = legacyDungeonPreparationSupport::promptContinue,
            finalizeRun = legacyRunResolutionSupport::finalizeRun,
            onRunCompleted = { board, runCount ->
                engine.questProgressTracker.onRunCompleted(board, runCount)
            },
            autoSave = ::autoSave,
            emit = ::println
        )
    }
    private val legacyExplorationFlow by lazy {
        ExplorationExtraFlow(
            synchronizeClock = legacyStatusTimeSupport::synchronizeClock,
            synchronizeClassQuest = { current ->
                current.copy(player = engine.classQuestService.synchronize(current.player))
            },
            synchronizeAchievements = { current ->
                current.copy(player = achievementTracker.synchronize(current.player))
            },
            hasClassMapUnlocked = legacyStateSupport::hasClassMapUnlocked,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            enterDungeon = { current, forceClassDungeon ->
                legacyDungeonRunFlow.enterDungeon(current, forceClassDungeon)
            },
            emit = ::println
        )
    }
    private val legacyProductionFlow by lazy {
        LegacyProductionFlow(
            engine = engine,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            readInt = legacyIoSupport::readInt,
            runProgressBar = legacyStatusTimeSupport::runProgressBar,
            format = ::format,
            itemName = legacyStateSupport::itemName,
            uiColor = legacyMenuFormattingSupport::uiColor,
            ansiUiHp = ansiUiHp,
            onGoldEarned = { player, amount ->
                applyAchievementUpdate(achievementTracker.onGoldEarned(player, amount))
            },
            advanceOutOfCombatTime = legacyStatusTimeSupport::advanceOutOfCombatTime,
            synchronizeQuestBoard = legacyStateSupport::synchronizeQuestBoard,
            autoSave = ::autoSave
        )
    }
    private val legacyCityServicesFlow by lazy {
        LegacyCityServicesFlow(
            engine = engine,
            repo = repo,
            tavernRestHealPct = tavernRestHealPct,
            deathDebuffBaseMinutes = deathDebuffBaseMinutes,
            deathDebuffExtraMinutes = deathDebuffExtraMinutes,
            deathXpPenaltyPct = deathXpPenaltyPct,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            autoSave = ::autoSave,
            computePlayerStats = engine::computePlayerStats,
            applyGoldSpent = { player, amount ->
                applyAchievementUpdate(achievementTracker.onGoldSpent(player, amount))
            },
            applyFullRestSleep = { player ->
                applyAchievementUpdate(achievementTracker.onFullRestSleep(player))
            }
        )
    }
    private val legacyHubFlow by lazy {
        LegacyHubFlow(
            readMenuChoice = legacyIoSupport::readMenuChoice,
            synchronizeClock = legacyStatusTimeSupport::synchronizeClock,
            normalizeLoadedState = legacyStateSupport::normalizeLoadedState,
            synchronizeClassQuest = { current ->
                current.copy(player = engine.classQuestService.synchronize(current.player))
            },
            synchronizeAchievements = { current ->
                current.copy(player = achievementTracker.synchronize(current.player))
            },
            synchronizeQuestBoard = { current ->
                current.copy(
                    questBoard = legacyStateSupport.synchronizeQuestBoard(
                        current.questBoard,
                        current.player,
                        current.itemInstances
                    )
                )
            },
            checkSubclassUnlock = legacyClassProgressionSupport::checkSubclassUnlock,
            checkSpecializationUnlock = legacyClassProgressionSupport::checkSpecializationUnlock,
            showClock = legacyStatusTimeSupport::showClock,
            showStatus = legacyStatusTimeSupport::showStatus,
            showDebuff = legacyStatusTimeSupport::showDebuff,
            menuAlert = legacyMenuFormattingSupport::menuAlert,
            labelWithAlert = legacyMenuFormattingSupport::labelWithAlert,
            hasUnspentAttributePoints = legacyStateSupport::hasUnspentAttributePoints,
            hasTalentPointsAvailable = legacyStateSupport::hasTalentPointsAvailable,
            hasReadyToClaim = { board -> legacyStateSupport.hasReadyToClaim(board) },
            hasAchievementRewardReady = legacyStateSupport::hasAchievementRewardReady,
            openExploreMenu = legacyExplorationFlow::openExploreMenu,
            openProductionMenu = legacyProductionFlow::openProductionMenu,
            openCityMenu = legacyCityServicesFlow::openCityMenu,
            saveGame = ::saveGame,
            openEquipped = legacyEquipmentFlow::openEquipped,
            openInventory = legacyInventoryFlow::openInventory,
            allocateUnspentPoints = legacyAttributePointSupport::allocateUnspentPoints,
            openTalents = legacyTalentFlow::openTalents,
            openQuestBoard = legacyQuestFlow::openQuestBoard,
            openAchievementMenu = legacyAchievementFlow::openAchievementMenu
        )
    }
    private val legacySessionBridge by lazy {
        SessionBridge(
            repo = repo,
            engine = engine,
            characterDef = characterDef,
            characterCreationPreview = characterCreationPreview,
            attributeMeta = attributeMeta,
            readNonEmpty = legacyIoSupport::readNonEmpty,
            readMenuChoice = legacyIoSupport::readMenuChoice,
            chooseSave = legacyIoSupport::choose,
            format = ::format,
            applyTwoHandedLoadout = legacyEquipmentFlow::applyTwoHandedLoadout,
            computePlayerStats = engine::computePlayerStats,
            ensureSkillProgress = { player -> engine.skillSystem.ensureProgress(player) },
            synchronizeAchievements = { player -> achievementTracker.synchronize(player) },
            synchronizeClock = legacyStatusTimeSupport::synchronizeClock,
            normalizeLoadedState = legacyStateSupport::normalizeLoadedState,
            hubLoop = legacyHubFlow::hubLoop,
            notify = ::println
        )
    }

    fun run() {
        println("=== RPG TXT ===")
        try {
            while (true) {
                println("\n1. Novo jogo")
                println("2. Carregar jogo")
                println("x. Sair")
                when (legacyIoSupport.readMenuChoice("Escolha: ", 1, 2)) {
                    1 -> legacySessionBridge.newGame()
                    2 -> legacySessionBridge.loadGame()
                    null -> return
                }
            }
        } catch (_: InputClosedException) {
            println("\nEntrada encerrada. Encerrando jogo.")
        }
    }

    fun runNewGameFlow(): GameState? {
        try {
            return legacySessionBridge.newGame()
        } catch (_: InputClosedException) {
            println("\nEntrada encerrada. Encerrando jogo.")
            return null
        }
    }

    fun runExplorationFromState(initialState: GameState): GameState? {
        return runLegacySection(initialState, legacyExplorationFlow::openExploreMenu)
    }

    fun runProductionFromState(initialState: GameState): GameState? {
        return runLegacySection(initialState, legacyProductionFlow::openProductionMenu)
    }

    fun runCityFromState(initialState: GameState): GameState? {
        return runLegacySection(initialState, legacyCityServicesFlow::openCityMenu)
    }

    private fun runLegacySection(
        initialState: GameState,
        block: (GameState) -> GameState
    ): GameState? {
        return try {
            legacySessionBridge.runLegacySection(initialState, block)
        } catch (_: InputClosedException) {
            println("\nEntrada encerrada. Encerrando jogo.")
            null
        }
    }

    private fun autoSave(state: GameState) {
        val path = Paths.get("data", "saves", "autosave.json")
        val synced = legacyStatusTimeSupport.synchronizeClock(state)
        JsonStore.save(path, synced.copy(currentRun = null))
    }

    private fun saveGame(state: GameState) {
        val name = legacyIoSupport.readNonEmpty("Nome do save (ex: save1): ")
        val path = Paths.get("data", "saves", "$name.json")
        val synced = legacyStatusTimeSupport.synchronizeClock(state)
        JsonStore.save(path, synced.copy(currentRun = null))
        println("Save criado em ${path.fileName}.")
    }

    private fun canonicalItemId(
        itemId: String,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): String {
        return itemInstances[itemId]?.templateId ?: itemId
    }

    private fun formatSignedDouble(value: Double): String {
        val rounded = "%.1f".format(value)
        return if (value >= 0.0) "+$rounded" else rounded
    }

    private fun format(value: Double): String = "%.1f".format(value)

    private fun applyAchievementUpdate(update: AchievementUpdate): PlayerState {
        showAchievementNotifications(update.unlockedTiers)
        return update.player
    }

    private fun showAchievementNotifications(notifications: List<AchievementTierUnlockedNotification>) {
        if (notifications.isEmpty()) return
        for (notification in notifications) {
            println(legacyMenuFormattingSupport.uiColor("(!) Conquista concluida (!)", ansiQuestAlert))
            println(notification.displayName)
            println(notification.displayDescription)
            println("Recompensa disponivel: ${notification.rewardGold} ouro")
        }
    }

    private fun formatSigned(value: Int): String = if (value >= 0) "+$value" else value.toString()

}
