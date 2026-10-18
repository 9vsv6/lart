package app.lart.mobile.data

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A tracker sign-in. The client id (and Trakt's secret) come from an app the user
 * registers with the tracker themselves, the same way debrid keys are pasted.
 */
@Serializable
data class TrackerAuth(
    val clientId: String,
    val clientSecret: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long = 0,
    val user: String? = null,
)

@Serializable
data class TrackerSettings(
    val trakt: TrackerAuth? = null,
    val simkl: TrackerAuth? = null,
    val anilist: TrackerAuth? = null,
)

/** What a device sign-in shows the user: a code to type on the tracker's site. */
data class DeviceCode(val userCode: String, val url: String, val deviceCode: String, val intervalS: Int, val expiresInS: Int)

/** What's playing, in the terms the trackers use. */
data class Scrobble(val imdb: String?, val kitsu: String?, val type: String, val season: Int?, val episode: Int?)

private val jsonType = "application/json".toMediaType()

// ---------------- Trakt ----------------

object Trakt {
    private const val API = "https://api.trakt.tv"

    private fun request(auth: TrackerAuth, path: String) = Request.Builder().url(API + path)
        .header("trakt-api-version", "2")
        .header("trakt-api-key", auth.clientId)
        .header("Content-Type", "application/json")
        .apply { auth.accessToken?.let { header("Authorization", "Bearer $it") } }

    suspend fun deviceCode(clientId: String): DeviceCode {
        val body = buildJsonObject { put("client_id", clientId) }.toString().toRequestBody(jsonType)
        val json = Net.json.parseToJsonElement(Net.call(Request.Builder().url("$API/oauth/device/code").post(body).build())).jsonObject
        return DeviceCode(
            json.string("user_code")!!,
            json.string("verification_url") ?: "https://trakt.tv/activate",
            json.string("device_code")!!,
            json["interval"]?.jsonPrimitive?.intOrNull ?: 5,
            json["expires_in"]?.jsonPrimitive?.intOrNull ?: 600,
        )
    }

    /** Polls until the user approves the code on trakt.tv (or it expires). */
    suspend fun awaitToken(clientId: String, secret: String, code: DeviceCode): TrackerAuth {
        val deadline = System.currentTimeMillis() + code.expiresInS * 1000L
        var interval = code.intervalS * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            val body = buildJsonObject {
                put("code", code.deviceCode)
                put("client_id", clientId)
                put("client_secret", secret)
            }.toString().toRequestBody(jsonType)
            try {
                val json = Net.json.parseToJsonElement(Net.call(Request.Builder().url("$API/oauth/device/token").post(body).build())).jsonObject
                val auth = tokens(TrackerAuth(clientId, secret), json)
                return auth.copy(user = runCatching { username(auth) }.getOrNull())
            } catch (e: Net.HttpError) {
                when (e.code) {
                    400 -> continue // Waiting for the user.
                    429 -> interval += 1000 // Polling too fast.
                    404 -> error("Trakt didn't recognise the code. Try again.")
                    409 -> error("That code was already used.")
                    410 -> error("The code expired. Try again.")
                    418 -> error("You denied the request on trakt.tv.")
                    else -> throw e
                }
            }
        }
        error("The code expired. Try again.")
    }

    private fun tokens(base: TrackerAuth, json: JsonObject) = base.copy(
        accessToken = json.string("access_token"),
        refreshToken = json.string("refresh_token"),
        expiresAt = System.currentTimeMillis() + (json["expires_in"]?.jsonPrimitive?.longOrNull ?: 7_776_000) * 1000,
    )

    private suspend fun username(auth: TrackerAuth): String? =
        Net.json.parseToJsonElement(Net.call(request(auth, "/users/settings").build())).jsonObject["user"]?.jsonObject?.string("username")

    /** Refreshes the token when it is within a day of expiring. */
    suspend fun fresh(auth: TrackerAuth): TrackerAuth {
        if (auth.expiresAt - System.currentTimeMillis() > 86_400_000 || auth.refreshToken == null || auth.clientSecret == null) return auth
        val body = buildJsonObject {
            put("refresh_token", auth.refreshToken)
            put("client_id", auth.clientId)
            put("client_secret", auth.clientSecret)
            put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob")
            put("grant_type", "refresh_token")
        }.toString().toRequestBody(jsonType)
        val json = Net.json.parseToJsonElement(Net.call(Request.Builder().url("$API/oauth/token").post(body).build())).jsonObject
        return tokens(auth, json)
    }

    suspend fun scrobble(auth: TrackerAuth, action: String, what: Scrobble, progress: Double) {
        val imdb = what.imdb ?: return
        val body = buildJsonObject {
            put("progress", progress)
            if (what.season != null && what.episode != null) {
                putJsonObject("show") { putJsonObject("ids") { put("imdb", imdb) } }
                putJsonObject("episode") {
                    put("season", what.season)
                    put("number", what.episode)
                }
            } else {
                putJsonObject("movie") { putJsonObject("ids") { put("imdb", imdb) } }
            }
        }.toString().toRequestBody(jsonType)
        Net.call(request(auth, "/scrobble/$action").post(body).build())
    }

    /** Paused titles on Trakt, for Continue watching ("Paused on Trakt" in Harbor). */
    suspend fun playback(auth: TrackerAuth): List<CwItem> {
        val list = Net.json.parseToJsonElement(Net.call(request(auth, "/sync/playback?limit=30").build())) as? JsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val item = element.jsonObject
            val progress = (item["progress"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null) / 100.0
            val paused = item.string("paused_at")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0
            when (item.string("type")) {
                "movie" -> {
                    val movie = item["movie"]?.jsonObject ?: return@mapNotNull null
                    val imdb = movie["ids"]?.jsonObject?.string("imdb") ?: return@mapNotNull null
                    CwItem(metahubMeta(imdb, "movie", movie.string("title").orEmpty()), imdb, 0, 0, null, null, paused, progress.toFloat(), "Trakt")
                }
                "episode" -> {
                    val show = item["show"]?.jsonObject ?: return@mapNotNull null
                    val episode = item["episode"]?.jsonObject ?: return@mapNotNull null
                    val imdb = show["ids"]?.jsonObject?.string("imdb") ?: return@mapNotNull null
                    val season = episode["season"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    val number = episode["number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    CwItem(metahubMeta(imdb, "series", show.string("title").orEmpty()), "$imdb:$season:$number", 0, 0, season, number, paused, progress.toFloat(), "Trakt")
                }
                else -> null
            }
        }
    }

    private fun metahubMeta(imdb: String, type: String, name: String) = Meta(
        id = imdb,
        type = type,
        name = name,
        poster = "https://images.metahub.space/poster/medium/$imdb/img",
        background = "https://images.metahub.space/background/medium/$imdb/img",
    )
}

// ---------------- Simkl ----------------

object Simkl {
    private const val API = "https://api.simkl.com"

    suspend fun pin(clientId: String): DeviceCode {
        val json = Net.json.parseToJsonElement(Net.get("$API/oauth/pin?client_id=$clientId&redirect=urn:ietf:wg:oauth:2.0:oob")).jsonObject
        return DeviceCode(
            json.string("user_code") ?: error(json.string("message") ?: "Simkl didn't give a code"),
            json.string("verification_url") ?: "https://simkl.com/pin",
            json.string("device_code").orEmpty(),
            json["interval"]?.jsonPrimitive?.intOrNull ?: 5,
            json["expires_in"]?.jsonPrimitive?.intOrNull ?: 900,
        )
    }

    suspend fun awaitToken(clientId: String, code: DeviceCode): TrackerAuth {
        val deadline = System.currentTimeMillis() + code.expiresInS * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(code.intervalS * 1000L)
            val json = runCatching { Net.json.parseToJsonElement(Net.get("$API/oauth/pin/${code.userCode}?client_id=$clientId")).jsonObject }.getOrNull()
                ?: continue
            val token = json.string("access_token")
            if (json.string("result") == "OK" && token != null) {
                val auth = TrackerAuth(clientId, accessToken = token, expiresAt = Long.MAX_VALUE)
                return auth.copy(user = runCatching { user(auth) }.getOrNull())
            }
        }
        error("The code expired. Try again.")
    }

    private fun request(auth: TrackerAuth, path: String) = Request.Builder().url(API + path)
        .header("simkl-api-key", auth.clientId)
        .header("Authorization", "Bearer ${auth.accessToken}")
        .header("Content-Type", "application/json")

    private suspend fun user(auth: TrackerAuth): String? =
        Net.json.parseToJsonElement(Net.call(request(auth, "/users/settings").post("{}".toRequestBody(jsonType)).build()))
            .jsonObject["user"]?.jsonObject?.string("name")

    suspend fun scrobble(auth: TrackerAuth, action: String, what: Scrobble, progress: Double) {
        val imdb = what.imdb ?: return
        val body = buildJsonObject {
            put("progress", progress)
            if (what.season != null && what.episode != null) {
                putJsonObject("show") { putJsonObject("ids") { put("imdb", imdb) } }
                putJsonObject("episode") {
                    put("season", what.season)
                    put("number", what.episode)
                }
            } else {
                putJsonObject("movie") { putJsonObject("ids") { put("imdb", imdb) } }
            }
        }.toString().toRequestBody(jsonType)
        Net.call(request(auth, "/scrobble/$action").post(body).build())
    }
}

// ---------------- AniList ----------------

object AniList {
    private const val API = "https://graphql.anilist.co"

    /** AniList's implicit sign-in. With the app's redirect set to the PIN page, AniList shows the token to paste. */
    fun authorizeUrl(clientId: String) = "https://anilist.co/api/v2/oauth/authorize?client_id=$clientId&response_type=token"

    private suspend fun query(auth: TrackerAuth, query: String, variables: JsonObject = JsonObject(emptyMap())): JsonObject {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody(jsonType)
        val request = Request.Builder().url(API).post(body)
            .header("Authorization", "Bearer ${auth.accessToken}")
            .header("Accept", "application/json")
            .build()
        val json = Net.json.parseToJsonElement(Net.call(request)).jsonObject
        (json["errors"] as? JsonArray)?.firstOrNull()?.jsonObject?.string("message")?.let { error(it) }
        return json["data"]?.jsonObject ?: error("AniList returned nothing")
    }

    suspend fun signIn(clientId: String, token: String): TrackerAuth {
        val auth = TrackerAuth(clientId, accessToken = token.trim(), expiresAt = System.currentTimeMillis() + 365L * 86_400_000)
        val name = query(auth, "query { Viewer { name } }")["Viewer"]?.jsonObject?.string("name")
        return auth.copy(user = name)
    }

    private val kitsuToAniList = mutableMapOf<String, Int?>()

    /** Kitsu id to AniList id, through Kitsu's own mappings. */
    private suspend fun anilistId(kitsuId: String): Int? = kitsuToAniList.getOrPut(kitsuId) {
        runCatching {
            Net.json.parseToJsonElement(Net.get("https://kitsu.io/api/edge/anime/$kitsuId/mappings")).jsonObject["data"]
                .let { it as? JsonArray }.orEmpty()
                .map { it.jsonObject["attributes"]?.jsonObject }
                .firstOrNull { it?.string("externalSite") == "anilist/anime" }
                ?.string("externalId")?.toIntOrNull()
        }.getOrNull()
    }

    /** Marks episode `episode` watched, never moving progress backwards. */
    suspend fun markWatched(auth: TrackerAuth, kitsuId: String, episode: Int) {
        val mediaId = anilistId(kitsuId) ?: return
        val vars = buildJsonObject { put("id", mediaId) }
        val media = query(auth, "query(\$id: Int) { Media(id: \$id) { episodes mediaListEntry { progress } } }", vars)["Media"]?.jsonObject
        val current = media?.get("mediaListEntry")?.let { it as? JsonObject }?.get("progress")?.jsonPrimitive?.intOrNull ?: 0
        if (episode <= current) return
        val total = media?.get("episodes")?.jsonPrimitive?.intOrNull
        val status = if (total != null && episode >= total) "COMPLETED" else "CURRENT"
        query(
            auth,
            "mutation(\$id: Int, \$progress: Int, \$status: MediaListStatus) { SaveMediaListEntry(mediaId: \$id, progress: \$progress, status: \$status) { id } }",
            buildJsonObject {
                put("id", mediaId)
                put("progress", episode)
                put("status", status)
            },
        )
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
