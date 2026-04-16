package rpg.item

import kotlin.random.Random

object ItemNameGenerator {
    private val prefixes = listOf(
        "Brutal",
        "Mistico",
        "Antigo",
        "Reforcado",
        "Reluzente",
        "Sombrio",
        "Veloz"
    )

    private val suffixes = listOf(
        "da Furia",
        "do Urso",
        "das Cinzas",
        "da Aurora",
        "do Eco",
        "da Ruina"
    )

    fun generate(base: String, rarity: ItemRarity, affixes: List<String>, rng: Random): String {
        val prefix = if (rarity >= ItemRarity.RARE) prefixes.random(rng) + " " else ""
        val suffix = if (affixes.isNotEmpty() && rarity >= ItemRarity.EPIC) " " + suffixes.random(rng) else ""
        return (prefix + base + suffix).trim()
    }
}
