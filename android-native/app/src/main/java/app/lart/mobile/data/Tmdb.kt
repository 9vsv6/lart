package app.lart.mobile.data

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.util.Locale

/** A person in a title's cast, for the detail page. */
data class CastMember(val name: String, val character: String?, val photo: String?)

/** What TMDB adds to a detail page: artwork, a translated overview, and the cast. */
data class TmdbExtras(val backdrop: String?, val logo: String?, val overview: String?, val title: String?, val cast: List<CastMember>)

/** A streaming service that carries the title, for "Watch on". */
data class WatchProvider(val name: String, val logo: String?)

/** Harbor's detail page from TMDB: artwork, facts, people, where to watch, related titles and media. */
data class TmdbDetails(
    val backdrop: String?,
    val logo: String?,
    val overview: String?,
    val title: String?,
    val tagline: String?,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    val status: String?,
    val studios: List<String>,
    val networks: List<String>,
    val castCount: Int,
    val seasons: Int?,
    val episodes: Int?,
    val firstAired: String?,
    val lastAired: String?,
    val originalTitle: String?,
    val budget: Long?,
    val revenue: Long?,
    val trailerYouTube: String?,
    val tmdbId: Int,
    val tvdbId: Int?,
    val countries: List<String>,
    val originalLanguage: String?,
    val voteAverage: Double?,
    val voteCount: Int,
    val cast: List<CastMember>,
    val directors: List<String>,
    val writers: List<String>,
    val producers: List<String>,
    val cinematography: List<String>,
    val music: List<String>,
    val editors: List<String>,
    val watchOn: List<WatchProvider>,
    val recommendations: List<Meta>,
    val similar: List<Meta>,
    val backdrops: List<String>,
    val posters: List<String>,
    val logos: List<String>,
)

