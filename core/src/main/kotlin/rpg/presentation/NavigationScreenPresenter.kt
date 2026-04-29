package rpg.presentation

import rpg.application.GameSession
import rpg.application.PendingDungeonChestEvent
import rpg.application.PendingDungeonEvent
import rpg.application.PendingDungeonLiquidEvent
import rpg.application.PendingDungeonNpcItemEvent
import rpg.application.PendingDungeonNpcMoneyEvent
import rpg.application.PendingDungeonNpcSuspiciousEvent
import rpg.application.actions.GameAction
import rpg.application.character.CharacterQueryService
import rpg.application.progression.AchievementQueryService
import rpg.application.progression.QuestQueryService
import rpg.engine.GameEngine
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel

internal class NavigationScreenPresenter(
    private val engine: GameEngine,
    private val support: PresentationSupport,
    private val characterQueryService: CharacterQueryService,
    private val questQueryService: QuestQueryService,
    private val achievementQueryService: AchievementQueryService
) {
    fun presentMainMenu(session: GameSession): ScreenViewModel {
        val options = mutableListOf<ScreenOptionViewModel>()
        if (session.gameState != null) {
            options += ScreenOptionViewModel("1", "Continuar sessao", GameAction.ContinueSession)
            options += ScreenOptionViewModel("2", "Novo jogo", GameAction.StartNewGame)
            options += ScreenOptionViewModel("3", "Carregar jogo", GameAction.OpenLoadGame)
        } else {
            options += ScreenOptionViewModel("1", "Novo jogo", GameAction.StartNewGame)
            options += ScreenOptionViewModel("2", "Carregar jogo", GameAction.OpenLoadGame)
        }
        options += ScreenOptionViewModel("x", "Sair", GameAction.Exit)
        return MenuScreenViewModel(
            title = "RPG TXT",
            subtitle = "Aventura em texto",
            bodyLines = listOf("Escolha uma opcao para comecar."),
            summary = session.gameState?.let(support::playerSummary),
            options = options,
            messages = session.messages
        )
    }

    fun presentSaveSelection(session: GameSession): ScreenViewModel {
        val options = session.availableSaves.mapIndexed { index, path ->
            ScreenOptionViewModel((index + 1).toString(), path.fileName.toString(), GameAction.LoadSave(path))
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Carregar jogo",
            bodyLines = if (session.availableSaves.isEmpty()) {
                listOf("Nenhum save disponivel.")
            } else {
                listOf("Selecione um save para continuar sua aventura.")
            },
            options = options,
            messages = session.messages
        )
    }

    fun presentHub(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Menu Principal")
        val characterAlert = if (
            characterQueryService.hasUnspentAttributePoints(state.player) ||
            characterQueryService.hasTalentPointsAvailable(state.player)
        ) " (!)" else ""
        val progressionAlert = if (
            questQueryService.hasQuestAlert(state) ||
            achievementQueryService.hasClaimableRewards(state)
        ) " (!)" else ""
        return MenuScreenViewModel(
            title = "Menu Principal",
            subtitle = session.currentSaveName?.let { "Save atual: $it" },
            summary = null,
            bodyLines = support.hubOverviewLines(state),
            options = listOf(
                ScreenOptionViewModel("1", "Explorar", GameAction.OpenExploration),
                ScreenOptionViewModel("2", "Personagem$characterAlert", GameAction.OpenCharacterMenu),
                ScreenOptionViewModel("3", "Producao", GameAction.OpenProductionMenu),
                ScreenOptionViewModel("4", "Progressao$progressionAlert", GameAction.OpenProgressionMenu),
                ScreenOptionViewModel("5", "Cidade", GameAction.OpenCityMenu),
                ScreenOptionViewModel("6", "Salvar", GameAction.OpenSaveMenu),
                ScreenOptionViewModel("x", "Sair para o menu", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentProductionMenu(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Producao")
        return MenuScreenViewModel(
            title = "Producao",
            summary = support.playerSummary(state),
            bodyLines = listOf("Crie equipamentos, consumiveis e colete recursos."),
            options = listOf(
                ScreenOptionViewModel("1", "Craft", GameAction.OpenCraftMenu),
                ScreenOptionViewModel("2", "Coleta de ervas", GameAction.OpenGatheringType(rpg.model.GatheringType.HERBALISM)),
                ScreenOptionViewModel("3", "Mineracao", GameAction.OpenGatheringType(rpg.model.GatheringType.MINING)),
                ScreenOptionViewModel("4", "Cortar madeira", GameAction.OpenGatheringType(rpg.model.GatheringType.WOODCUTTING)),
                ScreenOptionViewModel("5", "Pesca", GameAction.OpenGatheringType(rpg.model.GatheringType.FISHING)),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentCityMenu(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Cidade")
        return MenuScreenViewModel(
            title = "Cidade",
            summary = support.playerSummary(state),
            bodyLines = listOf("Descanse e acesse os servicos da cidade."),
            options = listOf(
                ScreenOptionViewModel("1", "Taverna", GameAction.OpenTavern),
                ScreenOptionViewModel("2", "Loja de Ouro", GameAction.OpenGoldShop),
                ScreenOptionViewModel("3", "Loja de Cash", GameAction.OpenCashShop),
                ScreenOptionViewModel("4", "Aprimoramentos", GameAction.OpenUpgradeShop),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentSaveMenu(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Salvar")
        val options = mutableListOf<ScreenOptionViewModel>()
        var index = 1
        session.currentSaveName?.let { current ->
            options += ScreenOptionViewModel(index++.toString(), "Salvar em $current", GameAction.SaveCurrentGame)
        }
        options += ScreenOptionViewModel(index++.toString(), "Salvar em autosave", GameAction.SaveAutosave)
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        val body = mutableListOf<String>()
        body += if (session.currentSaveName != null) {
            "Save atual: ${session.currentSaveName}"
        } else {
            "Sessao sem slot fixo. Salvar agora usara o autosave."
        }
        body += "Salve seu progresso para continuar depois."
        return MenuScreenViewModel(
            title = "Salvar",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentExploration(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Exploracao")
        val run = state.currentRun
        if (run != null && run.isActive) {
            val tierLabel = run.tierId?.takeIf { it.isNotBlank() }
            val options = mutableListOf<ScreenOptionViewModel>()
            if (tierLabel != null) {
                options += ScreenOptionViewModel("1", "Continuar", GameAction.EnterDungeon(tierLabel))
            }
            options += ScreenOptionViewModel("2", "Usar item", GameAction.OpenInventory)
            options += ScreenOptionViewModel("x", "Sair da Dungeon", GameAction.ExitDungeonRun)
            return MenuScreenViewModel(
                title = "Exploracao",
                summary = support.playerSummary(state),
                bodyLines = listOf(
                    "Run ativa: tier ${tierLabel ?: "desconhecido"} | profundidade ${run.depth} | vitorias ${run.victoriesInRun} | bosses ${run.bossesDefeatedInRun}",
                    "O que deseja fazer?"
                ),
                options = options,
                messages = session.messages
            )
        }
        return MenuScreenViewModel(
            title = "Exploracao",
            summary = support.playerSummary(state),
            bodyLines = listOf("Escolha seu proximo destino de aventura."),
            options = listOf(
                ScreenOptionViewModel("1", "Dungeons", GameAction.OpenDungeonSelection),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentExplorationLowHpConfirm(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Exploracao")
        return MenuScreenViewModel(
            title = "Explorar com vida baixa",
            summary = support.playerSummary(state),
            bodyLines = listOf(
                "Sua vida esta baixa. Recomendamos descansar.",
                "Deseja prosseguir?"
            ),
            options = listOf(
                ScreenOptionViewModel("1", "Sim", GameAction.ConfirmLowHpExploration),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentDungeonSelection(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Dungeons")
        val tiers = engine.availableTiers(state.player)
        val tierOptions = tiers.mapIndexed { index, tier ->
            ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = "${tier.id} (nivel minimo ${tier.minLevel})",
                action = GameAction.EnterDungeon(tier.id)
            )
        }
        return MenuScreenViewModel(
            title = "Dungeons",
            summary = support.playerSummary(state),
            bodyLines = listOf("Escolha um tier para iniciar a proxima sala da run."),
            options = tierOptions + ScreenOptionViewModel("x", "Voltar", GameAction.Back),
            messages = session.messages
        )
    }

    fun presentDungeonEvent(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Evento")
        val pending = session.pendingDungeonEvent
            ?: return MenuScreenViewModel(
                title = "Evento",
                summary = support.playerSummary(state),
                bodyLines = listOf("Nenhum evento pendente."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )

        val body = mutableListOf<String>()
        body += "Voce encontrou uma sala de evento."
        body += pending.introLine
        body += pending.detailLine

        val options = when (pending) {
            is PendingDungeonNpcMoneyEvent -> listOf(
                ScreenOptionViewModel("1", "Entregar ouro", GameAction.ResolveDungeonEvent(1)),
                ScreenOptionViewModel("2", "Recusar", GameAction.ResolveDungeonEvent(2)),
                ScreenOptionViewModel("x", "Voltar", GameAction.ResolveDungeonEvent(2))
            )

            is PendingDungeonNpcItemEvent -> listOf(
                ScreenOptionViewModel("1", "Entregar item", GameAction.ResolveDungeonEvent(1)),
                ScreenOptionViewModel("2", "Recusar", GameAction.ResolveDungeonEvent(2)),
                ScreenOptionViewModel("x", "Voltar", GameAction.ResolveDungeonEvent(2))
            )

            is PendingDungeonNpcSuspiciousEvent -> listOf(
                ScreenOptionViewModel("1", "Seguir a indicacao", GameAction.ResolveDungeonEvent(1)),
                ScreenOptionViewModel("2", "Ignorar", GameAction.ResolveDungeonEvent(2)),
                ScreenOptionViewModel("x", "Voltar", GameAction.ResolveDungeonEvent(2))
            )

            is PendingDungeonLiquidEvent -> listOf(
                ScreenOptionViewModel("1", "Provar", GameAction.ResolveDungeonEvent(1)),
                ScreenOptionViewModel("2", "Testar com cuidado", GameAction.ResolveDungeonEvent(2)),
                ScreenOptionViewModel("3", "Ignorar", GameAction.ResolveDungeonEvent(3)),
                ScreenOptionViewModel("x", "Voltar", GameAction.ResolveDungeonEvent(3))
            )

            is PendingDungeonChestEvent -> listOf(
                ScreenOptionViewModel("1", "Abrir rapido", GameAction.ResolveDungeonEvent(1)),
                ScreenOptionViewModel("2", "Inspecionar antes de abrir", GameAction.ResolveDungeonEvent(2)),
                ScreenOptionViewModel("3", "Ignorar", GameAction.ResolveDungeonEvent(3)),
                ScreenOptionViewModel("x", "Voltar", GameAction.ResolveDungeonEvent(3))
            )
        }

        return MenuScreenViewModel(
            title = "Evento",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentExit(session: GameSession): ScreenViewModel {
        return MenuScreenViewModel(
            title = "Saindo",
            bodyLines = listOf("Encerrando jogo."),
            options = emptyList(),
            messages = session.messages
        )
    }
}
