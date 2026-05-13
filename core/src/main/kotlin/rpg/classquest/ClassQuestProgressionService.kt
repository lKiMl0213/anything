package rpg.classquest

import java.util.UUID
import kotlin.math.min
import rpg.item.ItemRarity
import rpg.model.Bonuses
import rpg.model.ItemEffects
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState

internal class ClassQuestProgressionService(
    private val pathCatalog: ClassQuestPathCatalog,
    private val dungeonCatalog: ClassQuestDungeonCatalog,
    private val rewardResolver: ClassQuestRewardResolver
) {
    fun stageSnapshot(context: ClassQuestContext): ClassQuestStageSnapshot {
        val stage = context.definition.stages
            .firstOrNull { it.stage == context.progress.currentStage }
            ?: context.definition.stages.last()
        return ClassQuestStageSnapshot(
            stage = stage,
            mobTargets = stageMobTargets(context.definition, context.progress, stage),
            mobBaseTypes = stageMobBaseTypes(context.definition, context.progress, stage),
            bossTargets = stageBossTargets(context.definition, context.progress, stage),
            bossBaseTypes = stageBossBaseTypes(context.definition, context.progress, stage),
            collectTargets = stageCollectTargets(context.definition, context.progress, stage),
            finalBossTargets = stageFinalBossTargets(context.definition, context.progress, stage)
        )
    }

    fun stageProgressLines(context: ClassQuestContext): List<String> {
        val progress = context.progress
        val stage = stageSnapshot(context)
        val chosenPath = progress.chosenPath
        val dungeon = chosenPath?.let {
            dungeonCatalog.dungeonDefinition(
                context.definition.unlockType,
                it,
                context.definition.classId
            )
        }
        val lines = mutableListOf<String>()
        lines += "Quest ${pathCatalog.unlockLabel(context.definition.unlockType)} - Etapa ${stage.stage.stage}/4"
        chosenPath?.let { lines += "Caminho: ${pathCatalog.pathName(context.definition.unlockType, it)}" }
        if (dungeon != null) {
            lines += "Local recomendado: área de classe (${dungeon.pathName})."
            lines += "Acesso rápido: Exploração > áreas > instância de classe."
        }

        if (stage.stage.killTarget > 0) {
            lines += "Matar mobs especificos: ${progress.killCount}/${stage.stage.killTarget}"
            val mobTargets = resolveMonsterNames(stage.mobTargets, dungeon?.normalMonsters.orEmpty())
            if (mobTargets.isNotEmpty()) {
                lines += "Mobs alvo: ${mobTargets.joinToString(", ")}"
                if (dungeon != null) {
                    lines += "Onde encontrar: apenas na área de classe do caminho ${dungeon.pathName}."
                }
            } else {
                val baseTargets = resolveBaseTypeLabels(stage.mobBaseTypes)
                if (baseTargets.isNotEmpty()) {
                    lines += "Tipos de mob validos: ${baseTargets.joinToString(", ")}"
                }
            }
        }
        if (stage.stage.collectTarget > 0) {
            lines += "Coletar itens: ${progress.collectCount}/${stage.stage.collectTarget}"
            if (dungeon != null) {
                lines += "Coletavel da instancia: ${dungeon.collectibleName}"
                lines += "Onde coletar: drops dos mobs e bosses dessa instancia."
            }
        }
        if (stage.stage.bossKillTarget > 0) {
            lines += "Bosses derrotados: ${progress.bossKillCount}/${stage.stage.bossKillTarget}"
            val bossTargets = resolveMonsterNames(stage.bossTargets, dungeon?.bossMonsters.orEmpty())
            if (bossTargets.isNotEmpty()) {
                lines += "Bosses alvo: ${bossTargets.joinToString(", ")}"
            } else {
                val baseTargets = resolveBaseTypeLabels(stage.bossBaseTypes)
                if (baseTargets.isNotEmpty()) {
                    lines += "Tipos de boss validos: ${baseTargets.joinToString(", ")}"
                }
            }
        }
        if (stage.stage.requiresFinalBoss) {
            val finalBoss = if (progress.finalBossKilled) "concluido" else "pendente"
            lines += "Boss final: $finalBoss"
            val finalBossTargets = resolveMonsterNames(
                stage.finalBossTargets,
                dungeon?.let { listOf(it.finalBoss) }.orEmpty()
            )
            if (finalBossTargets.isNotEmpty()) {
                lines += "Boss final alvo: ${finalBossTargets.joinToString(", ")}"
            }
        }
        return lines
    }

    fun shouldSpawnFinalBoss(context: ClassQuestContext): Boolean {
        val stage = stageSnapshot(context).stage
        if (context.progress.status != ClassQuestStatus.IN_PROGRESS) return false
        if (stage.stage != 4) return false
        if (!stage.requiresFinalBoss || context.progress.finalBossKilled) return false
        if (stage.killTarget > 0 && context.progress.killCount < stage.killTarget) return false
        if (stage.collectTarget > 0 && context.progress.collectCount < stage.collectTarget) return false
        if (stage.bossKillTarget > 0 && context.progress.bossKillCount < stage.bossKillTarget) return false
        return true
    }

    fun collectibleDropsForDungeonKill(
        player: PlayerState,
        context: ClassQuestContext,
        monsterId: String,
        isBoss: Boolean
    ): List<ItemInstance> {
        if (context.progress.status != ClassQuestStatus.IN_PROGRESS) return emptyList()
        val chosenPath = context.progress.chosenPath ?: return emptyList()
        val dungeon = dungeonCatalog.dungeonDefinition(context.definition.unlockType, chosenPath, context.definition.classId) ?: return emptyList()
        val snapshot = stageSnapshot(context)
        if (snapshot.stage.collectTarget <= 0) return emptyList()

        val normalizedMonsterId = monsterId.lowercase()
        if (!dungeon.allIds().contains(normalizedMonsterId)) return emptyList()

        val quantity = when {
            normalizedMonsterId == dungeon.finalBoss.monsterId -> 3
            isBoss -> 2
            else -> 1
        }
        return List(quantity.coerceAtLeast(0)) {
            ItemInstance(
                id = UUID.randomUUID().toString(),
                templateId = dungeon.collectibleTemplateId,
                name = dungeon.collectibleName,
                level = player.level.coerceAtLeast(1),
                minLevel = 1,
                rarity = ItemRarity.COMMON,
                type = ItemType.MATERIAL,
                tags = listOf(
                    ClassQuestTagRules.classTagLiteral(ClassQuestTagRules.anyClassTag),
                    "questReward:true",
                    "sellValue:1",
                    "classquestCollectible:true",
                    "pathLocked:${dungeon.pathId}"
                ),
                bonuses = Bonuses(),
                effects = ItemEffects(),
                value = 1,
                description = "Coletavel exclusivo da instancia de classe."
            )
        }
    }

    fun applyProgress(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        context: ClassQuestContext,
        monsterId: String?,
        isBoss: Boolean,
        monsterBaseType: String?,
        collectedItems: Map<String, Int>,
        normalizeProgress: (ClassQuestProgress) -> ClassQuestProgress,
        withProgress: (PlayerState, ClassQuestDefinition, ClassQuestProgress) -> PlayerState
    ): ClassQuestUpdate {
        var progress = context.progress
        if (progress.status != ClassQuestStatus.IN_PROGRESS || progress.chosenPath.isNullOrBlank()) {
            return ClassQuestUpdate(player, itemInstances)
        }

        var workingPlayer = player
        var workingInstances = itemInstances
        val messages = mutableListOf<String>()
        val granted = mutableMapOf<String, Int>()

        val currentStageSnapshot = stageSnapshot(context)
        if (monsterId != null) {
            if (
                currentStageSnapshot.stage.killTarget > 0 &&
                (
                    monsterMatchesTarget(currentStageSnapshot.mobTargets, monsterId) ||
                        monsterMatchesBaseType(currentStageSnapshot.mobBaseTypes, monsterBaseType)
                    )
            ) {
                progress = progress.copy(
                    killCount = min(currentStageSnapshot.stage.killTarget, progress.killCount + 1)
                )
            }
            if (
                currentStageSnapshot.stage.bossKillTarget > 0 &&
                isBoss &&
                (
                    monsterMatchesTarget(currentStageSnapshot.bossTargets, monsterId) ||
                        monsterMatchesBaseType(currentStageSnapshot.bossBaseTypes, monsterBaseType)
                    )
            ) {
                progress = progress.copy(
                    bossKillCount = min(currentStageSnapshot.stage.bossKillTarget, progress.bossKillCount + 1)
                )
            }
            if (currentStageSnapshot.stage.requiresFinalBoss && isBoss) {
                val finalTargets = currentStageSnapshot.finalBossTargets
                if (finalTargets.isEmpty() || finalTargets.contains(monsterId)) {
                    progress = progress.copy(finalBossKilled = true)
                }
            }
        }

        if (collectedItems.isNotEmpty() && currentStageSnapshot.stage.collectTarget > 0) {
            val collectTargets = currentStageSnapshot.collectTargets
            val gain = collectedItems.entries.sumOf { (itemId, qty) ->
                if (collectTargets.isEmpty() || collectTargets.contains(itemId)) qty else 0
            }
            if (gain > 0) {
                progress = progress.copy(
                    collectCount = min(currentStageSnapshot.stage.collectTarget, progress.collectCount + gain)
                )
            }
        }

        var currentContext = context.copy(progress = normalizeProgress(progress))
        while (isStageComplete(currentContext)) {
            val stage = stageSnapshot(currentContext).stage
            val stageId = stage.stage
            if (stageId !in currentContext.progress.rewardsClaimed) {
                val rewardResult = rewardResolver.grantStageReward(
                    player = workingPlayer,
                    itemInstances = workingInstances,
                    context = currentContext,
                    stage = stage
                )
                workingPlayer = rewardResult.player
                workingInstances = rewardResult.itemInstances
                messages += rewardResult.messages
                for ((itemId, qty) in rewardResult.grantedItems) {
                    granted[itemId] = (granted[itemId] ?: 0) + qty
                }
            }

            var nextProgress = currentContext.progress.copy(
                rewardsClaimed = (currentContext.progress.rewardsClaimed + stageId).distinct().sorted()
            )
            if (stageId >= 4) {
                nextProgress = nextProgress.copy(
                    status = ClassQuestStatus.COMPLETED,
                    currentStage = 4
                )
                messages += "Quest de classe concluida com sucesso."
            } else {
                nextProgress = nextProgress.copy(
                    status = ClassQuestStatus.IN_PROGRESS,
                    currentStage = stageId + 1,
                    killCount = 0,
                    collectCount = 0,
                    bossKillCount = 0,
                    finalBossKilled = false
                )
                messages += "Etapa $stageId concluida. Etapa ${stageId + 1} iniciada."
            }
            currentContext = currentContext.copy(progress = normalizeProgress(nextProgress))
            if (currentContext.progress.status == ClassQuestStatus.COMPLETED) break
        }

        val updatedPlayer = withProgress(workingPlayer, currentContext.definition, currentContext.progress)
        return ClassQuestUpdate(
            player = updatedPlayer,
            itemInstances = workingInstances,
            messages = messages,
            grantedItems = granted
        )
    }

    private fun isStageComplete(context: ClassQuestContext): Boolean {
        val progress = context.progress
        val stage = stageSnapshot(context).stage
        if (stage.killTarget > 0 && progress.killCount < stage.killTarget) return false
        if (stage.collectTarget > 0 && progress.collectCount < stage.collectTarget) return false
        if (stage.bossKillTarget > 0 && progress.bossKillCount < stage.bossKillTarget) return false
        if (stage.requiresFinalBoss && !progress.finalBossKilled) return false
        return true
    }

    private fun monsterMatchesTarget(targets: Set<String>, monsterId: String): Boolean {
        if (targets.isEmpty()) return true
        return targets.contains(monsterId.lowercase())
    }

    private fun monsterMatchesBaseType(targets: Set<String>, baseType: String?): Boolean {
        if (targets.isEmpty()) return false
        val normalized = baseType?.lowercase() ?: return false
        return normalized in targets
    }

    private fun stageMobTargets(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (stage.killTarget <= 0) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonCatalog.dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return dungeon.normalIds()
    }

    private fun stageMobBaseTypes(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (stage.killTarget <= 0) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonCatalog.dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return dungeon.normalMonsters.map { it.baseType.lowercase() }.toSet()
    }

    private fun stageBossTargets(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (stage.bossKillTarget <= 0) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonCatalog.dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return dungeon.bossIds()
    }

    private fun stageBossBaseTypes(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (stage.bossKillTarget <= 0) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonCatalog.dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return dungeon.bossMonsters.map { it.baseType.lowercase() }.toSet()
    }

    private fun stageCollectTargets(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (stage.collectTarget <= 0) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonCatalog.dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return setOf(dungeon.collectibleTemplateId)
    }

    private fun stageFinalBossTargets(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (!stage.requiresFinalBoss) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonCatalog.dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return dungeon.finalBossIds()
    }

    private fun resolveMonsterNames(
        targetIds: Set<String>,
        candidates: List<ClassQuestDungeonMonster>
    ): List<String> {
        if (candidates.isEmpty()) return emptyList()
        val selected = if (targetIds.isEmpty()) {
            candidates
        } else {
            candidates.filter { it.monsterId in targetIds }
        }
        return selected.map { it.displayName }.distinct()
    }

    private fun resolveBaseTypeLabels(baseTypes: Set<String>): List<String> {
        return baseTypes
            .map { value ->
                value.split('_')
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { token ->
                        token.replaceFirstChar { ch -> ch.uppercase() }
                    }
            }
            .distinct()
    }
}







