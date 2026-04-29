package rpg.quest

import kotlin.random.Random
import rpg.classsystem.AttributeEngine
import rpg.classsystem.ClassSystem
import rpg.inventory.InventorySystem
import rpg.item.ItemEngine
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.progression.ExperienceEngine
import rpg.progression.PermanentUpgradeService
import rpg.registry.ItemRegistry

data class QuestClaimResult(
    val success: Boolean,
    val message: String,
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val board: QuestBoardState,
    val grantedItems: Map<String, Int> = emptyMap()
)

class QuestRewardService(
    private val itemRegistry: ItemRegistry,
    private val itemEngine: ItemEngine,
    private val classSystem: ClassSystem,
    private val rng: Random,
    private val permanentUpgradeService: PermanentUpgradeService
) {
    fun claimQuest(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        board: QuestBoardState,
        instanceId: String
    ): QuestClaimResult {
        val location = locateQuest(board, instanceId)
            ?: return QuestClaimResult(
                success = false,
                message = "Quest nao encontrada.",
                player = player,
                itemInstances = itemInstances,
                board = board
            )
        val quest = location.quest
        if (quest.status != QuestStatus.READY_TO_CLAIM) {
            return QuestClaimResult(
                success = false,
                message = "Quest ainda nao esta pronta para concluir.",
                player = player,
                itemInstances = itemInstances,
                board = board
            )
        }

        var updatedPlayer = player
        val inventory = updatedPlayer.inventory.toMutableList()
        val updatedInstances = itemInstances.toMutableMap()

        var preservedDeliveryItems = false
        if (quest.consumeTargetOnComplete) {
            val targetId = quest.generatedTargetId
            if (targetId.isNullOrBlank()) {
                return QuestClaimResult(
                    success = false,
                    message = "Quest de entrega sem alvo definido.",
                    player = player,
                    itemInstances = itemInstances,
                    board = board
                )
            }
            val keepChance = permanentUpgradeService.questItemKeepChancePct(player)
            preservedDeliveryItems = keepChance > 0.0 && rng.nextDouble(0.0, 100.0) <= keepChance
            if (!preservedDeliveryItems) {
                val consumed = consumeTargetItems(
                    inventory = inventory,
                    itemInstances = updatedInstances,
                    targetId = targetId,
                    amount = quest.requiredAmount
                )
                if (!consumed) {
                    return QuestClaimResult(
                        success = false,
                        message = "Itens insuficientes para concluir a entrega.",
                        player = player,
                        itemInstances = itemInstances,
                        board = board
                    )
                }
            }
        }

        updatedPlayer = updatedPlayer.copy(
            inventory = inventory,
            gold = updatedPlayer.gold + quest.rewards.gold,
            questCurrency = updatedPlayer.questCurrency + quest.rewards.specialCurrency
        )
        updatedPlayer = applyXpWithAutoPoints(updatedPlayer, quest.rewards.xp)

        val pendingRewardIds = mutableListOf<String>()
        val granted = mutableMapOf<String, Int>()
        for (rewardItem in quest.rewards.items) {
            if (rewardItem.quantity <= 0) continue
            val itemId = rewardItem.itemId
            if (itemRegistry.isTemplate(itemId)) {
                val template = itemRegistry.template(itemId) ?: continue
                repeat(rewardItem.quantity) {
                    val generated = itemEngine.generateFromTemplate(
                        template = template,
                        level = updatedPlayer.level,
                        rarity = template.rarity
                    )
                    updatedInstances[generated.id] = generated
                    pendingRewardIds += generated.id
                }
            } else if (itemRegistry.item(itemId) != null) {
                repeat(rewardItem.quantity) {
                    pendingRewardIds += itemId
                }
            }
        }

        val withCapacity = InventorySystem.addItemsWithLimit(
            player = updatedPlayer.copy(inventory = inventory),
            itemInstances = updatedInstances,
            itemRegistry = itemRegistry,
            incomingItemIds = pendingRewardIds
        )
        val rejectedGenerated = withCapacity.rejected.filter { updatedInstances.containsKey(it) }
        for (rejectedId in rejectedGenerated) {
            updatedInstances.remove(rejectedId)
        }
        for (acceptedId in withCapacity.accepted) {
            val canonical = updatedInstances[acceptedId]?.templateId ?: acceptedId
            granted[canonical] = (granted[canonical] ?: 0) + 1
        }
        updatedPlayer = updatedPlayer.copy(
            inventory = withCapacity.inventory,
            quiverInventory = withCapacity.quiverInventory,
            selectedAmmoTemplateId = withCapacity.selectedAmmoTemplateId
        )
        val claimedQuest = quest.copy(
            status = QuestStatus.CLAIMED,
            currentProgress = quest.requiredAmount
        )
        val boardAfterClaim = markClaimInBoard(board, location, claimedQuest)
        val updatedBoard = boardAfterClaim.copy(
            completedQuests = (board.completedQuests + claimedQuest).takeLast(MAX_COMPLETED_HISTORY)
        )

        return QuestClaimResult(
            success = true,
            message = buildString {
                append("Quest concluida: ${quest.title}")
                if (preservedDeliveryItems) {
                    append(" | Itens de entrega preservados.")
                }
            },
            player = updatedPlayer,
            itemInstances = updatedInstances,
            board = updatedBoard,
            grantedItems = granted
        )
    }

    private fun consumeTargetItems(
        inventory: MutableList<String>,
        itemInstances: MutableMap<String, ItemInstance>,
        targetId: String,
        amount: Int
    ): Boolean {
        if (amount <= 0) return true
        val matching = inventory.filter { id ->
            id == targetId || itemInstances[id]?.templateId == targetId
        }
        if (matching.size < amount) return false

        val toConsume = matching.take(amount)
        for (id in toConsume) {
            val index = inventory.indexOf(id)
            if (index >= 0) {
                inventory.removeAt(index)
            }
            if (itemInstances.containsKey(id)) {
                itemInstances.remove(id)
            }
        }
        return true
    }

    private fun applyXpWithAutoPoints(player: PlayerState, xp: Int): PlayerState {
        if (xp <= 0) return player
        var updated = ExperienceEngine.applyXp(player, xp)
        val gained = updated.level - player.level
        if (gained <= 0) return updated
        val classDef = classSystem.classDef(updated.classId)
        val raceDef = classSystem.raceDef(updated.raceId)
        val subclassDef = classSystem.subclassDef(updated.subclassId)
        val specializationDef = classSystem.specializationDef(updated.specializationId)
        repeat(gained) {
            updated = AttributeEngine.applyAutoPoints(updated, classDef, raceDef, subclassDef, specializationDef, rng)
        }
        return updated
    }

    private fun locateQuest(board: QuestBoardState, instanceId: String): QuestLocation? {
        val daily = board.dailyQuests.firstOrNull { it.instanceId == instanceId }
        if (daily != null) return QuestLocation(QuestBucket.DAILY, daily)
        val weekly = board.weeklyQuests.firstOrNull { it.instanceId == instanceId }
        if (weekly != null) return QuestLocation(QuestBucket.WEEKLY, weekly)
        val monthly = board.monthlyQuests.firstOrNull { it.instanceId == instanceId }
        if (monthly != null) return QuestLocation(QuestBucket.MONTHLY, monthly)
        val accepted = board.acceptedQuests.firstOrNull { it.instanceId == instanceId }
        if (accepted != null) return QuestLocation(QuestBucket.ACCEPTED, accepted)
        return null
    }

    private fun markClaimInBoard(
        board: QuestBoardState,
        location: QuestLocation,
        claimedQuest: QuestInstance
    ): QuestBoardState {
        return when (location.bucket) {
            QuestBucket.DAILY -> board.copy(
                dailyQuests = board.dailyQuests.map { quest ->
                    if (quest.instanceId == location.quest.instanceId) claimedQuest else quest
                }
            )
            QuestBucket.WEEKLY -> board.copy(
                weeklyQuests = board.weeklyQuests.map { quest ->
                    if (quest.instanceId == location.quest.instanceId) claimedQuest else quest
                }
            )
            QuestBucket.MONTHLY -> board.copy(
                monthlyQuests = board.monthlyQuests.map { quest ->
                    if (quest.instanceId == location.quest.instanceId) claimedQuest else quest
                }
            )
            QuestBucket.ACCEPTED -> board.copy(
                acceptedQuests = board.acceptedQuests.filterNot { it.instanceId == location.quest.instanceId }
            )
        }
    }

    private data class QuestLocation(
        val bucket: QuestBucket,
        val quest: QuestInstance
    )

    private enum class QuestBucket {
        DAILY,
        WEEKLY,
        MONTHLY,
        ACCEPTED
    }

    companion object {
        private const val MAX_COMPLETED_HISTORY = 200
    }
}
