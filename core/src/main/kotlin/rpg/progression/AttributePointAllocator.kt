package rpg.progression

import rpg.model.Attributes
import rpg.model.PlayerState

internal class AttributePointAllocator(
    private val readInt: (prompt: String, min: Int, max: Int) -> Int,
    private val clampPlayerResources: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val notify: (String) -> Unit
) {
    fun allocateUnspentPoints(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        if (player.unspentAttrPoints <= 0) {
            notify("Nenhum ponto de atributo disponivel.")
            return player
        }
        return allocateAttributePoints(player, itemInstances)
    }

    fun allocateAttributePoints(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        var remaining = player.unspentAttrPoints
        var attrs = player.baseAttributes
        notify("\nDistribuindo $remaining pontos de atributo.")

        val order = listOf("STR", "AGI", "DEX", "VIT", "INT", "SPR", "LUK")
        for (key in order) {
            if (remaining <= 0) break
            val add = readInt("$key (0-$remaining): ", 0, remaining)
            remaining -= add
            attrs = when (key) {
                "STR" -> attrs.copy(str = attrs.str + add)
                "AGI" -> attrs.copy(agi = attrs.agi + add)
                "DEX" -> attrs.copy(dex = attrs.dex + add)
                "VIT" -> attrs.copy(vit = attrs.vit + add)
                "INT" -> attrs.copy(`int` = attrs.`int` + add)
                "SPR" -> attrs.copy(spr = attrs.spr + add)
                else -> attrs.copy(luk = attrs.luk + add)
            }
        }

        val updated = player.copy(
            baseAttributes = attrs,
            unspentAttrPoints = remaining
        )

        return clampPlayerResources(updated, itemInstances)
    }
}
