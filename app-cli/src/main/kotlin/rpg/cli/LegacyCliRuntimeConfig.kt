// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.cli.model.AttrMeta

internal object LegacyCliRuntimeConfig {
    val attributeMeta: List<AttrMeta> = listOf(
        AttrMeta("STR", "Forca"),
        AttrMeta("AGI", "Agilidade"),
        AttrMeta("DEX", "Destreza"),
        AttrMeta("VIT", "Vitalidade"),
        AttrMeta("INT", "Inteligencia"),
        AttrMeta("SPR", "Espirito"),
        AttrMeta("LUK", "Sorte")
    )

    const val offhandBlockedId: String = "__offhand_blocked__"

    const val restHealPct: Double = 0.20
    const val restRegenMultiplier: Double = 3.0

    const val deathBaseLootLossPct: Double = 80.0
    const val deathMinLootLossPct: Double = 20.0
    const val deathDebuffPerStack: Double = 0.20
    const val deathDebuffBaseMinutes: Double = 10.0
    const val deathDebuffExtraMinutes: Double = 5.0
    const val deathGoldLossPct: Double = 0.20
    const val deathXpPenaltyPct: Double = 20.0

    const val roomTimeMinutes: Double = 0.5
    const val clockSyncEpsilonMs: Long = 1000L

    const val tavernRestHealPct: Double = 0.25
}

internal object LegacyCliAnsiPalette {
    const val combatReset = "\u001B[0m"
    const val combatHeader = "\u001B[37m"
    const val combatPlayer = "\u001B[36m"
    const val combatEnemy = "\u001B[31m"
    const val combatLoading = "\u001B[33m"
    const val combatReady = "\u001B[32m"
    const val combatBlocked = "\u001B[31m"
    const val combatCasting = "\u001B[36m"
    const val combatPause = "\u001B[35m"
    const val clearLine = "\u001B[2K"
    const val clearToEnd = "\u001B[J"

    const val uiName = "\u001B[37m"
    const val uiLevel = "\u001B[36m"
    const val uiHp = "\u001B[32m"
    const val uiMp = "\u001B[34m"
    const val uiGold = "\u001B[33m"
    const val uiCash = "\u001B[35m"

    const val questActive = "\u001B[34m"
    const val questReady = "\u001B[32m"
    const val questAlert = "\u001B[33m"
}
