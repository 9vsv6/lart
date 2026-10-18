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
)

data class Account(val authKey: String, val email: String)

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
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    private val resumeSerializer = MapSerializer(String.serializer(), Resume.serializer())
    private val metaListSerializer = ListSerializer(Meta.serializer())
    private val stringSet = SetSerializer(String.serializer())

    /** Newest first; one card per title. */
    val continueWatching: StateFlow<List<CwItem>> = combine(_resume, _library, _dismissed) { resume, library, dismissed ->
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
        (local + remote).sortedByDescending { it.updatedAt }
            .distinctBy { it.meta.id }
            .filter { "${it.meta.id}@${it.videoId}" !in dismissed }
            .take(20)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            val prefs = context.store.data.first()
            val authKey = prefs[KEY_AUTH]
            if (authKey != null) _account.value = Account(authKey, prefs[KEY_EMAIL] ?: "")
            val service = prefs[KEY_DEBRID_SERVICE]?.let { name -> DebridService.entries.firstOrNull { it.name == name } }
            val key = prefs[KEY_DEBRID_KEY]
            if (service != null && !key.isNullOrBlank()) _debrid.value = DebridAccount(service, key)
            _autoPlay.value = prefs[KEY_AUTOPLAY] ?: false
            _playerPrefs.value = prefs[KEY_PLAYER]
                ?.let { runCatching { Net.json.decodeFromString(PlayerPrefs.serializer(), it) }.getOrNull() }
                ?: PlayerPrefs()
            _resume.value = prefs[KEY_RESUME]?.let { runCatching { Net.json.decodeFromString(resumeSerializer, it) }.getOrNull() } ?: emptyMap()
            _myList.value = prefs[KEY_MYLIST]?.let { runCatching { Net.json.decodeFromString(metaListSerializer, it) }.getOrNull() } ?: emptyList()
            _dismissed.value = prefs[KEY_DISMISSED]?.let { runCatching { Net.json.decodeFromString(stringSet, it) }.getOrNull() } ?: emptySet()
            launch { _builtins.value = loadManifests(AddonClient.builtins) }
            refresh()
            _ready.value = true
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

    fun setAutoPlay(enabled: Boolean) {
        _autoPlay.value = enabled
        scope.launch { context.store.edit { it[KEY_AUTOPLAY] = enabled } }
    }

    fun updatePlayerPrefs(change: (PlayerPrefs) -> PlayerPrefs) {
        val next = change(_playerPrefs.value)
        _playerPrefs.value = next
        scope.launch { context.store.edit { it[KEY_PLAYER] = Net.json.encodeToString(PlayerPrefs.serializer(), next) } }
    }

    // ---- My List (Harbor's watchlist), kept on this device ----

    fun inMyList(id: String): Boolean = _myList.value.any { it.id == id }

    fun toggleMyList(meta: Meta) {
        val current = _myList.value
        val next = if (current.any { it.id == meta.id }) current.filterNot { it.id == meta.id }
        else listOf(meta.copy(videos = null, description = null)) + current
        _myList.value = next
        scope.launch { context.store.edit { it[KEY_MYLIST] = Net.json.encodeToString(metaListSerializer, next) } }
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
        scope.launch { context.store.edit { it[KEY_RESUME] = Net.json.encodeToString(resumeSerializer, next) } }
    }

    fun dismissContinueWatching(item: CwItem) {
        val next = _dismissed.value + "${item.meta.id}@${item.videoId}"
        _dismissed.value = next
        scope.launch { context.store.edit { it[KEY_DISMISSED] = Net.json.encodeToString(stringSet, next) } }
    }

    private companion object {
        val KEY_AUTH: Preferences.Key<String> = stringPreferencesKey("authKey")
        val KEY_EMAIL: Preferences.Key<String> = stringPreferencesKey("email")
        val KEY_RESUME: Preferences.Key<String> = stringPreferencesKey("resume")
        val KEY_DEBRID_SERVICE: Preferences.Key<String> = stringPreferencesKey("debridService")
        val KEY_DEBRID_KEY: Preferences.Key<String> = stringPreferencesKey("debridKey")
        val KEY_AUTOPLAY: Preferences.Key<Boolean> = booleanPreferencesKey("autoPlay")
        val KEY_PLAYER: Preferences.Key<String> = stringPreferencesKey("player")
        val KEY_MYLIST: Preferences.Key<String> = stringPreferencesKey("myList")
        val KEY_DISMISSED: Preferences.Key<String> = stringPreferencesKey("cwDismissed")

        fun parseTime(value: String?): Long =
            value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0
    }
}
