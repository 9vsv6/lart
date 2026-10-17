package app.lart

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Media3 ExoPlayer drawn behind the transparent WebView. The page keeps its
 * own controls and subtitles and drives this through window.LartAndroid; state
 * flows back as window.__lartNative(state) four times a second.
 *
 * Audio the device cannot decode (AC3, E-AC3, DTS, TrueHD and the 7.1 layouts
 * that use them) falls back to the bundled FFmpeg decoder.
 */
@OptIn(UnstableApi::class)
class NativePlayer(
  private val activity: MainActivity,
  private val container: ViewGroup,
  private val emit: (String) -> Unit,
) {
  private var player: ExoPlayer? = null
  private var view: PlayerView? = null
  private var lastError: String? = null
  private val handler = Handler(Looper.getMainLooper())
  private val ticker = object : Runnable {
    override fun run() {
      emitState()
      handler.postDelayed(this, 250)
    }
  }

  private fun ensurePlayer(): ExoPlayer {
    player?.let { return it }
    val renderers = DefaultRenderersFactory(activity)
      .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
      .setEnableDecoderFallback(true)
    val p = ExoPlayer.Builder(activity, renderers).build()
    p.setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build(),
      true,
    )
    // Subtitles stay in the page, which fetches, times and styles them.
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
      .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
      .build()
    p.addListener(object : Player.Listener {
      override fun onPlayerError(error: PlaybackException) {
        lastError = "${error.errorCodeName}: ${error.message ?: ""}"
        emitState()
      }
    })
    val v = PlayerView(activity).apply {
      useController = false
      resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      setShutterBackgroundColor(Color.BLACK)
      setBackgroundColor(Color.BLACK)
      keepScreenOn = true
      this.player = p
      visibility = View.GONE
    }
    container.addView(
      v,
      0,
      FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
    )
    player = p
    view = v
    return p
  }

  fun load(url: String, headersJson: String, startMs: Long) {
    val p = ensurePlayer()
    lastError = null
    val headers = mutableMapOf<String, String>()
    runCatching {
      val o = JSONObject(headersJson)
      for (key in o.keys()) headers[key] = o.getString(key)
    }
    val http = DefaultHttpDataSource.Factory()
      .setAllowCrossProtocolRedirects(true)
      .setUserAgent(headers.remove("User-Agent") ?: "Lart")
      .setDefaultRequestProperties(headers)
    val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(activity, http))
    p.setMediaSource(factory.createMediaSource(MediaItem.fromUri(url)))
    p.prepare()
    if (startMs > 0) p.seekTo(startMs)
    view?.visibility = View.VISIBLE
    activity.setNativeVideoVisible(true)
    handler.removeCallbacks(ticker)
    handler.post(ticker)
  }

  fun play() { player?.play() }

  fun pause() { player?.pause() }

  fun seek(ms: Long) { player?.seekTo(ms.coerceAtLeast(0)) }

  fun setVolume(v: Float) { player?.volume = v.coerceIn(0f, 1f) }

  fun setRate(r: Float) { player?.setPlaybackSpeed(r.coerceIn(0.25f, 4f)) }

  fun selectAudio(id: String) {
    val p = player ?: return
    val (g, t) = id.split(":").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: return
    val groups = p.currentTracks.groups
    if (g !in groups.indices || groups[g].type != C.TRACK_TYPE_AUDIO) return
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
      .setOverrideForType(TrackSelectionOverride(groups[g].mediaTrackGroup, t))
      .build()
  }

  fun stop() {
    handler.removeCallbacks(ticker)
    player?.stop()
    player?.clearMediaItems()
    view?.visibility = View.GONE
    activity.setNativeVideoVisible(false)
    lastError = null
  }

  fun onHostPause() { player?.pause() }

  fun release() {
    handler.removeCallbacks(ticker)
    player?.release()
    player = null
    view?.let { container.removeView(it) }
    view = null
  }

  @SuppressLint("SwitchIntDef")
  private fun emitState() {
    val p = player ?: return
    val state = when (p.playbackState) {
      Player.STATE_BUFFERING -> "buffering"
      Player.STATE_READY -> "ready"
      Player.STATE_ENDED -> "ended"
      else -> "idle"
    }
    val audio = JSONArray()
    p.currentTracks.groups.forEachIndexed { g, group ->
      if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
      for (t in 0 until group.length) {
        val f = group.getTrackFormat(t)
        val channels = when (f.channelCount) {
          8 -> "7.1"
          6 -> "5.1"
          2 -> "Stereo"
          1 -> "Mono"
          Format_NO_VALUE -> ""
          else -> "${f.channelCount}ch"
        }
        val codec = (f.sampleMimeType ?: "").substringAfter("/").uppercase()
        val name = listOfNotNull(f.label, f.language?.uppercase(), codec, channels)
          .filter { it.isNotBlank() }
          .joinToString(" · ")
        audio.put(
          JSONObject()
            .put("id", "$g:$t")
            .put("label", name.ifBlank { "Audio ${audio.length() + 1}" })
            .put("lang", f.language ?: "")
            .put("selected", group.isTrackSelected(t))
            .put("supported", group.isTrackSupported(t)),
        )
      }
    }
    val size = p.videoSize
    val json = JSONObject()
      .put("pos", p.currentPosition / 1000.0)
      .put("dur", if (p.duration == C.TIME_UNSET) -1.0 else p.duration / 1000.0)
      .put("buf", p.bufferedPosition / 1000.0)
      .put("pwr", p.playWhenReady)
      .put("playing", p.isPlaying)
      .put("state", state)
      .put("w", size.width)
      .put("h", size.height)
      .put("rate", p.playbackParameters.speed.toDouble())
      .put("vol", p.volume.toDouble())
      .put("audio", audio)
    lastError?.let { json.put("error", it) }
    emit(json.toString())
  }

  private companion object {
    const val Format_NO_VALUE = -1
  }
}
