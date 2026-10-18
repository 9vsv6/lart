package app.lart.mobile.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import app.lart.mobile.data.Net
import app.lart.mobile.data.PlayerPrefs
import dev.jdtech.mpv.MPVLib
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Locale

/**
 * mpv, Lart's main engine, as in Harbor's desktop player: libass subtitles (styled
 * anime subtitles look as their authors made them), software decoding when the
 * hardware can't (10-bit H.264), mpv's own audio and subtitle delay, and GLSL
 * shaders for Anime4K. Video goes to a SurfaceView through mpv's GPU output.
 */
class MpvEngine private constructor(context: Context, private val mpv: MPVLib) : Engine, MPVLib.EventObserver, SurfaceHolder.Callback {
    override val name = "mpv"
    private val main = Handler(Looper.getMainLooper())
    private val surface = SurfaceView(context).also { it.holder.addCallback(this) }
    /**
     * Black until mpv shows a frame: a fresh surface can briefly hold whatever its
     * graphics buffer last had, which must never be shown.
     */
    private val cover = View(context).apply { setBackgroundColor(android.graphics.Color.BLACK) }
    private val frame = android.widget.FrameLayout(context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        addView(surface, android.widget.FrameLayout.LayoutParams(-1, -1))
        addView(cover, android.widget.FrameLayout.LayoutParams(-1, -1))
    }
    override val view: View get() = frame
    override var listener: EngineListener? = null

    @Volatile private var position = 0.0
    @Volatile private var duration = 0.0
    @Volatile private var cacheTime = 0.0
    @Volatile private var paused = false
    @Volatile private var pausedForCache = false
    @Volatile private var eof = false
    @Volatile private var loaded = false
    /** Set by load() until mpv starts that file; END_FILE events before then belong to the old one. */
    @Volatile private var awaitingStart = false
    @Volatile private var width = 0L
    @Volatile private var height = 0L
    private var lastPlaying = false

    companion object {
        private const val TAG = "LartMpv"

        /** Null when libmpv can't start here; the player then falls back to Media3. */
        fun create(context: Context, preferredText: String?): MpvEngine? = runCatching {
            val mpv = MPVLib.create(context.applicationContext) ?: error("libmpv didn't start")
            configure(context, mpv, preferredText)
            mpv.init()
            // Applied after init, as mpv-android does: no window until there is a surface.
            mpv.setOptionString("force-window", "no")
            MpvEngine(context, mpv)
        }.onFailure { Log.w(TAG, "mpv unavailable, using Media3", it) }.getOrNull()

        private fun configure(context: Context, mpv: MPVLib, preferredText: String?) {
            fun opt(name: String, value: String) = mpv.setOptionString(name, value)
            opt("config", "no")
            opt("vo", "null")
            opt("gpu-context", "android")
            opt("opengl-es", "yes")
            // Hardware decoding straight into GPU textures (AImageReader), so shaders and subtitles
            // still apply; the copy-back path next, then software.
            opt("hwdec", "mediacodec,mediacodec-copy")
            opt("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
            opt("ao", "audiotrack,opensles")
            opt("tls-verify", "yes")
            caBundle(context)?.let { opt("tls-ca-file", it.absolutePath) }
            opt("cache", "yes")
            opt("demuxer-max-bytes", "${64 * 1024 * 1024}")
            opt("demuxer-max-back-bytes", "${32 * 1024 * 1024}")
            opt("keep-open", "yes")
            opt("idle", "yes")
            opt("input-default-bindings", "no")
            opt("save-position-on-quit", "no")
            opt("alang", "${Locale.getDefault().language},eng,en")
            preferredText?.takeIf { it.isNotEmpty() }?.let { opt("slang", it) }
            opt("sub-auto", "no")
            opt("user-agent", Net.USER_AGENT)
        }

        /** mpv's TLS needs a CA file: written once from the phone's own trusted certificates. */
        private fun caBundle(context: Context): File? = runCatching {
            val file = File(context.filesDir, "cacert.pem")
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < 7L * 86_400_000) return file
            val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val pem = buildString {
                for (alias in store.aliases()) {
                    val cert = store.getCertificate(alias) as? X509Certificate ?: continue
                    append("-----BEGIN CERTIFICATE-----\n")
                    append(Base64.encodeToString(cert.encoded, Base64.DEFAULT))
                    append("-----END CERTIFICATE-----\n")
                }
            }
            file.writeText(pem)
            file
        }.getOrNull()
    }

