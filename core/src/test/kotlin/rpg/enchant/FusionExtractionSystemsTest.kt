package rpg.enchant

import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import rpg.application.inventory.InventoryRulesSupport
import rpg.crafting.CraftingService
import rpg.economy.DropEngine
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.io.JsonStore
import rpg.item.ItemEngine
import rpg.item.ItemRarity
import rpg.model.Attributes
import rpg.model.Bonuses
import rpg.model.CraftDiscipline
import rpg.model.CraftRecipeDef
import rpg.model.DropCategory
import rpg.model.DropEntryDef
import rpg.model.DropTableDef
import rpg.model.EquipSlot
import rpg.model.GameState
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.model.RecipeIngredientDef
import rpg.model.WorldState
import rpg.monster.MonsterInstance
import rpg.monster.MonsterPersonality
import rpg.monster.MonsterRarity
import rpg.registry.DropTableRegistry
import rpg.registry.ItemRegistry

class FusionExtractionSystemsTest {
    private val repo = DataRepository(Path.of("data"))
    private val engine = GameEngine(repo, Random(7))

    @Test
    fun dropGenerationProducesEquipmentWithAffixes() {
        val rng = Random(11)
        val itemRegistry = ItemRegistry(repo.items, repo.itemTemplates)
        val itemEngine = ItemEngine(itemRegistry, repo.affixes, rng)
        val table = DropTableDef(
            id = "test_drop_table",
            baseChancePct = 100.0,
            entries = listOf(
                DropEntryDef(
                    templateIds = listOf("iron_sword"),
                    category = DropCategory.EQUIPMENT_DROP,
                    chancePct = 100.0,
                    minRarity = ItemRarity.RARE,
                    maxRarity = ItemRarity.RARE
                )
            )
        )
        val dropEngine = DropEngine(
            dropTables = DropTableRegistry(mapOf(table.id to table)),
            itemRegistry = itemRegistry,
            itemEngine = itemEngine,
            rng = rng,
            balance = repo.balance
        )
        val player = basePlayer(gold = 0, level = 1)
        val tier = MapTierDef(id = "test", minLevel = 1, recommendedLevel = 1, baseMonsterLevel = 1, dropTier = 1)
        val monster = MonsterInstance(
            archetypeId = "test_monster",
            name = "Monstro de Teste",
            level = 20,
            rarity = MonsterRarity.RARE,
            attributes = Attributes(),
            bonuses = Bonuses(),
            tags = setOf("test"),
            personality = MonsterPersonality.BALANCED,
            starCount = 0,
            powerScore = 100.0,
            dropTableId = table.id,
            baseXp = 10,
            baseGold = 10
        )

        val outcome = dropEngine.rollDrop(player, 0.0, tier, monster, 1.0)
        val item = outcome.itemInstance
        assertNotNull(item)
        assertTrue(item.affixes.isNotEmpty(), "Drop raro de template precisa vir com afixos.")
    }

    @Test
    fun modifiersPersistInSaveAndAppearInInventoryDetail() {
        val template = repo.itemTemplates.getValue("iron_sword")
        val generated = engine.itemEngine.generateFromTemplate(template, level = 20, rarity = ItemRarity.RARE)
        val itemId = generated.id
        val player = basePlayer(
            gold = 1200,
            inventory = listOf(itemId),
            equipped = mapOf(EquipSlot.WEAPON_MAIN.name to itemId)
        )
        val state = GameState(
            player = player,
            world = WorldState(mapId = "test_map", currentRoomId = "room_1"),
            itemInstances = mapOf(itemId to generated)
        )
        val file = Files.createTempFile("rpg-save-", ".json")
        JsonStore.save(file, state)
        val loaded = JsonStore.load<GameState>(file)
        val loadedItem = loaded.itemInstances[itemId]
        assertNotNull(loadedItem)
        assertEquals(generated.affixes, loadedItem.affixes)

        val support = InventoryRulesSupport(repo, engine)
        val stack = support.buildInventoryStacks(loaded.player, loaded.itemInstances).first()
        val detail = support.buildInventoryItemDetail(loaded.player, loaded.itemInstances, stack)
        assertTrue(detail.detailLines.any { it.startsWith("Afixos:") }, "Detalhe do inventario precisa exibir afixos.")
    }

