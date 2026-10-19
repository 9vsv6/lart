package app.lart.mobile.data

import kotlinx.serialization.Serializable

/**
 * A stream filter, as Harbor's saved stream filters (`src/lib/streams/custom-filters.ts`):
 * every non-empty field must match. Empty sets mean "any".
 */
@Serializable
data class StreamFilter(
    /** 2160, 1080, 720, 480. */
    val resolutions: Set<Int> = emptySet(),
    /** "remux", "bluray", "web". */
    val sources: Set<String> = emptySet(),
    /** "hevc", "av1", "h264". */
    val codecs: Set<String> = emptySet(),
    val requireHdr: Boolean = false,
    val cachedOnly: Boolean = false,
    /** 0 is no limit. */
    val maxSizeGb: Int = 0,
) {
    val active: Boolean
        get() = resolutions.isNotEmpty() || sources.isNotEmpty() || codecs.isNotEmpty() || requireHdr || cachedOnly || maxSizeGb > 0
}

/**
 * The settings Harbor has that Lart native can act on, kept per profile like Harbor's.
 * Defaults follow Harbor's (`src/lib/settings/defaults.ts`) unless a comment says why not.
 */
@Serializable
data class AppSettings(
    // ---- Playback: player ----
    /** "mpv" (Harbor's player) or "media3" (Android's own). */
    val engine: String = "mpv",
    /** Start where you left off; off starts every video from the beginning. */
    val resumePlayback: Boolean = true,
    /** Show "Resumed at 12:34 · Start over" when resuming. */
    val resumeNotice: Boolean = true,
    val seekBackSec: Int = 10,
    val seekForwardSec: Int = 10,
    val playbackSpeeds: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f),
    val sleepMinutes: List<Int> = listOf(15, 30, 45, 60, 90),
    /** Harbor hides the lock by default on desktop; on a phone it's the screen-lock button. */
    val screenLock: Boolean = true,
    /** "Fit", "Fill" or "Stretch". */
    val defaultFit: String = "Fit",

    // ---- Playback: between episodes ----
    val autoPlayNextEpisode: Boolean = true,
    /** When Up Next shows: seconds before the end; 0 means at the credits (when known), else 45 s. */
    val nextEpisodeLeadSec: Int = 45,
    val autoSkipRecap: Boolean = false,
    val autoSkipOutro: Boolean = false,
    val showSkipButton: Boolean = true,
    /** Hide the Skip button after this many seconds; 0 keeps it for the whole segment. */
    val skipButtonHideSec: Int = 0,

    // ---- Playback: sources ----
    val autoNextStreamOnStall: Boolean = false,
    val stallSec: Int = 10,
    val keepSourceNextEpisode: Boolean = false,
    val rememberLastStream: Boolean = true,

    // ---- mpv ----
    /** "auto" (hardware, then copy-back, then software), "on" (hardware only), "off". */
    val mpvHwdec: String = "auto",
    /** "balanced", "performance" or "quality". */
    val mpvQuality: String = "balanced",
    val downmixStereo: Boolean = false,
    /** Loudest mpv may go, as a multiple of 100%. */
    val volumeBoostMax: Int = 2,
    /** "auto", "small", "large". */
    val bufferSize: String = "auto",
    val audioNormalize: Boolean = false,
    /** "off", "bass", "voice", "bass-reduce", "night". */
    val audioProfile: String = "off",
    /** One mpv option per line, as key=value. */
    val mpvExtraOptions: String = "",
    val anime4kAnimeOnly: Boolean = true,
    val anime4kIndicator: Boolean = true,

    // ---- Languages ----
    /** Audio languages in order of preference; empty means the device language. */
    val audioLanguages: List<String> = emptyList(),
    /** More subtitle languages to try, after the main one. */
    val subtitleFallbacks: List<String> = emptyList(),
    val subtitlesOffByDefault: Boolean = false,
    /** Harbor's default is off; inside-the-video subtitles are in sync with the file, so Lart prefers them. */
    val preferEmbeddedSubs: Boolean = true,
    val hideSdh: Boolean = false,
    /** A second subtitle shown at the same time (mpv); empty is off. */
    val secondarySubLanguage: String = "",
    /** mpv sub-ass-override: "no", "scale", "force". */
    val assOverride: String = "scale",
    val subBold: Boolean = false,
    val subBoxOpacity: Float = 0.6f,
    /** "left", "center", "right". */
    val subAlignX: String = "center",
    val subtitleAddons: Boolean = true,
    /** TMDB's language for titles and descriptions; empty follows the device. */
    val metadataLanguage: String = "",

    // ---- Sources ----
    /** "lart" (ranked best first) or "addon" (each addon's own order, addons in your order). */
    val streamSort: String = "lart",
    val streamFilter: StreamFilter = StreamFilter(),
    /** "off", "balanced" (hide CAM and fakes) or "strict" (also tiny files). */
    val safetyFilter: String = "balanced",
    /** Language names to prefer in source titles ("English", "French"…). */
    val streamLanguages: List<String> = emptyList(),
    val requireStreamLanguage: Boolean = false,
    val torrentsDisabled: Boolean = false,
    val showFilename: Boolean = false,
    val addonTimeoutSec: Int = 20,

    // ---- Library and cards ----
    val showRatingBadge: Boolean = true,
    val hidePosterTitles: Boolean = false,
    val posterRadius: Int = 8,
    val posterScale: Float = 1f,
    val showTop10: Boolean = true,
    val heroAutoRotate: Boolean = true,
    val hideUnreleased: Boolean = false,
    val hideAnime: Boolean = false,
    val traktInContinueWatching: Boolean = true,
    val episodesNewestFirst: Boolean = false,
    val showEpisodeDescription: Boolean = true,
    val hideSpoilers: Boolean = false,
    val spoilerThumbnails: Boolean = true,
    val spoilerTitles: Boolean = true,
    val spoilerDescriptions: Boolean = true,
    /** Episode card size on title pages: 0 default, 1 large, 2 extra large (Harbor's Card size). */
    val episodeCardSize: Int = 0,

    // ---- Appearance and profiles ----
    val accent: Long = 0xFFF0A35E,
    /** "Who's watching?" on every launch when there are several profiles. */
    val askProfileOnLaunch: Boolean = true,
)
