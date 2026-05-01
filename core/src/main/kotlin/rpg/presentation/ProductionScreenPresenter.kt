package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.production.ProductionQueryService
import rpg.model.CraftDiscipline
import rpg.model.GatheringType
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class ProductionScreenPresenter(
    private val queryService: ProductionQueryService,
    private val support: PresentationSupport
) {
    fun presentCraftMenu(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Craft")
        return MenuScreenViewModel(
            title = "Craft",
            summary = support.playerSummary(state),
            bodyLines = listOf("Selecione a disciplina de craft. Os tempos são mostrados em cada receita."),
            options = listOf(
                ScreenOptionViewModel("1", "Forja", GameAction.OpenCraftDiscipline(CraftDiscipline.FORGE)),
                ScreenOptionViewModel("2", "Alquimia", GameAction.OpenCraftDiscipline(CraftDiscipline.ALCHEMY)),
                ScreenOptionViewModel("3", "Culinária", GameAction.OpenCraftDiscipline(CraftDiscipline.COOKING)),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentRecipeList(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Receitas")
        val discipline = session.selectedCraftDiscipline
            ?: return MenuScreenViewModel(
                title = "Receitas",
                summary = support.playerSummary(state),
                bodyLines = listOf("Selecione uma disciplina de craft primeiro."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )

        val recipes = queryService.recipes(state, discipline)
        val options = recipes.mapIndexed { index, recipe ->
            val status = if (recipe.available) "Disponível (${recipe.maxCraftable}x)" else "Indisponível"
            ScreenOptionViewModel(
                (index + 1).toString(),
                "${recipe.name} -> ${recipe.outputLabel} | $status | ação ${formatSeconds(recipe.estimatedPerActionSeconds)}s | lote ${formatSeconds(recipe.estimatedBatchSeconds)}s",
                GameAction.InspectCraftRecipe(recipe.id)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf<String>()
        body += "Disciplina: ${discipline.name.lowercase()}"
        if (recipes.isEmpty()) {
            body += "Nenhuma receita cadastrada para esta disciplina."
        } else {
            body += "Selecione uma receita para ver ingredientes completos e definir quantidade."
        }

        return MenuScreenViewModel(
            title = "Receitas",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentRecipeDetail(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Receita")
        val discipline = session.selectedCraftDiscipline
            ?: return MenuScreenViewModel(
                title = "Receita",
                summary = support.playerSummary(state),
                bodyLines = listOf("Escolha uma disciplina de craft primeiro."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val recipeId = session.selectedCraftRecipeId
            ?: return MenuScreenViewModel(
                title = "Receita",
                summary = support.playerSummary(state),
                bodyLines = listOf("Selecione uma receita na lista para abrir o detalhe."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val requestedQty = session.selectedCraftRecipeQuantity.coerceAtLeast(1)
        val recipe = queryService.recipe(
            state = state,
            discipline = discipline,
            recipeId = recipeId,
            requestedBatchSize = requestedQty
        ) ?: return MenuScreenViewModel(
            title = "Receita",
            summary = support.playerSummary(state),
            bodyLines = listOf("Receita selecionada não está mais disponível."),
            options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
            messages = session.messages
        )

        val maxSelectableQty = recipe.maxCraftable.coerceAtLeast(1)
        val currentQty = requestedQty.coerceAtMost(maxSelectableQty)
        val status = if (recipe.available) "Disponível" else "Indisponível"

        val body = mutableListOf<String>()
        body += "Receita: ${recipe.name} -> ${recipe.outputLabel} | $status | ação ${formatSeconds(recipe.estimatedPerActionSeconds)}s | lote ${formatSeconds(recipe.estimatedBatchSeconds)}s (${recipe.batchSize}x)"
        if (recipe.blockedReasons.isNotEmpty()) {
            body += "Bloqueios: ${recipe.blockedReasons.joinToString(" | ")}"
        }
        body += "Ingredientes:"
        recipe.ingredientLines.forEach { ingredientLine ->
            body += "- $ingredientLine"
        }
        body += "Quantidade selecionada: ${currentQty}x (máximo atual: ${maxSelectableQty}x)"

        val options = listOf(
            ScreenOptionViewModel(
                "1",
                if (recipe.available) "Craftar lote (${currentQty}x)" else "Craftar lote (${currentQty}x) [bloqueado]",
                GameAction.CraftRecipe(recipe.id)
            ),
            ScreenOptionViewModel(
                "2",
                "Definir quantidade (${currentQty}x | máx ${maxSelectableQty}x)",
                GameAction.ConfigureCraftRecipeQuantity(recipe.id, maxSelectableQty)
            ),
            ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        )

        return MenuScreenViewModel(
            title = "Detalhe da Receita",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentGatheringList(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Coleta")
        val type = session.selectedGatheringType
            ?: return MenuScreenViewModel(
                title = "Coleta",
                summary = support.playerSummary(state),
                bodyLines = listOf("Escolha um tipo de coleta no menu de produção."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val nodes = queryService.gatherNodes(state, type)
        val options = nodes.mapIndexed { index, node ->
            val status = if (node.available) "Disponível" else "Skill ${node.skillLevel}/${node.minSkillLevel}"
            ScreenOptionViewModel(
                (index + 1).toString(),
                "${node.name} -> ${node.resourceLabel} | $status | tempo ${formatSeconds(node.durationSeconds)}s",
                GameAction.GatherNode(node.id)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf<String>()
        body += "Categoria: ${gatheringTypeLabel(type)}"
        if (nodes.isEmpty()) {
            body += "Nenhum ponto de coleta disponível."
        } else {
            nodes.take(6).forEach { node ->
                body += "- ${node.name}: ${node.resourceLabel} (skill ${node.skillType.name.lowercase()} ${node.skillLevel}/${node.minSkillLevel}) | ${formatSeconds(node.durationSeconds)}s"
            }
            if (nodes.size > 6) body += "... (${nodes.size - 6} pontos adicionais nas opções)."
        }

        return MenuScreenViewModel(
            title = "Coleta",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    private fun gatheringTypeLabel(type: GatheringType): String = when (type) {
        GatheringType.HERBALISM -> "Coleta de ervas"
        GatheringType.MINING -> "Mineração"
        GatheringType.WOODCUTTING -> "Corte de madeira"
        GatheringType.FISHING -> "Pesca"
    }

    private fun formatSeconds(value: Double): String = "%.1f".format(value)
}
