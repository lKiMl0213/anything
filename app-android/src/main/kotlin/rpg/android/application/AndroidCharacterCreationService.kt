package rpg.android.application

import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.io.DataRepository
import rpg.model.Attributes
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.WorldState

class AndroidCharacterCreationService(
    private val repo: DataRepository,
    private val engine: GameEngine
) {
    fun create(
        name: String,
        raceId: String,
        classId: String,
        baseAttributes: Attributes,
        unspentPoints: Int
    ): GameState {
        val sanitizedName = name.trim().ifBlank { "Aventureiro" }
        val classDef = repo.classes[classId] ?: error("Classe nao encontrada: $classId")
        if (!repo.races.containsKey(raceId)) {
            error("Raca nao encontrada: $raceId")
        }

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
            name = sanitizedName,
            classId = classDef.id,
            raceId = raceId,
            baseAttributes = baseAttributes,
            unspentAttrPoints = unspentPoints.coerceAtLeast(0),
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
}
