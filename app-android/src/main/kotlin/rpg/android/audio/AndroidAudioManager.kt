package rpg.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import java.util.EnumMap

class AndroidAudioManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val packageName = appContext.packageName

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val indexedTracks = EnumMap<MusicTrack, Int?>(MusicTrack::class.java)
    private val indexedEffects = EnumMap<SoundEffect, Int?>(SoundEffect::class.java)
    private val loadedEffects = EnumMap<SoundEffect, Int>(SoundEffect::class.java)
    private val loadedSampleIds = mutableSetOf<Int>()

    private var settings = AudioSettings()
    private var musicPlayer: MediaPlayer? = null
    private var currentTrack: MusicTrack? = null
    private var released = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSampleIds += sampleId
            }
        }
        indexAllTracks()
        indexAllEffects()
    }

    fun updateSettings(next: AudioSettings) {
        settings = next
        if (!next.musicEnabled) {
            stopMusic()
        }
    }

    fun playMusic(track: MusicTrack?, loop: Boolean = true) {
        if (released) return
        if (!settings.musicEnabled || track == null) {
            stopMusic()
            return
        }
        if (currentTrack == track && musicPlayer?.isPlaying == true) return

        val resId = indexedTracks[track] ?: resolveRawResource(track.rawName)
        if (resId == null) {
            stopMusic()
            currentTrack = track
            return
        }

        val player = runCatching { MediaPlayer.create(appContext, resId) }.getOrNull()
        if (player == null) {
            stopMusic()
            currentTrack = track
            return
        }

        musicPlayer?.runCatching { stop() }
        musicPlayer?.release()
        musicPlayer = player
        currentTrack = track

        player.isLooping = loop
        player.setOnCompletionListener {
            if (!loop) {
                stopMusic()
            }
        }
        runCatching { player.start() }
            .onFailure { stopMusic() }
    }

    fun stopMusic() {
        musicPlayer?.runCatching { stop() }
        musicPlayer?.release()
        musicPlayer = null
    }

    fun playSfx(effect: SoundEffect, rate: Float = 1f) {
        if (released || !settings.effectsEnabled) return
        val sampleId = loadedEffects[effect] ?: return
        if (!loadedSampleIds.contains(sampleId)) return
        soundPool.play(
            sampleId,
            1f,
            1f,
            1,
            0,
            rate.coerceIn(0.5f, 1.5f)
        )
    }

    fun release() {
        if (released) return
        released = true
        stopMusic()
        soundPool.release()
        loadedEffects.clear()
        indexedTracks.clear()
        indexedEffects.clear()
        loadedSampleIds.clear()
    }

    private fun indexAllTracks() {
        MusicTrack.entries.forEach { track ->
            indexedTracks[track] = resolveRawResource(track.rawName)
        }
    }

    private fun indexAllEffects() {
        SoundEffect.entries.forEach { effect ->
            val resId = resolveRawResource(effect.rawName)
            indexedEffects[effect] = resId
            if (resId != null) {
                runCatching { soundPool.load(appContext, resId, 1) }
                    .getOrNull()
                    ?.let { sampleId ->
                        loadedEffects[effect] = sampleId
                    }
            }
        }
    }

    private fun resolveRawResource(rawName: String): Int? {
        val id = appContext.resources.getIdentifier(rawName, "raw", packageName)
        return if (id != 0) id else null
    }
}
