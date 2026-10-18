package app.lart.mobile.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import app.lart.mobile.LartApp
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Net
import app.lart.mobile.data.Playback
import app.lart.mobile.data.PlayerPrefs
import app.lart.mobile.data.RankedStream
import app.lart.mobile.data.Ready
import app.lart.mobile.data.SkipSegment
import app.lart.mobile.data.SkipSegments
import app.lart.mobile.data.Video
import app.lart.mobile.ui.LartTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.Locale

/**
 * Lart's player: Media3 underneath, Harbor's phone player on top. Gestures for
 * seeking, brightness and volume; menus for episodes, sources, subtitles (with
 * style and sync), audio, speed and a sleep timer; skip intro, up next,
 * "Still watching?" and picture-in-picture.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity(), PlayerActions {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var http: DefaultHttpDataSource.Factory
    private val state = PlayerState()
    private val repository get() = (application as LartApp).repository
    private val prefs: PlayerPrefs get() = repository.playerPrefs.value

    private var type = ""
    private var metaId = ""
    private var meta: Meta? = null
    private var subtitleFile: SubtitleFile? = null
    private var subtitleJob: Job? = null
    private var segments: List<SkipSegment> = emptyList()
    private var segmentsFor = ""
    private val autoSkipped = mutableSetOf<SkipSegment>()
    private var autoAdvanced = 0
    private var autoSubDone = false
    private var volumeLevel = -1f
    private val ticker = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        view = PlayerView(this).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setBackgroundColor(Color.BLACK)
        }
        val overlay = ComposeView(this).apply {
            setContent {
                val prefs by repository.playerPrefs.collectAsState()
                LartTheme { PlayerUi(state, prefs, this@PlayerActivity) }
            }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            },
        )

        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val exo = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        exo.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(Locale.getDefault().language)
            .apply { prefs.subLanguage.takeIf { it.isNotEmpty() }?.let { setPreferredTextLanguage(it) } }
            .build()
        exo.addListener(listener)
        exo.setPlaybackSpeed(prefs.speed)
        state.speed = prefs.speed
        view.player = exo
        player = exo

        lifecycleScope.launch {
            // Subtitles in the video move up out of the way of the controls, like addon ones.
            combine(repository.playerPrefs, snapshotFlow { state.controlsVisible && !state.inPip }) { p, lifted -> p to lifted }
                .collect { (p, lifted) -> applySubtitleStyle(p, lifted) }
        }
        start(intent)
        ticker.post(tick)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Picked something else while this player was in picture-in-picture.
        player?.let { saveResume(it) }
        setIntent(intent)
        start(intent)
    }

    private fun start(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        metaId = intent.getStringExtra(EXTRA_META).orEmpty()
        val videoId = intent.getStringExtra(EXTRA_VIDEO) ?: metaId
        val headers = intent.getStringExtra(EXTRA_HEADERS)
            ?.let { runCatching { Net.json.decodeFromString(headerSerializer, it) }.getOrNull() }
            .orEmpty()
        state.title = intent.getStringExtra(EXTRA_TITLE).orEmpty().substringBefore(" · ")
        autoAdvanced = 0
        applyHeaders(headers)
        begin(videoId, url)
        lifecycleScope.launch {
            meta = AddonClient.meta(repository.metaAddons(), type, metaId)
            describe()
        }
    }

    /** Starts `videoId` from `url`, resuming where this device or Stremio left off. */
    private fun begin(videoId: String, url: String, startAt: Long? = null) {
        val exo = player ?: return
        state.videoId = videoId
        state.currentUrl = url
        state.sources = null
        state.error = null
        state.ended = false
        state.skip = null
        segments = emptyList()
        segmentsFor = ""
        autoSkipped.clear()
        autoSubDone = false
        clearAddonSubtitle()
        if (state.subChoice is SubChoice.Addon) state.subChoice = SubChoice.Off

        val resume = startAt ?: repository.resumeFor(videoId, metaId)
        state.resumedFromMs = if (startAt == null && resume > 30_000) resume else 0
        exo.setMediaItem(MediaItem.fromUri(url), resume)
        exo.prepare()
        exo.play()
        describe()
        loadAddonSubtitles(videoId)
    }

    /** Title, episode line, the season list and what comes next, from the metadata. */
    private fun describe() {
        val meta = meta ?: return
        state.title = meta.name.ifBlank { state.title }
        val videos = meta.videos.orEmpty()
        state.isSeries = type != "movie" && videos.isNotEmpty()
        state.episodes = videos
        val current = videos.firstOrNull { it.id == state.videoId }
        state.episodeLine = current?.let { "${episodeCode(it)}  ·  ${it.label}" }
        state.next = if (state.isSeries) nextAfter(videos, state.videoId) else null
    }

    private fun applyHeaders(headers: Map<String, String>) {
        // Data sources read the factory's properties when they open, so this also covers later items.
        http.setUserAgent(headers["User-Agent"] ?: Net.USER_AGENT)
        http.setDefaultRequestProperties(headers - "User-Agent")
    }

    // ---- Progress, skip segments, sleep timer ----

    private val tick = object : Runnable {
        override fun run() {
            player?.let { exo ->
                val duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0
                val position = exo.currentPosition
                state.positionMs = position
                state.durationMs = duration
                state.bufferedMs = exo.bufferedPosition
                subtitleFile?.let { state.addonCue = it.textAt(position - state.subDelayMs) }

                if (duration > 0 && segmentsFor != state.videoId) {
                    segmentsFor = state.videoId
                    val forVideo = state.videoId
                    lifecycleScope.launch {
                        val found = SkipSegments.find(forVideo, duration)
                        if (state.videoId == forVideo) segments = found
                    }
                }
                val active = segments.firstOrNull { position >= it.startMs && position < it.endMs - 1_000 }
                state.skip = active
                if (active != null && prefs.autoSkipIntro && active.kind != SkipSegment.Kind.Outro && active !in autoSkipped) {
                    autoSkipped += active
                    exo.seekTo(active.endMs)
                    state.hint = "Skipped ${active.kind.name.lowercase()}"
                    state.hintIcon = 0
                }

                val sleepAt = state.sleepAt
                if (sleepAt != null && sleepAt > 0 && System.currentTimeMillis() >= sleepAt) {
                    exo.pause()
                    state.sleepAt = null
                    state.controlsVisible = true
                    state.hint = "Sleep timer: paused"
                }
            }
            ticker.postDelayed(this, 250)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            state.playing = isPlaying
            updatePipParams()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            state.buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
            state.ended = playbackState == Player.STATE_ENDED
            if (playbackState != Player.STATE_ENDED) return
            state.controlsVisible = true
            if (state.sleepAt == -1L) {
                state.sleepAt = null
                return
            }
            if (state.next == null) return
            autoAdvanced++
            val limit = prefs.stillWatchingAfter
            if (limit > 0 && autoAdvanced >= limit) state.stillWatching = true else playNext()
        }

        override fun onPlayerError(error: PlaybackException) {
            state.error = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The server refused the video (${error.cause?.message?.take(60) ?: "HTTP error"})."
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "The video server didn't answer. Check your connection."
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "This device can't decode this video."
                else -> "Playback failed (${error.errorCodeName})."
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) = updatePipParams()

        override fun onTracksChanged(tracks: Tracks) {
            readTracks(tracks)
            // With no decoder for the video track Media3 plays audio over black; say why instead.
            val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            if (video.isNotEmpty() && video.none { g -> (0 until g.length).any { g.isTrackSupported(it) } }) {
                state.error = "This device can't decode ${describeVideo(video.first().getTrackFormat(0))} video."
            }
        }
    }

    // ---- Tracks ----

    private fun readTracks(tracks: Tracks) {
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        tracks.groups.forEachIndexed { g, group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i, true)) continue
                val format = group.getTrackFormat(i)
                val key = "$g:$i"
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> audio += TrackOption(key, trackName(format, audio.size + 1), audioDetail(format), group.isTrackSelected(i))
                    C.TRACK_TYPE_TEXT -> text += TrackOption(key, trackName(format, text.size + 1), textDetail(format), group.isTrackSelected(i))
                }
            }
        }
        state.audioTracks = if (audio.size > 1) audio else emptyList()
        state.embeddedSubs = text
        if (state.subChoice !is SubChoice.Addon) {
            state.subChoice = text.firstOrNull { it.selected }?.let { SubChoice.Embedded(it.key) } ?: SubChoice.Off
        }
        maybeAutoSelectAddon()
    }

    private fun groupFor(key: String): Pair<Tracks.Group, Int>? {
        val (g, i) = key.split(':').map { it.toInt() }
        val group = player?.currentTracks?.groups?.getOrNull(g) ?: return null
        return group to i
    }

    override fun selectAudio(key: String) {
        val exo = player ?: return
        val (group, index) = groupFor(key) ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .build()
        state.panel = null
    }

    override fun selectSubtitle(choice: SubChoice) {
        val exo = player ?: return
        val builder = exo.trackSelectionParameters.buildUpon()
        when (choice) {
            SubChoice.Off -> {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                clearAddonSubtitle()
                repository.updatePlayerPrefs { it.copy(subLanguage = "") }
            }
            is SubChoice.Embedded -> {
                val (group, index) = groupFor(choice.key) ?: return
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                clearAddonSubtitle()
                group.getTrackFormat(index).language?.let { lang -> repository.updatePlayerPrefs { it.copy(subLanguage = lang) } }
            }
            is SubChoice.Addon -> {
                // Lart draws addon subtitles itself, so Media3's own text output is off.
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                loadAddonSubtitle(choice)
                repository.updatePlayerPrefs { it.copy(subLanguage = choice.subtitle.lang) }
            }
        }
        exo.trackSelectionParameters = builder.build()
        state.subChoice = choice
        state.panel = null
    }

    private fun clearAddonSubtitle() {
        subtitleJob?.cancel()
        subtitleFile = null
        state.addonCue = null
        state.subLoading = false
        state.subDelayMs = 0
    }

    private fun loadAddonSubtitle(choice: SubChoice.Addon) {
        clearAddonSubtitle()
        state.subLoading = true
        subtitleJob = lifecycleScope.launch {
            val file = runCatching { SubtitleFile.load(choice.subtitle.url) }.getOrNull()
            state.subLoading = false
            if (file == null || file.isEmpty) {
                state.hint = "That subtitle didn't load"
                state.hintIcon = 0
                state.subChoice = SubChoice.Off
            } else subtitleFile = file
        }
    }

    private fun loadAddonSubtitles(videoId: String) {
        state.addonSubs = null
        lifecycleScope.launch {
            val subs = withTimeoutOrNull(15_000) { AddonClient.subtitles(repository.addons.value, type, videoId) }.orEmpty()
            if (state.videoId != videoId) return@launch
            state.addonSubs = subs.distinctBy { it.url }
            maybeAutoSelectAddon()
        }
    }

    /** Turns on a subtitle in the remembered language when the video has none in it. */
    private fun maybeAutoSelectAddon() {
        if (autoSubDone) return
        val wanted = prefs.subLanguage.takeIf { it.isNotEmpty() } ?: return
        if (state.embeddedSubs.any { it.selected }) {
            autoSubDone = true
            return
        }
        val embedded = player?.currentTracks?.groups?.withIndex()?.firstNotNullOfOrNull { (g, group) ->
            if (group.type != C.TRACK_TYPE_TEXT) null
            else (0 until group.length).firstOrNull { sameLanguage(group.getTrackFormat(it).language, wanted) }?.let { "$g:$it" }
        }
        if (embedded != null) {
            autoSubDone = true
            selectSubtitle(SubChoice.Embedded(embedded))
            return
        }
        val addon = state.addonSubs ?: return
        autoSubDone = true
        addon.firstOrNull { sameLanguage(it.lang, wanted) }?.let { selectSubtitle(SubChoice.Addon(it)) }
    }

    private fun applySubtitleStyle(prefs: PlayerPrefs, lifted: Boolean) {
        val subtitleView: SubtitleView = view.subtitleView ?: return
        val (background, edge) = when (prefs.subBackground) {
            "box" -> 0xB3000000.toInt() to CaptionStyleCompat.EDGE_TYPE_NONE
            "shadow" -> Color.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            else -> Color.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_OUTLINE
        }
        subtitleView.setStyle(CaptionStyleCompat(prefs.subColor.toInt(), background, Color.TRANSPARENT, edge, Color.BLACK, null))
        subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * prefs.subScale)
        subtitleView.setBottomPaddingFraction(prefs.subBottom + if (lifted) CONTROLS_LIFT else 0f)
        subtitleView.setApplyEmbeddedFontSizes(false)
    }

    // ---- PlayerActions ----

    override fun back() = finish()

    override fun togglePlay() {
        val exo = player ?: return
        when {
            exo.playbackState == Player.STATE_ENDED -> exo.seekTo(0).also { exo.play() }
            exo.isPlaying -> exo.pause()
            else -> exo.play()
        }
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0))
    }

    override fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0, exo.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE))
    }

    override fun setSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        state.speed = speed
        state.panel = null
        repository.updatePlayerPrefs { it.copy(speed = speed) }
    }

    override fun setFit(fit: Fit) {
        state.fit = fit
        view.resizeMode = fit.mode
    }

    override fun setSubDelay(delayMs: Long) {
        state.subDelayMs = delayMs
    }

    override fun updatePrefs(change: (PlayerPrefs) -> PlayerPrefs) = repository.updatePlayerPrefs(change)

    override fun skipSegment() {
        val segment = state.skip ?: return
        if (segment.kind == SkipSegment.Kind.Outro && state.next != null) {
            playNext()
        } else {
            player?.seekTo(segment.endMs)
        }
    }

    override fun playNext() {
        state.next?.let { playEpisode(it) }
    }

    override fun playEpisode(video: Video) {
        if (state.nextLoading) return
        state.nextLoading = true
        state.panel = null
        lifecycleScope.launch {
            val debrid = repository.debrid.value
            val ready = Playback.best(Playback.ranked(repository.addons.value, type, video.id, debrid), debrid)
            state.nextLoading = false
            if (ready == null) {
                state.hint = "No source found for ${episodeCode(video)}"
                state.hintIcon = 0
                return@launch
            }
            player?.let { saveResume(it) }
            switchTo(video.id, ready, startAt = null)
        }
    }

    private fun switchTo(videoId: String, ready: Ready, startAt: Long?) {
        applyHeaders(ready.stream.behaviorHints?.proxyHeaders?.request.orEmpty())
        begin(videoId, ready.url, startAt)
    }

    override fun loadSources() {
        if (state.sources != null) return
        val videoId = state.videoId
        lifecycleScope.launch {
            val ranked = Playback.ranked(repository.addons.value, type, videoId, repository.debrid.value)
            if (state.videoId == videoId) {
                state.sources = ranked.filter { it.stream.playable(repository.debrid.value != null) }
            }
        }
    }

    override fun playSource(source: RankedStream) {
        val exo = player ?: return
        val position = exo.currentPosition
        state.panel = null
        state.hint = "Switching source…"
        state.hintIcon = 0
        lifecycleScope.launch {
            runCatching { Playback.resolve(source.stream, repository.debrid.value) }
                .onSuccess { ready ->
                    val sources = state.sources
                    switchTo(state.videoId, ready, startAt = position)
                    state.sources = sources
                }
                .onFailure {
                    state.hint = it.message ?: "That source didn't open"
                }
        }
    }

    override fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        state.panel = null
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun setSleep(minutes: Int?) {
        state.sleepAt = when (minutes) {
            null -> null
            0 -> -1L
            else -> System.currentTimeMillis() + minutes * 60_000L
        }
        state.panel = null
        state.hint = when (minutes) {
            null -> "Sleep timer off"
            0 -> "Pausing at the end of this episode"
            else -> "Pausing in $minutes minutes"
        }
        state.hintIcon = 0
    }

    override fun keepWatching() {
        state.stillWatching = false
        autoAdvanced = 0
        if (state.ended) playNext()
    }

    override fun startOver() {
        player?.seekTo(0)
    }

    override fun changeBrightness(delta: Float): Float {
        val attrs = window.attributes
        val current = if (attrs.screenBrightness >= 0) attrs.screenBrightness
        else runCatching { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
        val next = (current + delta).coerceIn(0.01f, 1f)
        attrs.screenBrightness = next
        window.attributes = attrs
        return next
    }

    override fun changeVolume(delta: Float): Float {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        if (volumeLevel < 0) volumeLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max
        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(volumeLevel * max), 0)
        return volumeLevel
    }

    override fun retry() {
        val exo = player ?: return
        val position = exo.currentPosition
        state.error = null
        exo.setMediaItem(MediaItem.fromUri(state.currentUrl), position)
        exo.prepare()
        exo.play()
    }

    // ---- Picture-in-picture and lifecycle ----

    private fun pipParams(): PictureInPictureParams {
        val size = player?.videoSize
        val ratio = if (size != null && size.width > 0 && size.height > 0) {
            Rational(size.width, size.height).let {
                // Android rejects ratios outside 1:2.39 .. 2.39:1.
                when {
                    it.toFloat() > 2.39f -> Rational(239, 100)
                    it.toFloat() < 0.42f -> Rational(100, 239)
                    else -> it
                }
            }
        } else Rational(16, 9)
        val builder = PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(state.playing)
        return builder.build()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching { setPictureInPictureParams(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters on its own (auto-enter); older versions need asking.
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S && state.playing) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        state.inPip = isInPictureInPictureMode
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // The picture-in-picture window was closed.
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        player?.let {
            saveResume(it)
            if (!isInPictureInPictureMode) it.pause()
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
        if (state.videoId.isNotEmpty()) {
            repository.saveResume(state.videoId, exo.currentPosition, duration, meta, meta?.videos?.firstOrNull { it.id == state.videoId })
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_META = "meta"
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_HEADERS = "headers"
        private val headerSerializer = MapSerializer(String.serializer(), String.serializer())

        fun intent(context: Context, ready: Ready, type: String, metaId: String, videoId: String, title: String): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, ready.url)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_META, metaId)
                .putExtra(EXTRA_VIDEO, videoId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_HEADERS, Net.json.encodeToString(headerSerializer, ready.stream.behaviorHints?.proxyHeaders?.request.orEmpty()))
    }
}

