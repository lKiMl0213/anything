package rpg.cli.input

class CliInputReader {
    fun readLineOrThrow(prompt: String = "Escolha: "): String {
        print(prompt)
        return readLine()?.trim() ?: throw IllegalStateException("Entrada encerrada.")
    }
}
