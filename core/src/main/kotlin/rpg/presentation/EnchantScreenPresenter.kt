package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.enchant.EnchantQueryService
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class EnchantScreenPresenter(
    private val queryService: EnchantQueryService,
    private val support: PresentationSupport
) {
    fun presentEnchantMenu(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Encantamento")
        return MenuScreenViewModel(
            title = "Encantamento",
            summary = support.playerSummary(state),
            bodyLines = listOf("Escolha a operação de encantamento."),
            options = listOf(
                ScreenOptionViewModel("1", "Encantar", GameAction.OpenEnchantItemList),
                ScreenOptionViewModel("2", "Fundir", GameAction.OpenFusionMenu),
                ScreenOptionViewModel("3", "Extrair", GameAction.OpenExtractionMenu),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentEnchantList(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Encantamento")
        val items = queryService.inventoryItems(state)
        val options = items.mapIndexed { index, item ->
            val qtyLabel = if (item.quantity > 1) " x${item.quantity}" else ""
            val status = if (item.available) {
                "Disponível"
            } else {
                item.blockedReasons.joinToString(" | ").ifBlank { "Bloqueado" }
            }
            ScreenOptionViewModel(
                (index + 1).toString(),
                "${item.displayLabel}$qtyLabel | +${item.enchantLevel}/${item.maxEnchantLevel} | $status",
                GameAction.InspectEnchantItem(item.itemId)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val bodyLines = if (items.isEmpty()) {
            listOf("Nenhum equipamento no inventário para encantamento.")
        } else {
            listOf(
                "Escolha um equipamento para configurar a tentativa.",
                "Runas de proteção impedem quebra e sempre são consumidas."
            )
        }
        return MenuScreenViewModel(
            title = "Encantamento - Itens",
            summary = support.playerSummary(state),
            bodyLines = bodyLines,
            options = options,
            messages = session.messages
        )
    }

    fun presentEnchantDetail(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Encantamento")
        val selectedItemId = session.selectedEnchantItemId
            ?: return MenuScreenViewModel(
                title = "Encantamento",
                summary = support.playerSummary(state),
                bodyLines = listOf("Escolha um equipamento primeiro."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val detail = queryService.itemDetail(state, selectedItemId)
            ?: return MenuScreenViewModel(
                title = "Encantamento",
                summary = support.playerSummary(state),
                bodyLines = listOf("Esse equipamento não está mais disponível."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )

        val selectedRunes = session.selectedEnchantEnhancementRunes.coerceAtLeast(0)
        val selectedProtection = session.selectedEnchantUseProtectionRune
        val selectedAttempt = queryService.selectedAttemptOption(
            state = state,
            itemId = detail.itemId,
            enhancementRunes = selectedRunes,
            useProtectionRune = selectedProtection
        )

        val options = listOf(
            ScreenOptionViewModel(
                "1",
                if (selectedAttempt.available) "Aprimorar" else "Aprimorar [bloqueado]",
                GameAction.AttemptEnchantItem(
                    itemId = detail.itemId,
                    enhancementRunes = selectedRunes,
                    useProtectionRune = selectedProtection
                )
            ),
            ScreenOptionViewModel("2", "Item", GameAction.OpenEnchantItemList),
            ScreenOptionViewModel("3", "Runas de aprimoramento: $selectedRunes", GameAction.CycleEnchantEnhancementRunes),
            ScreenOptionViewModel(
                "4",
                "Runas de proteção: ${if (selectedProtection) "SIM" else "NÃO"}",
                GameAction.ToggleEnchantProtectionRune
            ),
            ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        )

        val body = mutableListOf<String>()
        body += "Item: ${detail.itemLabel}"
        body += detail.detailLines
        body += "Seleção atual:"
        body += "- Runas de aprimoramento: $selectedRunes"
        body += "- Runas de proteção: ${if (selectedProtection) "SIM" else "NÃO"}"
        body += "- Prévia: ${selectedAttempt.displayLabel}"
        if (selectedAttempt.blockedReasons.isNotEmpty()) {
            body += "- Bloqueios atuais: ${selectedAttempt.blockedReasons.joinToString(" | ")}"
        }

        return MenuScreenViewModel(
            title = "Encantar",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }
}




