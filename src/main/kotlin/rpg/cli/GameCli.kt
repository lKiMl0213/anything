
package rpg.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import rpg.achievement.AchievementMenu
import rpg.achievement.AchievementService
import rpg.achievement.AchievementTierUnlockedNotification
import rpg.achievement.AchievementTracker
import rpg.achievement.AchievementUpdate
import rpg.classquest.ClassQuestDungeonDefinition
import rpg.classquest.ClassQuestMenu
import rpg.classquest.ClassQuestStatus
import rpg.classquest.ClassQuestTagRules
import rpg.classquest.ClassQuestUnlockType
import rpg.creation.CharacterCreationPreviewService
import rpg.engine.ComputedStats
import rpg.engine.GameEngine
import rpg.engine.Progression
import rpg.inventory.InventorySystem
import rpg.io.DataRepository
import rpg.io.JsonStore
import rpg.item.ItemRarity
import rpg.events.EventContext
import rpg.events.DungeonEventService
import rpg.events.EventEngine
import rpg.events.EventExecutor
import rpg.events.EventSource
import rpg.events.NpcEventVariant
import rpg.model.Attributes
import rpg.model.ClassDef
import rpg.model.GameState
import rpg.model.DerivedStats
import rpg.model.ItemType
import rpg.model.EquipSlot
import rpg.model.PlayerState
import rpg.model.RaceDef
import rpg.model.ShopCurrency
import rpg.model.CraftDiscipline
import rpg.model.GatheringType
import rpg.model.QuestTier
import rpg.model.SkillType
import rpg.model.TalentNodeType
import rpg.model.WorldState
import rpg.monster.MonsterInstance
import rpg.quest.QuestInstance
import rpg.quest.QuestStatus
import rpg.status.StatusSystem
import rpg.talent.TalentTreeService
import rpg.world.RunRoomType

class GameCli(private val repo: DataRepository) {
    private class InputClosedException : RuntimeException()

    private data class TalentMenuSlot(
        val stage: Int,
        val label: String,
        val treeId: String?,
        val spentPoints: Int
    )

    private val engine = GameEngine(repo)
    private val dungeonEventService = DungeonEventService(repo.dungeonEvents)
    private val talentTreeService = TalentTreeService(repo.balance.talentPoints)
    private val classQuestMenu = ClassQuestMenu(engine.classQuestService)
    private val achievementService = AchievementService()
    private val achievementTracker = AchievementTracker(achievementService)
    private val achievementMenu = AchievementMenu(achievementService)
    private val characterCreationPreview = CharacterCreationPreviewService(repo)

    private val characterDef = repo.character
    private val accessorySlots = characterDef.accessorySlots
    private val offhandBlockedId = "__offhand_blocked__"

    // Rest room healing
    private val restHealPct = 0.20
    private val restRegenMultiplier = 3.0


    // Death penalty + debuff
    private val deathBaseLootLossPct = 80.0
    private val deathMinLootLossPct = 20.0
    private val deathDebuffPerStack = 0.20
    private val deathDebuffBaseMinutes = 10.0
    private val deathDebuffExtraMinutes = 5.0
    private val deathGoldLossPct = 0.20
    private val deathXpPenaltyPct = 20.0

    // In-combat/out-of-combat pacing (minutes)
    private val roomTimeMinutes = 0.5
    private val clockSyncEpsilonMs = 1000L

    private val tavernRestHealPct = 0.25
    private val questZoneId: ZoneId = ZoneId.systemDefault()

    private val ansiCombatReset = "\u001B[0m"
    private val ansiCombatHeader = "\u001B[37m"
    private val ansiCombatPlayer = "\u001B[36m"
    private val ansiCombatEnemy = "\u001B[31m"
    private val ansiCombatLoading = "\u001B[33m"
    private val ansiCombatReady = "\u001B[32m"
    private val ansiCombatBlocked = "\u001B[31m"
    private val ansiCombatCasting = "\u001B[36m"
    private val ansiCombatPause = "\u001B[35m"
    private val ansiClearLine = "\u001B[2K"
    private val ansiClearToEnd = "\u001B[J"
    private val ansiUiName = "\u001B[37m"
    private val ansiUiLevel = "\u001B[36m"
    private val ansiUiHp = "\u001B[32m"
    private val ansiUiMp = "\u001B[34m"
    private val ansiUiGold = "\u001B[33m"
    private val ansiUiCash = "\u001B[35m"
    private val ansiQuestActive = "\u001B[34m"
    private val ansiQuestReady = "\u001B[32m"
    private val ansiQuestAlert = "\u001B[33m"
    private val attributeMeta = listOf(
        AttrMeta("STR", "Forca"),
        AttrMeta("AGI", "Agilidade"),
        AttrMeta("DEX", "Destreza"),
        AttrMeta("VIT", "Vitalidade"),
        AttrMeta("INT", "Inteligencia"),
        AttrMeta("SPR", "Espirito"),
        AttrMeta("LUK", "Sorte")
    )

    fun run() {
        println("=== RPG TXT ===")
        try {
            while (true) {
                println("\n1. Novo jogo")
                println("2. Carregar jogo")
                println("x. Sair")
                when (readMenuChoice("Escolha: ", 1, 2)) {
                    1 -> newGame()
                    2 -> loadGame()
                    null -> return
                }
            }
        } catch (_: InputClosedException) {
            println("\nEntrada encerrada. Encerrando jogo.")
        }
    }

    private fun newGame() {
        val name = readNonEmpty("Nome: ")
        val raceDef = chooseRaceForCreation() ?: return
        val classDef = chooseClassForCreation() ?: return

        showCharacterSummary(name, raceDef, classDef)
        val initialAttributeAllocation = allocateAttributesWithBonuses(characterDef.baseAttributePoints, raceDef, classDef)
        if (initialAttributeAllocation.unspentPoints > 0) {
            println(
                "Voce iniciou com ${initialAttributeAllocation.unspentPoints} ponto(s) de atributo nao distribuidos."
            )
            println("Use Menu > Personagem > Atributos para distribuir quando quiser.")
        }

        val starterEquipment = characterDef.starterEquipmentByClass[classDef.id]?.toMutableMap() ?: mutableMapOf()
        applyTwoHandedLoadout(starterEquipment)
        val inventory = characterDef.starterInventory.toMutableList()
        inventory += characterDef.starterInventoryByClass[classDef.id].orEmpty()

        var player = PlayerState(
            name = name,
            classId = classDef.id,
            raceId = raceDef.id,
            baseAttributes = initialAttributeAllocation.baseAttributes,
            unspentAttrPoints = initialAttributeAllocation.unspentPoints,
            inventory = inventory,
            equipped = starterEquipment,
            gold = characterDef.starterGold
        )
        player = InventorySystem.normalizeAmmoStorage(player, emptyMap(), engine.itemRegistry)
        player = engine.skillSystem.ensureProgress(player)
        player = achievementTracker.synchronize(player)

        val initialStats = computePlayerStats(player, emptyMap())
        player = player.copy(currentHp = initialStats.derived.hpMax, currentMp = initialStats.derived.mpMax)

        val mapDef = repo.maps.values.firstOrNull()
        val world = if (mapDef != null) {
            WorldState(mapId = mapDef.id, currentRoomId = mapDef.startRoomId)
        } else {
            WorldState(mapId = "default", currentRoomId = "")
        }

        val state = GameState(
            player = player,
            world = world,
            lastClockSyncEpochMs = System.currentTimeMillis()
        )
        hubLoop(state)
    }

    private fun loadGame() {
        val saveDir = Paths.get("saves")
        if (!Files.exists(saveDir)) {
            println("Nenhum save encontrado.")
            return
        }

        val saves = Files.list(saveDir).use { stream ->
            val results = mutableListOf<Path>()
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().lowercase().endsWith(".json") }
                .forEach { results.add(it) }
            results
        }

        if (saves.isEmpty()) {
            println("Nenhum save encontrado.")
            return
        }

