package rpg.events

import kotlin.math.max
import rpg.model.DungeonEventDef

enum class NpcEventVariant {
    MONEY,
    ITEM,
    SUSPICIOUS
}

class DungeonEventService(private val def: DungeonEventDef) {
    private val rules = def.rules
    private val texts = def.texts

    fun pickNpcVariant(rollInt: (Int) -> Int): NpcEventVariant {
        val weights = listOf(
            NpcEventVariant.MONEY to rules.npcMoneyWeight,
            NpcEventVariant.ITEM to rules.npcItemWeight,
            NpcEventVariant.SUSPICIOUS to rules.npcSuspiciousWeight
        ).map { it.first to it.second.coerceAtLeast(0) }
        val total = weights.sumOf { it.second }.coerceAtLeast(1)
        var roll = rollInt(total)
        for ((variant, weight) in weights) {
            roll -= weight
            if (roll < 0) return variant
        }
        return NpcEventVariant.MONEY
    }

    fun npcIntro(rollInt: (Int) -> Int): String = pick(texts.npcIntro, rollInt, "um viajante pede ajuda.")

    fun npcMoneyPitch(gold: Int, rollInt: (Int) -> Int): String {
        val template = pick(texts.npcMoneyPitch, rollInt, "Ele pede {gold} de ouro para pagar uma passagem segura.")
        return template.replace("{gold}", gold.toString())
    }

    fun npcMoneyRefuseLine(rollInt: (Int) -> Int): String = pick(texts.npcMoneyRefuseLines, rollInt, "A recusa termina em emboscada.")
    fun npcMoneyNoGoldLine(rollInt: (Int) -> Int): String = pick(texts.npcMoneyNoGoldLines, rollInt, "Voce nao tem ouro suficiente.")
    fun npcMoneyScamLine(rollInt: (Int) -> Int): String = pick(texts.npcMoneyScamLines, rollInt, "Era uma armadilha.")
    fun npcMoneyRewardLine(rollInt: (Int) -> Int): String = pick(texts.npcMoneyRewardLines, rollInt, "Ele agradece e ajuda.")
    fun npcMoneyNeutralLine(rollInt: (Int) -> Int): String = pick(texts.npcMoneyNeutralLines, rollInt, "Ele some sem recompensa.")

    fun npcItemPitch(itemName: String, qty: Int, rollInt: (Int) -> Int): String {
        val template = pick(texts.npcItemPitch, rollInt, "Ele pede {item} x{qty}.")
        return template.replace("{item}", itemName).replace("{qty}", qty.toString())
    }

    fun npcItemNoItemsLine(rollInt: (Int) -> Int): String = pick(texts.npcItemNoItemsLines, rollInt, "Voce nao tem itens para ajudar.")
    fun npcItemRefuseLine(rollInt: (Int) -> Int): String = pick(texts.npcItemRefuseLines, rollInt, "A recusa ativa uma armadilha.")
    fun npcItemScamLine(rollInt: (Int) -> Int): String = pick(texts.npcItemScamLines, rollInt, "A troca era falsa.")
    fun npcItemRewardLine(rollInt: (Int) -> Int): String = pick(texts.npcItemRewardLines, rollInt, "A troca gera uma recompensa.")
    fun npcItemNeutralLine(rollInt: (Int) -> Int): String = pick(texts.npcItemNeutralLines, rollInt, "Ele agradece e vai embora.")

    fun npcSuspiciousPitch(rollInt: (Int) -> Int): String = pick(texts.npcSuspiciousPitch, rollInt, "Ele oferece uma rota secreta.")
    fun npcSuspiciousRefuseLine(rollInt: (Int) -> Int): String = pick(texts.npcSuspiciousRefuseLines, rollInt, "Voce ignora e segue alerta.")
    fun npcSuspiciousScamLine(rollInt: (Int) -> Int): String = pick(texts.npcSuspiciousScamLines, rollInt, "Era uma armadilha.")
    fun npcSuspiciousRewardLine(rollInt: (Int) -> Int): String = pick(texts.npcSuspiciousRewardLines, rollInt, "O atalho se prova real.")

    fun liquidIntro(rollInt: (Int) -> Int): String = pick(texts.liquidIntro, rollInt, "um liquido estranho brilha sobre a pedra.")
    fun liquidIgnoreLine(rollInt: (Int) -> Int): String = pick(texts.liquidIgnoreLines, rollInt, "Voce decide nao arriscar.")
    fun chestIntro(rollInt: (Int) -> Int): String = pick(texts.chestIntro, rollInt, "um bau antigo repousa no canto.")
    fun chestIgnoreLine(rollInt: (Int) -> Int): String = pick(texts.chestIgnoreLines, rollInt, "Voce deixa o bau para tras.")
    fun chestAmbushLine(rollInt: (Int) -> Int): String = pick(texts.chestAmbushLines, rollInt, "Era uma armadilha.")

    fun requestedGold(playerLevel: Int, depth: Int, rollInt: (Int) -> Int): Int {
        return max(8, playerLevel * 2 + depth + rollInt(8))
    }

    fun shouldAmbushOnMoneyRefuse(rollInt: (Int) -> Int): Boolean {
        return chance(rules.npcMoneyRefuseAmbushChancePct, rollInt)
    }

    fun shouldAmbushOnMoneyNoGold(rollInt: (Int) -> Int): Boolean {
        return chance(rules.npcMoneyNoGoldAmbushChancePct, rollInt)
    }

    fun shouldScamOnMoneyGive(rollInt: (Int) -> Int): Boolean {
        return chance(rules.npcMoneyGiveScamChancePct, rollInt)
    }

    fun shouldRewardOnMoneyGive(rollInt: (Int) -> Int): Boolean {
        return chance(rules.npcMoneyGiveRewardChancePct, rollInt)
    }

    fun shouldAmbushOnItemRefuse(rollInt: (Int) -> Int): Boolean {
        return chance(rules.npcItemRefuseAmbushChancePct, rollInt)
    }

    fun shouldScamOnItemGive(rollInt: (Int) -> Int): Boolean {
        return chance(rules.npcItemGiveScamChancePct, rollInt)
    }

    fun shouldRewardOnItemGive(rollInt: (Int) -> Int): Boolean {
        return chance(rules.npcItemGiveRewardChancePct, rollInt)
    }

    fun shouldAmbushOnSuspiciousAccept(rollInt: (Int) -> Int): Boolean {
        return chance(rules.npcSuspiciousAmbushChancePct, rollInt)
    }

    fun chestMimicChancePct(inspected: Boolean): Int {
        return if (inspected) {
            rules.chestMimicInspectChancePct.coerceIn(0, 100)
        } else {
            rules.chestMimicFastChancePct.coerceIn(0, 100)
        }
    }

    private fun pick(lines: List<String>, rollInt: (Int) -> Int, fallback: String): String {
        if (lines.isEmpty()) return fallback
        val index = rollInt(lines.size).coerceIn(0, lines.size - 1)
        return lines[index]
    }

    private fun chance(chancePct: Int, rollInt: (Int) -> Int): Boolean {
        return rollInt(100) < chancePct.coerceIn(0, 100)
    }
}
