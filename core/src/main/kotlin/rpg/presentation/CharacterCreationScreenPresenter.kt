package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.creation.CharacterCreationQueryService
import rpg.navigation.NavigationState
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class CharacterCreationScreenPresenter(
    private val queryService: CharacterCreationQueryService
) {
    fun presentCreationMenu(session: GameSession): ScreenViewModel {
        val draft = session.creationDraft ?: queryService.initialDraft()
        val raceName = queryService.raceById(draft.raceId)?.name ?: "-"
        val className = queryService.classById(draft.classId)?.name ?: "-"
        val body = mutableListOf<String>()
        body += "Nome: ${draft.name}"
        body += "Raca: $raceName"
        body += "Classe: $className"
        body += "Pontos de atributo restantes: ${draft.remainingPoints}"
        body += ""
        body += "Atributos atuais:"
        queryService.attributeRows(draft).forEach { row ->
            body += "${row.code} (${row.label}) = ${row.finalValue}"
        }
        return MenuScreenViewModel(
            title = "Criacao de Personagem",
            bodyLines = body,
            options = listOf(
                ScreenOptionViewModel("1", "Escolher nome", GameAction.CycleCharacterCreationName),
                ScreenOptionViewModel("2", "Selecionar raca", GameAction.OpenCharacterCreationRace),
                ScreenOptionViewModel("3", "Selecionar classe", GameAction.OpenCharacterCreationClass),
                ScreenOptionViewModel("4", "Distribuir atributos", GameAction.OpenCharacterCreationAttributes),
                ScreenOptionViewModel("5", "Confirmar criacao", GameAction.ConfirmCharacterCreation),
                ScreenOptionViewModel("x", "Cancelar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentRaceSelection(session: GameSession): ScreenViewModel {
        val races = queryService.availableRaces()
        val options = races.mapIndexed { index, race ->
            ScreenOptionViewModel((index + 1).toString(), race.name, GameAction.SelectCharacterCreationRace(race.id))
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Selecao de Raca",
            bodyLines = listOf("Escolha uma para ver detalhes."),
            options = options,
            messages = session.messages
        )
    }

    fun presentClassSelection(session: GameSession): ScreenViewModel {
        val classes = queryService.availableClasses()
        val options = classes.mapIndexed { index, classDef ->
            ScreenOptionViewModel((index + 1).toString(), classDef.name, GameAction.SelectCharacterCreationClass(classDef.id))
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Selecao de Classe",
            bodyLines = listOf("Escolha uma para ver detalhes."),
            options = options,
            messages = session.messages
        )
    }

    fun presentRaceDetail(session: GameSession): ScreenViewModel {
        val raceId = session.selectedCreationRaceId
            ?: return MenuScreenViewModel(
                title = "Selecao de Raca",
                bodyLines = listOf("Selecione uma raca para ver detalhes."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val race = queryService.raceById(raceId)
            ?: return MenuScreenViewModel(
                title = "Selecao de Raca",
                bodyLines = listOf("Raca invalida."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        return MenuScreenViewModel(
            title = race.name,
            bodyLines = queryService.raceSummaryLines(raceId),
            options = listOf(
                ScreenOptionViewModel("1", "Confirmar esta raca", GameAction.ConfirmCharacterCreationRace),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentClassDetail(session: GameSession): ScreenViewModel {
        val classId = session.selectedCreationClassId
            ?: return MenuScreenViewModel(
                title = "Selecao de Classe",
                bodyLines = listOf("Selecione uma classe para ver detalhes."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val clazz = queryService.classById(classId)
            ?: return MenuScreenViewModel(
                title = "Selecao de Classe",
                bodyLines = listOf("Classe invalida."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        return MenuScreenViewModel(
            title = clazz.name,
            bodyLines = queryService.classSummaryLines(classId),
            options = listOf(
                ScreenOptionViewModel("1", "Confirmar esta classe", GameAction.ConfirmCharacterCreationClass),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentAttributeAllocation(session: GameSession): ScreenViewModel {
        val draft = session.creationDraft ?: queryService.initialDraft()
        val rows = queryService.attributeRows(draft)
        val body = mutableListOf<String>()
        body += "Pontos restantes: ${draft.remainingPoints}"
        body += ""
        rows.forEachIndexed { index, row ->
            body += "${index + 1}. ${row.code} (${row.label}) -> ${row.finalValue} [alocado ${row.allocated}]"
        }
        body += ""
        body += "Escolha uma opcao para alocar."

        val options = rows.mapIndexed { index, row ->
            ScreenOptionViewModel((index + 1).toString(), "${row.code} (${row.label})", GameAction.IncreaseCharacterCreationAttribute(row.code))
        }
        return MenuScreenViewModel(
            title = "Distribuicao de Atributos",
            bodyLines = body,
            options = options + ScreenOptionViewModel("x", "Voltar", GameAction.Back),
            messages = session.messages
        )
    }

    fun presentAttributeAllocationDetail(session: GameSession): ScreenViewModel {
        val draft = session.creationDraft ?: queryService.initialDraft()
        val code = session.selectedAttributeCode
        val detail = queryService.attributeDetail(code.orEmpty())
            ?: return MenuScreenViewModel(
                title = "Distribuicao de Atributos",
                bodyLines = listOf("Atributo invalido."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val allocated = queryService.readAllocated(draft.allocated, detail.code)
        val rows = queryService.attributeRows(draft)
        val row = rows.firstOrNull { it.code == detail.code }
        val finalValue = row?.finalValue ?: allocated

        val body = mutableListOf<String>()
        body += "Pontos restantes: ${draft.remainingPoints}"
        body += "${detail.code} (${detail.label}) -> $finalValue [alocado $allocated]"
        body += ""
        body += "Afeta diretamente:"
        detail.directEffects.forEach { body += "- $it" }
        body += ""
        body += "Impacto na gameplay:"
        detail.gameplayImpact.forEach { body += "- $it" }

        return MenuScreenViewModel(
            title = "${detail.code} (${detail.label})",
            bodyLines = body,
            options = listOf(
                ScreenOptionViewModel("1", "Alocar pontos", GameAction.IncreaseCharacterCreationAttribute(detail.code)),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun canPresent(navigation: NavigationState): Boolean {
        return navigation == NavigationState.CharacterCreation ||
            navigation == NavigationState.CharacterCreationRace ||
            navigation == NavigationState.CharacterCreationRaceDetail ||
            navigation == NavigationState.CharacterCreationClass ||
            navigation == NavigationState.CharacterCreationClassDetail ||
            navigation == NavigationState.CharacterCreationAttributes ||
            navigation == NavigationState.CharacterCreationAttributeDetail
    }
}
