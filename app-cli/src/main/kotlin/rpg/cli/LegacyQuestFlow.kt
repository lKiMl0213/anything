// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.cli.model.QuestUiSnapshot
import rpg.cli.model.UseItemResult
import rpg.achievement.AchievementTracker
import rpg.classquest.ClassQuestMenu
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.QuestTier
import rpg.quest.QuestInstance
import rpg.quest.QuestStatus

internal class LegacyQuestFlow(
    private val engine: GameEngine,
    private val classQuestMenu: ClassQuestMenu,
    private val achievementTracker: AchievementTracker,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val autoSave: (GameState) -> Unit,
    private val synchronizeQuestBoard: (
        board: rpg.quest.QuestBoardState,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> rpg.quest.QuestBoardState,
    private val applyAchievementUpdate: (rpg.achievement.AchievementUpdate) -> PlayerState,
    private val uiColor: (text: String, colorCode: String) -> String,
    private val ansiQuestAlert: String,
    private val support: LegacyQuestFlowSupport
) {
    fun openQuestBoard(state: GameState): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        var board = synchronizeQuestBoard(state.questBoard, player, itemInstances)
        while (true) {
            player = engine.classQuestService.synchronize(player)
            player = achievementTracker.synchronize(player)
            board = synchronizeQuestBoard(board, player, itemInstances)
            val classQuestEntry = classQuestMenu.dynamicEntry(player)
            val acceptableAlert = if (support.hasReadyToClaim(board.acceptedQuests)) uiColor("(!)", ansiQuestAlert) else ""
            val dailyAlert =
                if (support.hasReadyToClaim(support.questsForTier(board, QuestTier.DAILY))) uiColor("(!)", ansiQuestAlert) else ""
            val weeklyAlert =
                if (support.hasReadyToClaim(support.questsForTier(board, QuestTier.WEEKLY))) uiColor("(!)", ansiQuestAlert) else ""
            val monthlyAlert =
                if (support.hasReadyToClaim(support.questsForTier(board, QuestTier.MONTHLY))) uiColor("(!)", ansiQuestAlert) else ""
            println("\n=== Quests ===")
            println(
                "Replaces: diaria ${support.remainingReplaces(board.dailyReplaceUsed, QuestTier.DAILY)} | " +
                    "semanal ${support.remainingReplaces(board.weeklyReplaceUsed, QuestTier.WEEKLY)} | " +
                    "mensal ${support.remainingReplaces(board.monthlyReplaceUsed, QuestTier.MONTHLY)}"
            )
            println("Aceitas: ${board.acceptedQuests.size}/${rpg.quest.QuestBoardEngine.MAX_ACCEPTED_ACTIVE}")
            println("Pool aceitavel: ${board.availableAcceptableQuestPool.size}")
            var option = 1
            val classQuestOption = if (classQuestEntry != null) option++ else null
            val acceptableOption = option++
            val dailyOption = option++
            val weeklyOption = option++
            val monthlyOption = option++
            if (classQuestOption != null && classQuestEntry != null) {
                println("$classQuestOption. ${classQuestEntry.label}")
            }
            println("$acceptableOption. Aceitaveis $acceptableAlert")
            println("$dailyOption. Diarias $dailyAlert")
            println("$weeklyOption. Semanais $weeklyAlert")
            println("$monthlyOption. Mensais $monthlyAlert")
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, option - 1)
            when {
                classQuestOption != null && choice == classQuestOption -> {
                    val updated = openClassQuestMenu(player, itemInstances)
                    player = updated.player
                    itemInstances = updated.itemInstances
                    board = synchronizeQuestBoard(board, player, itemInstances)
                }
                choice == acceptableOption -> {
                    val updated = handleAcceptableQuestMenu(player, itemInstances, board)
                    player = updated.player
                    itemInstances = updated.itemInstances
                    board = updated.board
                }
                choice == dailyOption -> {
                    val updated = handleTierQuestMenu(QuestTier.DAILY, player, itemInstances, board)
                    player = updated.player
                    itemInstances = updated.itemInstances
                    board = updated.board
                }
                choice == weeklyOption -> {
                    val updated = handleTierQuestMenu(QuestTier.WEEKLY, player, itemInstances, board)
                    player = updated.player
                    itemInstances = updated.itemInstances
                    board = updated.board
                }
                choice == monthlyOption -> {
                    val updated = handleTierQuestMenu(QuestTier.MONTHLY, player, itemInstances, board)
                    player = updated.player
                    itemInstances = updated.itemInstances
                    board = updated.board
                }
                choice == null -> {
                    val updatedState = state.copy(
                        player = player,
                        itemInstances = itemInstances,
                        questBoard = board
                    )
                    autoSave(updatedState)
                    return updatedState
                }
            }
        }
    }
    private fun openClassQuestMenu(
        initialPlayer: PlayerState,
        initialItemInstances: Map<String, rpg.model.ItemInstance>
    ): UseItemResult {
        var player = engine.classQuestService.synchronize(initialPlayer)
        var itemInstances = initialItemInstances
        while (true) {
            val view = classQuestMenu.view(player)
            if (view == null) {
                println("Nenhuma quest de classe disponivel no momento.")
                return UseItemResult(player, itemInstances)
            }

            println("\n=== ${view.title} ===")
            println("Status: ${view.statusLabel}")
            view.lines.forEach { println(it) }

            if (view.canChoosePath) {
                println("1. Escolher caminho ${view.context.definition.pathAName}")
                println("2. Escolher caminho ${view.context.definition.pathBName}")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 2)) {
                    1 -> {
                        val result = engine.classQuestService.choosePath(
                            player = player,
                            itemInstances = itemInstances,
                            pathId = view.context.definition.pathA
                        )
                        result.messages.forEach { println(it) }
                        player = result.player
                        itemInstances = result.itemInstances
                    }
                    2 -> {
                        val result = engine.classQuestService.choosePath(
                            player = player,
                            itemInstances = itemInstances,
                            pathId = view.context.definition.pathB
                        )
                        result.messages.forEach { println(it) }
                        player = result.player
                        itemInstances = result.itemInstances
                    }
                    null -> return UseItemResult(player, itemInstances)
                }
                continue
            }

            if (view.canCancel) {
                println("1. Cancelar missao")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 1)) {
                    1 -> {
                        println(
                            "Tem certeza que deseja cancelar esta missao? Todo o progresso sera perdido e a missao retornara para a etapa 1."
                        )
                        println("1. Confirmar cancelamento")
                        println("2. Manter missao")
                        println("x. Voltar")
                        if (readMenuChoice("Escolha: ", 1, 2) == 1) {
                            val result = engine.classQuestService.cancelCurrentQuest(player, itemInstances)
                            result.messages.forEach { println(it) }
                            player = result.player
                            itemInstances = result.itemInstances
                        }
                    }
                    null -> return UseItemResult(player, itemInstances)
                }
                continue
            }

            println("x. Voltar")
            return UseItemResult(player, itemInstances)
        }
    }
    private fun handleTierQuestMenu(
        tier: QuestTier,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        board: rpg.quest.QuestBoardState
    ): QuestUiSnapshot {
        var snapshot = QuestUiSnapshot(player, itemInstances, board)
        while (true) {
            val quests = support.questsForTier(snapshot.board, tier)
            if (quests.isEmpty()) {
                println("Nenhuma quest nesta categoria.")
                return snapshot
            }

            println("\n=== ${support.tierLabel(tier)} ===")
            quests.forEachIndexed { index, quest ->
                println(
                    "${index + 1}. ${quest.title} | ${quest.currentProgress}/${quest.requiredAmount} | " +
                        "${support.questStatusLabelColored(quest.status)}"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, quests.size) ?: return snapshot

            val quest = quests[choice - 1]
            support.showQuestDetails(quest)
            val replaceUsed = when (tier) {
                QuestTier.DAILY -> snapshot.board.dailyReplaceUsed
                QuestTier.WEEKLY -> snapshot.board.weeklyReplaceUsed
                QuestTier.MONTHLY -> snapshot.board.monthlyReplaceUsed
                QuestTier.ACCEPTED -> 0
            }
            val replaceLimit = support.replaceLimitFor(tier)
            val canReplace = replaceLimit > 0 &&
                replaceUsed < replaceLimit &&
                quest.status != QuestStatus.CLAIMED
            val canClaim = quest.status == QuestStatus.READY_TO_CLAIM

            var option = 1
            var claimOption = -1
            var replaceOption = -1
            if (canClaim) {
                claimOption = option++
                println("$claimOption. Concluir e receber recompensa")
            }
            if (canReplace) {
                replaceOption = option++
                println("$replaceOption. Replace (${replaceLimit - replaceUsed} restantes)")
            }
            println("x. Voltar")
            val action = readMenuChoice("Escolha: ", 1, option - 1) ?: continue

            if (action == claimOption) {
                snapshot = claimQuest(snapshot, quest.instanceId)
                continue
            }
            if (action == replaceOption) {
                val replace = engine.questBoardEngine.replaceQuest(
                    board = snapshot.board,
                    player = snapshot.player,
                    tier = tier,
                    instanceId = quest.instanceId
                )
                println(replace.message)
                if (replace.success) {
                    val synced = synchronizeQuestBoard(replace.board, snapshot.player, snapshot.itemInstances)
                    snapshot = snapshot.copy(board = synced)
                }
            }
        }
    }
    private fun handleAcceptableQuestMenu(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        board: rpg.quest.QuestBoardState
    ): QuestUiSnapshot {
        var snapshot = QuestUiSnapshot(player, itemInstances, board)
        while (true) {
            val accepted = snapshot.board.acceptedQuests
                .filter { it.status == QuestStatus.ACTIVE || it.status == QuestStatus.READY_TO_CLAIM }
                .sortedWith(compareByDescending<QuestInstance> { it.status == QuestStatus.READY_TO_CLAIM }.thenBy { it.title })
            val acceptedAlert = if (support.hasReadyToClaim(accepted)) uiColor("(!)", ansiQuestAlert) else ""
            println("\n=== Quests Aceitaveis ===")
            println("1. Pool disponivel (${snapshot.board.availableAcceptableQuestPool.size})")
            println(
                "2. Quests aceitas (${accepted.size}/${rpg.quest.QuestBoardEngine.MAX_ACCEPTED_ACTIVE}) $acceptedAlert"
            )
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> {
                    val pool = snapshot.board.availableAcceptableQuestPool
                    if (pool.isEmpty()) {
                        println("Pool vazia. Aguarde o proximo ciclo de 20 minutos.")
                        continue
                    }
                    println("\nPool:")
                    pool.forEachIndexed { index, quest ->
                        println("${index + 1}. ${quest.title} | ${quest.description}")
                    }
                    println("x. Voltar")
                    val choice = readMenuChoice("Escolha: ", 1, pool.size) ?: continue
                    val selected = pool[choice - 1]
                    support.showQuestDetails(selected)
                    println("1. Aceitar")
                    println("x. Voltar")
                    if (readMenuChoice("Escolha: ", 1, 1) == 1) {
                        val accept = engine.questBoardEngine.acceptQuest(snapshot.board, selected.instanceId)
                        println(accept.message)
                        if (accept.success) {
                            snapshot = snapshot.copy(
                                board = synchronizeQuestBoard(accept.board, snapshot.player, snapshot.itemInstances)
                            )
                        }
                    }
                }
                2 -> {
                    if (accepted.isEmpty()) {
                        println("Nenhuma quest aceita.")
                        continue
                    }
                    println("\nAceitas:")
                    accepted.forEachIndexed { index, quest ->
                        println(
                            "${index + 1}. ${quest.title} | ${quest.currentProgress}/${quest.requiredAmount} | " +
                                "${support.questStatusLabelColored(quest.status)}"
                        )
                    }
                    println("x. Voltar")
                    val choice = readMenuChoice("Escolha: ", 1, accepted.size) ?: continue
                    val selected = accepted[choice - 1]
                    support.showQuestDetails(selected)
                    val canClaim = selected.status == QuestStatus.READY_TO_CLAIM
                    var option = 1
                    var claimOption = -1
                    if (canClaim) {
                        claimOption = option++
                        println("$claimOption. Concluir e receber recompensa")
                    }
                    val cancelOption = option++
                    println("$cancelOption. Cancelar quest")
                    println("x. Voltar")
                    when (readMenuChoice("Escolha: ", 1, option - 1)) {
                        claimOption -> {
                            snapshot = claimQuest(snapshot, selected.instanceId)
                        }
                        cancelOption -> {
                            val cancel = engine.questBoardEngine.cancelAcceptedQuest(snapshot.board, selected.instanceId)
                            println(cancel.message)
                            if (cancel.success) {
                                snapshot = snapshot.copy(
                                    board = synchronizeQuestBoard(cancel.board, snapshot.player, snapshot.itemInstances)
                                )
                            }
                        }
                    }
                }
                null -> return snapshot
            }
        }
    }
    private fun claimQuest(snapshot: QuestUiSnapshot, instanceId: String): QuestUiSnapshot {
        val result = engine.questRewardService.claimQuest(
            player = snapshot.player,
            itemInstances = snapshot.itemInstances,
            board = snapshot.board,
            instanceId = instanceId
        )
        println(result.message)
        if (!result.success) return snapshot

        var updatedPlayer = result.player
        var updatedItemInstances = result.itemInstances
        var updatedBoard = result.board
        val goldEarned = (result.player.gold - snapshot.player.gold).coerceAtLeast(0)
        if (goldEarned > 0) {
            updatedPlayer = applyAchievementUpdate(
                achievementTracker.onGoldEarned(updatedPlayer, goldEarned.toLong())
            )
        }
        updatedPlayer = applyAchievementUpdate(achievementTracker.onQuestCompleted(updatedPlayer))
        for ((itemId, qty) in result.grantedItems) {
            updatedBoard = engine.questProgressTracker.onItemCollected(
                board = updatedBoard,
                itemId = itemId,
                quantity = qty
            )
        }
        if (result.grantedItems.isNotEmpty()) {
            val classQuestUpdate = engine.classQuestService.onItemsCollected(
                player = updatedPlayer,
                itemInstances = updatedItemInstances,
                collectedItems = result.grantedItems
            )
            classQuestUpdate.messages.forEach { println(it) }
            val classQuestGold = (classQuestUpdate.player.gold - updatedPlayer.gold).coerceAtLeast(0)
            updatedPlayer = classQuestUpdate.player
            if (classQuestGold > 0) {
                updatedPlayer = applyAchievementUpdate(
                    achievementTracker.onGoldEarned(updatedPlayer, classQuestGold.toLong())
                )
            }
            updatedItemInstances = classQuestUpdate.itemInstances
        }
        updatedBoard = synchronizeQuestBoard(updatedBoard, updatedPlayer, updatedItemInstances)
        return QuestUiSnapshot(
            player = updatedPlayer,
            itemInstances = updatedItemInstances,
            board = updatedBoard
        )
    }
}
