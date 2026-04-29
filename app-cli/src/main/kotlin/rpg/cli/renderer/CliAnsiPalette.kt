package rpg.cli.renderer

internal object CliAnsiPalette {
    const val reset = "\u001B[0m"
    const val clearScreen = "\u001B[H\u001B[2J\u001B[3J"
    const val clearLine = "\u001B[2K"
    const val clearToEnd = "\u001B[J"

    const val header = "\u001B[37m"
    const val subtitle = "\u001B[36m"
    const val muted = "\u001B[90m"

    const val hp = "\u001B[32m"
    const val mp = "\u001B[34m"
    const val gold = "\u001B[33m"
    const val cash = "\u001B[35m"
    const val level = "\u001B[36m"
    const val name = "\u001B[37m"

    const val success = "\u001B[32m"
    const val warning = "\u001B[33m"
    const val danger = "\u001B[31m"
    const val info = "\u001B[36m"

    const val combatHeader = "\u001B[37m"
    const val combatPlayer = "\u001B[36m"
    const val combatEnemy = "\u001B[31m"
    const val combatLoading = "\u001B[33m"
    const val combatReady = "\u001B[32m"
    const val combatBlocked = "\u001B[31m"
    const val combatCasting = "\u001B[36m"
    const val combatPause = "\u001B[35m"
}
