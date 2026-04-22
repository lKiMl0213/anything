package rpg.cli

import rpg.engine.ComputedStats
import rpg.io.DataRepository
import rpg.model.MapTierDef
import rpg.model.PlayerState

internal class DungeonPreparationSupport(
    private val repo: DataRepository,
    private val restHealPct: Double,
    private val restRegenMultiplier: Double,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val computePlayerStats: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> ComputedStats,
    private val applyHealing: (
        player: PlayerState,
        hpDelta: Double,
        mpDelta: Double,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val applyRoomEffect: (player: PlayerState, multiplier: Double, rooms: Int) -> PlayerState,
    private val emit: (String) -> Unit
) {
    fun restRoom(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        tier: MapTierDef
    ): PlayerState {
        val stats = computePlayerStats(player, itemInstances)
        val healingMult = tier.biomeId?.let { repo.biomes[it]?.healingMultiplier ?: 1.0 } ?: 1.0
        val hpRestored = (stats.derived.hpMax * restHealPct + stats.derived.hpRegen * restRegenMultiplier) * healingMult
        val mpRestored = (stats.derived.mpMax * restHealPct + stats.derived.mpRegen * restRegenMultiplier) * healingMult
        emit("\nSala de descanso. HP e MP restaurados parcialmente.")
        return applyHealing(player, hpRestored, mpRestored, itemInstances)
    }

    fun preBossSanctuaryRoom(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        val stats = computePlayerStats(player, itemInstances)
        emit("\nSala de Preparacao do Boss:")
        emit("1. Recuperar 10% de HP/MP")
        emit("2. Ganhar +10% de atributos so para este boss")
        emit("x. Voltar")
        return when (readMenuChoice("Escolha: ", 1, 2)) {
            1 -> {
                val hp = stats.derived.hpMax * 0.10
                val mp = stats.derived.mpMax * 0.10
                emit("Voce recuperou parte dos recursos antes do boss.")
                applyHealing(player, hp, mp, itemInstances)
            }
            2 -> {
                emit("Voce entrou focado: +10% de atributos para este boss.")
                applyRoomEffect(player, 1.10, 1)
            }
            null -> player
            else -> player
        }
    }

    fun promptContinue(): Boolean {
        emit("\n1. Continuar")
        emit("2. Sair da dungeon")
        emit("x. Voltar")
        return readMenuChoice("Escolha: ", 1, 2) == 1
    }
}
