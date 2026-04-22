package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.progression.AchievementQueryService
import rpg.application.progression.QuestQueryService
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class ProgressionScreenPresenter(
    private val questQueryService: QuestQueryService,
    private val achievementQueryService: AchievementQueryService,
    private val support: PresentationSupport
) {
    fun presentProgressionMenu(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Progressao")
        val questAlert = if (questQueryService.hasQuestAlert(state)) " (!)" else ""
        val achievementAlert = if (achievementQueryService.hasClaimableRewards(state)) " (!)" else ""
        return MenuScreenViewModel(
            title = "Progressao",
            summary = support.playerSummary(state),
            bodyLines = listOf(
                "Fluxo modular desta area:",
                "- Quests ativas, diarias, semanais e mensais",
                "- Quest de classe",
                "- Conquistas e estatisticas"
            ),
            options = listOf(
                ScreenOptionViewModel("1", "Quests$questAlert", GameAction.OpenQuests),
                ScreenOptionViewModel("2", "Conquistas$achievementAlert", GameAction.OpenAchievements),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentQuestBoard(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Quests")
        val overview = questQueryService.questBoardOverview(state)
        val options = mutableListOf<ScreenOptionViewModel>()
        var index = 1
        if (overview.classQuestLabel != null) {
            options += ScreenOptionViewModel(index++.toString(), overview.classQuestLabel, GameAction.OpenClassQuest)
        }
        overview.sections.forEach { section ->
            val alert = if (section.hasAlert) " (!)" else ""
            options += ScreenOptionViewModel(
                index++.toString(),
                "${section.label}$alert | ${section.countLabel}",
                GameAction.OpenQuestSection(section.section)
            )
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Quests",
            summary = support.playerSummary(state),
            bodyLines = listOf(
                overview.replaceSummary,
                "Aceitas: ${overview.acceptedCountLabel}",
                "Pool aceitavel: ${overview.poolCountLabel}"
            ),
            options = options,
            messages = session.messages
        )
    }

    fun presentQuestList(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Quests")
        val section = session.selectedQuestSection ?: return support.presentMissingState("Quests")
        val view = questQueryService.questList(state, section)
        val options = view.quests.mapIndexed { index, quest ->
            ScreenOptionViewModel(
                (index + 1).toString(),
                "${quest.title} | ${quest.progressLabel} | ${quest.statusLabel}",
                GameAction.InspectQuest(section, quest.instanceId)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = view.title,
            summary = support.playerSummary(state),
            bodyLines = if (view.quests.isEmpty()) listOf(view.emptyMessage) else emptyList(),
            options = options,
            messages = session.messages
        )
    }

    fun presentQuestDetail(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Quest")
        val section = session.selectedQuestSection ?: return support.presentMissingState("Quest")
        val instanceId = session.selectedQuestId ?: return support.presentMissingState("Quest")
        val detail = questQueryService.questDetail(state, section, instanceId)
            ?: return MenuScreenViewModel(
                title = "Quest",
                bodyLines = listOf("Essa quest nao esta mais disponivel."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val options = mutableListOf<ScreenOptionViewModel>()
        var index = 1
        if (detail.canAccept) {
            options += ScreenOptionViewModel(index++.toString(), "Aceitar", GameAction.AcceptQuest(detail.instanceId))
        }
        if (detail.canClaim) {
            options += ScreenOptionViewModel(index++.toString(), "Concluir e receber recompensa", GameAction.ClaimQuest(detail.instanceId))
        }
        if (detail.canCancel) {
            options += ScreenOptionViewModel(index++.toString(), "Cancelar quest", GameAction.CancelQuest(detail.instanceId))
        }
        if (detail.canReplace) {
            options += ScreenOptionViewModel(
                index++.toString(),
                "Replace (${detail.replaceRemaining} restante(s))",
                GameAction.ReplaceQuest(detail.section, detail.instanceId)
            )
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = detail.title,
            summary = support.playerSummary(state),
            bodyLines = detail.detailLines,
            options = options,
            messages = session.messages
        )
    }

    fun presentClassQuest(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Quest de Classe")
        val view = questQueryService.classQuestView(state)
            ?: return MenuScreenViewModel(
                title = "Quest de Classe",
                summary = support.playerSummary(state),
                bodyLines = listOf("Nenhuma quest de classe disponivel no momento."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val options = mutableListOf<ScreenOptionViewModel>()
        var index = 1
        if (view.canChoosePath && view.pathAId != null && view.pathBId != null) {
            options += ScreenOptionViewModel(index++.toString(), "Escolher caminho ${view.pathALabel}", GameAction.ChooseClassQuestPath(view.pathAId))
            options += ScreenOptionViewModel(index++.toString(), "Escolher caminho ${view.pathBLabel}", GameAction.ChooseClassQuestPath(view.pathBId))
        }
        if (view.canCancel) {
            options += ScreenOptionViewModel(index++.toString(), "Cancelar missao", GameAction.RequestCancelClassQuest)
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = view.title,
            summary = support.playerSummary(state),
            bodyLines = listOf("Status: ${view.statusLabel}") + view.detailLines,
            options = options,
            messages = session.messages
        )
    }

    fun presentClassQuestCancelConfirm(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Quest de Classe")
        return MenuScreenViewModel(
            title = "Cancelar Missao",
            summary = support.playerSummary(state),
            bodyLines = listOf(
                "Tem certeza que deseja cancelar esta missao?",
                "TODO o progresso sera perdido e a missao retornara para a etapa 1."
            ),
            options = listOf(
                ScreenOptionViewModel("1", "Confirmar cancelamento", GameAction.ConfirmCancelClassQuest),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentAchievements(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Conquistas")
        val overview = achievementQueryService.overview(state)
        val options = overview.categories.mapIndexed { index, category ->
            val alert = if (category.readyRewards > 0) " (!)" else ""
            ScreenOptionViewModel(
                (index + 1).toString(),
                "${category.label}$alert | ${category.totalCount} conquista(s) | prontas ${category.readyRewards} | MAX ${category.maxedCount}",
                GameAction.OpenAchievementCategory(category.category)
            )
        } + listOf(
            ScreenOptionViewModel((overview.categories.size + 1).toString(), "Estatisticas", GameAction.OpenAchievementStatistics),
            ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        )
        return MenuScreenViewModel(
            title = "Conquistas",
            summary = support.playerSummary(state),
            bodyLines = if (overview.categories.isEmpty()) listOf("Nenhuma conquista cadastrada.") else emptyList(),
            options = options,
            messages = session.messages
        )
    }

    fun presentAchievementCategory(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Conquistas")
        val category = session.selectedAchievementCategory ?: return support.presentMissingState("Conquistas")
        val view = achievementQueryService.category(state, category)
        val options = view.items.mapIndexed { index, item ->
            val alert = if (item.rewardAvailable) " (!)" else ""
            ScreenOptionViewModel(
                (index + 1).toString(),
                "${item.title}$alert | ${item.progressLabel} | ${item.rewardLabel} | ${item.statusLabel}",
                GameAction.InspectAchievement(category, item.achievementId)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Conquistas | ${view.title}",
            summary = support.playerSummary(state),
            bodyLines = if (view.items.isEmpty()) listOf("Nenhuma conquista nesta categoria.") else emptyList(),
            options = options,
            messages = session.messages
        )
    }

    fun presentAchievementDetail(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Conquista")
        val category = session.selectedAchievementCategory ?: return support.presentMissingState("Conquista")
        val achievementId = session.selectedAchievementId ?: return support.presentMissingState("Conquista")
        val detail = achievementQueryService.detail(state, category, achievementId)
            ?: return MenuScreenViewModel(
                title = "Conquista",
                bodyLines = listOf("Conquista nao encontrada."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val options = mutableListOf<ScreenOptionViewModel>()
        if (detail.canClaim) {
            options += ScreenOptionViewModel("1", "Resgatar recompensa", GameAction.ClaimAchievementReward(detail.achievementId))
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = detail.title,
            summary = support.playerSummary(state),
            bodyLines = detail.detailLines,
            options = options,
            messages = session.messages
        )
    }

    fun presentAchievementStatistics(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Estatisticas")
        val stats = achievementQueryService.statistics(state)
        val body = mutableListOf<String>()
        body += "GERAL"
        body += stats.generalLines
        body += ""
        body += "MOBS"
        body += "Total de monstros abatidos: ${stats.totalMonstersKilled}"
        body += "Abates por estrela (0* ate 7*):"
        body += stats.starLines
        body += "Abates por tipo base:"
        body += if (stats.bestiaryLines.isEmpty()) {
            listOf("Nenhum registro no bestiario ainda.")
        } else {
            stats.bestiaryLines
        }
        return MenuScreenViewModel(
            title = "Estatisticas",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
            messages = session.messages
        )
    }
}
