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
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
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
  private var cueText = ""
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
    // The page draws every subtitle. Subtitles embedded in the file stay off
    // until the page picks one (selectText); their cue text is then sent up.
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
      .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
      .build()
    p.addListener(object : Player.Listener {
      override fun onPlayerError(error: PlaybackException) {
        lastError = "${error.errorCodeName}: ${error.message ?: ""}"
        emitState()
      }

      override fun onCues(cueGroup: CueGroup) {
        // ASS signs arrive as extra cues at the same time; keep each line once.
        cueText = cueGroup.cues.mapNotNull { it.text?.toString()?.trim() }
          .filter { it.isNotEmpty() }
          .distinct()
          .joinToString("\n")
        emitState()
      }

      // With no decoder for the video track ExoPlayer raises no error: it plays
      // the audio over a black picture. Video is hardware-only here (the FFmpeg
      // extension decodes audio), so say which format the device cannot play.
      override fun onTracksChanged(tracks: Tracks) {
        val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        if (video.isEmpty() || video.any { g -> (0 until g.length).any { g.isTrackSupported(it) } }) return
        val f = video.first().getTrackFormat(0)
        lastError = "VIDEO_UNSUPPORTED: This device cannot decode ${describeVideo(f)} video" +
          (if (f.width > 0) " at ${f.width}x${f.height}" else "") + ". Pick another source."
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
      // The page draws the cues; the view's own subtitle layer would double them.
      subtitleView?.visibility = View.GONE
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
    cueText = ""
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
      .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
      .clearOverridesOfType(C.TRACK_TYPE_TEXT)
      .build()
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

  fun play() {
    val p = player ?: return
    p.play()
    // The ticker stops while the app is in the background; playing restarts it.
    handler.removeCallbacks(ticker)
    handler.post(ticker)
  }

  fun pause() { player?.pause() }

  fun seek(ms: Long) {
    val p = player ?: return
    p.seekTo(ms.coerceAtLeast(0))
    // A seek after returning from the background needs the state flowing again
    // for the page to see it land.
    handler.removeCallbacks(ticker)
    handler.post(ticker)
  }

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

  /** Turns on the embedded subtitle track "group:track", or all of them off for "". */
  fun selectText(id: String) {
    val p = player ?: return
    val builder = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
    val parts = id.split(":").mapNotNull { it.toIntOrNull() }
    val groups = p.currentTracks.groups
    if (parts.size != 2 || parts[0] !in groups.indices || groups[parts[0]].type != C.TRACK_TYPE_TEXT) {
      cueText = ""
      p.trackSelectionParameters = builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
      emitState()
      return
    }
    p.trackSelectionParameters = builder
      .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
      .setOverrideForType(TrackSelectionOverride(groups[parts[0]].mediaTrackGroup, parts[1]))
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

  fun onHostPause() {
    val p = player ?: return
    p.pause()
    // One last state so the page records the paused position, then no more
    // JavaScript calls four times a second while nobody can see the app.
    emitState()
    handler.removeCallbacks(ticker)
  }

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
        val codec = when (val mime = f.sampleMimeType ?: "") {
          "audio/mp4a-latm" -> "AAC"
          "audio/eac3", "audio/eac3-joc" -> "DD+"
          "audio/ac3" -> "Dolby Digital"
          "audio/true-hd" -> "TrueHD"
          "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
          else -> mime.substringAfter("/").uppercase()
        }
        // "und" is the container's "undetermined"; it reads as noise.
        val lang = f.language?.takeUnless { it.equals("und", true) }
        val name = listOfNotNull(f.label, lang?.uppercase(), codec, channels)
          .filter { it.isNotBlank() }
          .distinct()
          .joinToString(" · ")
        audio.put(
          JSONObject()
            .put("id", "$g:$t")
            .put("label", name.ifBlank { "Audio ${audio.length() + 1}" })
            .put("lang", lang ?: "")
            .put("title", f.label ?: "")
            .put("codec", codec)
            .put("channels", channels)
            .put("selected", group.isTrackSelected(t))
            .put("supported", group.isTrackSupported(t)),
        )
      }
    }
    val subs = JSONArray()
    p.currentTracks.groups.forEachIndexed { g, group ->
      if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
      for (t in 0 until group.length) {
        val f = group.getTrackFormat(t)
        // Media3 parses subtitles while extracting; the original format moves to codecs.
        val mime = (if (f.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) f.codecs else f.sampleMimeType) ?: ""
        // PGS, VobSub and DVB subtitles are pictures; the page can only draw text.
        if (mime in IMAGE_SUBTITLES) continue
        val lang = f.language?.takeUnless { it.equals("und", true) }
        subs.put(
          JSONObject()
            .put("id", "$g:$t")
            .put("label", listOfNotNull(f.label, lang?.uppercase()).filter { it.isNotBlank() }.distinct()
              .joinToString(" · ").ifBlank { "Subtitle ${subs.length() + 1}" })
            .put("lang", lang ?: "")
            .put("forced", f.selectionFlags and C.SELECTION_FLAG_FORCED != 0)
            .put("selected", group.isTrackSelected(t)),
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
      .put("subs", subs)
      .put("cue", cueText)
    lastError?.let { json.put("error", it) }
    emit(json.toString())
  }

  private companion object {
    const val Format_NO_VALUE = -1
    val IMAGE_SUBTITLES = setOf(MimeTypes.APPLICATION_PGS, MimeTypes.APPLICATION_VOBSUB, MimeTypes.APPLICATION_DVBSUBS)

    fun describeVideo(f: Format): String {
      val codecs = f.codecs ?: ""
      return when (f.sampleMimeType) {
        // avc1.6E.... is the High 10 profile, common in anime releases and
        // decoded in hardware by almost no Android device.
        MimeTypes.VIDEO_H264 -> if (codecs.startsWith("avc1.6E", true)) "10-bit H.264 (Hi10P)" else "H.264"
        MimeTypes.VIDEO_H265 -> if (codecs.contains(".2.", true)) "10-bit HEVC" else "HEVC"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        else -> f.sampleMimeType?.substringAfter("/")?.uppercase() ?: "this"
      }
    }
  }
}
