// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.classquest.progress.ClassProgressionSupport

internal class LegacyCliRuntimeInventoryProgressionFlows(
    private val support: LegacyCliRuntimeSupportContext
) {
    val inventoryDetailSupport by lazy {
        LegacyInventoryDetailSupport(
            repo = support.repo,
            engine = support.engine,
            accessorySlots = support.characterDef.accessorySlots,
            offhandBlockedId = LegacyCliRuntimeConfig.offhandBlockedId,
            ansiCombatReset = LegacyCliAnsiPalette.combatReset,
            computePlayerStats = { player, itemInstances ->
                support.engine.computePlayerStats(player, itemInstances)
            },
            canonicalItemId = support::canonicalItemId,
            equippedSlotLabel = support.stateSupport::equippedSlotLabel,
            format = support::format,
            formatSigned = support::formatSigned,
            formatSignedDouble = support::formatSignedDouble
        )
    }

    val itemUseSellFlow by lazy {
        LegacyItemUseSellFlow(
            engine = support.engine,
            choose = support.ioSupport::choose,
            readInt = support.ioSupport::readInt,
            computePlayerStats = { player, itemInstances ->
                support.engine.computePlayerStats(player, itemInstances)
            },
            applyHealing = { player, hpDelta, mpDelta, itemInstances ->
                support.runResolutionSupport.applyHealing(player, hpDelta, mpDelta, itemInstances)
            },
            applyRoomEffect = support.runResolutionSupport::applyRoomEffect,
            normalizePlayerStorage = support.stateSupport::normalizePlayerStorage,
            onGoldEarned = { player, amount ->
                support.applyAchievementUpdate(support.achievementTracker.onGoldEarned(player, amount))
            }
        )
    }

    val quiverFlow by lazy {
        LegacyQuiverFlow(
            engine = support.engine,
            readMenuChoice = support.ioSupport::readMenuChoice,
            readInt = support.ioSupport::readInt,
            normalizePlayerStorage = support.stateSupport::normalizePlayerStorage,
            itemDisplayLabel = { item -> inventoryDetailSupport.itemDisplayLabel(item) },
            sellInventoryItem = { player, itemInstances, itemIds, itemName, saleValue, quantity ->
                itemUseSellFlow.sellInventoryItem(
                    player = player,
                    itemInstances = itemInstances,
                    itemIds = itemIds,
                    itemName = itemName,
                    saleValue = saleValue,
                    quantity = quantity
                )
            },
            sellQuiverAmmo = { player, itemInstances, itemIds, itemName, saleValue, quantity ->
                itemUseSellFlow.sellQuiverAmmo(
                    player = player,
                    itemInstances = itemInstances,
                    itemIds = itemIds,
                    itemName = itemName,
                    saleValue = saleValue,
                    quantity = quantity
                )
            },
            chooseSellQuantity = { maxQuantity ->
                itemUseSellFlow.chooseSellQuantity(maxQuantity)
            }
        )
    }

    val equipmentFlow by lazy {
        LegacyEquipmentFlow(
            engine = support.engine,
            accessorySlots = support.characterDef.accessorySlots,
            offhandBlockedId = LegacyCliRuntimeConfig.offhandBlockedId,
            readMenuChoice = support.ioSupport::readMenuChoice,
            readInt = support.ioSupport::readInt,
            autoSave = support::autoSave,
            computePlayerStats = { player, itemInstances ->
                support.engine.computePlayerStats(player, itemInstances)
            },
            normalizePlayerStorage = support.stateSupport::normalizePlayerStorage,
            clampPlayerResources = support.runResolutionSupport::clampPlayerResources,
            format = support::format,
            formatSignedDouble = support::formatSignedDouble,
            detailSupport = inventoryDetailSupport,
            itemDisplayLabel = { item -> inventoryDetailSupport.itemDisplayLabel(item) },
            equippedSlotLabel = support.stateSupport::equippedSlotLabel
        )
    }

    val inventoryFlow by lazy {
        LegacyInventoryFlow(
            engine = support.engine,
            readMenuChoice = support.ioSupport::readMenuChoice,
            autoSave = support::autoSave,
            normalizePlayerStorage = support.stateSupport::normalizePlayerStorage,
            detailSupport = inventoryDetailSupport,
            equipmentFlow = equipmentFlow,
            quiverFlow = quiverFlow,
            itemUseSellFlow = itemUseSellFlow
        )
    }

    val talentFlow by lazy {
        LegacyTalentFlow(
            repo = support.repo,
            engine = support.engine,
            talentTreeService = support.talentTreeService,
            readMenuChoice = support.ioSupport::readMenuChoice,
            menuAlert = support.menuFormattingSupport::menuAlert,
            labelWithAlert = support.menuFormattingSupport::labelWithAlert,
            hasTalentPointsAvailable = support.stateSupport::hasTalentPointsAvailable
        )
    }

    private val questFlowSupport by lazy {
        LegacyQuestFlowSupport(
            itemName = support.stateSupport::itemName,
            uiColor = support.menuFormattingSupport::uiColor,
            ansiQuestActive = LegacyCliAnsiPalette.questActive,
            ansiQuestReady = LegacyCliAnsiPalette.questReady,
            questZoneId = support.questZoneId
        )
    }

    val questFlow by lazy {
        LegacyQuestFlow(
            engine = support.engine,
            classQuestMenu = support.classQuestMenu,
            achievementTracker = support.achievementTracker,
            readMenuChoice = support.ioSupport::readMenuChoice,
            autoSave = support::autoSave,
            synchronizeQuestBoard = support.stateSupport::synchronizeQuestBoard,
            applyAchievementUpdate = support::applyAchievementUpdate,
            uiColor = support.menuFormattingSupport::uiColor,
            ansiQuestAlert = LegacyCliAnsiPalette.questAlert,
            support = questFlowSupport
        )
    }

    val achievementFlow by lazy {
        LegacyAchievementFlow(
            repo = support.repo,
            achievementService = support.achievementService,
            achievementTracker = support.achievementTracker,
            achievementMenu = support.achievementMenu,
            readMenuChoice = support.ioSupport::readMenuChoice,
            autoSave = support::autoSave,
            uiColor = support.menuFormattingSupport::uiColor,
            labelWithAlert = support.menuFormattingSupport::labelWithAlert,
            ansiQuestAlert = LegacyCliAnsiPalette.questAlert,
            pauseForEnter = {
                readLine() ?: throw LegacyCliRuntimeSupportContext.InputClosedException()
            },
            showAchievementNotifications = support::showAchievementNotifications
        )
    }

    val productionFlow by lazy {
        LegacyProductionFlow(
            engine = support.engine,
            readMenuChoice = support.ioSupport::readMenuChoice,
            readInt = support.ioSupport::readInt,
            clearScreen = support.ioSupport::clearScreen,
            runProgressBar = support.statusTimeSupport::runProgressBar,
            format = support::format,
            itemName = support.stateSupport::itemName,
            uiColor = support.menuFormattingSupport::uiColor,
            ansiUiHp = LegacyCliAnsiPalette.uiHp,
            onGoldEarned = { player, amount ->
                support.applyAchievementUpdate(support.achievementTracker.onGoldEarned(player, amount))
            },
            advanceOutOfCombatTime = support.statusTimeSupport::advanceOutOfCombatTime,
            synchronizeQuestBoard = support.stateSupport::synchronizeQuestBoard,
            autoSave = support::autoSave
        )
    }

    val cityServicesFlow by lazy {
        LegacyCityServicesFlow(
            engine = support.engine,
            repo = support.repo,
            cityRulesSupport = support.cityRulesSupport,
            permanentUpgradeService = support.engine.permanentUpgradeService,
            readMenuChoice = support.ioSupport::readMenuChoice,
            clearScreen = support.ioSupport::clearScreen,
            autoSave = support::autoSave,
            computePlayerStats = support.engine::computePlayerStats,
            applyGoldSpent = { player, amount ->
                support.applyAchievementUpdate(support.achievementTracker.onGoldSpent(player, amount))
            },
            applyFullRestSleep = { player ->
                support.applyAchievementUpdate(support.achievementTracker.onFullRestSleep(player))
            }
        )
    }

    val checkClassProgression: ClassProgressionSupport
        get() = support.classProgressionSupport
}
