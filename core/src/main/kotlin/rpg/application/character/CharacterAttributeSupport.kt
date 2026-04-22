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
        AttrMeta("STR", "Forca"),
        AttrMeta("AGI", "Agilidade"),
        AttrMeta("DEX", "Destreza"),
        AttrMeta("VIT", "Vitalidade"),
        AttrMeta("INT", "Inteligencia"),
        AttrMeta("SPR", "Espirito"),
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
        lines += "Bonus de equipamento: ${formatSigned(getAttr(equipmentBonus, meta.code))}"
        lines += "Bonus de classe/raca/talento: ${formatSigned(getAttr(classTalentBonus, meta.code))}"
        lines += "Bonus temporario: ${formatSigned(getAttr(temporaryBonus, meta.code))}"
        lines += "Valor final: ${getAttr(computed.attributes, meta.code)}"
        lines += "Afeta diretamente:"
        lines += generateAttributeDescription(meta).map { "- $it" }
        lines += "Impacto na gameplay:"
        lines += generateGameplayImpact(meta).map { "- $it" }
        if (player.unspentAttrPoints > 0) {
            lines += "Investindo 1 ponto agora:"
            lines += previewAttributeSpend(player, state.itemInstances, meta.code).map { "- $it" }
        }
        val notes = buildAttributeMultiplierNotes(player)
        if (notes.isNotEmpty()) {
            lines += "Observacoes:"
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
        val player = state.player
        val meta = attributeMeta.firstOrNull { it.code.equals(attributeCode, ignoreCase = true) }
            ?: return CharacterMutationResult(state, listOf("Atributo invalido."))
        if (player.unspentAttrPoints <= 0) {
            return CharacterMutationResult(state, listOf("Nenhum ponto de atributo disponivel."))
        }
        val updatedPlayer = clampPlayerResources(
            player.copy(
                baseAttributes = addAttr(player.baseAttributes, meta.code, 1),
                unspentAttrPoints = (player.unspentAttrPoints - 1).coerceAtLeast(0)
            ),
            state.itemInstances
        )
        return CharacterMutationResult(
            state.copy(player = updatedPlayer),
            listOf("1 ponto investido em ${meta.label}.")
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
            "Atributo final ${attributeCode.uppercase()}" to (getAttr(after.attributes, attributeCode) - getAttr(before.attributes, attributeCode)).toDouble(),
            "Dano fisico" to (after.derived.damagePhysical - before.derived.damagePhysical),
            "Dano magico" to (after.derived.damageMagic - before.derived.damageMagic),
            "HP maximo" to (after.derived.hpMax - before.derived.hpMax),
            "MP maximo" to (after.derived.mpMax - before.derived.mpMax),
            "Defesa fisica" to (after.derived.defPhysical - before.derived.defPhysical),
            "Defesa magica" to (after.derived.defMagic - before.derived.defMagic),
            "Velocidade de ataque" to (after.derived.attackSpeed - before.derived.attackSpeed),
            "Velocidade de movimento" to (after.derived.moveSpeed - before.derived.moveSpeed),
            "Critico" to (after.derived.critChancePct - before.derived.critChancePct),
            "Precisao" to (after.derived.accuracy - before.derived.accuracy),
            "Esquiva" to (after.derived.evasion - before.derived.evasion),
            "Regeneracao HP" to (after.derived.hpRegen - before.derived.hpRegen),
            "Regeneracao MP" to (after.derived.mpRegen - before.derived.mpRegen),
            "Drop bonus" to (after.derived.dropBonusPct - before.derived.dropBonusPct),
            "Tenacidade" to (after.derived.tenacityPct - before.derived.tenacityPct),
            "Penetracao fisica" to (after.derived.penPhysical - before.derived.penPhysical),
            "Penetracao magica" to (after.derived.penMagic - before.derived.penMagic),
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
            notes += "Multiplicador temporario de sala em atributos: ${formatSignedDouble(percent)}%"
        }
        if (player.runAttrMultiplier != 1.0) {
            val percent = (player.runAttrMultiplier - 1.0) * 100.0
            notes += "Multiplicador da run em atributos: ${formatSignedDouble(percent)}%"
        }
        if (player.deathDebuffStacks > 0) {
            val percent = -20.0 * player.deathDebuffStacks
            notes += "Debuff de morte ativo: ${formatSignedDouble(percent)}% em atributos"
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

    private fun generateAttributeDescription(attribute: AttrMeta): List<String> = when (attribute.code) {
        "STR" -> listOf(
            "Contribui para dano fisico base (+2.5 por ponto).",
            "Aumenta penetracao fisica (+0.5 por ponto).",
            "Contribui para defesa fisica (+0.5 por ponto)."
        )
        "AGI" -> listOf(
            "Aumenta velocidade de ataque base (+0.02 por ponto).",
            "Aumenta esquiva/evasao (+0.8 por ponto).",
            "Aumenta velocidade de movimento (+0.05 por ponto)."
        )
        "DEX" -> listOf(
            "Contribui para dano fisico base (+1.2 por ponto).",
            "Aumenta precisao/acerto (+1.5 por ponto).",
            "Aumenta chance de critico (+0.3) e dano critico (+0.5)."
        )
        "VIT" -> listOf(
            "Aumenta HP maximo (+12 por ponto).",
            "Aumenta defesa fisica (+1.5 por ponto).",
            "Aumenta regeneracao de HP (+0.2 por ponto)."
        )
        "INT" -> listOf(
            "Aumenta dano magico base (+3.0 por ponto).",
            "Aumenta penetracao magica (+0.7 por ponto).",
            "Contribui para defesa magica (+0.7 por ponto)."
        )
        "SPR" -> listOf(
            "Aumenta MP maximo (+10 por ponto).",
            "Aumenta defesa magica (+1.8 por ponto).",
            "Aumenta regeneracao de MP (+0.3) e reducao de cooldown (+0.15%)."
        )
        "LUK" -> listOf(
            "Aumenta chance de critico (+0.2 por ponto).",
            "Aumenta vampirismo (1% a cada 10 pontos).",
            "Aumenta bonus de drop (+0.2% por ponto)."
        )
        else -> listOf("Sem descricao configurada.")
    }

    private fun generateGameplayImpact(attribute: AttrMeta): List<String> = when (attribute.code) {
        "STR" -> listOf(
            "Mais STR aumenta o dano de ataques fisicos e melhora a consistencia contra defesa fisica.",
            "Builds corpo a corpo sentem ganho direto no burst."
        )
        "AGI" -> listOf(
            "Mais AGI acelera o ritmo do combate e ajuda a evitar golpes.",
            "Classes moveis aproveitam melhor esse atributo."
        )
        "DEX" -> listOf(
            "Mais DEX melhora acerto, critico e dano de ataques de precisao.",
            "Arqueiros e builds criticas escalam muito bem aqui."
        )
        "VIT" -> listOf(
            "Mais VIT aumenta margem de erro, sustain e resistencia fisica.",
            "Excelente para frontliners e setups defensivos."
        )
        "INT" -> listOf(
            "Mais INT aumenta dano magico e perfuracao magica.",
            "Magos ofensivos dependem fortemente deste atributo."
        )
        "SPR" -> listOf(
            "Mais SPR aumenta mana, sustain magico e defesa contra magia.",
            "Tambem acelera cooldowns de forma gradual."
        )
        "LUK" -> listOf(
            "Mais LUK melhora critico, drop e vampirismo.",
            "Bom atributo complementar para builds oportunistas."
        )
        else -> listOf("Sem impacto configurado.")
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
