package rpg.application.character

import kotlin.math.abs
import rpg.classquest.ClassQuestTagRules
import rpg.engine.GameEngine
import rpg.model.Attributes
import rpg.model.Bonuses
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState

internal class CharacterAttributeSupport(
    private val engine: GameEngine
) {
    private val attributeMeta = listOf(
        AttrMeta("STR", "Força"),
        AttrMeta("AGI", "Agilidade"),
        AttrMeta("DEX", "Destreza"),
        AttrMeta("VIT", "Vitalidade"),
        AttrMeta("INT", "Inteligência"),
        AttrMeta("SPR", "Espírito"),
        AttrMeta("LUK", "Sorte")
    )

    fun hasUnspentAttributePoints(player: PlayerState): Boolean = player.unspentAttrPoints > 0

    fun attributeRows(state: GameState): List<AttributeRowView> {
        val player = state.player
        val computed = engine.computePlayerStats(player, state.itemInstances)
        val equipmentBonus = equipmentBonuses(player, state.itemInstances).attributes
        val classTalentBonus = engine.classSystem.totalBonuses(player).attributes
        val temporaryBonus = temporaryAttributeBonuses(player)
        return attributeMeta.map { meta ->
            AttributeRowView(
                code = meta.code,
                label = meta.label,
                baseValue = getAttr(player.baseAttributes, meta.code),
                equipmentBonus = getAttr(equipmentBonus, meta.code),
                classTalentBonus = getAttr(classTalentBonus, meta.code),
                temporaryBonus = getAttr(temporaryBonus, meta.code),
                finalValue = getAttr(computed.attributes, meta.code)
            )
        }
    }

    fun attributeDetail(state: GameState, attributeCode: String): AttributeDetailView? {
        val meta = attributeMeta.firstOrNull { it.code.equals(attributeCode, ignoreCase = true) } ?: return null
        val player = state.player
        val computed = engine.computePlayerStats(player, state.itemInstances)
        val equipmentBonus = equipmentBonuses(player, state.itemInstances).attributes
        val classTalentBonus = engine.classSystem.totalBonuses(player).attributes
        val temporaryBonus = temporaryAttributeBonuses(player)

        val lines = mutableListOf<String>()
        lines += "Base: ${getAttr(player.baseAttributes, meta.code)}"
        lines += "Bônus de equipamento: ${formatSigned(getAttr(equipmentBonus, meta.code))}"
        lines += "Bônus de classe/raça/talento: ${formatSigned(getAttr(classTalentBonus, meta.code))}"
        lines += "Bônus temporário: ${formatSigned(getAttr(temporaryBonus, meta.code))}"
        lines += "Valor final: ${getAttr(computed.attributes, meta.code)}"
        lines += "Afeta diretamente:"
        lines += AttributeDescriptionCatalog.directEffects(meta.code).map { "- $it" }
        lines += "Impacto na gameplay:"
        lines += AttributeDescriptionCatalog.gameplayImpact(meta.code).map { "- $it" }
        if (player.unspentAttrPoints > 0) {
            lines += "Investindo 1 ponto agora:"
            lines += previewAttributeSpend(player, state.itemInstances, meta.code).map { "- $it" }
        }
        val notes = buildAttributeMultiplierNotes(player)
        if (notes.isNotEmpty()) {
            lines += "Observações:"
            lines += notes.map { "- $it" }
        }
        return AttributeDetailView(
            code = meta.code,
            label = meta.label,
            detailLines = lines,
            canAllocate = player.unspentAttrPoints > 0,
            availablePoints = player.unspentAttrPoints
        )
    }

    fun attributeDisplayLabel(code: String): String {
        return attributeMeta.firstOrNull { it.code == code }?.let { "${it.code} (${it.label})" } ?: code
    }

    fun allocateAttributePoint(state: GameState, attributeCode: String): CharacterMutationResult {
        return allocateAttributePoints(state, attributeCode, 1)
    }

    fun allocateAttributePoints(state: GameState, attributeCode: String, amount: Int): CharacterMutationResult {
        val player = state.player
        val meta = attributeMeta.firstOrNull { it.code.equals(attributeCode, ignoreCase = true) }
            ?: return CharacterMutationResult(state, listOf("Atributo inválido."))
        val requested = amount.coerceAtLeast(0)
        if (requested <= 0) {
            return CharacterMutationResult(state, listOf("Quantidade inválida de pontos."))
        }
        if (player.unspentAttrPoints <= 0) {
            return CharacterMutationResult(state, listOf("Nenhum ponto de atributo disponível."))
        }
        if (requested > player.unspentAttrPoints) {
            return CharacterMutationResult(state, listOf("Pontos insuficientes para essa alocação."))
        }
        val updatedPlayer = clampPlayerResources(
            player.copy(
                baseAttributes = addAttr(player.baseAttributes, meta.code, requested),
                unspentAttrPoints = (player.unspentAttrPoints - requested).coerceAtLeast(0)
            ),
            state.itemInstances
        )
        return CharacterMutationResult(
            state.copy(player = updatedPlayer),
            listOf("$requested ponto(s) investido(s) em ${meta.label}.")
        )
    }

    fun applyAttributes(state: GameState, targetValues: Map<String, Int>): CharacterMutationResult {
        val player = state.player
        val normalizedTargets = targetValues.mapKeys { it.key.uppercase() }

        val deltas = attributeMeta.map { meta ->
            val current = getAttr(player.baseAttributes, meta.code)
            val requested = normalizedTargets[meta.code] ?: current
            if (requested < current) {
                return CharacterMutationResult(
                    state,
                    listOf("Não é permitido remover pontos já aplicados em ${meta.label}.")
                )
            }
            meta to (requested - current)
        }

        val totalToSpend = deltas.sumOf { it.second }
        if (totalToSpend <= 0) {
            return CharacterMutationResult(state, listOf("Nenhum ponto de atributo para aplicar."))
        }
        if (totalToSpend > player.unspentAttrPoints) {
            return CharacterMutationResult(state, listOf("Pontos de atributo insuficientes para aplicar essa distribuição."))
        }

        val updatedBaseAttributes = deltas.fold(player.baseAttributes) { acc, (meta, delta) ->
            addAttr(acc, meta.code, delta)
        }
        val updatedPlayer = clampPlayerResources(
            player.copy(
                baseAttributes = updatedBaseAttributes,
                unspentAttrPoints = (player.unspentAttrPoints - totalToSpend).coerceAtLeast(0)
            ),
            state.itemInstances
        )
        return CharacterMutationResult(
            state.copy(player = updatedPlayer),
            listOf("Distribuição aplicada com sucesso: $totalToSpend ponto(s) de atributo.")
        )
    }

    fun clampPlayerResources(player: PlayerState, itemInstances: Map<String, ItemInstance>): PlayerState {
        val stats = engine.computePlayerStats(player, itemInstances)
        return player.copy(
            currentHp = player.currentHp.coerceIn(0.0, stats.derived.hpMax),
            currentMp = player.currentMp.coerceIn(0.0, stats.derived.mpMax)
        )
    }

    private fun previewAttributeSpend(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        attributeCode: String
    ): List<String> {
        val before = engine.computePlayerStats(player, itemInstances)
        val after = engine.computePlayerStats(
            player.copy(baseAttributes = addAttr(player.baseAttributes, attributeCode, 1)),
            itemInstances
        )
        val deltas = listOf(
            "Atributo final ${attributeCode.uppercase()}" to
                (getAttr(after.attributes, attributeCode) - getAttr(before.attributes, attributeCode)).toDouble(),
            "Dano físico" to (after.derived.damagePhysical - before.derived.damagePhysical),
            "Dano mágico" to (after.derived.damageMagic - before.derived.damageMagic),
            "HP máximo" to (after.derived.hpMax - before.derived.hpMax),
            "MP máximo" to (after.derived.mpMax - before.derived.mpMax),
            "Defesa física" to (after.derived.defPhysical - before.derived.defPhysical),
            "Defesa mágica" to (after.derived.defMagic - before.derived.defMagic),
            "Velocidade de ataque" to (after.derived.attackSpeed - before.derived.attackSpeed),
            "Velocidade de movimento" to (after.derived.moveSpeed - before.derived.moveSpeed),
            "Crítico" to (after.derived.critChancePct - before.derived.critChancePct),
            "Precisão" to (after.derived.accuracy - before.derived.accuracy),
            "Esquiva" to (after.derived.evasion - before.derived.evasion),
            "Regeneração HP" to (after.derived.hpRegen - before.derived.hpRegen),
            "Regeneração MP" to (after.derived.mpRegen - before.derived.mpRegen),
            "Bônus de drop" to (after.derived.dropBonusPct - before.derived.dropBonusPct),
            "Tenacidade" to (after.derived.tenacityPct - before.derived.tenacityPct),
            "Penetração física" to (after.derived.penPhysical - before.derived.penPhysical),
            "Penetração mágica" to (after.derived.penMagic - before.derived.penMagic),
            "Recarga" to (after.derived.cdrPct - before.derived.cdrPct)
        )
        return deltas
            .filter { (_, delta) -> abs(delta) >= 0.01 }
            .map { (label, delta) -> "$label ${formatSignedDouble(delta)}" }
    }

    private fun buildAttributeMultiplierNotes(player: PlayerState): List<String> {
        val notes = mutableListOf<String>()
        if (player.roomEffectRooms > 0 && player.roomEffectMultiplier != 1.0) {
            val percent = (player.roomEffectMultiplier - 1.0) * 100.0
            notes += "Multiplicador temporário de sala em atributos: ${formatSignedDouble(percent)}%"
        }
        if (player.runAttrMultiplier != 1.0) {
            val percent = (player.runAttrMultiplier - 1.0) * 100.0
            notes += "Multiplicador da run em atributos: ${formatSignedDouble(percent)}%"
        }
        if (player.deathDebuffStacks > 0) {
            val percent = -20.0 * player.deathDebuffStacks
            notes += "Debuff de morte ativo: ${formatSignedDouble(percent)}% em atributos"
        }
        if (player.foodBuffRemainingMinutes > 0.0 && player.foodBuffName.isNotBlank()) {
            notes += "Buff culinário ativo: ${player.foodBuffName} (${format(player.foodBuffRemainingMinutes)} min restantes)"
        }
        return notes
    }

    private fun equipmentBonuses(player: PlayerState, itemInstances: Map<String, ItemInstance>): Bonuses {
        return player.equipped.values.mapNotNull { id ->
            val instance = itemInstances[id]
            val def = if (instance == null) engine.itemRegistry.item(id) else null
            val tags = instance?.tags ?: def?.tags ?: emptyList()
            if (!ClassQuestTagRules.canEquip(player, tags)) return@mapNotNull null
            instance?.bonuses ?: def?.bonuses
        }.fold(Bonuses()) { acc, next -> acc + next }
    }

    private fun temporaryAttributeBonuses(player: PlayerState): Attributes {
        val room = if (player.roomAttrRooms > 0) player.roomAttrBonus else Attributes()
        return player.runAttrBonus + room
    }

    private fun getAttr(attributes: Attributes, code: String): Int = when (code.uppercase()) {
        "STR" -> attributes.str
        "AGI" -> attributes.agi
        "DEX" -> attributes.dex
        "VIT" -> attributes.vit
        "INT" -> attributes.`int`
        "SPR" -> attributes.spr
        "LUK" -> attributes.luk
        else -> 0
    }

    private fun addAttr(attributes: Attributes, code: String, delta: Int): Attributes = when (code.uppercase()) {
        "STR" -> attributes.copy(str = attributes.str + delta)
        "AGI" -> attributes.copy(agi = attributes.agi + delta)
        "DEX" -> attributes.copy(dex = attributes.dex + delta)
        "VIT" -> attributes.copy(vit = attributes.vit + delta)
        "INT" -> attributes.copy(`int` = attributes.`int` + delta)
        "SPR" -> attributes.copy(spr = attributes.spr + delta)
        "LUK" -> attributes.copy(luk = attributes.luk + delta)
        else -> attributes
    }

    private fun formatSigned(value: Int): String = if (value >= 0) "+$value" else value.toString()
    private fun formatSignedDouble(value: Double): String = if (value >= 0.0) "+${format(value)}" else format(value)
    private fun format(value: Double): String = "%.1f".format(value)

    private data class AttrMeta(
        val code: String,
        val label: String
    )
}