/**
 * TMDB, as Harbor uses it with a key: rows for Home, Movies and Shows, logos and
 * backdrops, translated titles and the cast. Titles from TMDB carry "tmdb:" ids and
 * are turned into IMDb ids (which stream addons understand) when opened.
 */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"
    private const val IMAGES = "https://image.tmdb.org/t/p"

    /** Settings → Languages → Details language; empty follows the device. */
    @Volatile var languageOverride: String = ""
    private val language: String get() = languageOverride.ifEmpty { Locale.getDefault().toLanguageTag() }

    private suspend fun get(key: String, path: String, params: Map<String, String> = emptyMap()): JsonObject {
        // v4 read tokens are long JWTs sent as a bearer token; v3 keys go in the query.
        val bearer = key.length > 60
        val query = buildString {
            append("language=").append(Uri.encode(language))
            params.forEach { (k, v) -> append('&').append(k).append('=').append(Uri.encode(v)) }
            if (!bearer) append("&api_key=").append(Uri.encode(key))
        }
        val request = Request.Builder().url("$API$path?$query").apply { if (bearer) header("Authorization", "Bearer $key") }.build()
        return Net.json.parseToJsonElement(Net.call(request)).jsonObject
    }

    suspend fun verify(key: String) {
        get(key, "/configuration")
    }

    @kotlinx.serialization.Serializable
    data class RowSpec(val title: String, val path: String, val params: Map<String, String> = emptyMap(), val type: String? = null)

    private val movieGenres = listOf(28 to "Action", 35 to "Comedy", 18 to "Drama", 878 to "Sci-Fi", 53 to "Thriller", 16 to "Animation", 27 to "Horror", 10749 to "Romance")
    private val tvGenres = listOf(18 to "Drama", 35 to "Comedy", 80 to "Crime", 10765 to "Sci-Fi & Fantasy", 16 to "Animation", 99 to "Documentary", 9648 to "Mystery", 10759 to "Action & Adventure")

    fun homeRows() = listOf(
        RowSpec("Trending Today", "/trending/all/day"),
        RowSpec("Popular Movies", "/movie/popular", type = "movie"),
        RowSpec("Popular Shows", "/tv/popular", type = "series"),
        RowSpec("Now Playing in Cinemas", "/movie/now_playing", type = "movie"),
        RowSpec("Top Rated Movies", "/movie/top_rated", type = "movie"),
        RowSpec("Top Rated Shows", "/tv/top_rated", type = "series"),
        RowSpec("On the Air", "/tv/on_the_air", type = "series"),
    )

    fun movieRows() = listOf(
        RowSpec("Trending Movies", "/trending/movie/week", type = "movie"),
        RowSpec("Popular Movies", "/movie/popular", type = "movie"),
        RowSpec("Top Rated Movies", "/movie/top_rated", type = "movie"),
        RowSpec("Coming Soon", "/movie/upcoming", type = "movie"),
    ) + movieGenres.map { (id, name) -> RowSpec("$name Movies", "/discover/movie", mapOf("with_genres" to "$id", "sort_by" to "popularity.desc"), "movie") }

    fun showRows() = listOf(
        RowSpec("Trending Shows", "/trending/tv/week", type = "series"),
        RowSpec("Popular Shows", "/tv/popular", type = "series"),
        RowSpec("Top Rated Shows", "/tv/top_rated", type = "series"),
        RowSpec("Airing Today", "/tv/airing_today", type = "series"),
    ) + tvGenres.map { (id, name) -> RowSpec("$name Shows", "/discover/tv", mapOf("with_genres" to "$id", "sort_by" to "popularity.desc"), "series") }

    private val genreNames = (movieGenres + tvGenres + listOf(12 to "Adventure", 14 to "Fantasy", 36 to "History", 10402 to "Music", 10751 to "Family", 37 to "Western", 10752 to "War", 10762 to "Kids", 10764 to "Reality"))
        .toMap()

    suspend fun row(key: String, spec: RowSpec, page: Int = 1): List<Meta> {
        val body = get(key, spec.path, spec.params + ("page" to "$page"))
        return (body["results"] as? JsonArray).orEmpty().mapNotNull { item -> toMeta(item.jsonObject, spec.type) }
    }

    private fun toMeta(item: JsonObject, forcedType: String?): Meta? {
        val mediaType = item.string("media_type")
        val type = forcedType ?: when (mediaType) {
            "movie" -> "movie"
            "tv" -> "series"
            else -> return null // People in "trending/all".
        }
        val id = item["id"]?.jsonPrimitive?.intOrNull ?: return null
        val name = item.string("title") ?: item.string("name") ?: return null
        val date = item.string("release_date") ?: item.string("first_air_date")
        return Meta(
            id = "tmdb:$type:$id",
            type = type,
            name = name,
            poster = item.string("poster_path")?.let { "$IMAGES/w342$it" },
            background = item.string("backdrop_path")?.let { "$IMAGES/w1280$it" },
            description = item.string("overview"),
            releaseInfo = date?.take(4),
            releaseDate = date,
            genres = (item["genre_ids"] as? JsonArray)?.mapNotNull { genreNames[it.jsonPrimitive.intOrNull] },
        )
    }

    private fun tmdbPath(meta: Meta): String? {
        val parts = meta.id.split(':')
        if (parts.size != 3 || parts[0] != "tmdb") return null
        return (if (parts[1] == "movie") "/movie/" else "/tv/") + parts[2]
    }

    /** The title's logo in the device language, else English, else any. */
    suspend fun logo(key: String, meta: Meta): String? {
        val path = tmdbPath(meta) ?: return null
        val lang = languageOverride.ifEmpty { Locale.getDefault().language }
        val images = get(key, "$path/images", mapOf("include_image_language" to "$lang,en,null"))
        val logos = (images["logos"] as? JsonArray).orEmpty().map { it.jsonObject }
        val best = logos.firstOrNull { it.string("iso_639_1") == lang } ?: logos.firstOrNull { it.string("iso_639_1") == "en" } ?: logos.firstOrNull()
        return best?.string("file_path")?.let { "$IMAGES/w500$it" }
    }

    /** Adds logos to the first few titles (the hero); the rest keep TMDB's artwork only. */
    suspend fun withLogos(key: String, metas: List<Meta>, count: Int = 8): List<Meta> = coroutineScope {
        val head = metas.take(count).map { meta -> async { runCatching { logo(key, meta) }.getOrNull()?.let { meta.copy(logo = it) } ?: meta } }.awaitAll()
        head + metas.drop(count)
    }

    /** The IMDb id for a "tmdb:" title, which stream addons and Cinemeta use. */
    suspend fun imdbId(key: String, meta: Meta): String? {
        val path = tmdbPath(meta) ?: return null
        return get(key, "$path/external_ids").string("imdb_id")?.takeIf { it.startsWith("tt") }
    }

    /** Everything Harbor's detail page shows from TMDB, for an IMDb title. */
    suspend fun details(key: String, imdbId: String, type: String): TmdbDetails? {
        val found = get(key, "/find/$imdbId", mapOf("external_source" to "imdb_id"))
        val (kind, result) = if (type == "movie") {
            "movie" to (found["movie_results"] as? JsonArray)?.firstOrNull()
        } else {
            "tv" to (found["tv_results"] as? JsonArray)?.firstOrNull()
        }
        val id = result?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull ?: return null
        val lang = languageOverride.ifEmpty { Locale.getDefault().language }
        val d = get(
            key,
            "/$kind/$id",
            mapOf(
                "append_to_response" to "credits,aggregate_credits,images,recommendations,similar,videos,watch/providers,external_ids",
                "include_image_language" to "$lang,en,null",
            ),
        )
        val images = d["images"]?.jsonObject
        fun pictures(name: String, size: String) =
            (images?.get(name) as? JsonArray).orEmpty().mapNotNull { it.jsonObject.string("file_path") }.distinct().take(30).map { "$IMAGES/$size$it" }
        val logos = (images?.get("logos") as? JsonArray).orEmpty().map { it.jsonObject }
        val logo = (logos.firstOrNull { it.string("iso_639_1") == lang } ?: logos.firstOrNull { it.string("iso_639_1") == "en" } ?: logos.firstOrNull())
            ?.string("file_path")?.let { "$IMAGES/w500$it" }
        // Series use the whole run's cast and crew (aggregate_credits), as Harbor does.
        val credits = d["credits"]?.jsonObject
        val aggregate = d["aggregate_credits"]?.jsonObject?.takeIf { kind == "tv" }
        val castJson = ((aggregate ?: credits)?.get("cast") as? JsonArray).orEmpty().map { it.jsonObject }
        val cast = castJson.take(60).map {
            val role = it.string("character") ?: (it["roles"] as? JsonArray)?.firstOrNull()?.jsonObject?.string("character")
            CastMember(it.string("name").orEmpty(), role?.takeIf { c -> c.isNotBlank() }, it.string("profile_path")?.let { p -> "$IMAGES/w342$p" })
        }
        val crew = ((credits?.get("crew") as? JsonArray).orEmpty() + (aggregate?.get("crew") as? JsonArray).orEmpty()).map { it.jsonObject }
        fun jobsOf(person: JsonObject): List<String> = listOfNotNull(person.string("job")) +
            (person["jobs"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject.string("job") }
        fun job(vararg jobs: String) = crew.filter { p -> jobsOf(p).any { it in jobs } }.mapNotNull { it.string("name") }.distinct()
        val trailer = (d["videos"]?.jsonObject?.get("results") as? JsonArray).orEmpty().map { it.jsonObject }
            .firstOrNull { it.string("site") == "YouTube" && it.string("type") == "Trailer" }?.string("key")
        val creators = (d["created_by"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject.string("name") }
        val country = Locale.getDefault().country.ifEmpty { "US" }
        val providers = d["watch/providers"]?.jsonObject?.get("results")?.jsonObject?.let { it[country] ?: it["US"] }?.jsonObject
        val watchOn = listOf("flatrate", "free", "ads").flatMap { (providers?.get(it) as? JsonArray).orEmpty() }.map { it.jsonObject }
            .distinctBy { it.string("provider_name") }
            .mapNotNull { p -> p.string("provider_name")?.let { WatchProvider(it, p.string("logo_path")?.let { l -> "$IMAGES/w92$l" }) } }
        val forced = if (kind == "movie") "movie" else "series"
        fun rail(name: String) = (d[name]?.jsonObject?.get("results") as? JsonArray).orEmpty().mapNotNull { toMeta(it.jsonObject, forced) }
        val runtime = d["runtime"]?.jsonPrimitive?.intOrNull ?: (d["episode_run_time"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.intOrNull
        val originalLanguage = d.string("original_language")?.let { Locale(it).getDisplayLanguage(Locale.getDefault()).ifBlank { null } }
        return TmdbDetails(
            backdrop = d.string("backdrop_path")?.let { "$IMAGES/w1280$it" },
            logo = logo,
            overview = d.string("overview")?.takeIf { it.isNotBlank() },
            title = d.string("title") ?: d.string("name"),
            tagline = d.string("tagline")?.takeIf { it.isNotBlank() },
            genres = (d["genres"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject.string("name") },
            runtimeMinutes = runtime,
            status = d.string("status"),
            studios = (d["production_companies"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject.string("name") }.distinct().take(4),
            networks = (d["networks"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject.string("name") },
            castCount = castJson.size,
            seasons = d["number_of_seasons"]?.jsonPrimitive?.intOrNull,
            episodes = d["number_of_episodes"]?.jsonPrimitive?.intOrNull,
            firstAired = d.string("first_air_date") ?: d.string("release_date"),
            lastAired = d.string("last_air_date"),
            originalTitle = (d.string("original_title") ?: d.string("original_name"))?.takeIf { it != (d.string("title") ?: d.string("name")) },
            budget = d["budget"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.takeIf { it > 0 },
            revenue = d["revenue"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.takeIf { it > 0 },
            trailerYouTube = trailer,
            tmdbId = id,
            tvdbId = d["external_ids"]?.jsonObject?.get("tvdb_id")?.jsonPrimitive?.intOrNull,
            countries = (d["production_countries"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject.string("name") },
            originalLanguage = originalLanguage,
            voteAverage = d["vote_average"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
            voteCount = d["vote_count"]?.jsonPrimitive?.intOrNull ?: 0,
            cast = cast,
            directors = job("Director").ifEmpty { creators },
            writers = job("Screenplay", "Writer", "Story", "Novel", "Teleplay").take(4),
            producers = job("Producer").take(4),
            cinematography = job("Director of Photography"),
            music = job("Original Music Composer", "Music"),
            editors = job("Editor"),
            watchOn = watchOn,
            recommendations = rail("recommendations"),
            similar = rail("similar"),
            backdrops = pictures("backdrops", "w780"),
            posters = pictures("posters", "w342"),
            logos = pictures("logos", "w300"),
        )
    }

    /** TMDB's episode scores for a season, by episode number (the fallback for episode ratings). */
    suspend fun seasonRatings(key: String, tvId: Int, season: Int): Map<Int, String> {
        val body = runCatching { get(key, "/tv/$tvId/season/$season") }.getOrNull() ?: return emptyMap()
        return (body["episodes"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e.jsonObject
            val n = o["episode_number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val v = o["vote_average"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            n to "%.1f".format(java.util.Locale.US, v)
        }.toMap()
    }

    /** Artwork, translation and cast for an IMDb title. */
    suspend fun extras(key: String, imdbId: String, type: String): TmdbExtras? {
        val found = get(key, "/find/$imdbId", mapOf("external_source" to "imdb_id"))
        val (kind, result) = if (type == "movie") {
            "movie" to (found["movie_results"] as? JsonArray)?.firstOrNull()
        } else {
            "tv" to (found["tv_results"] as? JsonArray)?.firstOrNull()
        }
        val id = result?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull ?: return null
        val lang = languageOverride.ifEmpty { Locale.getDefault().language }
        val details = get(
            key,
            "/$kind/$id",
            mapOf("append_to_response" to "credits,images", "include_image_language" to "$lang,en,null"),
        )
        val logos = (details["images"]?.jsonObject?.get("logos") as? JsonArray).orEmpty().map { it.jsonObject }
        val logo = (logos.firstOrNull { it.string("iso_639_1") == lang } ?: logos.firstOrNull { it.string("iso_639_1") == "en" } ?: logos.firstOrNull())
            ?.string("file_path")?.let { "$IMAGES/w500$it" }
        val cast = (details["credits"]?.jsonObject?.get("cast") as? JsonArray).orEmpty().take(20).map { it.jsonObject }.map {
            CastMember(it.string("name").orEmpty(), it.string("character")?.takeIf { c -> c.isNotBlank() }, it.string("profile_path")?.let { p -> "$IMAGES/w185$p" })
        }
        return TmdbExtras(
            backdrop = details.string("backdrop_path")?.let { "$IMAGES/w1280$it" },
            logo = logo,
            overview = details.string("overview")?.takeIf { it.isNotBlank() },
            title = details.string("title") ?: details.string("name"),
            cast = cast,
        )
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
