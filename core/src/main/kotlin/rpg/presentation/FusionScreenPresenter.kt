package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.enchant.FusionQueryService
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class FusionScreenPresenter(
    private val queryService: FusionQueryService,
    private val support: PresentationSupport
) {
    fun presentSlot1(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Fusão")
        val items = queryService.slot1Candidates(state)
        val options = items.mapIndexed { index, item ->
            ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = "${item.displayLabel} | ${item.modeLabel}",
                action = GameAction.SelectFusionSlot1(item.itemId)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        val body = if (items.isEmpty()) {
            listOf("Nenhum item compatível para o Slot 1.")
        } else {
            listOf("Selecione o item do Slot 1.")
        }
        return MenuScreenViewModel(
            title = "Fusão - Slot 1",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentSlot2(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Fusão")
        val slot1 = session.selectedFusionSlot1ItemId
            ?: return MenuScreenViewModel(
                title = "Fusão - Slot 2",
                summary = support.playerSummary(state),
                bodyLines = listOf("Selecione o Slot 1 primeiro."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val items = queryService.slot2Candidates(state, slot1)
        val options = items.mapIndexed { index, item ->
            ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = item.displayLabel,
                action = GameAction.SelectFusionSlot2(item.itemId)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        val body = if (items.isEmpty()) {
            listOf("Nenhum item compatível para o Slot 2 com o Slot 1 atual.")
        } else {
            listOf("Slot 1 definido. Escolha o Slot 2.")
        }
        return MenuScreenViewModel(
            title = "Fusão - Slot 2",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentPreview(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Fusão")
        val slot1 = session.selectedFusionSlot1ItemId
            ?: return support.presentMissingState("Fusão")
        val slot2 = session.selectedFusionSlot2ItemId
            ?: return support.presentMissingState("Fusão")
        val preview = queryService.preview(state, slot1, slot2)
        val options = mutableListOf<ScreenOptionViewModel>()
        options += ScreenOptionViewModel(
            "1",
            if (preview.available) "Fundir" else "Fundir [bloqueado]",
            GameAction.AttemptFusion(slot1ItemId = slot1, slot2ItemId = slot2)
        )
        options += ScreenOptionViewModel("2", "Item 1", GameAction.OpenFusionMenu)
        options += ScreenOptionViewModel("3", "Item 2", GameAction.SelectFusionSlot1(slot1))
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        val body = mutableListOf<String>()
        body += "Slot 1: ${preview.slot1Label}"
        body += "Slot 2: ${preview.slot2Label}"
        body += preview.detailLines
        return MenuScreenViewModel(
            title = "Fundir",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }
}