/** The episode after `videoId` in airing order; specials only follow specials. */
internal fun nextAfter(videos: List<Video>, videoId: String): Video? {
    val current = videos.firstOrNull { it.id == videoId } ?: return null
    val ordered = videos.filter { (it.season == 0) == (current.season == 0) }
        .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
    return ordered.getOrNull(ordered.indexOf(current) + 1)
}

private fun sameLanguage(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    fun iso3(code: String) = runCatching { Locale.forLanguageTag(code).isO3Language }.getOrNull()?.ifEmpty { null }
        ?: runCatching { Locale(code).isO3Language }.getOrNull() ?: code.lowercase()
    // Bibliographic codes some addons use ("fre", "ger") map to the terminology ones.
    val alias = mapOf("fre" to "fra", "ger" to "deu", "dut" to "nld", "chi" to "zho", "cze" to "ces", "gre" to "ell", "per" to "fas", "rum" to "ron", "slo" to "slk", "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "geo" to "kat", "ice" to "isl", "mac" to "mkd", "may" to "msa", "wel" to "cym")
    val x = iso3(a).let { alias[it] ?: it }
    val y = iso3(b).let { alias[it] ?: it }
    return x == y
}

private fun trackName(format: Format, number: Int): String {
    val language = format.language?.takeIf { it != "und" }?.let { languageName(it) }
    val label = format.label?.takeIf { it.isNotBlank() }
    return when {
        language != null && label != null && !label.equals(language, true) -> "$language · $label"
        language != null -> language
        label != null -> label
        else -> "Track $number"
    }
}

private fun audioDetail(format: Format): String? {
    val codec = when (format.sampleMimeType) {
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        else -> format.sampleMimeType?.substringAfter('/')?.uppercase()
    }
    val channels = when (format.channelCount) {
        Format.NO_VALUE -> null
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${format.channelCount} ch"
    }
    return listOfNotNull(codec, channels).joinToString(" · ").ifEmpty { null }
}

private fun textDetail(format: Format): String? = buildList {
    if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) add("Forced")
    when (format.sampleMimeType) {
        MimeTypes.TEXT_SSA -> add("Styled")
        MimeTypes.APPLICATION_PGS, MimeTypes.APPLICATION_VOBSUB, MimeTypes.APPLICATION_DVBSUBS -> add("Image")
    }
    if (format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0) add("SDH")
}.joinToString(" · ").ifEmpty { null }

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
