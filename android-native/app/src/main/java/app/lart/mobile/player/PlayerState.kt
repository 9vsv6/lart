package app.lart.mobile.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.ui.AspectRatioFrameLayout
import app.lart.mobile.data.RankedStream
import app.lart.mobile.data.SkipSegment
import app.lart.mobile.data.Subtitle
import app.lart.mobile.data.Video

/** A subtitle event as the overlay draws it: text, or an image (PGS/VobSub). */
data class UiCue(
    val text: String? = null,
    val image: androidx.compose.ui.graphics.ImageBitmap? = null,
    val top: Boolean = false,
    /** Width as a fraction of the picture, for images; 0 when unknown. */
    val width: Float = 0f,
)

/** One audio or embedded subtitle track, as the menus show it. */
data class TrackOption(val key: String, val label: String, val detail: String?, val selected: Boolean)

sealed interface SubChoice {
    data object Off : SubChoice
    data class Embedded(val key: String) : SubChoice
    data class Addon(val subtitle: Subtitle) : SubChoice
}

enum class Panel { Subtitles, SubStyle, Audio, Speed, Sources, Episodes, Sleep, Anime4K }

enum class Fit(val label: String, val mode: Int) {
    Fit("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("Fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Stretch("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

/** Everything the player overlay draws; the activity updates it from ExoPlayer. */
class PlayerState {
    var title by mutableStateOf("")
    var episodeLine by mutableStateOf<String?>(null)
    var isSeries by mutableStateOf(false)

    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var ended by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0)
    var durationMs by mutableLongStateOf(0)
    var bufferedMs by mutableLongStateOf(0)

    var controlsVisible by mutableStateOf(true)
    var locked by mutableStateOf(false)
    var inPip by mutableStateOf(false)
    var panel by mutableStateOf<Panel?>(null)

    var speed by mutableFloatStateOf(1f)
    var fit by mutableStateOf(Fit.Fit)

    var audioTracks by mutableStateOf<List<TrackOption>>(emptyList())
    var embeddedSubs by mutableStateOf<List<TrackOption>>(emptyList())
    var addonSubs by mutableStateOf<List<Subtitle>?>(null)
    var subChoice by mutableStateOf<SubChoice>(SubChoice.Off)
    var subDelayMs by mutableLongStateOf(0)
    /** Subtitle events showing now, already shifted by the sync offset. */
    var cues by mutableStateOf<List<UiCue>>(emptyList())
    var audioDelayMs by mutableLongStateOf(0)

    /** The Chromecast's name while casting, else null. */
    var castingTo by mutableStateOf<String?>(null)
    var castPlaying by mutableStateOf(false)

    /** "mpv" or "Media3" (the fallback). */
    var engineName by mutableStateOf("")
    var anime4kAvailable by mutableStateOf(false)
    var anime4kLoading by mutableStateOf(false)
    var subLoading by mutableStateOf(false)

    var skip by mutableStateOf<SkipSegment?>(null)
    var next by mutableStateOf<Video?>(null)
    var nextLoading by mutableStateOf(false)
    var episodes by mutableStateOf<List<Video>>(emptyList())
    var videoId by mutableStateOf("")
    var sources by mutableStateOf<List<RankedStream>?>(null)
    var currentUrl by mutableStateOf("")

    /** Wall-clock time the sleep timer pauses at, or -1 for "end of this episode". */
    var sleepAt by mutableStateOf<Long?>(null)
    var stillWatching by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var resumedFromMs by mutableLongStateOf(0)
    /** Short-lived feedback from gestures: "+10s", "Volume 60%". */
    var hint by mutableStateOf<String?>(null)
    var hintIcon by mutableIntStateOf(0)
}
