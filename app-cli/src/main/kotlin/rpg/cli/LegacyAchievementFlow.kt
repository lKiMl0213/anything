// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.achievement.AchievementCategory
import rpg.achievement.AchievementMenu
import rpg.achievement.AchievementService
import rpg.achievement.AchievementTierUnlockedNotification
import rpg.achievement.AchievementTracker
import rpg.io.DataRepository
import rpg.model.GameState
import rpg.model.PlayerState

internal class LegacyAchievementFlow(
    private val repo: DataRepository,
    private val achievementService: AchievementService,
    private val achievementTracker: AchievementTracker,
    private val achievementMenu: AchievementMenu,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val autoSave: (GameState) -> Unit,
    private val uiColor: (text: String, colorCode: String) -> String,
    private val labelWithAlert: (baseLabel: String, alert: String) -> String,
    private val ansiQuestAlert: String,
    private val pauseForEnter: () -> Unit,
    private val showAchievementNotifications: (notifications: List<AchievementTierUnlockedNotification>) -> Unit
) {
    fun openAchievementMenu(state: GameState): GameState {
        var player = achievementTracker.synchronize(state.player)
        while (true) {
            val claimAlert = if (hasAchievementRewardReady(player)) uiColor("(!)", ansiQuestAlert) else ""
            println("\n=== Conquistas ===")
            println(labelWithAlert("1. CONQUISTAS", claimAlert))
            println("2. ESTATISTICAS")
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> player = openAchievementCollection(player)
                2 -> player = showAchievementStatistics(player)
                null -> {
                    val updated = state.copy(player = player)
                    autoSave(updated)
                    return updated
                }
            }
        }
    }

    private fun openAchievementCollection(initialPlayer: PlayerState): PlayerState {
        var player = achievementTracker.synchronize(initialPlayer)
        while (true) {
            val list = achievementMenu.buildAchievementList(player)
            player = list.player
            if (list.views.isEmpty()) {
                println("Nenhuma conquista cadastrada.")
                return player
            }

            println("\n=== CONQUISTAS ===")
            val grouped = list.views.groupBy { it.category }
            val categories = AchievementCategory.values().toList()
                .filter { grouped.containsKey(it) }
            categories.forEachIndexed { index, category ->
                val categoryViews = grouped[category].orEmpty()
                val readyRewards = categoryViews.count { it.rewardAvailable }
                val maxed = categoryViews.count { it.status == rpg.achievement.AchievementStatus.MAX }
                val alert = if (readyRewards > 0) uiColor("(!)", ansiQuestAlert) else ""
                println(labelWithAlert("${index + 1}. ${category.label}", alert))
                println(
                    "   ${categoryViews.size} conquista(s) | " +
                        "Recompensas prontas: $readyRewards | MAX: $maxed"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, categories.size) ?: return player
            val selectedCategory = categories[choice - 1]
            player = openAchievementCategory(player, selectedCategory)
        }
    }

    private fun openAchievementCategory(
        initialPlayer: PlayerState,
        category: AchievementCategory
    ): PlayerState {
        var player = achievementTracker.synchronize(initialPlayer)
        while (true) {
            val list = achievementMenu.buildAchievementList(player)
            player = list.player
            val views = list.views.filter { it.category == category }
            if (views.isEmpty()) {
                println("Nenhuma conquista em ${category.label}.")
                return player
            }

            println("\n=== CONQUISTAS | ${category.label} ===")
            views.forEachIndexed { index, view ->
                val alert = if (view.rewardAvailable) uiColor("(!)", ansiQuestAlert) else ""
                val target = view.currentTierTarget?.toString() ?: "MAX"
                val reward = view.nextRewardGold?.let { "$it ouro" } ?: "MAX"
                println(labelWithAlert("${index + 1}. ${view.displayName}", alert))
                println(
                    "   ${view.displayDescription} | Progresso ${view.currentValue}/$target | " +
                        "Concluida ${view.timesCompleted}x | Recompensa: $reward | Status: ${view.status.label}"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, views.size) ?: return player
            val selected = views[choice - 1]
            val target = selected.currentTierTarget?.toString() ?: "MAX"
            val reward = selected.nextRewardGold?.let { "$it ouro" } ?: "MAX"
            println("\n${selected.displayName}")
            println(selected.displayDescription)
            println("Progresso atual: ${selected.currentValue}/$target")
            println("Concluida(s): ${selected.timesCompleted}")
            println("Recompensa do proximo tier: $reward")
            println("Status: ${selected.status.label}")

            if (selected.rewardAvailable) {
                println("1. Resgatar recompensa")
                println("x. Voltar")
                if (readMenuChoice("Escolha: ", 1, 1) == 1) {
                    val claim = achievementService.claimReward(player, selected.id)
                    println(claim.message)
                    player = claim.player
                    showAchievementNotifications(claim.unlockedTiers)
                }
            } else {
                println("Pressione ENTER para voltar.")
                pauseForEnter()
            }
        }
    }

    private fun showAchievementStatistics(initialPlayer: PlayerState): PlayerState {
        val statsView = achievementMenu.buildStatistics(initialPlayer, knownMonsterBaseTypes())
        val player = statsView.player
        val lifetime = player.lifetimeStats
        println("\n=== ESTATISTICAS ===")
        println("GERAL")
        statsView.generalLines.forEach { println(it) }
        println("\nMOBS")
        println("Total de monstros abatidos: ${lifetime.totalMonstersKilled}")
        println("Abates por estrela (0* ate 7*):")
        statsView.killsByStarLines.forEach { println(it) }
        println("Abates por tipo base:")
        if (statsView.bestiaryLines.isEmpty()) {
            println("Nenhum registro no bestiario ainda.")
        } else {
            statsView.bestiaryLines.forEach { println(it) }
        }
        println("Pressione ENTER para voltar.")
        pauseForEnter()
        return player
    }

    private fun knownMonsterBaseTypes(): Set<String> {
        val fromRepo = repo.monsterArchetypes.values.map { archetype ->
            archetype.monsterTypeId.ifBlank { archetype.baseType.ifBlank { archetype.id.substringBefore('_') } }
        }
        val fromTypes = repo.monsterTypes.keys
        return (fromRepo + fromTypes + rpg.achievement.MonsterTypeMasteryService.trackedTypes)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun hasAchievementRewardReady(player: PlayerState): Boolean {
        return achievementService.hasClaimableRewards(player)
    }
}
