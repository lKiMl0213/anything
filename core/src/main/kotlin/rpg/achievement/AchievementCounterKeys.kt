package rpg.achievement

object AchievementCounterKeys {
    object Enchant {
        const val NAMESPACE = "enchant"
        const val ATTEMPTS = "attempts"
        const val SUCCESSES = "successes"
        const val ITEMS_PLUS_10_OR_MORE = "items_plus_10_or_more"
    }

    object Fusion {
        const val NAMESPACE = "fusion"
        const val TOTAL = "total"
    }

    object Extraction {
        const val NAMESPACE = "extraction"
        const val TOTAL = "total"
        const val STONES_CREATED = "stones_created"
    }

    object Hunting {
        const val NAMESPACE = "hunting"
        const val TOTAL_UNITS = "total_units"
        const val RARE_DROPS = "rare_drops"
    }

    object Cooking {
        const val NAMESPACE = "cooking"
        const val RECIPES_DONE = "recipes_done"
        const val BUFFS_USED = "buffs_used"
    }

    object EnchantResources {
        const val NAMESPACE = "enchant_resources"
        const val ACQUIRED = "acquired"
    }

    object Dungeon {
        const val NAMESPACE = "dungeon"
        const val INFINITE_HIGHEST_FLOOR = "infinite_highest_floor"
    }

    fun customCounterKey(namespace: String, key: String): String {
        val left = namespace.trim().lowercase().ifBlank { "global" }
        val right = key.trim().lowercase().ifBlank { "counter" }
        return "$left:$right"
    }
}
