package rpg.classquest

import java.util.UUID
import kotlin.math.min
import kotlin.random.Random
import rpg.classsystem.AttributeEngine
import rpg.classsystem.ClassSystem
import rpg.inventory.InventorySystem
import rpg.io.DataRepository
import rpg.item.ItemRarity
import rpg.model.Bonuses
import rpg.model.DerivedStats
import rpg.model.EquipSlot
import rpg.model.ItemEffects
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.progression.ExperienceEngine
import rpg.registry.ItemRegistry

data class ClassQuestUpdate(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val messages: List<String> = emptyList(),
    val grantedItems: Map<String, Int> = emptyMap()
)

data class ClassQuestContext(
    val definition: ClassQuestDefinition,
    val progress: ClassQuestProgress
)

data class ClassQuestStageSnapshot(
    val stage: ClassQuestStageDefinition,
    val mobTargets: Set<String>,
    val mobBaseTypes: Set<String>,
    val bossTargets: Set<String>,
    val bossBaseTypes: Set<String>,
    val collectTargets: Set<String>,
    val finalBossTargets: Set<String>
)

data class ClassQuestDungeonMonster(
    val monsterId: String,
    val displayName: String,
    val baseArchetypeId: String,
    val baseType: String,
    val family: String,
    val lootProfileId: String,
    val identityTags: Set<String> = emptySet()
)

data class ClassQuestDungeonDefinition(
    val unlockType: ClassQuestUnlockType,
    val classId: String,
    val pathId: String,
    val pathName: String,
    val normalMonsters: List<ClassQuestDungeonMonster>,
    val bossMonsters: List<ClassQuestDungeonMonster>,
    val finalBoss: ClassQuestDungeonMonster,
    val collectibleTemplateId: String,
    val collectibleName: String
) {
    fun normalIds(): Set<String> = normalMonsters.map { it.monsterId }.toSet()
    fun bossIds(): Set<String> = bossMonsters.map { it.monsterId }.toSet()
    fun finalBossIds(): Set<String> = setOf(finalBoss.monsterId)
    fun allIds(): Set<String> = normalIds() + bossIds() + finalBossIds()
}

