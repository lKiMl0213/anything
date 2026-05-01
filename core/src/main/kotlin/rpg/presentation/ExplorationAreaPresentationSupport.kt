package rpg.presentation

import rpg.achievement.AchievementCounterKeys
import rpg.application.actions.GameAction
import rpg.engine.GameEngine
import rpg.model.DungeonRun
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.presentation.model.ScreenOptionViewModel

internal class ExplorationAreaPresentationSupport(
    private val engine: GameEngine
) {
    fun tierSelectionLabel(tier: MapTierDef, highestInfiniteFloor: Long): String {
        val areaName = engine.tierDisplayName(tier)
        val baseLabel = "$areaName (nv minimo ${tier.minLevel})"
        if (tier.isInfinite) {
            val info = if (highestInfiniteFloor > 0L) highestInfiniteFloor else 0L
            val baseNote = tier.menuNote.trim().ifBlank { "Boss a cada 10 vitorias." }
            return "$baseLabel | Andar mais alto alcancado: $info | $baseNote"
        }
        val previewMonsters = tier.allowedMonsterTemplates
            .mapNotNull(engine::monsterTemplateDisplayName)
            .distinct()
            .take(5)
        val note = tier.menuNote.trim().ifBlank {
            if (previewMonsters.isEmpty()) {
                "Sem preview de mobs."
            } else {
                "Mobs: ${previewMonsters.joinToString(", ")}"
            }
        }
        return "$baseLabel | $note"
    }

    fun infiniteHighestFloor(player: PlayerState): Long {
        val key = AchievementCounterKeys.customCounterKey(
            AchievementCounterKeys.Dungeon.NAMESPACE,
            AchievementCounterKeys.Dungeon.INFINITE_HIGHEST_FLOOR
        )
        return player.lifetimeStats.customCounters[key] ?: 0L
    }

    fun runLabel(player: PlayerState, run: DungeonRun): String {
        val tierLabel = engine.tierDisplayName(run.tierId) ?: "Area desconhecida"
        val classPathName = engine.classQuestService.resolveDungeonPathNameByRunData(
            player = player,
            unlockTypeRaw = run.classDungeonUnlockType,
            pathIdRaw = run.classDungeonPathId
        ) ?: return tierLabel
        return "Instancia de Classe ($classPathName) | Area base: $tierLabel"
    }

    fun continueRunAction(run: DungeonRun): GameAction? {
        val activeTierId = run.tierId?.trim().orEmpty()
        if (activeTierId.isEmpty()) return null
        return if (!run.classDungeonPathId.isNullOrBlank()) {
            GameAction.EnterClassDungeon(activeTierId)
        } else {
            GameAction.EnterDungeon(activeTierId)
        }
    }

    fun classDungeonSelectionOption(player: PlayerState, key: String): ScreenOptionViewModel? {
        val dungeon = engine.classQuestService.activeDungeon(player) ?: return null
        val entryTier = engine.classDungeonEntryTier(player, dungeon.unlockType) ?: return null
        val tierLabel = engine.tierDisplayName(entryTier)
        val label = "Instancia de Classe (${dungeon.pathName}) | Base: $tierLabel (nv minimo ${entryTier.minLevel})"
        return ScreenOptionViewModel(key, label, GameAction.EnterClassDungeon(entryTier.id))
    }
}
