package rpg.classquest

import kotlin.random.Random
import rpg.classsystem.ClassSystem
import rpg.io.DataRepository
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.registry.ItemRegistry

class ClassQuestService(
    private val repo: DataRepository,
    private val itemRegistry: ItemRegistry,
    private val classSystem: ClassSystem,
    private val rng: Random
) {
    private val pathCatalog = ClassQuestPathCatalog(repo = repo, classSystem = classSystem)
    private val dungeonCatalog = ClassQuestDungeonCatalog(pathCatalog::pathName)
    private val rewardResolver = ClassQuestRewardResolver(
        repo = repo,
        itemRegistry = itemRegistry,
        classSystem = classSystem,
        rng = rng
    )
    private val progressionService = ClassQuestProgressionService(
        pathCatalog = pathCatalog,
        dungeonCatalog = dungeonCatalog,
        rewardResolver = rewardResolver
    )

    fun synchronize(player: PlayerState): PlayerState {
        val synced = player.classQuestProgressByKey.toMutableMap()

        for (unlockType in ClassQuestUnlockType.entries) {
            val definition = pathCatalog.definitionFor(player, unlockType) ?: continue
            val key = progressKey(definition.classId, unlockType)
            val current = synced[key] ?: emptyProgress(definition.classId, unlockType)
            val shouldShow = pathCatalog.shouldShowQuest(player, definition)
            val nextStatus = when {
                current.status == ClassQuestStatus.COMPLETED -> ClassQuestStatus.COMPLETED
                current.status == ClassQuestStatus.IN_PROGRESS -> ClassQuestStatus.IN_PROGRESS
                shouldShow -> ClassQuestStatus.AVAILABLE
                else -> ClassQuestStatus.NOT_AVAILABLE
            }
            synced[key] = normalize(current.copy(status = nextStatus))
        }

        return player.copy(classQuestProgressByKey = synced.toMap())
    }

    fun currentContext(player: PlayerState): ClassQuestContext? {
        val syncedPlayer = synchronize(player)
        val definition = pathCatalog.activeDefinition(syncedPlayer) ?: return null
        val progress = progressForDefinition(syncedPlayer, definition)
        if (progress.status == ClassQuestStatus.NOT_AVAILABLE) return null
        if (progress.status == ClassQuestStatus.COMPLETED && progress.chosenPath.isNullOrBlank()) return null
        return ClassQuestContext(definition = definition, progress = progress)
    }

    fun progressFor(player: PlayerState, unlockType: ClassQuestUnlockType): ClassQuestProgress? {
        val definition = pathCatalog.definitionFor(player, unlockType) ?: return null
        return progressForDefinition(synchronize(player), definition)
    }

    fun completedPathFor(player: PlayerState, unlockType: ClassQuestUnlockType): String? {
        val progress = progressFor(player, unlockType) ?: return null
        if (progress.status != ClassQuestStatus.COMPLETED) return null
        return progress.chosenPath?.takeIf { it.isNotBlank() }
    }

    fun choosePath(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        pathId: String
    ): ClassQuestUpdate {
        val syncedPlayer = synchronize(player)
        val context = currentContext(syncedPlayer)
            ?: return ClassQuestUpdate(
                player = syncedPlayer,
                itemInstances = itemInstances,
                messages = listOf("Nenhuma quest de classe disponivel no momento.")
            )

        val chosen = pathId.trim().lowercase()
        if (chosen !in context.definition.paths().map { it.lowercase() }.toSet()) {
            return ClassQuestUpdate(
                player = syncedPlayer,
                itemInstances = itemInstances,
                messages = listOf("Caminho invalido para esta quest.")
            )
        }

        val progress = context.progress
        if (progress.status == ClassQuestStatus.COMPLETED) {
            return ClassQuestUpdate(
                player = syncedPlayer,
                itemInstances = itemInstances,
                messages = listOf("Esta quest de classe ja foi concluida.")
            )
        }
        if (
            progress.status == ClassQuestStatus.IN_PROGRESS &&
            progress.chosenPath != null &&
            progress.chosenPath.lowercase() != chosen
        ) {
            return ClassQuestUpdate(
                player = syncedPlayer,
                itemInstances = itemInstances,
                messages = listOf("Voce ja iniciou outro caminho nesta quest.")
            )
        }

        val keepCurrentProgress = progress.chosenPath?.lowercase() == chosen
        val updatedProgress = normalize(
            progress.copy(
                status = ClassQuestStatus.IN_PROGRESS,
                chosenPath = chosen,
                currentStage = if (keepCurrentProgress) progress.currentStage else 1,
                killCount = if (keepCurrentProgress) progress.killCount else 0,
                collectCount = if (keepCurrentProgress) progress.collectCount else 0,
                bossKillCount = if (keepCurrentProgress) progress.bossKillCount else 0,
                finalBossKilled = if (keepCurrentProgress) progress.finalBossKilled else false
            )
        )
        val message = if (keepCurrentProgress) {
            "Continuando caminho ${pathCatalog.pathName(context.definition.unlockType, chosen)}."
        } else {
            "Caminho escolhido: ${pathCatalog.pathName(context.definition.unlockType, chosen)}."
        }

        return ClassQuestUpdate(
            player = withProgress(syncedPlayer, context.definition, updatedProgress),
            itemInstances = itemInstances,
            messages = listOf(message)
        )
    }

    fun cancelCurrentQuest(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): ClassQuestUpdate {
        val syncedPlayer = synchronize(player)
        val context = currentContext(syncedPlayer)
            ?: return ClassQuestUpdate(
                player = syncedPlayer,
                itemInstances = itemInstances,
                messages = listOf("Nenhuma quest de classe disponivel.")
            )
        val progress = context.progress
        if (progress.status != ClassQuestStatus.IN_PROGRESS) {
            return ClassQuestUpdate(
                player = syncedPlayer,
                itemInstances = itemInstances,
                messages = listOf("Nenhuma quest de classe em andamento para cancelar.")
            )
        }

        val reset = normalize(
            progress.copy(
                status = ClassQuestStatus.CANCELED,
                chosenPath = null,
                currentStage = 1,
                killCount = 0,
                collectCount = 0,
                bossKillCount = 0,
                finalBossKilled = false
            )
        )
        return ClassQuestUpdate(
            player = withProgress(syncedPlayer, context.definition, reset),
            itemInstances = itemInstances,
            messages = listOf("Quest de classe cancelada. Progresso retornou para a etapa 1.")
        )
    }

    fun onCombatOutcome(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        monsterId: String,
        isBoss: Boolean,
        monsterBaseType: String? = null
    ): ClassQuestUpdate {
        return applyProgress(
            player = synchronize(player),
            itemInstances = itemInstances,
            monsterId = monsterId.lowercase(),
            isBoss = isBoss,
            monsterBaseType = monsterBaseType?.lowercase(),
            collectedItems = emptyMap()
        )
    }

    fun onItemsCollected(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        collectedItems: Map<String, Int>
    ): ClassQuestUpdate {
        val normalized = collectedItems
            .mapKeys { it.key.lowercase() }
            .mapValues { (_, qty) -> qty.coerceAtLeast(0) }
            .filterValues { it > 0 }
        if (normalized.isEmpty()) return ClassQuestUpdate(synchronize(player), itemInstances)
        return applyProgress(
            player = synchronize(player),
            itemInstances = itemInstances,
            monsterId = null,
            isBoss = false,
            monsterBaseType = null,
            collectedItems = normalized
        )
    }

    fun stageSnapshot(context: ClassQuestContext): ClassQuestStageSnapshot {
        return progressionService.stageSnapshot(context)
    }

    fun stageProgressLines(context: ClassQuestContext): List<String> {
        return progressionService.stageProgressLines(context)
    }

    fun pathName(unlockType: ClassQuestUnlockType, pathId: String): String {
        return pathCatalog.pathName(unlockType, pathId)
    }

    fun statusLabel(status: ClassQuestStatus): String = when (status) {
        ClassQuestStatus.NOT_AVAILABLE -> "Nao disponivel"
        ClassQuestStatus.AVAILABLE -> "Disponivel"
        ClassQuestStatus.IN_PROGRESS -> "Em andamento"
        ClassQuestStatus.COMPLETED -> "Concluida"
        ClassQuestStatus.CANCELED -> "Cancelada"
    }

    fun activeDungeon(player: PlayerState): ClassQuestDungeonDefinition? {
        val context = currentContext(player) ?: return null
        if (context.progress.status != ClassQuestStatus.IN_PROGRESS) return null
        val chosenPath = context.progress.chosenPath ?: return null
        return dungeonCatalog.dungeonDefinition(context.definition.unlockType, chosenPath, context.definition.classId)
    }

    fun shouldSpawnFinalBoss(context: ClassQuestContext): Boolean {
        return progressionService.shouldSpawnFinalBoss(context)
    }

    fun collectibleDropsForDungeonKill(
        player: PlayerState,
        monsterId: String,
        isBoss: Boolean
    ): List<ItemInstance> {
        val context = currentContext(player) ?: return emptyList()
        return progressionService.collectibleDropsForDungeonKill(
            player = player,
            context = context,
            monsterId = monsterId,
            isBoss = isBoss
        )
    }

    private fun applyProgress(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        monsterId: String?,
        isBoss: Boolean,
        monsterBaseType: String?,
        collectedItems: Map<String, Int>
    ): ClassQuestUpdate {
        val context = currentContext(player) ?: return ClassQuestUpdate(player, itemInstances)
        return progressionService.applyProgress(
            player = player,
            itemInstances = itemInstances,
            context = context,
            monsterId = monsterId,
            isBoss = isBoss,
            monsterBaseType = monsterBaseType,
            collectedItems = collectedItems,
            normalizeProgress = ::normalize,
            withProgress = ::withProgress
        )
    }

    private fun progressForDefinition(player: PlayerState, definition: ClassQuestDefinition): ClassQuestProgress {
        val key = progressKey(definition.classId, definition.unlockType)
        val current = player.classQuestProgressByKey[key] ?: emptyProgress(definition.classId, definition.unlockType)
        return normalize(current)
    }

    private fun withProgress(
        player: PlayerState,
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress
    ): PlayerState {
        val key = progressKey(definition.classId, definition.unlockType)
        return player.copy(
            classQuestProgressByKey = player.classQuestProgressByKey + (key to normalize(progress))
        )
    }

    private fun emptyProgress(classId: String, unlockType: ClassQuestUnlockType): ClassQuestProgress {
        return ClassQuestProgress(
            classId = classId.lowercase(),
            unlockType = unlockType,
            status = ClassQuestStatus.NOT_AVAILABLE
        )
    }

    private fun normalize(progress: ClassQuestProgress): ClassQuestProgress {
        return progress.copy(
            classId = progress.classId.lowercase(),
            chosenPath = progress.chosenPath?.lowercase(),
            currentStage = progress.currentStage.coerceIn(1, 4),
            killCount = progress.killCount.coerceAtLeast(0),
            collectCount = progress.collectCount.coerceAtLeast(0),
            bossKillCount = progress.bossKillCount.coerceAtLeast(0),
            rewardsClaimed = progress.rewardsClaimed.distinct().sorted()
        )
    }

    private fun progressKey(classId: String, unlockType: ClassQuestUnlockType): String {
        return "${classId.lowercase()}:${unlockType.name.lowercase()}"
    }
}
