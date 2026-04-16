package rpg.achievement

import java.util.Locale
import rpg.model.PlayerState

class AchievementMenu(private val service: AchievementService) {
    fun buildAchievementList(player: PlayerState): AchievementListResult {
        return service.buildAchievementList(player)
    }

    fun buildStatistics(
        player: PlayerState,
        knownBaseTypes: Set<String>
    ): AchievementStatisticsView {
        val synced = service.synchronize(player, emitNotifications = false).player
        val stats = synced.lifetimeStats

        val generalLines = listOf(
            "Ouro obtido total: ${stats.totalGoldEarned}",
            "Ouro gasto total: ${stats.totalGoldSpent}",
            "Mortes: ${stats.totalDeaths}",
            "Descansos completos: ${stats.totalFullRestSleeps}",
            "Batalhas vencidas: ${stats.totalBattlesWon}",
            "Batalhas perdidas: ${stats.totalBattlesLost}",
            "Bosses derrotados: ${stats.totalBossesKilled}",
            "Dano causado: ${formatDouble(stats.totalDamageDealt)}",
            "Dano recebido: ${formatDouble(stats.totalDamageTaken)}"
        )

        val starLines = (0..7).map { star ->
            val kills = stats.killsByStar[star] ?: 0L
            "$star*: $kills"
        }

        val mergedBaseTypes = (knownBaseTypes.map(::normalizeBaseType) + stats.killsByBaseType.keys)
            .filter { it.isNotBlank() }
            .toSet()
            .sortedWith(
                compareByDescending<String> { stats.killsByBaseType[it] ?: 0L }
                    .thenBy { baseTypeLabel(it) }
            )

        val bestiaryLines = mergedBaseTypes.map { baseType ->
            val kills = stats.killsByBaseType[baseType] ?: 0L
            val maxStar = stats.highestStarByBaseType[baseType] ?: 0
            "${baseTypeLabel(baseType)}: $kills mortos | Maior estrela: ${maxStar}*"
        }

        return AchievementStatisticsView(
            player = synced,
            generalLines = generalLines,
            killsByStarLines = starLines,
            bestiaryLines = bestiaryLines
        )
    }

    private fun normalizeBaseType(raw: String): String {
        return raw.trim().lowercase().ifBlank { "unknown" }
    }

    private fun baseTypeLabel(baseType: String): String {
        return when (normalizeBaseType(baseType)) {
            "slime" -> "Slime"
            "wolf" -> "Lobo"
            "elemental" -> "Elemental"
            else -> {
                normalizeBaseType(baseType)
                    .replace('-', '_')
                    .split('_')
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { token ->
                        token.lowercase().replaceFirstChar { char ->
                            if (char.isLowerCase()) {
                                char.titlecase(Locale.getDefault())
                            } else {
                                char.toString()
                            }
                        }
                    }
            }
        }
    }

    private fun formatDouble(value: Double): String = "%.1f".format(value)
}

