package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.engine.GameEngine
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel

internal class NavigationScreenPresenter(
    private val engine: GameEngine,
    private val support: PresentationSupport
) {
    fun presentMainMenu(session: GameSession): ScreenViewModel {
        val options = mutableListOf<ScreenOptionViewModel>()
        if (session.gameState != null) {
            options += ScreenOptionViewModel("1", "Continuar sessao", GameAction.ContinueSession)
            options += ScreenOptionViewModel("2", "Novo jogo (legado)", GameAction.StartNewGame)
            options += ScreenOptionViewModel("3", "Carregar jogo", GameAction.OpenLoadGame)
        } else {
            options += ScreenOptionViewModel("1", "Novo jogo (legado)", GameAction.StartNewGame)
            options += ScreenOptionViewModel("2", "Carregar jogo", GameAction.OpenLoadGame)
        }
        options += ScreenOptionViewModel("x", "Sair", GameAction.Exit)
        return MenuScreenViewModel(
            title = "RPG TXT",
            subtitle = "Fluxo modular inicial + legado preservado",
            bodyLines = listOf(
                "O fluxo novo isola navegacao, apresentacao e input.",
                "A criacao de personagem ainda permanece no legado nesta etapa."
            ),
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
                listOf("Selecione um save para entrar no fluxo modular.")
            },
            options = options,
            messages = session.messages
        )
    }

    fun presentHub(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Menu Principal")
        return MenuScreenViewModel(
            title = "Menu Principal",
            subtitle = session.currentSaveName?.let { "Save atual: $it" },
            summary = support.playerSummary(state),
            bodyLines = listOf(
                "Fluxo modular migrado nesta etapa:",
                "- Sessao principal, save/load e retorno ao hub",
                "- Exploracao basica e combate",
                "- Inventario",
                "- Equipamentos",
                "- Atributos e talentos",
                "- Quests, conquistas e quest de classe",
                "- Taverna",
                "",
                "Producao ainda usa handoff.",
                "Cidade so delega ao legado onde ainda faltam servicos secundarios."
            ),
            options = listOf(
                ScreenOptionViewModel("1", "Personagem", GameAction.OpenCharacterMenu),
                ScreenOptionViewModel("2", "Explorar", GameAction.OpenExploration),
                ScreenOptionViewModel("3", "Producao", GameAction.OpenProductionMenu),
                ScreenOptionViewModel("4", "Progressao", GameAction.OpenProgressionMenu),
                ScreenOptionViewModel("5", "Cidade", GameAction.OpenCityMenu),
                ScreenOptionViewModel("6", "Salvar", GameAction.OpenSaveMenu),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentProductionMenu(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Producao")
        return MenuScreenViewModel(
            title = "Producao",
            summary = support.playerSummary(state),
            bodyLines = listOf(
                "Craft e gathering ainda usam o fluxo legado.",
                "O retorno para a sessao modular acontece automaticamente ao sair de la."
            ),
            options = listOf(
                ScreenOptionViewModel("1", "Abrir producao (legado)", GameAction.OpenLegacyProduction),
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
            bodyLines = listOf(
                "Fluxo modular desta area:",
                "- Taverna",
                "",
                "Lojas e servicos restantes ainda usam handoff pontual para o legado."
            ),
            options = listOf(
                ScreenOptionViewModel("1", "Taverna", GameAction.OpenTavern),
                ScreenOptionViewModel("2", "Outros servicos da cidade (legado)", GameAction.OpenLegacyCity),
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
        body += "O jogo salva sem a run atual em andamento, mantendo o padrao do projeto."
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
        return MenuScreenViewModel(
            title = "Exploracao",
            summary = support.playerSummary(state),
            bodyLines = listOf(
                "A migracao atual cobre o fluxo de dungeon e combate.",
                "Eventos especiais e outras rotas de exploracao usam handoff pontual para o legado."
            ),
            options = listOf(
                ScreenOptionViewModel("1", "Dungeons", GameAction.OpenDungeonSelection),
                ScreenOptionViewModel("2", "Exploracao extra (legado)", GameAction.OpenLegacyExploration),
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

    fun presentExit(session: GameSession): ScreenViewModel {
        return MenuScreenViewModel(
            title = "Saindo",
            bodyLines = listOf("Encerrando jogo."),
            options = emptyList(),
            messages = session.messages
        )
    }
}
