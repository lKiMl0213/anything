package rpg.android.ui.scale

enum class GameUiScale(
    val storageKey: String,
    val densityFontFactor: Float
) {
    SMALL(
        storageKey = "small",
        densityFontFactor = 0.92f
    ),
    MEDIUM(
        storageKey = "medium",
        densityFontFactor = 1.00f
    ),
    LARGE(
        storageKey = "large",
        densityFontFactor = 1.12f
    );

    companion object {
        val default: GameUiScale = MEDIUM

        fun fromStorageKey(raw: String?): GameUiScale {
            if (raw.isNullOrBlank()) return default
            return entries.firstOrNull { it.storageKey.equals(raw, ignoreCase = true) } ?: default
        }
    }
}

