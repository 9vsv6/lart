package app.lart.mobile.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.lart.mobile.LartApp
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Net
import app.lart.mobile.data.Playback
import app.lart.mobile.data.PlayerPrefs
import app.lart.mobile.data.RankedStream
import app.lart.mobile.data.Ready
import app.lart.mobile.data.Scrobble
import app.lart.mobile.data.SkipSegment
import app.lart.mobile.data.SkipSegments
import app.lart.mobile.data.Video
import app.lart.mobile.ui.LartTheme
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.Locale

/**
 * Lart's player: mpv underneath (Media3 when mpv can't start), Harbor's phone player
 * on top. Gestures for seeking, brightness and volume; menus for episodes, sources,
 * subtitles (with style and sync), audio (with delay), speed, Anime4K and a sleep
 * timer; skip intro, up next, "Still watching?", casting and picture-in-picture.
 */
class PlayerActivity : FragmentActivity(), PlayerActions, EngineListener {
    private lateinit var engine: Engine
    private val state = PlayerState()
    private val repository get() = (application as LartApp).repository
    private val prefs: PlayerPrefs get() = repository.playerPrefs.value

    private var type = ""
    private var metaId = ""
    private var meta: Meta? = null
    private var headers: Map<String, String> = emptyMap()
    private var subtitleFile: SubtitleFile? = null
    private var subtitleJob: Job? = null
    private var segments: List<SkipSegment> = emptyList()
    private var segmentsFor = ""
    private val autoSkipped = mutableSetOf<SkipSegment>()
    private var autoAdvanced = 0
    private var autoSubDone = false
    private var volumeLevel = -1f
    /** Trakt's paused position for this video, applied once its length is known. */
    private var pendingFraction: Float? = null
    private var animeMarked = false
    private var scrobbling = false
    private var audioChecked = false
    private var castContext: CastContext? = null
    private var remotePositionMs = 0L
    private var anime4kJob: Job? = null
    private val ticker = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        // mpv is the main engine; Media3 takes over only where libmpv can't start.
        engine = MpvEngine.create(this, prefs.subLanguage) ?: ExoEngine(this, prefs.subLanguage)
        engine.listener = this
        state.engineName = engine.name
        state.anime4kAvailable = engine.supportsShaders

        val overlay = ComposeView(this).apply {
            setContent {
                val prefs by repository.playerPrefs.collectAsState()
                LartTheme { PlayerUi(state, prefs, this@PlayerActivity, castButton.takeIf { castContext != null }) }
            }
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                addView(engine.view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            },
        )

