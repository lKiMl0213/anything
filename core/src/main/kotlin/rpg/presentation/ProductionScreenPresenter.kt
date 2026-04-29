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
            bodyLines = listOf("Selecione a disciplina de craft."),
            options = listOf(
                ScreenOptionViewModel("1", "Forja", GameAction.OpenCraftDiscipline(CraftDiscipline.FORGE)),
                ScreenOptionViewModel("2", "Alquimia", GameAction.OpenCraftDiscipline(CraftDiscipline.ALCHEMY)),
                ScreenOptionViewModel("3", "Culinaria", GameAction.OpenCraftDiscipline(CraftDiscipline.COOKING)),
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
            val status = if (recipe.available) "Disponivel (${recipe.maxCraftable}x)" else "Indisponivel"
            ScreenOptionViewModel(
                (index + 1).toString(),
                "${recipe.name} -> ${recipe.outputLabel} | $status",
                GameAction.CraftRecipe(recipe.id)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf<String>()
        body += "Disciplina: ${discipline.name.lowercase()}"
        if (recipes.isEmpty()) {
            body += "Nenhuma receita cadastrada para esta disciplina."
        } else {
            body += "Ingredientes:"
            recipes.take(5).forEach { recipe ->
                val reason = if (recipe.blockedReasons.isEmpty()) "" else " [${recipe.blockedReasons.joinToString(" | ")}]"
                body += "- ${recipe.name}$reason"
                recipe.ingredientLines.forEach { ingredientLine ->
                    body += "  $ingredientLine"
                }
            }
            if (recipes.size > 5) body += "... (${recipes.size - 5} receitas adicionais listadas nas opcoes)."
        }

        return MenuScreenViewModel(
            title = "Receitas",
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
                bodyLines = listOf("Escolha um tipo de coleta no menu de producao."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val nodes = queryService.gatherNodes(state, type)
        val options = nodes.mapIndexed { index, node ->
            val status = if (node.available) "Disponivel" else "Skill ${node.skillLevel}/${node.minSkillLevel}"
            ScreenOptionViewModel(
                (index + 1).toString(),
                "${node.name} -> ${node.resourceLabel} | $status",
                GameAction.GatherNode(node.id)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf<String>()
        body += "Categoria: ${gatheringTypeLabel(type)}"
        if (nodes.isEmpty()) {
            body += "Nenhum ponto de coleta disponivel."
        } else {
            nodes.take(6).forEach { node ->
                body += "- ${node.name}: ${node.resourceLabel} (skill ${node.skillType.name.lowercase()} ${node.skillLevel}/${node.minSkillLevel})"
            }
            if (nodes.size > 6) body += "... (${nodes.size - 6} pontos adicionais nas opcoes)."
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
        GatheringType.MINING -> "Mineracao"
        GatheringType.WOODCUTTING -> "Corte de madeira"
        GatheringType.FISHING -> "Pesca"
    }
}
