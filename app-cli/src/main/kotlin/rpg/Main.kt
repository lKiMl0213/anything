package rpg

import java.nio.file.Paths
import rpg.cli.GameCli
import rpg.io.DataRepository

fun main() {
    val repo = DataRepository(Paths.get("data"))
    GameCli(repo).run()
}