    @Test
    fun modifiersAffectCombatStatsWhenEquipped() {
        val template = repo.itemTemplates.getValue("iron_sword")
        val generatedRaw = engine.itemEngine.generateFromTemplate(template, level = 25, rarity = ItemRarity.EPIC)
        val generated = generatedRaw.copy(tags = emptyList())
        val itemId = generated.id
        val withoutEquip = basePlayer(gold = 900, inventory = listOf(itemId))
        val withEquip = withoutEquip.copy(equipped = mapOf(EquipSlot.WEAPON_MAIN.name to itemId))
        val instances = mapOf(itemId to generated)
        val baseStats = engine.computePlayerStats(withoutEquip, instances)
        val equippedStats = engine.computePlayerStats(withEquip, instances)
        assertTrue(
            equippedStats.derived.damagePhysical != baseStats.derived.damagePhysical ||
                equippedStats.derived.damageMagic != baseStats.derived.damageMagic ||
                equippedStats.derived.critChancePct != baseStats.derived.critChancePct,
            "Bonuses do item precisam impactar stats usados em combate."
        )
    }

    @Test
    fun craftCanOutputEquipmentWithAffixes() {
        val recipe = CraftRecipeDef(
            id = "test_craft_sunstone_recurve",
            name = "Teste de Arco Raro",
            discipline = CraftDiscipline.FORGE,
            outputItemId = "sunstone_recurve",
            outputQty = 1,
            minPlayerLevel = 1,
            minSkillLevel = 1,
            ingredients = listOf(RecipeIngredientDef(itemId = "runic_stone", quantity = 1))
        )
        val crafting = CraftingService(
            recipes = mapOf(recipe.id to recipe),
            itemRegistry = engine.itemRegistry,
            itemEngine = engine.itemEngine,
            skillSystem = engine.skillSystem,
            rng = Random(13),
            permanentUpgradeService = engine.permanentUpgradeService
        )
        val player = basePlayer(gold = 0, inventory = listOf("runic_stone"))
        val result = crafting.craft(player, emptyMap(), recipe.id, times = 1)
        assertTrue(result.success)
        val outputId = result.player.inventory.firstOrNull { result.itemInstances.containsKey(it) }
        assertNotNull(outputId)
        val crafted = result.itemInstances[outputId]
        assertNotNull(crafted)
        assertTrue(crafted.affixes.isNotEmpty(), "Craft de template raro precisa manter afixos/modificadores.")
    }

    @Test
    fun fusionPreservesModifiersRespectsLimitsAndConsumesInputs() {
        val affixName = repo.affixes.getValue("str").name
        val itemAId = "item_a"
        val itemBId = "item_b"
        val itemA = rpg.model.ItemInstance(
            id = itemAId,
            templateId = "iron_sword",
            name = "Espada de Teste A",
            level = 20,
            minLevel = 20,
            rarity = ItemRarity.RARE,
            type = rpg.model.ItemType.EQUIPMENT,
            slot = EquipSlot.WEAPON_MAIN,
            bonuses = Bonuses(attributes = Attributes(str = 3)),
            affixes = listOf(affixName),
            enchantLevel = 10,
            enchantBaseBonuses = Bonuses(attributes = Attributes(str = 3)),
            enchantBasePowerScore = 50
        )
        val itemB = itemA.copy(
            id = itemBId,
            name = "Espada de Teste B",
            enchantLevel = 10
        )
        val fusionService = FusionService(
            itemRegistry = engine.itemRegistry,
            skillSystem = engine.skillSystem,
            rng = Random(17),
            enchantConfig = repo.enchantConfig,
            fusionConfig = repo.fusionConfig.copy(
                minSuccessChancePct = 100.0,
                maxSuccessChancePct = 100.0,
                upgradeChanceByBaseLevel = mapOf(0 to 0.0, 10 to 0.0)
            ),
            affixes = repo.affixes
        )
        val player = basePlayer(gold = 50000, inventory = listOf(itemAId, itemBId))
        val result = fusionService.fuse(
            player = player,
            itemInstances = mapOf(itemAId to itemA, itemBId to itemB),
            request = FusionRequest(slot1ItemId = itemAId, slot2ItemId = itemBId)
        )
        assertTrue(result.success)
        assertTrue(result.player.inventory.none { it == itemAId || it == itemBId }, "Itens de entrada precisam ser consumidos.")
        val outputId = result.outputItemId
        assertNotNull(outputId)
        val output = result.itemInstances[outputId]
        assertNotNull(output)
        assertTrue(output.type == rpg.model.ItemType.EQUIPMENT)
        assertEquals(2, output.affixes.size, "Afixos dos dois itens devem ser preservados.")
        assertTrue(output.bonuses.attributes.str >= 6, "Afixos iguais devem manter soma base de modificadores.")
        assertTrue(output.enchantLevel <= 11, "Resultado nao pode passar de max(A,B)+1.")
        assertTrue(output.enchantLevel in 0..15)
    }

