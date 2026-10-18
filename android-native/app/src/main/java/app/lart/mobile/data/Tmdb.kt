package app.lart.mobile.data

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.util.Locale

/** A person in a title's cast, for the detail page. */
data class CastMember(val name: String, val character: String?, val photo: String?)

/** What TMDB adds to a detail page: artwork, a translated overview, and the cast. */
data class TmdbExtras(val backdrop: String?, val logo: String?, val overview: String?, val title: String?, val cast: List<CastMember>)

/**
 * TMDB, as Harbor uses it with a key: rows for Home, Movies and Shows, logos and
 * backdrops, translated titles and the cast. Titles from TMDB carry "tmdb:" ids and
 * are turned into IMDb ids (which stream addons understand) when opened.
 */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"
    private const val IMAGES = "https://image.tmdb.org/t/p"

    private val language: String get() = Locale.getDefault().toLanguageTag()

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
        val lang = Locale.getDefault().language
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

    /** Artwork, translation and cast for an IMDb title. */
    suspend fun extras(key: String, imdbId: String, type: String): TmdbExtras? {
        val found = get(key, "/find/$imdbId", mapOf("external_source" to "imdb_id"))
        val (kind, result) = if (type == "movie") {
            "movie" to (found["movie_results"] as? JsonArray)?.firstOrNull()
        } else {
            "tv" to (found["tv_results"] as? JsonArray)?.firstOrNull()
        }
        val id = result?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull ?: return null
        val lang = Locale.getDefault().language
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
