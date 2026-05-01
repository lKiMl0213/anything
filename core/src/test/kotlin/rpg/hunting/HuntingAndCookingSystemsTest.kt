package rpg.hunting

import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.Attributes
import rpg.model.PlayerState
import rpg.validation.DataIntegrityValidator

class HuntingAndCookingSystemsTest {
    private val repo = DataRepository(Path.of("data"))
    private val engine = GameEngine(repo, Random(31))

    @Test
    fun huntingProducesValidDropsAndConsumesGold() {
        val player = basePlayer(level = 24, gold = 50000)
        val preview = engine.huntingService.preview(
            player = player,
            itemInstances = emptyMap(),
            spotId = "hunting_deep_woods",
            selectedDurationSeconds = 300
        )
        assertTrue(preview.available, "Preview da caça deveria estar disponível para este setup.")

        val result = engine.huntingService.hunt(
            player = player,
            itemInstances = emptyMap(),
            spotId = "hunting_deep_woods",
            selectedDurationSeconds = 300
        )
        assertTrue(result.success, result.message)
        assertTrue(result.collectedByItemId.isNotEmpty(), "Caça precisa gerar itens.")
        assertTrue(result.player.gold < player.gold, "Caça precisa consumir ouro.")
        assertTrue(
            result.collectedByItemId.keys.all { id -> engine.itemRegistry.entry(id) != null },
            "Todos os drops precisam referenciar itens válidos."
        )
    }

    @Test
    fun cookingBuffReplacesPreviousAndImprovesHuntingEfficiency() {
        val player = basePlayer(level = 30, gold = 20000)
        val previewWithoutBuff = engine.huntingService.preview(
            player = player,
            itemInstances = emptyMap(),
            spotId = "hunting_moon_ridge",
            selectedDurationSeconds = 600
        )

        val efficiencyBuff = engine.cookingBuffService.applyFromItem(player, "field_ration")
        assertTrue(efficiencyBuff.applied)
        assertEquals("hunting", efficiencyBuff.player.foodBuffTaskId)

        val previewWithBuff = engine.huntingService.preview(
            player = efficiencyBuff.player,
            itemInstances = emptyMap(),
            spotId = "hunting_moon_ridge",
            selectedDurationSeconds = 600
        )
        assertTrue(
            previewWithBuff.durationSeconds == previewWithoutBuff.durationSeconds,
            "Tempo total escolhido deve permanecer fixo."
        )
        assertTrue(
            previewWithBuff.cycleDurationSeconds < previewWithoutBuff.cycleDurationSeconds,
            "Buff de eficiência deve reduzir o tempo por ciclo."
        )
        assertTrue(
            previewWithBuff.cycles >= previewWithoutBuff.cycles,
            "Buff de eficiência não deve reduzir ciclos no mesmo tempo total."
        )

        val replaced = engine.cookingBuffService.applyFromItem(efficiencyBuff.player, "grilled_meat")
        assertTrue(replaced.applied)
        assertTrue(replaced.replacedPrevious, "Novo buff culinário deve substituir o anterior.")
        assertTrue(replaced.player.foodBuffTaskId == null, "Buff de dano não deve manter taskId.")
    }

    @Test
    fun dataIntegrityValidatorAcceptsHuntingAndCookingReferences() {
        val errors = DataIntegrityValidator().validate(repo, engine.itemRegistry)
        assertTrue(errors.isEmpty(), errors.joinToString(separator = "\n"))
    }

    private fun basePlayer(level: Int, gold: Int): PlayerState {
        val classId = repo.classes.keys.first()
        val raceId = repo.races.keys.first()
        return PlayerState(
            name = "HunterTester",
            classId = classId,
            raceId = raceId,
            level = level,
            gold = gold,
            baseAttributes = Attributes(
                str = 12,
                agi = 11,
                dex = 10,
                vit = 10,
                `int` = 8,
                spr = 8,
                luk = 9
            ),
            currentHp = 150.0,
            currentMp = 80.0
        )
    }
}