    @Test
    fun extractionCreatesExactStoneAndHandlesItemOutcome() {
        val itemId = "enchanted_item"
        val enchanted = rpg.model.ItemInstance(
            id = itemId,
            templateId = "iron_sword",
            name = "Espada Encantada",
            level = 25,
            minLevel = 25,
            rarity = ItemRarity.RARE,
            type = rpg.model.ItemType.EQUIPMENT,
            slot = EquipSlot.WEAPON_MAIN,
            bonuses = Bonuses(attributes = Attributes(str = 2)),
            enchantLevel = 10,
            enchantBaseBonuses = Bonuses(attributes = Attributes(str = 2)),
            enchantBasePowerScore = 45
        )
        val extractionService = ExtractionService(
            itemRegistry = engine.itemRegistry,
            skillSystem = engine.skillSystem,
            rng = Random(19),
            enchantConfig = repo.enchantConfig,
            extractionConfig = repo.extractionConfig.copy(
                withScrollMinChancePct = 100.0,
                withScrollCapPct = 100.0
            )
        )
        val withProtectionPlayer = basePlayer(
            gold = 50000,
            inventory = listOf(itemId, "extract_scroll_remocao", "extract_scroll_protecao")
        )
        val withProtection = extractionService.extract(
            player = withProtectionPlayer,
            itemInstances = mapOf(itemId to enchanted),
            request = ExtractionRequest(
                itemId = itemId,
                useRemovalScroll = true,
                useProtectionScroll = true
            )
        )
        assertTrue(withProtection.success)
        val stoneId = withProtection.extractedStoneId
        assertNotNull(stoneId)
        val stone = withProtection.itemInstances[stoneId]
        assertNotNull(stone)
        assertEquals(10, stone.enchantLevel, "Pedra precisa refletir exatamente o +X do item.")
        assertEquals("enchant_stone_tier_10", stone.templateId, "Extracao deve gerar o template de pedra correspondente ao +X.")
        val preserved = withProtection.itemInstances[itemId]
        assertNotNull(preserved)
        assertEquals(0, preserved.enchantLevel, "Com protecao, item deve voltar para +0.")

        val withoutProtectionPlayer = basePlayer(
            gold = 50000,
            inventory = listOf(itemId, "extract_scroll_remocao")
        )
        val withoutProtection = extractionService.extract(
            player = withoutProtectionPlayer,
            itemInstances = mapOf(itemId to enchanted),
            request = ExtractionRequest(
                itemId = itemId,
                useRemovalScroll = true,
                useProtectionScroll = false
            )
        )
        assertTrue(withoutProtection.success)
        assertTrue(withoutProtection.itemDestroyed, "Sem protecao, item deve ser consumido no sucesso.")
        assertTrue(itemId !in withoutProtection.player.inventory)
    }

    private fun basePlayer(
        gold: Int,
        level: Int = 30,
        inventory: List<String> = emptyList(),
        equipped: Map<String, String> = emptyMap()
    ): PlayerState {
        val classId = repo.classes.keys.first()
        val raceId = repo.races.keys.first()
        return PlayerState(
            name = "Tester",
            classId = classId,
            raceId = raceId,
            level = level,
            gold = gold,
            inventory = inventory,
            equipped = equipped,
            baseAttributes = Attributes(str = 12, agi = 10, dex = 10, vit = 10, `int` = 8, spr = 8, luk = 8),
            currentHp = 120.0,
            currentMp = 60.0
        )
    }
}
