package app.lart.mobile.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import app.lart.mobile.LartApp
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.Net
import app.lart.mobile.data.Playback
import app.lart.mobile.data.Ready
import app.lart.mobile.data.Subtitle
import app.lart.mobile.data.Video
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.Locale

/**
 * Full-screen Media3 playback. Embedded text and ASS subtitles, audio and subtitle
 * track menus come from Media3 itself; addon subtitles are attached to the item.
 * Audio the device can't decode (AC3, E-AC3, DTS, TrueHD) falls back to FFmpeg.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var videoId: String
    private lateinit var type: String
    private lateinit var metaId: String
    private lateinit var http: DefaultHttpDataSource.Factory
    private lateinit var nextButton: Button
    private val repository get() = (application as LartApp).repository

    /** The episode after this one, once the series' metadata has loaded. */
    private var next: Video? = null
    private var advancing = false
    private val ticker = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        metaId = intent.getStringExtra(EXTRA_META).orEmpty()
        videoId = intent.getStringExtra(EXTRA_VIDEO) ?: metaId
        val headers = intent.getStringExtra(EXTRA_HEADERS)
            ?.let { runCatching { Net.json.decodeFromString(headerSerializer, it) }.getOrNull() }
            .orEmpty()

        val view = PlayerView(this).apply {
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            keepScreenOn = true
        }
        val density = resources.displayMetrics.density
        nextButton = Button(this).apply {
            text = "Next episode"
            isAllCaps = false
            visibility = View.GONE
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                cornerRadius = 24 * density
                setColor(Color.WHITE)
            }
            setPadding((20 * density).toInt(), 0, (20 * density).toInt(), 0)
            setOnClickListener { playNext() }
        }
        val root = FrameLayout(this).apply {
            addView(view)
            addView(
                nextButton,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (48 * density).toInt(), Gravity.BOTTOM or Gravity.END)
                    .apply { setMargins(0, 0, (24 * density).toInt(), (96 * density).toInt()) },
            )
        }
        setContentView(root)

        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        applyHeaders(headers)
        val exo = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .build()
        exo.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(Locale.getDefault().language)
            .build()
        exo.addListener(listener)
        view.player = exo
        player = exo

        lifecycleScope.launch { load(exo, url) }
        if (type == "series") lifecycleScope.launch { findNext() }
        ticker.post(tick)
    }

    private fun applyHeaders(headers: Map<String, String>) {
        // Data sources read the factory's properties when they open, so this also covers later items.
        http.setUserAgent(headers["User-Agent"] ?: Net.USER_AGENT)
        http.setDefaultRequestProperties(headers - "User-Agent")
    }

    private suspend fun load(exo: ExoPlayer, url: String) {
        // Addon subtitles join the item before playback starts; a slow addon is skipped.
        val subtitles = withTimeoutOrNull(5_000) {
            AddonClient.subtitles(repository.addons.value, type, videoId)
        }.orEmpty()
        val item = MediaItem.Builder()
            .setUri(url)
            .setSubtitleConfigurations(subtitles.mapIndexed { index, sub -> sub.toConfiguration(index) })
            .build()
        exo.setMediaItem(item, repository.resumeFor(videoId, metaId))
        exo.prepare()
        exo.play()
    }

    private suspend fun findNext() {
        val meta = AddonClient.meta(repository.addons.value, type, metaId) ?: return
        next = nextAfter(meta.videos.orEmpty(), videoId)
    }

    /** Shows "Next episode" for the last 45 seconds (credits), and on the end screen. */
    private val tick = object : Runnable {
        override fun run() {
            val exo = player ?: return
            val duration = exo.duration
            val nearEnd = duration != C.TIME_UNSET && duration > 0 && duration - exo.currentPosition <= 45_000
            nextButton.visibility = if (next != null && (nearEnd || exo.playbackState == Player.STATE_ENDED)) View.VISIBLE else View.GONE
            ticker.postDelayed(this, 1_000)
        }
    }

    /** Finds the best source for the next episode and switches to it in place. */
    private fun playNext() {
        val exo = player ?: return
        val upcoming = next ?: return
        if (advancing) return
        advancing = true
        nextButton.text = "Finding a source…"
        lifecycleScope.launch {
            val debrid = repository.debrid.value
            val ready = Playback.best(Playback.ranked(repository.addons.value, type, upcoming.id, debrid), debrid)
            advancing = false
            nextButton.text = "Next episode"
            if (ready == null) {
                Toast.makeText(this@PlayerActivity, "Couldn't find a source for the next episode.", Toast.LENGTH_LONG).show()
                return@launch
            }
            saveResume(exo)
            videoId = upcoming.id
            next = null
            nextButton.visibility = View.GONE
            applyHeaders(ready.stream.behaviorHints?.proxyHeaders?.request.orEmpty())
            load(exo, ready.url)
            findNext()
        }
    }

    private val listener = object : Player.Listener {
        // Binge: when an episode ends, go straight on to the next one.
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED && next != null) playNext()
        }

        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(this@PlayerActivity, "Playback failed: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
        }

        // With no decoder for the video track Media3 plays audio over black; say why instead.
        override fun onTracksChanged(tracks: Tracks) {
            val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            if (video.isEmpty() || video.any { g -> (0 until g.length).any { g.isTrackSupported(it) } }) return
            val format = video.first().getTrackFormat(0)
            Toast.makeText(
                this@PlayerActivity,
                "This device can't decode ${describeVideo(format)} video. Pick another source.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onPause() {
        super.onPause()
        player?.let {
            it.pause()
            saveResume(it)
        }
    }

    override fun onDestroy() {
        ticker.removeCallbacks(tick)
        player?.let {
            saveResume(it)
            it.release()
        }
        player = null
        super.onDestroy()
    }

    private fun saveResume(exo: ExoPlayer) {
        val duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: return
        repository.saveResume(videoId, exo.currentPosition, duration)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_META = "meta"
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_HEADERS = "headers"
        private val headerSerializer = MapSerializer(String.serializer(), String.serializer())

        fun intent(context: Context, ready: Ready, type: String, metaId: String, videoId: String): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, ready.url)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_META, metaId)
                .putExtra(EXTRA_VIDEO, videoId)
                .putExtra(EXTRA_HEADERS, Net.json.encodeToString(headerSerializer, ready.headers()))

        private fun Ready.headers(): Map<String, String> = stream.behaviorHints?.proxyHeaders?.request.orEmpty()
    }
}

