package rpg.cli

import rpg.cli.model.InitialAttributeAllocation
import rpg.creation.CharacterCreationPreviewService
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.ClassDef
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.RaceDef
import rpg.model.WorldState

internal class LegacyCharacterCreationFlow(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val characterDef: rpg.model.CharacterDef,
    private val characterCreationPreview: CharacterCreationPreviewService,
    private val readNonEmpty: (prompt: String) -> String,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val format: (Double) -> String,
    private val allocateAttributesWithBonuses: (points: Int, raceDef: RaceDef, classDef: ClassDef) -> InitialAttributeAllocation,
    private val applyTwoHandedLoadout: (equipped: MutableMap<String, String>) -> Unit,
    private val computePlayerStats: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> rpg.engine.ComputedStats,
    private val ensureSkillProgress: (player: PlayerState) -> PlayerState,
    private val synchronizeAchievements: (player: PlayerState) -> PlayerState,
    private val hubLoop: (state: GameState) -> GameState
) {
    fun newGame(): GameState? {
        val name = readNonEmpty("Nome: ")
        val raceDef = chooseRaceForCreation() ?: return null
        val classDef = chooseClassForCreation() ?: return null

        showCharacterSummary(name, raceDef, classDef)
        val initialAttributeAllocation = allocateAttributesWithBonuses(
            characterDef.baseAttributePoints,
            raceDef,
            classDef
        )
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
        if (classDef.id.equals("archer", ignoreCase = true)) {
            repeat(10) { inventory += "arrow_wood" }
        }

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
        player = rpg.inventory.InventorySystem.normalizeAmmoStorage(player, emptyMap(), engine.itemRegistry)
        player = ensureSkillProgress(player)
        player = synchronizeAchievements(player)

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
        return hubLoop(state)
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

    private fun formatSigned(value: Int): String = if (value >= 0) "+$value" else value.toString()
}
