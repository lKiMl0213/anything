package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.character.CharacterQueryService
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class CharacterScreenPresenter(
    private val characterQueryService: CharacterQueryService,
    private val support: PresentationSupport
) {
    fun presentCharacterMenu(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Personagem")
        val attributeAlert = if (characterQueryService.hasUnspentAttributePoints(state.player)) " (!)" else ""
        val talentAlert = if (characterQueryService.hasTalentPointsAvailable(state.player)) " (!)" else ""
        return MenuScreenViewModel(
            title = "Personagem",
            summary = support.playerSummary(state),
            bodyLines = listOf("Gerencie equipamentos, inventario, atributos e talentos."),
            options = listOf(
                ScreenOptionViewModel("1", "Equipados", GameAction.OpenEquipped),
                ScreenOptionViewModel("2", "Inventario", GameAction.OpenInventory),
                ScreenOptionViewModel("3", "Atributos$attributeAlert", GameAction.OpenAttributes),
                ScreenOptionViewModel("4", "Talentos$talentAlert", GameAction.OpenTalents),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentAttributes(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Atributos")
        val rows = characterQueryService.attributeRows(state)
        val body = mutableListOf<String>()
        body += "Pontos disponiveis: ${state.player.unspentAttrPoints}"
        body += "Base + equipamento + classe/talento + temporarios = valor final."
        val options = rows.mapIndexed { index, row ->
            ScreenOptionViewModel(
                (index + 1).toString(),
                "${row.code} (${row.label}) = ${row.finalValue}",
                GameAction.InspectAttribute(row.code)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Atributos",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentAttributeDetail(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Atributo")
        val code = session.selectedAttributeCode ?: return support.presentMissingState("Atributo")
        val detail = characterQueryService.attributeDetail(state, code)
            ?: return MenuScreenViewModel(
                title = "Atributo",
                bodyLines = listOf("Atributo nao encontrado."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val options = mutableListOf<ScreenOptionViewModel>()
        if (detail.canAllocate) {
            options += ScreenOptionViewModel(
                "1",
                "Alocar pontos (${detail.availablePoints} disponiveis)",
                GameAction.AllocateAttributePoint(detail.code)
            )
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = characterQueryService.attributeDisplayLabel(code),
            summary = support.playerSummary(state),
            bodyLines = detail.detailLines,
            options = options,
            messages = session.messages
        )
    }

    fun presentTalents(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Talentos")
        val overview = characterQueryService.talentOverview(state)
        val options = overview.stages.map { stage ->
            val suffix = if (stage.treeId == null) "" else " | ${stage.spentPoints} ponto(s) gastos"
            ScreenOptionViewModel(
                stage.stage.toString(),
                "${stage.stage}a: ${stage.label}$suffix",
                GameAction.OpenTalentStage(stage.stage)
            )
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Arvores de Talento",
            summary = support.playerSummary(state),
            bodyLines = listOf(
                "Pontos usados/totais: ${overview.totalSpent}/${overview.totalEarned}",
                "Voce tem ${overview.totalAvailable} ponto(s) disponivel(is)",
                "Classe:"
            ),
            options = options,
            messages = session.messages
        )
    }

    fun presentTalentTreeDetail(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Talentos")
        val treeId = session.selectedTalentTreeId ?: return support.presentMissingState("Talentos")
        val detail = characterQueryService.talentTreeDetail(state, treeId)
            ?: return MenuScreenViewModel(
                title = "Talentos",
                bodyLines = listOf("Essa arvore nao esta disponivel no momento."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val options = detail.nodes.mapIndexed { index, node ->
            ScreenOptionViewModel((index + 1).toString(), node.name, GameAction.InspectTalentNode(node.nodeId))
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        val body = mutableListOf(
            "${detail.stageLabel} | pontos disponiveis: ${detail.pointsAvailable}",
            "Skills desbloqueadas: ${detail.unlockedSkillsLabel}",
            "Skills bloqueadas: ${detail.blockedSkillsLabel}",
            "Pode evoluir agora: ${detail.availableNodesLabel}",
            "== Nem todas as habilidades podem ser maximizadas, escolha com cuidado =="
        )
        detail.nodes.forEachIndexed { index, node ->
            body += "${index + 1}. ${node.name} [${node.typeLabel}] Rank ${node.rankLabel} | Estado: ${node.stateLabel}"
            body += "   Pre-req: ${node.prerequisitesLabel} | Exclusivo: ${node.exclusiveLabel}"
            body += "   Efeito: ${node.effectLabel}"
        }
        return MenuScreenViewModel(
            title = detail.title,
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentTalentNodeDetail(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Talento")
        val treeId = session.selectedTalentTreeId ?: return support.presentMissingState("Talento")
        val nodeId = session.selectedTalentNodeId ?: return support.presentMissingState("Talento")
        val detail = characterQueryService.talentNodeDetail(state, treeId, nodeId)
            ?: return MenuScreenViewModel(
                title = "Talento",
                bodyLines = listOf("Node nao encontrado ou arvore indisponivel."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val options = mutableListOf<ScreenOptionViewModel>()
        if (detail.canRankUp) {
            options += ScreenOptionViewModel(
                "1",
                "Confirmar investir 1 ponto (custo ${detail.nextCost})",
                GameAction.ConfirmTalentRankUp(detail.nodeId)
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
}