        val chosen = choose("Save", saves) { it.fileName.toString() }
        val rawState = JsonStore.load<GameState>(chosen)
        val state = synchronizeClock(normalizeLoadedState(rawState))
        println("Save carregado: ${chosen.fileName}")
        hubLoop(state)
    }

    private fun hubLoop(initialState: GameState) {
        var state = synchronizeClock(normalizeLoadedState(initialState))
        while (true) {
            state = synchronizeClock(state)
            state = normalizeLoadedState(state)
            state = state.copy(player = engine.classQuestService.synchronize(state.player))
            state = state.copy(player = achievementTracker.synchronize(state.player))
            println("\n=== Menu Principal ===")
            state = checkSubclassUnlock(state)
            state = checkSpecializationUnlock(state)
            state = state.copy(player = achievementTracker.synchronize(state.player))
            state = state.copy(questBoard = synchronizeQuestBoard(state.questBoard, state.player, state.itemInstances))
            showClock(state)
            showStatus(state.player, state.itemInstances)
            showDebuff(state.player)

            val characterAlert = menuAlert(
                hasUnspentAttributePoints(state.player) || hasTalentPointsAvailable(state.player)
            )
            val progressionAlert = menuAlert(
                hasReadyToClaim(state.questBoard) || hasAchievementRewardReady(state.player)
            )

            println("\n1. Explorar")
            println(labelWithAlert("2. Personagem", characterAlert))
            println("3. Producao")
            println(labelWithAlert("4. Progressao", progressionAlert))
            println("5. Cidade")
            println("6. Salvar")
            println("x. Sair para o menu")

            when (readMenuChoice("Escolha: ", 1, 6)) {
                1 -> state = openExploreMenu(state)
                2 -> state = openCharacterMenu(state)
                3 -> state = openProductionMenu(state)
                4 -> state = openProgressionMenu(state)
                5 -> state = openCityMenu(state)
                6 -> saveGame(state)
                null -> return
            }
        }
    }

    private fun normalizeLoadedState(state: GameState): GameState {
        val ammoNormalizedPlayer = InventorySystem.normalizeAmmoStorage(
            state.player,
            state.itemInstances,
            engine.itemRegistry
        )
        val migratedPlayer = achievementTracker.synchronize(
            engine.classSystem.sanitizePlayerHierarchy(
                engine.skillSystem.ensureProgress(ammoNormalizedPlayer)
            )
        )
        return if (migratedPlayer == state.player) {
            if (ammoNormalizedPlayer == state.player) state else state.copy(player = ammoNormalizedPlayer)
        } else {
            state.copy(player = migratedPlayer)
        }
    }

    private fun openExploreMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            updated = synchronizeClock(updated)
            updated = updated.copy(player = engine.classQuestService.synchronize(updated.player))
            updated = updated.copy(player = achievementTracker.synchronize(updated.player))

            println("\n=== Explorar ===")
            println("1. Dungeons")
            println("2. Mapa aberto (futuro)")

            var option = 3
            val classMapOption = if (hasClassMapUnlocked(updated.player)) option++ else null
            if (classMapOption != null) {
                println("$classMapOption. Mapa de classe")
            }
            println("x. Voltar")

            val choice = readMenuChoice("Escolha: ", 1, option - 1) ?: return updated
            when {
                choice == 1 -> updated = enterDungeon(updated)
                choice == 2 -> println("Mapa aberto ainda nao esta disponivel.")
                classMapOption != null && choice == classMapOption -> updated = enterDungeon(updated, forceClassDungeon = true)
            }
        }
    }

    private fun openCharacterMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            val attributeAlert = menuAlert(hasUnspentAttributePoints(updated.player))
            val talentAlert = menuAlert(hasTalentPointsAvailable(updated.player))
            println("\n=== Personagem ===")
            println("1. Equipados")
            println("2. Inventario")
            println(labelWithAlert("3. Atributos", attributeAlert))
            println(labelWithAlert("4. Talentos", talentAlert))
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 4)) {
                1 -> updated = openEquipped(updated)
                2 -> updated = openInventory(updated)
                3 -> updated = updated.copy(
                    player = allocateUnspentPoints(updated.player, updated.itemInstances)
                )
                4 -> updated = openTalents(updated)
                null -> return updated
            }
        }
    }

    private fun openProductionMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            println("\n=== Producao ===")
            println("1. Craft")
            println("2. Coleta de ervas")
            println("3. Mineracao")
            println("4. Cortar madeira")
            println("5. Pesca")
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 5)) {
                1 -> updated = openCrafting(updated)
                2 -> updated = openGathering(updated, forcedType = GatheringType.HERBALISM)
                3 -> updated = openGathering(updated, forcedType = GatheringType.MINING)
                4 -> updated = openGathering(updated, forcedType = GatheringType.WOODCUTTING)
                5 -> updated = openGathering(updated, forcedType = GatheringType.FISHING)
                null -> return updated
            }
        }
    }

    private fun openProgressionMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            updated = synchronizeClock(updated)
            updated = updated.copy(player = engine.classQuestService.synchronize(updated.player))
            updated = updated.copy(player = achievementTracker.synchronize(updated.player))
            updated = updated.copy(
                questBoard = synchronizeQuestBoard(updated.questBoard, updated.player, updated.itemInstances)
            )

            val questsAlert = if (hasReadyToClaim(updated.questBoard)) uiColor("(!)", ansiQuestAlert) else ""
            val achievementsAlert = if (hasAchievementRewardReady(updated.player)) uiColor("(!)", ansiQuestAlert) else ""

            println("\n=== Progressao ===")
            println(labelWithAlert("1. Quests", questsAlert))
            println(labelWithAlert("2. Conquistas", achievementsAlert))
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> updated = openQuestBoard(updated)
                2 -> updated = openAchievementMenu(updated)
                null -> return updated
            }
        }
    }

    private fun openCityMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            println("\n=== Cidade ===")
            println("1. Taverna")
            println("2. Loja de Ouro")
            println("3. Loja de Cash")
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 3)) {
                1 -> updated = visitTavern(updated)
                2 -> updated = openShop(updated)
                3 -> updated = openCashShop(updated)
                null -> return updated
            }
        }
    }

    private fun openEquipped(state: GameState): GameState {
        var player = state.player
        val itemInstances = state.itemInstances
        val slotOrder = listOf(
            EquipSlot.WEAPON_MAIN.name,
            EquipSlot.WEAPON_OFF.name,
            EquipSlot.ALJAVA.name,
            EquipSlot.HEAD.name,
            EquipSlot.CHEST.name,
            EquipSlot.LEGS.name,
            EquipSlot.GLOVES.name,
            EquipSlot.BOOTS.name
        ) + accessorySlots

        while (true) {
            println("\n=== Equipados ===")
            slotOrder.forEachIndexed { index, slot ->
                val equippedId = player.equipped[slot]
                val label = when {
                    equippedId == null -> "-"
                    equippedId == offhandBlockedId -> "Bloqueado por arma de duas maos"
                    else -> engine.itemResolver.resolve(equippedId, itemInstances)?.let(::itemDisplayLabel) ?: equippedId
                }
                println("${index + 1}. ${equippedSlotLabel(slot)} -> $label")
            }
            val stats = computePlayerStats(player, itemInstances)
            println(
                "Resumo: DMG ${format(stats.derived.damagePhysical)} | " +
                    "DEF ${format(stats.derived.defPhysical)} | " +
                    "HP ${format(stats.derived.hpMax)} | " +
                    "MP ${format(stats.derived.mpMax)}"
            )
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, slotOrder.size)
            if (choice == null) {
                val updated = state.copy(player = player, itemInstances = itemInstances)
                autoSave(updated)
                return updated
            }

            val slotKey = slotOrder[choice - 1]
            val equippedId = player.equipped[slotKey]
            if (equippedId == null) {
                println("Slot vazio.")
                continue
            }
            if (equippedId == offhandBlockedId) {
                println("Slot bloqueado por arma de duas maos.")
                continue
            }
            val item = engine.itemResolver.resolve(equippedId, itemInstances)
            if (item == null) {
                println("Item equipado nao encontrado.")
                continue
            }

            showEquippedItemDetails(player, itemInstances, slotKey, item)
            println("1. Desequipar")
            println("x. Voltar")
            if (readMenuChoice("Escolha: ", 1, 1) == 1) {
                val updated = unequipSlot(player, itemInstances, slotKey, item)
                if (updated != player) {
                    player = updated
                    autoSave(state.copy(player = player, itemInstances = itemInstances))
                }
            }
        }
    }

    private fun showEquippedItemDetails(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        slotKey: String,
        item: rpg.item.ResolvedItem
    ) {
        println("\n=== ${equippedSlotLabel(slotKey)} ===")
        println("Item: ${itemDisplayLabel(item)}")
        if (item.description.isNotBlank()) {
            println("Descricao: ${item.description}")
        }
        val slotLabel = item.slot?.name ?: slotKey
        val handLabel = if (item.twoHanded) " (duas maos)" else ""
        println("Slot: $slotLabel$handLabel")
        val bonusLabel = formatItemBonuses(item)
        if (bonusLabel.isNotBlank()) {
            println("Bonus: $bonusLabel")
        }
        val removalPreview = buildUnequippedPreview(player, slotKey)
        val before = computePlayerStats(player, itemInstances)
        val after = computePlayerStats(removalPreview, itemInstances)
        println(
            "Ao desequipar: DMG ${formatSignedDouble(after.derived.damagePhysical - before.derived.damagePhysical)} | " +
                "DEF ${formatSignedDouble(after.derived.defPhysical - before.derived.defPhysical)} | " +
                "HP ${formatSignedDouble(after.derived.hpMax - before.derived.hpMax)} | " +
                "SPD ${formatSignedDouble(after.derived.attackSpeed - before.derived.attackSpeed)}"
        )
    }

    private fun buildUnequippedPreview(player: PlayerState, slotKey: String): PlayerState {
        val equipped = player.equipped.toMutableMap()
        equipped.remove(slotKey)
        if (slotKey == EquipSlot.WEAPON_MAIN.name && equipped[EquipSlot.WEAPON_OFF.name] == offhandBlockedId) {
            equipped.remove(EquipSlot.WEAPON_OFF.name)
        }
        return player.copy(equipped = equipped)
    }

    private fun normalizePlayerStorage(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        return InventorySystem.normalizeAmmoStorage(player, itemInstances, engine.itemRegistry)
    }

    private fun unequipSlot(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        slotKey: String,
        item: rpg.item.ResolvedItem
    ): PlayerState {
        val unequipped = buildUnequippedPreview(player, slotKey)
        val insertion = InventorySystem.addItemsWithLimit(
            player = unequipped,
            itemInstances = itemInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = listOf(item.id)
        )
        if (insertion.rejected.isNotEmpty()) {
            println("Inventario sem espaco para desequipar ${item.name}.")
            return player
        }
        println("Desequipou ${item.name}.")
        return clampPlayerResources(
            normalizePlayerStorage(
                unequipped.copy(
                    inventory = insertion.inventory,
                    quiverInventory = insertion.quiverInventory,
                    selectedAmmoTemplateId = insertion.selectedAmmoTemplateId
                ),
                itemInstances
            ),
            itemInstances
        )
    }

    private fun hasClassMapUnlocked(player: PlayerState): Boolean {
        return engine.classQuestService.activeDungeon(player) != null
    }

    private fun equippedSlotLabel(slotKey: String): String = when (slotKey) {
        EquipSlot.WEAPON_MAIN.name -> "Arma primaria"
        EquipSlot.WEAPON_OFF.name -> "Arma secundaria"
        EquipSlot.ALJAVA.name -> "Aljava"
        EquipSlot.HEAD.name -> "Cabeca"
        EquipSlot.CHEST.name -> "Peito"
        EquipSlot.LEGS.name -> "Pernas"
        EquipSlot.GLOVES.name -> "Luvas"
        EquipSlot.BOOTS.name -> "Botas"
        else -> if (slotKey.uppercase().startsWith("ACCESSORY")) {
            slotKey.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        } else {
            slotKey
        }
    }

    private fun checkSubclassUnlock(state: GameState): GameState {
        val player = state.player
        if (player.subclassId != null) return state
        val classDef = repo.classes[player.classId] ?: return state
        if (classDef.secondClassIds.isEmpty()) return state
        val unlockLevel = classDef.secondClassUnlockLevel.coerceAtLeast(1)
        if (player.level < unlockLevel) return state

        val chosenPath = engine.classQuestService.completedPathFor(player, ClassQuestUnlockType.SUBCLASS)
            ?.lowercase()
            ?: return state
        if (chosenPath !in classDef.secondClassIds.map { it.lowercase() }.toSet()) return state
        val chosen = repo.subclasses[chosenPath] ?: return state
        if (chosen.parentClassId.lowercase() != classDef.id.lowercase()) return state

        val updatedProgress = player.subclassUnlockProgressByClass + (
            classDef.id to rpg.model.SubclassUnlockProgress(
                stage = rpg.model.SubclassUnlockStage.CHOSEN,
                questTemplateId = classDef.secondClassUnlockQuestTemplateId.orEmpty(),
                preparedAtLevel = player.level,
                preparedAtEpochMs = System.currentTimeMillis()
            )
        )
        var updated = player.copy(
            subclassId = chosen.id,
            subclassUnlockProgressByClass = updatedProgress
        )
        println("2a classe liberada via quest de classe: ${chosen.name}.")
        updated = applyFullTalentReset(updated)
        println("Reset completo de talentos aplicado. Pontos de talento devolvidos.")
        updated = applyAchievementUpdate(achievementTracker.onSubclassUnlocked(updated))
        updated = applyAchievementUpdate(achievementTracker.onClassResetTriggered(updated))
        return state.copy(player = updated)
    }

    private fun checkSpecializationUnlock(state: GameState): GameState {
        val player = state.player
        if (player.subclassId == null) return state
        if (player.specializationId != null) return state
        val classDef = repo.classes[player.classId] ?: return state

        val unlockLevel = classDef.specializationUnlockLevel.coerceAtLeast(1)
        if (player.level < unlockLevel) return state

        val chosenPath = engine.classQuestService.completedPathFor(player, ClassQuestUnlockType.SPECIALIZATION)
            ?.lowercase()
            ?: return state
        val options = engine.classSystem.specializationOptions(classDef, player.subclassId)
        if (options.isEmpty()) return state
        val chosen = options.firstOrNull { it.id.lowercase() == chosenPath } ?: return state

        val updatedProgress = player.specializationUnlockProgressByClass + (
            classDef.id to rpg.model.SpecializationUnlockProgress(
                stage = rpg.model.SpecializationUnlockStage.CHOSEN,
                questTemplateId = classDef.specializationUnlockQuestTemplateId.orEmpty(),
                preparedAtLevel = player.level,
                preparedAtEpochMs = System.currentTimeMillis()
            )
        )
        var updated = player.copy(
            specializationId = chosen.id,
            specializationUnlockProgressByClass = updatedProgress
        )
        println("Especializacao liberada via quest de classe: ${chosen.name}.")
        updated = applyAchievementUpdate(achievementTracker.onSpecializationUnlocked(updated))
        updated = applyFullTalentReset(updated)
        println("Reset completo de talentos aplicado. Pontos de talento devolvidos.")
        updated = applyAchievementUpdate(achievementTracker.onClassResetTriggered(updated))
        return state.copy(player = updated)
    }

    private fun applyFullTalentReset(player: PlayerState): PlayerState {
        val resetV2 = talentTreeService.buildResetState(player, repo.talentTreesV2.values)
        return resetV2.copy(
            talentNodeRanks = emptyMap(),
            unlockedTalentTrees = emptyList(),
            unspentSkillPoints = resetV2.unspentSkillPoints.coerceAtLeast(0)
        )
    }

    private fun applySafeClassReset(player: PlayerState): PlayerState {
        val talentReset = applyFullTalentReset(player)
        val currentClass = player.classId.lowercase()
        val cleanedClassQuestProgress = talentReset.classQuestProgressByKey
            .filterValues { progress -> progress.classId.lowercase() != currentClass }
        return talentReset.copy(
            subclassId = null,
            specializationId = null,
            subclassUnlockProgressByClass = talentReset.subclassUnlockProgressByClass
                .filterKeys { it.lowercase() != currentClass },
            specializationUnlockProgressByClass = talentReset.specializationUnlockProgressByClass
                .filterKeys { it.lowercase() != currentClass },
            classQuestProgressByKey = cleanedClassQuestProgress
        )
    }

    private fun openTalents(state: GameState): GameState {
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
                rpg.model.TalentPointMode.SHARED_POOL -> ledger.sharedEarned
                rpg.model.TalentPointMode.PER_TREE -> ledger.earnedByTree.values.sum()
            }
            val totalAvailable = when (ledger.mode) {
                rpg.model.TalentPointMode.SHARED_POOL -> ledger.sharedAvailable
                rpg.model.TalentPointMode.PER_TREE -> ledger.availableByTree.values.sum()
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

    private fun openTalentTreeDetails(
        initialPlayer: PlayerState,
        selectedSlot: TalentMenuSlot
    ): PlayerState {
        var player = initialPlayer
        while (true) {
            val trees = talentTreeService.activeTrees(player, repo.talentTreesV2.values)
            val tree = trees.firstOrNull { it.id == selectedSlot.treeId }
            if (tree == null) {
                println("Essa arvore nao esta disponivel no momento.")
                return player
            }
            val ledger = talentTreeService.pointsLedger(player, trees)
            val pointsLabel = if (ledger.mode == rpg.model.TalentPointMode.PER_TREE) {
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
                println("   Efeito: ${talentNodeEffectSummary(node)}")
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
        trees: List<rpg.model.TalentTree>,
        ledger: rpg.talent.TalentPointLedger
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

    private fun talentTreeDisplayName(player: PlayerState, tree: rpg.model.TalentTree): String {
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

    private fun talentNodeStateLabel(
        currentRank: Int,
        maxRank: Int,
        check: rpg.talent.TalentRankCheck
    ): String {
        return when {
            currentRank >= maxRank -> "MAX"
            check.allowed -> "Disponivel"
            check.reason == "Pontos de talento insuficientes." -> "Sem pontos"
            else -> "Bloqueada"
        }
    }

    private fun formatTalentPrerequisites(
        tree: rpg.model.TalentTree,
        node: rpg.model.TalentNode
    ): String {
        if (node.prerequisites.isEmpty()) return "-"
        val byId = tree.nodes.associateBy { it.id }
        return node.prerequisites.joinToString(", ") { req ->
            val name = byId[req.nodeId]?.name ?: req.nodeId
            "$name NV${req.minRank}"
        }
    }

    private fun formatTalentExclusiveGroup(
        tree: rpg.model.TalentTree,
        node: rpg.model.TalentNode
    ): String {
        val group = node.exclusiveGroup?.takeIf { it.isNotBlank() } ?: return "-"
        val peers = tree.nodes
            .filter { it.id != node.id && it.exclusiveGroup == group }
            .map { it.name }
        return if (peers.isEmpty()) "Grupo exclusivo" else peers.joinToString(", ")
    }

    private fun talentNodeEffectSummary(node: rpg.model.TalentNode): String {
        val pieces = mutableListOf<String>()
        val maxRank = node.maxRank.coerceAtLeast(1)
        val bonuses = node.modifiers.bonuses
        val add = bonuses.derivedAdd
        val mult = bonuses.derivedMult
        val attrs = bonuses.attributes

        addTalentEffectSeries(pieces, "Forca", maxRank, attrs.str.toDouble())
        addTalentEffectSeries(pieces, "Agilidade", maxRank, attrs.agi.toDouble())
        addTalentEffectSeries(pieces, "Destreza", maxRank, attrs.dex.toDouble())
        addTalentEffectSeries(pieces, "Vitalidade", maxRank, attrs.vit.toDouble())
        addTalentEffectSeries(pieces, "Inteligencia", maxRank, attrs.`int`.toDouble())
        addTalentEffectSeries(pieces, "Espirito", maxRank, attrs.spr.toDouble())
        addTalentEffectSeries(pieces, "Sorte", maxRank, attrs.luk.toDouble())

        addTalentEffectSeries(pieces, "Dano fisico", maxRank, add.damagePhysical)
        addTalentEffectSeries(pieces, "Dano magico", maxRank, add.damageMagic)
        addTalentEffectSeries(pieces, "HP maximo", maxRank, add.hpMax)
        addTalentEffectSeries(pieces, "MP maximo", maxRank, add.mpMax)
        addTalentEffectSeries(pieces, "Defesa fisica", maxRank, add.defPhysical)
        addTalentEffectSeries(pieces, "Defesa magica", maxRank, add.defMagic)
        addTalentEffectSeries(pieces, "Velocidade de ataque", maxRank, add.attackSpeed)
        addTalentEffectSeries(pieces, "Velocidade de movimento", maxRank, add.moveSpeed)
        addTalentEffectSeries(pieces, "Chance critica", maxRank, add.critChancePct, suffix = "%")
        addTalentEffectSeries(pieces, "Dano critico", maxRank, add.critDamagePct, suffix = "%")
        addTalentEffectSeries(pieces, "Vampirismo", maxRank, add.vampirismPct, suffix = "%")
        addTalentEffectSeries(pieces, "Recarga", maxRank, add.cdrPct, suffix = "%")
        addTalentEffectSeries(pieces, "Penetracao fisica", maxRank, add.penPhysical)
        addTalentEffectSeries(pieces, "Penetracao magica", maxRank, add.penMagic)
        addTalentEffectSeries(pieces, "Regeneracao de HP", maxRank, add.hpRegen)
        addTalentEffectSeries(pieces, "Regeneracao de MP", maxRank, add.mpRegen)
        addTalentEffectSeries(pieces, "Precisao", maxRank, add.accuracy)
        addTalentEffectSeries(pieces, "Esquiva", maxRank, add.evasion)
        addTalentEffectSeries(pieces, "Tenacidade", maxRank, add.tenacityPct, suffix = "%")
        addTalentEffectSeries(pieces, "Reducao de dano", maxRank, add.damageReductionPct, suffix = "%")

        addTalentEffectSeries(pieces, "Dano fisico", maxRank, mult.damagePhysical, suffix = "%")
        addTalentEffectSeries(pieces, "Dano magico", maxRank, mult.damageMagic, suffix = "%")
        addTalentEffectSeries(pieces, "HP maximo", maxRank, mult.hpMax, suffix = "%")
        addTalentEffectSeries(pieces, "MP maximo", maxRank, mult.mpMax, suffix = "%")
        addTalentEffectSeries(pieces, "Defesa fisica", maxRank, mult.defPhysical, suffix = "%")
        addTalentEffectSeries(pieces, "Defesa magica", maxRank, mult.defMagic, suffix = "%")
        addTalentEffectSeries(pieces, "Velocidade de ataque", maxRank, mult.attackSpeed, suffix = "%")
        addTalentEffectSeries(pieces, "Velocidade de movimento", maxRank, mult.moveSpeed, suffix = "%")
        addTalentEffectSeries(pieces, "Chance critica", maxRank, mult.critChancePct, suffix = "%")
        addTalentEffectSeries(pieces, "Dano critico", maxRank, mult.critDamagePct, suffix = "%")
        addTalentEffectSeries(pieces, "Vampirismo", maxRank, mult.vampirismPct, suffix = "%")
        addTalentEffectSeries(pieces, "Recarga", maxRank, mult.cdrPct, suffix = "%")
        addTalentEffectSeries(pieces, "Regeneracao de HP", maxRank, mult.hpRegen, suffix = "%")
        addTalentEffectSeries(pieces, "Regeneracao de MP", maxRank, mult.mpRegen, suffix = "%")
        addTalentEffectSeries(pieces, "Precisao", maxRank, mult.accuracy, suffix = "%")
        addTalentEffectSeries(pieces, "Esquiva", maxRank, mult.evasion, suffix = "%")
        addTalentEffectSeries(pieces, "Tenacidade", maxRank, mult.tenacityPct, suffix = "%")
        addTalentEffectSeries(pieces, "Reducao de dano", maxRank, mult.damageReductionPct, suffix = "%")

        node.modifiers.atb.forEach { (rawKey, value) ->
            when (rawKey.trim().lowercase()) {
                "fillratepct" -> addTalentEffectSeries(pieces, "Velocidade da barra", maxRank, value, suffix = "%")
                "casttimepct" -> addTalentEffectSeries(pieces, "Tempo de conjuracao", maxRank, value, suffix = "%")
                "gcdpct" -> addTalentEffectSeries(pieces, "Recarga global", maxRank, value, suffix = "%")
                "cooldownpct" -> addTalentEffectSeries(pieces, "Cooldown", maxRank, value, suffix = "%")
                "interruptchancepct" -> addTalentEffectSeries(pieces, "Chance de interrupcao", maxRank, value, suffix = "%")
                "interruptresistpct" -> addTalentEffectSeries(pieces, "Resistencia a interrupcao", maxRank, value, suffix = "%")
                "bargainonhitpct" -> addTalentEffectSeries(pieces, "Barra ganha ao acertar", maxRank, value, suffix = "%")
                "bargainoncritpct" -> addTalentEffectSeries(pieces, "Barra ganha ao critar", maxRank, value, suffix = "%")
                "bargainondamagedpct" -> addTalentEffectSeries(pieces, "Barra ganha ao sofrer dano", maxRank, value, suffix = "%")
                "barlossonhitpct" -> addTalentEffectSeries(pieces, "Barra removida do alvo", maxRank, value, suffix = "%")
                "manaonhit" -> addTalentEffectSeries(pieces, "Mana por acerto", maxRank, value)
                "manaonhitpctmax" -> addTalentEffectSeries(pieces, "Mana por acerto", maxRank, value, suffix = "% do MP max")
                "nomanacostchancepct" -> addTalentEffectSeries(pieces, "Chance de nao gastar mana", maxRank, value, suffix = "%", includePlus = false)
                "cooldownreduceonkillseconds" -> addTalentEffectSeries(pieces, "Reducao de cooldown ao abater", maxRank, value, suffix = "s")
                "bargainonstatusapplypct" -> addTalentEffectSeries(pieces, "Barra ganha ao aplicar status", maxRank, value, suffix = "%")
                "tempdamagebuffonstatusapplypct" -> addTalentEffectSeries(pieces, "Buff temporario de dano ao aplicar status", maxRank, value, suffix = "%")
                "tempfillratebuffonstatusapplypct" -> addTalentEffectSeries(pieces, "Buff temporario de barra ao aplicar status", maxRank, value, suffix = "%")
                "tempbuffdurationseconds" -> addTalentEffectSeries(pieces, "Duracao do buff temporario", maxRank, value, suffix = "s", includePlus = false)
                "hastepct" -> addTalentEffectSeries(pieces, "Aceleracao", maxRank, value, suffix = "%")
                "slowresistpct" -> addTalentEffectSeries(pieces, "Resistencia a lentidao", maxRank, value, suffix = "%")
                "slowamplifypct" -> addTalentEffectSeries(pieces, "Potencia de lentidao aplicada", maxRank, value, suffix = "%")
            }
        }

        val lowHpBonus = node.modifiers.status["bonusDamageVsLowHpPct"]
        val lowHpThreshold = node.modifiers.status["lowHpThresholdPct"]
        if (lowHpBonus != null && lowHpBonus != 0.0) {
            val bonusValues = talentEffectSequence(maxRank) { rank -> lowHpBonus * rank }
            val thresholdText = lowHpThreshold?.let { " abaixo de ${formatTalentCompact(it)}% de HP" }.orEmpty()
            pieces += "Dano contra alvo$thresholdText ${formatTalentSequence(bonusValues, "%")}"
        }

        node.modifiers.status.forEach { (rawKey, value) ->
            val key = rawKey.trim()
            when {
                key.equals("applyChancePct", ignoreCase = true) ->
                    addTalentEffectSeries(pieces, "Chance de aplicar status", maxRank, value, suffix = "%")
                key.equals("durationPct", ignoreCase = true) ->
                    addTalentEffectSeries(pieces, "Duracao dos status causados", maxRank, value, suffix = "%")
                key.equals("incomingDurationPct", ignoreCase = true) ->
                    addTalentEffectSeries(pieces, "Duracao dos status recebidos", maxRank, value, suffix = "%")
                key.equals("reflectDamagePct", ignoreCase = true) ->
                    addTalentEffectSeries(pieces, "Dano refletido", maxRank, value, suffix = "%")
                key.equals("bonusDamageVsLowHpPct", ignoreCase = true) -> Unit
                key.equals("lowHpThresholdPct", ignoreCase = true) -> Unit
                key.startsWith("applyChance.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "applyChance.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Chance de aplicar $statusName", maxRank, value, suffix = "%")
                    }
                key.startsWith("duration.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "duration.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Duracao de $statusName", maxRank, value, suffix = "%")
                    }
                key.startsWith("incomingDuration.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "incomingDuration.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Duracao de $statusName recebido", maxRank, value, suffix = "%")
                    }
                key.startsWith("consumeChance.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "consumeChance.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(
                            pieces,
                            "Chance de consumir $statusName",
                            maxRank,
                            value,
                            suffix = "%",
                            includePlus = false
                        )
                    }
                key.startsWith("bonusDamageVs.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "bonusDamageVs.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Dano contra $statusName", maxRank, value, suffix = "%")
                    }
                key.startsWith("bonusDamageWhileSelf.", ignoreCase = true) && key.endsWith(".Pct", ignoreCase = true) ->
                    talentStatusNameFromKey(key, "bonusDamageWhileSelf.", ".Pct")?.let { statusName ->
                        addTalentEffectSeries(pieces, "Dano enquanto voce estiver com $statusName", maxRank, value, suffix = "%")
                    }
            }
        }

        val combat = node.modifiers.combat
        if (combat.isNotEmpty()) {
            combat["damageMultiplier"]?.let { base ->
                val growth = combat["damageMultiplierPerRank"] ?: 0.0
                val values = talentEffectSequence(maxRank) { rank -> (base + (rank - 1) * growth) * 100.0 }
                pieces += "Dano ${formatTalentSequence(values, "%", includePlus = false)}"
            }
            (combat["mpCost"] ?: combat["manaCost"])?.let { base ->
                val growth = combat["mpCostPerRank"] ?: combat["manaCostPerRank"] ?: 0.0
                val values = talentEffectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Custo ${formatTalentSequence(values, " MP", includePlus = false)}"
            }
            (combat["cooldownSeconds"] ?: combat["cooldown"])?.let { base ->
                val growth = combat["cooldownPerRank"] ?: 0.0
                val values = talentEffectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Cooldown ${formatTalentSequence(values, "s", includePlus = false)}"
            }
            (combat["castTimeSeconds"] ?: combat["cast"])?.let { base ->
                val growth = combat["castTimePerRank"] ?: 0.0
                val values = talentEffectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Conjuracao ${formatTalentSequence(values, "s", includePlus = false)}"
            }
            combat["selfHealFlat"]?.let { base ->
                val growth = combat["selfHealFlatPerRank"] ?: 0.0
                val values = talentEffectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Cura propria ${formatTalentSequence(values, "", includePlus = false)}"
            }
            combat["selfHealPctMaxHp"]?.let { base ->
                val growth = combat["selfHealPctMaxHpPerRank"] ?: 0.0
                val values = talentEffectSequence(maxRank) { rank -> base + (rank - 1) * growth }
                pieces += "Cura propria ${formatTalentSequence(values, "% do HP max", includePlus = false)}"
            }
            val aoeUnlockRank = (combat["aoeUnlockRank"] ?: 0.0).toInt()
            val aoeBonusDamagePct = combat["aoeBonusDamagePct"] ?: 0.0
            if (aoeUnlockRank > 0 && aoeBonusDamagePct > 0.0) {
                pieces += "No NV$aoeUnlockRank acerta area com ${formatTalentCompact(aoeBonusDamagePct)}% de dano extra"
            }
        }

        if (node.modifiers.applyStatuses.isNotEmpty()) {
            node.modifiers.applyStatuses.forEach { status ->
                val chancePerRank = combat["statusChancePerRankPct"] ?: 0.0
                val durationPerRank = combat["statusDurationPerRankSeconds"] ?: 0.0
                val effectPerRank = combat["statusEffectPerRank"] ?: 0.0
                val stacksPerRank = combat["statusMaxStacksPerRank"] ?: 0.0
                val chanceValues = talentEffectSequence(maxRank) { rank ->
                    status.chancePct + (rank - 1) * chancePerRank
                }
                val durationValues = talentEffectSequence(maxRank) { rank ->
                    status.durationSeconds + (rank - 1) * durationPerRank
                }
                val effectValues = talentEffectSequence(maxRank) { rank ->
                    status.effectValue + (rank - 1) * effectPerRank
                }
                val stackValues = talentEffectSequence(maxRank) { rank ->
                    (status.maxStacks + ((rank - 1) * stacksPerRank).toInt()).toDouble()
                }
                val detail = when (status.type) {
                    rpg.status.StatusType.BLEEDING ->
                        formatTalentSequence(effectValues.map { it * 100.0 }, "% do HP max por tique", includePlus = false)
                    rpg.status.StatusType.BURNING, rpg.status.StatusType.POISONED ->
                        formatTalentSequence(effectValues, " por tique", includePlus = false)
                    rpg.status.StatusType.SLOW, rpg.status.StatusType.WEAKNESS ->
                        formatTalentSequence(effectValues, "%", includePlus = false)
                    rpg.status.StatusType.PARALYZED ->
                        formatTalentSequence(effectValues, "% de falha", includePlus = false)
                    rpg.status.StatusType.FROZEN -> "congela o alvo"
                    rpg.status.StatusType.MARKED -> "marca o alvo"
                }
                val stackText = if (status.stackable || status.maxStacks > 1) {
                    " | acumulos ${formatTalentSequence(stackValues, "", includePlus = false)}"
                } else {
                    ""
                }
                pieces += "Aplica ${rpg.status.StatusSystem.displayName(status.type)} " +
                    "${formatTalentSequence(chanceValues, "%", includePlus = false)} por " +
                    "${formatTalentSequence(durationValues, "s", includePlus = false)} | efeito $detail$stackText"
            }
        }

        return if (pieces.isEmpty()) {
            "Sem efeito definido."
        } else {
            pieces.joinToString(". ")
        }
    }

    private fun addTalentEffectSeries(
        pieces: MutableList<String>,
        label: String,
        maxRank: Int,
        baseValue: Double,
        suffix: String = "",
        includePlus: Boolean = true
    ) {
        if (baseValue == 0.0) return
        val values = talentEffectSequence(maxRank) { rank -> baseValue * rank }
        pieces += "$label ${formatTalentSequence(values, suffix, includePlus)}"
    }

    private fun talentEffectSequence(
        maxRank: Int,
        valueAtRank: (Int) -> Double
    ): List<Double> {
        return (1..maxRank.coerceAtLeast(1)).map(valueAtRank)
    }

    private fun formatTalentSequence(
        values: List<Double>,
        suffix: String = "",
        includePlus: Boolean = true
    ): String {
        return values.joinToString("/") { value ->
            val sign = if (includePlus && value > 0.0) "+" else ""
            "$sign${formatTalentCompact(value)}$suffix"
        }
    }

    private fun formatTalentCompact(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        if (rounded == rounded.toLong().toDouble()) {
            return rounded.toLong().toString()
        }
        if ((rounded * 10.0) == (rounded * 10.0).toLong().toDouble()) {
            return "%.1f".format(rounded)
        }
        return "%.2f".format(rounded)
    }

    private fun talentStatusNameFromKey(
        rawKey: String,
        prefix: String,
        suffix: String
    ): String? {
        val body = rawKey
            .substring(prefix.length, rawKey.length - suffix.length)
            .trim()
            .uppercase()
            .replace("-", "_")
            .replace(" ", "_")
        val type = runCatching { rpg.status.StatusType.valueOf(body) }.getOrNull() ?: return null
        return rpg.status.StatusSystem.displayName(type)
    }

    private fun talentNodeDepth(tree: rpg.model.TalentTree, nodeId: String): Int {
        val byId = tree.nodes.associateBy { it.id }
        val visited = mutableSetOf<String>()
        fun depth(currentId: String): Int {
            if (!visited.add(currentId)) return 0
            val node = byId[currentId] ?: return 0
            if (node.prerequisites.isEmpty()) return 0
            return 1 + (node.prerequisites.maxOfOrNull { depth(it.nodeId) } ?: 0)
        }
        return depth(nodeId)
    }

    private fun openQuestBoard(state: GameState): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        var board = synchronizeQuestBoard(state.questBoard, player, itemInstances)

        while (true) {
            player = engine.classQuestService.synchronize(player)
            player = achievementTracker.synchronize(player)
            board = synchronizeQuestBoard(board, player, itemInstances)
            val classQuestEntry = classQuestMenu.dynamicEntry(player)
            val acceptableAlert = if (hasReadyToClaim(board.acceptedQuests)) uiColor("(!)", ansiQuestAlert) else ""
            val dailyAlert = if (hasReadyToClaim(questsForTier(board, QuestTier.DAILY))) uiColor("(!)", ansiQuestAlert) else ""
            val weeklyAlert = if (hasReadyToClaim(questsForTier(board, QuestTier.WEEKLY))) uiColor("(!)", ansiQuestAlert) else ""
            val monthlyAlert = if (hasReadyToClaim(questsForTier(board, QuestTier.MONTHLY))) uiColor("(!)", ansiQuestAlert) else ""
            println("\n=== Quests ===")
            println(
                "Replaces: diaria ${remainingReplaces(board.dailyReplaceUsed, QuestTier.DAILY)} | " +
                    "semanal ${remainingReplaces(board.weeklyReplaceUsed, QuestTier.WEEKLY)} | " +
                    "mensal ${remainingReplaces(board.monthlyReplaceUsed, QuestTier.MONTHLY)}"
            )
            println("Aceitas: ${board.acceptedQuests.size}/${rpg.quest.QuestBoardEngine.MAX_ACCEPTED_ACTIVE}")
            println("Pool aceitavel: ${board.availableAcceptableQuestPool.size}")
            var option = 1
            val classQuestOption = if (classQuestEntry != null) option++ else null
            val acceptableOption = option++
            val dailyOption = option++
            val weeklyOption = option++
            val monthlyOption = option++
            if (classQuestOption != null && classQuestEntry != null) {
                println("$classQuestOption. ${classQuestEntry.label}")
            }
            println("$acceptableOption. Aceitaveis $acceptableAlert")
            println("$dailyOption. Diarias $dailyAlert")
            println("$weeklyOption. Semanais $weeklyAlert")
            println("$monthlyOption. Mensais $monthlyAlert")
            println("x. Voltar")

            val choice = readMenuChoice("Escolha: ", 1, option - 1)
            when {
                classQuestOption != null && choice == classQuestOption -> {
                    val updated = openClassQuestMenu(player, itemInstances)
                    player = updated.player
                    itemInstances = updated.itemInstances
                    board = synchronizeQuestBoard(board, player, itemInstances)
                }
                choice == acceptableOption -> {
                    val updated = handleAcceptableQuestMenu(player, itemInstances, board)
                    player = updated.player
                    itemInstances = updated.itemInstances
                    board = updated.board
                }
                choice == dailyOption -> {
                    val updated = handleTierQuestMenu(QuestTier.DAILY, player, itemInstances, board)
                    player = updated.player
                    itemInstances = updated.itemInstances
                    board = updated.board
                }
                choice == weeklyOption -> {
                    val updated = handleTierQuestMenu(QuestTier.WEEKLY, player, itemInstances, board)
                    player = updated.player
                    itemInstances = updated.itemInstances
                    board = updated.board
                }
                choice == monthlyOption -> {
                    val updated = handleTierQuestMenu(QuestTier.MONTHLY, player, itemInstances, board)
                    player = updated.player
                    itemInstances = updated.itemInstances
                    board = updated.board
                }
                choice == null -> {
                    val updatedState = state.copy(
                        player = player,
                        itemInstances = itemInstances,
                        questBoard = board
                    )
                    autoSave(updatedState)
                    return updatedState
                }
            }
        }
    }

    private fun openAchievementMenu(state: GameState): GameState {
        var player = achievementTracker.synchronize(state.player)
        while (true) {
            val claimAlert = if (hasAchievementRewardReady(player)) uiColor("(!)", ansiQuestAlert) else ""
            println("\n=== Conquistas ===")
            println(labelWithAlert("1. CONQUISTAS", claimAlert))
            println("2. ESTATISTICAS")
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> player = openAchievementCollection(player)
                2 -> player = showAchievementStatistics(player)
                null -> {
                    val updated = state.copy(player = player)
                    autoSave(updated)
                    return updated
                }
            }
        }
    }

    private fun openAchievementCollection(initialPlayer: PlayerState): PlayerState {
        var player = achievementTracker.synchronize(initialPlayer)
        while (true) {
            val list = achievementMenu.buildAchievementList(player)
            player = list.player
            if (list.views.isEmpty()) {
                println("Nenhuma conquista cadastrada.")
                return player
            }

            println("\n=== CONQUISTAS ===")
            val grouped = list.views.groupBy { it.category }
            val categories = rpg.achievement.AchievementCategory.values().toList()
                .filter { grouped.containsKey(it) }
            categories.forEachIndexed { index, category ->
                val categoryViews = grouped[category].orEmpty()
                val readyRewards = categoryViews.count { it.rewardAvailable }
                val maxed = categoryViews.count { it.status == rpg.achievement.AchievementStatus.MAX }
                val alert = if (readyRewards > 0) uiColor("(!)", ansiQuestAlert) else ""
                println(labelWithAlert("${index + 1}. ${category.label}", alert))
                println(
                    "   ${categoryViews.size} conquista(s) | " +
                        "Recompensas prontas: $readyRewards | MAX: $maxed"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, categories.size) ?: return player
            val selectedCategory = categories[choice - 1]
            player = openAchievementCategory(player, selectedCategory)
        }
    }

    private fun openAchievementCategory(
        initialPlayer: PlayerState,
        category: rpg.achievement.AchievementCategory
    ): PlayerState {
        var player = achievementTracker.synchronize(initialPlayer)
        while (true) {
            val list = achievementMenu.buildAchievementList(player)
            player = list.player
            val views = list.views.filter { it.category == category }
            if (views.isEmpty()) {
                println("Nenhuma conquista em ${category.label}.")
                return player
            }

            println("\n=== CONQUISTAS | ${category.label} ===")
            views.forEachIndexed { index, view ->
                val alert = if (view.rewardAvailable) uiColor("(!)", ansiQuestAlert) else ""
                val target = view.currentTierTarget?.toString() ?: "MAX"
                val reward = view.nextRewardGold?.let { "$it ouro" } ?: "MAX"
                println(labelWithAlert("${index + 1}. ${view.displayName}", alert))
                println(
                    "   ${view.displayDescription} | Progresso ${view.currentValue}/$target | " +
                        "Concluida ${view.timesCompleted}x | Recompensa: $reward | Status: ${view.status.label}"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, views.size) ?: return player
            val selected = views[choice - 1]
            val target = selected.currentTierTarget?.toString() ?: "MAX"
            val reward = selected.nextRewardGold?.let { "$it ouro" } ?: "MAX"
            println("\n${selected.displayName}")
            println(selected.displayDescription)
            println("Progresso atual: ${selected.currentValue}/$target")
            println("Concluida(s): ${selected.timesCompleted}")
            println("Recompensa do proximo tier: $reward")
            println("Status: ${selected.status.label}")

            if (selected.rewardAvailable) {
                println("1. Resgatar recompensa")
                println("x. Voltar")
                if (readMenuChoice("Escolha: ", 1, 1) == 1) {
                    val claim = achievementService.claimReward(player, selected.id)
                    println(claim.message)
                    player = claim.player
                    showAchievementNotifications(claim.unlockedTiers)
                }
            } else {
                println("Pressione ENTER para voltar.")
                readLine() ?: throw InputClosedException()
            }
        }
    }

    private fun showAchievementStatistics(initialPlayer: PlayerState): PlayerState {
        val statsView = achievementMenu.buildStatistics(initialPlayer, knownMonsterBaseTypes())
        val player = statsView.player
        val lifetime = player.lifetimeStats
        println("\n=== ESTATISTICAS ===")
        println("GERAL")
        statsView.generalLines.forEach { println(it) }
        println("\nMOBS")
        println("Total de monstros abatidos: ${lifetime.totalMonstersKilled}")
        println("Abates por estrela (0* ate 7*):")
        statsView.killsByStarLines.forEach { println(it) }
        println("Abates por tipo base:")
        if (statsView.bestiaryLines.isEmpty()) {
            println("Nenhum registro no bestiario ainda.")
        } else {
            statsView.bestiaryLines.forEach { println(it) }
        }
        println("Pressione ENTER para voltar.")
        readLine() ?: throw InputClosedException()
        return player
    }

    private fun knownMonsterBaseTypes(): Set<String> {
        val fromRepo = repo.monsterArchetypes.values.map { archetype ->
            archetype.baseType.ifBlank { archetype.id.substringBefore('_') }
        }
        return (fromRepo + listOf("slime", "wolf", "elemental"))
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun openClassQuestMenu(
        initialPlayer: PlayerState,
        initialItemInstances: Map<String, rpg.model.ItemInstance>
    ): UseItemResult {
        var player = engine.classQuestService.synchronize(initialPlayer)
        var itemInstances = initialItemInstances

        while (true) {
            val view = classQuestMenu.view(player)
            if (view == null) {
                println("Nenhuma quest de classe disponivel no momento.")
                return UseItemResult(player, itemInstances)
            }

            println("\n=== ${view.title} ===")
            println("Status: ${view.statusLabel}")
            view.lines.forEach { println(it) }

            if (view.canChoosePath) {
                println("1. Escolher caminho ${view.context.definition.pathAName}")
                println("2. Escolher caminho ${view.context.definition.pathBName}")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 2)) {
                    1 -> {
                        val result = engine.classQuestService.choosePath(
                            player = player,
                            itemInstances = itemInstances,
                            pathId = view.context.definition.pathA
                        )
                        result.messages.forEach { println(it) }
                        player = result.player
                        itemInstances = result.itemInstances
                    }
                    2 -> {
                        val result = engine.classQuestService.choosePath(
                            player = player,
                            itemInstances = itemInstances,
                            pathId = view.context.definition.pathB
                        )
                        result.messages.forEach { println(it) }
                        player = result.player
                        itemInstances = result.itemInstances
                    }
                    null -> return UseItemResult(player, itemInstances)
                }
                continue
            }

            if (view.canCancel) {
                println("1. Cancelar missao")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 1)) {
                    1 -> {
                        println(
                            "Tem certeza que deseja cancelar esta missao? TODO o progresso sera perdido e a missao retornara para a etapa 1."
                        )
                        println("1. Confirmar cancelamento")
                        println("2. Manter missao")
                        println("x. Voltar")
                        if (readMenuChoice("Escolha: ", 1, 2) == 1) {
                            val result = engine.classQuestService.cancelCurrentQuest(player, itemInstances)
                            result.messages.forEach { println(it) }
                            player = result.player
                            itemInstances = result.itemInstances
                        }
                    }
                    null -> return UseItemResult(player, itemInstances)
                }
                continue
            }

            println("x. Voltar")
            return UseItemResult(player, itemInstances)
        }
    }

    private fun openCrafting(state: GameState): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        var board = synchronizeQuestBoard(state.questBoard, player, itemInstances)
        var worldTimeMinutes = state.worldTimeMinutes
        var lastClockSync = state.lastClockSyncEpochMs

        while (true) {
            println("\n=== Craft ===")
            println("1. Forja")
            println("2. Alquimia")
            println("3. Culinaria")
            println("x. Voltar")
            val discipline = when (readMenuChoice("Escolha: ", 1, 3)) {
                1 -> CraftDiscipline.FORGE
                2 -> CraftDiscipline.ALCHEMY
                3 -> CraftDiscipline.COOKING
                null -> {
                    val updatedState = state.copy(
                        player = player,
                        itemInstances = itemInstances,
                        questBoard = board,
                        worldTimeMinutes = worldTimeMinutes,
                        lastClockSyncEpochMs = lastClockSync
                    )
                    autoSave(updatedState)
                    return updatedState
                }
                else -> continue
            }

            val recipes = engine.craftingService.availableRecipes(player.level, discipline)
            if (recipes.isEmpty()) {
                println("Nenhuma receita disponivel para ${discipline.name.lowercase()}.")
                continue
            }

            println("\nReceitas de ${discipline.name.lowercase()}:")
            recipes.forEachIndexed { index, recipe ->
                val skill = engine.craftingService.recipeSkill(recipe)
                val skillSnapshot = engine.skillSystem.snapshot(player, skill)
                val ingredients = recipe.ingredients.joinToString(", ") { ingredient ->
                    "${itemName(ingredient.itemId)} x${ingredient.quantity}"
                }
                val output = "${itemName(recipe.outputItemId)} x${recipe.outputQty}"
                val maxCraftable = engine.craftingService.maxCraftable(player, itemInstances, recipe)
                val blockedReasons = mutableListOf<String>()
                if (player.level < recipe.minPlayerLevel) {
                    blockedReasons += "lvl necessario ${recipe.minPlayerLevel}"
                }
                if (skillSnapshot.level < recipe.minSkillLevel) {
                    blockedReasons += "skill ${skill.name.lowercase()} ${skillSnapshot.level}/${recipe.minSkillLevel}"
                }
                val availableLabel = uiColor("(${maxCraftable}x disponivel)", ansiUiHp)
                val blockLabel = if (blockedReasons.isEmpty()) {
                    ""
                } else {
                    " [${blockedReasons.joinToString(" | ")}]"
                }
                println(
                    "${index + 1}. ${recipe.name} -> $output | ingredientes: $ingredients " +
                        "$availableLabel$blockLabel"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, recipes.size) ?: continue

            val recipe = recipes[choice - 1]
            val maxCraftable = engine.craftingService.maxCraftable(player, itemInstances, recipe)
            if (maxCraftable <= 0) {
                println("Ingredientes ou requisitos insuficientes para ${recipe.name}.")
                continue
            }
            val maxAllowed = min(20, maxCraftable)
            println("Quantidade maxima disponivel agora: ${uiColor("${maxCraftable}x", ansiUiHp)}")
            val times = readInt("Quantidade de crafts (1-$maxAllowed): ", 1, maxAllowed)
            val skill = engine.craftingService.recipeSkill(recipe)
            val skillSnapshotBefore = engine.skillSystem.snapshot(player, skill)
            val duration = engine.skillSystem.actionDurationSeconds(
                baseSeconds = recipe.baseDurationSeconds * times.coerceAtLeast(1),
                skillLevel = skillSnapshotBefore.level
            )
            runProgressBar("Craftando ${recipe.name}", duration)
            val result = engine.craftingService.craft(player, itemInstances, recipe.id, times)
            println(result.message)
            if (!result.success) continue

            player = result.player
            itemInstances = result.itemInstances
            val spentMinutes = (duration / 60.0).coerceAtLeast(0.01)
            player = advanceOutOfCombatTime(player, itemInstances, spentMinutes)
            worldTimeMinutes += spentMinutes
            lastClockSync = System.currentTimeMillis()
            println("Tempo gasto em craft: ${format(spentMinutes)} min.")
            if (result.skillSnapshot != null) {
                println(
                    "Skill ${result.skillSnapshot.skill.name.lowercase()}: +" +
                        "${format(result.gainedXp)} XP (lvl ${result.skillSnapshot.level})"
                )
            }
            val outputId = result.outputItemId
            val outputQty = result.outputQuantity
            if (outputId != null && outputQty > 0 && result.recipe != null) {
                val disciplineTag = result.skillType?.name?.lowercase()
                    ?: result.recipe.discipline.name.lowercase()
                board = engine.questProgressTracker.onItemCrafted(
                    board = board,
                    outputItemId = outputId,
                    disciplineTag = disciplineTag,
                    quantity = outputQty
                )
                board = engine.questProgressTracker.onItemCollected(
                    board = board,
                    itemId = outputId,
                    quantity = outputQty
                )
                val classQuestUpdate = engine.classQuestService.onItemsCollected(
                    player = player,
                    itemInstances = itemInstances,
                    collectedItems = mapOf(outputId to outputQty)
                )
                classQuestUpdate.messages.forEach { println(it) }
                val classQuestGold = (classQuestUpdate.player.gold - player.gold).coerceAtLeast(0)
                player = classQuestUpdate.player
                if (classQuestGold > 0) {
                    player = applyAchievementUpdate(
                        achievementTracker.onGoldEarned(player, classQuestGold.toLong())
                    )
                }
                itemInstances = classQuestUpdate.itemInstances
            }
            board = synchronizeQuestBoard(board, player, itemInstances)
            autoSave(
                state.copy(
                    player = player,
                    itemInstances = itemInstances,
                    questBoard = board,
                    worldTimeMinutes = worldTimeMinutes,
                    lastClockSyncEpochMs = lastClockSync
                )
            )
        }
    }

    private fun openGathering(
        state: GameState,
        forcedType: GatheringType? = null
    ): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        var board = synchronizeQuestBoard(state.questBoard, player, itemInstances)
        var worldTimeMinutes = state.worldTimeMinutes
        var lastClockSync = state.lastClockSyncEpochMs

        while (true) {
            val type = if (forcedType != null) {
                forcedType
            } else {
                println("\n=== Gathering ===")
                println("1. Mineracao")
                println("2. Coleta de Ervas")
                println("3. Corte de Madeira")
                println("4. Pesca")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 4)) {
                    1 -> GatheringType.MINING
                    2 -> GatheringType.HERBALISM
                    3 -> GatheringType.WOODCUTTING
                    4 -> GatheringType.FISHING
                    null -> {
                        val updatedState = state.copy(
                            player = player,
                            itemInstances = itemInstances,
                            questBoard = board,
                            worldTimeMinutes = worldTimeMinutes,
                            lastClockSyncEpochMs = lastClockSync
                        )
                        autoSave(updatedState)
                        return updatedState
                    }
                    else -> continue
                }
            }

            val nodes = engine.gatheringService.availableNodes(player.level, type)
            if (nodes.isEmpty()) {
                println("Nenhum ponto de coleta disponivel nesta categoria.")
                if (forcedType != null) {
                    val updatedState = state.copy(
                        player = player,
                        itemInstances = itemInstances,
                        questBoard = board,
                        worldTimeMinutes = worldTimeMinutes,
                        lastClockSyncEpochMs = lastClockSync
                    )
                    autoSave(updatedState)
                    return updatedState
                } else {
                    continue
                }
            }

            println("\n=== ${gatheringTypeLabel(type)} ===")
            println("\nPontos disponiveis:")
            nodes.forEachIndexed { index, node ->
                val skill = engine.gatheringService.nodeSkill(node)
                val skillSnapshot = engine.skillSystem.snapshot(player, skill)
                val estSeconds = engine.skillSystem.actionDurationSeconds(
                    baseSeconds = node.baseDurationSeconds,
                    skillLevel = skillSnapshot.level
                )
                val blocked = skillSnapshot.level < node.minSkillLevel
                val blockLabel = if (!blocked) {
                    ""
                } else {
                    " [skill requerida ${node.minSkillLevel}]"
                }
                println(
                    "${index + 1}. ${node.name} -> ${itemName(node.resourceItemId)} " +
                        "(skill ${skill.name.lowercase()}: ${skillSnapshot.level}, " +
                        "tempo estimado: ${format(estSeconds)}s por coleta)$blockLabel"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, nodes.size)
            if (choice == null) {
                if (forcedType != null) {
                    val updatedState = state.copy(
                        player = player,
                        itemInstances = itemInstances,
                        questBoard = board,
                        worldTimeMinutes = worldTimeMinutes,
                        lastClockSyncEpochMs = lastClockSync
                    )
                    autoSave(updatedState)
                    return updatedState
                }
                continue
            }

            val node = nodes[choice - 1]
            val skill = engine.gatheringService.nodeSkill(node)
            val skillSnapshotBefore = engine.skillSystem.snapshot(player, skill)
            val duration = engine.skillSystem.actionDurationSeconds(
                baseSeconds = node.baseDurationSeconds,
                skillLevel = skillSnapshotBefore.level
            )
            runProgressBar("Coletando ${node.name}", duration)
            val result = engine.gatheringService.gather(player, itemInstances, node.id)
            println(result.message)
            if (!result.success || result.node == null || result.resourceItemId == null || result.quantity <= 0) {
                continue
            }

            player = result.player
            itemInstances = result.itemInstances
            val spentMinutes = (duration / 60.0).coerceAtLeast(0.01)
            player = advanceOutOfCombatTime(player, itemInstances, spentMinutes)
            worldTimeMinutes += spentMinutes
            lastClockSync = System.currentTimeMillis()
            println("Tempo gasto na coleta: ${format(spentMinutes)} min.")
            if (result.skillSnapshot != null) {
                println(
                    "Skill ${result.skillSnapshot.skill.name.lowercase()}: +" +
                        "${format(result.gainedXp)} XP (lvl ${result.skillSnapshot.level})"
                )
            }
            board = engine.questProgressTracker.onGatheringCompleted(
                board = board,
                resourceItemId = result.resourceItemId,
                gatheringTag = result.skillType?.name?.lowercase() ?: result.node.type.name.lowercase(),
                quantity = result.quantity
            )
            board = engine.questProgressTracker.onItemCollected(
                board = board,
                itemId = result.resourceItemId,
                quantity = result.quantity
            )
            val classQuestUpdate = engine.classQuestService.onItemsCollected(
                player = player,
                itemInstances = itemInstances,
                collectedItems = mapOf(result.resourceItemId to result.quantity)
            )
            classQuestUpdate.messages.forEach { println(it) }
            val classQuestGold = (classQuestUpdate.player.gold - player.gold).coerceAtLeast(0)
            player = classQuestUpdate.player
            if (classQuestGold > 0) {
                player = applyAchievementUpdate(
                    achievementTracker.onGoldEarned(player, classQuestGold.toLong())
                )
            }
            itemInstances = classQuestUpdate.itemInstances
            board = synchronizeQuestBoard(board, player, itemInstances)
            autoSave(
                state.copy(
                    player = player,
                    itemInstances = itemInstances,
                    questBoard = board,
                    worldTimeMinutes = worldTimeMinutes,
                    lastClockSyncEpochMs = lastClockSync
                )
            )
        }
    }

    private fun gatheringTypeLabel(type: GatheringType): String = when (type) {
        GatheringType.HERBALISM -> "Coleta de Ervas"
        GatheringType.MINING -> "Mineracao"
        GatheringType.WOODCUTTING -> "Cortar Madeira"
        GatheringType.FISHING -> "Pesca"
    }

    private fun handleTierQuestMenu(
        tier: QuestTier,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        board: rpg.quest.QuestBoardState
    ): QuestUiSnapshot {
        var snapshot = QuestUiSnapshot(player, itemInstances, board)
        while (true) {
            val quests = questsForTier(snapshot.board, tier)
            if (quests.isEmpty()) {
                println("Nenhuma quest nesta categoria.")
                return snapshot
            }

            println("\n=== ${tierLabel(tier)} ===")
            quests.forEachIndexed { index, quest ->
                println(
                    "${index + 1}. ${quest.title} | ${quest.currentProgress}/${quest.requiredAmount} | " +
                        "${questStatusLabelColored(quest.status)}"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, quests.size) ?: return snapshot

            val quest = quests[choice - 1]
            showQuestDetails(quest)
            val replaceUsed = when (tier) {
                QuestTier.DAILY -> snapshot.board.dailyReplaceUsed
                QuestTier.WEEKLY -> snapshot.board.weeklyReplaceUsed
                QuestTier.MONTHLY -> snapshot.board.monthlyReplaceUsed
                QuestTier.ACCEPTED -> 0
            }
            val replaceLimit = replaceLimitFor(tier)
            val canReplace = replaceLimit > 0 &&
                replaceUsed < replaceLimit &&
                quest.status != QuestStatus.CLAIMED
            val canClaim = quest.status == QuestStatus.READY_TO_CLAIM

            var option = 1
            var claimOption = -1
            var replaceOption = -1
            if (canClaim) {
                claimOption = option++
                println("$claimOption. Concluir e receber recompensa")
            }
            if (canReplace) {
                replaceOption = option++
                println("$replaceOption. Replace (${replaceLimit - replaceUsed} restantes)")
            }
            println("x. Voltar")
            val action = readMenuChoice("Escolha: ", 1, option - 1) ?: continue

            if (action == claimOption) {
                snapshot = claimQuest(snapshot, quest.instanceId)
                continue
            }
            if (action == replaceOption) {
                val replace = engine.questBoardEngine.replaceQuest(
                    board = snapshot.board,
                    player = snapshot.player,
                    tier = tier,
                    instanceId = quest.instanceId
                )
                println(replace.message)
                if (replace.success) {
                    val synced = synchronizeQuestBoard(replace.board, snapshot.player, snapshot.itemInstances)
                    snapshot = snapshot.copy(board = synced)
                }
            }
        }
    }

    private fun handleAcceptableQuestMenu(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        board: rpg.quest.QuestBoardState
    ): QuestUiSnapshot {
        var snapshot = QuestUiSnapshot(player, itemInstances, board)
        while (true) {
            val accepted = snapshot.board.acceptedQuests
                .filter { it.status == QuestStatus.ACTIVE || it.status == QuestStatus.READY_TO_CLAIM }
                .sortedWith(compareByDescending<QuestInstance> { it.status == QuestStatus.READY_TO_CLAIM }.thenBy { it.title })
            val acceptedAlert = if (hasReadyToClaim(accepted)) uiColor("(!)", ansiQuestAlert) else ""
            println("\n=== Quests Aceitaveis ===")
            println("1. Pool disponivel (${snapshot.board.availableAcceptableQuestPool.size})")
            println(
                "2. Quests aceitas (${accepted.size}/${rpg.quest.QuestBoardEngine.MAX_ACCEPTED_ACTIVE}) $acceptedAlert"
            )
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> {
                    val pool = snapshot.board.availableAcceptableQuestPool
                    if (pool.isEmpty()) {
                        println("Pool vazia. Aguarde o proximo ciclo de 20 minutos.")
                        continue
                    }
                    println("\nPool:")
                    pool.forEachIndexed { index, quest ->
                        println("${index + 1}. ${quest.title} | ${quest.description}")
                    }
                    println("x. Voltar")
                    val choice = readMenuChoice("Escolha: ", 1, pool.size) ?: continue
                    val selected = pool[choice - 1]
                    showQuestDetails(selected)
                    println("1. Aceitar")
                    println("x. Voltar")
                    if (readMenuChoice("Escolha: ", 1, 1) == 1) {
                        val accept = engine.questBoardEngine.acceptQuest(snapshot.board, selected.instanceId)
                        println(accept.message)
                        if (accept.success) {
                            snapshot = snapshot.copy(
                                board = synchronizeQuestBoard(accept.board, snapshot.player, snapshot.itemInstances)
                            )
                        }
                    }
                }
                2 -> {
                    if (accepted.isEmpty()) {
                        println("Nenhuma quest aceita.")
                        continue
                    }
                    println("\nAceitas:")
                    accepted.forEachIndexed { index, quest ->
                        println(
                            "${index + 1}. ${quest.title} | ${quest.currentProgress}/${quest.requiredAmount} | " +
                                "${questStatusLabelColored(quest.status)}"
                        )
                    }
                    println("x. Voltar")
                    val choice = readMenuChoice("Escolha: ", 1, accepted.size) ?: continue
                    val selected = accepted[choice - 1]
                    showQuestDetails(selected)
                    val canClaim = selected.status == QuestStatus.READY_TO_CLAIM
                    var option = 1
                    var claimOption = -1
                    if (canClaim) {
                        claimOption = option++
                        println("$claimOption. Concluir e receber recompensa")
                    }
                    val cancelOption = option++
                    println("$cancelOption. Cancelar quest")
                    println("x. Voltar")
                    when (readMenuChoice("Escolha: ", 1, option - 1)) {
                        claimOption -> {
                            snapshot = claimQuest(snapshot, selected.instanceId)
                        }
                        cancelOption -> {
                            val cancel = engine.questBoardEngine.cancelAcceptedQuest(snapshot.board, selected.instanceId)
                            println(cancel.message)
                            if (cancel.success) {
                                snapshot = snapshot.copy(
                                    board = synchronizeQuestBoard(cancel.board, snapshot.player, snapshot.itemInstances)
                                )
                            }
                        }
                    }
                }
                null -> return snapshot
            }
        }
    }

    private fun claimQuest(snapshot: QuestUiSnapshot, instanceId: String): QuestUiSnapshot {
        val result = engine.questRewardService.claimQuest(
            player = snapshot.player,
            itemInstances = snapshot.itemInstances,
            board = snapshot.board,
            instanceId = instanceId
        )
        println(result.message)
        if (!result.success) return snapshot

        var updatedPlayer = result.player
        var updatedItemInstances = result.itemInstances
        var updatedBoard = result.board
        val goldEarned = (result.player.gold - snapshot.player.gold).coerceAtLeast(0)
        if (goldEarned > 0) {
            updatedPlayer = applyAchievementUpdate(
                achievementTracker.onGoldEarned(updatedPlayer, goldEarned.toLong())
            )
        }
        updatedPlayer = applyAchievementUpdate(achievementTracker.onQuestCompleted(updatedPlayer))
        for ((itemId, qty) in result.grantedItems) {
            updatedBoard = engine.questProgressTracker.onItemCollected(
                board = updatedBoard,
                itemId = itemId,
                quantity = qty
            )
        }
        if (result.grantedItems.isNotEmpty()) {
            val classQuestUpdate = engine.classQuestService.onItemsCollected(
                player = updatedPlayer,
                itemInstances = updatedItemInstances,
                collectedItems = result.grantedItems
            )
            classQuestUpdate.messages.forEach { println(it) }
            val classQuestGold = (classQuestUpdate.player.gold - updatedPlayer.gold).coerceAtLeast(0)
            updatedPlayer = classQuestUpdate.player
            if (classQuestGold > 0) {
                updatedPlayer = applyAchievementUpdate(
                    achievementTracker.onGoldEarned(updatedPlayer, classQuestGold.toLong())
                )
            }
            updatedItemInstances = classQuestUpdate.itemInstances
        }
        updatedBoard = synchronizeQuestBoard(updatedBoard, updatedPlayer, updatedItemInstances)
        return QuestUiSnapshot(
            player = updatedPlayer,
            itemInstances = updatedItemInstances,
            board = updatedBoard
        )
    }

    private fun questsForTier(board: rpg.quest.QuestBoardState, tier: QuestTier): List<QuestInstance> {
        val base = when (tier) {
            QuestTier.DAILY -> board.dailyQuests
            QuestTier.WEEKLY -> board.weeklyQuests
            QuestTier.MONTHLY -> board.monthlyQuests
            QuestTier.ACCEPTED -> board.acceptedQuests
        }
        return base
            .filter { it.status == QuestStatus.ACTIVE || it.status == QuestStatus.READY_TO_CLAIM }
            .sortedWith(compareByDescending<QuestInstance> { it.status == QuestStatus.READY_TO_CLAIM }
                .thenBy { it.title })
    }

    private fun remainingReplaces(used: Int, tier: QuestTier): Int {
        val limit = replaceLimitFor(tier)
        return (limit - used).coerceAtLeast(0)
    }

    private fun replaceLimitFor(tier: QuestTier): Int {
        return when (tier) {
            QuestTier.DAILY -> rpg.quest.QuestBoardEngine.DAILY_REPLACE_LIMIT
            QuestTier.WEEKLY -> rpg.quest.QuestBoardEngine.WEEKLY_REPLACE_LIMIT
            QuestTier.MONTHLY -> rpg.quest.QuestBoardEngine.MONTHLY_REPLACE_LIMIT
            QuestTier.ACCEPTED -> 0
        }
    }

    private fun tierLabel(tier: QuestTier): String {
        return when (tier) {
            QuestTier.DAILY -> "Quests Diarias"
            QuestTier.WEEKLY -> "Quests Semanais"
            QuestTier.MONTHLY -> "Quests Mensais"
            QuestTier.ACCEPTED -> "Quests Aceitas"
        }
    }

    private fun showQuestDetails(quest: QuestInstance) {
        println("\n--- ${quest.title} ---")
        println("Tipo: ${quest.objectiveType.name}")
        println("Descricao: ${quest.description}")
        if (quest.hint.isNotBlank()) {
            println("Dica: ${quest.hint}")
        }
        println("Progresso: ${quest.currentProgress}/${quest.requiredAmount}")
        println("Status: ${questStatusLabelColored(quest.status)}")
        println("Categoria: ${tierLabel(quest.tier)}")
        println("Prazo: ${formatQuestDeadline(quest.expiresAt)}")
        println("Recompensa: ${formatQuestRewards(quest)}")
    }

    private fun formatQuestRewards(quest: QuestInstance): String {
        val items = if (quest.rewards.items.isEmpty()) {
            "sem itens"
        } else {
            quest.rewards.items.joinToString(", ") { "${itemName(it.itemId)} x${it.quantity}" }
        }
        return "XP ${quest.rewards.xp}, Ouro ${quest.rewards.gold}, Moeda ${quest.rewards.specialCurrency}, Itens: $items"
    }

    private fun formatQuestDeadline(expiresAt: Long?): String {
        if (expiresAt == null) return "Sem prazo"
        val instant = Instant.ofEpochMilli(expiresAt).atZone(questZoneId)
        return "${instant.toLocalDate()} ${instant.toLocalTime().withNano(0)}"
    }

    private fun questStatusLabel(status: QuestStatus): String {
        return when (status) {
            QuestStatus.ACTIVE -> "Ativa"
            QuestStatus.READY_TO_CLAIM -> "Pronta"
            QuestStatus.CLAIMED -> "Concluida"
            QuestStatus.CANCELLED -> "Cancelada"
        }
    }

    private fun questStatusLabelColored(status: QuestStatus): String {
        val base = questStatusLabel(status)
        return when (status) {
            QuestStatus.ACTIVE -> uiColor(base, ansiQuestActive)
            QuestStatus.READY_TO_CLAIM -> uiColor(base, ansiQuestReady)
            else -> base
        }
    }

    private fun itemName(itemId: String): String {
        return engine.itemRegistry.entry(itemId)?.name ?: itemId
    }

    private fun enterDungeon(state: GameState, forceClassDungeon: Boolean = false): GameState {
        val classDungeon = engine.classQuestService.activeDungeon(state.player)
        var classDungeonMode: ClassQuestDungeonDefinition? = null
        if (forceClassDungeon) {
            if (classDungeon == null) {
                println("Mapa de classe indisponivel. Pegue e inicie a missao de classe primeiro.")
                return state
            }
            classDungeonMode = classDungeon
        } else if (classDungeon != null) {
            println("\n=== DUNGEONS ===")
            println("1. Dungeon Infinita")
            println("2. Instancia de Classe (${classDungeon.pathName})")
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> Unit
                2 -> classDungeonMode = classDungeon
                null -> return state
            }
        }

        val tiers = engine.availableTiers(state.player)
        if (tiers.isEmpty()) {
            println("Nivel insuficiente para entrar em qualquer tier.")
            return state
        }

        println("\nEscolha o tier da dungeon:")
        tiers.forEachIndexed { index, tier ->
            println("${index + 1}. ${tier.id} (min lvl ${tier.minLevel})")
        }
        println("x. Voltar")
        val tierChoice = readMenuChoice("Escolha: ", 1, tiers.size) ?: return state
        val chosenTier = tiers[tierChoice - 1]
        if (!engine.canEnterTier(state.player, chosenTier)) {
            println("Nivel insuficiente para este tier.")
            return state
        }

        if (classDungeonMode == null) {
            println("\nVoce entrou na dungeon infinita (${chosenTier.id}).")
        } else {
            println(
                "\nVoce entrou na instancia de classe de ${classDungeonMode.pathName} " +
                    "(tier ${chosenTier.id})."
            )
        }
        val clearedState = state.copy(player = clearRunEffects(state.player))
        autoSave(clearedState)
        var player = clearedState.player
        var itemInstances = clearedState.itemInstances
        var questBoard = synchronizeQuestBoard(clearedState.questBoard, player, itemInstances)
        var run = engine.startRun(chosenTier.id)
        var worldTimeMinutes = clearedState.worldTimeMinutes
        var lastClockSync = clearedState.lastClockSyncEpochMs
        val loot = mutableListOf<String>()

        while (run.isActive) {
            val roomType = if (classDungeonMode == null) {
                engine.nextRoomType(run)
            } else {
                if (engine.isBossRoomDue(run)) RunRoomType.BOSS else RunRoomType.MONSTER
            }
            println("\n--- Sala ${run.roomsCleared + 1} | Dificuldade ${run.difficultyLevel} ---")

            when (roomType) {
                RunRoomType.BOSS -> {
                    player = preBossSanctuaryRoom(player, itemInstances)
                    val monster = if (classDungeonMode == null) {
                        engine.generateMonster(chosenTier, run, player, isBoss = true)
                    } else {
                        generateClassQuestDungeonMonster(
                            dungeon = classDungeonMode,
                            tier = chosenTier,
                            run = run,
                            player = player,
                            isBoss = true
                        )
                    }
                    println(engine.encounterText(monster, chosenTier, computePlayerStats(player, itemInstances)))
                    val outcome = battleMonster(
                        playerState = player,
                        itemInstances = itemInstances,
                        monster = monster,
                        tier = chosenTier,
                        lootCollector = loot,
                        isBoss = true,
                        classDungeon = classDungeonMode
                    )
                    itemInstances = outcome.itemInstances
                    if (!outcome.victory) {
                        return handleRunFailure(
                            state = state,
                            outcome = outcome,
                            loot = loot,
                            itemInstances = itemInstances,
                            questBoard = questBoard,
                            worldTimeMinutes = worldTimeMinutes,
                            lastClockSync = lastClockSync
                        )
                    }
                    player = outcome.playerAfter
                    questBoard = applyBattleQuestProgress(
                        board = questBoard,
                        monster = monster,
                        outcome = outcome,
                        isBoss = true
                    )
                    run = engine.advanceRun(
                        run = run,
                        bossDefeated = true,
                        clearedRoomType = RunRoomType.BOSS,
                        victoryInRoom = true
                    )
                    questBoard = engine.questProgressTracker.onFloorReached(questBoard, run.depth)
                    if (classDungeonMode == null) {
                        println("Boss derrotado! A dificuldade aumentou.")
                    } else {
                        println("Boss da instancia derrotado.")
                    }
                }
                RunRoomType.MONSTER -> {
                    val monster = if (classDungeonMode == null) {
                        engine.generateMonster(chosenTier, run, player, isBoss = false)
                    } else {
                        generateClassQuestDungeonMonster(
                            dungeon = classDungeonMode,
                            tier = chosenTier,
                            run = run,
                            player = player,
                            isBoss = false
                        )
                    }
                    println(engine.encounterText(monster, chosenTier, computePlayerStats(player, itemInstances)))
                    val outcome = battleMonster(
                        playerState = player,
                        itemInstances = itemInstances,
                        monster = monster,
                        tier = chosenTier,
                        lootCollector = loot,
                        isBoss = false,
                        classDungeon = classDungeonMode
                    )
                    itemInstances = outcome.itemInstances
                    if (!outcome.victory) {
                        return handleRunFailure(
                            state = state,
                            outcome = outcome,
                            loot = loot,
                            itemInstances = itemInstances,
                            questBoard = questBoard,
                            worldTimeMinutes = worldTimeMinutes,
                            lastClockSync = lastClockSync
                        )
                    }
                    player = outcome.playerAfter
                    questBoard = applyBattleQuestProgress(
                        board = questBoard,
                        monster = monster,
                        outcome = outcome,
                        isBoss = false
                    )
                    run = engine.advanceRun(
                        run = run,
                        bossDefeated = false,
                        clearedRoomType = RunRoomType.MONSTER,
                        victoryInRoom = true
                    )
                    questBoard = engine.questProgressTracker.onFloorReached(questBoard, run.depth)
                }
                RunRoomType.REST -> {
                    player = restRoom(player, itemInstances, chosenTier)
                    run = engine.advanceRun(
                        run = run,
                        bossDefeated = false,
                        clearedRoomType = RunRoomType.REST,
                        victoryInRoom = false
                    )
                    questBoard = engine.questProgressTracker.onFloorReached(questBoard, run.depth)
                }
                RunRoomType.EVENT -> {
                    val eventOutcome = eventRoom(
                        player = player,
                        itemInstances = itemInstances,
                        loot = loot,
                        difficultyLevel = run.difficultyLevel,
                        depth = run.depth,
                        tier = chosenTier,
                        questBoard = questBoard
                    )
                    if (eventOutcome.battleOutcome != null) {
                        val outcome = eventOutcome.battleOutcome
                        itemInstances = outcome.itemInstances
                        if (!outcome.victory) {
                            return handleRunFailure(
                                state = state,
                                outcome = outcome,
                                loot = loot,
                                itemInstances = itemInstances,
                                questBoard = eventOutcome.questBoard,
                                worldTimeMinutes = worldTimeMinutes,
                                lastClockSync = lastClockSync
                            )
                        }
                        player = outcome.playerAfter
                    } else {
                        player = eventOutcome.player
                        itemInstances = eventOutcome.itemInstances ?: itemInstances
                    }
                    questBoard = eventOutcome.questBoard
                    run = engine.advanceRun(
                        run = run,
                        bossDefeated = false,
                        clearedRoomType = RunRoomType.EVENT,
                        victoryInRoom = eventOutcome.battleOutcome?.victory == true
                    )
                    questBoard = engine.questProgressTracker.onFloorReached(questBoard, run.depth)
                }
            }

            run = run.copy(lootCollected = loot.toList())
            player = tickEffects(player, itemInstances)
            worldTimeMinutes += roomTimeMinutes
            lastClockSync = System.currentTimeMillis()

            if (!promptContinue()) {
                val finalized = finalizeRun(player, loot, itemInstances)
                val updated = finalized.player
                itemInstances = finalized.itemInstances
                questBoard = engine.questProgressTracker.onRunCompleted(questBoard, 1)
                val updatedState = state.copy(
                    player = updated,
                    currentRun = null,
                    itemInstances = itemInstances,
                    questBoard = questBoard,
                    worldTimeMinutes = worldTimeMinutes,
                    lastClockSyncEpochMs = lastClockSync
                )
                autoSave(updatedState)
                println("\nRun encerrada. Loot guardado: ${loot.size} itens.")
                return updatedState
            }
        }

        return state.copy(
            player = player,
            currentRun = null,
            itemInstances = itemInstances,
            questBoard = questBoard,
            worldTimeMinutes = worldTimeMinutes,
            lastClockSyncEpochMs = lastClockSync
        )
    }

    private fun generateClassQuestDungeonMonster(
        dungeon: ClassQuestDungeonDefinition?,
        tier: rpg.model.MapTierDef,
        run: rpg.model.DungeonRun,
        player: PlayerState,
        isBoss: Boolean
    ): MonsterInstance {
        if (dungeon == null) {
            return engine.generateMonster(tier, run, player, isBoss)
        }

        val context = engine.classQuestService.currentContext(player)
        val shouldSpawnFinal = if (context != null && context.progress.chosenPath == dungeon.pathId) {
            engine.classQuestService.shouldSpawnFinalBoss(context)
        } else {
            false
        }
        val normalPool = if (dungeon.normalMonsters.isNotEmpty()) dungeon.normalMonsters else listOf(dungeon.finalBoss)
        val bossPool = if (dungeon.bossMonsters.isNotEmpty()) dungeon.bossMonsters else listOf(dungeon.finalBoss)
        val blueprint = when {
            !isBoss -> normalPool[engine.rollInt(normalPool.size)]
            shouldSpawnFinal -> dungeon.finalBoss
            bossPool.isNotEmpty() -> bossPool[engine.rollInt(bossPool.size)]
            else -> dungeon.finalBoss
        }

        val hasTemplate = repo.monsterArchetypes.containsKey(blueprint.baseArchetypeId)
        val templateId = if (hasTemplate) blueprint.baseArchetypeId else repo.monsterArchetypes.keys.first()
        val classTier = adjustedTierForClassDungeon(tier, dungeon.unlockType, isBoss)
            .copy(allowedMonsterTemplates = listOf(templateId))
        val generated = engine.generateMonster(classTier, run, player, isBoss)
        val scaledXp = when (dungeon.unlockType) {
            ClassQuestUnlockType.SUBCLASS -> if (isBoss) 1.25 else 1.15
            ClassQuestUnlockType.SPECIALIZATION -> if (isBoss) 1.45 else 1.30
        }
        val scaledGold = when (dungeon.unlockType) {
            ClassQuestUnlockType.SUBCLASS -> if (isBoss) 1.20 else 1.10
            ClassQuestUnlockType.SPECIALIZATION -> if (isBoss) 1.35 else 1.20
        }
        val bonusTags = setOf(
            "classquest",
            "classquest:${dungeon.unlockType.name.lowercase()}",
            "path:${dungeon.pathId}",
            "base:${blueprint.baseType}",
            "family:${blueprint.family}",
            "type:${blueprint.family}"
        ) + blueprint.identityTags
        val questTags = generated.questTags + bonusTags
        val lootProfileId = blueprint.lootProfileId.ifBlank { generated.lootProfileId }
        return generated.copy(
            archetypeId = blueprint.monsterId,
            id = blueprint.monsterId,
            sourceArchetypeId = generated.sourceArchetypeId,
            baseType = blueprint.baseType,
            monsterTypeId = blueprint.family,
            family = blueprint.family,
            name = blueprint.displayName,
            displayName = blueprint.displayName,
            variantName = "",
            tags = generated.tags + bonusTags,
            questTags = questTags,
            dropTableId = lootProfileId,
            lootProfileId = lootProfileId,
            baseXp = (generated.baseXp * scaledXp).toInt().coerceAtLeast(1),
            baseGold = (generated.baseGold * scaledGold).toInt().coerceAtLeast(1)
        )
    }

    private fun adjustedTierForClassDungeon(
        tier: rpg.model.MapTierDef,
        unlockType: ClassQuestUnlockType,
        isBoss: Boolean
    ): rpg.model.MapTierDef {
        val levelBoost = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> if (isBoss) 3 else 2
            ClassQuestUnlockType.SPECIALIZATION -> if (isBoss) 8 else 5
        }
        val difficultyBoost = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> if (isBoss) 1.12 else 1.08
            ClassQuestUnlockType.SPECIALIZATION -> if (isBoss) 1.35 else 1.22
        }
        return tier.copy(
            baseMonsterLevel = (tier.baseMonsterLevel + levelBoost).coerceAtLeast(1),
            difficultyMultiplier = (tier.difficultyMultiplier * difficultyBoost).coerceAtLeast(1.0)
        )
    }

    private fun handleRunFailure(
        state: GameState,
        outcome: BattleOutcome,
        loot: MutableList<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        questBoard: rpg.quest.QuestBoardState,
        worldTimeMinutes: Double,
        lastClockSync: Long
    ): GameState {
        return if (outcome.escaped) {
            val finalized = finalizeRun(outcome.playerAfter, loot, itemInstances)
            val updated = finalized.player
            val progressedBoard = engine.questProgressTracker.onRunCompleted(questBoard, 1)
            println("\nVoce saiu da run com ${loot.size} itens.")
            val updatedState = state.copy(
                player = updated,
                currentRun = null,
                itemInstances = finalized.itemInstances,
                questBoard = progressedBoard,
                worldTimeMinutes = worldTimeMinutes,
                lastClockSyncEpochMs = lastClockSync
            )
            autoSave(updatedState)
            updatedState
        } else {
            val result = applyDeathPenalty(outcome.playerAfter, loot, itemInstances)
            println("\nVoce foi derrotado e expulso da dungeon.")
            val updatedState = state.copy(
                player = result.player,
                currentRun = null,
                itemInstances = result.itemInstances,
                questBoard = questBoard,
                worldTimeMinutes = worldTimeMinutes,
                lastClockSyncEpochMs = lastClockSync
            )
            autoSave(updatedState)
            updatedState
        }
    }

    private fun restRoom(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        tier: rpg.model.MapTierDef
    ): PlayerState {
        val stats = computePlayerStats(player, itemInstances)
        val healingMult = tier.biomeId?.let { repo.biomes[it]?.healingMultiplier ?: 1.0 } ?: 1.0
        val hpRestored = (stats.derived.hpMax * restHealPct + stats.derived.hpRegen * restRegenMultiplier) * healingMult
        val mpRestored = (stats.derived.mpMax * restHealPct + stats.derived.mpRegen * restRegenMultiplier) * healingMult
        println("\nSala de descanso. HP e MP restaurados parcialmente.")
        return applyHealing(player, hpRestored, mpRestored, itemInstances)
    }

    private fun preBossSanctuaryRoom(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        val stats = computePlayerStats(player, itemInstances)
        println("\nSala de Preparacao do Boss:")
        println("1. Recuperar 10% de HP/MP")
        println("2. Ganhar +10% de atributos so para este boss")
        println("x. Voltar")
        return when (readMenuChoice("Escolha: ", 1, 2)) {
            1 -> {
                val hp = stats.derived.hpMax * 0.10
                val mp = stats.derived.mpMax * 0.10
                println("Voce recuperou parte dos recursos antes do boss.")
                applyHealing(player, hp, mp, itemInstances)
            }
            2 -> {
                println("Voce entrou focado: +10% de atributos para este boss.")
                applyRoomEffect(player, 1.10, 1)
            }
            null -> player
            else -> player
        }
    }

    private fun eventRoom(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: rpg.model.MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        val roll = engine.rollInt(100)
        val biome = tier.biomeId?.let { repo.biomes[it] }
        val npcThreshold = (34 + (biome?.npcEventBonusPct ?: 0)).coerceIn(10, 85)
        val secondaryThreshold = npcThreshold + ((100 - npcThreshold) / 2)
        return when {
            roll < npcThreshold -> npcEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
            roll < secondaryThreshold -> liquidEvent(player, itemInstances, depth, questBoard)
            else -> chestEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
        }
    }

    private fun npcEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: rpg.model.MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        val intro = dungeonEventService.npcIntro { bound -> engine.rollInt(bound) }
        println("\nEvento: $intro")

        return when (dungeonEventService.pickNpcVariant { bound -> engine.rollInt(bound) }) {
            NpcEventVariant.MONEY -> npcMoneyRequestEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
            NpcEventVariant.ITEM -> npcItemRequestEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
            NpcEventVariant.SUSPICIOUS -> npcSuspiciousEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
        }
    }

    private fun liquidEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        depth: Int,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        println("\nEvento: ${dungeonEventService.liquidIntro { bound -> engine.rollInt(bound) }}")
        println("1. Provar")
        println("2. Testar com cuidado")
        println("3. Ignorar")
        println("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, 3) ?: 3
        if (choice == 3) {
            println(dungeonEventService.liquidIgnoreLine { bound -> engine.rollInt(bound) })
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        val context = eventContext(player, itemInstances, depth)
        val event = if (choice == 2) {
            val first = EventEngine.generateEvent(EventSource.LIQUID, context)
            val second = EventEngine.generateEvent(EventSource.LIQUID, context)
            if (hasDirectDamageRisk(first) && !hasDirectDamageRisk(second)) second else first
        } else {
            EventEngine.generateEvent(EventSource.LIQUID, context)
        }
        println("[${event.rarity}] ${event.description}")
        val updated = applyEventWithFeedback(player, itemInstances, event, context)
        return EventRoomOutcome(player = updated, questBoard = questBoard)
    }

    private fun chestEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: rpg.model.MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        println("\nEvento: ${dungeonEventService.chestIntro { bound -> engine.rollInt(bound) }}")
        println("1. Abrir rapido")
        println("2. Inspecionar antes de abrir")
        println("3. Ignorar")
        println("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, 3) ?: 3
        if (choice == 3) {
            println(dungeonEventService.chestIgnoreLine { bound -> engine.rollInt(bound) })
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        val mimicChance = dungeonEventService.chestMimicChancePct(inspected = choice == 2)
        if (engine.rollInt(100) < mimicChance) {
            println(dungeonEventService.chestAmbushLine { bound -> engine.rollInt(bound) })
            return runNpcAmbush(player, itemInstances, loot, difficultyLevel, tier, questBoard)
        }
        val context = eventContext(player, itemInstances, depth)
        val event = EventEngine.generateEvent(EventSource.CHEST_REWARD, context)
        println("[${event.rarity}] ${event.description}")
        val updated = applyEventWithFeedback(player, itemInstances, event, context)
        return EventRoomOutcome(player = updated, questBoard = questBoard)
    }

    private fun npcMoneyRequestEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: rpg.model.MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        val requestedGold = dungeonEventService.requestedGold(
            playerLevel = player.level,
            depth = depth,
            rollInt = { bound -> engine.rollInt(bound) }
        )
        println(dungeonEventService.npcMoneyPitch(requestedGold) { bound -> engine.rollInt(bound) })
        println("1. Entregar ouro")
        println("2. Recusar")
        println("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, 2) ?: 2

        if (choice == 2) {
            if (dungeonEventService.shouldAmbushOnMoneyRefuse { bound -> engine.rollInt(bound) }) {
                println(dungeonEventService.npcMoneyRefuseLine { bound -> engine.rollInt(bound) })
                return runNpcAmbush(player, itemInstances, loot, difficultyLevel, tier, questBoard)
            }
            println("O viajante murmura algo e some no corredor.")
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        if (player.gold < requestedGold) {
            println(dungeonEventService.npcMoneyNoGoldLine { bound -> engine.rollInt(bound) })
            if (dungeonEventService.shouldAmbushOnMoneyNoGold { bound -> engine.rollInt(bound) }) {
                println(dungeonEventService.npcMoneyRefuseLine { bound -> engine.rollInt(bound) })
                return runNpcAmbush(player, itemInstances, loot, difficultyLevel, tier, questBoard)
            }
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        val paidPlayer = applyAchievementUpdate(
            achievementTracker.onGoldSpent(
                player = player.copy(gold = player.gold - requestedGold),
                amount = requestedGold.toLong()
            )
        )
        return when {
            dungeonEventService.shouldScamOnMoneyGive { bound -> engine.rollInt(bound) } -> {
                println(dungeonEventService.npcMoneyScamLine { bound -> engine.rollInt(bound) })
                runNpcAmbush(paidPlayer, itemInstances, loot, difficultyLevel, tier, questBoard)
            }
            dungeonEventService.shouldRewardOnMoneyGive { bound -> engine.rollInt(bound) } -> {
                println(dungeonEventService.npcMoneyRewardLine { bound -> engine.rollInt(bound) })
                val context = eventContext(paidPlayer, itemInstances, depth)
                val event = EventEngine.generateEvent(EventSource.NPC_HELP, context)
                println("[${event.rarity}] ${event.description}")
                val updated = applyEventWithFeedback(paidPlayer, itemInstances, event, context)
                EventRoomOutcome(player = updated, questBoard = questBoard)
            }
            else -> {
                println(dungeonEventService.npcMoneyNeutralLine { bound -> engine.rollInt(bound) })
                EventRoomOutcome(player = paidPlayer, questBoard = questBoard)
            }
        }
    }

    private fun npcItemRequestEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: rpg.model.MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        val requested = pickTravelerRequestedStack(player, itemInstances)
        if (requested == null) {
            println(dungeonEventService.npcItemNoItemsLine { bound -> engine.rollInt(bound) })
            return npcMoneyRequestEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
        }

        val qty = if (requested.quantity >= 2 && engine.rollInt(100) < 45) 2 else 1
        println(
            dungeonEventService.npcItemPitch(
                itemName = requested.item.name,
                qty = qty,
                rollInt = { bound -> engine.rollInt(bound) }
            )
        )
        println("1. Entregar item")
        println("2. Recusar")
        println("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, 2) ?: 2
        if (choice == 2) {
            if (dungeonEventService.shouldAmbushOnItemRefuse { bound -> engine.rollInt(bound) }) {
                println(dungeonEventService.npcItemRefuseLine { bound -> engine.rollInt(bound) })
                return runNpcAmbush(player, itemInstances, loot, difficultyLevel, tier, questBoard)
            }
            println("O viajante segue por outro caminho.")
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        val consumed = consumeInventoryItems(player, itemInstances, requested.itemIds, qty)
        return when {
            dungeonEventService.shouldScamOnItemGive { bound -> engine.rollInt(bound) } -> {
                println(dungeonEventService.npcItemScamLine { bound -> engine.rollInt(bound) })
                runNpcAmbush(consumed.player, consumed.itemInstances, loot, difficultyLevel, tier, questBoard)
            }
            dungeonEventService.shouldRewardOnItemGive { bound -> engine.rollInt(bound) } -> {
                println(dungeonEventService.npcItemRewardLine { bound -> engine.rollInt(bound) })
                val context = eventContext(consumed.player, consumed.itemInstances, depth)
                val event = EventEngine.generateEvent(EventSource.NPC_HELP, context)
                println("[${event.rarity}] ${event.description}")
                val updated = applyEventWithFeedback(consumed.player, consumed.itemInstances, event, context)
                EventRoomOutcome(player = updated, itemInstances = consumed.itemInstances, questBoard = questBoard)
            }
            else -> {
                println(dungeonEventService.npcItemNeutralLine { bound -> engine.rollInt(bound) })
                EventRoomOutcome(player = consumed.player, itemInstances = consumed.itemInstances, questBoard = questBoard)
            }
        }
    }

    private fun npcSuspiciousEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: rpg.model.MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        println(dungeonEventService.npcSuspiciousPitch { bound -> engine.rollInt(bound) })
        println("1. Seguir a indicacao")
        println("2. Ignorar")
        println("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, 2) ?: 2
        if (choice == 2) {
            println(dungeonEventService.npcSuspiciousRefuseLine { bound -> engine.rollInt(bound) })
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        if (dungeonEventService.shouldAmbushOnSuspiciousAccept { bound -> engine.rollInt(bound) }) {
            println(dungeonEventService.npcSuspiciousScamLine { bound -> engine.rollInt(bound) })
            return runNpcAmbush(player, itemInstances, loot, difficultyLevel, tier, questBoard)
        }

        println(dungeonEventService.npcSuspiciousRewardLine { bound -> engine.rollInt(bound) })
        val context = eventContext(player, itemInstances, depth)
        val event = EventEngine.generateEvent(EventSource.NPC_HELP, context)
        println("[${event.rarity}] ${event.description}")
        val updated = applyEventWithFeedback(player, itemInstances, event, context)
        return EventRoomOutcome(player = updated, questBoard = questBoard)
    }

    private fun runNpcAmbush(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        tier: rpg.model.MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        val mimic = buildMimicMonster(difficultyLevel)
        val outcome = battleMonster(player, itemInstances, mimic, tier, loot, isBoss = false)
        val progressedBoard = if (outcome.victory) {
            applyBattleQuestProgress(questBoard, mimic, outcome, isBoss = false)
        } else {
            questBoard
        }
        return EventRoomOutcome(
            player = outcome.playerAfter,
            itemInstances = outcome.itemInstances,
            battleOutcome = outcome,
            questBoard = progressedBoard
        )
    }

    private fun pickTravelerRequestedStack(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): InventoryStack? {
        val candidates = buildInventoryStacks(player, itemInstances).filter { it.item.type != ItemType.EQUIPMENT }
        if (candidates.isEmpty()) return null
        return candidates[engine.rollInt(candidates.size)]
    }

    private fun consumeInventoryItems(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        itemIds: List<String>,
        quantity: Int
    ): UseItemResult {
        val count = quantity.coerceAtLeast(1)
        val toConsume = itemIds.take(count)
        val inventory = player.inventory.toMutableList()
        val instances = itemInstances.toMutableMap()
        var removed = 0
        for (itemId in toConsume) {
            if (inventory.remove(itemId)) {
                removed++
                if (instances.containsKey(itemId)) {
                    instances.remove(itemId)
                }
            }
        }
        if (removed > 0) {
            println("Itens entregues: x$removed")
        }
        return UseItemResult(
            player = player.copy(inventory = inventory),
            itemInstances = instances.toMap()
        )
    }

    private fun hasDirectDamageRisk(event: rpg.events.EventDefinition): Boolean {
        return event.effects.any { it is rpg.events.EventEffect.DamagePercentCurrent }
    }

    private fun applyEventWithFeedback(
        before: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        event: rpg.events.EventDefinition,
        context: EventContext
    ): PlayerState {
        val raw = EventExecutor.execute(event, before, context)
        val addedIds = extractAddedInventoryIds(before.inventory, raw.inventory)
        val inserted = InventorySystem.addItemsWithLimit(
            player = before.copy(
                equipped = raw.equipped,
                inventory = before.inventory
            ),
            itemInstances = itemInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = addedIds
        )
        val after = raw.copy(
            inventory = inserted.inventory,
            quiverInventory = inserted.quiverInventory,
            selectedAmmoTemplateId = inserted.selectedAmmoTemplateId
        )
        val hpDelta = after.currentHp - before.currentHp
        val mpDelta = after.currentMp - before.currentMp
        val goldDelta = after.gold - before.gold
        if (hpDelta != 0.0) {
            val label = if (hpDelta > 0) "+" else ""
            println("Efeito: HP $label${format(hpDelta)}")
        }
        if (mpDelta != 0.0) {
            val label = if (mpDelta > 0) "+" else ""
            println("Efeito: MP $label${format(mpDelta)}")
        }
        if (goldDelta != 0) {
            val label = if (goldDelta > 0) "+" else ""
            println("Efeito: Ouro $label$goldDelta")
        }
        if (inserted.accepted.isNotEmpty()) {
            val grouped = inserted.accepted.groupingBy { canonicalItemId(it, itemInstances) }.eachCount()
            val labels = grouped.entries.joinToString(", ") { "${itemName(it.key)} x${it.value}" }
            println("Itens obtidos: $labels")
        }
        if (inserted.rejected.isNotEmpty()) {
            println("Inventario cheio: ${inserted.rejected.size} item(ns) do evento foram descartados.")
        }
        var updatedPlayer = clampPlayerResources(after, itemInstances)
        if (goldDelta > 0) {
            updatedPlayer = applyAchievementUpdate(
                achievementTracker.onGoldEarned(updatedPlayer, goldDelta.toLong())
            )
        } else if (goldDelta < 0) {
            updatedPlayer = applyAchievementUpdate(
                achievementTracker.onGoldSpent(updatedPlayer, -goldDelta.toLong())
            )
        }
        return updatedPlayer
    }

    private fun extractAddedInventoryIds(before: List<String>, after: List<String>): List<String> {
        val remaining = before.groupingBy { it }.eachCount().toMutableMap()
        val added = mutableListOf<String>()
        for (id in after) {
            val count = remaining[id] ?: 0
            if (count > 0) {
                remaining[id] = count - 1
            } else {
                added += id
            }
        }
        return added
    }

    private fun buildMimicMonster(difficultyLevel: Int): MonsterInstance {
        return engine.buildMimicMonster(difficultyLevel)
    }

    private fun applyBattleQuestProgress(
        board: rpg.quest.QuestBoardState,
        monster: MonsterInstance,
        outcome: BattleOutcome,
        isBoss: Boolean
    ): rpg.quest.QuestBoardState {
        if (!outcome.victory) return board
        val tags = monster.tags.mapTo(mutableSetOf()) { it.lowercase() }
        tags.add(monster.baseType.lowercase())
        tags.add("base:${monster.baseType.lowercase()}")
        tags.add(monster.family.lowercase())
        tags.add("family:${monster.family.lowercase()}")
        tags.addAll(monster.questTags.map { it.lowercase() })
        tags.add(monster.rarity.name.lowercase())
        if (monster.rarity.ordinal >= rpg.monster.MonsterRarity.ELITE.ordinal) {
            tags.add("elite")
        }
        if (isBoss) {
            tags.add("boss")
            tags.add("elite")
        }
        var updated = engine.questProgressTracker.onMonsterKilled(
            board = board,
            monsterId = monster.archetypeId,
            monsterBaseType = monster.baseType,
            monsterTags = tags,
            amount = 1
        )
        for ((itemId, qty) in outcome.collectedItems) {
            updated = engine.questProgressTracker.onItemCollected(
                board = updated,
                itemId = itemId,
                quantity = qty
            )
        }
        return updated
    }

    private fun applyRoomEffect(player: PlayerState, multiplier: Double, rooms: Int): PlayerState {
        if (multiplier < 1.0 && player.ignoreNextDebuff) {
            return player.copy(ignoreNextDebuff = false)
        }
        return player.copy(roomEffectMultiplier = multiplier, roomEffectRooms = rooms)
    }

    private fun applyHealing(
        player: PlayerState,
        hpDelta: Double,
        mpDelta: Double,
        itemInstances: Map<String, rpg.model.ItemInstance> = emptyMap()
    ): PlayerState {
        val stats = computePlayerStats(player, itemInstances)
        val multiplier = player.nextHealMultiplier
        val newHp = (player.currentHp + hpDelta * multiplier).coerceAtMost(stats.derived.hpMax)
        val newMp = (player.currentMp + mpDelta * multiplier).coerceAtMost(stats.derived.mpMax)
        val consumed = multiplier != 1.0 && (hpDelta > 0.0 || mpDelta > 0.0)
        return if (consumed) {
            player.copy(currentHp = newHp, currentMp = newMp, nextHealMultiplier = 1.0)
        } else {
            player.copy(currentHp = newHp, currentMp = newMp)
        }
    }

    private fun clearRunEffects(player: PlayerState): PlayerState {
        return player.copy(
            roomEffectMultiplier = 1.0,
            roomEffectRooms = 0,
            roomAttrBonus = Attributes(),
            roomAttrRooms = 0,
            roomDerivedAdd = DerivedStats(),
            roomDerivedMult = DerivedStats(),
            roomDerivedRooms = 0,
            runAttrBonus = Attributes(),
            runDerivedAdd = DerivedStats(),
            runDerivedMult = DerivedStats(),
            runAttrMultiplier = 1.0,
            nextHealMultiplier = 1.0,
            ignoreNextDebuff = false,
            reviveOnce = false,
            roomRegenHpPct = 0.0,
            roomRegenHpRooms = 0,
            roomRegenMpPct = 0.0,
            roomRegenMpRooms = 0,
            roomAttrRollRooms = 0,
            roomAttrRollAmount = 0
        )
    }

    private fun promptContinue(): Boolean {
        println("\n1. Continuar")
        println("2. Sair da dungeon")
        println("x. Voltar")
        return readMenuChoice("Escolha: ", 1, 2) == 1
    }

    private fun autoSave(state: GameState) {
        val path = Paths.get("saves", "autosave.json")
        val synced = synchronizeClock(state)
        JsonStore.save(path, synced.copy(currentRun = null))
    }

    private fun saveGame(state: GameState) {
        val name = readNonEmpty("Nome do save (ex: save1): ")
        val path = Paths.get("saves", "$name.json")
        val synced = synchronizeClock(state)
        JsonStore.save(path, synced.copy(currentRun = null))
        println("Save criado em ${path.fileName}.")
    }
    private inner class CliCombatController : rpg.combat.PlayerCombatController {
        private var lastRenderEpochMs: Long = 0L
        private var lastSignature: String = ""
        private var decisionActive: Boolean = false
        private var decisionView: DecisionView = DecisionView.MAIN
        private var decisionConsumables: List<String> = emptyList()
        private var decisionSkills: List<CombatSkillOption> = emptyList()
        private val combatHistory: ArrayDeque<String> = ArrayDeque()
        private val combatHistoryLimit = 6
        private val combatLogHeight = 6
        private val combatMenuHeight = 12
        private val combatBlockHeight = 6 + 1 + combatLogHeight + combatMenuHeight
        private var combatBlockInitialized: Boolean = false
        private var transientLinesBelowBlock: Int = 0

        override fun onFrame(snapshot: rpg.combat.CombatSnapshot) {
            val now = System.currentTimeMillis()
            val signature = combatFrameSignature(snapshot)
            val unchanged = signature == lastSignature
            val minIntervalMs = if (snapshot.pausedForDecision) 0L else 140L

            if (snapshot.pausedForDecision && unchanged) return
            if (unchanged && now - lastRenderEpochMs < minIntervalMs) return
            if (now - lastRenderEpochMs < minIntervalMs) return

            renderCombatFrameTracked(snapshot, signature, now)
        }

        override fun onDecisionStarted(snapshot: rpg.combat.CombatSnapshot) {
            if (decisionActive) return
            decisionActive = true
            decisionView = DecisionView.MAIN
            decisionConsumables = emptyList()
            decisionSkills = buildCombatSkillOptions(snapshot)
            renderCombatFrameTracked(snapshot)
        }

        override fun onDecisionEnded() {
            decisionActive = false
            decisionView = DecisionView.MAIN
            decisionConsumables = emptyList()
            decisionSkills = emptyList()
        }

        fun onCombatEvent(rawMessage: String) {
            val message = rawMessage.trimEnd()
            if (message.isBlank()) return
            appendCombatHistory(message)
        }

        override fun pollAction(snapshot: rpg.combat.CombatSnapshot): rpg.combat.CombatAction? {
            if (!decisionActive) {
                onDecisionStarted(snapshot)
            }
            renderCombatFrameTracked(snapshot)
            val input = (readLine()?.trim() ?: throw InputClosedException()).lowercase()
            transientLinesBelowBlock = 1
            return when (decisionView) {
                DecisionView.MAIN -> parseMainDecisionInput(input, snapshot)
                DecisionView.ATTACK -> parseAttackDecisionInput(input, snapshot)
                DecisionView.ITEM -> parseItemDecisionInput(input)
            }
        }

        private fun parseMainDecisionInput(
            input: String,
            snapshot: rpg.combat.CombatSnapshot
        ): rpg.combat.CombatAction? {
            return when (input) {
                "1" -> {
                    decisionView = DecisionView.ATTACK
                    null
                }
                "2" -> {
                    val consumables = snapshot.player.inventory.filter { id ->
                        engine.itemResolver.resolve(id, snapshot.itemInstances)?.type == ItemType.CONSUMABLE
                    }
                    if (consumables.isEmpty()) {
                        appendCombatHistory("Nenhum consumivel disponivel.")
                        null
                    } else {
                        decisionConsumables = consumables
                        decisionView = DecisionView.ITEM
                        null
                    }
                }
                "3" -> {
                    rpg.combat.CombatAction.Escape
                }
                else -> {
                    appendCombatHistory("Opcao invalida.")
                    null
                }
            }
        }

        private fun parseAttackDecisionInput(
            input: String,
            snapshot: rpg.combat.CombatSnapshot
        ): rpg.combat.CombatAction? {
            if (input == "x") {
                decisionView = DecisionView.MAIN
                return null
            }
            val actions = buildAttackDecisionActions(snapshot)
            val index = input.toIntOrNull()
            if (index == null || index !in 1..actions.size) {
                appendCombatHistory("Opcao invalida.")
                return null
            }
            return when (val action = actions[index - 1]) {
                is CombatMenuAction.BasicAttack -> {
                    if (!action.available) {
                        appendCombatHistory(action.unavailableReason ?: "Ataque indisponivel.")
                        null
                    } else {
                        rpg.combat.CombatAction.Attack(preferMagic = action.preferMagic)
                    }
                }
                is CombatMenuAction.SkillAttack -> {
                    if (!action.skill.available) {
                        appendCombatHistory(action.skill.unavailableReason ?: "Habilidade indisponivel.")
                        null
                    } else {
                        rpg.combat.CombatAction.Skill(
                            spec = rpg.combat.CombatSkillSpec(
                                id = action.skill.id,
                                name = action.skill.name,
                                mpCost = action.skill.mpCost,
                                cooldownSeconds = action.skill.cooldownSeconds,
                                damageMultiplier = action.skill.damageMultiplier,
                                preferMagic = action.skill.preferMagic,
                                castTimeSeconds = action.skill.castTimeSeconds,
                                onHitStatuses = action.skill.onHitStatuses,
                                selfHealFlat = action.skill.selfHealFlat,
                                selfHealPctMaxHp = action.skill.selfHealPctMaxHp,
                                ammoCost = action.skill.ammoCost,
                                rank = action.skill.rank,
                                aoeUnlockRank = action.skill.aoeUnlockRank,
                                aoeBonusDamagePct = action.skill.aoeBonusDamagePct
                            )
                        )
                    }
                }
            }
        }

        private fun parseItemDecisionInput(input: String): rpg.combat.CombatAction? {
            if (input == "x") {
                decisionView = DecisionView.MAIN
                return null
            }
            val index = input.toIntOrNull()
            if (index == null || index !in 1..decisionConsumables.size) {
                appendCombatHistory("Opcao invalida.")
                return null
            }
            val itemId = decisionConsumables[index - 1]
            return rpg.combat.CombatAction.UseItem(itemId)
        }

        private fun mainDecisionMenuLines(): List<String> {
            return listOf(
                combatColor("Voce esta pronto para agir.", ansiCombatPause),
                "1. Atacar",
                "2. Usar item",
                "3. Fugir",
                "Escolha: "
            )
        }

        private fun attackDecisionMenuLines(snapshot: rpg.combat.CombatSnapshot): List<String> {
            val actions = buildAttackDecisionActions(snapshot)
            val lines = mutableListOf<String>()
            lines += "Ataques:"
            rangedAmmoStatusLine(snapshot)?.let { lines += it }
            actions.forEachIndexed { index, action ->
                val label = when (action) {
                    is CombatMenuAction.BasicAttack -> {
                        val base = "Ataque Basico"
                        if (action.available) base else "$base [${action.unavailableReason}]"
                    }
                    is CombatMenuAction.SkillAttack -> {
                        val cost = format(action.skill.mpCost)
                        val castLabel = if (action.skill.castTimeSeconds > 0.0) {
                            " | Cast ${format(action.skill.castTimeSeconds)}s"
                        } else {
                            ""
                        }
                        val healLabel = when {
                            action.skill.selfHealPctMaxHp > 0.0 -> " | Cura ${format(action.skill.selfHealPctMaxHp)}% HP"
                            action.skill.selfHealFlat > 0.0 -> " | Cura ${format(action.skill.selfHealFlat)} HP"
                            else -> ""
                        }
                        val rankLabel = if (action.skill.maxRank > 1) " | Rank ${action.skill.rank}/${action.skill.maxRank}" else ""
                        val aoeLabel = if (action.skill.aoeUnlockRank > 0) {
                            val status = if (action.skill.rank >= action.skill.aoeUnlockRank) "AOE ativo" else "AOE no rank ${action.skill.aoeUnlockRank}"
                            " | $status"
                        } else {
                            ""
                        }
                        val base = "${action.skill.name} ($cost MP | CD ${format(action.skill.cooldownSeconds)}s$castLabel$healLabel$rankLabel$aoeLabel)"
                        if (action.skill.available) {
                            base
                        } else {
                            "$base [${action.skill.unavailableReason}]"
                        }
                    }
                }
                lines += "${index + 1}. $label"
            }
            lines += "x. Voltar"
            lines += "Escolha: "
            return lines
        }

        private fun buildAttackDecisionActions(snapshot: rpg.combat.CombatSnapshot): List<CombatMenuAction> {
            val actions = mutableListOf<CombatMenuAction>()
            val ammoRestriction = rangedAmmoRestriction(snapshot)
            actions += CombatMenuAction.BasicAttack(
                preferMagic = inferBasicAttackMagic(snapshot),
                available = ammoRestriction == null,
                unavailableReason = ammoRestriction
            )
            decisionSkills = buildCombatSkillOptions(snapshot)
            decisionSkills.forEach { skill ->
                actions += CombatMenuAction.SkillAttack(skill)
            }
            return actions
        }

        private fun buildCombatSkillOptions(snapshot: rpg.combat.CombatSnapshot): List<CombatSkillOption> {
            val basicPreferMagic = inferBasicAttackMagic(snapshot)
            val unlocked = collectV2CombatSkillOptions(snapshot, basicPreferMagic).distinctBy { it.id }
            if (unlocked.isEmpty()) return emptyList()

            val stateReady = snapshot.playerRuntime.state == rpg.combat.CombatState.READY
            val gcdReady = snapshot.playerRuntime.gcdRemaining <= 0.0
            val stateValid = stateReady && gcdReady
            val ammoRestriction = rangedAmmoRestriction(snapshot)

            return unlocked.map { skill ->
                val cooldownRemain = snapshot.playerRuntime.skillCooldowns[skill.id] ?: 0.0
                val cooldownTurns = if (cooldownRemain <= 0.0) {
                    0
                } else {
                    ceil(cooldownRemain / repo.balance.combat.globalCooldownSeconds).toInt().coerceAtLeast(1)
                }
                val hasMana = snapshot.player.currentMp + 1e-6 >= skill.mpCost
                val available = stateValid && hasMana && cooldownRemain <= 0.0 && ammoRestriction == null
                val unavailableReason = when {
                    ammoRestriction != null -> ammoRestriction
                    !stateReady -> "Estado: ${combatStateLabel(snapshot.playerRuntime.state)}"
                    !gcdReady -> "GCD: ${format(snapshot.playerRuntime.gcdRemaining)}s"
                    !hasMana -> "Sem mana"
                    cooldownRemain > 0.0 -> "Cooldown: ${cooldownTurns} turnos"
                    else -> null
                }
                skill.copy(
                    available = available,
                    unavailableReason = unavailableReason,
                    cooldownRemainingSeconds = cooldownRemain
                )
            }
        }

        private fun collectV2CombatSkillOptions(
            snapshot: rpg.combat.CombatSnapshot,
            basicPreferMagic: Boolean?
        ): List<CombatSkillOption> {
            val player = snapshot.player
            val trees = talentTreeService.activeTrees(player, repo.talentTreesV2.values)
            if (trees.isEmpty()) return emptyList()

            val options = mutableListOf<CombatSkillOption>()
            for (tree in trees) {
                for (node in tree.nodes) {
                    if (node.nodeType != TalentNodeType.ACTIVE_SKILL) continue
                    val rank = talentTreeService.nodeCurrentRank(player, node)
                    if (rank <= 0) continue

                    val combat = node.modifiers.combat
                    val preferMagic = resolveNodePreferMagic(
                        node = node,
                        basicPreferMagic = basicPreferMagic
                    )
                    val damageBias = if (preferMagic == true) {
                        node.modifiers.bonuses.derivedMult.damageMagic +
                            node.modifiers.bonuses.derivedAdd.damageMagic +
                            node.modifiers.bonuses.attributes.`int` * 0.6 +
                            node.modifiers.bonuses.attributes.spr * 0.3
                    } else {
                        node.modifiers.bonuses.derivedMult.damagePhysical +
                            node.modifiers.bonuses.derivedAdd.damagePhysical +
                            node.modifiers.bonuses.attributes.str * 0.6 +
                            node.modifiers.bonuses.attributes.dex * 0.3
                    }

                    val rankFactor = rank.toDouble().coerceAtLeast(1.0)
                    val pointCost = node.costPerRank
                        .getOrElse((rank - 1).coerceAtMost(node.costPerRank.lastIndex)) { node.costPerRank.lastOrNull() ?: 1 }
                        .coerceAtLeast(1)

                    val mpBase = combat["mpCost"] ?: combat["manaCost"] ?: (4.0 + pointCost * 3.2 + rankFactor * 1.1)
                    val mpGrowth = combat["mpCostPerRank"] ?: combat["manaCostPerRank"] ?: 0.0
                    val cooldownBase = combat["cooldownSeconds"] ?: combat["cooldown"] ?: (0.8 + pointCost * 0.45)
                    val cooldownGrowth = combat["cooldownPerRank"] ?: 0.0
                    val dmgBase = combat["damageMultiplier"] ?: combat["multiplier"] ?: (1.05 + damageBias.coerceAtLeast(0.0) / 100.0)
                    val dmgGrowth = combat["damageMultiplierPerRank"] ?: combat["multiplierPerRank"] ?: 0.0
                    val castBase = combat["castTimeSeconds"] ?: combat["cast"] ?: 0.0
                    val castGrowth = combat["castTimePerRank"] ?: 0.0
                    val healFlatBase = combat["selfHealFlat"] ?: combat["healFlat"] ?: 0.0
                    val healFlatGrowth = combat["selfHealFlatPerRank"] ?: combat["healFlatPerRank"] ?: 0.0
                    val healPctBase = combat["selfHealPctMaxHp"] ?: combat["healPctMaxHp"] ?: 0.0
                    val healPctGrowth = combat["selfHealPctMaxHpPerRank"] ?: combat["healPctMaxHpPerRank"] ?: 0.0
                    val ammoCostBase = (combat["ammoCost"] ?: 1.0).toInt().coerceAtLeast(1)
                    val ammoCostGrowth = (combat["ammoCostPerRank"] ?: 0.0).toInt()

                    val skillId = node.unlocksSkillId?.takeIf { it.isNotBlank() } ?: node.id
                    val statusChancePerRank = combat["statusChancePerRankPct"] ?: 0.0
                    val statusDurationPerRank = combat["statusDurationPerRankSeconds"] ?: 0.0
                    val statusEffectPerRank = combat["statusEffectPerRank"] ?: 0.0
                    val statusStacksPerRank = combat["statusMaxStacksPerRank"] ?: 0.0
                    val onHitStatuses = node.modifiers.applyStatuses.map { status ->
                        val deltaRank = (rankFactor - 1.0).coerceAtLeast(0.0)
                        status.copy(
                            chancePct = (status.chancePct + statusChancePerRank * deltaRank).coerceIn(0.0, 100.0),
                            durationSeconds = (status.durationSeconds + statusDurationPerRank * deltaRank).coerceAtLeast(0.1),
                            effectValue = (status.effectValue + statusEffectPerRank * deltaRank).coerceAtLeast(0.0),
                            maxStacks = (status.maxStacks + (statusStacksPerRank * deltaRank).toInt()).coerceAtLeast(1)
                        )
                    }
                    options += CombatSkillOption(
                        id = skillId,
                        name = node.name,
                        mpCost = (mpBase + (rankFactor - 1.0) * mpGrowth).coerceAtLeast(0.0),
                        cooldownSeconds = (cooldownBase + (rankFactor - 1.0) * cooldownGrowth).coerceAtLeast(0.0),
                        castTimeSeconds = (castBase + (rankFactor - 1.0) * castGrowth).coerceAtLeast(0.0),
                        damageMultiplier = (dmgBase + (rankFactor - 1.0) * dmgGrowth).coerceAtLeast(0.1),
                        preferMagic = preferMagic,
                        onHitStatuses = onHitStatuses,
                        selfHealFlat = (healFlatBase + (rankFactor - 1.0) * healFlatGrowth).coerceAtLeast(0.0),
                        selfHealPctMaxHp = (healPctBase + (rankFactor - 1.0) * healPctGrowth).coerceAtLeast(0.0),
                        ammoCost = (ammoCostBase + ((rank - 1).coerceAtLeast(0) * ammoCostGrowth)).coerceAtLeast(1),
                        rank = rank,
                        maxRank = node.maxRank.coerceAtLeast(1),
                        aoeUnlockRank = (combat["aoeUnlockRank"] ?: 0.0).toInt().coerceAtLeast(0),
                        aoeBonusDamagePct = (combat["aoeBonusDamagePct"] ?: 0.0).coerceAtLeast(0.0),
                        available = false,
                        unavailableReason = null,
                        cooldownRemainingSeconds = 0.0
                    )
                }
            }
            return options
        }

        private fun resolveNodePreferMagic(
            node: rpg.model.TalentNode,
            basicPreferMagic: Boolean?
        ): Boolean? {
            node.modifiers.combat["damageTypeMagic"]?.let { return it > 0.0 }

            val magicBias = node.modifiers.bonuses.derivedMult.damageMagic +
                node.modifiers.bonuses.derivedAdd.damageMagic +
                node.modifiers.bonuses.attributes.`int` +
                node.modifiers.bonuses.attributes.spr
            val physicalBias = node.modifiers.bonuses.derivedMult.damagePhysical +
                node.modifiers.bonuses.derivedAdd.damagePhysical +
                node.modifiers.bonuses.attributes.str +
                node.modifiers.bonuses.attributes.dex
            if (magicBias > physicalBias) return true
            if (physicalBias > magicBias) return false
            return basicPreferMagic
        }

        private fun inferBasicAttackMagic(snapshot: rpg.combat.CombatSnapshot): Boolean? {
            val player = snapshot.player
            var score = 0
            val magicClasses = setOf(
                "mage",
                "arcanist",
                "elementalist",
                "archmage",
                "cleric",
                "pyromancer",
                "elemental_master"
            )
            val physicalClasses = setOf(
                "swordman",
                "warrior",
                "archer",
                "barbarian",
                "hunter",
                "ranger",
                "bounty_hunter",
                "assassin",
                "sharpshooter",
                "shadow_hunter",
                "paladin",
                "elite_guard",
                "predator",
                "berserker"
            )
            if (player.classId in magicClasses) score += 2
            if (player.classId in physicalClasses) score -= 2
            player.subclassId?.let {
                if (it in magicClasses) score += 2
                if (it in physicalClasses) score -= 2
            }
            val mainWeaponId = player.equipped[EquipSlot.WEAPON_MAIN.name]
            if (!mainWeaponId.isNullOrBlank()) {
                val weapon = engine.itemResolver.resolve(mainWeaponId, snapshot.itemInstances)
                val source = "${weapon?.id.orEmpty()} ${weapon?.name.orEmpty()} ${weapon?.tags?.joinToString(" ").orEmpty()}".lowercase()
                if (listOf("staff", "cajado", "scepter", "cetro", "wand", "arcane", "magic", "magico").any { it in source }) {
                    score += 3
                }
                if (listOf("sword", "espada", "axe", "machado", "bow", "arco", "lanca", "spear").any { it in source }) {
                    score -= 2
                }
            }
            if (snapshot.playerStats.derived.damageMagic > snapshot.playerStats.derived.damagePhysical * 1.1) {
                score += 1
            } else if (snapshot.playerStats.derived.damagePhysical > snapshot.playerStats.derived.damageMagic * 1.1) {
                score -= 1
            }
            return when {
                score > 0 -> true
                score < 0 -> false
                else -> null
            }
        }

        private fun rangedAmmoRestriction(snapshot: rpg.combat.CombatSnapshot): String? {
            if (!playerUsesBow(snapshot)) return null
            if (snapshot.player.equipped[EquipSlot.ALJAVA.name].isNullOrBlank()) {
                return "Sem aljava equipada"
            }
            if (countArrows(snapshot) <= 0) {
                return "Sem flechas"
            }
            return null
        }

        private fun rangedAmmoStatusLine(snapshot: rpg.combat.CombatSnapshot): String? {
            if (!playerUsesBow(snapshot)) return null
            val normalizedPlayer = InventorySystem.normalizeAmmoStorage(snapshot.player, snapshot.itemInstances, engine.itemRegistry)
            val quiverName = snapshot.player.equipped[EquipSlot.ALJAVA.name]?.let { quiverId ->
                engine.itemResolver.resolve(quiverId, snapshot.itemInstances)?.name ?: quiverId
            } ?: "nenhuma"
            val quiverCapacity = InventorySystem.quiverCapacity(normalizedPlayer, snapshot.itemInstances, engine.itemRegistry)
            val reserve = InventorySystem.inventoryArrowReserveCount(normalizedPlayer, snapshot.itemInstances, engine.itemRegistry)
            val activeAmmo = normalizedPlayer.selectedAmmoTemplateId?.let { templateId ->
                buildAmmoStacks(normalizedPlayer.quiverInventory, snapshot.itemInstances, normalizedPlayer.selectedAmmoTemplateId)
                    .firstOrNull { it.templateId == templateId }
                    ?.item
                    ?.name
            } ?: "-"
            return "Municao: ${countArrows(snapshot)}/$quiverCapacity flecha(s) | Reserva: $reserve | Ativa: $activeAmmo | Aljava: $quiverName"
        }

        private fun playerUsesBow(snapshot: rpg.combat.CombatSnapshot): Boolean {
            val mainWeaponId = snapshot.player.equipped[EquipSlot.WEAPON_MAIN.name] ?: return false
            val weapon = engine.itemResolver.resolve(mainWeaponId, snapshot.itemInstances) ?: return false
            val normalizedTags = weapon.tags.mapTo(mutableSetOf()) { it.trim().lowercase() }
            if ("bow" in normalizedTags) return true
            val source = "${weapon.id} ${weapon.name}".lowercase()
            return "bow" in source || "arco" in source
        }

        private fun countArrows(snapshot: rpg.combat.CombatSnapshot): Int {
            val normalizedPlayer = InventorySystem.normalizeAmmoStorage(snapshot.player, snapshot.itemInstances, engine.itemRegistry)
            return InventorySystem.quiverAmmoCount(normalizedPlayer, snapshot.itemInstances, engine.itemRegistry)
        }

        private fun itemDecisionMenuLines(itemInstances: Map<String, rpg.model.ItemInstance>): List<String> {
            val lines = mutableListOf<String>()
            lines += "Consumiveis:"
            decisionConsumables.forEachIndexed { index, itemId ->
                val name = engine.itemResolver.resolve(itemId, itemInstances)?.name ?: itemId
                lines += "${index + 1}. $name"
            }
            lines += "x. Voltar"
            lines += "Escolha: "
            return lines
        }

        private fun renderCombatFrameTracked(
            snapshot: rpg.combat.CombatSnapshot,
            signature: String = combatFrameSignature(snapshot),
            now: Long = System.currentTimeMillis()
        ) {
            lastRenderEpochMs = now
            lastSignature = signature
            renderCombatFrame(snapshot)
        }

        private fun renderCombatFrame(snapshot: rpg.combat.CombatSnapshot) {
            val playerBar = combatActionBar(snapshot.playerRuntime)
            val monsterBar = combatActionBar(snapshot.monsterRuntime)
            val playerState = combatStateLabel(snapshot.playerRuntime.state)
            val monsterState = combatStateLabel(snapshot.monsterRuntime.state)
            val enemyName = engine.monsterDisplayName(snapshot.monster)
            val playerHp = "${format(snapshot.player.currentHp)} / ${format(snapshot.playerStats.derived.hpMax)}"
            val playerMp = "${format(snapshot.player.currentMp)} / ${format(snapshot.playerStats.derived.mpMax)}"
            val monsterHp = "${format(snapshot.monsterHp)} / ${format(snapshot.monsterStats.derived.hpMax)}"
            val lineOne = buildString {
                append("Voce    ")
                append(playerBar)
                append(" ")
                append(combatColor(playerState, combatStateColor(snapshot.playerRuntime.state)))
            }
            val lineTwo = buildString {
                append("Inimigo ")
                append(monsterBar)
                append(" ")
                append(combatColor(monsterState, combatStateColor(snapshot.monsterRuntime.state)))
            }
            val lines = mutableListOf<String>()
            lines += combatColor("Combate | $enemyName", ansiCombatHeader)
            lines += "Voce    HP ${combatColor(playerHp, ansiCombatPlayer)} | MP ${combatColor(playerMp, ansiCombatCasting)}"
            lines += "Inimigo HP ${combatColor(monsterHp, ansiCombatEnemy)}"
            lines += "$lineOne"
            lines += "$lineTwo"
            lines += if (snapshot.pausedForDecision) {
                combatColor("Aguardando sua acao.", ansiCombatPause)
            } else {
                ""
            }
            lines += "Historico:"
            val recentLog = combatHistory.toList().takeLast(combatLogHeight)
            repeat(combatLogHeight) { index ->
                val line = recentLog.getOrNull(index).orEmpty()
                lines += if (line.isBlank()) "" else "- $line"
            }
            val menuLines = buildDecisionSectionLines(snapshot)
            lines += menuLines
            redrawCombatBlock(lines)
        }

        private fun clearCombatHistory() {
            combatHistory.clear()
        }

        private fun appendCombatHistory(message: String) {
            combatHistory.addLast(message)
            while (combatHistory.size > combatHistoryLimit) {
                combatHistory.removeFirst()
            }
        }

        private fun buildDecisionSectionLines(snapshot: rpg.combat.CombatSnapshot): List<String> {
            if (!snapshot.pausedForDecision || !decisionActive) {
                return normalizeFixedWindow(
                    listOf(""),
                    combatMenuHeight
                )
            }
            val rawLines = when (decisionView) {
                DecisionView.MAIN -> mainDecisionMenuLines()
                DecisionView.ATTACK -> attackDecisionMenuLines(snapshot)
                DecisionView.ITEM -> itemDecisionMenuLines(snapshot.itemInstances)
            }
            return normalizeFixedWindow(rawLines, combatMenuHeight)
        }

        private fun normalizeFixedWindow(lines: List<String>, height: Int): List<String> {
            if (height <= 0) return emptyList()
            val normalized = lines.toMutableList()
            if (normalized.size > height) {
                val keep = (height - 1).coerceAtLeast(1)
                val hidden = (normalized.size - keep).coerceAtLeast(0)
                val trimmed = normalized.take(keep).toMutableList()
                trimmed += "... ($hidden linha(s) ocultas)"
                return trimmed
            }
            while (normalized.size < height) {
                normalized += ""
            }
            return normalized
        }

        private fun redrawCombatBlock(rawLines: List<String>) {
            val lines = if (rawLines.size == combatBlockHeight) {
                rawLines
            } else {
                normalizeFixedWindow(rawLines, combatBlockHeight)
            }
            if (combatBlockInitialized) {
                moveCursorUp(combatBlockHeight + transientLinesBelowBlock)
            }
            for (line in lines) {
                print('\r')
                print(ansiClearLine)
                print(line)
                print('\n')
            }
            print('\r')
            print(ansiClearLine)
            System.out.flush()
            combatBlockInitialized = true
            transientLinesBelowBlock = 0
        }

        private fun moveCursorUp(lines: Int) {
            if (lines <= 0) return
            print("\u001B[${lines}A")
        }

        fun finalizeDisplay() {
            if (!combatBlockInitialized) {
                clearCombatHistory()
                return
            }
            moveCursorUp(combatBlockHeight + transientLinesBelowBlock)
            print(ansiClearToEnd)
            System.out.flush()
            combatBlockInitialized = false
            transientLinesBelowBlock = 0
            clearCombatHistory()
        }

        private fun combatFrameSignature(snapshot: rpg.combat.CombatSnapshot): String {
            val playerPct = ((snapshot.playerRuntime.actionBar / snapshot.playerRuntime.actionThreshold) * 100.0).toInt()
            val monsterPct = ((snapshot.monsterRuntime.actionBar / snapshot.monsterRuntime.actionThreshold) * 100.0).toInt()
            val playerCast = snapshot.playerRuntime.castRemaining.toInt()
            val monsterCast = snapshot.monsterRuntime.castRemaining.toInt()
            val playerGcd = snapshot.playerRuntime.gcdRemaining.toInt()
            val monsterGcd = snapshot.monsterRuntime.gcdRemaining.toInt()
            return listOf(
                format(snapshot.player.currentHp),
                format(snapshot.player.currentMp),
                format(snapshot.monsterHp),
                playerPct.toString(),
                monsterPct.toString(),
                snapshot.playerRuntime.state.name,
                snapshot.monsterRuntime.state.name,
                playerCast.toString(),
                monsterCast.toString(),
                playerGcd.toString(),
                monsterGcd.toString(),
                snapshot.pausedForDecision.toString()
            ).joinToString("|")
        }

    }

    private fun battleMonster(
        playerState: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        monster: MonsterInstance,
        tier: rpg.model.MapTierDef,
        lootCollector: MutableList<String>?,
        isBoss: Boolean,
        classDungeon: ClassQuestDungeonDefinition? = null
    ): BattleOutcome {
        val bossLabel = if (isBoss) "BOSS " else ""
        val displayName = engine.monsterDisplayName(monster)
        println("\n$bossLabel$displayName apareceu!")
        if (monster.onHitStatuses.isNotEmpty()) {
            val statusInfo = monster.onHitStatuses.joinToString(" | ") {
                "${rpg.status.StatusSystem.displayName(it.type)} ${format(it.chancePct)}%"
            }
            println("Efeitos no golpe do inimigo: $statusInfo")
        }

        val combatController = CliCombatController()
        val result = engine.combatEngine.runBattle(
            playerState = playerState,
            itemInstances = itemInstances,
            monster = monster,
            tier = tier,
            displayName = displayName,
            controller = combatController,
            eventLogger = { message -> combatController.onCombatEvent(message) }
        )
        combatController.finalizeDisplay()
        println()

        var player = result.playerAfter
        var instances = result.itemInstances
        player = applyAchievementUpdate(
            achievementTracker.onBattleResolved(
                player = player,
                telemetry = result.telemetry,
                victory = result.victory,
                escaped = result.escaped,
                isBoss = isBoss,
                monsterBaseType = monster.baseType,
                monsterStars = monster.stars
            )
        )

        if (result.escaped) {
            return BattleOutcome(player, instances, victory = false, escaped = true)
        }
        if (!result.victory) {
            return BattleOutcome(player, instances, victory = false)
        }

        println("$displayName foi derrotado!")
        val levelBefore = player.level
        val victory = engine.resolveVictory(player, instances, monster, tier, collectToLoot = lootCollector != null)
        player = victory.player
        instances = victory.itemInstances
        player = applyAchievementUpdate(
            achievementTracker.onGoldEarned(player, victory.goldGain.toLong())
        )
        val collectedItems = mutableMapOf<String, Int>()
        println("Ganhou ${victory.xpGain} XP e ${victory.goldGain} ouro.")

        if (player.level > levelBefore) {
            println("\nLevel up! Agora voce esta no nivel ${player.level}.")
        }

        if (lootCollector != null) {
            val outcome = victory.dropOutcome
            if (outcome.itemInstance != null) {
                lootCollector.add(outcome.itemInstance.id)
                val canonicalId = outcome.itemInstance.templateId
                collectedItems[canonicalId] = (collectedItems[canonicalId] ?: 0) + 1
                println("Drop encontrado: ${itemDisplayLabel(outcome.itemInstance.name, outcome.itemInstance.rarity)}")
            } else if (outcome.itemId != null) {
                val resolved = engine.itemResolver.resolve(outcome.itemId, instances)
                val name = resolved?.let(::itemDisplayLabel) ?: outcome.itemId
                val qty = outcome.quantity.coerceAtLeast(1)
                repeat(qty) { lootCollector.add(outcome.itemId) }
                collectedItems[outcome.itemId] = (collectedItems[outcome.itemId] ?: 0) + qty
                val label = if (qty > 1) "$name x$qty" else name
                println("Drop encontrado: $label")
            }
        } else {
            val outcome = victory.dropOutcome
            if (outcome.itemInstance != null) {
                val canonicalId = outcome.itemInstance.templateId
                collectedItems[canonicalId] = (collectedItems[canonicalId] ?: 0) + 1
            } else if (outcome.itemId != null) {
                val qty = outcome.quantity.coerceAtLeast(1)
                collectedItems[outcome.itemId] = (collectedItems[outcome.itemId] ?: 0) + qty
            }
        }

        if (classDungeon != null && lootCollector != null) {
            val collectibleDrops = engine.classQuestService.collectibleDropsForDungeonKill(
                player = player,
                monsterId = monster.archetypeId,
                isBoss = isBoss
            )
            if (collectibleDrops.isNotEmpty()) {
                val updatedInstances = instances.toMutableMap()
                for (drop in collectibleDrops) {
                    updatedInstances[drop.id] = drop
                    lootCollector.add(drop.id)
                    val canonicalId = drop.templateId
                    collectedItems[canonicalId] = (collectedItems[canonicalId] ?: 0) + 1
                }
                instances = updatedInstances.toMap()
                val grouped = collectibleDrops.groupingBy { it.templateId }.eachCount()
                for ((templateId, qty) in grouped) {
                    val name = collectibleDrops.firstOrNull { it.templateId == templateId }?.name ?: templateId
                    val label = if (qty > 1) "$name x$qty" else name
                    println("Drop exclusivo da instancia: $label")
                }
            }
        }

        val classQuestUpdate = engine.classQuestService.onCombatOutcome(
            player = player,
            itemInstances = instances,
            monsterId = monster.archetypeId,
            isBoss = isBoss,
            monsterBaseType = monster.baseType
        )
        classQuestUpdate.messages.forEach { println(it) }
        val classQuestGold = (classQuestUpdate.player.gold - player.gold).coerceAtLeast(0)
        player = classQuestUpdate.player
        if (classQuestGold > 0) {
            player = applyAchievementUpdate(
                achievementTracker.onGoldEarned(player, classQuestGold.toLong())
            )
        }
        instances = classQuestUpdate.itemInstances

        return BattleOutcome(
            playerAfter = player,
            itemInstances = instances,
            victory = true,
            collectedItems = collectedItems
        )
    }

    private fun openShop(state: GameState): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        val entries = repo.shopEntries.values
            .filter { it.enabled && it.currency == ShopCurrency.GOLD }
            .sortedWith(compareBy({ it.minPlayerLevel }, { it.price }, { it.name }))
        if (entries.isEmpty()) {
            println("Loja de ouro sem itens configurados.")
            return state
        }

        while (true) {
            println("\n=== Loja de Ouro ===")
            println("Ouro atual: ${player.gold}")
            entries.forEachIndexed { index, entry ->
                val reqLevel = requiredLevelForShopEntry(entry)
                val itemName = shopEntryItemName(entry)
                val lock = if (player.level < reqLevel) " [bloqueado]" else ""
                println(
                    "${index + 1}. $itemName x${entry.quantity} - ${entry.price} ouro " +
                        "(lvl req $reqLevel)$lock"
                )
            }
            println("x. Voltar")

            val choice = readMenuChoice("Escolha: ", 1, entries.size)
            if (choice == null) {
                val updated = state.copy(player = player, itemInstances = itemInstances)
                autoSave(updated)
                return updated
            }

            val selected = entries[choice - 1]
            if (!showShopEntryDetails(player, selected)) {
                continue
            }
            val purchase = buyShopEntry(player, itemInstances, selected)
            println(purchase.message)
            if (purchase.success) {
                var updatedPlayer = purchase.player
                val spentGold = (player.gold - updatedPlayer.gold).coerceAtLeast(0)
                if (spentGold > 0) {
                    updatedPlayer = applyAchievementUpdate(
                        achievementTracker.onGoldSpent(updatedPlayer, spentGold.toLong())
                    )
                }
                player = updatedPlayer
                itemInstances = purchase.itemInstances
                autoSave(state.copy(player = player, itemInstances = itemInstances))
            }
        }
    }

    private fun openCashShop(state: GameState): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        val entries = repo.shopEntries.values
            .filter { it.enabled && it.currency == ShopCurrency.CASH }
            .sortedWith(compareBy({ it.minPlayerLevel }, { it.price }, { it.name }))
        val packs = repo.cashPacks.values.filter { it.enabled }.sortedBy { it.premiumCashAmount }

        while (true) {
            println("\n=== Loja CASH ===")
            println("CASH atual: ${player.premiumCash}")
            println("1. Comprar pacote CASH (simulacao client-side)")
            println("2. Comprar itens CASH")
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> {
                    if (packs.isEmpty()) {
                        println("Nenhum pacote CASH configurado.")
                        continue
                    }
                    println("\nPacotes disponiveis:")
                    packs.forEachIndexed { index, pack ->
                        val label = if (pack.platformPriceLabel.isBlank()) "sem preco" else pack.platformPriceLabel
                        println("${index + 1}. ${pack.name} -> +${pack.premiumCashAmount} CASH ($label)")
                    }
                    println("x. Voltar")
                    val choice = readMenuChoice("Escolha: ", 1, packs.size) ?: continue
                    val chosen = packs[choice - 1]
                    player = player.copy(premiumCash = player.premiumCash + chosen.premiumCashAmount)
                    println("Compra simulada concluida: +${chosen.premiumCashAmount} CASH.")
                    autoSave(state.copy(player = player, itemInstances = itemInstances))
                }

                2 -> {
                    if (entries.isEmpty()) {
                        println("Nenhum item CASH configurado.")
                        continue
                    }
                    while (true) {
                        println("\nItens CASH (saldo: ${player.premiumCash}):")
                        entries.forEachIndexed { index, entry ->
                            val reqLevel = requiredLevelForShopEntry(entry)
                            val itemName = shopEntryItemName(entry)
                            val lock = if (player.level < reqLevel) " [bloqueado]" else ""
                            println(
                                "${index + 1}. $itemName x${entry.quantity} - ${entry.price} CASH " +
                                    "(lvl req $reqLevel)$lock"
                            )
                        }
                        println("x. Voltar")
                        val choice = readMenuChoice("Escolha: ", 1, entries.size) ?: break

                        val selected = entries[choice - 1]
                        if (!showShopEntryDetails(player, selected)) {
                            continue
                        }
                        val purchase = buyShopEntry(player, itemInstances, selected)
                        println(purchase.message)
                        if (purchase.success) {
                            player = purchase.player
                            itemInstances = purchase.itemInstances
                            autoSave(state.copy(player = player, itemInstances = itemInstances))
                        }
                    }
                }

                null -> {
                    val updated = state.copy(player = player, itemInstances = itemInstances)
                    autoSave(updated)
                    return updated
                }
            }
        }
    }

    private fun requiredLevelForShopEntry(entry: rpg.model.ShopEntryDef): Int {
        val itemReq = engine.itemRegistry.item(entry.itemId)?.minLevel
            ?: engine.itemRegistry.template(entry.itemId)?.minLevel
            ?: 1
        return max(entry.minPlayerLevel, itemReq)
    }

    private fun showShopEntryDetails(
        player: PlayerState,
        entry: rpg.model.ShopEntryDef
    ): Boolean {
        val itemName = shopEntryItemName(entry)
        val requiredLevel = requiredLevelForShopEntry(entry)
        val currencyLabel = if (entry.currency == ShopCurrency.GOLD) "ouro" else "CASH"
        val balance = if (entry.currency == ShopCurrency.GOLD) player.gold else player.premiumCash
        val description = shopEntryDescription(entry)
        println("\n=== Item da Loja ===")
        println("Item: $itemName x${entry.quantity.coerceAtLeast(1)}")
        println("Preco: ${entry.price} $currencyLabel")
        println("Saldo atual: $balance $currencyLabel")
        println("Nivel requerido: $requiredLevel")
        if (description.isNotBlank()) {
            println("Descricao: $description")
        }
        if (player.level < requiredLevel) {
            println("Status: bloqueado por nivel.")
        }
        println("1. Comprar")
        println("x. Voltar")
        return readMenuChoice("Escolha: ", 1, 1) == 1
    }

    private fun shopEntryItemName(entry: rpg.model.ShopEntryDef): String {
        val resolvedName = engine.itemRegistry.item(entry.itemId)?.name
            ?: engine.itemRegistry.template(entry.itemId)?.name
        return if (resolvedName.isNullOrBlank()) entry.name else resolvedName
    }

    private fun shopEntryDescription(entry: rpg.model.ShopEntryDef): String {
        if (entry.description.isNotBlank()) return entry.description
        return engine.itemRegistry.entry(entry.itemId)?.description ?: ""
    }

    private fun buyShopEntry(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        entry: rpg.model.ShopEntryDef
    ): ShopPurchaseResult {
        val requiredLevel = requiredLevelForShopEntry(entry)
        if (player.level < requiredLevel) {
            return ShopPurchaseResult(
                success = false,
                player = player,
                itemInstances = itemInstances,
                message = "Nivel insuficiente. Requer nivel $requiredLevel."
            )
        }

        when (entry.currency) {
            ShopCurrency.GOLD -> {
                if (player.gold < entry.price) {
                    return ShopPurchaseResult(false, player, itemInstances, "Ouro insuficiente.")
                }
            }

            ShopCurrency.CASH -> {
                if (player.premiumCash < entry.price) {
                    return ShopPurchaseResult(false, player, itemInstances, "CASH insuficiente.")
                }
            }
        }

        val qty = entry.quantity.coerceAtLeast(1)
        val workingInstances = itemInstances.toMutableMap()
        val incoming = mutableListOf<String>()

        repeat(qty) {
            if (engine.itemRegistry.isTemplate(entry.itemId)) {
                val template = engine.itemRegistry.template(entry.itemId)
                    ?: return ShopPurchaseResult(
                        false,
                        player,
                        itemInstances,
                        "Template de item invalido na loja: ${entry.itemId}."
                    )
                val generated = engine.itemEngine.generateFromTemplate(
                    template = template,
                    level = max(player.level, template.minLevel),
                    rarity = template.rarity
                )
                workingInstances[generated.id] = generated
                incoming += generated.id
            } else {
                if (engine.itemRegistry.item(entry.itemId) == null) {
                    return ShopPurchaseResult(
                        false,
                        player,
                        itemInstances,
                        "Item invalido na loja: ${entry.itemId}."
                    )
                }
                incoming += entry.itemId
            }
        }

        val insertion = InventorySystem.addItemsWithLimit(
            player = player,
            itemInstances = workingInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = incoming
        )
        if (insertion.rejected.isNotEmpty()) {
            return ShopPurchaseResult(
                success = false,
                player = player,
                itemInstances = itemInstances,
                message = "Inventario sem espaco para essa compra."
            )
        }

        var updatedPlayer = player.copy(
            inventory = insertion.inventory,
            quiverInventory = insertion.quiverInventory,
            selectedAmmoTemplateId = insertion.selectedAmmoTemplateId
        )
        updatedPlayer = when (entry.currency) {
            ShopCurrency.GOLD -> updatedPlayer.copy(gold = updatedPlayer.gold - entry.price)
            ShopCurrency.CASH -> updatedPlayer.copy(premiumCash = updatedPlayer.premiumCash - entry.price)
        }
        val currency = if (entry.currency == ShopCurrency.GOLD) "ouro" else "CASH"
        val itemName = shopEntryItemName(entry)
        return ShopPurchaseResult(
            success = true,
            player = updatedPlayer,
            itemInstances = workingInstances.toMap(),
            message = "Compra concluida: $itemName x$qty por ${entry.price} $currency."
        )
    }

    private fun openInventory(state: GameState): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        var inventoryFilter = InventoryFilter()

        while (true) {
            player = normalizePlayerStorage(player, itemInstances)
            val allStacks = buildInventoryStacks(player, itemInstances)
            val stacks = applyInventoryFilter(allStacks, inventoryFilter)
            val slotUsed = InventorySystem.slotsUsed(player, itemInstances, engine.itemRegistry)
            val slotLimit = InventorySystem.inventoryLimit(player, itemInstances, engine.itemRegistry)
            val quiverCapacity = InventorySystem.quiverCapacity(player, itemInstances, engine.itemRegistry)
            val quiverAmmo = InventorySystem.quiverAmmoCount(player, itemInstances, engine.itemRegistry)
            val reserveAmmo = InventorySystem.inventoryArrowReserveCount(player, itemInstances, engine.itemRegistry)
            val hasQuiverMenu = quiverCapacity > 0 || quiverAmmo > 0 || reserveAmmo > 0
            if (allStacks.isEmpty() && !hasQuiverMenu) {
                println("Inventario vazio.")
                val updated = state.copy(player = player, itemInstances = itemInstances)
                autoSave(updated)
                return updated
            }

            println("\n=== Inventario ===")
            println("Slots: $slotUsed/$slotLimit")
            println("Filtros: ${inventoryFilterSummary(inventoryFilter)}")
            var nextOption = 1
            val quiverOption = if (hasQuiverMenu) nextOption++ else null
            val filterOption = if (allStacks.isNotEmpty()) nextOption++ else null
            if (hasQuiverMenu) {
                val activeAmmoName = player.selectedAmmoTemplateId?.let { templateId ->
                    buildAmmoStacks(player.quiverInventory, itemInstances, player.selectedAmmoTemplateId)
                        .firstOrNull { it.templateId == templateId }
                        ?.item
                        ?.name
                } ?: "-"
                println("Aljava: $quiverAmmo/$quiverCapacity | Reserva: $reserveAmmo | Municao ativa: $activeAmmoName")
                println("${quiverOption}. Gerenciar aljava")
            }
            if (filterOption != null) {
                println("${filterOption}. Filtrar inventario")
            }
            if (allStacks.isNotEmpty() && stacks.isEmpty()) {
                println("Nenhum item corresponde aos filtros atuais.")
            }
            stacks.forEachIndexed { index, stack ->
                val qtyLabel = if (stack.quantity > 1) " x${stack.quantity}" else ""
                println("${nextOption + index}. ${itemDisplayLabel(stack.item)}$qtyLabel")
            }
            println("x. Voltar")

            val maxOption = nextOption + stacks.size - 1
            val choice = readMenuChoice("Escolha: ", 1, maxOption.coerceAtLeast(1))
            if (choice == null) {
                val updated = state.copy(player = player, itemInstances = itemInstances)
                autoSave(updated)
                return updated
            }

            if (quiverOption != null && choice == quiverOption) {
                val result = openQuiverMenu(player, itemInstances)
                player = result.player
                itemInstances = result.itemInstances
                autoSave(state.copy(player = player, itemInstances = itemInstances))
                continue
            }

            if (filterOption != null && choice == filterOption) {
                inventoryFilter = openInventoryFilterMenu(inventoryFilter)
                continue
            }

            val selectedIndex = choice - nextOption
            if (selectedIndex !in stacks.indices) {
                continue
            }
            val selected = stacks[selectedIndex]
            val result = handleInventoryAction(player, itemInstances, selected)
            player = result.player
            itemInstances = result.itemInstances
            autoSave(state.copy(player = player, itemInstances = itemInstances))
        }
    }

    private fun buildInventoryStacks(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): List<InventoryStack> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (itemId in player.inventory) {
            val key = InventorySystem.stackKey(itemId, itemInstances, engine.itemRegistry)
            grouped.getOrPut(key) { mutableListOf() }.add(itemId)
        }

        return grouped.values.mapNotNull { ids ->
            val sampleId = ids.firstOrNull() ?: return@mapNotNull null
            val resolved = engine.itemResolver.resolve(sampleId, itemInstances) ?: return@mapNotNull null
            InventoryStack(
                sampleItemId = sampleId,
                quantity = ids.size,
                itemIds = ids.toList(),
                item = resolved
            )
        }.sortedWith(
            compareByDescending<InventoryStack> { it.item.rarity.ordinal }
                .thenByDescending { it.item.powerScore }
                .thenBy { it.item.name.lowercase() }
        )
    }

    private fun applyInventoryFilter(
        stacks: List<InventoryStack>,
        filter: InventoryFilter
    ): List<InventoryStack> {
        return stacks.filter { stack ->
            val typeOk = filter.type == null || stack.item.type == filter.type
            val rarityOk = filter.minimumRarity == null || stack.item.rarity.ordinal >= filter.minimumRarity.ordinal
            typeOk && rarityOk
        }
    }

    private fun inventoryFilterSummary(filter: InventoryFilter): String {
        val typeLabel = when (filter.type) {
            null -> "todos"
            ItemType.EQUIPMENT -> "equipamentos"
            ItemType.CONSUMABLE -> "consumiveis"
            ItemType.MATERIAL -> "materiais"
        }
        val rarityLabel = filter.minimumRarity?.colorLabel ?: "qualquer raridade"
        return "tipo=$typeLabel | raridade min=$rarityLabel"
    }

    private fun openInventoryFilterMenu(current: InventoryFilter): InventoryFilter {
        var filter = current
        while (true) {
            println("\n=== Filtros do Inventario ===")
            println("Atual: ${inventoryFilterSummary(filter)}")
            println("1. Tipo")
            println("2. Raridade minima")
            println("3. Limpar filtros")
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 3)) {
                1 -> {
                    println("\nTipo:")
                    println("1. Todos")
                    println("2. Equipamentos")
                    println("3. Consumiveis")
                    println("4. Materiais")
                    println("x. Voltar")
                    when (readMenuChoice("Escolha: ", 1, 4)) {
                        1 -> filter = filter.copy(type = null)
                        2 -> filter = filter.copy(type = ItemType.EQUIPMENT)
                        3 -> filter = filter.copy(type = ItemType.CONSUMABLE)
                        4 -> filter = filter.copy(type = ItemType.MATERIAL)
                        null -> {}
                    }
                }
                2 -> {
                    println("\nRaridade minima:")
                    println("1. Qualquer")
                    ItemRarity.entries.forEachIndexed { index, rarity ->
                        println("${index + 2}. ${rarity.colorLabel}")
                    }
                    println("x. Voltar")
                    when (val choice = readMenuChoice("Escolha: ", 1, ItemRarity.entries.size + 1)) {
                        1 -> filter = filter.copy(minimumRarity = null)
                        null -> {}
                        else -> {
                            val rarity = ItemRarity.entries.getOrNull(choice - 2)
                            if (rarity != null) {
                                filter = filter.copy(minimumRarity = rarity)
                            }
                        }
                    }
                }
                3 -> filter = InventoryFilter()
                null -> return filter
            }
        }
    }

    private fun buildAmmoStacks(
        itemIds: List<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        selectedTemplateId: String?
    ): List<AmmoStack> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (itemId in itemIds) {
            if (!InventorySystem.isArrowAmmo(itemId, itemInstances, engine.itemRegistry)) continue
            val templateId = InventorySystem.ammoTemplateId(itemId, itemInstances, engine.itemRegistry)
            grouped.getOrPut(templateId) { mutableListOf() }.add(itemId)
        }

        return grouped.mapNotNull { (templateId, ids) ->
            val sampleId = ids.firstOrNull() ?: return@mapNotNull null
            val resolved = engine.itemResolver.resolve(sampleId, itemInstances) ?: return@mapNotNull null
            AmmoStack(
                templateId = templateId,
                sampleItemId = sampleId,
                quantity = ids.size,
                itemIds = ids.toList(),
                item = resolved
            )
        }.sortedWith(
            compareByDescending<AmmoStack> { it.templateId == selectedTemplateId?.trim()?.lowercase() }
                .thenBy { it.item.name.lowercase() }
        )
    }

    private fun openQuiverMenu(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): UseItemResult {
        var updatedPlayer = normalizePlayerStorage(player, itemInstances)
        var updatedInstances = itemInstances

        while (true) {
            updatedPlayer = normalizePlayerStorage(updatedPlayer, updatedInstances)
            val quiverName = updatedPlayer.equipped[EquipSlot.ALJAVA.name]?.let { quiverId ->
                engine.itemResolver.resolve(quiverId, updatedInstances)?.name ?: quiverId
            } ?: "Nenhuma"
            val quiverCapacity = InventorySystem.quiverCapacity(updatedPlayer, updatedInstances, engine.itemRegistry)
            val quiverStacks = buildAmmoStacks(
                updatedPlayer.quiverInventory,
                updatedInstances,
                updatedPlayer.selectedAmmoTemplateId
            )
            val reserveStacks = buildAmmoStacks(
                updatedPlayer.inventory,
                updatedInstances,
                updatedPlayer.selectedAmmoTemplateId
            )

            println("\n=== Aljava ===")
            println("Aljava equipada: $quiverName")
            println("Capacidade: ${updatedPlayer.quiverInventory.size}/$quiverCapacity")
            val activeAmmoLabel = quiverStacks
                .firstOrNull { it.templateId == updatedPlayer.selectedAmmoTemplateId }
                ?.item
                ?.name
                ?: "-"
            println("Municao ativa: $activeAmmoLabel")

            if (quiverStacks.isNotEmpty()) {
                println("Carregadas:")
                quiverStacks.forEachIndexed { index, stack ->
                    val marker = if (stack.templateId == updatedPlayer.selectedAmmoTemplateId) "[ATIVA] " else ""
                    println("${index + 1}. $marker${itemDisplayLabel(stack.item)} x${stack.quantity}")
                }
            } else {
                println("Carregadas: -")
            }

            if (reserveStacks.isNotEmpty()) {
                println("Reserva:")
                reserveStacks.forEachIndexed { index, stack ->
                    println("${index + 1}. ${itemDisplayLabel(stack.item)} x${stack.quantity}")
                }
            } else {
                println("Reserva: -")
            }

            var option = 1
            val selectAmmoOption = if (quiverStacks.isNotEmpty()) option++ else null
            val loadAmmoOption = if (quiverCapacity > updatedPlayer.quiverInventory.size && reserveStacks.isNotEmpty()) option++ else null
            val unloadAmmoOption = if (quiverStacks.isNotEmpty()) option++ else null
            val sellReserveOption = if (reserveStacks.isNotEmpty()) option++ else null
            val sellLoadedOption = if (quiverStacks.isNotEmpty()) option++ else null

            if (selectAmmoOption != null) println("$selectAmmoOption. Selecionar municao ativa")
            if (loadAmmoOption != null) println("$loadAmmoOption. Carregar da reserva")
            if (unloadAmmoOption != null) println("$unloadAmmoOption. Retirar da aljava")
            if (sellReserveOption != null) println("$sellReserveOption. Vender da reserva")
            if (sellLoadedOption != null) println("$sellLoadedOption. Vender da aljava")
            println("x. Voltar")

            val choice = readMenuChoice("Escolha: ", 1, (option - 1).coerceAtLeast(1)) ?: return UseItemResult(
                updatedPlayer,
                updatedInstances
            )
            when (choice) {
                selectAmmoOption -> {
                    val stack = chooseAmmoStack(quiverStacks, "Municao ativa")
                    updatedPlayer = InventorySystem.selectAmmoTemplate(
                        updatedPlayer,
                        updatedInstances,
                        engine.itemRegistry,
                        stack.templateId
                    )
                    println("Municao ativa alterada para ${stack.item.name}.")
                }
                loadAmmoOption -> {
                    val stack = chooseAmmoStack(reserveStacks, "Carregar")
                    val maxQty = minOf(
                        stack.quantity,
                        (quiverCapacity - updatedPlayer.quiverInventory.size).coerceAtLeast(0)
                    )
                    if (maxQty <= 0) {
                        println("A aljava esta cheia.")
                        continue
                    }
                    val qty = chooseTransferQuantity("Quantidade para carregar", maxQty)
                    val result = InventorySystem.moveAmmoToQuiver(
                        updatedPlayer,
                        updatedInstances,
                        engine.itemRegistry,
                        stack.itemIds.take(qty)
                    )
                    updatedPlayer = updatedPlayer.copy(
                        inventory = result.inventory,
                        quiverInventory = result.quiverInventory,
                        selectedAmmoTemplateId = result.selectedAmmoTemplateId
                    )
                    println("Carregou ${stack.item.name} x${result.accepted.size}.")
                }
                unloadAmmoOption -> {
                    val stack = chooseAmmoStack(quiverStacks, "Retirar")
                    val qty = chooseTransferQuantity("Quantidade para retirar", stack.quantity)
                    val result = InventorySystem.unloadAmmoFromQuiver(
                        updatedPlayer,
                        updatedInstances,
                        engine.itemRegistry,
                        stack.itemIds.take(qty)
                    )
                    updatedPlayer = updatedPlayer.copy(
                        inventory = result.inventory,
                        quiverInventory = result.quiverInventory,
                        selectedAmmoTemplateId = result.selectedAmmoTemplateId
                    )
                    if (result.accepted.isNotEmpty()) {
                        println("Retirou ${stack.item.name} x${result.accepted.size}.")
                    }
                    if (result.rejected.isNotEmpty()) {
                        println("Inventario sem espaco para ${result.rejected.size} flecha(s).")
                    }
                }
                sellReserveOption -> {
                    val stack = chooseAmmoStack(reserveStacks, "Vender da reserva")
                    val qty = chooseSellQuantity(stack.quantity)
                    val saleValue = engine.economyEngine.sellValue(
                        itemValue = stack.item.value,
                        rarity = stack.item.rarity,
                        type = stack.item.type,
                        tags = stack.item.tags
                    )
                    val result = sellInventoryItem(
                        updatedPlayer,
                        updatedInstances,
                        stack.itemIds,
                        stack.item.name,
                        saleValue,
                        qty
                    )
                    updatedPlayer = result.player
                    updatedInstances = result.itemInstances
                }
                sellLoadedOption -> {
                    val stack = chooseAmmoStack(quiverStacks, "Vender da aljava")
                    val qty = chooseSellQuantity(stack.quantity)
                    val saleValue = engine.economyEngine.sellValue(
                        itemValue = stack.item.value,
                        rarity = stack.item.rarity,
                        type = stack.item.type,
                        tags = stack.item.tags
                    )
                    val result = sellQuiverAmmo(
                        updatedPlayer,
                        updatedInstances,
                        stack.itemIds,
                        stack.item.name,
                        saleValue,
                        qty
                    )
                    updatedPlayer = result.player
                    updatedInstances = result.itemInstances
                }
            }
        }
    }

    private fun chooseAmmoStack(options: List<AmmoStack>, label: String): AmmoStack {
        if (options.isEmpty()) error("Nenhuma municao disponivel para $label")
        println("\n$label:")
        options.forEachIndexed { index, stack ->
            println("${index + 1}. ${itemDisplayLabel(stack.item)} x${stack.quantity}")
        }
        val choice = readInt("Escolha: ", 1, options.size)
        return options[choice - 1]
    }

    private fun chooseTransferQuantity(label: String, maxQuantity: Int): Int {
        if (maxQuantity <= 1) return 1
        return readInt("$label (1-$maxQuantity): ", 1, maxQuantity)
    }

    private fun handleInventoryAction(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        stack: InventoryStack
    ): UseItemResult {
        val itemId = stack.itemIds.firstOrNull() ?: return UseItemResult(player, itemInstances)
        val resolved = engine.itemResolver.resolve(itemId, itemInstances) ?: return UseItemResult(player, itemInstances)
        val forcedSaleValue = ClassQuestTagRules.forcedSellValue(resolved.tags)
        val saleValue = forcedSaleValue ?: engine.economyEngine.sellValue(
            itemValue = resolved.value,
            rarity = resolved.rarity,
            type = resolved.type,
            tags = resolved.tags
        )
        println("\nItem: ${itemDisplayLabel(resolved)}")
        println("Tipo: ${resolved.type.name.lowercase()} | Valor de venda por unidade: $saleValue")
        printInventoryItemDetails(player, itemInstances, stack, resolved)

        return when (resolved.type) {
            ItemType.CONSUMABLE -> {
                println("1. Usar")
                println("2. Vender")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 2)) {
                    1 -> {
                        if (player.level < resolved.minLevel) {
                            println("Nivel insuficiente para usar este item (req ${resolved.minLevel}).")
                            UseItemResult(player, itemInstances)
                        } else {
                            useItem(player, itemInstances, itemId)
                        }
                    }
                    2 -> {
                        val qty = chooseSellQuantity(stack.quantity)
                        sellInventoryItem(player, itemInstances, stack.itemIds, resolved.name, saleValue, qty)
                    }
                    null -> UseItemResult(player, itemInstances)
                    else -> UseItemResult(player, itemInstances)
                }
            }
            ItemType.EQUIPMENT -> {
                println("1. Equipar")
                println("2. Vender")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 2)) {
                    1 -> {
                        if (player.level < resolved.minLevel) {
                            println("Nivel insuficiente para equipar (req ${resolved.minLevel}).")
                            return UseItemResult(player, itemInstances)
                        }
                        val updated = equipItem(player, resolved, itemInstances)
                        UseItemResult(updated, itemInstances)
                    }
                    2 -> {
                        val qty = chooseSellQuantity(stack.quantity)
                        sellInventoryItem(player, itemInstances, stack.itemIds, resolved.name, saleValue, qty)
                    }
                    null -> UseItemResult(player, itemInstances)
                    else -> UseItemResult(player, itemInstances)
                }
            }
            ItemType.MATERIAL -> {
                println("1. Vender")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 1)) {
                    1 -> {
                        val qty = chooseSellQuantity(stack.quantity)
                        sellInventoryItem(player, itemInstances, stack.itemIds, resolved.name, saleValue, qty)
                    }
                    null -> UseItemResult(player, itemInstances)
                    else -> UseItemResult(player, itemInstances)
                }
            }
        }
    }

    private fun sellInventoryItem(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        itemIds: List<String>,
        itemName: String,
        saleValue: Int,
        quantity: Int
    ): UseItemResult {
        val inventory = player.inventory.toMutableList()
        val toSell = itemIds.take(quantity.coerceAtLeast(1))
        if (toSell.isEmpty()) {
            return UseItemResult(player, itemInstances)
        }
        var sold = 0
        val updatedInstances = itemInstances.toMutableMap()
        for (itemId in toSell) {
            if (inventory.remove(itemId)) {
                sold++
                if (updatedInstances.containsKey(itemId)) {
                    updatedInstances.remove(itemId)
                }
            }
        }
        if (sold <= 0) {
            return UseItemResult(player, itemInstances)
        }
        var updatedPlayer = player.copy(
            inventory = inventory,
            gold = player.gold + saleValue * sold
        )
        updatedPlayer = applyAchievementUpdate(
            achievementTracker.onGoldEarned(updatedPlayer, (saleValue * sold).toLong())
        )
        println("Vendeu $itemName x$sold por ${saleValue * sold} ouro.")
        return UseItemResult(updatedPlayer, updatedInstances.toMap())
    }

    private fun sellQuiverAmmo(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        itemIds: List<String>,
        itemName: String,
        saleValue: Int,
        quantity: Int
    ): UseItemResult {
        val quiverInventory = player.quiverInventory.toMutableList()
        val toSell = itemIds.take(quantity.coerceAtLeast(1))
        if (toSell.isEmpty()) {
            return UseItemResult(player, itemInstances)
        }

        var sold = 0
        val updatedInstances = itemInstances.toMutableMap()
        for (itemId in toSell) {
            if (quiverInventory.remove(itemId)) {
                sold++
                if (updatedInstances.containsKey(itemId)) {
                    updatedInstances.remove(itemId)
                }
            }
        }
        if (sold <= 0) {
            return UseItemResult(player, itemInstances)
        }

        var updatedPlayer = player.copy(
            quiverInventory = quiverInventory,
            gold = player.gold + saleValue * sold
        )
        updatedPlayer = normalizePlayerStorage(updatedPlayer, updatedInstances)
        updatedPlayer = applyAchievementUpdate(
            achievementTracker.onGoldEarned(updatedPlayer, (saleValue * sold).toLong())
        )
        println("Vendeu $itemName x$sold por ${saleValue * sold} ouro.")
        return UseItemResult(updatedPlayer, updatedInstances.toMap())
    }

    private fun chooseSellQuantity(maxQuantity: Int): Int {
        if (maxQuantity <= 1) return 1
        return readInt("Quantidade para vender (1-$maxQuantity): ", 1, maxQuantity)
    }

    private fun printInventoryItemDetails(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        stack: InventoryStack,
        item: rpg.item.ResolvedItem
    ) {
        println("Raridade: ${colorizeUi(item.rarity.colorLabel, item.rarity.ansiColorCode)}")
        if (item.qualityRollPct != 100 || item.powerScore > 0) {
            val powerLabel = if (item.powerScore > 0) " | Poder ${item.powerScore}" else ""
            println("Qualidade: ${item.qualityRollPct}%$powerLabel")
        }
        if (item.minLevel > 1) {
            println("Nivel requerido: ${item.minLevel}")
        }
        if (item.description.isNotBlank()) {
            println("Descricao: ${item.description}")
        }
        if (item.affixes.isNotEmpty()) {
            println("Afixos: ${item.affixes.joinToString(", ")}")
        }
        when (item.type) {
            ItemType.CONSUMABLE -> {
                val restores = mutableListOf<String>()
                if (item.effects.hpRestore > 0.0) restores += "HP +${format(item.effects.hpRestore)}"
                if (item.effects.mpRestore > 0.0) restores += "MP +${format(item.effects.mpRestore)}"
                if (item.effects.hpRestorePct > 0.0) restores += "HP +${format(item.effects.hpRestorePct)}%"
                if (item.effects.mpRestorePct > 0.0) restores += "MP +${format(item.effects.mpRestorePct)}%"
                if (item.effects.fullRestore) restores += "Restaura HP/MP total"
                if (item.effects.clearNegativeStatuses) restores += "Remove status negativos"
                if (item.effects.statusImmunitySeconds > 0.0) {
                    restores += "Imunidade ${format(item.effects.statusImmunitySeconds)}s"
                }
                if (item.effects.roomAttributeMultiplierPct != 0.0 && item.effects.roomAttributeDurationRooms > 0) {
                    restores += "Buff ${format(item.effects.roomAttributeMultiplierPct)}% por ${item.effects.roomAttributeDurationRooms} sala(s)"
                }
                if (item.effects.runAttributeMultiplierPct != 0.0) {
                    restores += "Buff de run ${format(item.effects.runAttributeMultiplierPct)}%"
                }
                if (restores.isNotEmpty()) {
                    println("Efeito: ${restores.joinToString(" | ")}")
                }
            }
            ItemType.MATERIAL -> {
                val canonical = canonicalItemId(stack.sampleItemId, itemInstances)
                if (InventorySystem.isArrowAmmo(stack.sampleItemId, itemInstances, engine.itemRegistry)) {
                    println("Uso: municao para armas de arco; carregue pela aljava.")
                    val ammoBonusLabel = formatItemBonuses(item)
                    if (ammoBonusLabel.isNotBlank()) {
                        println("Bonus da municao: $ammoBonusLabel")
                    }
                    val ammoEffectLabel = formatItemEffectsSummary(item)
                    if (ammoEffectLabel.isNotBlank()) {
                        println("Efeito da municao: $ammoEffectLabel")
                    }
                }
                val gatherSources = repo.gatherNodes.values
                    .filter { it.resourceItemId == canonical }
                    .take(4)
                    .map { it.name }
                val uses = repo.craftRecipes.values
                    .filter { recipe -> recipe.ingredients.any { it.itemId == canonical } }
                    .take(6)
                    .map { "${it.name} (${it.discipline.name.lowercase()})" }
                if (gatherSources.isNotEmpty()) {
                    println("Origem: ${gatherSources.joinToString(", ")}")
                }
                if (uses.isNotEmpty()) {
                    println("Usado em: ${uses.joinToString(", ")}")
                }
            }
            ItemType.EQUIPMENT -> {
                val slotLabel = item.slot?.name ?: "desconhecido"
                val handLabel = if (item.twoHanded) " (duas maos)" else ""
                println("Slot: $slotLabel$handLabel")
                val classTag = ClassQuestTagRules.effectiveClassTag(item.tags)
                println("Tag de classe: ${classTagDisplayLabel(classTag)}")
                val classLock = ClassQuestTagRules.classLocked(item.tags)
                val pathLock = ClassQuestTagRules.pathLocked(item.tags)
                if (classLock != null || pathLock != null) {
                    val classLabel = classLock ?: "-"
                    val pathLabel = pathLock ?: "-"
                    println("Restricoes: classe=$classLabel | caminho=$pathLabel")
                }
                if (ClassQuestTagRules.isQuestReward(item.tags)) {
                    println("Item de set de quest (revenda fixa).")
                }
                val bonusLabel = formatItemBonuses(item)
                if (bonusLabel.isNotBlank()) {
                    println("Bonus: $bonusLabel")
                }
                val effectLabel = formatItemEffectsSummary(item)
                if (effectLabel.isNotBlank()) {
                    println("Efeito: $effectLabel")
                }

                val preview = previewEquipDelta(player, item, itemInstances)
                if (preview != null) {
                    val equippedLabel = preview.replacedItem?.let(::itemDisplayLabel) ?: "Vazio"
                    println("Comparando com (${equippedSlotLabel(preview.slotKey)}): $equippedLabel")
                    val deltaLabel = formatEquipComparison(preview.before, preview.after)
                    if (deltaLabel.isNotBlank()) {
                        println("Delta: $deltaLabel")
                    } else {
                        println("Delta: sem alteracoes relevantes nos atributos principais.")
                    }
                }
            }
        }
    }

    private fun previewEquipDelta(
        player: PlayerState,
        item: rpg.item.ResolvedItem,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): EquipComparisonPreview? {
        if (questEquipRestrictionReason(player, item) != null) return null
        val target = resolveEquipPreviewTarget(player, item, itemInstances) ?: return null
        val equipped = player.equipped.toMutableMap()
        when (target.slotKey) {
            EquipSlot.WEAPON_MAIN.name -> {
                equipped[EquipSlot.WEAPON_MAIN.name] = item.id
                if (item.twoHanded) {
                    equipped[EquipSlot.WEAPON_OFF.name] = offhandBlockedId
                } else if (equipped[EquipSlot.WEAPON_OFF.name] == offhandBlockedId) {
                    equipped.remove(EquipSlot.WEAPON_OFF.name)
                }
            }
            EquipSlot.WEAPON_OFF.name -> {
                if (equipped[EquipSlot.WEAPON_OFF.name] != offhandBlockedId) {
                    equipped[EquipSlot.WEAPON_OFF.name] = item.id
                } else {
                    return null
                }
            }
            else -> equipped[target.slotKey] = item.id
        }
        val before = computePlayerStats(player, itemInstances)
        val after = computePlayerStats(player.copy(equipped = equipped), itemInstances)
        return EquipComparisonPreview(
            slotKey = target.slotKey,
            replacedItem = target.currentItemId?.let { currentId ->
                engine.itemResolver.resolve(currentId, itemInstances)
            },
            before = before,
            after = after
        )
    }

    private fun resolveEquipPreviewTarget(
        player: PlayerState,
        item: rpg.item.ResolvedItem,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): EquipPreviewTarget? {
        val slot = item.slot ?: return null
        val equipped = player.equipped
        return when (slot) {
            EquipSlot.ACCESSORY -> {
                val emptySlot = accessorySlots.firstOrNull { !equipped.containsKey(it) }
                if (emptySlot != null) {
                    EquipPreviewTarget(slotKey = emptySlot, currentItemId = null)
                } else {
                    val targetSlot = accessorySlots.minByOrNull { slotKey ->
                        val currentId = equipped[slotKey]
                        engine.itemResolver.resolve(currentId ?: "", itemInstances)?.powerScore ?: Int.MAX_VALUE
                    } ?: return null
                    EquipPreviewTarget(slotKey = targetSlot, currentItemId = equipped[targetSlot])
                }
            }
            EquipSlot.WEAPON_MAIN -> EquipPreviewTarget(
                slotKey = EquipSlot.WEAPON_MAIN.name,
                currentItemId = equipped[EquipSlot.WEAPON_MAIN.name]?.takeIf { it != offhandBlockedId }
            )
            EquipSlot.WEAPON_OFF -> {
                if (equipped[EquipSlot.WEAPON_OFF.name] == offhandBlockedId) {
                    null
                } else {
                    EquipPreviewTarget(
                        slotKey = EquipSlot.WEAPON_OFF.name,
                        currentItemId = equipped[EquipSlot.WEAPON_OFF.name]
                    )
                }
            }
            else -> EquipPreviewTarget(
                slotKey = slot.name,
                currentItemId = equipped[slot.name]?.takeIf { it != offhandBlockedId }
            )
        }
    }

    private fun formatEquipComparison(before: ComputedStats, after: ComputedStats): String {
        val deltas = listOf(
            "DMG" to (after.derived.damagePhysical - before.derived.damagePhysical),
            "M-DMG" to (after.derived.damageMagic - before.derived.damageMagic),
            "DEF" to (after.derived.defPhysical - before.derived.defPhysical),
            "M-DEF" to (after.derived.defMagic - before.derived.defMagic),
            "HP" to (after.derived.hpMax - before.derived.hpMax),
            "MP" to (after.derived.mpMax - before.derived.mpMax),
            "CRIT" to (after.derived.critChancePct - before.derived.critChancePct),
            "ACC" to (after.derived.accuracy - before.derived.accuracy),
            "EVA" to (after.derived.evasion - before.derived.evasion),
            "ASPD" to (after.derived.attackSpeed - before.derived.attackSpeed),
            "MOVE" to (after.derived.moveSpeed - before.derived.moveSpeed),
            "CDR" to (after.derived.cdrPct - before.derived.cdrPct),
            "DR" to (after.derived.damageReductionPct - before.derived.damageReductionPct)
        ).mapNotNull { (label, delta) ->
            if (abs(delta) < 0.01) null else "$label ${formatSignedDouble(delta)}"
        }
        return deltas.joinToString(" | ")
    }

    private fun questEquipRestrictionReason(player: PlayerState, item: rpg.item.ResolvedItem): String? {
        val classTag = ClassQuestTagRules.effectiveClassTag(item.tags)
        if (!ClassQuestTagRules.canUseClassTag(player, classTag)) {
            return "Item restrito a ${classTagDisplayLabel(classTag)}."
        }

        val classLock = ClassQuestTagRules.classLocked(item.tags)
        if (classLock != null && classLock != player.classId.lowercase()) {
            val className = repo.classes[classLock]?.name ?: classLock
            return "Item restrito a classe $className."
        }
        val pathLock = ClassQuestTagRules.pathLocked(item.tags) ?: return null
        val ownerClass = classLock ?: player.classId.lowercase()
        val allowed = ClassQuestTagRules.allowedPaths(player, ownerClass)
        if (pathLock !in allowed) {
            val pathName = repo.subclasses[pathLock]?.name ?: repo.specializations[pathLock]?.name ?: pathLock
            return "Item restrito ao caminho $pathName."
        }
        return null
    }

    private fun classTagDisplayLabel(tag: String): String {
        val normalized = tag.trim().lowercase()
        if (normalized.isBlank() || normalized == ClassQuestTagRules.anyClassTag) {
            return "Any.Class (neutro)"
        }
        val className = repo.classes[normalized]?.name
        if (className != null) return className
        val subclassName = repo.subclasses[normalized]?.name
        if (subclassName != null) return subclassName
        val specializationName = repo.specializations[normalized]?.name
        if (specializationName != null) return specializationName
        return normalized
    }

    private fun formatItemBonuses(item: rpg.item.ResolvedItem): String {
        val attrs = item.bonuses.attributes
        val attrParts = mutableListOf<String>()
        if (attrs.str != 0) attrParts += "STR ${formatSigned(attrs.str)}"
        if (attrs.agi != 0) attrParts += "AGI ${formatSigned(attrs.agi)}"
        if (attrs.dex != 0) attrParts += "DEX ${formatSigned(attrs.dex)}"
        if (attrs.vit != 0) attrParts += "VIT ${formatSigned(attrs.vit)}"
        if (attrs.`int` != 0) attrParts += "INT ${formatSigned(attrs.`int`)}"
        if (attrs.spr != 0) attrParts += "SPR ${formatSigned(attrs.spr)}"
        if (attrs.luk != 0) attrParts += "LUK ${formatSigned(attrs.luk)}"
        val add = item.bonuses.derivedAdd
        val mult = item.bonuses.derivedMult
        val derivedParts = mutableListOf<String>()
        if (add.damagePhysical != 0.0) derivedParts += "DMG ${formatSignedDouble(add.damagePhysical)}"
        if (add.damageMagic != 0.0) derivedParts += "M-DMG ${formatSignedDouble(add.damageMagic)}"
        if (add.defPhysical != 0.0) derivedParts += "DEF ${formatSignedDouble(add.defPhysical)}"
        if (add.defMagic != 0.0) derivedParts += "M-DEF ${formatSignedDouble(add.defMagic)}"
        if (add.hpMax != 0.0) derivedParts += "HP ${formatSignedDouble(add.hpMax)}"
        if (add.mpMax != 0.0) derivedParts += "MP ${formatSignedDouble(add.mpMax)}"
        if (add.attackSpeed != 0.0) derivedParts += "ASPD ${formatSignedDouble(add.attackSpeed)}"
        if (add.moveSpeed != 0.0) derivedParts += "MOVE ${formatSignedDouble(add.moveSpeed)}"
        if (add.critChancePct != 0.0) derivedParts += "CRIT ${formatSignedDouble(add.critChancePct)}%"
        if (add.critDamagePct != 0.0) derivedParts += "CRIT DMG ${formatSignedDouble(add.critDamagePct)}%"
        if (add.accuracy != 0.0) derivedParts += "ACC ${formatSignedDouble(add.accuracy)}"
        if (add.evasion != 0.0) derivedParts += "EVA ${formatSignedDouble(add.evasion)}"
        if (add.cdrPct != 0.0) derivedParts += "CDR ${formatSignedDouble(add.cdrPct)}%"
        if (add.dropBonusPct != 0.0) derivedParts += "DROP ${formatSignedDouble(add.dropBonusPct)}%"
        if (add.hpRegen != 0.0) derivedParts += "HP REG ${formatSignedDouble(add.hpRegen)}"
        if (add.mpRegen != 0.0) derivedParts += "MP REG ${formatSignedDouble(add.mpRegen)}"
        if (add.vampirismPct != 0.0) derivedParts += "VAMP ${formatSignedDouble(add.vampirismPct)}%"
        if (add.damageReductionPct != 0.0) derivedParts += "DR ${formatSignedDouble(add.damageReductionPct)}%"
        if (add.tenacityPct != 0.0) derivedParts += "TEN ${formatSignedDouble(add.tenacityPct)}%"
        if (add.penPhysical != 0.0) derivedParts += "P-PEN ${formatSignedDouble(add.penPhysical)}"
        if (add.penMagic != 0.0) derivedParts += "M-PEN ${formatSignedDouble(add.penMagic)}"
        if (add.xpGainPct != 0.0) derivedParts += "XP ${formatSignedDouble(add.xpGainPct)}%"
        if (mult.attackSpeed != 0.0) derivedParts += "ASPD ${formatSignedDouble(mult.attackSpeed)}%"
        if (mult.moveSpeed != 0.0) derivedParts += "MOVE ${formatSignedDouble(mult.moveSpeed)}%"
        if (mult.damagePhysical != 0.0) derivedParts += "DMG ${formatSignedDouble(mult.damagePhysical)}%"
        if (mult.damageMagic != 0.0) derivedParts += "M-DMG ${formatSignedDouble(mult.damageMagic)}%"
        if (mult.defPhysical != 0.0) derivedParts += "DEF ${formatSignedDouble(mult.defPhysical)}%"
        if (mult.defMagic != 0.0) derivedParts += "M-DEF ${formatSignedDouble(mult.defMagic)}%"
        return (attrParts + derivedParts).joinToString(", ")
    }

    private fun formatItemEffectsSummary(item: rpg.item.ResolvedItem): String {
        val parts = mutableListOf<String>()
        if (item.effects.statusImmunitySeconds > 0.0) {
            parts += "Imunidade ${format(item.effects.statusImmunitySeconds)}s"
        }
        if (item.effects.runAttributeMultiplierPct != 0.0) {
            parts += "Buff de run ${format(item.effects.runAttributeMultiplierPct)}%"
        }
        for (status in item.effects.applyStatuses) {
            val chanceLabel = if (status.chancePct > 0.0) "${format(status.chancePct)}%" else "100%"
            val durationLabel = if (status.durationSeconds > 0.0) " por ${format(status.durationSeconds)}s" else ""
            parts += "${StatusSystem.displayName(status.type)} $chanceLabel$durationLabel"
        }
        return parts.joinToString(", ")
    }

    private fun itemDisplayLabel(item: rpg.item.ResolvedItem): String {
        return itemDisplayLabel(item.name, item.rarity)
    }

    private fun itemDisplayLabel(name: String, rarity: rpg.item.ItemRarity): String {
        return colorizeUi("[${rarity.colorLabel}] $name", rarity.ansiColorCode)
    }

    private fun colorizeUi(text: String, colorCode: String): String {
        return "$colorCode$text$ansiCombatReset"
    }

    private fun useItem(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): UseItemResult {
        val consumables = player.inventory.filter { id ->
            engine.itemResolver.resolve(id, itemInstances)?.type == ItemType.CONSUMABLE
        }
        if (consumables.isEmpty()) {
            println("Nenhum consumivel disponivel.")
            return UseItemResult(player, itemInstances)
        }

        val itemId = choose("Consumivel", consumables) { id ->
            engine.itemResolver.resolve(id, itemInstances)?.name ?: id
        }
        return useItem(player, itemInstances, itemId)
    }

    private fun useItem(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        itemId: String
    ): UseItemResult {
        val item = engine.itemResolver.resolve(itemId, itemInstances) ?: return UseItemResult(player, itemInstances)
        if (item.type != ItemType.CONSUMABLE) return UseItemResult(player, itemInstances)

        val stats = computePlayerStats(player, itemInstances)
        val hpPctRestore = stats.derived.hpMax * (item.effects.hpRestorePct / 100.0)
        val mpPctRestore = stats.derived.mpMax * (item.effects.mpRestorePct / 100.0)
        var hpRestored = item.effects.hpRestore + hpPctRestore
        var mpRestored = item.effects.mpRestore + mpPctRestore
        if (item.effects.fullRestore) {
            hpRestored = stats.derived.hpMax
            mpRestored = stats.derived.mpMax
        }

        var updatedPlayer = applyHealing(player, hpRestored, mpRestored, itemInstances)
        if (item.effects.roomAttributeMultiplierPct != 0.0 && item.effects.roomAttributeDurationRooms > 0) {
            val mult = (1.0 + item.effects.roomAttributeMultiplierPct / 100.0).coerceAtLeast(0.1)
            updatedPlayer = applyRoomEffect(updatedPlayer, mult, item.effects.roomAttributeDurationRooms)
        }
        if (item.effects.runAttributeMultiplierPct != 0.0) {
            val mult = (1.0 + item.effects.runAttributeMultiplierPct / 100.0).coerceAtLeast(0.1)
            updatedPlayer = updatedPlayer.copy(runAttrMultiplier = (updatedPlayer.runAttrMultiplier * mult).coerceAtLeast(0.1))
        }

        val inventory = updatedPlayer.inventory.toMutableList()
        inventory.remove(itemId)

        val updatedInstances = if (itemInstances.containsKey(itemId)) {
            itemInstances - itemId
        } else {
            itemInstances
        }

        if (item.effects.clearNegativeStatuses || item.effects.statusImmunitySeconds > 0.0) {
            println("Esse efeito defensivo e aplicado apenas em combate.")
        }
        println("Usou ${item.name}.")
        return UseItemResult(updatedPlayer.copy(inventory = inventory), updatedInstances)
    }

    private fun equipItem(
        player: PlayerState,
        item: rpg.item.ResolvedItem,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        val lockReason = questEquipRestrictionReason(player, item)
        if (lockReason != null) {
            println(lockReason)
            return player
        }
        if (player.level < item.minLevel) {
            println("Nivel insuficiente para equipar ${item.name} (req ${item.minLevel}).")
            return player
        }
        val slot = item.slot ?: return player
        val equipped = player.equipped.toMutableMap()
        val inventory = player.inventory.toMutableList()

        return when (slot) {
            EquipSlot.ACCESSORY -> {
                val targetSlot = pickAccessorySlot(equipped, itemInstances) ?: return player
                moveEquippedToInventory(equipped, inventory, targetSlot)
                equipped[targetSlot] = item.id
                inventory.remove(item.id)
                val updated = normalizePlayerStorage(
                    player.copy(equipped = equipped, inventory = inventory),
                    itemInstances
                )
                println("Equipou ${item.name} no slot $targetSlot.")
                clampPlayerResources(updated, itemInstances)
            }
            EquipSlot.WEAPON_MAIN -> {
                equipMainWeapon(player, item, itemInstances, equipped, inventory)
            }
            EquipSlot.WEAPON_OFF -> {
                equipOffhand(player, item, itemInstances, equipped, inventory)
            }
            else -> {
                val slotKey = slot.name
                moveEquippedToInventory(equipped, inventory, slotKey)
                equipped[slotKey] = item.id
                inventory.remove(item.id)
                val updated = normalizePlayerStorage(
                    player.copy(equipped = equipped, inventory = inventory),
                    itemInstances
                )
                println("Equipou ${item.name} no slot ${slot.name}.")
                clampPlayerResources(updated, itemInstances)
            }
        }
    }

    private fun equipMainWeapon(
        player: PlayerState,
        item: rpg.item.ResolvedItem,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        equipped: MutableMap<String, String>,
        inventory: MutableList<String>
    ): PlayerState {
        val mainKey = EquipSlot.WEAPON_MAIN.name
        val offKey = EquipSlot.WEAPON_OFF.name
        val offhand = equipped[offKey]

        if (item.twoHanded) {
            moveEquippedToInventory(equipped, inventory, mainKey)
            if (offhand != null && offhand != offhandBlockedId) {
                moveEquippedToInventory(equipped, inventory, offKey)
            }
            equipped[mainKey] = item.id
            equipped[offKey] = offhandBlockedId
            inventory.remove(item.id)
            val updated = normalizePlayerStorage(
                player.copy(equipped = equipped, inventory = inventory),
                itemInstances
            )
            println("Equipou ${item.name} (duas maos).")
            return clampPlayerResources(updated, itemInstances)
        }

        moveEquippedToInventory(equipped, inventory, mainKey)
        if (offhand == offhandBlockedId) {
            equipped.remove(offKey)
        }
        equipped[mainKey] = item.id
        inventory.remove(item.id)
        val updated = normalizePlayerStorage(
            player.copy(equipped = equipped, inventory = inventory),
            itemInstances
        )
        println("Equipou ${item.name} na arma primaria.")
        return clampPlayerResources(updated, itemInstances)
    }

    private fun equipOffhand(
        player: PlayerState,
        item: rpg.item.ResolvedItem,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        equipped: MutableMap<String, String>,
        inventory: MutableList<String>
    ): PlayerState {
        val mainKey = EquipSlot.WEAPON_MAIN.name
        val offKey = EquipSlot.WEAPON_OFF.name
        val mainItemId = equipped[mainKey]

        if (item.twoHanded) {
            println("Item de duas maos nao pode ser equipado na secundaria.")
            return player
        }

        if (mainItemId != null && isTwoHanded(mainItemId, itemInstances)) {
            println("Arma de duas maos equipada. Remova-a antes de usar secundaria.")
            return player
        }
        if (equipped[offKey] == offhandBlockedId) {
            println("Arma de duas maos equipada. Remova-a antes de usar secundaria.")
            return player
        }
        if (item.tags.contains("shield")) {
            if (mainItemId != null && isTwoHanded(mainItemId, itemInstances)) {
                println("Escudos nao podem ser usados com armas de duas maos.")
                return player
            }
        }

        moveEquippedToInventory(equipped, inventory, offKey)
        equipped[offKey] = item.id
        inventory.remove(item.id)
        val updated = normalizePlayerStorage(
            player.copy(equipped = equipped, inventory = inventory),
            itemInstances
        )
        println("Equipou ${item.name} na arma secundaria.")
        return clampPlayerResources(updated, itemInstances)
    }

    private fun pickAccessorySlot(
        equipped: Map<String, String>,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): String? {
        val emptySlot = accessorySlots.firstOrNull { !equipped.containsKey(it) }
        if (emptySlot != null) return emptySlot

        println("\nSlots de acessorio ocupados:")
        accessorySlots.forEachIndexed { index, slot ->
            val itemId = equipped[slot]
            val name = if (itemId == null || itemId == offhandBlockedId) {
                "Vazio"
            } else {
                engine.itemResolver.resolve(itemId, itemInstances)?.name ?: itemId
            }
            println("${index + 1}. $slot -> $name")
        }
        val choice = readInt("Substituir qual slot? ", 1, accessorySlots.size)
        return accessorySlots[choice - 1]
    }

    private fun moveEquippedToInventory(
        equipped: MutableMap<String, String>,
        inventory: MutableList<String>,
        slotKey: String
    ) {
        val previous = equipped[slotKey] ?: return
        if (previous != offhandBlockedId) {
            inventory.add(previous)
        }
        equipped.remove(slotKey)
    }

    private fun isTwoHanded(itemId: String, itemInstances: Map<String, rpg.model.ItemInstance>): Boolean {
        val resolved = engine.itemResolver.resolve(itemId, itemInstances)
        return resolved?.twoHanded == true
    }

    private fun applyTwoHandedLoadout(equipped: MutableMap<String, String>) {
        val mainKey = EquipSlot.WEAPON_MAIN.name
        val offKey = EquipSlot.WEAPON_OFF.name
        val mainItemId = equipped[mainKey] ?: return
        val resolved = engine.itemResolver.resolve(mainItemId, emptyMap()) ?: return
        if (resolved.twoHanded) {
            equipped[offKey] = offhandBlockedId
        }
    }

    private fun allocateUnspentPoints(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        if (player.unspentAttrPoints <= 0) {
            println("Nenhum ponto de atributo disponivel.")
            return player
        }
        return allocateAttributePoints(player, itemInstances)
    }

    private fun allocateAttributePoints(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        var remaining = player.unspentAttrPoints
        var attrs = player.baseAttributes
        println("\nDistribuindo $remaining pontos de atributo.")

        val order = listOf("STR", "AGI", "DEX", "VIT", "INT", "SPR", "LUK")
        for (key in order) {
            if (remaining <= 0) break
            val add = readInt("$key (0-$remaining): ", 0, remaining)
            remaining -= add
            attrs = when (key) {
                "STR" -> attrs.copy(str = attrs.str + add)
                "AGI" -> attrs.copy(agi = attrs.agi + add)
                "DEX" -> attrs.copy(dex = attrs.dex + add)
                "VIT" -> attrs.copy(vit = attrs.vit + add)
                "INT" -> attrs.copy(`int` = attrs.`int` + add)
                "SPR" -> attrs.copy(spr = attrs.spr + add)
                else -> attrs.copy(luk = attrs.luk + add)
            }
        }

        val updated = player.copy(
            baseAttributes = attrs,
            unspentAttrPoints = remaining
        )

        return clampPlayerResources(updated, itemInstances)
    }

    private fun clampPlayerResources(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance> = emptyMap()
    ): PlayerState {
        val stats = computePlayerStats(player, itemInstances)
        val hp = player.currentHp.coerceIn(0.0, stats.derived.hpMax)
        val mp = player.currentMp.coerceIn(0.0, stats.derived.mpMax)
        return player.copy(currentHp = hp, currentMp = mp)
    }

    private fun computePlayerStats(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance> = emptyMap()
    ): ComputedStats {
        return engine.computePlayerStats(player, itemInstances)
    }

    private fun eventContext(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        depth: Int
    ): EventContext {
        return engine.buildEventContext(player, itemInstances, depth)
    }

    private fun finalizeRun(
        player: PlayerState,
        loot: MutableList<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): RunFinalizeResult {
        val cleared = clearRunEffects(player)
        val withCapacity = InventorySystem.addItemsWithLimit(
            player = cleared,
            itemInstances = itemInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = loot
        )
        val rejectedGenerated = withCapacity.rejected.filter { itemInstances.containsKey(it) }.toSet()
        if (withCapacity.rejected.isNotEmpty()) {
            println("Inventario cheio: ${withCapacity.rejected.size} item(ns) da run foram perdidos.")
        }
        var updatedPlayer = cleared.copy(
            inventory = withCapacity.inventory,
            quiverInventory = withCapacity.quiverInventory,
            selectedAmmoTemplateId = withCapacity.selectedAmmoTemplateId
        )
        var updatedItemInstances = itemInstances - rejectedGenerated
        if (withCapacity.accepted.isNotEmpty()) {
            val collectedByCanonical = withCapacity.accepted
                .groupingBy { itemId -> updatedItemInstances[itemId]?.templateId ?: itemId }
                .eachCount()
            val classQuestUpdate = engine.classQuestService.onItemsCollected(
                player = updatedPlayer,
                itemInstances = updatedItemInstances,
                collectedItems = collectedByCanonical
            )
            classQuestUpdate.messages.forEach { println(it) }
            val classQuestGold = (classQuestUpdate.player.gold - updatedPlayer.gold).coerceAtLeast(0)
            updatedPlayer = classQuestUpdate.player
            if (classQuestGold > 0) {
                updatedPlayer = applyAchievementUpdate(
                    achievementTracker.onGoldEarned(updatedPlayer, classQuestGold.toLong())
                )
            }
            updatedItemInstances = classQuestUpdate.itemInstances
        }
        return RunFinalizeResult(
            player = updatedPlayer,
            itemInstances = updatedItemInstances
        )
    }

    private fun applyDeathPenalty(
        player: PlayerState,
        loot: MutableList<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): DeathPenaltyResult {
        val cleared = clearRunEffects(player)
        val stats = computePlayerStats(cleared, itemInstances)
        val lossReduction = stats.attributes.luk * 0.3
        val lossPct = (deathBaseLootLossPct - lossReduction).coerceIn(deathMinLootLossPct, deathBaseLootLossPct)
        val keepPct = 100.0 - lossPct
        val keepCount = ceil(loot.size * (keepPct / 100.0)).toInt().coerceAtMost(loot.size)

        engine.shuffleLoot(loot)
        val kept = loot.take(keepCount)
        val lost = loot.drop(keepCount).toSet()

        val withCapacity = InventorySystem.addItemsWithLimit(
            player = cleared,
            itemInstances = itemInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = kept
        )
        val allLost = lost + withCapacity.rejected.toSet()

        val nextStacks = if (cleared.deathDebuffMinutes > 0.0) cleared.deathDebuffStacks + 1 else 1
        val duration = deathDebuffBaseMinutes + (nextStacks - 1) * deathDebuffExtraMinutes
        val lostGold = (cleared.gold * deathGoldLossPct).toInt().coerceAtLeast(0)

        println("Perdeu ${allLost.size} itens no caos da derrota.")
        if (lostGold > 0) {
            println("Perdeu $lostGold de ouro na derrota.")
        }

        var updatedPlayer = cleared.copy(
            inventory = withCapacity.inventory,
            quiverInventory = withCapacity.quiverInventory,
            selectedAmmoTemplateId = withCapacity.selectedAmmoTemplateId,
            currentHp = 1.0,
            gold = (cleared.gold - lostGold).coerceAtLeast(0),
            deathDebuffStacks = nextStacks,
            deathDebuffMinutes = duration,
            deathXpPenaltyPct = deathXpPenaltyPct,
            deathXpPenaltyMinutes = duration
        )
        updatedPlayer = applyAchievementUpdate(achievementTracker.onDeath(updatedPlayer))
        var updatedInstances = itemInstances - allLost.filter { itemInstances.containsKey(it) }.toSet()
        if (withCapacity.accepted.isNotEmpty()) {
            val collectedByCanonical = withCapacity.accepted
                .groupingBy { itemId -> updatedInstances[itemId]?.templateId ?: itemId }
                .eachCount()
            val classQuestUpdate = engine.classQuestService.onItemsCollected(
                player = updatedPlayer,
                itemInstances = updatedInstances,
                collectedItems = collectedByCanonical
            )
            classQuestUpdate.messages.forEach { println(it) }
            val classQuestGold = (classQuestUpdate.player.gold - updatedPlayer.gold).coerceAtLeast(0)
            updatedPlayer = classQuestUpdate.player
            if (classQuestGold > 0) {
                updatedPlayer = applyAchievementUpdate(
                    achievementTracker.onGoldEarned(updatedPlayer, classQuestGold.toLong())
                )
            }
            updatedInstances = classQuestUpdate.itemInstances
        }
        return DeathPenaltyResult(updatedPlayer, updatedInstances)
    }

    private fun tickEffects(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        var updated = player
        val stats = computePlayerStats(updated, itemInstances)

        if (updated.roomRegenHpRooms > 0) {
            val heal = stats.derived.hpMax * updated.roomRegenHpPct
            val newHp = min(stats.derived.hpMax, updated.currentHp + heal)
            val remaining = updated.roomRegenHpRooms - 1
            updated = if (remaining <= 0) {
                updated.copy(currentHp = newHp, roomRegenHpRooms = 0, roomRegenHpPct = 0.0)
            } else {
                updated.copy(currentHp = newHp, roomRegenHpRooms = remaining)
            }
        }

        if (updated.roomRegenMpRooms > 0) {
            val regen = stats.derived.mpMax * updated.roomRegenMpPct
            val newMp = min(stats.derived.mpMax, updated.currentMp + regen)
            val remaining = updated.roomRegenMpRooms - 1
            updated = if (remaining <= 0) {
                updated.copy(currentMp = newMp, roomRegenMpRooms = 0, roomRegenMpPct = 0.0)
            } else {
                updated.copy(currentMp = newMp, roomRegenMpRooms = remaining)
            }
        }

        if (updated.deathDebuffMinutes > 0.0) {
            val remaining = (updated.deathDebuffMinutes - roomTimeMinutes).coerceAtLeast(0.0)
            updated = if (remaining <= 0.0) {
                updated.copy(
                    deathDebuffMinutes = 0.0,
                    deathDebuffStacks = 0,
                    deathXpPenaltyMinutes = 0.0,
                    deathXpPenaltyPct = 0.0
                )
            } else {
                updated.copy(
                    deathDebuffMinutes = remaining,
                    deathXpPenaltyMinutes = remaining
                )
            }
        }

        if (updated.roomEffectRooms > 0) {
            val remainingRooms = updated.roomEffectRooms - 1
            updated = if (remainingRooms <= 0) {
                updated.copy(roomEffectRooms = 0, roomEffectMultiplier = 1.0)
            } else {
                updated.copy(roomEffectRooms = remainingRooms)
            }
        }

        if (updated.roomAttrRooms > 0) {
            val remaining = updated.roomAttrRooms - 1
            updated = if (remaining <= 0) {
                updated.copy(roomAttrRooms = 0, roomAttrBonus = Attributes())
            } else {
                updated.copy(roomAttrRooms = remaining)
            }
        }

        if (updated.roomDerivedRooms > 0) {
            val remaining = updated.roomDerivedRooms - 1
            updated = if (remaining <= 0) {
                updated.copy(roomDerivedRooms = 0, roomDerivedAdd = DerivedStats(), roomDerivedMult = DerivedStats())
            } else {
                updated.copy(roomDerivedRooms = remaining)
            }
        }

        if (updated.roomAttrRollRooms > 0) {
            val remaining = updated.roomAttrRollRooms - 1
            val chosen = engine.pickRandomAttribute()
            val bonus = addAttr(Attributes(), chosen, max(1, updated.roomAttrRollAmount))
            val updatedBonus = updated.runAttrBonus + bonus
            updated = if (remaining <= 0) {
                updated.copy(runAttrBonus = updatedBonus, roomAttrRollRooms = 0, roomAttrRollAmount = 0)
            } else {
                updated.copy(runAttrBonus = updatedBonus, roomAttrRollRooms = remaining)
            }
        }

        return updated
    }

    private fun advanceOutOfCombatTime(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        minutes: Double
    ): PlayerState {
        if (minutes <= 0.0) return player
        val stats = computePlayerStats(player, itemInstances)
        val newHp = (player.currentHp + stats.derived.hpRegen * minutes).coerceAtMost(stats.derived.hpMax)
        val newMp = (player.currentMp + stats.derived.mpRegen * minutes).coerceAtMost(stats.derived.mpMax)
        var updated = player.copy(currentHp = newHp, currentMp = newMp)

        if (updated.deathDebuffMinutes > 0.0) {
            val remaining = (updated.deathDebuffMinutes - minutes).coerceAtLeast(0.0)
            updated = if (remaining <= 0.0) {
                updated.copy(
                    deathDebuffMinutes = 0.0,
                    deathDebuffStacks = 0,
                    deathXpPenaltyMinutes = 0.0,
                    deathXpPenaltyPct = 0.0
                )
            } else {
                updated.copy(
                    deathDebuffMinutes = remaining,
                    deathXpPenaltyMinutes = remaining
                )
            }
        }
        if (updated.deathDebuffMinutes <= 0.0 && updated.deathXpPenaltyMinutes > 0.0) {
            val remainingXpPenalty = (updated.deathXpPenaltyMinutes - minutes).coerceAtLeast(0.0)
            updated = if (remainingXpPenalty <= 0.0) {
                updated.copy(deathXpPenaltyMinutes = 0.0, deathXpPenaltyPct = 0.0)
            } else {
                updated.copy(deathXpPenaltyMinutes = remainingXpPenalty)
            }
        }

        return updated
    }

    private fun visitTavern(state: GameState): GameState {
        var updatedState = state

        while (true) {
            val player = updatedState.player
            val stats = computePlayerStats(player, updatedState.itemInstances)
            val stacks = player.deathDebuffStacks
            val costRest = max(10, 12 + player.level * 2)
            val costSleep = max(25, 30 + player.level * 4)
            val costPurifyOne = if (stacks > 0) 30 + (stacks - 1) * 15 else 0
            val costPurifyAll = if (stacks > 0) 80 + (stacks - 1) * 40 else 0

            println("\nTaverna:")
            println("1. Descansar (cura 25% HP/MP) - custo $costRest")
            println("2. Dormir (cura total) - custo $costSleep")
            println("3. Purificar 1 stack de debuff - custo $costPurifyOne")
            println("4. Purificar tudo - custo $costPurifyAll")
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 4)) {
                1 -> {
                    if (player.gold < costRest) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    var updatedPlayer = player.copy(
                        gold = player.gold - costRest,
                        currentHp = min(stats.derived.hpMax, player.currentHp + stats.derived.hpMax * tavernRestHealPct),
                        currentMp = min(stats.derived.mpMax, player.currentMp + stats.derived.mpMax * tavernRestHealPct)
                    )
                    updatedPlayer = applyAchievementUpdate(
                        achievementTracker.onGoldSpent(updatedPlayer, costRest.toLong())
                    )
                    println("Voce descansou na taverna.")
                    updatedState = updatedState.copy(player = updatedPlayer)
                    autoSave(updatedState)
                }
                2 -> {
                    if (player.gold < costSleep) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    var updatedPlayer = player.copy(
                        gold = player.gold - costSleep,
                        currentHp = stats.derived.hpMax,
                        currentMp = stats.derived.mpMax
                    )
                    updatedPlayer = applyAchievementUpdate(
                        achievementTracker.onGoldSpent(updatedPlayer, costSleep.toLong())
                    )
                    updatedPlayer = applyAchievementUpdate(
                        achievementTracker.onFullRestSleep(updatedPlayer)
                    )
                    println("Voce dormiu e acordou renovado.")
                    updatedState = updatedState.copy(player = updatedPlayer)
                    autoSave(updatedState)
                }
                3 -> {
                    if (stacks <= 0) {
                        println("Nenhum debuff ativo.")
                        continue
                    }
                    if (player.gold < costPurifyOne) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    val newStacks = max(0, stacks - 1)
                    val capMinutes = if (newStacks == 0) {
                        0.0
                    } else {
                        deathDebuffBaseMinutes + (newStacks - 1) * deathDebuffExtraMinutes
                    }
                    val newMinutes = if (newStacks == 0) 0.0 else min(player.deathDebuffMinutes, capMinutes)
                    var updatedPlayer = player.copy(
                        gold = player.gold - costPurifyOne,
                        deathDebuffStacks = newStacks,
                        deathDebuffMinutes = newMinutes,
                        deathXpPenaltyMinutes = newMinutes,
                        deathXpPenaltyPct = if (newStacks > 0) deathXpPenaltyPct else 0.0
                    )
                    updatedPlayer = applyAchievementUpdate(
                        achievementTracker.onGoldSpent(updatedPlayer, costPurifyOne.toLong())
                    )
                    println("Um stack do debuff foi removido.")
                    updatedState = updatedState.copy(player = updatedPlayer)
                    autoSave(updatedState)
                }
                4 -> {
                    if (stacks <= 0) {
                        println("Nenhum debuff ativo.")
                        continue
                    }
                    if (player.gold < costPurifyAll) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    var updatedPlayer = player.copy(
                        gold = player.gold - costPurifyAll,
                        deathDebuffStacks = 0,
                        deathDebuffMinutes = 0.0,
                        deathXpPenaltyMinutes = 0.0,
                        deathXpPenaltyPct = 0.0
                    )
                    updatedPlayer = applyAchievementUpdate(
                        achievementTracker.onGoldSpent(updatedPlayer, costPurifyAll.toLong())
                    )
                    println("Purificacao completa realizada.")
                    updatedState = updatedState.copy(player = updatedPlayer)
                    autoSave(updatedState)
                }
                null -> return updatedState
            }
        }
    }

    private fun showStatus(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) {
        val stats = computePlayerStats(player, itemInstances)
        val className = repo.classes[player.classId]?.name ?: player.classId
        val secondClassName = player.subclassId?.let { repo.subclasses[it]?.name ?: it } ?: "-"
        val specializationName = player.specializationId?.let { repo.specializations[it]?.name ?: it } ?: "-"
        val slotLimit = InventorySystem.inventoryLimit(player, itemInstances, engine.itemRegistry)
        val slotUsed = InventorySystem.slotsUsed(player, itemInstances, engine.itemRegistry)
        println("Nome ${uiColor(player.name, ansiUiName)} | Nivel ${uiColor(player.level.toString(), ansiUiLevel)} | XP ${player.xp}/${Progression.xpForNext(player.level)}")
        println("Classe base $className | 2a classe $secondClassName | Especializacao $specializationName")
        val questCurrencyLabel = if (player.questCurrency > 0) {
            " | Moeda de Quest ${player.questCurrency}"
        } else {
            ""
        }
        println(
            "HP ${uiColor("${format(player.currentHp)}/${format(stats.derived.hpMax)}", ansiUiHp)} | " +
                "MP ${uiColor("${format(player.currentMp)}/${format(stats.derived.mpMax)}", ansiUiMp)} | " +
                "Ouro ${uiColor(player.gold.toString(), ansiUiGold)} | " +
                "CASH ${uiColor(player.premiumCash.toString(), ansiUiCash)}$questCurrencyLabel"
        )
        println("Inventario: $slotUsed/$slotLimit slots")
        val hpEta = etaMinutesToFull(player.currentHp, stats.derived.hpMax, stats.derived.hpRegen)
        val mpEta = etaMinutesToFull(player.currentMp, stats.derived.mpMax, stats.derived.mpRegen)
        if (hpEta != null || mpEta != null) {
            val hpLabel = hpEta?.let { "${format(it)} min" } ?: "--"
            val mpLabel = mpEta?.let { "${format(it)} min" } ?: "--"
            println("Regen natural: HP cheio em $hpLabel | MP cheio em $mpLabel")
        }
        println(
            "Skills: MIN ${skillLevel(player, SkillType.MINING)} | " +
                "GAT ${skillLevel(player, SkillType.GATHERING)} | " +
                "WOOD ${skillLevel(player, SkillType.WOODCUTTING)} | " +
                "FISH ${skillLevel(player, SkillType.FISHING)} | " +
                "BS ${skillLevel(player, SkillType.BLACKSMITH)} | " +
                "ALCH ${skillLevel(player, SkillType.ALCHEMIST)} | " +
                "COOK ${skillLevel(player, SkillType.COOKING)}"
        )
        println("STR ${stats.attributes.str} AGI ${stats.attributes.agi} DEX ${stats.attributes.dex} VIT ${stats.attributes.vit} INT ${stats.attributes.`int`} SPR ${stats.attributes.spr} LUK ${stats.attributes.luk}")
    }

    private fun showDebuff(player: PlayerState) {
        if (player.deathDebuffStacks > 0) {
            val minutes = "%.1f".format(player.deathDebuffMinutes)
            println("Debuff de morte: -${(deathDebuffPerStack * 100).toInt()}% atributos x${player.deathDebuffStacks} (${minutes} min)")
        }
        if (player.deathXpPenaltyMinutes > 0.0 && player.deathXpPenaltyPct > 0.0) {
            println("Penalidade de XP: -${format(player.deathXpPenaltyPct)}% (${format(player.deathXpPenaltyMinutes)} min)")
        }
        if (player.roomEffectRooms > 0) {
            val percent = ((player.roomEffectMultiplier - 1.0) * 100).toInt()
            val label = if (percent >= 0) "+$percent%" else "$percent%"
            println("Efeito temporario: $label atributos (${player.roomEffectRooms} salas)")
        }
    }

    private fun showCharacterSummary(name: String, raceDef: RaceDef, classDef: ClassDef) {
        println("\nCriacao de personagem:")
        println("Nome: $name")
        println("Raca: ${raceDef.name}")
        println("Classe: ${classDef.name}")
    }

    private fun chooseRaceForCreation(): RaceDef? {
        val races = characterCreationPreview.availableRaces()
        if (races.isEmpty()) {
            println("Nenhuma raca cadastrada.")
            return null
        }
        while (true) {
            println("\n=== Selecao de Raca ===")
            races.forEachIndexed { index, race ->
                println("${index + 1}. ${race.name}")
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha uma raca para visualizar: ", 1, races.size) ?: return null
            val selected = races[choice - 1]
            if (openRacePreviewAndConfirm(selected)) return selected
        }
    }

    private fun openRacePreviewAndConfirm(raceDef: RaceDef): Boolean {
        val preview = characterCreationPreview.buildRacePreview(raceDef)
        while (true) {
            println("\n=== Raca | ${preview.race.name} ===")
            if (preview.race.description.isNotBlank()) {
                println(preview.race.description)
            }

            println("\nAtributos iniciais:")
            if (preview.initialAttributes.isEmpty()) {
                println("- Nenhum bonus de atributo positivo.")
            } else {
                preview.initialAttributes.forEach { line ->
                    println("- ${line.code} (${line.label}): +${line.value}")
                }
            }

            println("\nCrescimento por nivel:")
            if (preview.growthAttributes.isEmpty()) {
                println("- Nenhum crescimento de atributo positivo.")
            } else {
                preview.growthAttributes.forEach { line ->
                    println("- ${line.code} (${line.label}): +${line.value}")
                }
            }

            println("\nClasses sugeridas:")
            if (preview.suggestedClasses.isEmpty()) {
                println("- Nenhuma classe disponivel para recomendacao.")
            } else {
                preview.suggestedClasses.forEachIndexed { index, suggestion ->
                    println(
                        "${index + 1}. ${suggestion.name} " +
                            "(score ${format(suggestion.score)}) - ${suggestion.reason}"
                    )
                }
            }

            println("\n1. Confirmar raca")
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 1)) {
                1 -> return true
                null -> return false
            }
        }
    }

    private fun chooseClassForCreation(): ClassDef? {
        val classes = characterCreationPreview.availableClasses()
        if (classes.isEmpty()) {
            println("Nenhuma classe cadastrada.")
            return null
        }
        while (true) {
            println("\n=== Selecao de Classe ===")
            classes.forEachIndexed { index, classDef ->
                println("${index + 1}. ${classDef.name}")
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha uma classe para visualizar: ", 1, classes.size) ?: return null
            val selected = classes[choice - 1]
            if (openClassPreviewAndConfirm(selected)) return selected
        }
    }

    private fun openClassPreviewAndConfirm(classDef: ClassDef): Boolean {
        val preview = characterCreationPreview.buildClassPreview(classDef)
        while (true) {
            println("\n=== Classe | ${preview.clazz.name} ===")
            if (preview.clazz.description.isNotBlank()) {
                println(preview.clazz.description)
            }

            println("\nRacas sugeridas:")
            if (preview.suggestedRaces.isEmpty()) {
                println("- Nenhuma raca disponivel para recomendacao.")
            } else {
                preview.suggestedRaces.forEachIndexed { index, suggestion ->
                    println(
                        "${index + 1}. ${suggestion.name} " +
                            "(score ${format(suggestion.score)}) - ${suggestion.reason}"
                    )
                }
            }

            if (preview.initialAttributes.isNotEmpty()) {
                println("\nAtributos iniciais:")
                preview.initialAttributes.forEach { line ->
                    println("- ${line.code} (${line.label}): ${formatSigned(line.value)}")
                }
            }

            if (preview.growthAttributes.isNotEmpty()) {
                println("\nCrescimento por nivel:")
                preview.growthAttributes.forEach { line ->
                    println("- ${line.code} (${line.label}): ${formatSigned(line.value)}")
                }
            }

            println("\n2as classes:")
            if (preview.secondClasses.isEmpty()) {
                println("- Nenhuma 2a classe vinculada.")
            } else {
                preview.secondClasses.forEach { secondClass ->
                    val specializationLabel = if (secondClass.specializations.isEmpty()) {
                        "sem especializacao"
                    } else {
                        secondClass.specializations.joinToString(", ")
                    }
                    println("- ${secondClass.name} -> $specializationLabel")
                }
            }

            println("\n1. Confirmar classe")
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 1)) {
                1 -> return true
                null -> return false
            }
        }
    }

    private fun allocateAttributesWithBonuses(
        points: Int,
        raceDef: RaceDef,
        classDef: ClassDef
    ): InitialAttributeAllocation {
        while (true) {
            val allocation = runGuidedAttributeDistribution(points, raceDef, classDef)
            renderAttributeDistributionSummary(
                base = allocation.baseAttributes,
                raceDef = raceDef,
                classDef = classDef,
                remainingPoints = allocation.unspentPoints
            )
            if (confirmAttributeDistribution()) {
                return allocation
            }
            println("Redistribuindo atributos...")
        }
    }

    private fun runGuidedAttributeDistribution(
        points: Int,
        raceDef: RaceDef,
        classDef: ClassDef
    ): InitialAttributeAllocation {
        var state = AttributeDistributionState(
            allocated = Attributes(),
            remainingPoints = points.coerceAtLeast(0)
        )
        for (meta in attributeMeta) {
            state = promptAttributeStep(meta, state, raceDef, classDef)
        }
        return InitialAttributeAllocation(
            baseAttributes = state.allocated,
            unspentPoints = state.remainingPoints
        )
    }

    private fun promptAttributeStep(
        attribute: AttrMeta,
        state: AttributeDistributionState,
        raceDef: RaceDef,
        classDef: ClassDef
    ): AttributeDistributionState {
        while (true) {
            renderAttributeStep(attribute, state, raceDef, classDef)
            val toAllocate = readAttributeStepInput(state.remainingPoints)
            return state.copy(
                allocated = applyAttributePoints(state.allocated, attribute.code, toAllocate),
                remainingPoints = (state.remainingPoints - toAllocate).coerceAtLeast(0)
            )
        }
    }

    private fun renderAttributeStep(
        attribute: AttrMeta,
        state: AttributeDistributionState,
        raceDef: RaceDef,
        classDef: ClassDef
    ) {
        val baseValue = getAttr(state.allocated, attribute.code)
        val raceBonus = getAttr(raceDef.bonuses.attributes, attribute.code)
        val classBonus = getAttr(classDef.bonuses.attributes, attribute.code)
        val total = baseValue + raceBonus + classBonus
        println("\n----------------------------------")
        println("Distribuicao de atributos")
        println()
        println("Atributos restantes: ${state.remainingPoints}")
        println()
        println("${attribute.code} (${attribute.label})")
        println()
        println(
            "Base: $baseValue | Bonus racial: ${formatSigned(raceBonus)} | " +
                "Bonus classe: ${formatSigned(classBonus)} | Total atual: $total"
        )
        println()
        println("Descricao:")
        generateAttributeDescription(attribute).forEach { println("- $it") }
        println()
        println("Impacto na gameplay:")
        generateGameplayImpact(attribute).forEach { println("- $it") }
        println()
    }

    private fun readAttributeStepInput(remainingPoints: Int): Int {
        while (true) {
            print("Distribuir pontos (0 - $remainingPoints): ")
            val input = readLine()?.trim() ?: throw InputClosedException()
            val validated = validateInput(input, 0, remainingPoints)
            if (validated != null) return validated
            println("Entrada invalida. Informe um numero entre 0 e $remainingPoints.")
        }
    }

    private fun renderAttributeDistributionSummary(
        base: Attributes,
        raceDef: RaceDef,
        classDef: ClassDef,
        remainingPoints: Int
    ) {
        println("\nResumo final:")
        println()
        for (meta in attributeMeta) {
            val baseValue = getAttr(base, meta.code)
            val raceBonus = getAttr(raceDef.bonuses.attributes, meta.code)
            val classBonus = getAttr(classDef.bonuses.attributes, meta.code)
            val totalBonus = raceBonus + classBonus
            val total = baseValue + totalBonus
            println("${meta.code} = $baseValue (${formatSigned(totalBonus)}) -> Total $total")
            println("   Bonus racial: ${formatSigned(raceBonus)} | Bonus classe: ${formatSigned(classBonus)}")
        }
        if (remainingPoints > 0) {
            println("\nPontos nao distribuidos: $remainingPoints")
        }
    }

    private fun confirmAttributeDistribution(): Boolean {
        while (true) {
            print("Confirmar? (S/N): ")
            val input = readLine()?.trim()?.lowercase() ?: throw InputClosedException()
            when (input) {
                "s", "sim" -> return true
                "n", "nao" -> return false
            }
            println("Resposta invalida. Digite S para confirmar ou N para redistribuir.")
        }
    }

    private fun applyAttributePoints(current: Attributes, attributeCode: String, points: Int): Attributes {
        return addAttr(current, attributeCode, points.coerceAtLeast(0))
    }

    private fun generateAttributeDescription(attribute: AttrMeta): List<String> = when (attribute.code) {
        "STR" -> listOf(
            "Contribui para dano fisico base (+2.5 por ponto).",
            "Aumenta penetracao fisica (+0.5 por ponto).",
            "Contribui para defesa fisica (+0.5 por ponto)."
        )
        "AGI" -> listOf(
            "Aumenta velocidade de ataque base (+0.02 por ponto).",
            "Aumenta esquiva/evasao (+0.8 por ponto).",
            "Aumenta velocidade de movimento (+0.05 por ponto)."
        )
        "DEX" -> listOf(
            "Contribui para dano fisico base (+1.2 por ponto).",
            "Aumenta precisao/acerto (+1.5 por ponto).",
            "Aumenta chance de critico (+0.3) e dano critico (+0.5)."
        )
        "VIT" -> listOf(
            "Aumenta HP maximo (+12 por ponto).",
            "Aumenta defesa fisica (+1.5 por ponto).",
            "Aumenta regeneracao de HP (+0.2 por ponto)."
        )
        "INT" -> listOf(
            "Aumenta dano magico base (+3.0 por ponto).",
            "Aumenta penetracao magica (+0.7 por ponto).",
            "Contribui para defesa magica (+0.7 por ponto)."
        )
        "SPR" -> listOf(
            "Aumenta MP maximo (+10 por ponto).",
            "Aumenta defesa magica (+1.8 por ponto).",
            "Aumenta regeneracao de MP (+0.3) e reducao de cooldown (+0.15%)."
        )
        "LUK" -> listOf(
            "Aumenta chance de critico (+0.2 por ponto).",
            "Aumenta vampirismo (1% a cada 10 pontos).",
            "Aumenta bonus de drop (+0.2% por ponto)."
        )
        else -> listOf("Sem descricao configurada.")
    }

    private fun generateGameplayImpact(attribute: AttrMeta): List<String> = when (attribute.code) {
        "STR" -> listOf(
            "Mais STR aumenta o dano de ataques fisicos e melhora dano contra alvos com defesa fisica.",
            "Builds corpo a corpo sentem ganho imediato em consistencia de dano."
        )
        "AGI" -> listOf(
            "Mais AGI acelera a barra semi-ATB porque o fill rate usa attackSpeed.",
            "Tambem reduz a chance de ser acertado por ataques com teste de precisao."
        )
        "DEX" -> listOf(
            "Mais DEX reduz erros (accuracy vs evasion) e aumenta frequencia de criticos.",
            "Tambem melhora a chance de fuga no combate (peso secundario na formula)."
        )
        "VIT" -> listOf(
            "Mais VIT aumenta sobrevivencia por HP e mitigacao fisica.",
            "Regeneracao de HP fora de combate e em descansos tambem escala."
        )
        "INT" -> listOf(
            "Mais INT aumenta dano de habilidades/ataques magicos.",
            "Tambem ajuda a atravessar defesa magica inimiga via penetracao magica."
        )
        "SPR" -> listOf(
            "Mais SPR aumenta sustain de mana (MP maximo + regen).",
            "Tambem reduz cooldowns efetivos e melhora resistencia a dano magico."
        )
        "LUK" -> listOf(
            "Mais LUK melhora criticos e gera sustain por vampirismo.",
            "Tambem aumenta chance de fuga (junto com AGI/DEX) e melhora chance de drop."
        )
        else -> listOf("Sem impacto configurado.")
    }

    private fun synchronizeClock(state: GameState): GameState {
        val now = System.currentTimeMillis()
        val last = if (state.lastClockSyncEpochMs > 0L) state.lastClockSyncEpochMs else now
        val elapsedMs = (now - last).coerceAtLeast(0L)
        if (elapsedMs < clockSyncEpsilonMs) {
            return if (state.lastClockSyncEpochMs > 0L) {
                state
            } else {
                state.copy(lastClockSyncEpochMs = now)
            }
        }
        val minutes = elapsedMs / 60000.0
        val updatedPlayer = advanceOutOfCombatTime(state.player, state.itemInstances, minutes)
        return state.copy(
            player = updatedPlayer,
            worldTimeMinutes = state.worldTimeMinutes + minutes,
            lastClockSyncEpochMs = now
        )
    }

    private fun showClock(@Suppress("UNUSED_PARAMETER") state: GameState) {
        val now = Instant.now().atZone(questZoneId)
        val date = now.toLocalDate()
        val time = now.toLocalTime().withNano(0)
        println("Clock sistema: $date $time")
    }

    private fun formatClock(worldMinutes: Double): String {
        val total = worldMinutes.toInt().coerceAtLeast(0)
        val minutesOfDay = total % 1440
        val hour = minutesOfDay / 60
        val minute = minutesOfDay % 60
        return "%02d:%02d".format(hour, minute)
    }

    private fun runProgressBar(label: String, durationSeconds: Double) {
        val totalSeconds = durationSeconds.coerceAtLeast(1.0)
        val steps = 20
        val stepMs = ((totalSeconds * 1000.0) / steps).toLong().coerceAtLeast(30L)
        for (step in 1..steps) {
            Thread.sleep(stepMs)
            val progress = step.toDouble() / steps
            val filled = (progress * 20).toInt().coerceIn(0, 20)
            val bar = "#".repeat(filled) + "-".repeat(20 - filled)
            val pct = (progress * 100).toInt()
            print("\r$label [$bar] ${pct}%")
        }
        println()
    }

    private fun combatActionBar(runtime: rpg.combat.CombatRuntimeState, width: Int = 18): String {
        val threshold = runtime.actionThreshold.coerceAtLeast(1.0)
        val pct = ((runtime.actionBar / threshold) * 100.0).coerceIn(0.0, 100.0)
        val filled = ((pct / 100.0) * width).toInt().coerceIn(0, width)
        val bar = "#".repeat(filled) + "-".repeat(width - filled)
        val suffix = if (runtime.state == rpg.combat.CombatState.READY) "PRONTO" else "${pct.toInt()}%"
        return combatColor("[$bar] $suffix", combatStateColor(runtime.state))
    }

    private fun combatCastBar(runtime: rpg.combat.CombatRuntimeState, width: Int = 18): String {
        val total = runtime.castTotal.coerceAtLeast(0.1)
        val progress = ((total - runtime.castRemaining) / total).coerceIn(0.0, 1.0)
        val filled = (progress * width).toInt().coerceIn(0, width)
        val bar = "#".repeat(filled) + "-".repeat(width - filled)
        return combatColor("[$bar] ${(progress * 100.0).toInt()}%", ansiCombatCasting)
    }

    private fun combatStateLabel(state: rpg.combat.CombatState): String = when (state) {
        rpg.combat.CombatState.IDLE -> "Carregando"
        rpg.combat.CombatState.READY -> "Pronto"
        rpg.combat.CombatState.CASTING -> "Castando"
        rpg.combat.CombatState.STUNNED -> "Atordoado"
        rpg.combat.CombatState.DEAD -> "Morto"
    }

    private fun combatStateColor(state: rpg.combat.CombatState): String = when (state) {
        rpg.combat.CombatState.READY -> ansiCombatReady
        rpg.combat.CombatState.CASTING -> ansiCombatCasting
        rpg.combat.CombatState.STUNNED, rpg.combat.CombatState.DEAD -> ansiCombatBlocked
        else -> ansiCombatLoading
    }

    private fun combatColor(text: String, colorCode: String): String = "$colorCode$text$ansiCombatReset"

    private fun skillLevel(player: PlayerState, skill: SkillType): Int {
        return engine.skillSystem.snapshot(player, skill).level
    }

    private fun canonicalItemId(
        itemId: String,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): String {
        return itemInstances[itemId]?.templateId ?: itemId
    }

    private fun formatSignedDouble(value: Double): String {
        val rounded = "%.1f".format(value)
        return if (value >= 0.0) "+$rounded" else rounded
    }

    private fun format(value: Double): String = "%.1f".format(value)

    private fun etaMinutesToFull(current: Double, maxValue: Double, regenPerMinute: Double): Double? {
        if (regenPerMinute <= 0.0) return null
        if (current >= maxValue) return 0.0
        return ((maxValue - current) / regenPerMinute).coerceAtLeast(0.0)
    }

    private fun synchronizeQuestBoard(
        board: rpg.quest.QuestBoardState,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): rpg.quest.QuestBoardState {
        val synced = engine.questBoardEngine.synchronize(board, player)
        return engine.questProgressTracker.synchronizeCollectProgressFromInventory(
            board = synced,
            inventory = player.inventory,
            itemInstanceTemplateById = { id -> itemInstances[id]?.templateId }
        )
    }

    private fun hasReadyToClaim(board: rpg.quest.QuestBoardState): Boolean {
        return hasReadyToClaim(board.dailyQuests) ||
            hasReadyToClaim(board.weeklyQuests) ||
            hasReadyToClaim(board.monthlyQuests) ||
            hasReadyToClaim(board.acceptedQuests)
    }

    private fun hasReadyToClaim(quests: List<QuestInstance>): Boolean {
        return quests.any { it.status == QuestStatus.READY_TO_CLAIM }
    }

    private fun hasAchievementRewardReady(player: PlayerState): Boolean {
        return achievementService.hasClaimableRewards(player)
    }

    private fun hasUnspentAttributePoints(player: PlayerState): Boolean {
        return player.unspentAttrPoints > 0
    }

    private fun hasTalentPointsAvailable(player: PlayerState): Boolean {
        val trees = talentTreeService.activeTrees(player, repo.talentTreesV2.values)
        if (trees.isEmpty()) return false
        val ledger = talentTreeService.pointsLedger(player, trees)
        return when (ledger.mode) {
            rpg.model.TalentPointMode.SHARED_POOL -> ledger.sharedAvailable > 0
            rpg.model.TalentPointMode.PER_TREE -> ledger.availableByTree.values.any { it > 0 }
        }
    }

    private fun menuAlert(active: Boolean): String {
        return if (active) uiColor("(!)", ansiQuestAlert) else ""
    }

    private fun labelWithAlert(baseLabel: String, alert: String): String {
        return if (alert.isBlank()) baseLabel else "$baseLabel $alert"
    }

    private fun applyAchievementUpdate(update: AchievementUpdate): PlayerState {
        showAchievementNotifications(update.unlockedTiers)
        return update.player
    }

    private fun showAchievementNotifications(notifications: List<AchievementTierUnlockedNotification>) {
        if (notifications.isEmpty()) return
        for (notification in notifications) {
            println(uiColor("(!) Conquista concluida (!)", ansiQuestAlert))
            println(notification.displayName)
            println(notification.displayDescription)
            println("Recompensa disponivel: ${notification.rewardGold} ouro")
        }
    }

    private fun uiColor(text: String, colorCode: String): String = "$colorCode$text$ansiCombatReset"

    private fun readNonEmpty(prompt: String): String {
        while (true) {
            print(prompt)
            val input = readLine()?.trim() ?: throw InputClosedException()
            if (input.isNotEmpty()) return input
        }
    }

    private fun readMenuChoice(prompt: String, min: Int, max: Int): Int? {
        while (true) {
            print(prompt)
            val input = (readLine()?.trim() ?: throw InputClosedException()).lowercase()
            if (input == "x") return null
            val value = input.toIntOrNull()
            if (value != null && value in min..max) return value
        }
    }

    private fun readInt(prompt: String, min: Int, max: Int): Int {
        while (true) {
            print(prompt)
            val input = readLine()?.trim() ?: throw InputClosedException()
            val value = validateInput(input, min, max)
            if (value != null) return value
        }
    }

    private fun validateInput(input: String, min: Int, max: Int): Int? {
        val value = input.toIntOrNull() ?: return null
        if (value < min) return null
        if (value > max) return null
        return value
    }

    private fun <T> choose(label: String, options: List<T>, nameOf: (T) -> String): T {
        if (options.isEmpty()) error("Nenhuma opcao disponivel para $label")
        println("\n$label:")
        options.forEachIndexed { index, option ->
            println("${index + 1}. ${nameOf(option)}")
        }
        val choice = readInt("Escolha: ", 1, options.size)
        return options[choice - 1]
    }

    private fun allocateAttributes(points: Int): Attributes {
        var remaining = points
        var attrs = Attributes()
        println("\nDistribua $points pontos.")
        for (meta in attributeMeta) {
            if (remaining <= 0) break
            val add = readInt("${meta.code} (${meta.label}) (0-$remaining): ", 0, remaining)
            remaining -= add
            attrs = addAttr(attrs, meta.code, add)
        }
        return attrs
    }

    private fun getAttr(attrs: Attributes, code: String): Int = when (code) {
        "STR" -> attrs.str
        "AGI" -> attrs.agi
        "DEX" -> attrs.dex
        "VIT" -> attrs.vit
        "INT" -> attrs.`int`
        "SPR" -> attrs.spr
        "LUK" -> attrs.luk
        else -> 0
    }

    private fun addAttr(attrs: Attributes, code: String, delta: Int): Attributes = when (code) {
        "STR" -> attrs.copy(str = attrs.str + delta)
        "AGI" -> attrs.copy(agi = attrs.agi + delta)
        "DEX" -> attrs.copy(dex = attrs.dex + delta)
        "VIT" -> attrs.copy(vit = attrs.vit + delta)
        "INT" -> attrs.copy(`int` = attrs.`int` + delta)
        "SPR" -> attrs.copy(spr = attrs.spr + delta)
        "LUK" -> attrs.copy(luk = attrs.luk + delta)
        else -> attrs
    }

    private fun totalAttrBonus(raceDef: RaceDef, classDef: ClassDef, code: String): Int {
        val raceBonus = getAttr(raceDef.bonuses.attributes, code)
        val classBonus = getAttr(classDef.bonuses.attributes, code)
        return raceBonus + classBonus
    }

    private fun formatSigned(value: Int): String = if (value >= 0) "+$value" else value.toString()

    private data class BattleOutcome(
        val playerAfter: PlayerState,
        val itemInstances: Map<String, rpg.model.ItemInstance> = emptyMap(),
        val victory: Boolean,
        val escaped: Boolean = false,
        val collectedItems: Map<String, Int> = emptyMap()
    )

    private data class CombatSkillOption(
        val id: String,
        val name: String,
        val mpCost: Double,
        val cooldownSeconds: Double,
        val castTimeSeconds: Double,
        val damageMultiplier: Double,
        val preferMagic: Boolean?,
        val onHitStatuses: List<rpg.model.CombatStatusApplyDef>,
        val selfHealFlat: Double,
        val selfHealPctMaxHp: Double,
        val ammoCost: Int,
        val rank: Int,
        val maxRank: Int,
        val aoeUnlockRank: Int,
        val aoeBonusDamagePct: Double,
        val available: Boolean,
        val unavailableReason: String?,
        val cooldownRemainingSeconds: Double
    )

    private sealed interface CombatMenuAction {
        data class BasicAttack(
            val preferMagic: Boolean?,
            val available: Boolean,
            val unavailableReason: String?
        ) : CombatMenuAction
        data class SkillAttack(val skill: CombatSkillOption) : CombatMenuAction
    }

    private enum class DecisionView {
        MAIN,
        ATTACK,
        ITEM
    }

    private data class AttrMeta(
        val code: String,
        val label: String
    )

    private data class AttributeDistributionState(
        val allocated: Attributes,
        val remainingPoints: Int
    )

    private data class InitialAttributeAllocation(
        val baseAttributes: Attributes,
        val unspentPoints: Int
    )

    private data class ShopPurchaseResult(
        val success: Boolean,
        val player: PlayerState,
        val itemInstances: Map<String, rpg.model.ItemInstance>,
        val message: String
    )

    private data class UseItemResult(
        val player: PlayerState,
        val itemInstances: Map<String, rpg.model.ItemInstance>
    )

    private data class InventoryStack(
        val sampleItemId: String,
        val quantity: Int,
        val itemIds: List<String>,
        val item: rpg.item.ResolvedItem
    )

    private data class InventoryFilter(
        val type: ItemType? = null,
        val minimumRarity: ItemRarity? = null
    )

    private data class AmmoStack(
        val templateId: String,
        val sampleItemId: String,
        val quantity: Int,
        val itemIds: List<String>,
        val item: rpg.item.ResolvedItem
    )

    private data class EquipPreviewTarget(
        val slotKey: String,
        val currentItemId: String?
    )

    private data class EquipComparisonPreview(
        val slotKey: String,
        val replacedItem: rpg.item.ResolvedItem?,
        val before: ComputedStats,
        val after: ComputedStats
    )

    private data class DeathPenaltyResult(
        val player: PlayerState,
        val itemInstances: Map<String, rpg.model.ItemInstance>
    )

    private data class RunFinalizeResult(
        val player: PlayerState,
        val itemInstances: Map<String, rpg.model.ItemInstance>
    )

    private data class EventRoomOutcome(
        val player: PlayerState,
        val battleOutcome: BattleOutcome? = null,
        val itemInstances: Map<String, rpg.model.ItemInstance>? = null,
        val questBoard: rpg.quest.QuestBoardState
    )

    private data class QuestUiSnapshot(
        val player: PlayerState,
        val itemInstances: Map<String, rpg.model.ItemInstance>,
        val board: rpg.quest.QuestBoardState
    )

}
