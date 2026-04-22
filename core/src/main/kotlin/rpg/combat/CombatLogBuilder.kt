package rpg.combat

internal class CombatLogBuilder(
    private val logger: (String) -> Unit
) {
    fun subjectLabel(actor: CombatActor): String = if (actor.kind == CombatantKind.PLAYER) {
        "Voce"
    } else {
        "O inimigo"
    }

    fun format(value: Double): String = "%.1f".format(value)

    fun combatLog(message: String) {
        logger(message)
    }

    fun colorize(text: String, colorCode: String): String = "$colorCode$text$ansiReset"

    companion object {
        const val ansiReset = "\u001B[0m"
        const val ansiRed = "\u001B[31m"
        const val ansiGreen = "\u001B[32m"
        const val ansiYellow = "\u001B[33m"
        const val ansiBlue = "\u001B[34m"
        const val ansiCyan = "\u001B[36m"
    }
}
