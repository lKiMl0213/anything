package rpg.application.creation

import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.io.DataRepository
import rpg.model.Attributes
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.WorldState

class CharacterCreationCommandService(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val queryService: CharacterCreationQueryService
) {
    fun setName(draft: CharacterCreationDraft, name: String): CharacterCreationDraft {
        return draft.copy(name = name.trim())
    }

    fun cycleName(draft: CharacterCreationDraft): CharacterCreationDraft {
        return draft.copy(name = queryService.cycleName(draft.name))
    }

    fun selectRace(draft: CharacterCreationDraft, raceId: String): CharacterCreationDraft {
        return draft.copy(raceId = raceId)
    }

    fun selectClass(draft: CharacterCreationDraft, classId: String): CharacterCreationDraft {
        return draft.copy(classId = classId)
    }

    fun increaseAttribute(draft: CharacterCreationDraft, code: String): CharacterCreationDraft {
        if (draft.remainingPoints <= 0) return draft
        return draft.copy(allocated = applyAttrDelta(draft.allocated, code, 1))
    }

    fun decreaseAttribute(draft: CharacterCreationDraft, code: String): CharacterCreationDraft {
        val current = queryService.readAllocated(draft.allocated, code)
        if (current <= 0) return draft
        return draft.copy(allocated = applyAttrDelta(draft.allocated, code, -1))
    }

    fun setAttributeAllocation(draft: CharacterCreationDraft, code: String, allocated: Int): CharacterCreationDraft {
        val normalizedTarget = allocated.coerceAtLeast(0)
        val current = queryService.readAllocated(draft.allocated, code)
        val maxAllowed = current + draft.remainingPoints
        val clampedTarget = normalizedTarget.coerceIn(0, maxAllowed)
        val delta = clampedTarget - current
        return if (delta == 0) {
            draft
        } else {
            draft.copy(allocated = applyAttrDelta(draft.allocated, code, delta))
        }
    }

    fun canConfirm(draft: CharacterCreationDraft): Boolean {
        return queryService.raceById(draft.raceId) != null &&
            queryService.classById(draft.classId) != null &&
            draft.name.isNotBlank()
    }

    fun createState(draft: CharacterCreationDraft): GameState {
        val raceId = draft.raceId ?: error("Raca nao selecionada.")
        val classId = draft.classId ?: error("Classe nao selecionada.")
        val classDef = repo.classes[classId] ?: error("Classe nao encontrada: $classId")
        if (!repo.races.containsKey(raceId)) error("Raca nao encontrada: $raceId")

        val starterEquipment = repo.character.starterEquipmentByClass[classDef.id]
            ?.toMutableMap()
            ?: mutableMapOf()
        applyTwoHandedLoadout(starterEquipment)

        val starterInventory = buildList {
            addAll(repo.character.starterInventory)
            addAll(repo.character.starterInventoryByClass[classDef.id].orEmpty())
            if (classDef.id.equals("archer", ignoreCase = true)) {
                repeat(10) { add("arrow_wood") }
            }
        }

        var player = PlayerState(
            name = draft.name.trim().ifBlank { "Aventureiro" },
            classId = classDef.id,
            raceId = raceId,
            baseAttributes = draft.allocated,
            unspentAttrPoints = draft.remainingPoints,
            equipped = starterEquipment,
            gold = repo.character.starterGold
        )

        val starterInsert = InventorySystem.addItemsWithLimit(
            player = player,
            itemInstances = emptyMap(),
            itemRegistry = engine.itemRegistry,
            incomingItemIds = starterInventory
        )
        player = player.copy(
            inventory = starterInsert.inventory,
            quiverInventory = starterInsert.quiverInventory,
            selectedAmmoTemplateId = starterInsert.selectedAmmoTemplateId
        )
        player = InventorySystem.normalizeAmmoStorage(player, emptyMap(), engine.itemRegistry)
        player = engine.skillSystem.ensureProgress(player)
        player = engine.classQuestService.synchronize(player)

        val initialStats = engine.computePlayerStats(player, emptyMap())
        player = player.copy(
            currentHp = initialStats.derived.hpMax,
            currentMp = initialStats.derived.mpMax
        )

        val map = repo.maps.values.firstOrNull()
        val world = if (map != null) {
            WorldState(mapId = map.id, currentRoomId = map.startRoomId)
        } else {
            WorldState(mapId = "default", currentRoomId = "")
        }

        return GameState(
            player = player,
            world = world,
            lastClockSyncEpochMs = System.currentTimeMillis()
        )
    }

    private fun applyTwoHandedLoadout(equipped: MutableMap<String, String>) {
        val mainWeapon = equipped["WEAPON_MAIN"] ?: return
        val resolved = engine.itemResolver.resolve(mainWeapon, emptyMap()) ?: return
        if (resolved.twoHanded) {
            equipped["WEAPON_OFF"] = "__offhand_blocked__"
        }
    }

    private fun applyAttrDelta(attrs: Attributes, code: String, delta: Int): Attributes = when (code.uppercase()) {
        "STR" -> attrs.copy(str = (attrs.str + delta).coerceAtLeast(0))
        "AGI" -> attrs.copy(agi = (attrs.agi + delta).coerceAtLeast(0))
        "DEX" -> attrs.copy(dex = (attrs.dex + delta).coerceAtLeast(0))
        "VIT" -> attrs.copy(vit = (attrs.vit + delta).coerceAtLeast(0))
        "INT" -> attrs.copy(`int` = (attrs.`int` + delta).coerceAtLeast(0))
        "SPR" -> attrs.copy(spr = (attrs.spr + delta).coerceAtLeast(0))
        "LUK" -> attrs.copy(luk = (attrs.luk + delta).coerceAtLeast(0))
        else -> attrs
    }
}
