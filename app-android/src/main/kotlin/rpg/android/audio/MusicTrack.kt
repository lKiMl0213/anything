package rpg.android.audio

enum class MusicTrack(
    val rawName: String
) {
    MENU("music_menu"),
    HOME("music_home"),
    CHARACTER("music_character"),
    PRODUCTION("music_production"),
    CITY("music_city"),
    PROGRESS("music_progress"),
    BATTLE("music_battle"),
    BOSS("music_boss"),
    VICTORY("music_victory"),
    DEFEAT("music_defeat")
}

