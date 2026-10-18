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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val Context.store by preferencesDataStore(name = "lart")

@Serializable
data class Resume(val positionMs: Long, val durationMs: Long, val updatedAt: Long)

data class Account(val authKey: String, val email: String)

/**
 * App-wide state: the Stremio account, its addons, its library, and resume points.
 * Resume points stay on the device; progress already in the Stremio library is read
 * but never written back yet, so nothing here can damage an account's library.
 */
class Repository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _addons = MutableStateFlow<List<Addon>>(emptyList())
    val addons: StateFlow<List<Addon>> = _addons.asStateFlow()

    private val _library = MutableStateFlow<List<LibraryItem>>(emptyList())
    val library: StateFlow<List<LibraryItem>> = _library.asStateFlow()

    private val _debrid = MutableStateFlow<DebridAccount?>(null)
    val debrid: StateFlow<DebridAccount?> = _debrid.asStateFlow()

    private val _autoPlay = MutableStateFlow(false)
    /** Skip the streams list and play the best source straight away. */
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var resume: Map<String, Resume> = emptyMap()
    private val resumeSerializer = MapSerializer(String.serializer(), Resume.serializer())

    init {
        scope.launch {
            val prefs = context.store.data.first()
            val authKey = prefs[KEY_AUTH]
            if (authKey != null) _account.value = Account(authKey, prefs[KEY_EMAIL] ?: "")
            val service = prefs[KEY_DEBRID_SERVICE]?.let { name -> DebridService.entries.firstOrNull { it.name == name } }
            val key = prefs[KEY_DEBRID_KEY]
            if (service != null && !key.isNullOrBlank()) _debrid.value = DebridAccount(service, key)
            _autoPlay.value = prefs[KEY_AUTOPLAY] ?: false
            resume = prefs[KEY_RESUME]?.let { runCatching { Net.json.decodeFromString(resumeSerializer, it) }.getOrNull() } ?: emptyMap()
            refresh()
            _ready.value = true
        }
    }

    /** Reloads the addon collection and the library for the current account. */
    suspend fun refresh() {
        val account = _account.value
        val remote = account?.let { runCatching { StremioApi.addons(it.authKey) }.getOrNull() }.orEmpty()
        _addons.value = remote.ifEmpty { loadDefaultAddons() }
        _library.value = account?.let { runCatching { StremioApi.library(it.authKey) }.getOrNull() }.orEmpty()
    }

    private suspend fun loadDefaultAddons(): List<Addon> = kotlinx.coroutines.coroutineScope {
        AddonClient.defaults.map { url -> async { runCatching { AddonClient.manifest(url) }.getOrNull() } }
            .awaitAll()
            .filterNotNull()
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

    /** Where to resume `videoId`: this device's last position, else the Stremio library's. */
    fun resumeFor(videoId: String, metaId: String): Long {
        resume[videoId]?.let { return it.positionMs }
        val item = _library.value.firstOrNull { it.id == metaId } ?: return 0
        val state = item.state ?: return 0
        return if ((state.videoId ?: metaId) == videoId) state.timeOffset else 0
    }

    fun saveResume(videoId: String, positionMs: Long, durationMs: Long) {
        // Within the last 5% counts as finished: start from the beginning next time.
        val finished = durationMs > 0 && positionMs >= durationMs * 0.95
        resume = if (finished || positionMs < 10_000) resume - videoId
        else resume + (videoId to Resume(positionMs, durationMs, System.currentTimeMillis()))
        val snapshot = resume
        scope.launch { context.store.edit { it[KEY_RESUME] = Net.json.encodeToString(resumeSerializer, snapshot) } }
    }

    private companion object {
        val KEY_AUTH: Preferences.Key<String> = stringPreferencesKey("authKey")
        val KEY_EMAIL: Preferences.Key<String> = stringPreferencesKey("email")
        val KEY_RESUME: Preferences.Key<String> = stringPreferencesKey("resume")
        val KEY_DEBRID_SERVICE: Preferences.Key<String> = stringPreferencesKey("debridService")
        val KEY_DEBRID_KEY: Preferences.Key<String> = stringPreferencesKey("debridKey")
        val KEY_AUTOPLAY: Preferences.Key<Boolean> = booleanPreferencesKey("autoPlay")
    }
}
