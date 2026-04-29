package rpg.engine

import kotlin.math.min
import rpg.classquest.ClassQuestTagRules
import rpg.classsystem.ClassSystem
import rpg.model.Attributes
import rpg.model.Bonuses
import rpg.model.DerivedStats
import rpg.model.PlayerState
import rpg.io.DataRepository
import rpg.registry.ItemRegistry

class StatsEngine(private val repo: DataRepository, private val itemRegistry: ItemRegistry) {
    private val classSystem = ClassSystem(repo)

    fun computePlayerStats(player: PlayerState, itemInstances: Map<String, rpg.model.ItemInstance> = emptyMap()): ComputedStats {
        val classBonuses = classSystem.totalBonuses(player)
        val equipBonuses = player.equipped.values.mapNotNull { id ->
            val instance = itemInstances[id]
            val def = if (instance == null) itemRegistry.item(id) else null
            val tags = instance?.tags ?: def?.tags ?: emptyList()
            if (!ClassQuestTagRules.canEquip(player, tags)) {
                return@mapNotNull null
            }
            instance?.bonuses ?: def?.bonuses
        }

        val bonuses = mutableListOf<Bonuses>()
        bonuses.add(classBonuses)
        bonuses.addAll(equipBonuses)
        bonuses.add(runBonuses(player))
        bonuses.add(roomBonuses(player))

        val deathMultiplier = if (player.deathDebuffStacks > 0) {
            (1.0 - 0.20 * player.deathDebuffStacks).coerceAtLeast(0.1)
        } else {
            1.0
        }
        val roomMultiplier = if (player.roomEffectRooms > 0) player.roomEffectMultiplier else 1.0
        val totalMultiplier = (deathMultiplier * roomMultiplier * player.runAttrMultiplier).coerceAtLeast(0.1)

        val totalBonus = bonuses.fold(Bonuses()) { acc, next -> acc + next }
        // Alocacao de atributo permanece linear: 1 ponto investido = +1 atributo efetivo.
        val effectiveAttributes = (player.baseAttributes + totalBonus.attributes).scale(totalMultiplier)

        val baseDerived = StatsCalculator.baseDerived(effectiveAttributes)
        val withAdd = baseDerived + totalBonus.derivedAdd
        val withMult = withAdd.applyMultiplier(totalBonus.derivedMult)
        val capped = withMult.copy(cdrPct = min(withMult.cdrPct, 40.0))
        return ComputedStats(effectiveAttributes, capped)
    }

    private fun runBonuses(player: PlayerState): Bonuses {
        return Bonuses(
            attributes = player.runAttrBonus,
            derivedAdd = player.runDerivedAdd,
            derivedMult = player.runDerivedMult
        )
    }

    private fun roomBonuses(player: PlayerState): Bonuses {
        val attr = if (player.roomAttrRooms > 0) player.roomAttrBonus else Attributes()
        val derivedAdd = if (player.roomDerivedRooms > 0) player.roomDerivedAdd else DerivedStats()
        val derivedMult = if (player.roomDerivedRooms > 0) player.roomDerivedMult else DerivedStats()
        return Bonuses(attributes = attr, derivedAdd = derivedAdd, derivedMult = derivedMult)
    }
}
