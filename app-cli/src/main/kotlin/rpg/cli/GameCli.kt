package rpg.cli

import rpg.io.DataRepository

class GameCli(private val repo: DataRepository) {
    fun run() {
        CliFlowController(repo).run()
    }
}
