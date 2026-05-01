package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.enchant.ExtractionQueryService
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class ExtractionScreenPresenter(
    private val queryService: ExtractionQueryService,
    private val support: PresentationSupport
) {
    fun presentItemList(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Extração")
        val items = queryService.inventoryItems(state)
        val options = items.mapIndexed { index, item ->
            val status = if (item.available) "Disponível" else item.blockedReasons.joinToString(" | ").ifBlank { "Indisponível" }
            ScreenOptionViewModel(
                key = (index + 1).toString(),
                label = "${item.displayLabel} | $status",
                action = GameAction.SelectExtractionItem(item.itemId)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        val body = if (items.isEmpty()) {
            listOf("Nenhum equipamento encantado disponível para extração.")
        } else {
            listOf("Selecione o item encantado para extração.")
        }
        return MenuScreenViewModel(
            title = "Extração - Item",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentPreview(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Extração")
        val selected = session.selectedExtractionItemId
            ?: return MenuScreenViewModel(
                title = "Extração",
                summary = support.playerSummary(state),
                bodyLines = listOf("Selecione um item primeiro."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val detail = queryService.detail(state, selected)
            ?: return MenuScreenViewModel(
                title = "Extração",
                summary = support.playerSummary(state),
                bodyLines = listOf("Item inválido para extração."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )

        val selectedRemoval = session.selectedExtractionUseRemovalScroll
        val selectedProtection = session.selectedExtractionUseProtectionScroll
        val selectedAttempt = detail.attemptOptions.firstOrNull { option ->
            option.useRemovalScroll == selectedRemoval && option.useProtectionScroll == selectedProtection
        } ?: detail.attemptOptions.firstOrNull()

        val options = listOf(
            ScreenOptionViewModel(
                key = "1",
                label = if (selectedAttempt?.available == true) "Extrair" else "Extrair [bloqueado]",
                action = GameAction.AttemptExtraction(
                    itemId = detail.itemId,
                    useRemovalScroll = selectedRemoval,
                    useProtectionScroll = selectedProtection
                )
            ),
            ScreenOptionViewModel("2", "Item", GameAction.OpenExtractionMenu),
            ScreenOptionViewModel(
                "3",
                "Pergaminho de remoção: ${if (selectedRemoval) "SIM" else "NÃO"}",
                GameAction.ToggleExtractionRemovalScroll
            ),
            ScreenOptionViewModel(
                "4",
                "Pergaminho de proteção: ${if (selectedProtection) "SIM" else "NÃO"}",
                GameAction.ToggleExtractionProtectionScroll
            ),
            ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        )

        val body = mutableListOf<String>()
        body += detail.detailLines
        body += "Remoção: [${if (selectedRemoval) "SIM" else "NÃO"}]"
        body += "Proteção: [${if (selectedProtection) "SIM" else "NÃO"}]"
        selectedAttempt?.let {
            body += "Tentativa selecionada: ${it.displayLabel}"
            if (it.blockedReasons.isNotEmpty()) {
                body += "Bloqueios atuais: ${it.blockedReasons.joinToString(" | ")}"
            }
        }

        return MenuScreenViewModel(
            title = "Extração",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }
}
