package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.globalboss.GlobalBossQueryService
import rpg.globalboss.config.GlobalBossCadence
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class GlobalBossScreenPresenter(
    private val queryService: GlobalBossQueryService,
    private val support: PresentationSupport
) {
    fun presentMenu(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Eventos")
        val view = queryService.menuView(state)
        val weekly = view.items.firstOrNull { it.cadence == GlobalBossCadence.WEEKLY }
        val monthly = view.items.firstOrNull { it.cadence == GlobalBossCadence.MONTHLY }
        val body = mutableListOf<String>()
        weekly?.let { body += "Semanal: ${it.runsLabel} | Tempo: ${it.timeRemainingLabel}" }
        monthly?.let { body += "Mensal: ${it.runsLabel} | Tempo: ${it.timeRemainingLabel}" }
        if (body.isEmpty()) {
            body += "Nenhum evento global configurado."
        }
        val title = if (view.hasAlert) "Eventos(!)" else "Eventos"
        return MenuScreenViewModel(
            title = title,
            summary = support.playerSummary(state),
            bodyLines = body,
            options = listOfNotNull(
                weekly?.let {
                    ScreenOptionViewModel("1", "Boss Semanal${if (it.alert) "(!)" else ""}", GameAction.OpenGlobalBossWeekly)
                },
                monthly?.let {
                    ScreenOptionViewModel("2", "Boss Mensal${if (it.alert) "(!)" else ""}", GameAction.OpenGlobalBossMonthly)
                },
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentEventDetail(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Boss Global")
        val eventId = session.selectedGlobalBossEventId
            ?: return MenuScreenViewModel(
                title = "Boss Global",
                summary = support.playerSummary(state),
                bodyLines = listOf("Nenhum evento selecionado."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val detail = queryService.eventDetail(state, eventId)
            ?: return MenuScreenViewModel(
                title = "Boss Global",
                summary = support.playerSummary(state),
                bodyLines = listOf("Evento nao encontrado."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val body = mutableListOf<String>()
        body += "Tempo restante do ciclo: ${detail.cycleRemainingLabel}"
        body += "Boss: ${detail.bossName}"
        body += detail.totalDamageLabel
        body += detail.totalPointsLabel
        body += detail.bestRunLabel
        body += detail.runsLabel
        detail.rankingLabel?.let { body += it }

        val startLabel = if (detail.canStartRun) "Iniciar run manual" else "Iniciar run manual [indisponivel]"
        val autoLabel = if (detail.canAutoClear) "Auto clear" else "Auto clear [indisponivel]"
        val buyLabel = if (detail.canBuyAttempt) {
            "Comprar tentativa (${detail.buyCostCash} CASH)"
        } else {
            "Comprar tentativa [indisponivel]"
        }
        val milestonesLabel = if (detail.claimableMilestonesCount > 0) "Milestones (!)" else "Milestones"
        val cadenceLabel = if (detail.cadence == GlobalBossCadence.WEEKLY) "Semanal" else "Mensal"
        return MenuScreenViewModel(
            title = "Boss Global $cadenceLabel" + if (detail.alert) " (!)" else "",
            subtitle = if (detail.cadence == GlobalBossCadence.WEEKLY) "Evento semanal" else "Evento mensal",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = listOf(
                ScreenOptionViewModel("1", startLabel, GameAction.StartGlobalBossRun(detail.eventId)),
                ScreenOptionViewModel("2", autoLabel, GameAction.AutoClearGlobalBossRun(detail.eventId)),
                ScreenOptionViewModel("3", buyLabel, GameAction.BuyGlobalBossRunAttempt(detail.eventId)),
                ScreenOptionViewModel("4", milestonesLabel, GameAction.OpenGlobalBossMilestones(detail.eventId)),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentMilestones(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Milestones")
        val eventId = session.selectedGlobalBossEventId
            ?: return MenuScreenViewModel(
                title = "Milestones",
                summary = support.playerSummary(state),
                bodyLines = listOf("Nenhum evento selecionado."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val view = queryService.milestoneMenu(state, eventId)
            ?: return MenuScreenViewModel(
                title = "Milestones",
                summary = support.playerSummary(state),
                bodyLines = listOf("Evento nao encontrado."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val body = mutableListOf<String>()
        body += view.summaryLines
        body += ""
        for (milestone in view.milestones) {
            body += milestone.label
            body += milestone.rewardLabel
            body += milestone.statusLabel
            milestone.claimedAtLabel?.let { body += it }
            body += ""
        }

        val options = mutableListOf<ScreenOptionViewModel>()
        var index = 1
        for (milestone in view.milestones.filter { it.claimable }) {
            options += ScreenOptionViewModel(
                key = index.toString(),
                label = "Receber ${milestone.label}",
                action = GameAction.ClaimGlobalBossMilestone(view.eventId, milestone.id)
            )
            index++
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        return MenuScreenViewModel(
            title = view.title + if (view.alert) " (!)" else "",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }
}
