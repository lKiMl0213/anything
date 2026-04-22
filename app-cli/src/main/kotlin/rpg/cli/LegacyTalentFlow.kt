package rpg.cli

import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.TalentNode
import rpg.model.TalentNodeType
import rpg.model.TalentPointMode
import rpg.model.TalentTree
import rpg.talent.TalentPointLedger
import rpg.talent.TalentRankCheck
import rpg.talent.TalentEffectSummary
import rpg.talent.TalentTreeService

internal class LegacyTalentFlow(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val talentTreeService: TalentTreeService,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val menuAlert: (active: Boolean) -> String,
    private val labelWithAlert: (baseLabel: String, alert: String) -> String,
    private val hasTalentPointsAvailable: (PlayerState) -> Boolean,
    private val effectSummary: TalentEffectSummary = TalentEffectSummary()
) {
    fun openTalents(state: GameState): GameState {
        var player = state.player
        while (true) {
            val trees = talentTreeService.activeTrees(player, repo.talentTreesV2.values)
            if (trees.isEmpty()) {
                println("Nenhuma arvore V2 ativa para o personagem.")
                return state.copy(player = player)
            }
            val ledger = talentTreeService.pointsLedger(player, trees)
            val talentAlert = menuAlert(hasTalentPointsAvailable(player))
            println("\n=== ${labelWithAlert("Arvores de Talento", talentAlert)} ===")
            val totalSpent = ledger.spentByTree.values.sum()
            val totalEarned = when (ledger.mode) {
                TalentPointMode.SHARED_POOL -> ledger.sharedEarned
                TalentPointMode.PER_TREE -> ledger.earnedByTree.values.sum()
            }
            val totalAvailable = when (ledger.mode) {
                TalentPointMode.SHARED_POOL -> ledger.sharedAvailable
                TalentPointMode.PER_TREE -> ledger.availableByTree.values.sum()
            }
            val slots = buildTalentMenuSlots(player, trees, ledger)
            println("Pontos usados/totais: $totalSpent/$totalEarned")
            println("Voce tem $totalAvailable ponto(s) disponivel(is)")
            println("Classe:")
            slots.forEach { slot ->
                val suffix = if (slot.treeId == null) {
                    ""
                } else {
                    " | ${slot.spentPoints} ponto(s) gastos"
                }
                println("${slot.stage}. ${talentOrdinalLabel(slot.stage)}: ${slot.label}$suffix")
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, slots.size) ?: return state.copy(player = player)
            val selectedSlot = slots[choice - 1]
            if (selectedSlot.treeId == null) {
                println("Essa etapa da classe ainda esta bloqueada.")
                continue
            }
            player = openTalentTreeDetails(player, selectedSlot)
        }
    }

    private fun openTalentTreeDetails(initialPlayer: PlayerState, selectedSlot: TalentMenuSlot): PlayerState {
        var player = initialPlayer
        while (true) {
            val trees = talentTreeService.activeTrees(player, repo.talentTreesV2.values)
            val tree = trees.firstOrNull { it.id == selectedSlot.treeId }
            if (tree == null) {
                println("Essa arvore nao esta disponivel no momento.")
                return player
            }
            val ledger = talentTreeService.pointsLedger(player, trees)
            val pointsLabel = if (ledger.mode == TalentPointMode.PER_TREE) {
                "${ledger.availableByTree[tree.id] ?: 0}"
            } else {
                "${ledger.sharedAvailable}"
            }
            println("\n=== ${talentTreeDisplayName(player, tree)} ===")
            println("${talentStageLabel(selectedSlot.stage)} | pontos disponiveis: $pointsLabel")
            val unlockedSkillNames = tree.nodes
                .filter { it.nodeType == TalentNodeType.ACTIVE_SKILL && talentTreeService.nodeCurrentRank(player, it) > 0 }
                .map { it.name }
            println("Skills desbloqueadas: ${if (unlockedSkillNames.isEmpty()) "-" else unlockedSkillNames.joinToString(", ")}")
            val availableNodes = tree.nodes.filter { talentTreeService.canRankUp(player, tree, it.id, trees).allowed }
            println("Pode evoluir agora: ${if (availableNodes.isEmpty()) "-" else availableNodes.joinToString(", ") { it.name }}")
            println("== Nem todas as habilidades podem ser maximizadas, escolha com cuidado ==")

            val orderedNodes = tree.nodes
            orderedNodes.forEachIndexed { index, node ->
                val rank = talentTreeService.nodeCurrentRank(player, node)
                val maxRank = node.maxRank.coerceAtLeast(1)
                val check = talentTreeService.canRankUp(player, tree, node.id, trees)
                println(
                    "${index + 1}. ${node.name} [${talentNodeTypeLabel(node.nodeType)}] " +
                        "Rank ${rank}/${maxRank} | Estado: ${talentNodeStateLabel(rank, maxRank, check)}"
                )
                println("   Pre-req: ${formatTalentPrerequisites(tree, node)} | Exclusivo: ${formatTalentExclusiveGroup(tree, node)}")
                println("   Efeito: ${effectSummary.summarize(node)}")
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, orderedNodes.size) ?: return player
            val node = orderedNodes[choice - 1]
            val result = talentTreeService.rankUp(player, tree, node.id, trees)
            println(result.message)
            if (result.success) {
                player = result.player
            }
        }
    }

    private fun buildTalentMenuSlots(
        player: PlayerState,
        trees: List<TalentTree>,
        ledger: TalentPointLedger
    ): List<TalentMenuSlot> {
        val treeByTier = trees.groupBy { it.tier }
        val baseTree = treeByTier[1]?.firstOrNull()
        val subclassTree = treeByTier[2]?.firstOrNull()
        val specializationTree = treeByTier[3]?.firstOrNull()
        return listOf(
            TalentMenuSlot(
                stage = 1,
                label = baseTree?.let { talentTreeDisplayName(player, it) } ?: talentClassFamilyName(player.classId),
                treeId = baseTree?.id,
                spentPoints = baseTree?.let { ledger.spentByTree[it.id] ?: 0 } ?: 0
            ),
            TalentMenuSlot(
                stage = 2,
                label = subclassTree?.let { talentTreeDisplayName(player, it) } ?: "[BLOQUEADO]",
                treeId = subclassTree?.id,
                spentPoints = subclassTree?.let { ledger.spentByTree[it.id] ?: 0 } ?: 0
            ),
            TalentMenuSlot(
                stage = 3,
                label = specializationTree?.let { talentTreeDisplayName(player, it) } ?: "[BLOQUEADO]",
                treeId = specializationTree?.id,
                spentPoints = specializationTree?.let { ledger.spentByTree[it.id] ?: 0 } ?: 0
            )
        )
    }

    private fun talentTreeDisplayName(player: PlayerState, tree: TalentTree): String {
        return when (tree.tier) {
            1 -> talentClassFamilyName(player.classId)
            2 -> engine.classSystem.subclassDef(player.subclassId)?.name ?: tree.id
            3 -> engine.classSystem.specializationDef(player.specializationId)?.name ?: tree.id
            else -> tree.id
        }
    }

    private fun talentClassFamilyName(classId: String): String = when (classId) {
        "swordman" -> "Espadachim"
        "archer" -> "Arqueiro"
        "mage" -> "Mago"
        else -> engine.classSystem.classDef(classId).name
    }

    private fun talentOrdinalLabel(stage: Int): String = "${stage}a"

    private fun talentStageLabel(stage: Int): String = when (stage) {
        1 -> "1a Classe"
        2 -> "2a Classe"
        3 -> "Especializacao"
        else -> "${talentOrdinalLabel(stage)} Classe"
    }

    private fun talentNodeTypeLabel(type: TalentNodeType): String = when (type) {
        TalentNodeType.ACTIVE_SKILL -> "Ativa"
        TalentNodeType.PASSIVE -> "Passiva"
        TalentNodeType.MODIFIER -> "Modificador"
        TalentNodeType.UPGRADE -> "Aprimoramento"
        TalentNodeType.CHOICE -> "Escolha"
    }

    private fun talentNodeStateLabel(currentRank: Int, maxRank: Int, check: TalentRankCheck): String {
        return when {
            currentRank >= maxRank -> "MAX"
            check.allowed -> "Disponivel"
            check.reason == "Pontos de talento insuficientes." -> "Sem pontos"
            else -> "Bloqueada"
        }
    }

    private fun formatTalentPrerequisites(tree: TalentTree, node: TalentNode): String {
        if (node.prerequisites.isEmpty()) return "-"
        val byId = tree.nodes.associateBy { it.id }
        return node.prerequisites.joinToString(", ") { req ->
            val name = byId[req.nodeId]?.name ?: req.nodeId
            "$name NV${req.minRank}"
        }
    }

    private fun formatTalentExclusiveGroup(tree: TalentTree, node: TalentNode): String {
        val group = node.exclusiveGroup?.takeIf { it.isNotBlank() } ?: return "-"
        val peers = tree.nodes
            .filter { it.id != node.id && it.exclusiveGroup == group }
            .map { it.name }
        return if (peers.isEmpty()) "Grupo exclusivo" else peers.joinToString(", ")
    }
}

private data class TalentMenuSlot(
    val stage: Int,
    val label: String,
    val treeId: String?,
    val spentPoints: Int
)
