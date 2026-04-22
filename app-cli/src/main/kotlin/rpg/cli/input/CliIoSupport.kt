package rpg.cli.input

internal class CliIoSupport(
    private val onInputClosed: () -> Nothing,
    private val readInputLine: () -> String?,
    private val printInline: (String) -> Unit,
    private val printLine: (String) -> Unit
) {
    fun readNonEmpty(prompt: String): String {
        while (true) {
            printInline(prompt)
            val input = readInputLine()?.trim() ?: onInputClosed()
            if (input.isNotEmpty()) return input
        }
    }

    fun readMenuChoice(prompt: String, min: Int, max: Int): Int? {
        while (true) {
            printInline(prompt)
            val input = (readInputLine()?.trim() ?: onInputClosed()).lowercase()
            if (input == "x") return null
            val value = input.toIntOrNull()
            if (value != null && value in min..max) return value
        }
    }

    fun readInt(prompt: String, min: Int, max: Int): Int {
        while (true) {
            printInline(prompt)
            val input = readInputLine()?.trim() ?: onInputClosed()
            val value = validateInput(input, min, max)
            if (value != null) return value
        }
    }

    fun validateInput(input: String, min: Int, max: Int): Int? {
        val value = input.toIntOrNull() ?: return null
        if (value < min) return null
        if (value > max) return null
        return value
    }

    fun <T> choose(
        label: String,
        options: List<T>,
        nameOf: (T) -> String
    ): T {
        if (options.isEmpty()) error("Nenhuma opcao disponivel para $label")
        printLine("\n$label:")
        options.forEachIndexed { index, option ->
            printLine("${index + 1}. ${nameOf(option)}")
        }
        val choice = readInt("Escolha: ", 1, options.size)
        return options[choice - 1]
    }
}
