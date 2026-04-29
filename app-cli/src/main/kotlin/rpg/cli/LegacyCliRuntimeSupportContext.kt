// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import java.nio.file.Paths
import java.time.ZoneId
import rpg.achievement.AchievementMenu
import rpg.achievement.AchievementService
import rpg.achievement.AchievementTierUnlockedNotification
import rpg.achievement.AchievementTracker
import rpg.achievement.AchievementUpdate
import rpg.classquest.ClassQuestMenu
import rpg.classquest.progress.ClassProgressionSupport
import rpg.application.city.CityRulesSupport
import rpg.cli.input.CliIoSupport
import rpg.cli.model.AttrMeta
import rpg.cli.text.TextFormattingSupport
import rpg.creation.CharacterCreationPreviewService
import rpg.dungeon.RunResolutionService
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.io.JsonStore
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.progression.AttributePointAllocator
import rpg.state.StateSyncService
import rpg.talent.TalentTreeService

internal class LegacyCliRuntimeSupportContext(
    val repo: DataRepository
) {
    class InputClosedException : RuntimeException()

    val engine = GameEngine(repo)
    val talentTreeService = TalentTreeService(repo.balance.talentPoints)
    val classQuestMenu = ClassQuestMenu(engine.classQuestService)
    val achievementService = AchievementService()
    val achievementTracker = AchievementTracker(achievementService)
    val achievementMenu = AchievementMenu(achievementService)
    val characterCreationPreview = CharacterCreationPreviewService(repo)
    val questZoneId: ZoneId = ZoneId.systemDefault()
    val characterDef = repo.character
    val attributeMeta: List<AttrMeta> = LegacyCliRuntimeConfig.attributeMeta

    val ioSupport by lazy {
        CliIoSupport(
            onInputClosed = { throw InputClosedException() },
            readInputLine = { readLine() },
            printInline = { text -> print(text) },
            printLine = { text -> println(text) }
        )
    }

    val menuFormattingSupport by lazy {
        TextFormattingSupport(
            ansiReset = LegacyCliAnsiPalette.combatReset,
            ansiAlert = LegacyCliAnsiPalette.questAlert
        )
    }

    val stateSupport by lazy {
        StateSyncService(
            repo = repo,
            engine = engine,
            achievementTracker = achievementTracker,
            achievementService = achievementService,
            talentTreeService = talentTreeService
        )
    }

    val classProgressionSupport by lazy {
        ClassProgressionSupport(
            repo = repo,
            engine = engine,
            talentTreeService = talentTreeService,
            achievementTracker = achievementTracker,
            applyAchievementUpdate = ::applyAchievementUpdate,
            notify = ::println
        )
    }

    val cityRulesSupport by lazy {
        CityRulesSupport(engine = engine)
    }

    val statusTimeSupport by lazy {
        LegacyStatusTimeSupport(
            repo = repo,
            engine = engine,
            questZoneId = questZoneId,
            roomTimeMinutes = LegacyCliRuntimeConfig.roomTimeMinutes,
            clockSyncEpsilonMs = LegacyCliRuntimeConfig.clockSyncEpsilonMs,
            deathDebuffPerStack = LegacyCliRuntimeConfig.deathDebuffPerStack,
            ansiUiName = LegacyCliAnsiPalette.uiName,
            ansiUiLevel = LegacyCliAnsiPalette.uiLevel,
            ansiUiHp = LegacyCliAnsiPalette.uiHp,
            ansiUiMp = LegacyCliAnsiPalette.uiMp,
            ansiUiGold = LegacyCliAnsiPalette.uiGold,
            ansiUiCash = LegacyCliAnsiPalette.uiCash,
            uiColor = { text, colorCode -> menuFormattingSupport.uiColor(text, colorCode) },
            computePlayerStats = { player, itemInstances ->
                engine.computePlayerStats(player, itemInstances)
            },
            format = ::format
        )
    }

    val runResolutionSupport by lazy {
        RunResolutionService(
            engine = engine,
            deathBaseLootLossPct = LegacyCliRuntimeConfig.deathBaseLootLossPct,
            deathMinLootLossPct = LegacyCliRuntimeConfig.deathMinLootLossPct,
            deathDebuffBaseMinutes = LegacyCliRuntimeConfig.deathDebuffBaseMinutes,
            deathDebuffExtraMinutes = LegacyCliRuntimeConfig.deathDebuffExtraMinutes,
            deathGoldLossPct = LegacyCliRuntimeConfig.deathGoldLossPct,
            deathXpPenaltyPct = LegacyCliRuntimeConfig.deathXpPenaltyPct,
            applyAchievementUpdate = ::applyAchievementUpdate,
            onGoldEarned = { player, amount -> achievementTracker.onGoldEarned(player, amount) },
            onDeath = { player -> achievementTracker.onDeath(player) },
            notify = ::println,
            computePlayerStats = engine::computePlayerStats
        )
    }

    val dungeonPreparationSupport by lazy {
        DungeonPreparationSupport(
            repo = repo,
            restHealPct = LegacyCliRuntimeConfig.restHealPct,
            restRegenMultiplier = LegacyCliRuntimeConfig.restRegenMultiplier,
            readMenuChoice = ioSupport::readMenuChoice,
            computePlayerStats = { player, itemInstances ->
                engine.computePlayerStats(player, itemInstances)
            },
            applyHealing = { player, hpDelta, mpDelta, itemInstances ->
                runResolutionSupport.applyHealing(player, hpDelta, mpDelta, itemInstances)
            },
            applyRoomEffect = runResolutionSupport::applyRoomEffect,
            emit = ::println
        )
    }

    val attributePointSupport by lazy {
        AttributePointAllocator(
            readInt = ioSupport::readInt,
            clampPlayerResources = runResolutionSupport::clampPlayerResources,
            notify = ::println
        )
    }

    fun autoSave(state: GameState) {
        val path = Paths.get("data", "saves", "autosave.json")
        val synced = statusTimeSupport.synchronizeClock(state)
        JsonStore.save(path, synced.copy(currentRun = null))
    }

    fun saveGame(state: GameState) {
        val name = ioSupport.readNonEmpty("Nome do save (ex: save1): ")
        val path = Paths.get("data", "saves", "$name.json")
        val synced = statusTimeSupport.synchronizeClock(state)
        JsonStore.save(path, synced.copy(currentRun = null))
        println("Save criado em ${path.fileName}.")
    }

    fun canonicalItemId(itemId: String, itemInstances: Map<String, ItemInstance>): String {
        return itemInstances[itemId]?.templateId ?: itemId
    }

    fun formatSignedDouble(value: Double): String {
        val rounded = "%.1f".format(value)
        return if (value >= 0.0) "+$rounded" else rounded
    }

    fun formatSigned(value: Int): String = if (value >= 0) "+$value" else value.toString()
    fun format(value: Double): String = "%.1f".format(value)

    fun applyAchievementUpdate(update: AchievementUpdate): PlayerState {
        showAchievementNotifications(update.unlockedTiers)
        return update.player
    }

    fun showAchievementNotifications(notifications: List<AchievementTierUnlockedNotification>) {
        if (notifications.isEmpty()) return
        for (notification in notifications) {
            println(menuFormattingSupport.uiColor("(!) Conquista concluida (!)", LegacyCliAnsiPalette.questAlert))
            println(notification.displayName)
            println(notification.displayDescription)
            println("Recompensa disponivel: ${notification.rewardGold} ouro")
        }
    }
}
