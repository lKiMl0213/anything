package rpg.balance

import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import rpg.classsystem.AttributeEngine
import rpg.classsystem.ClassSystem
import rpg.engine.Progression
import rpg.io.DataRepository
import rpg.model.Attributes
import rpg.model.PlayerState
import rpg.progression.AttributePointSystem

class AttributeBalanceConsistencyTest {
    private val repo = DataRepository(Path.of("data"))
    private val classSystem = ClassSystem(repo)

    @Test
    fun raceAttributeBonusesHaveSingleNetTotal() {
        val totals = repo.races.values
            .sortedBy { it.id }
            .associate { it.id to sumAttributes(it.bonuses.attributes) }
        assertTrue(totals.isNotEmpty(), "Repositorio precisa conter racas.")
        assertEquals(
            1,
            totals.values.toSet().size,
            "Todas as racas devem ter o mesmo total liquido de atributos. Totais: $totals"
        )
        assertEquals(7, totals.values.first(), "Total liquido padrao de raca deve permanecer em 7.")
    }

    @Test
    fun raceGrowthHasSingleNetTotal() {
        val totals = repo.races.values
            .sortedBy { it.id }
            .associate { it.id to sumAttributes(it.growth) }
        assertTrue(totals.isNotEmpty(), "Repositorio precisa conter racas.")
        assertEquals(
            1,
            totals.values.toSet().size,
            "Todas as racas devem manter o mesmo total de crescimento por nivel. Totais: $totals"
        )
        assertEquals(7, totals.values.first(), "Total de crescimento racial padrao deve permanecer em 7.")
    }

    @Test
    fun classAttributeBonusesHaveSingleNetTotal() {
        val totals = repo.classes.values
            .sortedBy { it.id }
            .associate { it.id to sumAttributes(it.bonuses.attributes) }
        assertTrue(totals.isNotEmpty(), "Repositorio precisa conter classes base.")
        assertEquals(
            1,
            totals.values.toSet().size,
            "Todas as classes base devem ter o mesmo total liquido de atributos. Totais: $totals"
        )
        assertEquals(14, totals.values.first(), "Total liquido padrao de classe base deve permanecer em 14.")
    }

    @Test
    fun classGrowthHasSingleNetTotal() {
        val totals = repo.classes.values
            .sortedBy { it.id }
            .associate { it.id to sumAttributes(it.growth) }
        assertTrue(totals.isNotEmpty(), "Repositorio precisa conter classes base.")
        assertEquals(
            1,
            totals.values.toSet().size,
            "Todas as classes base devem manter o mesmo total de crescimento. Totais: $totals"
        )
        assertEquals(0, totals.values.first(), "Crescimento base das classes deve permanecer em 0.")
    }

    @Test
    fun levelAttributePointsAndAutoPointsAreConsistent() {
        assertEquals(5, AttributePointSystem.POINTS_PER_LEVEL, "Pontos livres por nivel devem permanecer consistentes.")
        assertEquals(
            AttributePointSystem.POINTS_PER_LEVEL,
            Progression.ATTR_POINTS_PER_LEVEL,
            "Alias de progressao deve refletir o mesmo valor de pontos por nivel."
        )

        val random = Random(123)
        repo.classes.values.sortedBy { it.id }.forEach { classDef ->
            repo.races.values.sortedBy { it.id }.forEach { raceDef ->
                val player = PlayerState(
                    name = "BalanceProbe",
                    classId = classDef.id,
                    raceId = raceDef.id,
                    level = 10,
                    baseAttributes = Attributes()
                )
                val before = sumAttributes(player.baseAttributes)
                val updated = AttributeEngine.applyAutoPoints(
                    player = player,
                    classDef = classDef,
                    raceDef = raceDef,
                    subclassDef = null,
                    specializationDef = null,
                    rng = random
                )
                val after = sumAttributes(updated.baseAttributes)
                assertEquals(2, after - before, "Auto pontos por nivel devem distribuir exatamente 2 atributos.")
            }
        }
    }

    @Test
    fun raceAndClassBonusesAreAppliedByClassSystem() {
        repo.classes.values.sortedBy { it.id }.forEach { classDef ->
            repo.races.values.sortedBy { it.id }.forEach { raceDef ->
                val player = PlayerState(
                    name = "BalanceProbe",
                    classId = classDef.id,
                    raceId = raceDef.id
                )
                val total = classSystem.totalBonuses(player).attributes
                val expected = classDef.bonuses.attributes + raceDef.bonuses.attributes
                assertEquals(
                    expected,
                    total,
                    "Soma de bonus de classe+raca precisa bater com a aplicacao no ClassSystem (${classDef.id}/${raceDef.id})."
                )
            }
        }
    }

    private fun sumAttributes(attributes: Attributes): Int {
        return attributes.str +
            attributes.agi +
            attributes.dex +
            attributes.vit +
            attributes.`int` +
            attributes.spr +
            attributes.luk
    }
}
