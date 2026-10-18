package app.lart.mobile.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
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
import app.lart.mobile.data.Stream
import app.lart.mobile.data.Subtitle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val metaId = intent.getStringExtra(EXTRA_META).orEmpty()
        videoId = intent.getStringExtra(EXTRA_VIDEO) ?: metaId
        val headers = intent.getStringExtra(EXTRA_HEADERS)
            ?.let { runCatching { Net.json.decodeFromString(headerSerializer, it) }.getOrNull() }
            .orEmpty()

        val repository = (application as LartApp).repository
        val view = PlayerView(this).apply {
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            keepScreenOn = true
        }
        setContentView(view)

        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(headers["User-Agent"] ?: Net.USER_AGENT)
            .setDefaultRequestProperties(headers - "User-Agent")
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

        lifecycleScope.launch {
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
    }

    private val listener = object : Player.Listener {
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
        player?.let {
            saveResume(it)
            it.release()
        }
        player = null
        super.onDestroy()
    }

    private fun saveResume(exo: ExoPlayer) {
        val duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: return
        (application as LartApp).repository.saveResume(videoId, exo.currentPosition, duration)
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

        fun intent(context: Context, stream: Stream, type: String, metaId: String, videoId: String, title: String): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, stream.url)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_META, metaId)
                .putExtra(EXTRA_VIDEO, videoId)
                .putExtra("title", title)
                .putExtra(
                    EXTRA_HEADERS,
                    Net.json.encodeToString(headerSerializer, stream.behaviorHints?.proxyHeaders?.request.orEmpty()),
                )
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
