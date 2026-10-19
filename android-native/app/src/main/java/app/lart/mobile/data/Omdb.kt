package app.lart.mobile.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject

/** One outlet's score for a title: "IMDb" 8.7/10, "Rotten Tomatoes" 96%, "Metacritic" 87/100. */
data class Score(val source: String, val value: String)

/** OMDb with the user's own key, as Harbor's metadata providers: the real IMDb, RT and Metacritic scores. */
object Omdb {
    private val cache = mutableMapOf<String, List<Score>>()

    suspend fun verify(key: String) {
        val body = Net.json.parseToJsonElement(Net.get("https://www.omdbapi.com/?i=tt0111161&apikey=${android.net.Uri.encode(key)}")).jsonObject
        require(body.string("Response") == "True") { body.string("Error") ?: "OMDb didn't accept the key" }
    }

    suspend fun scores(key: String, imdbId: String): List<Score> {
        cache[imdbId]?.let { return it }
        val body = runCatching {
            Net.json.parseToJsonElement(Net.get("https://www.omdbapi.com/?i=$imdbId&apikey=${android.net.Uri.encode(key)}")).jsonObject
        }.getOrNull() ?: return emptyList()
        val ratings = (body["Ratings"] as? JsonArray).orEmpty().mapNotNull { r ->
            val o = r.jsonObject
            val source = when (o.string("Source")) {
                "Internet Movie Database" -> "IMDb"
                "Rotten Tomatoes" -> "Rotten Tomatoes"
                "Metacritic" -> "Metacritic"
                else -> return@mapNotNull null
            }
            val value = o.string("Value") ?: return@mapNotNull null
            Score(source, value.removeSuffix("/10").removeSuffix("/100"))
        }
        cache[imdbId] = ratings
        return ratings
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
