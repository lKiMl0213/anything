package rpg.enchant

import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.item.ItemRarity
import rpg.model.Attributes
import rpg.model.ItemInstance
import rpg.model.PlayerState

class EnchantSafetyMechanicsTest {
    private val repo = DataRepository(Path.of("data"))
    private val engine = GameEngine(repo, Random(41))

    @Test
    fun enhancementRuneUsesRelativeFormula() {
        val config = EnchantConfig(
            baseChanceByLevel = mapOf(10 to 10.0),
            breakChanceByLevel = mapOf(10 to 0.0),
            enhancementRuneBonusPctPerUnit = 10.0,
            successBonusPerSkillLevelPct = 0.0,
            maxSuccessBonusPct = 0.0,
            breakReductionPerSkillLevelPct = 0.0,
            maxBreakReductionPct = 0.0
        )
        val chance = EnchantChanceCalculator(config).calculate(
            currentEnchantLevel = 10,
            enhancementRunes = 1,
            useProtectionRune = false,
            enchantSkillLevel = 1
        )
        assertEquals(
            11.0,
            chance.finalSuccessChancePct,
            0.0001,
            "Runa deve aplicar bonus relativo: 10% * (1 + 10%) = 11%."
        )
    }

    @Test
    fun higherTierRuneAppliesConfiguredRelativeBonusInPreview() {
        val config = EnchantConfig(
            baseChanceByLevel = mapOf(10 to 10.0),
            breakChanceByLevel = mapOf(10 to 0.0),
            enhancementRuneItemId = "rune_common",
            enhancementRuneItemIdsByTier = mapOf(
                "common" to "rune_common",
                "rare" to "rune_rare"
            ),
            enhancementRuneBonusPctByTier = mapOf(
                "common" to 5.0,
                "rare" to 20.0
            ),
            successBonusPerSkillLevelPct = 0.0,
            maxSuccessBonusPct = 0.0,
            breakReductionPerSkillLevelPct = 0.0,
            maxBreakReductionPct = 0.0
        )
        val service = EnchantService(
            itemRegistry = engine.itemRegistry,
            skillSystem = engine.skillSystem,
            rng = Random(99),
            config = config
        )
        val item = testEquipment().copy(id = "equip_tier_bonus")
        val player = basePlayer(
            gold = 9000,
            inventory = listOf(item.id, "rune_rare")
        )

        val preview = service.preview(
            player = player,
            itemInstances = mapOf(item.id to item),
            request = EnchantAttemptRequest(
                itemId = item.id,
                enhancementRunes = 1,
                useProtectionRune = false
            )
        )

        assertEquals(
            12.0,
            preview.successChancePct,
            0.0001,
            "Runa rara deve aplicar bonus relativo configurado: 10% * (1 + 20%) = 12%."
        )
    }

    @Test
    fun protectionRunePreventsBreakAndIsConsumed() {
        val config = forcedFailureConfig()
        val service = EnchantService(
            itemRegistry = engine.itemRegistry,
            skillSystem = engine.skillSystem,
            rng = Random(42),
            config = config
        )
        val item = testEquipment().copy(id = "equip_protected")
        val player = basePlayer(
            gold = 9000,
            inventory = listOf(item.id, config.protectionRuneItemId)
        )
        val result = service.enchant(
            player = player,
            itemInstances = mapOf(item.id to item),
            request = EnchantAttemptRequest(
                itemId = item.id,
                enhancementRunes = 0,
                useProtectionRune = true
            )
        )

        assertFalse(result.success)
        assertFalse(result.destroyed, "Com protecao, item nao pode quebrar.")
        assertTrue(result.consumedProtectionRune, "Runa de protecao deve ser consumida sempre.")
        assertTrue(result.player.inventory.contains(item.id), "Item deve permanecer no inventario.")
        assertFalse(
            result.player.inventory.contains(config.protectionRuneItemId),
            "Runa de protecao consumida nao deve permanecer no inventario."
        )
        assertTrue(result.itemInstances.containsKey(item.id), "Item encantado deve continuar existindo.")
    }

    @Test
    fun failedEnchantWithoutProtectionCanBreakItem() {
        val config = forcedFailureConfig()
        val service = EnchantService(
            itemRegistry = engine.itemRegistry,
            skillSystem = engine.skillSystem,
            rng = Random(43),
            config = config
        )
        val item = testEquipment().copy(id = "equip_unprotected")
        val player = basePlayer(
            gold = 9000,
            inventory = listOf(item.id)
        )
        val result = service.enchant(
            player = player,
            itemInstances = mapOf(item.id to item),
            request = EnchantAttemptRequest(
                itemId = item.id,
                enhancementRunes = 0,
                useProtectionRune = false
            )
        )

        assertFalse(result.success)
        assertTrue(result.destroyed, "Sem protecao e com quebra forçada, item deve quebrar.")
        assertFalse(result.player.inventory.contains(item.id))
        assertFalse(result.itemInstances.containsKey(item.id))
    }

    private fun forcedFailureConfig(): EnchantConfig {
        return EnchantConfig(
            baseChanceByLevel = mapOf(0 to 0.0, 10 to 0.0),
            breakChanceByLevel = mapOf(0 to 100.0, 10 to 100.0),
            successBonusPerSkillLevelPct = 0.0,
            maxSuccessBonusPct = 0.0,
            breakReductionPerSkillLevelPct = 0.0,
            maxBreakReductionPct = 0.0,
            durationReductionPerSkillLevelPct = 0.0,
            maxDurationReductionPct = 0.0
        )
    }

    private fun testEquipment(): ItemInstance {
        val template = repo.itemTemplates.getValue("iron_sword")
        return engine.itemEngine.generateFromTemplate(
            template = template,
            level = 20,
            rarity = ItemRarity.RARE
        ).copy(enchantLevel = 10)
    }

    private fun basePlayer(gold: Int, inventory: List<String>): PlayerState {
        val classId = repo.classes.keys.first()
        val raceId = repo.races.keys.first()
        return engine.skillSystem.ensureProgress(
            PlayerState(
                name = "EnchantTester",
                classId = classId,
                raceId = raceId,
                level = 30,
                gold = gold,
                inventory = inventory,
                baseAttributes = Attributes(
                    str = 12,
                    agi = 10,
                    dex = 10,
                    vit = 11,
                    `int` = 8,
                    spr = 8,
                    luk = 8
                ),
                currentHp = 140.0,
                currentMp = 70.0
            )
        )
    }
}