    init {
        mpv.addObserver(this)
        mpv.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("width", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        mpv.observeProperty("height", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        mpv.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_NONE)
    }

    // ---- Surface ----

    override fun surfaceCreated(holder: SurfaceHolder) {
        mpv.attachSurface(holder.surface)
        // Paused video gets no new frame on its own; redraw so the cover can lift.
        if (loaded) main.postDelayed({ cover.visibility = View.GONE }, 300)
        mpv.setOptionString("force-window", "yes")
        mpv.setPropertyString("vo", "gpu")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mpv.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // A new surface (back from the background) starts covered again until mpv draws.
        main.post { cover.visibility = View.VISIBLE }
        mpv.setPropertyString("vo", "null")
        mpv.setOptionString("force-window", "no")
        mpv.detachSurface()
    }

    // ---- Events, from mpv's thread ----

    override fun eventProperty(property: String) {
        if (property == "track-list") main.post { listener?.onTracksChanged() }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "width" -> width = value
            "height" -> height = value
        }
        if (property == "width" || property == "height") main.post { listener?.onVideoSizeChanged() }
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> position = value
            "duration" -> duration = value
            "demuxer-cache-time" -> cacheTime = value
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> paused = value
            "paused-for-cache" -> {
                pausedForCache = value
                main.post { listener?.onBufferingChanged(value) }
            }
            "eof-reached" -> {
                eof = value
                if (value && loaded) main.post { listener?.onEnded() }
            }
        }
        reportPlaying()
    }

    override fun eventProperty(property: String, value: String) {}

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                awaitingStart = false
                loaded = false
                main.post { listener?.onBufferingChanged(true) }
            }
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                loaded = true
                // The start position applied; later files start from wherever load() says.
                mpv.setPropertyString("start", "none")
                main.post {
                    listener?.onBufferingChanged(false)
                    listener?.onTracksChanged()
                }
            }
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> main.post {
                cover.visibility = View.GONE
                listener?.onBufferingChanged(false)
            }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                // With keep-open, a normal end pauses at the last frame; END_FILE before
                // anything loaded means the source failed.
                if (!loaded && !awaitingStart) main.post { listener?.onError("mpv couldn't open this source.") }
            }
        }
        reportPlaying()
    }

    private fun reportPlaying() {
        val now = isPlaying
        if (now != lastPlaying) {
            lastPlaying = now
            main.post { listener?.onPlayingChanged(now) }
        }
    }

    // ---- Playback ----

    override fun load(url: String, headers: Map<String, String>, startMs: Long) {
        cover.visibility = View.VISIBLE
        awaitingStart = true
        loaded = false
        eof = false
        position = 0.0
        duration = 0.0
        mpv.setPropertyString("user-agent", headers["User-Agent"] ?: Net.USER_AGENT)
        // mpv takes extra headers as a comma-separated list.
        mpv.setPropertyString("http-header-fields", (headers - "User-Agent").entries.joinToString(",") { (k, v) -> "$k: ${v.replace(",", "\\,")}" })
        mpv.setPropertyString("start", if (startMs > 0) "%.3f".format(Locale.US, startMs / 1000.0) else "none")
        mpv.command(arrayOf("loadfile", url, "replace"))
        mpv.setPropertyBoolean("pause", false)
    }

    override fun play() {
        if (eof) seekTo(0)
        mpv.setPropertyBoolean("pause", false)
    }

    override fun pause() = mpv.setPropertyBoolean("pause", true)
    override val isPlaying get() = loaded && !paused && !pausedForCache && !eof
    override val ended get() = eof
    override val positionMs get() = (position * 1000).toLong()
    override val durationMs get() = (duration * 1000).toLong()
    override val bufferedMs get() = (cacheTime * 1000).toLong()

    override fun seekTo(positionMs: Long) {
        mpv.command(arrayOf("seek", "%.3f".format(Locale.US, positionMs.coerceAtLeast(0) / 1000.0), "absolute"))
    }

    override fun setSpeed(speed: Float) = mpv.setPropertyDouble("speed", speed.toDouble())

    override fun setFit(fit: Fit) {
        when (fit) {
            Fit.Fit -> {
                mpv.setPropertyString("keepaspect", "yes")
                mpv.setPropertyDouble("panscan", 0.0)
            }
            Fit.Fill -> {
                mpv.setPropertyString("keepaspect", "yes")
                mpv.setPropertyDouble("panscan", 1.0)
            }
            Fit.Stretch -> {
                mpv.setPropertyString("keepaspect", "no")
                mpv.setPropertyDouble("panscan", 0.0)
            }
        }
    }

    // mpv: positive audio-delay plays the sound later; positive sub-delay shows subtitles later.
    override fun setAudioDelay(delayMs: Long) = mpv.setPropertyDouble("audio-delay", delayMs / 1000.0)
    override fun setSubDelay(delayMs: Long) = mpv.setPropertyDouble("sub-delay", delayMs / 1000.0)

    override val videoSize get() = if (width > 0 && height > 0) width.toInt() to height.toInt() else null

    // ---- Tracks ----

    private fun tracks(kind: String): List<EngineTrack> {
        // mpv returns the whole list as JSON; one read, and no errors for fields a track lacks.
        val list = mpv.getPropertyString("track-list")
            ?.let { runCatching { Net.json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
            ?: return emptyList()
        val out = ArrayList<EngineTrack>()
        for (element in list) {
            val track = element as? JsonObject ?: continue
            fun s(name: String) = (track[name] as? JsonPrimitive)?.contentOrNull
            fun b(name: String) = (track[name] as? JsonPrimitive)?.booleanOrNull == true
            if (s("type") != kind) continue
            val id = s("id") ?: continue
            val title = s("title")
            val lang = s("lang")
            val codec = s("codec")
            val detail = buildList {
                if (kind == "audio") {
                    codecName(codec)?.let(::add)
                    channelLabel((track["demux-channel-count"] as? JsonPrimitive)?.intOrNull)?.let(::add)
                } else {
                    if (b("forced")) add("Forced")
                    when (codec) {
                        "ass", "ssa" -> add("Styled")
                        "hdmv_pgs_subtitle", "dvd_subtitle", "dvb_subtitle" -> add("Image")
                    }
                }
            }.joinToString(" · ").ifEmpty { null }
            out += EngineTrack(
                key = "$kind:$id",
                label = trackName(lang, title, out.size + 1),
                detail = detail,
                selected = b("selected"),
                language = lang,
                commentary = title?.contains("commentary", ignoreCase = true) == true,
                external = b("external"),
            )
        }
        return out
    }

    override fun audioTracks() = tracks("audio")
    override fun textTracks() = tracks("sub")

    override fun selectAudio(key: String) = mpv.setPropertyString("aid", key.substringAfter(':'))

    override fun selectText(key: String?) = mpv.setPropertyString("sid", key?.substringAfter(':') ?: "no")

    override val drawsSubtitles = true

    override fun addExternalSubtitle(url: String, language: String, title: String): Boolean {
        removeExternalSubtitles()
        mpv.command(arrayOf("sub-add", url, "select", title, language))
        return true
    }

    override fun removeExternalSubtitles() {
        tracks("sub").filter { it.external }.forEach { mpv.command(arrayOf("sub-remove", it.key.substringAfter(':'))) }
    }

    /** Harbor's subtitle style on mpv's text subtitles; styled (ASS) ones keep their look, scaled. */
    override fun applySubtitleStyle(prefs: PlayerPrefs, lifted: Boolean) {
        val color = "#%08X".format(prefs.subColor.toInt())
        mpv.setPropertyDouble("sub-scale", prefs.subScale.toDouble())
        mpv.setPropertyString("sub-color", color)
        when (prefs.subBackground) {
            "box" -> {
                mpv.setPropertyString("sub-border-style", "opaque-box")
                mpv.setPropertyString("sub-outline-color", "#B3000000")
                mpv.setPropertyString("sub-back-color", "#B3000000")
                mpv.setPropertyDouble("sub-shadow-offset", 0.0)
            }
            "shadow" -> {
                mpv.setPropertyString("sub-border-style", "outline-and-shadow")
                mpv.setPropertyDouble("sub-outline-size", 0.0)
                mpv.setPropertyDouble("sub-shadow-offset", 2.5)
                mpv.setPropertyString("sub-shadow-color", "#C0000000")
            }
            else -> {
                mpv.setPropertyString("sub-border-style", "outline-and-shadow")
                mpv.setPropertyString("sub-outline-color", "#FF000000")
                mpv.setPropertyDouble("sub-outline-size", 3.0)
                mpv.setPropertyDouble("sub-shadow-offset", 0.0)
            }
        }
        // sub-pos: 100 is the bottom of the picture; lifted above the controls while they show.
        val bottom = prefs.subBottom + if (lifted) CONTROLS_LIFT else 0f
        mpv.setPropertyInt("sub-pos", (100 - bottom * 100 + 6).toInt().coerceIn(0, 100))
    }

    override fun cuesAt(positionMs: Long): List<UiCue> = emptyList()

    override val supportsShaders = true

    override fun setShaders(paths: List<String>) {
        if (paths.isEmpty()) mpv.command(arrayOf("change-list", "glsl-shaders", "clr", ""))
        else mpv.command(arrayOf("change-list", "glsl-shaders", "set", paths.joinToString(":")))
    }

    override fun release() {
        mpv.removeObserver(this)
        runCatching { mpv.destroy() }
    }
}

private fun codecName(codec: String?): String? = when (codec) {
    null -> null
    "ac3" -> "Dolby Digital"
    "eac3" -> "Dolby Digital Plus"
    "truehd" -> "TrueHD"
    "dts" -> "DTS"
    "aac" -> "AAC"
    "opus" -> "Opus"
    "flac" -> "FLAC"
    "mp3" -> "MP3"
    else -> codec.uppercase()
}
