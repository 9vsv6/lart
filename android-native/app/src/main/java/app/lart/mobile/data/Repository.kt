package app.lart.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

private val Context.store by preferencesDataStore(name = "lart")

/** A resume point on this device, with enough of the title to show it in Continue watching. */
@Serializable
data class Resume(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val metaId: String? = null,
    val type: String? = null,
    val name: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

/** One card in Continue watching, from this device or from the Stremio library. */
data class CwItem(
    val meta: Meta,
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val season: Int?,
    val episode: Int?,
    val updatedAt: Long,
    /** Progress as a fraction when only that is known (Trakt). */
    val fraction: Float? = null,
    /** Where the card came from when it isn't this device or Stremio: "Trakt". */
    val source: String? = null,
)

data class Account(val authKey: String, val email: String)

@Serializable
data class Profile(val id: String, val name: String, val color: Long)

const val MAIN_PROFILE = "main"

/** Harbor's profile colours. */
val PROFILE_COLORS = listOf(0xFFF0A35E, 0xFF5E9CF0, 0xFF6CCB8A, 0xFFE0607E, 0xFFB38BF0, 0xFF4CC3C9, 0xFFE8C547, 0xFF9AA0A6)

/** Player choices that persist between videos, like Harbor's subtitle style bar. */
@Serializable
data class PlayerPrefs(
    /** Language code to turn subtitles on in automatically; empty means off. */
    val subLanguage: String = "",
    val subScale: Float = 1f,
    val subColor: Long = 0xFFFFFFFF,
    /** "outline", "shadow" or "box". */
    val subBackground: String = "outline",
    /** Distance from the bottom of the picture, as a fraction of its height. */
    val subBottom: Float = 0.08f,
    val autoSkipIntro: Boolean = false,
    val speed: Float = 1f,
    /** Ask "Still watching?" after this many episodes in a row; 0 turns it off. */
    val stillWatchingAfter: Int = 3,
    /** Anime4K mode name ("A", "B", …) for mpv; empty is off. */
    val anime4kMode: String = "",
    /** Anime4K's heavier VL networks instead of M. */
    val anime4kHq: Boolean = false,
)

/**
 * App-wide state: the Stremio account, its addons, its library, resume points and
 * My List. Resume points and My List stay on the device; the Stremio library is read
 * but never written back yet, so nothing here can damage an account's library.
 */
class Repository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _addons = MutableStateFlow<List<Addon>>(emptyList())
    val addons: StateFlow<List<Addon>> = _addons.asStateFlow()

    /** Cinemeta and Kitsu, always available for details and the Anime view, like Harbor's built-ins. */
    private val _builtins = MutableStateFlow<List<Addon>>(emptyList())
    val builtins: StateFlow<List<Addon>> = _builtins.asStateFlow()

    private val _library = MutableStateFlow<List<LibraryItem>>(emptyList())
    val library: StateFlow<List<LibraryItem>> = _library.asStateFlow()

    private val _debrid = MutableStateFlow<DebridAccount?>(null)
    val debrid: StateFlow<DebridAccount?> = _debrid.asStateFlow()

    private val _tmdbKey = MutableStateFlow<String?>(null)
    /** The user's own TMDB key; with it, rows and artwork come from TMDB as in Harbor. */
    val tmdbKey: StateFlow<String?> = _tmdbKey.asStateFlow()

    private val _autoPlay = MutableStateFlow(false)
    /** Skip the streams list and play the best source straight away. */
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()

    private val _playerPrefs = MutableStateFlow(PlayerPrefs())
    val playerPrefs: StateFlow<PlayerPrefs> = _playerPrefs.asStateFlow()

    private val _myList = MutableStateFlow<List<Meta>>(emptyList())
    val myList: StateFlow<List<Meta>> = _myList.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _resume = MutableStateFlow<Map<String, Resume>>(emptyMap())
    private val _traktCw = MutableStateFlow<List<CwItem>>(emptyList())

    private val _trackers = MutableStateFlow(TrackerSettings())
    /** This profile's tracker sign-ins. */
    val trackers: StateFlow<TrackerSettings> = _trackers.asStateFlow()
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    private val resumeSerializer = MapSerializer(String.serializer(), Resume.serializer())
    private val metaListSerializer = ListSerializer(Meta.serializer())
    private val stringSet = SetSerializer(String.serializer())

    /** Newest first; one card per title. */
    val continueWatching: StateFlow<List<CwItem>> = combine(_resume, _library, _dismissed, _traktCw) { resume, library, dismissed, trakt ->
        val local = resume.mapNotNull { (videoId, r) ->
            val metaId = r.metaId ?: return@mapNotNull null
            CwItem(
                Meta(id = metaId, type = r.type ?: "movie", name = r.name.orEmpty(), poster = r.poster, background = r.background),
                videoId, r.positionMs, r.durationMs, r.season, r.episode, r.updatedAt,
            )
        }
        val remote = library.filter { !it.removed && !it.temp && (it.state?.timeOffset ?: 0) > 0 }.map { item ->
            val state = item.state!!
            val parts = state.videoId?.split(':').orEmpty()
            CwItem(
                item.toMeta(),
                state.videoId ?: item.id,
                state.timeOffset,
                state.duration,
                parts.getOrNull(parts.size - 2)?.toIntOrNull()?.takeIf { parts.size >= 3 },
                parts.lastOrNull()?.toIntOrNull()?.takeIf { parts.size >= 3 },
                parseTime(state.lastWatched ?: item.mtime),
            )
        }
        (local + remote + trakt).sortedByDescending { it.updatedAt }
            .distinctBy { it.meta.id }
            .filter { "${it.meta.id}@${it.videoId}" !in dismissed }
            .take(20)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun loadProfile(prefs: Preferences) {
        _autoPlay.value = prefs[keyAutoPlay] ?: false
        _playerPrefs.value = prefs[keyPlayer]
            ?.let { runCatching { Net.json.decodeFromString(PlayerPrefs.serializer(), it) }.getOrNull() }
            ?: PlayerPrefs()
        _resume.value = prefs[keyResume]?.let { runCatching { Net.json.decodeFromString(resumeSerializer, it) }.getOrNull() } ?: emptyMap()
        _myList.value = prefs[keyMyList]?.let { runCatching { Net.json.decodeFromString(metaListSerializer, it) }.getOrNull() } ?: emptyList()
        _dismissed.value = prefs[keyDismissed]?.let { runCatching { Net.json.decodeFromString(stringSet, it) }.getOrNull() } ?: emptySet()
        _trackers.value = prefs[keyTrackers]?.let { runCatching { Net.json.decodeFromString(TrackerSettings.serializer(), it) }.getOrNull() } ?: TrackerSettings()
        _traktCw.value = emptyList()
        scope.launch { refreshTrackers() }
        profileLoaded.forEach { it(prefs) }
    }

    // ---- Profiles, like Harbor's: each has its own lists, progress, player settings and trackers ----

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<Profile?>(null)
    val activeProfile: StateFlow<Profile?> = _activeProfile.asStateFlow()

    private val _profileChosen = MutableStateFlow(false)
    /** False until someone picks a profile on "Who's watching?". */
    val profileChosen: StateFlow<Boolean> = _profileChosen.asStateFlow()

    private var profileId = MAIN_PROFILE
    private val profileList = ListSerializer(Profile.serializer())

    /** Other per-profile state (trackers) reloads through these when the profile changes. */
    internal val profileLoaded = mutableListOf<(Preferences) -> Unit>()

    /** A store key for the active profile. The first profile keeps the plain keys, so data from before profiles stays. */
    internal fun profileKey(name: String): String = if (profileId == MAIN_PROFILE) name else "$name@$profileId"

    private val keyResume get() = stringPreferencesKey(profileKey("resume"))
    private val keyMyList get() = stringPreferencesKey(profileKey("myList"))
    private val keyDismissed get() = stringPreferencesKey(profileKey("cwDismissed"))
    private val keyPlayer get() = stringPreferencesKey(profileKey("player"))
    private val keyAutoPlay get() = booleanPreferencesKey(profileKey("autoPlay"))
    private val keyTrackers get() = stringPreferencesKey(profileKey("trackers"))

    fun updateTrackers(change: (TrackerSettings) -> TrackerSettings) {
        val next = change(_trackers.value)
        _trackers.value = next
        scope.launch { context.store.edit { it[keyTrackers] = Net.json.encodeToString(TrackerSettings.serializer(), next) } }
        if (next.trakt?.accessToken == null) _traktCw.value = emptyList()
    }

    /** Trakt's paused titles join Continue watching; its token is refreshed when due. */
    suspend fun refreshTrackers() {
        val trakt = _trackers.value.trakt?.takeIf { it.accessToken != null } ?: return
        val fresh = runCatching { Trakt.fresh(trakt) }.getOrDefault(trakt)
        if (fresh != trakt) updateTrackers { it.copy(trakt = fresh) }
        _traktCw.value = runCatching { Trakt.playback(fresh) }.getOrDefault(emptyList())
    }

    /** Where Trakt says `videoId` was paused, as a fraction, when this device has no position. */
    fun traktFraction(videoId: String): Float? = _traktCw.value.firstOrNull { it.videoId == videoId }?.fraction

    /** Tells signed-in trackers what's playing: "start", "pause" or "stop" (stop at 80%+ marks watched). */
    fun scrobble(action: String, what: Scrobble, progressPercent: Double) {
        val trackers = _trackers.value
        scope.launch {
            trackers.trakt?.takeIf { it.accessToken != null }?.let { auth -> runCatching { Trakt.scrobble(auth, action, what, progressPercent) } }
            trackers.simkl?.takeIf { it.accessToken != null }?.let { auth -> runCatching { Simkl.scrobble(auth, action, what, progressPercent) } }
        }
    }

    /** AniList progress for anime (Kitsu ids), once an episode is mostly watched. */
    fun markAnimeWatched(kitsuId: String, episode: Int) {
        val auth = _trackers.value.anilist?.takeIf { it.accessToken != null } ?: return
        scope.launch { runCatching { AniList.markWatched(auth, kitsuId, episode) } }
    }

    internal suspend fun editStore(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.store.edit { block(it) }
    }

    suspend fun switchProfile(id: String) {
        val profile = _profiles.value.firstOrNull { it.id == id } ?: return
        profileId = id
        _activeProfile.value = profile
        context.store.edit { it[KEY_ACTIVE_PROFILE] = id }
        loadProfile(context.store.data.first())
        _profileChosen.value = true
    }

    /** Shows "Who's watching?" again, as Harbor's Switch profile does. */
    fun showProfilePicker() {
        _profileChosen.value = false
    }

    suspend fun saveProfile(profile: Profile) {
        val current = _profiles.value
        val next = if (current.any { it.id == profile.id }) current.map { if (it.id == profile.id) profile else it } else current + profile
        _profiles.value = next
        if (_activeProfile.value?.id == profile.id) _activeProfile.value = profile
        context.store.edit { it[KEY_PROFILES] = Net.json.encodeToString(profileList, next) }
    }

    suspend fun newProfile(name: String): Profile {
        val used = _profiles.value.map { it.color }.toSet()
        val profile = Profile(
            id = java.util.UUID.randomUUID().toString().take(8),
            name = name.trim().ifEmpty { "Profile ${_profiles.value.size + 1}" },
            color = PROFILE_COLORS.firstOrNull { it !in used } ?: PROFILE_COLORS.random(),
        )
        saveProfile(profile)
        return profile
    }

    /** Deletes a profile and everything it kept. The first profile can't be deleted. */
    suspend fun deleteProfile(id: String) {
        if (id == MAIN_PROFILE) return
        if (profileId == id) switchProfile(MAIN_PROFILE)
        val next = _profiles.value.filterNot { it.id == id }
        _profiles.value = next
        context.store.edit { prefs ->
            prefs[KEY_PROFILES] = Net.json.encodeToString(profileList, next)
            prefs.asMap().keys.filter { it.name.endsWith("@$id") }.forEach { prefs.remove(it) }
        }
    }

    /** Reloads the addon collection and the library for the current account. */
    suspend fun refresh() {
        val account = _account.value
        val remote = account?.let { runCatching { StremioApi.addons(it.authKey) }.getOrNull() }.orEmpty()
        _addons.value = remote.ifEmpty { loadManifests(AddonClient.defaults) }
        _library.value = account?.let { runCatching { StremioApi.library(it.authKey) }.getOrNull() }.orEmpty()
    }

    private suspend fun loadManifests(urls: List<String>): List<Addon> = kotlinx.coroutines.coroutineScope {
        urls.map { url -> async { runCatching { AddonClient.manifest(url) }.getOrNull() } }.awaitAll().filterNotNull()
    }

    /** The account's addons, then any built-in the account doesn't have, for metadata lookups. */
    fun metaAddons(): List<Addon> {
        val installed = _addons.value
        val ids = installed.map { it.manifest.id }.toSet()
        return installed + _builtins.value.filter { it.manifest.id !in ids }
    }

    suspend fun signIn(email: String, password: String) {
        val login = StremioApi.login(email, password)
        context.store.edit {
            it[KEY_AUTH] = login.authKey
            it[KEY_EMAIL] = login.email
        }
        _account.value = Account(login.authKey, login.email)
        refresh()
    }

    suspend fun signOut() {
        _account.value?.let { StremioApi.logout(it.authKey) }
        context.store.edit {
            it.remove(KEY_AUTH)
            it.remove(KEY_EMAIL)
        }
        _account.value = null
        refresh()
    }

    /** Checks the key with the service first, so a typo fails here and not at play time. */
    suspend fun setDebrid(account: DebridAccount): String {
        val name = Debrid.verify(account)
        context.store.edit {
            it[KEY_DEBRID_SERVICE] = account.service.name
            it[KEY_DEBRID_KEY] = account.apiKey
        }
        _debrid.value = account
        return name
    }

    suspend fun clearDebrid() {
        context.store.edit {
            it.remove(KEY_DEBRID_SERVICE)
            it.remove(KEY_DEBRID_KEY)
        }
        _debrid.value = null
    }

    /** Checks the key with TMDB first. */
    suspend fun setTmdbKey(key: String?) {
        val clean = key?.trim()?.takeIf { it.isNotEmpty() }
        if (clean != null) Tmdb.verify(clean)
        context.store.edit { if (clean == null) it.remove(KEY_TMDB) else it[KEY_TMDB] = clean }
        _tmdbKey.value = clean
    }

    fun setAutoPlay(enabled: Boolean) {
        _autoPlay.value = enabled
        scope.launch { context.store.edit { it[keyAutoPlay] = enabled } }
    }

    fun updatePlayerPrefs(change: (PlayerPrefs) -> PlayerPrefs) {
        val next = change(_playerPrefs.value)
        _playerPrefs.value = next
        scope.launch { context.store.edit { it[keyPlayer] = Net.json.encodeToString(PlayerPrefs.serializer(), next) } }
    }

    // ---- My List (Harbor's watchlist), kept on this device ----

    fun inMyList(id: String): Boolean = _myList.value.any { it.id == id }

    fun toggleMyList(meta: Meta) {
        val current = _myList.value
        val next = if (current.any { it.id == meta.id }) current.filterNot { it.id == meta.id }
        else listOf(meta.copy(videos = null, description = null)) + current
        _myList.value = next
        scope.launch { context.store.edit { it[keyMyList] = Net.json.encodeToString(metaListSerializer, next) } }
    }

    // ---- Resume points and Continue watching ----

    /** Where to resume `videoId`: this device's last position, else the Stremio library's. */
    fun resumeFor(videoId: String, metaId: String): Long {
        _resume.value[videoId]?.let { return it.positionMs }
        val item = _library.value.firstOrNull { it.id == metaId } ?: return 0
        val state = item.state ?: return 0
        return if ((state.videoId ?: metaId) == videoId) state.timeOffset else 0
    }

    fun saveResume(videoId: String, positionMs: Long, durationMs: Long, meta: Meta? = null, video: Video? = null) {
        // Within the last 5% counts as finished: start from the beginning next time.
        val finished = durationMs > 0 && positionMs >= durationMs * 0.95
        val current = _resume.value
        val previous = current[videoId]
        val next = if (finished || positionMs < 10_000) current - videoId
        else current + (
            videoId to Resume(
                positionMs, durationMs, System.currentTimeMillis(),
                metaId = meta?.id ?: previous?.metaId,
                type = meta?.type ?: previous?.type,
                name = meta?.name ?: previous?.name,
                poster = meta?.poster ?: previous?.poster,
                background = meta?.background ?: previous?.background,
                season = video?.season ?: previous?.season,
                episode = video?.episode ?: previous?.episode,
            )
        )
        _resume.value = next
        // Watching it again brings a dismissed card back.
        meta?.let { m -> _dismissed.value = _dismissed.value.filterNot { it.startsWith(m.id + "@") }.toSet() }
        scope.launch { context.store.edit { it[keyResume] = Net.json.encodeToString(resumeSerializer, next) } }
    }

    fun dismissContinueWatching(item: CwItem) {
        val next = _dismissed.value + "${item.meta.id}@${item.videoId}"
        _dismissed.value = next
        scope.launch { context.store.edit { it[keyDismissed] = Net.json.encodeToString(stringSet, next) } }
    }

    // Last, so every property above exists before the background load reads it.
    init {
        scope.launch {
            val prefs = context.store.data.first()
            val authKey = prefs[KEY_AUTH]
            if (authKey != null) _account.value = Account(authKey, prefs[KEY_EMAIL] ?: "")
            val service = prefs[KEY_DEBRID_SERVICE]?.let { name -> DebridService.entries.firstOrNull { it.name == name } }
            val key = prefs[KEY_DEBRID_KEY]
            if (service != null && !key.isNullOrBlank()) _debrid.value = DebridAccount(service, key)
            _tmdbKey.value = prefs[KEY_TMDB]?.takeIf { it.isNotBlank() }
            val profiles = prefs[KEY_PROFILES]?.let { runCatching { Net.json.decodeFromString(profileList, it) }.getOrNull() }
                ?.takeIf { it.isNotEmpty() } ?: listOf(Profile(MAIN_PROFILE, "Me", PROFILE_COLORS.first()))
            _profiles.value = profiles
            profileId = prefs[KEY_ACTIVE_PROFILE]?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
            _activeProfile.value = profiles.first { it.id == profileId }
            // One profile: nobody to ask. Several: Harbor asks "Who's watching?" on launch.
            _profileChosen.value = profiles.size == 1
            loadProfile(prefs)
            launch { _builtins.value = loadManifests(AddonClient.builtins) }
            refresh()
            _ready.value = true
        }
    }

    private companion object {
        val KEY_AUTH: Preferences.Key<String> = stringPreferencesKey("authKey")
        val KEY_EMAIL: Preferences.Key<String> = stringPreferencesKey("email")
        val KEY_DEBRID_SERVICE: Preferences.Key<String> = stringPreferencesKey("debridService")
        val KEY_DEBRID_KEY: Preferences.Key<String> = stringPreferencesKey("debridKey")
        val KEY_PROFILES: Preferences.Key<String> = stringPreferencesKey("profiles")
        val KEY_TMDB: Preferences.Key<String> = stringPreferencesKey("tmdbKey")
        val KEY_ACTIVE_PROFILE: Preferences.Key<String> = stringPreferencesKey("activeProfile")

        fun parseTime(value: String?): Long =
            value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0
    }
}
