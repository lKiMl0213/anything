package rpg.cli.text

internal class TextFormattingSupport(
    private val ansiReset: String,
    private val ansiAlert: String
) {
    fun menuAlert(active: Boolean): String {
        return if (active) uiColor("(!)", ansiAlert) else ""
    }

    fun labelWithAlert(baseLabel: String, alert: String): String {
        return if (alert.isBlank()) baseLabel else "$baseLabel $alert"
    }

    fun uiColor(text: String, colorCode: String): String = "$colorCode$text$ansiReset"
}
