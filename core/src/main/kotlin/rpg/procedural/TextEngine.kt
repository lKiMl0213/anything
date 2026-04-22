package rpg.procedural

import kotlin.random.Random
import rpg.io.DataRepository
import rpg.model.TextPoolDef
import rpg.monster.MonsterInstance

class TextEngine(
    private val repo: DataRepository,
    private val rng: Random
) {
    private val defaultPool = repo.textPools["default"]

    fun dangerLevel(monsterPower: Double, playerPower: Double): String {
        if (playerPower <= 0.0) return "high"
        val ratio = monsterPower / playerPower
        return when {
            ratio < 0.85 -> "low"
            ratio < 1.15 -> "medium"
            else -> "high"
        }
    }

    fun encounterText(monster: MonsterInstance, biomeId: String?, dangerLevel: String): String {
        val pools = selectPools(monster, biomeId, dangerLevel)
        val intros = pools.flatMap { it.intros }.ifEmpty { defaultPool?.intros ?: emptyList() }
        val descriptors = pools.flatMap { it.descriptors }.ifEmpty { defaultPool?.descriptors ?: emptyList() }
        val threats = pools.flatMap { it.threats }.ifEmpty { defaultPool?.threats ?: emptyList() }

        val intro = (intros.ifEmpty { listOf("Voce encontra") }).random(rng)
        val desc = (descriptors.ifEmpty { listOf("uma criatura desconhecida") }).random(rng)
        val threat = (threats.ifEmpty { listOf("Ela se move com cautela.") }).random(rng)

        return "$intro $desc. $threat"
    }

    private fun selectPools(monster: MonsterInstance, biomeId: String?, dangerLevel: String): List<TextPoolDef> {
        val pools = repo.textPools.values
        val matches = pools.filter { pool ->
            val tagOk = pool.monsterTags.isEmpty() || pool.monsterTags.any { monster.tags.contains(it) }
            val biomeOk = pool.biomeIds.isEmpty() || (biomeId != null && pool.biomeIds.contains(biomeId))
            val dangerOk = pool.dangerLevels.isEmpty() || pool.dangerLevels.contains(dangerLevel)
            tagOk && biomeOk && dangerOk
        }
        return if (matches.isNotEmpty()) matches else listOfNotNull(defaultPool)
    }
}