class ClassQuestService(
    private val repo: DataRepository,
    private val itemRegistry: ItemRegistry,
    private val classSystem: ClassSystem,
    private val rng: Random
) {
    fun synchronize(player: PlayerState): PlayerState {
        val classDef = classSystem.classDef(player.classId)
        val synced = player.classQuestProgressByKey.toMutableMap()

        for (unlockType in ClassQuestUnlockType.entries) {
            val definition = definitionFor(classDef, unlockType) ?: continue
            val key = progressKey(definition.classId, unlockType)
            val current = synced[key] ?: emptyProgress(definition.classId, unlockType)
            val shouldShow = shouldShowQuest(player, definition)
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
        val definition = activeDefinition(syncedPlayer) ?: return null
        val progress = progressForDefinition(syncedPlayer, definition)
        if (progress.status == ClassQuestStatus.NOT_AVAILABLE) return null
        if (progress.status == ClassQuestStatus.COMPLETED && progress.chosenPath.isNullOrBlank()) return null
        return ClassQuestContext(definition = definition, progress = progress)
    }

    fun progressFor(player: PlayerState, unlockType: ClassQuestUnlockType): ClassQuestProgress? {
        val classDef = runCatching { classSystem.classDef(player.classId) }.getOrNull() ?: return null
        val definition = definitionFor(classDef, unlockType) ?: return null
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
            "Continuando caminho ${pathName(context.definition.unlockType, chosen)}."
        } else {
            "Caminho escolhido: ${pathName(context.definition.unlockType, chosen)}."
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
        val lines = mutableListOf<String>()
        lines += "Quest ${unlockLabel(context.definition.unlockType)} - Etapa ${stage.stage.stage}/4"
        progress.chosenPath?.let { lines += "Caminho: ${pathName(context.definition.unlockType, it)}" }

        if (stage.stage.killTarget > 0) {
            lines += "Matar mobs especificos: ${progress.killCount}/${stage.stage.killTarget}"
        }
        if (stage.stage.collectTarget > 0) {
            lines += "Coletar itens: ${progress.collectCount}/${stage.stage.collectTarget}"
            val path = progress.chosenPath
            if (path != null) {
                val dungeon = dungeonDefinition(context.definition.unlockType, path, context.definition.classId)
                if (dungeon != null) {
                    lines += "Coletavel da instancia: ${dungeon.collectibleName}"
                }
            }
        }
        if (stage.stage.bossKillTarget > 0) {
            lines += "Bosses derrotados: ${progress.bossKillCount}/${stage.stage.bossKillTarget}"
        }
        if (stage.stage.requiresFinalBoss) {
            val finalBoss = if (progress.finalBossKilled) "concluido" else "pendente"
            lines += "Boss final: $finalBoss"
        }
        return lines
    }

    fun pathName(unlockType: ClassQuestUnlockType, pathId: String): String {
        val id = pathId.lowercase()
        return when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> repo.subclasses[id]?.name ?: id
            ClassQuestUnlockType.SPECIALIZATION -> repo.specializations[id]?.name ?: id
        }
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
        return dungeonDefinition(context.definition.unlockType, chosenPath, context.definition.classId)
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
        monsterId: String,
        isBoss: Boolean
    ): List<ItemInstance> {
        val context = currentContext(player) ?: return emptyList()
        if (context.progress.status != ClassQuestStatus.IN_PROGRESS) return emptyList()
        val chosenPath = context.progress.chosenPath ?: return emptyList()
        val dungeon = dungeonDefinition(context.definition.unlockType, chosenPath, context.definition.classId) ?: return emptyList()
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

    private fun applyProgress(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        monsterId: String?,
        isBoss: Boolean,
        monsterBaseType: String?,
        collectedItems: Map<String, Int>
    ): ClassQuestUpdate {
        val context = currentContext(player) ?: return ClassQuestUpdate(player, itemInstances)
        var progress = context.progress
        if (progress.status != ClassQuestStatus.IN_PROGRESS || progress.chosenPath.isNullOrBlank()) {
            return ClassQuestUpdate(player, itemInstances)
        }

        var workingPlayer = player
        var workingInstances = itemInstances
        val messages = mutableListOf<String>()
        val granted = mutableMapOf<String, Int>()

        val stageSnapshot = stageSnapshot(context)
        if (monsterId != null) {
            if (
                stageSnapshot.stage.killTarget > 0 &&
                (
                    monsterMatchesTarget(stageSnapshot.mobTargets, monsterId) ||
                        monsterMatchesBaseType(stageSnapshot.mobBaseTypes, monsterBaseType)
                    )
            ) {
                progress = progress.copy(
                    killCount = min(stageSnapshot.stage.killTarget, progress.killCount + 1)
                )
            }
            if (
                stageSnapshot.stage.bossKillTarget > 0 &&
                isBoss &&
                (
                    monsterMatchesTarget(stageSnapshot.bossTargets, monsterId) ||
                        monsterMatchesBaseType(stageSnapshot.bossBaseTypes, monsterBaseType)
                    )
            ) {
                progress = progress.copy(
                    bossKillCount = min(stageSnapshot.stage.bossKillTarget, progress.bossKillCount + 1)
                )
            }
            if (stageSnapshot.stage.requiresFinalBoss && isBoss) {
                val finalTargets = stageSnapshot.finalBossTargets
                if (finalTargets.isEmpty() || finalTargets.contains(monsterId)) {
                    progress = progress.copy(finalBossKilled = true)
                }
            }
        }

        if (collectedItems.isNotEmpty() && stageSnapshot.stage.collectTarget > 0) {
            val collectTargets = stageSnapshot.collectTargets
            val gain = collectedItems.entries.sumOf { (itemId, qty) ->
                if (collectTargets.isEmpty() || collectTargets.contains(itemId)) qty else 0
            }
            if (gain > 0) {
                progress = progress.copy(
                    collectCount = min(stageSnapshot.stage.collectTarget, progress.collectCount + gain)
                )
            }
        }

        var currentContext = context.copy(progress = normalize(progress))
        while (isStageComplete(currentContext)) {
            val stage = stageSnapshot(currentContext).stage
            val stageId = stage.stage
            if (stageId !in currentContext.progress.rewardsClaimed) {
                val rewardResult = grantStageReward(
                    player = workingPlayer,
                    itemInstances = workingInstances,
                    context = currentContext
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
            currentContext = currentContext.copy(progress = normalize(nextProgress))
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

    private fun grantStageReward(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        context: ClassQuestContext
    ): ClassQuestUpdate {
        val stage = stageSnapshot(context).stage
        val reward = stage.reward
        var updatedPlayer = player.copy(gold = player.gold + reward.gold)
        updatedPlayer = applyXpWithAutoPoints(updatedPlayer, reward.xp)

        val updatedInstances = itemInstances.toMutableMap()
        val incomingItems = mutableListOf<String>()

        if (itemExists(reward.hpPotionId)) {
            repeat(reward.hpPotionQty.coerceAtLeast(0)) { incomingItems += reward.hpPotionId }
        }
        val manaItemId = if (itemExists(reward.mpPotionId)) reward.mpPotionId else defaultMpPotionId
        if (itemExists(manaItemId)) {
            repeat(reward.mpPotionQty.coerceAtLeast(0)) { incomingItems += manaItemId }
        }

        for (slot in reward.equipmentSlots) {
            val generated = buildQuestSetEquipment(
                player = updatedPlayer,
                definition = context.definition,
                pathId = context.progress.chosenPath ?: continue,
                slot = slot,
                stage = stage.stage
            )
            updatedInstances[generated.id] = generated
            incomingItems += generated.id
        }

        val insert = InventorySystem.addItemsWithLimit(
            player = updatedPlayer,
            itemInstances = updatedInstances,
            itemRegistry = itemRegistry,
            incomingItemIds = incomingItems
        )

        val rejectedGenerated = insert.rejected.filter { updatedInstances.containsKey(it) }
        for (id in rejectedGenerated) {
            updatedInstances.remove(id)
        }

        val granted = mutableMapOf<String, Int>()
        for (id in insert.accepted) {
            val canonical = updatedInstances[id]?.templateId ?: id
            granted[canonical] = (granted[canonical] ?: 0) + 1
        }

        updatedPlayer = updatedPlayer.copy(inventory = insert.inventory)
        val stageName = "Etapa ${stage.stage}"
        val messages = mutableListOf(
            "$stageName: +${reward.xp} XP, +${reward.gold} ouro e recompensas recebidas."
        )
        if (insert.rejected.isNotEmpty()) {
            messages += "$stageName: inventario cheio, ${insert.rejected.size} item(ns) foram descartados."
        }

        return ClassQuestUpdate(
            player = updatedPlayer,
            itemInstances = updatedInstances.toMap(),
            messages = messages,
            grantedItems = granted
        )
    }

    private fun buildQuestSetEquipment(
        player: PlayerState,
        definition: ClassQuestDefinition,
        pathId: String,
        slot: EquipSlot,
        stage: Int
    ): ItemInstance {
        val normalizedPath = pathId.lowercase()
        val basePower = player.level.coerceAtLeast(1).toDouble() *
            if (definition.unlockType == ClassQuestUnlockType.SUBCLASS) 1.05 else 1.35

        val derived = when (slot) {
            EquipSlot.HEAD -> DerivedStats(defPhysical = basePower * 0.9, hpMax = basePower * 2.0)
            EquipSlot.CHEST -> DerivedStats(defPhysical = basePower * 1.4, hpMax = basePower * 3.0)
            EquipSlot.LEGS -> DerivedStats(defPhysical = basePower * 1.1, hpMax = basePower * 2.3)
            EquipSlot.GLOVES -> {
                if (isMagicPath(normalizedPath, definition.classId)) {
                    DerivedStats(damageMagic = basePower * 0.8, attackSpeed = basePower * 0.02)
                } else {
                    DerivedStats(damagePhysical = basePower * 0.8, attackSpeed = basePower * 0.02)
                }
            }
            EquipSlot.BOOTS -> DerivedStats(defPhysical = basePower * 0.8, moveSpeed = basePower * 0.03)
            EquipSlot.WEAPON_MAIN -> {
                if (isMagicPath(normalizedPath, definition.classId)) {
                    DerivedStats(damageMagic = basePower * 2.6, mpMax = basePower * 1.4)
                } else {
                    DerivedStats(damagePhysical = basePower * 2.6, hpMax = basePower * 1.4)
                }
            }
            else -> DerivedStats(defPhysical = basePower * 0.7)
        }

        val slotName = if (slot == EquipSlot.WEAPON_MAIN) {
            pathWeaponName(definition.unlockType, normalizedPath, definition.classId)
        } else {
            slotDisplayName(slot)
        }
        val questKind = if (definition.unlockType == ClassQuestUnlockType.SUBCLASS) "Subclasse" else "Especializacao"
        val pathName = pathName(definition.unlockType, normalizedPath)
        val templateId = "class_quest_${definition.classId}_${definition.unlockType.name.lowercase()}_${normalizedPath}_${slot.name.lowercase()}"
        val tags = listOf(
            ClassQuestTagRules.classTagLiteral(normalizedPath),
            "classLocked:${definition.classId}",
            "pathLocked:$normalizedPath",
            "questReward:true",
            "sellValue:1"
        )
        return ItemInstance(
            id = UUID.randomUUID().toString(),
            templateId = templateId,
            name = "$slotName de $pathName",
            level = player.level.coerceAtLeast(1),
            minLevel = player.level.coerceAtLeast(1),
            rarity = ItemRarity.RARE,
            type = ItemType.EQUIPMENT,
            slot = slot,
            twoHanded = false,
            tags = tags,
            bonuses = Bonuses(derivedAdd = derived),
            value = 1,
            description = "Recompensa da quest de $questKind (etapa $stage)."
        )
    }

    private fun applyXpWithAutoPoints(player: PlayerState, xp: Int): PlayerState {
        if (xp <= 0) return player
        var updated = ExperienceEngine.applyXp(player, xp)
        val gained = updated.level - player.level
        if (gained <= 0) return updated
        val classDef = classSystem.classDef(updated.classId)
        val raceDef = classSystem.raceDef(updated.raceId)
        val subclassDef = classSystem.subclassDef(updated.subclassId)
        val specializationDef = classSystem.specializationDef(updated.specializationId)
        repeat(gained) {
            updated = AttributeEngine.applyAutoPoints(updated, classDef, raceDef, subclassDef, specializationDef, rng)
        }
        return updated
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
        val dungeon = dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return dungeon.normalIds()
    }

    private fun stageMobBaseTypes(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (stage.killTarget <= 0) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return dungeon.normalMonsters.map { it.baseType.lowercase() }.toSet()
    }

    private fun stageBossTargets(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (stage.bossKillTarget <= 0) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return dungeon.bossIds()
    }

    private fun stageBossBaseTypes(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (stage.bossKillTarget <= 0) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return dungeon.bossMonsters.map { it.baseType.lowercase() }.toSet()
    }

    private fun stageCollectTargets(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (stage.collectTarget <= 0) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return setOf(dungeon.collectibleTemplateId)
    }

    private fun stageFinalBossTargets(
        definition: ClassQuestDefinition,
        progress: ClassQuestProgress,
        stage: ClassQuestStageDefinition
    ): Set<String> {
        if (!stage.requiresFinalBoss) return emptySet()
        val path = progress.chosenPath ?: return emptySet()
        val dungeon = dungeonDefinition(definition.unlockType, path, definition.classId) ?: return emptySet()
        return dungeon.finalBossIds()
    }

    private fun dungeonDefinition(
        unlockType: ClassQuestUnlockType,
        pathId: String,
        classId: String
    ): ClassQuestDungeonDefinition? {
        val path = pathId.lowercase()
        val baseClass = classId.lowercase()
        fun monster(
            id: String,
            name: String,
            baseArchetypeId: String,
            baseType: String = "",
            family: String = "",
            lootProfileId: String = "",
            vararg tags: String
        ): ClassQuestDungeonMonster {
            val inferredBaseType = baseType.trim().ifBlank {
                name.substringBefore(' ').ifBlank { baseArchetypeId.substringBefore('_') }
            }
            val normalizedBaseType = inferredBaseType.trim().lowercase().ifBlank { "monster" }
            val normalizedFamily = family.trim().lowercase().ifBlank { defaultFamilyForBaseType(normalizedBaseType) }
            val normalizedLootProfile = lootProfileId.trim().lowercase().ifBlank {
                defaultLootProfileFor(normalizedBaseType, normalizedFamily)
            }
            return ClassQuestDungeonMonster(
                monsterId = id.lowercase(),
                displayName = name,
                baseArchetypeId = baseArchetypeId.lowercase(),
                baseType = normalizedBaseType,
                family = normalizedFamily,
                lootProfileId = normalizedLootProfile,
                identityTags = tags.map { it.lowercase() }.toSet()
            )
        }

        val definition = when (path) {
            "pyromancer" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "mage",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SUBCLASS, path),
                normalMonsters = listOf(
                    monster("cq_pyromancer_elemental_fire", "Elemental de Fogo", "slime", "elemental", "elemental", "elemental_fire", "fogo"),
                    monster("cq_pyromancer_salamander", "Salamandra Ignea", "skeleton_warrior", "salamander", "elemental", "elemental_fire", "fogo"),
                    monster("cq_pyromancer_magma_core", "Nucleo de Magma", "mimic", "magma_core", "elemental", "elemental_fire", "magma"),
                    monster("cq_pyromancer_ash_cultist", "Cultista das Cinzas", "skeleton_archer", "ash_cultist", "humanoid", "elemental_fire", "cultist"),
                    monster("cq_pyromancer_volcanic_guardian", "Guardiao Vulcanico", "skeleton_warrior", "volcanic_guardian", "construct", "elemental_fire", "vulcanic")
                ),
                bossMonsters = listOf(
                    monster("cq_pyromancer_igneous_priest", "Sacerdote Igneo", "lich", "igneous_priest", "humanoid", "elemental_fire", "boss"),
                    monster("cq_pyromancer_volcanic_colossus", "Colosso Vulcanico", "mimic", "volcanic_colossus", "construct", "elemental_fire", "boss")
                ),
                finalBoss = monster("cq_pyromancer_final_furnace_avatar", "Avatar da Fornalha", "lich", "furnace_avatar", "elemental", "elemental_fire", "boss_final"),
                collectibleTemplateId = "cq_collect_pyromancer",
                collectibleName = "Nucleo de Brasa"
            )
            "arcanist" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "mage",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SUBCLASS, path),
                normalMonsters = listOf(
                    monster("cq_arcanist_arcane_sentinel", "Sentinela Arcana", "skeleton_warrior", "arcane_sentinel", "construct", "construct_arcane", "arcane"),
                    monster("cq_arcanist_unstable_orb", "Orbe Instavel", "slime", "unstable_orb", "construct", "construct_arcane", "arcane"),
                    monster("cq_arcanist_temporal_echo", "Eco Temporal", "skeleton_archer", "temporal_echo", "construct", "construct_arcane", "temporal"),
                    monster("cq_arcanist_ether_watcher", "Vigia do Eter", "skeleton_archer", "ether_watcher", "construct", "construct_arcane", "ether"),
                    monster("cq_arcanist_arcane_construct", "Construto Arcano", "mimic", "arcane_construct", "construct", "construct_arcane", "construct")
                ),
                bossMonsters = listOf(
                    monster("cq_arcanist_void_magister", "Magistrado do Vazio", "lich", "void_magister", "construct", "construct_arcane", "boss"),
                    monster("cq_arcanist_runic_executioner", "Executor Runico", "mimic", "runic_executioner", "construct", "construct_arcane", "boss")
                ),
                finalBoss = monster("cq_arcanist_final_time_judge", "Juiz das Cronorunas", "lich", "time_judge", "construct", "construct_arcane", "boss_final"),
                collectibleTemplateId = "cq_collect_arcanist",
                collectibleName = "Fragmento Temporal"
            )
            "hunter" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "archer",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SUBCLASS, path),
                normalMonsters = listOf(
                    monster("cq_hunter_wolf", "Lobo", "skeleton_warrior", "wolf", "beast", "wolf_beast", "predator"),
                    monster("cq_hunter_gray_wolf", "Lobo Cinzento", "skeleton_archer", "wolf", "beast", "wolf_beast", "predator"),
                    monster("cq_hunter_wild_panther", "Pantera Selvagem", "slime", "panther", "beast", "wolf_beast", "predator"),
                    monster("cq_hunter_young_bear", "Urso Jovem", "mimic", "bear", "beast", "wolf_beast", "beast"),
                    monster("cq_hunter_predator_hawk", "Falcao Predador", "skeleton_archer", "hawk", "beast", "wolf_beast", "predator")
                ),
                bossMonsters = listOf(
                    monster("cq_hunter_pack_tyrant", "Tirano da Matilha", "mimic", "pack_tyrant", "beast", "wolf_beast", "boss"),
                    monster("cq_hunter_primal_alpha", "Alfa Primal", "lich", "primal_alpha", "beast", "wolf_beast", "boss")
                ),
                finalBoss = monster("cq_hunter_final_fang_lord", "Senhor das Presas", "lich", "fang_lord", "beast", "wolf_beast", "boss_final"),
                collectibleTemplateId = "cq_collect_hunter",
                collectibleName = "Presa Marcada"
            )
            "assassin" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "archer",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SUBCLASS, path),
                normalMonsters = listOf(
                    monster("cq_assassin_stealth_bandit", "Bandido Furtivo", "skeleton_archer", "bandit", "humanoid", "bandit_shadow", "shadow"),
                    monster("cq_assassin_shadow_blade", "Lamina Sombria", "skeleton_warrior", "shadow_blade", "humanoid", "bandit_shadow", "assassin"),
                    monster("cq_assassin_mist_stalker", "Espreitador da Nevoa", "slime", "mist_stalker", "humanoid", "bandit_shadow", "shadow"),
                    monster("cq_assassin_night_hunter", "Cacador Noturno", "mimic", "night_hunter", "humanoid", "bandit_shadow", "assassin"),
                    monster("cq_assassin_silent_mercenary", "Mercenario Silencioso", "skeleton_warrior", "mercenary", "humanoid", "bandit_shadow", "mercenary")
                ),
                bossMonsters = listOf(
                    monster("cq_assassin_twilight_reaper", "Ceifador do Crepusculo", "lich", "twilight_reaper", "humanoid", "bandit_shadow", "boss"),
                    monster("cq_assassin_blade_master", "Mestre das Laminas", "mimic", "blade_master", "humanoid", "bandit_shadow", "boss")
                ),
                finalBoss = monster("cq_assassin_final_shadow_king", "Rei da Nevoa", "lich", "shadow_king", "humanoid", "bandit_shadow", "boss_final"),
                collectibleTemplateId = "cq_collect_assassin",
                collectibleName = "Selo da Sombra"
            )
            "fighter" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "warrior",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SUBCLASS, path),
                normalMonsters = listOf(
                    monster("cq_fighter_war_brute", "Bruto de Guerra", "skeleton_warrior", "war_brute", "humanoid", "war_scraps", "war"),
                    monster("cq_fighter_berserker", "Berserker", "skeleton_archer", "berserker", "humanoid", "war_scraps", "rage"),
                    monster("cq_fighter_wild_gladiator", "Gladiador Selvagem", "mimic", "wild_gladiator", "humanoid", "war_scraps", "arena"),
                    monster("cq_fighter_shield_breaker", "Quebra-Escudos", "skeleton_warrior", "shield_breaker", "humanoid", "war_scraps", "crusher"),
                    monster("cq_fighter_armored_bull", "Touro Blindado", "slime", "armored_bull", "beast", "wolf_beast", "beast")
                ),
                bossMonsters = listOf(
                    monster("cq_fighter_arena_champion", "Campeao da Arena", "mimic", "arena_champion", "humanoid", "war_scraps", "boss"),
                    monster("cq_fighter_berserker_general", "General Berserker", "lich", "berserker_general", "humanoid", "war_scraps", "boss")
                ),
                finalBoss = monster("cq_fighter_final_war_tyrant", "Tirano de Guerra", "lich", "war_tyrant", "humanoid", "war_scraps", "boss_final"),
                collectibleTemplateId = "cq_collect_fighter",
                collectibleName = "Trofeu de Guerra"
            )
            "paladin" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SUBCLASS,
                classId = "warrior",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SUBCLASS, path),
                normalMonsters = listOf(
                    monster("cq_paladin_profaned_skeleton", "Esqueleto Profanado", "skeleton_warrior", "skeleton", "undead", "undead_relics", "undead"),
                    monster("cq_paladin_fallen_knight", "Cavaleiro Caido", "skeleton_warrior", "fallen_knight", "undead", "undead_relics", "corrupted"),
                    monster("cq_paladin_corrupted_acolyte", "Acolito Corrompido", "skeleton_archer", "corrupted_acolyte", "undead", "undead_relics", "corrupted"),
                    monster("cq_paladin_shadow_herald", "Arauto Sombrio", "slime", "shadow_herald", "undead", "undead_relics", "shadow"),
                    monster("cq_paladin_heretic_guardian", "Guardiao Herege", "mimic", "heretic_guardian", "undead", "undead_relics", "undead")
                ),
                bossMonsters = listOf(
                    monster("cq_paladin_dread_inquisitor", "Inquisidor Maldito", "lich", "dread_inquisitor", "undead", "undead_ancient", "boss"),
                    monster("cq_paladin_ruin_bearer", "Portador da Ruina", "mimic", "ruin_bearer", "undead", "undead_relics", "boss")
                ),
                finalBoss = monster("cq_paladin_final_fallen_seraph", "Arcanjo Caido", "lich", "fallen_seraph", "undead", "undead_ancient", "boss_final"),
                collectibleTemplateId = "cq_collect_paladin",
                collectibleName = "Sigilo Luminar"
            )
            "archmage" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "mage",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SPECIALIZATION, path),
                normalMonsters = listOf(
                    monster("cq_archmage_arcane_devourer", "Devorador Arcano", "skeleton_warrior", "arcane_devourer", "construct", "construct_arcane", "arcane"),
                    monster("cq_archmage_spectral_magistrate", "Magistrado Espectral", "lich", "spectral_magistrate", "construct", "construct_arcane", "spectral"),
                    monster("cq_archmage_celestial_watcher", "Observador Celeste", "skeleton_archer", "celestial_watcher", "construct", "construct_arcane", "celestial"),
                    monster("cq_archmage_living_mana_fragment", "Fragmento de Mana Viva", "slime", "living_mana_fragment", "construct", "construct_arcane", "mana"),
                    monster("cq_archmage_veil_lord", "Senhor do Veu", "mimic", "veil_lord", "construct", "construct_arcane", "void")
                ),
                bossMonsters = listOf(
                    monster("cq_archmage_void_architect", "Arquiteto do Vazio", "lich", "void_architect", "construct", "construct_arcane", "boss"),
                    monster("cq_archmage_star_regent", "Regente Estelar", "mimic", "star_regent", "construct", "construct_arcane", "boss")
                ),
                finalBoss = monster("cq_archmage_final_astral_noble", "Nobre Astral", "lich", "astral_noble", "construct", "construct_arcane", "boss_final"),
                collectibleTemplateId = "cq_collect_archmage",
                collectibleName = "Foco Arcano"
            )
            "elemental_master" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "mage",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SPECIALIZATION, path),
                normalMonsters = listOf(
                    monster("cq_elemental_hybrid_elemental", "Elemental Hibrido", "skeleton_warrior", "elemental_hybrid", "elemental", "elemental_fire", "elemental"),
                    monster("cq_elemental_storm_spirit", "Espirito da Tempestade", "skeleton_archer", "storm_spirit", "elemental", "elemental_fire", "storm"),
                    monster("cq_elemental_glacial_guardian", "Guardiao Glacial", "mimic", "glacial_guardian", "elemental", "elemental_fire", "ice"),
                    monster("cq_elemental_lava_heart", "Coracao de Lava", "slime", "lava_heart", "elemental", "elemental_fire", "lava"),
                    monster("cq_elemental_elemental_avatar", "Avatar Elemental", "lich", "elemental_avatar", "elemental", "elemental_fire", "elemental")
                ),
                bossMonsters = listOf(
                    monster("cq_elemental_prismatic_core", "Nucleo Prismatico", "mimic", "prismatic_core", "elemental", "elemental_fire", "boss"),
                    monster("cq_elemental_current_master", "Mestre das Correntes", "lich", "current_master", "elemental", "elemental_fire", "boss")
                ),
                finalBoss = monster("cq_elemental_final_prismatic_lord", "Soberano Prismatico", "lich", "prismatic_lord", "elemental", "elemental_fire", "boss_final"),
                collectibleTemplateId = "cq_collect_elemental_master",
                collectibleName = "Essencia Prismatica"
            )
            "sharpshooter" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "archer",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SPECIALIZATION, path),
                normalMonsters = listOf(
                    monster("cq_sharpshooter_armored_spotter", "Olheiro Blindado", "skeleton_archer", "armored_spotter", "humanoid", "war_scraps", "ranged"),
                    monster("cq_sharpshooter_veteran_archer", "Arqueiro Veterano", "skeleton_archer", "veteran_archer", "humanoid", "war_scraps", "ranged"),
                    monster("cq_sharpshooter_cliff_watcher", "Vigia do Penhasco", "skeleton_warrior", "cliff_watcher", "humanoid", "war_scraps", "sniper"),
                    monster("cq_sharpshooter_iron_sentinel", "Sentinela de Ferro", "mimic", "iron_sentinel", "construct", "war_scraps", "siege"),
                    monster("cq_sharpshooter_siege_gunner", "Atirador de Cerco", "lich", "siege_gunner", "humanoid", "war_scraps", "siege")
                ),
                bossMonsters = listOf(
                    monster("cq_sharpshooter_siege_commander", "Comandante de Cerco", "mimic", "siege_commander", "humanoid", "war_scraps", "boss"),
                    monster("cq_sharpshooter_absolute_sight", "Mira Absoluta", "lich", "absolute_sight", "humanoid", "war_scraps", "boss")
                ),
                finalBoss = monster("cq_sharpshooter_final_line_judge", "Juiz da Linha de Tiro", "lich", "line_judge", "humanoid", "war_scraps", "boss_final"),
                collectibleTemplateId = "cq_collect_sharpshooter",
                collectibleName = "Nucleo de Precisao"
            )
            "wanderer" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "archer",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SPECIALIZATION, path),
                normalMonsters = listOf(
                    monster("cq_wanderer_wastes_beast", "Besta do Ermo", "skeleton_warrior", "wastes_beast", "beast", "wolf_beast", "wilderness"),
                    monster("cq_wanderer_dune_runner", "Corredor das Dunas", "skeleton_archer", "dune_runner", "beast", "wolf_beast", "mobility"),
                    monster("cq_wanderer_roaming_predator", "Predador Errante", "slime", "roaming_predator", "beast", "wolf_beast", "predator"),
                    monster("cq_wanderer_wind_raptor", "Raptor do Vento", "mimic", "wind_raptor", "beast", "wolf_beast", "wind"),
                    monster("cq_wanderer_exiled_ranger", "Patrulheiro Exilado", "lich", "exiled_ranger", "humanoid", "bandit_shadow", "ranger")
                ),
                bossMonsters = listOf(
                    monster("cq_wanderer_route_master", "Mestre das Rotas", "mimic", "route_master", "humanoid", "wolf_beast", "boss"),
                    monster("cq_wanderer_nomad_storm", "Tempestade Nomade", "lich", "nomad_storm", "beast", "wolf_beast", "boss")
                ),
                finalBoss = monster("cq_wanderer_final_route_lord", "Senhor das Rotas", "lich", "route_lord", "humanoid", "wolf_beast", "boss_final"),
                collectibleTemplateId = "cq_collect_wanderer",
                collectibleName = "Marca do Errante"
            )
            "imperial_guard" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "warrior",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SPECIALIZATION, path),
                normalMonsters = listOf(
                    monster("cq_guard_fortress_champion", "Campeao da Fortaleza", "skeleton_warrior", "fortress_champion", "humanoid", "war_scraps", "tank"),
                    monster("cq_guard_steel_defender", "Defensor de Aco", "skeleton_warrior", "steel_defender", "humanoid", "war_scraps", "defender"),
                    monster("cq_guard_royal_sentinel", "Sentinela Real", "skeleton_archer", "royal_sentinel", "humanoid", "war_scraps", "royal"),
                    monster("cq_guard_war_colossus", "Colosso de Guerra", "mimic", "war_colossus", "construct", "war_scraps", "tank"),
                    monster("cq_guard_fallen_imperial_captain", "Capitao Imperial Caido", "lich", "imperial_captain", "undead", "war_scraps", "imperial")
                ),
                bossMonsters = listOf(
                    monster("cq_guard_imperial_executioner", "Executor Imperial", "mimic", "imperial_executioner", "humanoid", "war_scraps", "boss"),
                    monster("cq_guard_wall_general", "General da Muralha", "lich", "wall_general", "humanoid", "war_scraps", "boss")
                ),
                finalBoss = monster("cq_guard_final_armor_emperor", "Imperador da Couraca", "lich", "armor_emperor", "humanoid", "war_scraps", "boss_final"),
                collectibleTemplateId = "cq_collect_imperial_guard",
                collectibleName = "Emblema Imperial"
            )
            "barbarian" -> ClassQuestDungeonDefinition(
                unlockType = ClassQuestUnlockType.SPECIALIZATION,
                classId = "warrior",
                pathId = path,
                pathName = pathName(ClassQuestUnlockType.SPECIALIZATION, path),
                normalMonsters = listOf(
                    monster("cq_barbarian_tribal_butcher", "Carniceiro Tribal", "skeleton_warrior", "tribal_butcher", "humanoid", "war_scraps", "aggressive"),
                    monster("cq_barbarian_devastator", "Devastador", "skeleton_archer", "devastator", "humanoid", "war_scraps", "aggressive"),
                    monster("cq_barbarian_frenzied_warrior", "Guerreiro Frenetico", "mimic", "frenzied_warrior", "humanoid", "war_scraps", "frenzy"),
                    monster("cq_barbarian_war_mastiff", "Mastim de Guerra", "slime", "war_mastiff", "beast", "wolf_beast", "beast"),
                    monster("cq_barbarian_wild_behemoth", "Beemote Selvagem", "lich", "wild_behemoth", "beast", "wolf_beast", "beast")
                ),
                bossMonsters = listOf(
                    monster("cq_barbarian_slaughter_lord", "Senhor da Matanca", "mimic", "slaughter_lord", "humanoid", "war_scraps", "boss"),
                    monster("cq_barbarian_ancient_rage", "Furia Ancestral", "lich", "ancient_rage", "humanoid", "war_scraps", "boss")
                ),
                finalBoss = monster("cq_barbarian_final_frenzy_king", "Rei do Frenesi", "lich", "frenzy_king", "humanoid", "war_scraps", "boss_final"),
                collectibleTemplateId = "cq_collect_barbarian",
                collectibleName = "Reliquia de Frenesi"
            )
            else -> null
        } ?: return null

        if (definition.unlockType != unlockType) return null
        if (definition.classId != baseClass) return null
        return definition
    }

    private fun activeDefinition(player: PlayerState): ClassQuestDefinition? {
        val classDef = runCatching { classSystem.classDef(player.classId) }.getOrNull() ?: return null
        if (player.subclassId == null) {
            return definitionFor(classDef, ClassQuestUnlockType.SUBCLASS)
        }
        if (player.specializationId == null) {
            return definitionFor(classDef, ClassQuestUnlockType.SPECIALIZATION)
        }
        return null
    }

    private fun definitionFor(
        classDef: rpg.model.ClassDef,
        unlockType: ClassQuestUnlockType
    ): ClassQuestDefinition? {
        val paths = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> classDef.subclassIds
            ClassQuestUnlockType.SPECIALIZATION -> classDef.specializationIds
        }.map { it.lowercase() }.distinct()
        if (paths.size < 2) return null

        val pathA = paths[0]
        val pathB = paths[1]
        val unlockLevel = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> classDef.subclassUnlockLevel.coerceAtLeast(1)
            ClassQuestUnlockType.SPECIALIZATION -> classDef.specializationUnlockLevel.coerceAtLeast(1)
        }
        return ClassQuestDefinition(
            classId = classDef.id.lowercase(),
            className = classDef.name,
            unlockType = unlockType,
            unlockLevel = unlockLevel,
            pathA = pathA,
            pathAName = pathName(unlockType, pathA),
            pathB = pathB,
            pathBName = pathName(unlockType, pathB),
            stages = stageDefinitions(unlockType)
        )
    }

    private fun stageDefinitions(unlockType: ClassQuestUnlockType): List<ClassQuestStageDefinition> {
        val collectStage2 = if (unlockType == ClassQuestUnlockType.SUBCLASS) 15 else 20
        val collectStage4 = if (unlockType == ClassQuestUnlockType.SUBCLASS) 20 else 25
        val killStage4 = if (unlockType == ClassQuestUnlockType.SUBCLASS) 20 else 30
        return listOf(
            ClassQuestStageDefinition(
                stage = 1,
                killTarget = 25,
                reward = stageReward(unlockType, stage = 1, equipment = listOf(EquipSlot.HEAD))
            ),
            ClassQuestStageDefinition(
                stage = 2,
                collectTarget = collectStage2,
                reward = stageReward(unlockType, stage = 2, equipment = listOf(EquipSlot.CHEST, EquipSlot.LEGS))
            ),
            ClassQuestStageDefinition(
                stage = 3,
                bossKillTarget = 10,
                reward = stageReward(unlockType, stage = 3, equipment = listOf(EquipSlot.GLOVES, EquipSlot.BOOTS))
            ),
            ClassQuestStageDefinition(
                stage = 4,
                collectTarget = collectStage4,
                killTarget = killStage4,
                requiresFinalBoss = true,
                reward = stageReward(unlockType, stage = 4, equipment = listOf(EquipSlot.WEAPON_MAIN))
            )
        )
    }

    private fun stageReward(
        unlockType: ClassQuestUnlockType,
        stage: Int,
        equipment: List<EquipSlot>
    ): ClassQuestReward {
        val (xp, gold) = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> when (stage) {
                1 -> 700 to 130
                2 -> 1050 to 200
                3 -> 1600 to 300
                else -> 2400 to 420
            }
            ClassQuestUnlockType.SPECIALIZATION -> when (stage) {
                1 -> 1600 to 280
                2 -> 2300 to 420
                3 -> 3200 to 620
                else -> 4700 to 900
            }
        }
        return ClassQuestReward(
            xp = xp,
            gold = gold,
            hpPotionId = "hp_potion_medium",
            hpPotionQty = 1,
            mpPotionId = defaultMpPotionId,
            mpPotionQty = 1,
            equipmentSlots = equipment
        )
    }

    private fun shouldShowQuest(
        player: PlayerState,
        definition: ClassQuestDefinition
    ): Boolean {
        if (player.level < definition.unlockLevel) return false
        return when (definition.unlockType) {
            ClassQuestUnlockType.SUBCLASS -> player.subclassId == null
            ClassQuestUnlockType.SPECIALIZATION -> player.subclassId != null && player.specializationId == null
        }
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

    private fun unlockLabel(unlockType: ClassQuestUnlockType): String = when (unlockType) {
        ClassQuestUnlockType.SUBCLASS -> "Subclasse"
        ClassQuestUnlockType.SPECIALIZATION -> "Especializacao"
    }

    private fun slotDisplayName(slot: EquipSlot): String = when (slot) {
        EquipSlot.HEAD -> "Elmo"
        EquipSlot.CHEST -> "Peitoral"
        EquipSlot.LEGS -> "Calcas"
        EquipSlot.BOOTS -> "Botas"
        EquipSlot.GLOVES -> "Luvas"
        EquipSlot.WEAPON_MAIN -> "Arma"
        EquipSlot.WEAPON_OFF -> "Secundaria"
        EquipSlot.ALJAVA -> "Aljava"
        EquipSlot.CAPE -> "Capa"
        EquipSlot.BACKPACK -> "Mochila"
        EquipSlot.ACCESSORY -> "Acessorio"
    }

    private fun pathWeaponName(
        unlockType: ClassQuestUnlockType,
        pathId: String,
        classId: String
    ): String {
        val path = pathId.lowercase()
        return when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> when (path) {
                "pyromancer" -> "Catalisador Igneo"
                "arcanist" -> "Tomo Arcano"
                "hunter" -> "Arco de Caca"
                "assassin" -> "Lamina de Sombra"
                "fighter" -> "Lamina de Vanguarda"
                "paladin" -> "Martelo Sagrado"
                else -> when (classId.lowercase()) {
                    "mage" -> "Tomo Arcano"
                    "archer" -> "Arco de Caca"
                    else -> "Lamina de Vanguarda"
                }
            }
            ClassQuestUnlockType.SPECIALIZATION -> when (path) {
                "archmage" -> "Orbe Arcana"
                "elemental_master" -> "Nucleo Elemental"
                "sharpshooter" -> "Arcobalista de Elite"
                "wanderer" -> "Arco do Andarilho"
                "imperial_guard" -> "Lanca Imperial"
                "barbarian" -> "Machado de Frenesi"
                else -> when (classId.lowercase()) {
                    "mage" -> "Orbe Arcana"
                    "archer" -> "Arcobalista de Elite"
                    else -> "Lanca Imperial"
                }
            }
        }
    }

    private fun isMagicPath(pathId: String, classId: String): Boolean {
        val id = pathId.lowercase()
        if (classId.lowercase() == "mage") return true
        val magicTokens = listOf("mage", "arc", "pyro", "element", "cleric", "sorc")
        return magicTokens.any { token -> token in id }
    }

    private fun defaultLootProfileFor(baseType: String, family: String): String {
        val normalizedBase = baseType.trim().lowercase()
        val normalizedFamily = family.trim().lowercase()
        return when {
            containsAny(normalizedBase, "slime", "gel", "ooze", "viscous") -> "slime_core"
            containsAny(normalizedBase, "wolf", "panther", "bear", "hawk", "beast", "raptor", "mastiff", "behemoth", "predator") ||
                normalizedFamily in setOf("beast", "animal", "predator") -> "wolf_beast"
            containsAny(normalizedBase, "elemental", "salamander", "magma", "lava", "fire", "storm", "glacial", "prismatic") ||
                normalizedFamily == "elemental" -> "elemental_fire"
            containsAny(normalizedBase, "skeleton", "lich", "undead", "ghoul", "corrupt", "fallen", "heretic", "acolyte", "seraph") ||
                normalizedFamily in setOf("undead", "shadow") -> {
                if (containsAny(normalizedBase, "lich", "seraph")) "undead_ancient" else "undead_relics"
            }
            containsAny(normalizedBase, "bandit", "mercenary", "duelist", "assassin", "blade", "stalker", "reaper", "ranger") ||
                normalizedFamily == "shadow" -> "bandit_shadow"
            containsAny(normalizedBase, "construct", "sentinel", "orb", "arcane", "golem", "watcher", "magister", "core", "colossus") ||
                normalizedFamily in setOf("construct", "arcane") -> "construct_arcane"
            else -> "war_scraps"
        }
    }

    private fun defaultFamilyForBaseType(baseType: String): String {
        val normalized = baseType.trim().lowercase()
        return when {
            containsAny(normalized, "slime", "wolf", "panther", "bear", "hawk", "predator", "behemoth", "mastiff", "beast", "raptor") -> "beast"
            containsAny(normalized, "elemental", "salamander", "magma", "lava", "fire", "spirit", "avatar", "storm", "glacial") -> "elemental"
            containsAny(normalized, "skeleton", "knight", "acolyte", "herald", "heretic", "lich", "profaned", "corrupt", "fallen", "seraph") -> "undead"
            containsAny(normalized, "sentinel", "construct", "orb", "watcher", "magister", "devourer", "fragment", "core", "colossus") -> "construct"
            containsAny(normalized, "bandit", "mercenary", "duelist", "hunter", "gladiator", "brute", "berserker", "champion", "captain", "warrior", "ranger") -> "humanoid"
            else -> "humanoid"
        }
    }

    private fun containsAny(value: String, vararg tokens: String): Boolean {
        return tokens.any { token -> token in value }
    }

    private fun itemExists(itemId: String): Boolean {
        return itemRegistry.item(itemId) != null || itemRegistry.template(itemId) != null
    }

    private val defaultMpPotionId: String
        get() = when {
            itemExists("mp_potion_medium") -> "mp_potion_medium"
            itemExists("ether_small") -> "ether_small"
            itemExists("mp_potion_small") -> "mp_potion_small"
            else -> "hp_potion_medium"
        }
}
