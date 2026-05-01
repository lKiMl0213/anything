package rpg

import java.nio.file.Paths
import rpg.cli.ConsoleEncodingConfigurator
import rpg.cli.GameCli
import rpg.io.DataRepository

fun main() {
    ConsoleEncodingConfigurator.apply()
    val repo = DataRepository(Paths.get("data"))
    GameCli(repo).run()
}
