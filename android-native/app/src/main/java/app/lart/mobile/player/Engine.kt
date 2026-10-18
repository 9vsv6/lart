package app.lart.mobile.player

import android.view.View
import app.lart.mobile.data.PlayerPrefs

/** A track as an engine reports it; the activity turns these into menu rows. */
data class EngineTrack(
    val key: String,
    val label: String,
    val detail: String?,
    val selected: Boolean,
    val language: String?,
    val commentary: Boolean = false,
    /** Added from an addon (mpv loads those as its own tracks). */
    val external: Boolean = false,
)

interface EngineListener {
    fun onPlayingChanged(playing: Boolean)
    fun onBufferingChanged(buffering: Boolean)
    fun onEnded()
    fun onError(message: String)
    fun onTracksChanged()
    fun onVideoSizeChanged()
}

/**
 * What the player screen needs from a playback engine. mpv is the main engine, as in
 * Harbor's desktop player; Media3 is the fallback when mpv can't start on a device.
 */
interface Engine {
    val name: String
    val view: View
    var listener: EngineListener?

    fun load(url: String, headers: Map<String, String>, startMs: Long)
    fun play()
    fun pause()
    val isPlaying: Boolean
    val ended: Boolean
    val positionMs: Long
    /** 0 while unknown. */
    val durationMs: Long
    val bufferedMs: Long
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun setFit(fit: Fit)
    fun setAudioDelay(delayMs: Long)
    fun setSubDelay(delayMs: Long)
    val videoSize: Pair<Int, Int>?

    fun audioTracks(): List<EngineTrack>
    fun textTracks(): List<EngineTrack>
    fun selectAudio(key: String)
    /** null turns subtitles off. */
    fun selectText(key: String?)

    /** True when the engine draws subtitles itself (mpv, with libass); else Lart draws them. */
    val drawsSubtitles: Boolean
    /** Loads an addon subtitle as the engine's own track. Engines that can't return false. */
    fun addExternalSubtitle(url: String, language: String, title: String): Boolean
    fun removeExternalSubtitles()
    fun applySubtitleStyle(prefs: PlayerPrefs, lifted: Boolean)
    /** Subtitle events for Lart to draw at `positionMs` (engines that don't draw them). */
    fun cuesAt(positionMs: Long): List<UiCue>

    /** GLSL shaders (Anime4K); false when the engine has no shader support. */
    val supportsShaders: Boolean
    fun setShaders(paths: List<String>)

    fun release()
}