        engine.setSpeed(prefs.speed)
        state.speed = prefs.speed
        // mpv draws subtitles itself: keep its style in step with the settings and the controls.
        lifecycleScope.launch {
            combine(repository.playerPrefs, snapshotFlow { state.controlsVisible && !state.inPip }) { p, lifted -> p to lifted }
                .collect { (p, lifted) -> engine.applySubtitleStyle(p, lifted) }
        }
        start(intent)
        ticker.post(tick)
        setUpCast()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Picked something else while this player was in picture-in-picture.
        saveResume()
        setIntent(intent)
        start(intent)
    }

    private fun start(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        metaId = intent.getStringExtra(EXTRA_META).orEmpty()
        val videoId = intent.getStringExtra(EXTRA_VIDEO) ?: metaId
        headers = intent.getStringExtra(EXTRA_HEADERS)
            ?.let { runCatching { Net.json.decodeFromString(headerSerializer, it) }.getOrNull() }
            .orEmpty()
        state.title = intent.getStringExtra(EXTRA_TITLE).orEmpty().substringBefore(" · ")
        autoAdvanced = 0
        begin(videoId, url)
        lifecycleScope.launch {
            meta = AddonClient.meta(repository.metaAddons(), type, metaId)
            describe()
        }
    }

    /** Starts `videoId` from `url`, resuming where this device or Stremio left off. */
    private fun begin(videoId: String, url: String, startAt: Long? = null) {
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
        setSubDelay(0)
        if (state.subChoice is SubChoice.Addon) state.subChoice = SubChoice.Off

        stopScrobble()
        animeMarked = false
        audioChecked = false
        val resume = startAt ?: repository.resumeFor(videoId, metaId)
        state.resumedFromMs = if (startAt == null && resume > 30_000) resume else 0
        pendingFraction = if (startAt == null && resume == 0L) repository.traktFraction(videoId)?.takeIf { it in 0.02f..0.95f } else null
        state.buffering = true
        engine.load(url, headers, resume)
        engine.setAudioDelay(state.audioDelayMs)
        describe()
        loadAddonSubtitles(videoId)
        applyAnime4K()
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

    // ---- Progress, skip segments, sleep timer ----

    private val tick = object : Runnable {
        override fun run() {
            val duration = engine.durationMs
            val position = engine.positionMs
            state.positionMs = position
            state.durationMs = duration
            state.bufferedMs = engine.bufferedMs
            pendingFraction?.let { fraction ->
                if (duration > 0) {
                    pendingFraction = null
                    val target = (duration * fraction).toLong()
                    engine.seekTo(target)
                    state.resumedFromMs = target
                }
            }
            // AniList: an anime episode counts as watched at 80%.
            if (!animeMarked && duration > 0 && position >= duration * 0.8 && state.videoId.startsWith("kitsu:")) {
                animeMarked = true
                val parts = state.videoId.split(':')
                parts.lastOrNull()?.toIntOrNull()?.takeIf { parts.size >= 3 }?.let { repository.markAnimeWatched(parts[1], it) }
            }
            // With Media3, Lart draws the subtitles; mpv draws its own.
            if (!engine.drawsSubtitles) {
                val subtitleTime = position - state.subDelayMs
                val cues = when (state.subChoice) {
                    is SubChoice.Addon -> subtitleFile?.textAt(subtitleTime)?.let { listOf(UiCue(text = it)) }.orEmpty()
                    is SubChoice.Embedded -> engine.cuesAt(subtitleTime)
                    SubChoice.Off -> emptyList()
                }
                if (cues != state.cues) state.cues = cues
            }

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
                engine.seekTo(active.endMs)
                state.hint = "Skipped ${active.kind.name.lowercase()}"
                state.hintIcon = 0
            }

            val sleepAt = state.sleepAt
            if (sleepAt != null && sleepAt > 0 && System.currentTimeMillis() >= sleepAt) {
                engine.pause()
                state.sleepAt = null
                state.controlsVisible = true
                state.hint = "Sleep timer: paused"
            }
            ticker.postDelayed(this, 250)
        }
    }

    // ---- EngineListener ----

    override fun onPlayingChanged(playing: Boolean) {
        state.playing = playing
        // Playing means whatever error was showing no longer applies.
        if (playing) state.error = null
        if (playing) scrobble("start") else if (!engine.ended) scrobble("pause")
        updatePipParams()
    }

    override fun onBufferingChanged(buffering: Boolean) {
        state.buffering = buffering
    }

    override fun onEnded() {
        state.ended = true
        stopScrobble()
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

    override fun onError(message: String) {
        state.error = message
        state.buffering = false
    }

    override fun onVideoSizeChanged() = updatePipParams()

    override fun onTracksChanged() {
        val audio = engine.audioTracks()
        val text = engine.textTracks()
        state.audioTracks = if (audio.size > 1) audio.map { TrackOption(it.key, it.label, it.detail, it.selected) } else emptyList()
        // Addon subtitles mpv loaded show under "From your addons", not "In the video".
        state.embeddedSubs = text.filterNot { it.external }.map { TrackOption(it.key, it.label, it.detail, it.selected) }
        avoidCommentary(audio)
        if (state.subChoice !is SubChoice.Addon) {
            state.subChoice = text.firstOrNull { it.selected && !it.external }?.let { SubChoice.Embedded(it.key) } ?: SubChoice.Off
        }
        maybeAutoSelectAddon(text)
    }

    // ---- Tracks ----

    /**
     * An engine can pick a commentary track when it is the only one tagged with the
     * device language. Once per video, swap it for the main track if there is one.
     */
    private fun avoidCommentary(audio: List<EngineTrack>) {
        if (audioChecked || audio.isEmpty()) return
        audioChecked = true
        val selected = audio.firstOrNull { it.selected } ?: return
        if (!selected.commentary) return
        audio.firstOrNull { !it.commentary }?.let { selectAudio(it.key) }
    }

    override fun selectAudio(key: String) {
        engine.selectAudio(key)
        state.panel = null
    }

    override fun selectSubtitle(choice: SubChoice) {
        when (choice) {
            SubChoice.Off -> {
                engine.selectText(null)
                clearAddonSubtitle()
                repository.updatePlayerPrefs { it.copy(subLanguage = "") }
            }
            is SubChoice.Embedded -> {
                clearAddonSubtitle()
                engine.selectText(choice.key)
                engine.textTracks().firstOrNull { it.key == choice.key }?.language
                    ?.let { lang -> repository.updatePlayerPrefs { it.copy(subLanguage = lang) } }
            }
            is SubChoice.Addon -> {
                if (!engine.addExternalSubtitle(choice.subtitle.url, choice.subtitle.lang, languageName(choice.subtitle.lang))) {
                    // Media3: Lart loads and draws addon subtitles itself.
                    engine.selectText(null)
                    loadAddonSubtitle(choice)
                }
                repository.updatePlayerPrefs { it.copy(subLanguage = choice.subtitle.lang) }
            }
        }
        state.subChoice = choice
        state.panel = null
    }

    private fun clearAddonSubtitle() {
        subtitleJob?.cancel()
        subtitleFile = null
        state.cues = emptyList()
        state.subLoading = false
        if (engine.drawsSubtitles) engine.removeExternalSubtitles()
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
            maybeAutoSelectAddon(engine.textTracks())
        }
    }

    /** Turns on a subtitle in the remembered language when the video has none in it. */
    private fun maybeAutoSelectAddon(text: List<EngineTrack>) {
        if (autoSubDone) return
        val wanted = prefs.subLanguage.takeIf { it.isNotEmpty() } ?: return
        if (text.any { it.selected }) {
            autoSubDone = true
            return
        }
        text.firstOrNull { !it.external && sameLanguage(it.language, wanted) }?.let {
            autoSubDone = true
            selectSubtitle(SubChoice.Embedded(it.key))
            return
        }
        val addon = state.addonSubs ?: return
        autoSubDone = true
        addon.firstOrNull { sameLanguage(it.lang, wanted) }?.let { selectSubtitle(SubChoice.Addon(it)) }
    }

    // ---- Anime4K (mpv) ----

    private fun applyAnime4K() {
        if (!engine.supportsShaders) return
        val mode = Anime4K.Mode.entries.firstOrNull { it.name == prefs.anime4kMode }
        anime4kJob?.cancel()
        if (mode == null) {
            engine.setShaders(emptyList())
            state.anime4kLoading = false
            return
        }
        state.anime4kLoading = true
        anime4kJob = lifecycleScope.launch {
            runCatching { Anime4K.shaders(this@PlayerActivity, mode, prefs.anime4kHq) }
                .onSuccess { engine.setShaders(it) }
                .onFailure {
                    state.hint = "Anime4K didn't download: ${it.message?.take(60)}"
                    state.hintIcon = 0
                }
            state.anime4kLoading = false
        }
    }

    override fun setAnime4K(mode: String?, hq: Boolean) {
        repository.updatePlayerPrefs { it.copy(anime4kMode = mode.orEmpty(), anime4kHq = hq) }
        // updatePlayerPrefs sets the value at once, so the new choice is read back here.
        applyAnime4K()
        state.hint = if (mode == null) "Anime4K off" else "Anime4K ${Anime4K.Mode.valueOf(mode).label}"
        state.hintIcon = 0
    }

    // ---- PlayerActions ----

    override fun back() = finish()

    override fun togglePlay() {
        when {
            engine.ended -> {
                engine.seekTo(0)
                engine.play()
            }
            engine.isPlaying -> engine.pause()
            else -> engine.play()
        }
    }

    override fun seekTo(positionMs: Long) = engine.seekTo(positionMs.coerceAtLeast(0))

    override fun seekBy(deltaMs: Long) {
        val duration = engine.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        engine.seekTo((engine.positionMs + deltaMs).coerceIn(0, duration))
    }

    override fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
        state.speed = speed
        state.panel = null
        repository.updatePlayerPrefs { it.copy(speed = speed) }
    }

    override fun setFit(fit: Fit) {
        state.fit = fit
        engine.setFit(fit)
    }

    override fun setSubDelay(delayMs: Long) {
        val clamped = delayMs.coerceIn(-30_000, 30_000)
        state.subDelayMs = clamped
        engine.setSubDelay(clamped)
    }

    override fun setAudioDelay(delayMs: Long) {
        val clamped = delayMs.coerceIn(-5_000, 5_000)
        state.audioDelayMs = clamped
        engine.setAudioDelay(clamped)
    }

    override fun updatePrefs(change: (PlayerPrefs) -> PlayerPrefs) = repository.updatePlayerPrefs(change)

    override fun skipSegment() {
        val segment = state.skip ?: return
        if (segment.kind == SkipSegment.Kind.Outro && state.next != null) playNext() else engine.seekTo(segment.endMs)
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
            saveResume()
            switchTo(video.id, ready, startAt = null)
        }
    }

    private fun switchTo(videoId: String, ready: Ready, startAt: Long?) {
        headers = ready.stream.behaviorHints?.proxyHeaders?.request.orEmpty()
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
        val position = engine.positionMs
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
                .onFailure { state.hint = it.message ?: "That source didn't open" }
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

    override fun startOver() = engine.seekTo(0)

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
        val position = engine.positionMs
        state.error = null
        state.buffering = true
        engine.load(state.currentUrl, headers, position)
    }

    // ---- Picture-in-picture and lifecycle ----

    private fun pipParams(): PictureInPictureParams {
        val size = engine.videoSize
        val ratio = if (size != null) {
            Rational(size.first, size.second).let {
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
        saveResume()
        if (!isInPictureInPictureMode && state.castingTo == null) engine.pause()
    }

    // ---- Chromecast ----

    /** The Cast button, themed so the route picker dialog can open from this activity. */
    private val castButton: @androidx.compose.runtime.Composable () -> Unit = {
        androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
            androidx.mediarouter.app.MediaRouteButton(android.view.ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)).also {
                CastButtonFactory.setUpMediaRouteButton(applicationContext, it)
            }
        })
    }

    private val castListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = startCasting(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = startCasting(session)
        override fun onSessionEnded(session: CastSession, error: Int) = endCasting()
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            state.hint = "Couldn't connect to the Chromecast"
            state.hintIcon = 0
        }
        override fun onSessionEnding(session: CastSession) {
            session.remoteMediaClient?.approximateStreamPosition?.takeIf { it > 0 }?.let { remotePositionMs = it }
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
            state.castPlaying = client.isPlaying || client.isBuffering
        }
    }

    private fun setUpCast() {
        castContext = runCatching { CastContext.getSharedInstance(this) }.getOrNull() ?: return
        val sessions = castContext!!.sessionManager
        sessions.addSessionManagerListener(castListener, CastSession::class.java)
        // Opened while already connected: send this video straight to the TV.
        sessions.currentCastSession?.takeIf { it.isConnected }?.let { session -> ticker.post { startCasting(session) } }
    }

    private fun startCasting(session: CastSession) {
        val client = session.remoteMediaClient ?: return
        remotePositionMs = engine.positionMs
        engine.pause()
        // The Chromecast fetches the stream itself; addons that need special headers won't play there.
        CastLoader.load(client, CastItem(state.currentUrl, state.title, state.episodeLine, meta?.poster, engine.positionMs))
        client.registerCallback(remoteCallback)
        client.addProgressListener({ progress, _ -> if (progress > 0) remotePositionMs = progress }, 1_000)
        state.castingTo = session.castDevice?.friendlyName ?: "your TV"
        state.castPlaying = true
        state.panel = null
    }

    private fun endCasting() {
        if (state.castingTo == null) return
        state.castingTo = null
        // Carry on here from where the TV got to.
        engine.seekTo(remotePositionMs)
        engine.play()
    }

    override fun castTogglePlay() {
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.togglePlayback()
    }

    override fun castSeekBy(deltaMs: Long) {
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val target = (client.approximateStreamPosition + deltaMs).coerceAtLeast(0)
        client.seek(MediaSeekOptions.Builder().setPosition(target).build())
    }

    override fun stopCasting() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    override fun onDestroy() {
        castContext?.sessionManager?.removeSessionManagerListener(castListener, CastSession::class.java)
        ticker.removeCallbacks(tick)
        stopScrobble()
        saveResume()
        engine.listener = null
        engine.release()
        super.onDestroy()
    }

    // ---- Trackers (Trakt, Simkl) ----

    private fun scrobbleTarget(): Scrobble? {
        val imdb = metaId.substringBefore(':').takeIf { it.startsWith("tt") } ?: return null
        val video = meta?.videos?.firstOrNull { it.id == state.videoId }
        val parts = state.videoId.split(':')
        val season = video?.season ?: parts.getOrNull(1)?.toIntOrNull()?.takeIf { parts.size >= 3 }
        val episode = video?.episode ?: parts.getOrNull(2)?.toIntOrNull()
        return Scrobble(imdb, null, type, season, episode)
    }

    private fun progressPercent(): Double {
        val duration = engine.durationMs.takeIf { it > 0 } ?: return 0.0
        return (engine.positionMs * 100.0 / duration).coerceIn(0.0, 100.0)
    }

    private fun scrobble(action: String) {
        val target = scrobbleTarget() ?: return
        // Trackers want a duration-based progress; skip until the length is known.
        if (progressPercent() <= 0.0 && action != "start") return
        scrobbling = action == "start"
        repository.scrobble(action, target, progressPercent())
    }

    /** "stop" ends the session; at 80% or more Trakt and Simkl mark it watched. */
    private fun stopScrobble() {
        if (!scrobbling) return
        scrobbling = false
        scrobbleTarget()?.let { repository.scrobble("stop", it, progressPercent()) }
    }

    private fun saveResume() {
        val duration = engine.durationMs.takeIf { it > 0 } ?: return
        if (state.videoId.isNotEmpty()) {
            repository.saveResume(state.videoId, engine.positionMs, duration, meta, meta?.videos?.firstOrNull { it.id == state.videoId })
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
