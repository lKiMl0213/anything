package rpg.state

import rpg.achievement.AchievementService
import rpg.achievement.AchievementTracker
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.io.DataRepository
import rpg.model.EquipSlot
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.quest.QuestInstance
import rpg.quest.QuestStatus
import rpg.talent.TalentTreeService

internal class StateSyncService(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker,
    private val achievementService: AchievementService,
    private val talentTreeService: TalentTreeService
) {
    fun normalizeLoadedState(state: GameState): GameState {
        val ammoNormalizedPlayer = InventorySystem.normalizeAmmoStorage(
            state.player,
            state.itemInstances,
            engine.itemRegistry
        )
        val migratedPlayer = achievementTracker.synchronize(
            engine.classSystem.sanitizePlayerHierarchy(
                engine.skillSystem.ensureProgress(ammoNormalizedPlayer)
            )
        )
        return if (migratedPlayer == state.player) {
            if (ammoNormalizedPlayer == state.player) state else state.copy(player = ammoNormalizedPlayer)
        } else {
            state.copy(player = migratedPlayer)
        }
    }

    fun normalizePlayerStorage(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        return InventorySystem.normalizeAmmoStorage(player, itemInstances, engine.itemRegistry)
    }

    fun hasClassMapUnlocked(player: PlayerState): Boolean {
        return engine.classQuestService.activeDungeon(player) != null
    }

    fun equippedSlotLabel(slotKey: String): String = when (slotKey) {
        EquipSlot.WEAPON_MAIN.name -> "Arma primaria"
        EquipSlot.WEAPON_OFF.name -> "Arma secundaria"
        EquipSlot.ALJAVA.name -> "Aljava"
        EquipSlot.HEAD.name -> "Cabeca"
        EquipSlot.CHEST.name -> "Peito"
        EquipSlot.LEGS.name -> "Pernas"
        EquipSlot.GLOVES.name -> "Luvas"
        EquipSlot.BOOTS.name -> "Botas"
        else -> if (slotKey.uppercase().startsWith("ACCESSORY")) {
            slotKey.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        } else {
            slotKey
        }
    }

    fun itemName(itemId: String): String {
        return engine.itemRegistry.entry(itemId)?.name ?: itemId
    }

    fun synchronizeQuestBoard(
        board: rpg.quest.QuestBoardState,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): rpg.quest.QuestBoardState {
        val synced = engine.questBoardEngine.synchronize(board, player)
        return engine.questProgressTracker.synchronizeCollectProgressFromInventory(
            board = synced,
            inventory = player.inventory,
            itemInstanceTemplateById = { id -> itemInstances[id]?.templateId }
        )
    }

    fun hasReadyToClaim(board: rpg.quest.QuestBoardState): Boolean {
        return hasReadyToClaim(board.dailyQuests) ||
            hasReadyToClaim(board.weeklyQuests) ||
            hasReadyToClaim(board.monthlyQuests) ||
            hasReadyToClaim(board.acceptedQuests)
    }

    fun hasReadyToClaim(quests: List<QuestInstance>): Boolean {
        return quests.any { it.status == QuestStatus.READY_TO_CLAIM }
    }

    fun hasAchievementRewardReady(player: PlayerState): Boolean {
        return achievementService.hasClaimableRewards(player)
    }

    fun hasUnspentAttributePoints(player: PlayerState): Boolean {
        return player.unspentAttrPoints > 0
    }

    fun hasTalentPointsAvailable(player: PlayerState): Boolean {
        val trees = talentTreeService.activeTrees(player, repo.talentTreesV2.values)
        if (trees.isEmpty()) return false
        val ledger = talentTreeService.pointsLedger(player, trees)
        return when (ledger.mode) {
            rpg.model.TalentPointMode.SHARED_POOL -> ledger.sharedAvailable > 0
            rpg.model.TalentPointMode.PER_TREE -> ledger.availableByTree.values.any { it > 0 }
        }
    }
}