private fun Subtitle.toConfiguration(index: Int): MediaItem.SubtitleConfiguration {
    val path = Uri.parse(url).lastPathSegment.orEmpty().lowercase()
    val mime = when {
        path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        path.endsWith(".ttml") || path.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP // OpenSubtitles and most addons serve SRT.
    }
    return MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
        .setMimeType(mime)
        .setLanguage(lang)
        .setLabel("${Locale(lang).displayLanguage.ifBlank { lang }} · addon")
        .setId(id ?: "addon-$index")
        .build()
}

/** The episode after `videoId` in airing order; specials only follow specials. */
internal fun nextAfter(videos: List<Video>, videoId: String): Video? {
    val current = videos.firstOrNull { it.id == videoId } ?: return null
    val ordered = videos.filter { (it.season == 0) == (current.season == 0) }
        .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
    return ordered.getOrNull(ordered.indexOf(current) + 1)
}

private fun describeVideo(format: Format): String {
    val codecs = format.codecs.orEmpty()
    return when (format.sampleMimeType) {
        // avc1.6E.... is the High 10 profile: common in anime, decoded in hardware almost nowhere.
        MimeTypes.VIDEO_H264 -> if (codecs.startsWith("avc1.6E", true)) "10-bit H.264 (Hi10P)" else "H.264"
        MimeTypes.VIDEO_H265 -> if (codecs.contains(".2.")) "10-bit HEVC" else "HEVC"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        else -> format.sampleMimeType?.substringAfter('/')?.uppercase() ?: "this"
    }
}
