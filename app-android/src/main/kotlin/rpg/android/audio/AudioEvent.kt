package rpg.android.audio

sealed interface AudioEvent {
    data class PlaySfx(val effect: SoundEffect) : AudioEvent
    data class PlayMusicStinger(
        val track: MusicTrack,
        val durationMs: Long = 1800L
    ) : AudioEvent
}

